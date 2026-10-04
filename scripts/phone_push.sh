#!/bin/bash
# 经数据线向手机传输大量文件，可中断、可续传。
# 用法：./scripts/phone_push.sh <Deck 上的目录> <手机上的目录>
#   例：./scripts/phone_push.sh ~/Games/omni_library/media_library/shortvideo/抖音 \
#                               /storage/emulated/0/omni_library/media_library/shortvideo/抖音
#
# 按「本地目录下的每一项」（一个文件夹或文件）逐个传输：
#   1. 手机上已有同名项且文件数、总字节数都与本地一致 → 跳过（已传完）；
#   2. 否则先传到临时名 .part-<名称>，核对一致后再改为正式名称；不一致的旧副本（上次中断留下的）先删除再重传；
#   3. 传输出错（数据线断开、adb 掉线）时重启 adb 并等待手机重新连接，同一项最多重试 5 次。
# 一次 adb push 整个大目录时，中途断开一次就要从头再来，也分不清哪些文件只传了一半：
# 断点留下的半截文件修改时间比本地新，adb push --sync 会把它当作已传完而跳过，因此这里不用 --sync，而是逐项核对。
#
# 注意：手机 USB 模式为「文件传输（MTP）」时，Deck 的桌面（KDE 的 kiod）会自动打开手机，与 adb 抢占 USB 设备，
# 表现为插着线但 adb devices 里没有手机、传输中途 EOF。脚本开始时把手机 USB 模式设为「仅充电」（USB 调试不受影响）。
set -uo pipefail

SRC=${1:?用法：$0 <Deck 上的目录> <手机上的目录>}
DST=${2:?用法：$0 <Deck 上的目录> <手机上的目录>}
SRC=${SRC%/}
DST=${DST%/}
[ -d "$SRC" ] || { echo "本地目录不存在：$SRC"; exit 1; }

ADB=(adb -d)

# 等待手机经数据线连接；失败时重启 adb 服务（adb 偶尔认不回重新枚举的设备）
wait_phone() {
    for _ in 1 2 3; do
        timeout 60 "${ADB[@]}" wait-for-usb-device 2>/dev/null && timeout 10 "${ADB[@]}" shell true 2>/dev/null && return 0
        echo "  …手机未连接，重启 adb 服务"
        timeout 15 adb kill-server 2>/dev/null
        timeout 20 adb start-server >/dev/null 2>&1
    done
    echo "手机一直没有连上：请检查数据线，并确认手机上的「USB 调试」已打开"
    exit 1
}

# 输出「文件数 总字节数」
local_stat() { find "$1" -type f -printf '%s\n' | awk '{n++; s+=$1} END {print n+0, s+0}'; }
phone_stat() {
    timeout 120 "${ADB[@]}" shell "[ -e '$1' ] && find '$1' -type f -exec stat -c %s {} + | awk '{n++; s+=\$1} END {print n+0, s+0}' || echo none" 2>/dev/null | tr -d '\r'
}

wait_phone
timeout 10 "${ADB[@]}" shell svc usb setFunctions >/dev/null 2>&1 # 仅充电，避免桌面经 MTP 抢占 USB
wait_phone
"${ADB[@]}" shell "mkdir -p '$DST'"

mapfile -t ITEMS < <(cd "$SRC" && find . -mindepth 1 -maxdepth 1 ! -name '.*' -printf '%f\n' | sort)
total=${#ITEMS[@]}
done_n=0 skip_n=0 fail_n=0 i=0
start=$(date +%s)

for name in "${ITEMS[@]}"; do
    i=$((i + 1))
    case "$name" in *"'"*) echo "[$i/$total] 跳过（名称含单引号，请手动传输）：$name"; fail_n=$((fail_n + 1)); continue ;; esac
    want=$(local_stat "$SRC/$name")
    have=$(phone_stat "$DST/$name")
    if [ "$have" = "$want" ]; then
        skip_n=$((skip_n + 1))
        continue
    fi
    echo "[$i/$total] $name（${want% *} 个文件，$(numfmt --to=iec "${want#* }")）"
    ok=0
    for try in 1 2 3 4 5; do
        "${ADB[@]}" shell "rm -rf '$DST/.part-$name'" 2>/dev/null
        if timeout 3600 "${ADB[@]}" push "$SRC/$name" "$DST/.part-$name" >/dev/null 2>&1 \
            && [ "$(phone_stat "$DST/.part-$name")" = "$want" ]; then
            "${ADB[@]}" shell "rm -rf '$DST/$name' && mv '$DST/.part-$name' '$DST/$name'" && { ok=1; break; }
        fi
        echo "  第 $try 次失败，等待手机重新连接后重试"
        sleep 3
        wait_phone
    done
    if [ $ok = 1 ]; then done_n=$((done_n + 1)); else fail_n=$((fail_n + 1)); echo "  放弃：$name"; fi
done

echo "完成：新传 $done_n 项，已存在跳过 $skip_n 项，失败 $fail_n 项，共 $total 项；用时 $(( ($(date +%s) - start) / 60 )) 分钟"
[ $fail_n = 0 ]
