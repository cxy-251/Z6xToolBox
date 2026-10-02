#!/bin/bash
# 把按键配置推送到设备并（重新）启动 keymap 守护进程，由 z6x run 守护（意外退出后自动重启）。
# 用法：./tools/keymap.sh <设备名> [start|stop|status] [--push-config]
# 配置以设备上的为准（可能已在 hub「遥控器按键」页面修改过）：设备上已有配置时只拉回本机
# tools/keymap/<设备名>.conf，不覆盖；设备上没有配置或指定 --push-config 时才推送本机的。
# 需要先部署 z6x：./tools/build.sh deploy <设备名>。设备重启后需重新执行本脚本（与 hub 相同）。
set -euo pipefail
cd "$(dirname "$0")"
NAME="${1:?缺少设备名，例如 ./tools/keymap.sh projector}"
cd ../hub && source ./lib.sh && resolve_device "$NAME" && cd ../tools
[ "$MODE" = adb ] || { echo "keymap 需要 shell 身份读取输入设备，只支持以 ADB 方式运行的设备"; exit 1; }
D=/data/local/tmp/z6x-tools
# 按 PID 文件结束旧的守护进程（先结束 z6x run，它会把 SIGTERM 转发给 keymap）
STOP="[ -f $D/keymap.pid ] && kill \$(cat $D/keymap.pid) 2>/dev/null; rm -f $D/keymap.pid; sleep 1"
PUSH=0; ACTION=start
for a in "${@:2}"; do case "$a" in --push-config) PUSH=1 ;; *) ACTION="$a" ;; esac; done
case "$ACTION" in
  stop)
    adb -s "$ADDR" shell "$STOP"; echo "== keymap 已停止" ;;
  status)
    adb -s "$ADDR" shell "[ -f $D/keymap.pid ] && kill -0 \$(cat $D/keymap.pid) 2>/dev/null && echo '运行中（PID '\$(cat $D/keymap.pid)'）' || echo 未运行; tail -5 $D/keymap.log | sed 's/^\[[0-9]*\] //'" ;;
  start)
    CONF="keymap/$NAME.conf"
    [ -f "$CONF" ] || { echo "缺少 $CONF"; exit 1; }
    if [ "$PUSH" = 0 ] && adb -s "$ADDR" shell "test -f $D/keymap.conf"; then
      adb -s "$ADDR" pull "$D/keymap.conf" "$CONF" >/dev/null && echo "== 使用设备上的配置（已同步到本机 $CONF）"
    else
      adb -s "$ADDR" push "$CONF" "$D/keymap.conf" >/dev/null && echo "== 已推送本机的 $CONF"
    fi
    adb -s "$ADDR" shell "$D/z6x keymap --check --config $D/keymap.conf"
    # 由被守护的进程自己写入 PID（setsid 可能另起进程，$! 不可靠）；adb shell 可能等待后台进程，加超时
    timeout 20 adb -s "$ADDR" shell "$STOP; cd $D && setsid sh -c 'echo \$\$ > keymap.pid; exec ./z6x run --name keymap -- ./z6x keymap --daemon --config keymap.conf' > keymap.log 2>&1 < /dev/null &" || true
    sleep 2
    adb -s "$ADDR" shell "sed 's/^\[[0-9]*\] //' $D/keymap.log | tail -4" ;;
esac
