#!/bin/bash
# 通过网络把一个文件夹导入投影仪上的资源库（边打包边上传，投影仪端边接收边解压）。
# 适合少量内容；大批量请用 U 盘（见资源库页面「存储与转移」）。
#
# 用法：./hub/library-import.sh <本机文件夹> <分类> [资源库 id] [--overwrite]
#   分类：games.rpg、games.slg、media.manga、media.shortvideo.douyin 等（与 omni-deck 的逻辑键相同）
#   资源库 id 省略时使用投影仪上的第一个资源库（优先机身存储）。
# 示例：./hub/library-import.sh ~/Games/omni_library/standalone_games/slg_games/"002 - Summer Sisters" games.slg
set -euo pipefail
cd "$(dirname "$0")"
HOST="${Z6X_HOST:-192.168.0.109}"
SRC="${1:?缺少本机文件夹}"
KEY="${2:?缺少分类，例如 games.rpg}"
LIB="${3:-}"
OVERWRITE=0
[ "${3:-}" = "--overwrite" ] && LIB="" && OVERWRITE=1
[ "${4:-}" = "--overwrite" ] && OVERWRITE=1

[ -d "$SRC" ] || { echo "不是文件夹：$SRC"; exit 1; }
TOKEN=$(grep '^token:' hub.yaml | sed 's/token: *"\(.*\)"/\1/')
AUTH="Authorization: Bearer $TOKEN"
if [ -z "$LIB" ]; then
  LIB=$(curl -sf -H "$AUTH" "http://$HOST:8090/api/library/libs" | grep -m1 '"id"' | sed 's/.*"id": *"\([^"]*\)".*/\1/') || true
  [ -n "$LIB" ] || { echo "投影仪上还没有资源库，请先在资源库页面「存储与转移」中建立。"; exit 1; }
fi

NAME=$(basename "$SRC")
SIZE=$(du -sh "$SRC" | cut -f1)
echo "导入「$NAME」（$SIZE）→ 资源库 $LIB 的 $KEY"
KEY_ENC=$(python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1]))' "$KEY")
tar -C "$(dirname "$SRC")" -cf - "$NAME" \
  | curl --progress-bar -f -H "$AUTH" -T - -X POST \
      "http://$HOST:8090/api/library/import?lib=$LIB&key=$KEY_ENC&overwrite=$OVERWRITE"
echo
