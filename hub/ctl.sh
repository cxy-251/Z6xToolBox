#!/bin/bash
# 开关设备上的 z6x-hub（不重新编译）。
# 用法：./hub/ctl.sh <设备名或 ADB 地址> <命令>
#   start    启动（使用设备上已部署的程序和配置）
#   stop     停止
#   status   查看是否运行、当前网络是否可信、各模块状态
#   trust    把设备当前连接的 Wi-Fi 加入可信网络（在家里执行一次即可），然后重启 hub
# Termux 方式的设备（手机）经 SSH 控制；在手机的 Termux 中也可直接执行 hub start / hub stop。
# 网页首页的「停止 hub」按钮也可以停止。
set -euo pipefail
cd "$(dirname "$0")"
source ./lib.sh
[ $# -ge 2 ] || { sed -n 2,9p "$0" | sed 's/^# \{0,1\}//'; exit 1; }
resolve_device "$1"

case "$2" in
  start)
    start_hub ;;
  stop)
    if [ "$MODE" = termux ]; then rsh "$RDIR/hub.sh stop"
    else rsh 'pid=$(pidof z6x-hub) && kill $pid && echo "== 已停止（PID $pid）" || echo "== hub 本来就没有运行"'; fi ;;
  status)
    if [ "$MODE" = termux ]; then rsh "$RDIR/hub.sh status"
    else
      pid=$(rsh pidof z6x-hub || true)
      if [ -z "$pid" ]; then echo "$NAME：hub 未运行"; exit 0; fi
      echo "$NAME：hub 运行中（PID $pid）"
      rsh "cd $RDIR && ./z6x-hub -c hub.yaml -network"
    fi
    h=$(curl -s -m 5 "http://$HOST:8090/api/health") && echo "$h" | grep -oE '"name": "[^"]*"|"state": "[^"]*"' | paste - - | sed 's/"name": //; s/"state": //; s/"//g; s/^/  /' \
      || echo "  对外服务：未监听（未运行，或不在可信网络上）"
    ;;
  trust)
    rsh "cd $RDIR && ./z6x-hub -c hub.yaml -trust"
    get "$RDIR/hub.yaml" "$CFG" && chmod 600 "$CFG"
    echo "== 已同步到本机 $CFG"
    start_hub ;;
  *)
    echo "未知命令：$2（可选：start stop status trust）"; exit 1 ;;
esac
