#!/bin/bash
# 编译 z6x-hub 并部署到投影仪，然后启动。用法：./deploy.sh [投影仪地址，默认 192.168.0.109:5555]
# 需要从 ADB 启动：这样 hub 以 shell 身份运行（uid 2000），并继承 adbd 的 oom 分值 -1000。
set -euo pipefail
cd "$(dirname "$0")"
DEV="${1:-192.168.0.109:5555}"
DIR=/data/local/tmp/z6x-hub
export PATH="$HOME/.local/go/bin:$PATH"

if [ ! -f hub.yaml ]; then
  TOKEN=$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 32)
  sed "s/换成随机长字符串/$TOKEN/" hub.example.yaml > hub.yaml
  chmod 600 hub.yaml
  echo "已生成 hub.yaml，token 为：$TOKEN（只保存在本机 hub.yaml 中）"
fi

VERSION=$(git describe --always --dirty 2>/dev/null || echo dev)
echo "== 编译 $VERSION"
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath \
  -ldflags="-s -w -X z6x/hub/internal/core.Version=$VERSION" -o z6x-hub ./cmd/z6x-hub

echo "== 部署到 $DEV"
adb -s "$DEV" shell "mkdir -p $DIR"
adb -s "$DEV" push z6x-hub hub.yaml "$DIR/" >/dev/null
adb -s "$DEV" shell "chmod 755 $DIR/z6x-hub && chmod 600 $DIR/hub.yaml && $DIR/z6x-hub -c $DIR/hub.yaml -check"

echo "== 重启 hub"
# setsid 让 hub 脱离 adb shell 的会话，否则 adb shell 会一直等待它退出而无法返回。
timeout 20 adb -s "$DEV" shell "cd $DIR && pid=\$(pidof z6x-hub); [ -n \"\$pid\" ] && kill \$pid; sleep 1; setsid ./z6x-hub -c hub.yaml > stdout.log 2>&1 < /dev/null &"
sleep 2
HOST="${DEV%%:*}"
curl -s -m 5 "http://$HOST:8090/api/health" || { echo "hub 未响应，查看日志：adb shell cat $DIR/hub.log"; exit 1; }
