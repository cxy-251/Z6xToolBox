# deploy.sh 与 ctl.sh 共用：解析设备，并按设备的运行方式执行远程命令、传输文件、启动 hub。
#
# 运行方式（devices/devices.txt 第三列）：
#   adb     从 ADB 启动，shell 身份（uid 2000）。投影仪使用：ADB 常开，hub 随 adbd 一直运行。
#   termux  在 Termux 中运行，普通应用身份，经 SSH（8022 端口，密钥 ~/.ssh/<设备名>_ed25519）部署。
#           手机使用：无线调试关闭时 adbd 会连同它启动的进程一起结束，hub 放在 Termux 里不受影响，
#           也可以在手机上用 hub start / hub stop 自行开关。
#
# resolve_device <设备名或地址> 设置：NAME、MODE、HOST、CFG、RDIR（设备上的目录），adb 方式另有 ADDR。

resolve_device() {
  local target="$1" line
  if [[ "$target" == *.* ]]; then
    line=$(awk -v h="${target%%:*}" '!/^#/ && NF>=2 { split($2, a, ":"); if (a[1] == h) { print; exit } }' devices/devices.txt)
    [ -n "$line" ] || { echo "devices/devices.txt 中没有 IP 为 ${target%%:*} 的设备"; exit 1; }
  else
    line=$(awk -v n="$target" '!/^#/ && $1 == n { print; exit }' devices/devices.txt)
    [ -n "$line" ] || { echo "未知设备：$target（可选：$(awk '!/^#/ && NF { printf "%s ", $1 }' devices/devices.txt)）"; exit 1; }
  fi
  read -r NAME ADDR MODE <<<"$line"
  MODE="${MODE:-adb}"
  HOST="${ADDR%%:*}"
  CFG="devices/$NAME.yaml"
  if [ "$MODE" = termux ]; then
    RDIR=z6x-hub  # 相对于 Termux 的主目录
    SSH=(ssh -i "$HOME/.ssh/${NAME}_ed25519" -p 8022 -o ConnectTimeout=8 -o BatchMode=yes "$HOST")
    "${SSH[@]}" true 2>/dev/null || { echo "$NAME 的 SSH 连不上：请在手机上打开 Termux 并执行 sshd"; exit 1; }
    return
  fi
  RDIR=/data/local/tmp/z6x-hub
  if [[ "$target" == *.*:* ]]; then ADDR="$target"; fi
  if [[ "$ADDR" != *:* ]]; then
    # 只有 IP：找该 IP 当前已连接的端口（手机无线调试每次开启端口都不同）
    ADDR=$(adb devices | awk -v h="$ADDR" '$2 == "device" && index($1, h ":") == 1 { print $1; exit }')
    [ -n "$ADDR" ] || { echo "$NAME 未连接。请先在手机「无线调试」中查看端口，执行 adb connect <IP>:<端口>"; exit 1; }
  fi
}

# rsh <命令>：在设备上执行
rsh() {
  if [ "$MODE" = termux ]; then "${SSH[@]}" "$@"; else adb -s "$ADDR" shell "$@"; fi
}

# put <本机文件> <设备上的路径>、get <设备上的路径> <本机文件>
put() {
  if [ "$MODE" = termux ]; then
    scp -q -i "$HOME/.ssh/${NAME}_ed25519" -P 8022 "$1" "$HOST:$2"
  else adb -s "$ADDR" push "$1" "$2" >/dev/null; fi
}
get() {
  if [ "$MODE" = termux ]; then
    scp -q -i "$HOME/.ssh/${NAME}_ed25519" -P 8022 "$HOST:$1" "$2"
  else adb -s "$ADDR" pull "$1" "$2" >/dev/null; fi
}

# start_hub：用设备上已有的程序和配置启动（先结束旧进程），并检查健康状态。
start_hub() {
  if [ "$MODE" = termux ]; then
    rsh "$RDIR/hub.sh start" >/dev/null
  else
    # setsid 让 hub 脱离 adb shell 的会话，否则 adb shell 会一直等待它退出而无法返回。
    timeout 20 adb -s "$ADDR" shell "cd $RDIR && pid=\$(pidof z6x-hub); [ -n \"\$pid\" ] && kill \$pid; sleep 1; setsid ./z6x-hub -c hub.yaml > stdout.log 2>&1 < /dev/null &"
  fi
  sleep 2
  local h
  if ! h=$(curl -s -m 5 "http://$HOST:8090/api/health"); then
    echo "== hub 未对外服务：可能未运行，或当前网络不可信。查看：./hub/ctl.sh $NAME status"
    return 1
  fi
  echo "== hub 已启动：$(echo "$h" | grep -c '"running"') 个模块运行中，$(echo "$h" | grep -c '"failed"') 个失败"
}
