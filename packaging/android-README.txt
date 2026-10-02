z6x-hub 与 z6x：在安卓设备上运行的程序（静态链接的 Linux 程序，无需 root）

z6x-hub   Go 编写的常驻服务：文件管理与 WebDAV、资源库（网页游戏、漫画、多联放映、音声、小说）、
          任务管理、遥控器按键、SSH 等，按配置文件开关模块。
z6x       Rust 编写的命令集：sys、ports、key、keymap、hash、watch、ping、iobench、run、http。

运行方式（两种）：
1. ADB 方式（shell 身份，权限最高；投影仪使用）
     adb push z6x-hub z6x projector.example.yaml /data/local/tmp/z6x-hub/
     复制 projector.example.yaml 为 hub.yaml，把 token 改成随机长字符串，然后：
     adb shell "cd /data/local/tmp/z6x-hub && setsid ./z6x-hub -c hub.yaml > stdout.log 2>&1 < /dev/null &"
   adb-boot.sh 与 projector-boot.sh 用于开机自动启动（Termux:Boot 经本机 ADB 调用）。
2. Termux 方式（普通应用身份；手机使用）
     参考 phone.example.yaml 与 hub.sh：hub start / hub stop / hub status

浏览器打开 http://设备IP:8090 ，输入 token 登录。
完整说明（每一步的原理与实测记录）见桌面工具箱「提案 → 规格」与「手机」专区，
源码与部署脚本：https://github.com/cxy-251/Z6xToolBox
