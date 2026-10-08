#!/bin/bash
# 经数据线向手机传输大量文件：按文件增量同步，可随时中断、重新运行即续传。
# 用法：./scripts/phone_push.sh <Deck 上的目录> <手机上的目录>
#   例：./scripts/phone_push.sh ~/Games/omni_library/media_library/shortvideo/抖音 \
#                               /storage/emulated/0/omni_library/media_library/shortvideo/抖音
#
# 做法：
#   1. 两端各列出一份「相对路径 + 大小」清单（手机端用 toybox find -printf，末尾带结束标记，防止清单被截断）；
#   2. 手机上缺少、或大小与本地不同的文件才传输（中断留下的半截文件大小偏小，会被重传覆盖）；
#      手机上多出的文件一律不删除，也不经过临时目录，不额外占用空间；
#   3. 按顶层的每一项处理：手机上完全没有的整项一次推送（最快）；已有部分文件的，同一目录下缺失的文件合并为一次 adb push；
#   4. 每项传完重新核对，不一致则重试，最多 5 次；出错时重启 adb、等待手机重新连接。
# 不用 adb push --sync：中断留下的半截文件修改时间比本地新，会被当作已传完而跳过。
# 只按大小比对：内容改过但大小恰好不变的文件检测不出来（媒体文件基本不会这样）。
#
# 以下情况只报告、不传输：
#   • 软链接：手机共享存储（FUSE）不能创建软链接；
#   • 只有大小写不同的同名文件：手机共享存储不区分大小写，两者会互相覆盖。
#
# 注意：手机 USB 模式为「文件传输（MTP）」时，Deck 的桌面（KDE 的 kiod）会自动打开手机，与 adb 抢占 USB 设备，
# 表现为插着线但 adb devices 里没有手机、传输中途 EOF。脚本开始时把手机 USB 模式设为「仅充电」（USB 调试不受影响）。
# 经扩展坞连接时手机端 adb 频繁卡死（2026-10-04，每传几百 MB 一次），直接插 Deck 机身的 USB-C 口则正常。
set -uo pipefail
export LC_ALL=C.UTF-8

SRC=${1:?用法：$0 <Deck 上的目录> <手机上的目录>}
DST=${2:?用法：$0 <Deck 上的目录> <手机上的目录>}
SRC=${SRC%/}
DST=${DST%/}
[ -d "$SRC" ] || { echo "本地目录不存在：$SRC"; exit 1; }

ADB=(adb -d)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# q：把任意字符串转成远程 shell 可用的单引号形式（' → '\''）
q() { printf "'%s'" "${1//\'/\'\\\'\'}"; }

# 等待手机经数据线连接；失败时重启 adb 服务（adb 偶尔认不回重新枚举的设备）
wait_phone() {
    for _ in 1 2 3; do
        timeout 60 "${ADB[@]}" wait-for-usb-device 2>/dev/null && timeout 10 "${ADB[@]}" shell true 2>/dev/null && return 0
        echo "  …手机未连接，重启 adb 服务"
        timeout 15 adb kill-server 2>/dev/null
        timeout 20 adb start-server >/dev/null 2>&1
    done
    # 手机端 adbd 的 USB 通道卡死时（lsusb 能看到手机，adb 显示 offline 或列表为空），只能在手机一侧恢复
    echo "  ！手机的 adb 卡住了：请拔下数据线再插上（或在开发者选项中关闭再打开「USB 调试」），插回后自动继续（最多等 30 分钟）"
    timeout 1800 "${ADB[@]}" wait-for-usb-device 2>/dev/null && sleep 3 && timeout 10 "${ADB[@]}" shell svc usb setFunctions >/dev/null 2>&1
    sleep 3
    timeout 60 "${ADB[@]}" wait-for-usb-device 2>/dev/null && timeout 10 "${ADB[@]}" shell true 2>/dev/null && return 0
    echo "手机一直没有连上：请检查数据线，并确认手机上的「USB 调试」已打开"
    exit 1
}

# phone_list <手机目录> <输出文件>：输出「相对路径<TAB>大小」，目录不存在时为空；清单不完整时返回 1
phone_list() {
    timeout 600 "${ADB[@]}" shell "[ -d $(q "$1") ] && find $(q "$1") -type f -printf '%P\t%s\n'; echo __END__" 2>/dev/null | tr -d '\r' > "$2.raw"
    [ "$(tail -n1 "$2.raw")" = __END__ ] || return 1
    sed '$d' "$2.raw" > "$2"
}

# pending <本地清单> <手机清单>：输出需要传输的相对路径（手机上没有，或大小不同；路径按小写比对）
pending() {
    # 用 FILENAME 区分两份清单：手机清单为空时 NR == FNR 会把本地清单误当作手机清单
    awk -F'\t' 'FILENAME == ARGV[1] { have[tolower($1)] = $2; next } !(tolower($1) in have) || have[tolower($1)] != $2 { print $1 }' "$2" "$1"
}

wait_phone
timeout 10 "${ADB[@]}" shell svc usb setFunctions >/dev/null 2>&1 # 仅充电，避免桌面经 MTP 抢占 USB
wait_phone
"${ADB[@]}" shell "mkdir -p $(q "$DST")"

