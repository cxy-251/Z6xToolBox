#!/data/data/com.termux/files/usr/bin/sh
# 投影仪 Termux:Boot 开机脚本（安装到 Termux 的 ~/.termux/boot/z6x-start.sh）。
# 1. 确保 adbd 在运行：极米系统预设了网络 ADB（5555），但 adbd 被停止一次后开机不再自动运行；
#    普通应用身份在 SELinux Permissive 下可以请求 init 启动它（与当初在 SimpleSSHD 中打开 ADB 的方法相同）。
#    adbd 已在运行时 init 会忽略该请求，没有副作用。
# 2. 经本机 ADB（127.0.0.1:5555，ro.adb.secure=0 无需授权）以 shell 身份执行 /data/local/tmp/z6x-boot.sh，
#    启动 z6x-hub 与 keymap。
/system/bin/setprop ctl.start adbd
sleep 20
for i in 1 2 3 4 5 6; do
  adb connect 127.0.0.1:5555 >/dev/null 2>&1
  if adb -s 127.0.0.1:5555 shell sh /data/local/tmp/z6x-boot.sh; then break; fi
  /system/bin/setprop ctl.start adbd
  sleep 10
done
adb disconnect 127.0.0.1:5555 >/dev/null 2>&1
