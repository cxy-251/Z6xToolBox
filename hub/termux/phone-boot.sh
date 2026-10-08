#!/data/data/com.termux/files/usr/bin/sh
# 手机 Termux:Boot 开机脚本（由 deploy.sh 安装到 ~/.termux/boot/z6x-start.sh）：启动 SSH、z6x-hub 与短视频封面生成
sshd
~/z6x-hub/hub.sh start > ~/z6x-hub/boot.log 2>&1
~/z6x-hub/thumbs.sh start >> ~/z6x-hub/boot.log 2>&1
