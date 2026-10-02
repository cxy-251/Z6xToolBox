#!/data/data/com.termux/files/usr/bin/bash
# 在 Termux 中开关 z6x-hub。部署时由 deploy.sh 放到 ~/z6x-hub/，并在 ~/.bashrc 中设置别名 hub。
# 用法：hub start | stop | status | log
cd "$(dirname "$0")"
pid() { local p; p=$(cat hub.pid 2>/dev/null) && [ -r "/proc/$p/cmdline" ] && grep -q z6x-hub "/proc/$p/cmdline" && echo "$p"; }

case "${1:-status}" in
  start)
    p=$(pid) && kill "$p" && sleep 1
    # 唤醒锁默认不申请：2026-10-02 持有唤醒锁约一小时后，HyperOS 把 Termux 判为异常耗电并强制结束（AutoPowerKill）。
    # 需要时用 Z6X_WAKELOCK=1 hub start 开启。
    [ "${Z6X_WAKELOCK:-0}" = 1 ] && termux-wake-lock
    nohup ./z6x-hub -c hub.yaml > stdout.log 2>&1 < /dev/null &
    echo $! > hub.pid
    sleep 1
    if p=$(pid); then echo "hub 已启动（PID $p）"; ./z6x-hub -c hub.yaml -network; else echo "启动失败："; tail -5 stdout.log; fi ;;
  stop)
    if p=$(pid); then kill "$p"; echo "hub 已停止"; else echo "hub 本来就没有运行"; fi
    rm -f hub.pid
    termux-wake-unlock 2>/dev/null ;;
  status)
    if p=$(pid); then echo "hub 运行中（PID $p）"; ./z6x-hub -c hub.yaml -network; else echo "hub 未运行"; exit 1; fi ;;
  log)
    tail -20 hub.log ;;
  *)
    echo "用法：hub start | stop | status | log"; exit 1 ;;
esac
