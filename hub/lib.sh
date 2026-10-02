# deploy.sh 与 ctl.sh 共用：把设备名或 ADB 地址解析为 NAME（设备名）、ADDR（ADB 地址）、CFG（本机配置）。
# 用法：resolve_device <设备名或地址>
DIR=/data/local/tmp/z6x-hub

resolve_device() {
  local target="$1"
  if [[ "$target" == *.* ]]; then
    ADDR="$target"
    NAME=$(awk -v h="${target%%:*}" '!/^#/ && NF>=2 { split($2, a, ":"); if (a[1] == h) { print $1; exit } }' devices/devices.txt)
    [ -n "$NAME" ] || { echo "devices/devices.txt 中没有 IP 为 ${target%%:*} 的设备"; exit 1; }
  else
    NAME="$target"
    ADDR=$(awk -v n="$NAME" '!/^#/ && $1 == n { print $2; exit }' devices/devices.txt)
    [ -n "$ADDR" ] || { echo "未知设备：$NAME（可选：$(awk '!/^#/ && NF { printf "%s ", $1 }' devices/devices.txt)）"; exit 1; }
  fi
  if [[ "$ADDR" != *:* ]]; then
    # 只有 IP：找该 IP 当前已连接的端口（手机无线调试每次开启端口都不同）
    ADDR=$(adb devices | awk -v h="$ADDR" '$2 == "device" && index($1, h ":") == 1 { print $1; exit }')
    [ -n "$ADDR" ] || { echo "$NAME 未连接。请先在手机「无线调试」中查看端口，执行 adb connect <IP>:<端口>"; exit 1; }
  fi
  CFG="devices/$NAME.yaml"
}

# start_hub：用设备上已有的程序和配置启动（先结束旧进程）。
# setsid 让 hub 脱离 adb shell 的会话，否则 adb shell 会一直等待它退出而无法返回。
start_hub() {
  timeout 20 adb -s "$ADDR" shell "cd $DIR && pid=\$(pidof z6x-hub); [ -n \"\$pid\" ] && kill \$pid; sleep 1; setsid ./z6x-hub -c hub.yaml > stdout.log 2>&1 < /dev/null &"
  sleep 2
  local host="${ADDR%%:*}" h
  if ! h=$(curl -s -m 5 "http://$host:8090/api/health"); then
    if adb -s "$ADDR" shell "pidof z6x-hub" >/dev/null; then
      echo "== hub 已启动，但当前网络不可信，暂不对外服务（查看：./hub/ctl.sh $NAME status）"
      return 0
    fi
    echo "hub 未运行，查看日志：adb -s $ADDR shell tail $DIR/hub.log"
    return 1
  fi
  echo "== hub 已启动：$(echo "$h" | grep -c '"running"') 个模块运行中，$(echo "$h" | grep -c '"failed"') 个失败"
}
