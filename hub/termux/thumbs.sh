#!/data/data/com.termux/files/usr/bin/bash
# 在手机的 Termux 中生成短视频封面（scripts/shortvideo_thumbs.py），由 deploy.sh 放到 ~/z6x-hub/，别名 thumbs。
# 用法：thumbs start | stop | status | log
#   start  在后台循环运行：跑完一轮（已有封面的跳过，只补新作品）后等待一段时间再跑；开机时由 Termux:Boot 启动
#          参数（同时运行数、温度阈值、封面宽度、间隔）在 hub.yaml 的 library.thumbs 中配置，每轮开始时读取
#   stop   停止（正在生成的封面不完整的会在下次启动时清理）
#   status 是否在运行，以及最近一轮的进度
#   log    最近的日志（每行带时间；失败的文件会逐个写明）
# 按机身温度自动调速、过热暂停（阈值见 library.thumbs），见脚本说明。依赖：pkg install python ffmpeg
cd "$(dirname "$0")"
pid() { local p; p=$(cat thumbs.pid 2>/dev/null) && [ -r "/proc/$p/cmdline" ] && grep -q thumbs.sh "/proc/$p/cmdline" && echo "$p"; }

case "${1:-status}" in
  start)
    if p=$(pid); then echo "封面生成已在运行（PID $p）"; exit 0; fi
    command -v ffmpeg >/dev/null && command -v python >/dev/null || { echo "缺少 ffmpeg 或 python：pkg install python ffmpeg"; exit 1; }
    # setsid：自成一个进程组，stop 时连同正在运行的 python 与 ffmpeg 一起结束
    setsid nohup "$0" loop > /dev/null 2>&1 < /dev/null &
    echo $! > thumbs.pid
    sleep 1
    if p=$(pid); then echo "封面生成已启动（PID $p），日志：thumbs log"; else echo "启动失败"; exit 1; fi ;;
  loop)
    while true; do
      # 每轮读取一次配置：改了 hub.yaml 后下一轮即生效
      SETTINGS=$(./z6x-hub -c hub.yaml -thumbs-config 2>>thumbs.log) || SETTINGS='{}'
      HOURS=$(python -c 'import json,sys;print(json.loads(sys.argv[1]).get("interval_hours",6))' "$SETTINGS")
      echo "======== $(date '+%m-%d %H:%M:%S') 开始一轮，参数 $SETTINGS" >> thumbs.log
      # 运行中每半分钟重新读取参数（并发、温度阈值），改设置后无须等到下一轮
      python shortvideo_thumbs.py --settings "$SETTINGS" --settings-cmd "./z6x-hub -c hub.yaml -thumbs-config" >> thumbs.log 2>&1
      echo "======== $(date '+%m-%d %H:%M:%S') 本轮结束（退出码 $?），$HOURS 小时后再检查新作品" >> thumbs.log
      INTERVAL=$(python -c 'import sys;print(int(float(sys.argv[1])*3600))' "$HOURS")
      # 日志只保留最近 2000 行
      tail -2000 thumbs.log > thumbs.log.tmp && mv thumbs.log.tmp thumbs.log
      sleep "$INTERVAL"
    done ;;
  stop)
    if p=$(pid); then kill -- -"$p" 2>/dev/null || kill "$p"; echo "封面生成已停止"; else echo "封面生成本来就没有运行"; fi
    rm -f thumbs.pid ;;
  status)
    if p=$(pid); then echo "封面生成运行中（PID $p）"; else echo "封面生成未运行"; fi
    grep -E "开始|\[|完成|本轮结束" thumbs.log 2>/dev/null | tail -3
    [ -n "$p" ] || exit 1 ;;
  log)
    tail -30 thumbs.log ;;
  *)
    echo "用法：thumbs start | stop | status | log"; exit 1 ;;
esac
