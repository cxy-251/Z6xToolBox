#!/system/bin/sh
# 在设备上以 shell 身份启动 z6x-hub（已在运行的不重复启动）。遥控器改键守护进程等后台任务由 hub 的
# 「后台任务」模块按「开机自动启动」的设置启动（hub 启动约 15 秒后），这里不再单独启动。
# 投影仪开机时由 Termux:Boot 通过「本机 ADB」（adb connect 127.0.0.1:5555）调用，也可以从 Deck 手动执行：
#   adb -s 192.168.0.109:5555 shell sh /data/local/tmp/z6x-boot.sh
# 由 deploy.sh 推送到 /data/local/tmp/z6x-boot.sh。
H=/data/local/tmp/z6x-hub
log() { echo "$(date '+%m-%d %H:%M:%S') $*" >> /data/local/tmp/z6x-boot.log; }

if [ -x $H/z6x-hub ] && [ -z "$(pidof z6x-hub)" ]; then
  cd $H && setsid ./z6x-hub -c hub.yaml > stdout.log 2>&1 < /dev/null &
  log "已启动 hub"
else
  log "hub 已在运行或未部署"
fi

