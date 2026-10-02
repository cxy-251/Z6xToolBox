#!/bin/bash
# 编译 z6x-hub 并部署到指定设备，然后启动。投影仪和手机运行同一个程序，各自使用 devices/<设备名>.yaml。
# 运行方式（ADB 或 Termux）见 lib.sh 与 devices/devices.txt。
# 用法：./deploy.sh [设备名或 ADB 地址，默认 projector] [--push-config]
#   ./deploy.sh projector
#   ./deploy.sh phone                       手机需先用 adb connect 连上（无线调试端口会变）
#   ./deploy.sh 192.168.0.104:41235         直接给地址时，按 IP 在 devices/devices.txt 中找到设备名
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

source ./lib.sh
resolve_device "$TARGET"
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

echo "== 部署到 $NAME（$MODE：${ADDR:-$HOST}）"
rsh "mkdir -p $RDIR"
if [ "$PUSH_CONFIG" = 0 ] && rsh "test -f $RDIR/hub.yaml"; then
  get "$RDIR/hub.yaml" "$CFG"
  chmod 600 "$CFG"
  echo "== 使用设备上的现有配置（已同步到本机 $CFG）"
else
  put "$CFG" "$RDIR/hub.yaml"
  echo "== 已推送本机的 $CFG"
fi
# 先传到临时文件再改名：运行中的程序文件不能直接覆盖，改名则不受影响
put z6x-hub "$RDIR/z6x-hub.new"
rsh "mv $RDIR/z6x-hub.new $RDIR/z6x-hub && chmod 755 $RDIR/z6x-hub && chmod 600 $RDIR/hub.yaml && $RDIR/z6x-hub -c $RDIR/hub.yaml -check"
if [ "$MODE" = termux ]; then
  put termux/hub.sh "$RDIR/hub.sh"
  rsh "chmod 755 $RDIR/hub.sh; grep -q 'alias hub=' ~/.bashrc 2>/dev/null || echo 'alias hub=~/$RDIR/hub.sh' >> ~/.bashrc"
fi

echo "== 重启 hub"
start_hub
