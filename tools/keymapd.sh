#!/system/bin/sh
# 在设备上开关遥控器改键守护进程（z6x keymap，由 z6x run 守护，意外退出后自动重启）。
# 由 ./tools/build.sh deploy 推送到 /data/local/tmp/z6x-tools/keymapd.sh；hub 的「后台任务」用它启动、停止、查看状态。
# 用法：keymapd.sh start | stop | status
cd "$(dirname "$0")"
pid() { p=$(cat keymap.pid 2>/dev/null) && [ -n "$p" ] && kill -0 "$p" 2>/dev/null && echo "$p"; }

case "${1:-status}" in
  start)
    if p=$(pid); then echo "已在运行（PID $p）"; exit 0; fi
    [ -f keymap.conf ] || { echo "缺少 keymap.conf：在 Deck 上执行 ./tools/keymap.sh projector --push-config"; exit 1; }
    # 由被守护的进程自己写入 PID（setsid 可能另起进程，$! 不可靠）
    setsid sh -c 'echo $$ > keymap.pid; exec ./z6x run --name keymap -- ./z6x keymap --daemon --config keymap.conf' > keymap.log 2>&1 < /dev/null &
    sleep 1
    if p=$(pid); then echo "已启动（PID $p）"; else echo "启动失败："; tail -5 keymap.log; exit 1; fi ;;
  stop)
    # 结束 z6x run，它会把 SIGTERM 转发给 keymap
    if p=$(pid); then kill "$p"; rm -f keymap.pid; echo "已停止"; else echo "本来就没有运行"; fi ;;
  status)
    if p=$(pid); then echo "运行中（PID $p）"; else echo "未运行"; exit 1; fi ;;
  *)
    echo "用法：keymapd.sh start | stop | status"; exit 2 ;;
esac
