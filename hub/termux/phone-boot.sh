#!/data/data/com.termux/files/usr/bin/sh
# 手机 Termux:Boot 开机脚本（由 deploy.sh 安装到 ~/.termux/boot/z6x-start.sh）：启动 SSH 与 z6x-hub。
# 短视频封面生成等后台任务由 hub 的「后台任务」模块按「开机自动启动」的设置启动
sshd
~/z6x-hub/hub.sh start > ~/z6x-hub/boot.log 2>&1
# 不退出：开机脚本是 Termux 的一个后台任务，任务结束后 Termux 的服务随之退出，应用变为可回收，
# 系统会连同上面启动的进程一起结束（2026-10-08 手机重启实测：开机脚本已执行，几分钟内 hub 与封面生成都被结束）。
# 一直挂着让服务保持运行（通知栏显示 Termux 有 1 个任务）。不用唤醒锁：持有唤醒锁约一小时会被 HyperOS 判为异常耗电而结束。
echo "$(date '+%F %T') 开机启动完成，保持运行" >> ~/z6x-hub/boot.log
exec sleep 2147483647
