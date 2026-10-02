#!/system/bin/sh
# 在设备上以 shell 身份启动 z6x-hub 与 keymap（已在运行的不重复启动）。
# 投影仪开机时由 Termux:Boot 通过「本机 ADB」（adb connect 127.0.0.1:5555）调用，也可以从 Deck 手动执行：
#   adb -s 192.168.0.109:5555 shell sh /data/local/tmp/z6x-boot.sh
# 由 deploy.sh 推送到 /data/local/tmp/z6x-boot.sh。
H=/data/local/tmp/z6x-hub
T=/data/local/tmp/z6x-tools
log() { echo "$(date '+%m-%d %H:%M:%S') $*" >> /data/local/tmp/z6x-boot.log; }

if [ -x $H/z6x-hub ] && [ -z "$(pidof z6x-hub)" ]; then
  cd $H && setsid ./z6x-hub -c hub.yaml > stdout.log 2>&1 < /dev/null &
  log "已启动 hub"
else
  log "hub 已在运行或未部署"
fi

if [ -x $T/z6x ] && [ -f $T/keymap.conf ]; then
  if [ -f $T/keymap.pid ] && kill -0 "$(cat $T/keymap.pid)" 2>/dev/null; then
    log "keymap 已在运行"
  else
    cd $T && setsid sh -c 'echo $$ > keymap.pid; exec ./z6x run --name keymap -- ./z6x keymap --daemon --config keymap.conf' > keymap.log 2>&1 < /dev/null &
    log "已启动 keymap"
  fi
fi