# 本地清单；软链接与大小写冲突只报告
(cd "$SRC" && find . -type f -printf '%P\t%s\n') > "$TMP/local"
links=$(cd "$SRC" && find . -type l | wc -l)
[ "$links" -gt 0 ] && { echo "跳过 $links 个软链接（手机共享存储不支持）："; (cd "$SRC" && find . -type l | head -5); }
cut -f1 "$TMP/local" | awk '{ k = tolower($0) } k in seen { print seen[k] "  ↔  " $0 } { seen[k] = $0 }' > "$TMP/case"
if [ -s "$TMP/case" ]; then
    echo "以下文件只有大小写不同，手机上会互相覆盖，跳过后者："
    head -20 "$TMP/case"
    awk -F'\t' '{ k = tolower($1) } !(k in seen) { seen[k] = 1; print }' "$TMP/local" > "$TMP/local.dedup" && mv "$TMP/local.dedup" "$TMP/local"
fi

# 旧版脚本（经临时目录中转）留下的残留
"${ADB[@]}" shell "rm -rf $(q "$DST")/.part-*" 2>/dev/null

until phone_list "$DST" "$TMP/phone"; do echo "  读取手机上的文件清单失败，重试"; wait_phone; done
pending "$TMP/local" "$TMP/phone" > "$TMP/todo"
todo_n=$(wc -l < "$TMP/todo")
total_n=$(wc -l < "$TMP/local")
echo "本地 $total_n 个文件，手机上已有 $((total_n - todo_n)) 个，需要传输 $todo_n 个"

mapfile -t ITEMS < <(cut -d/ -f1 "$TMP/todo" | awk '!seen[$0]++')
done_n=0 fail_n=0 i=0
start=$(date +%s)

for name in "${ITEMS[@]}"; do
    i=$((i + 1))
    N="$name" awk -F'\t' '$1 == ENVIRON["N"] || index($1, ENVIRON["N"] "/") == 1' "$TMP/local" > "$TMP/item.local"
    bytes=$(awk -F'\t' '{ s += $2 } END { print s + 0 }' "$TMP/item.local")
    files_n=$(wc -l < "$TMP/item.local")
    limit=$((120 + bytes / 5000000 + files_n / 10)) # 超时：至少 2 分钟，按 5MB/s 估算，另加每个文件 0.1 秒（小文件多时主要耗在逐个文件上）
    echo "[$i/${#ITEMS[@]}] $name（$files_n 个文件，$(numfmt --to=iec "$bytes")）"
    ok=0
    for try in 1 2 3 4 5; do
        # 本项在手机上的现状：存在与否、已有文件清单
        exists=$(timeout 10 "${ADB[@]}" shell "[ -e $(q "$DST/$name") ] && echo y" 2>/dev/null | tr -d '\r')
        if [ -f "$SRC/$name" ]; then
            timeout 10 "${ADB[@]}" shell "[ -f $(q "$DST/$name") ] && stat -c %s $(q "$DST/$name")" 2>/dev/null | tr -d '\r' |
                N="$name" awk 'NF { print ENVIRON["N"] "\t" $1 }' > "$TMP/item.phone"
        elif phone_list "$DST/$name" "$TMP/item.rel"; then
            N="$name" awk '{ print ENVIRON["N"] "/" $0 }' "$TMP/item.rel" > "$TMP/item.phone"
        else
            echo "  读取清单失败"; wait_phone; continue
        fi
        pending "$TMP/item.local" "$TMP/item.phone" > "$TMP/item.todo"
        if [ ! -s "$TMP/item.todo" ]; then ok=1; break; fi
        [ "$try" -gt 1 ] && echo "  还差 $(wc -l < "$TMP/item.todo") 个文件"

        if [ -z "$exists" ] || [ -f "$SRC/$name" ]; then
            # 手机上没有这一项（或它是单个文件）：整项一次推送。目标必须不存在，否则目录会被放进同名目录里
            timeout "$limit" "${ADB[@]}" push "$SRC/$name" "$DST/$name" >/dev/null 2>&1 || { echo "  传输中断"; sleep 3; wait_phone; }
            continue
        fi
        # 已有部分文件：按所在目录分组，每组最多 500 个文件合并为一次 adb push
        failed=0
        while IFS= read -r dir; do
            mapfile -t files < <(D="$dir" awk '{ p = $0; sub(/\/[^\/]*$/, "", p) } p == ENVIRON["D"]' "$TMP/item.todo")
            # adb shell 会读取标准输入，不重定向就会吞掉 while 循环剩下的目录列表，每轮只处理第一个目录
            "${ADB[@]}" shell "mkdir -p $(q "$DST/$dir")" < /dev/null 2>/dev/null
            for ((k = 0; k < ${#files[@]}; k += 500)); do
                args=()
                for f in "${files[@]:k:500}"; do args+=("$SRC/$f"); done
                timeout "$limit" "${ADB[@]}" push "${args[@]}" "$DST/$dir/" < /dev/null >/dev/null 2>&1 || { failed=1; break 2; }
            done
        done < <(awk '{ sub(/\/[^\/]*$/, ""); print }' "$TMP/item.todo" | sort -u)
        [ $failed = 1 ] && { echo "  传输中断"; sleep 3; wait_phone; }
    done
    if [ $ok = 1 ]; then done_n=$((done_n + 1)); else fail_n=$((fail_n + 1)); echo "  放弃：$name"; fi
done

echo "完成：处理 $done_n 项，失败 $fail_n 项；用时 $(( ($(date +%s) - start) / 60 )) 分钟"
[ $fail_n = 0 ]
