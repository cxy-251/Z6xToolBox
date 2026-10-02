#!/bin/bash
# 编译 z6x-hub 并部署到指定设备，然后启动。投影仪和手机运行同一个程序，各自使用 devices/<设备名>.yaml。
# 用法：./deploy.sh [设备名或 ADB 地址，默认 projector] [--push-config]
#   ./deploy.sh projector
#   ./deploy.sh phone                       手机需先用 adb connect 连上（无线调试端口会变）
#   ./deploy.sh 192.168.0.104:41235         直接给地址时，按 IP 在 devices/devices.txt 中找到设备名
# 需要从 ADB 启动：这样 hub 以 shell 身份运行（uid 2000），并继承 adbd 的 oom 分值 -1000。
#
# 配置以设备上的 hub.yaml 为准（可能已在网页配置页中修改过）：
#   - 设备上已有配置时，先把它拉回本机，覆盖本机的 devices/<设备名>.yaml，不会覆盖网页上的修改；
#   - 设备上没有配置（首次部署），或指定 --push-config 时，才把本机的配置推送过去。
set -euo pipefail
cd "$(dirname "$0")"
TARGET="projector"
PUSH_CONFIG=0
for a in "$@"; do
  case "$a" in
    --push-config) PUSH_CONFIG=1 ;;
    *) TARGET="$a" ;;
  esac
done

# 解析设备名和 ADB 地址
if [[ "$TARGET" == *.* ]]; then
  ADDR="$TARGET"
  NAME=$(awk -v h="${TARGET%%:*}" '!/^#/ && NF>=2 { split($2, a, ":"); if (a[1] == h) { print $1; exit } }' devices/devices.txt)
  [ -n "$NAME" ] || { echo "devices/devices.txt 中没有 IP 为 ${TARGET%%:*} 的设备"; exit 1; }
else
  NAME="$TARGET"
  ADDR=$(awk -v n="$NAME" '!/^#/ && $1 == n { print $2; exit }' devices/devices.txt)
  [ -n "$ADDR" ] || { echo "未知设备：$NAME（可选：$(awk '!/^#/ && NF { printf "%s ", $1 }' devices/devices.txt)）"; exit 1; }
fi
if [[ "$ADDR" != *:* ]]; then
  # 只有 IP：找该 IP 当前已连接的端口（手机无线调试每次开启端口都不同）
  ADDR=$(adb devices | awk -v h="$ADDR" '$2 == "device" && index($1, h ":") == 1 { print $1; exit }')
  [ -n "$ADDR" ] || { echo "$NAME 未连接。请先在手机「无线调试」中查看端口，执行 adb connect <IP>:<端口>"; exit 1; }
fi
CFG="devices/$NAME.yaml"
DIR=/data/local/tmp/z6x-hub
export PATH="$HOME/.local/go/bin:$PATH"

if [ ! -f "$CFG" ]; then
  [ -f "devices/$NAME.example.yaml" ] || { echo "缺少配置模板 devices/$NAME.example.yaml"; exit 1; }
  TOKEN=$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 32)
  sed "s/换成随机长字符串/$TOKEN/" "devices/$NAME.example.yaml" > "$CFG"
  chmod 600 "$CFG"
  echo "已生成 $CFG 并写入随机 token（只保存在本机，登录时用 grep token $CFG 查看）"
fi

VERSION=$(git describe --always --dirty 2>/dev/null || echo dev)
echo "== 编译 $VERSION"
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath \
  -ldflags="-s -w -X z6x/hub/internal/core.Version=$VERSION" -o z6x-hub ./cmd/z6x-hub

echo "== 部署到 $NAME（$ADDR）"
adb -s "$ADDR" shell "mkdir -p $DIR"
if [ "$PUSH_CONFIG" = 0 ] && adb -s "$ADDR" shell "test -f $DIR/hub.yaml"; then
  adb -s "$ADDR" pull "$DIR/hub.yaml" "$CFG" >/dev/null
  chmod 600 "$CFG"
  echo "== 使用设备上的现有配置（已同步到本机 $CFG）"
else
  adb -s "$ADDR" push "$CFG" "$DIR/hub.yaml" >/dev/null
  echo "== 已推送本机的 $CFG"
fi
adb -s "$ADDR" push z6x-hub "$DIR/" >/dev/null
adb -s "$ADDR" shell "chmod 755 $DIR/z6x-hub && chmod 600 $DIR/hub.yaml && $DIR/z6x-hub -c $DIR/hub.yaml -check"

echo "== 重启 hub"
# setsid 让 hub 脱离 adb shell 的会话，否则 adb shell 会一直等待它退出而无法返回。
timeout 20 adb -s "$ADDR" shell "cd $DIR && pid=\$(pidof z6x-hub); [ -n \"\$pid\" ] && kill \$pid; sleep 1; setsid ./z6x-hub -c hub.yaml > stdout.log 2>&1 < /dev/null &"
sleep 2
HOST="${ADDR%%:*}"
H=$(curl -s -m 5 "http://$HOST:8090/api/health") || { echo "hub 未响应，查看日志：adb -s $ADDR shell cat $DIR/hub.log"; exit 1; }
echo "== hub 已启动：$(echo "$H" | grep -c '"running"') 个模块运行中，$(echo "$H" | grep -c '"failed"') 个失败"
