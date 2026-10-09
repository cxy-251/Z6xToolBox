z6x-hub 与 z6x：在安卓设备上运行的程序（静态链接的 Linux 程序，无需 root；arm64 适用于大多数设备，arm 为只有 32 位用户空间的设备）

一、z6x-hub（Go，常驻服务）
  浏览器访问 http://设备IP:8090 ，用配置中的 password 登录（设备自己的浏览器可用 http://127.0.0.1:8090，不连网络也能用）。
  首页为资源页，按标签分类、首次点开才加载：
    游戏（RPG Maker、SLG 网页游戏）、短视频（推荐、博主置顶、播放器、图集自动翻页）、多联放映、音声（专辑封面）、漫画、小说、
    工具（文件共享、系统状态、测速、局域网扫描、网络唤醒、发送文字、后台任务、AirPlay 音箱、Clash 白名单等，按配置开启）、
    存储与转移、设置（网络、可调参数、系统状态）。
  资源库与 omni-deck 的目录结构兼容（omni_library/）。每台设备一份配置 hub.yaml，参考 *.example.yaml。

  运行方式（同一个程序，按运行身份能用的功能不同）：
  1. ADB 方式（shell 身份，权限最高；投影仪使用）
       adb push z6x-hub projector.example.yaml /data/local/tmp/z6x-hub/
       复制 projector.example.yaml 为 hub.yaml，把 token 改成随机长字符串、password 改成自己的密码，然后：
       adb shell "cd /data/local/tmp/z6x-hub && setsid ./z6x-hub -c hub.yaml > stdout.log 2>&1 < /dev/null &"
     开机自动启动：adb-boot.sh 推送到 /data/local/tmp/z6x-boot.sh，projector-boot.sh 放到 Termux 的 ~/.termux/boot/（需安装 Termux:Boot）。
  2. Termux 方式（普通应用身份；手机使用）
       hub.sh start | stop | status | log ；开机自动启动：phone-boot.sh 放到 ~/.termux/boot/z6x-start.sh
     后台任务：thumbs.sh（短视频封面生成，依赖 pkg install python ffmpeg）由 hub 的「后台任务」开关。

  局限：
  - 普通应用身份（Termux）读不到 CPU 使用率、网络流量等（系统状态页会标明），遥控、任务管理、通知等需要 shell 身份的功能不可用。
  - 手机息屏时 CPU 会休眠：hub 网页仍可访问（网络数据会唤醒），但 AirPlay 音箱在长时间息屏后可能搜不到，亮屏即恢复。
  - AirPlay 音箱只支持 AirPlay 1 的音频，约 2 秒延迟，不支持视频与屏幕镜像；需要 Termux 的 PulseAudio（pkg install pulseaudio），
    hub 以 shell 身份运行时设 run_as_termux（需设备允许 shell 使用 run-as）。
  - 安卓可能销毁应用的监听端口，hub 会在 15 秒内自动重新监听；只在 network.trusted 中的 Wi-Fi 上对外服务。

二、z6x（Rust，命令集，形式同 BusyBox）
  z6x sys | ports | key | keymap | hash | watch | ping | iobench | run | http ，每个子命令支持 --help。
  keymapd.sh：遥控器改键守护进程的开关脚本，供 hub 的「后台任务」调用（投影仪）。
  局限：按键注入、遥控器改键、端口所属查询需要 shell 身份（ADB）；hash、ping、iobench、watch、run、http 在 Termux 中也可用。
  ping 只接受 IP 地址（静态程序在安卓上无法解析域名）。

完整说明（每一步的原理与实测记录）见桌面工具箱「提案 → 规格」与「手机」专区。
源码与部署脚本：https://github.com/cxy-251/Z6xToolBox
