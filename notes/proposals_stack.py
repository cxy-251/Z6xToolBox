# 每个提案的技术栈、参考项目、发展方向（Claude 的表述）。
# S[(语言, 编号)] = (技术栈, 参考项目, 发展方向)
# 技术栈不写具体版本号和体积：没有实际编译过，写了就是猜测。参考项目只列确实存在、和本方案相关的。
S = {}

# ---------------- Go ----------------
S['G',1] = ("Go 标准库 net/http + golang.org/x/net/webdav；CGO_ENABLED=0 静态编译为单个可执行文件。",
 "rclone serve webdav、dufs（成熟的 WebDAV 服务，可对照行为和兼容性）。",
 "作为 hub 第一期的 files 模块实现，同时提供 HTTP 下载和上传接口。以后的相册、阅读器、Deck 备份都建立在它之上。")
S['G',2] = ("aria2c（C++ 编写的命令行下载器，需要静态编译的 ARM 版本）+ JSON-RPC 接口 + AriaNg 网页前端。",
 "aria2、AriaNg。",
 "不在 hub 里重写下载器。等有离线下载需求时，把现成的静态 aria2c 作为外部程序部署，hub 只负责启动它和提供入口。")
S['G',3] = ("mosdns 或 AdGuard Home（两者都是 Go 编写，可静态编译）。",
 "mosdns、AdGuard Home。",
 "不做。DNS 分流和去广告由 Clash 负责；即使以后要做，也受限于 53 端口无法绑定，需要路由器配合转发。")
S['G',4] = ("Go 后端（扫描目录、HTTP Range 输出视频）+ 单页网页（CSS Grid 布局）+ 电视上的浏览器负责解码播放。",
 "无直接对应的项目。",
 "第三期候选。前提是先在电视浏览器里实测能同时硬解几路视频；不满足就不做。")
S['G',5] = ("Navidrome（Go 编写的音乐服务器，内置 SQLite）+ 手机端 Subsonic 协议客户端。",
 "Navidrome。",
 "不进 hub。需要时按 Go 测试服务的方式单独部署官方二进制，hub 的首页可以放一个链接。")
S['G',6] = ("Go net/http 接口 + 调用系统的 input、am 命令。",
 "Home Assistant 的 RESTful Command 集成（调用方）。",
 "已并入 hub 第一期的 control 模块。切换 HDMI 信号源的方法找到后再加接口。")
S['G',7] = ("安卓电源管理（PowerManager、DeviceIdleController）+ settings global 设置。",
 "无。",
 "已完成实测，结论写进 hub 规格：「关屏」时服务持续在线，「关机」时进入睡眠、开机后自动恢复。hub 要能承受被冻结一段时间再继续运行。")
S['G',8] = ("Go net/http + 纯 Go 图片处理库 disintegration/imaging（不依赖 CGO）+ EXIF 解析。",
 "FileBrowser、PhotoPrism（可参考目录浏览和按时间组织的方式）。",
 "第三期候选，作为 files 模块的扩展（缩略图 + 按时间浏览）。缩图并发限制在 2 以内。")
S['G',9] = ("投影仪端：hub files 模块的上传接口；Deck 端：Bash 脚本（tar 打包 + curl 上传）+ systemd 定时器。",
 "restic、Syncthing（增量备份和同步的成熟方案）。",
 "第一期由 files 的上传接口支撑。Deck 端的备份脚本放在本工具箱里，作为一篇操作指南。")
S['G',10] = ("Go 读取 /proc 和 /sys 的文本 + time.Ticker 定时采样 + JSON 输出。",
 "Prometheus node_exporter（指标命名和 /proc 解析方式）。",
 "hub 第一期的 metrics 模块。同时输出 Prometheus 文本格式（见「Prometheus 监控接口」）。")
S['G',11] = ("Go net/http + 内存中的消息列表（加锁的切片）+ 调用 input text 和 am start。",
 "microbin、PrivateBin（网页粘贴板）。",
 "hub 第一期的 paste 模块。中文输入受 input text 限制，以后可以通过安装支持广播输入的输入法解决。")
S['G',12] = ("Go net/http + goroutine 并发检测直播源可用性 + M3U 解析。",
 "iptv-org/iptv（公开的直播源列表）。",
 "第三期候选，和「IPTV 列表清洗」「节目单生成」合成一个直播模块。")
S['G',13] = ("Go 标准库 archive/zip 读取 CBZ 漫画 + 单页网页阅读器。",
 "Kavita、Komga（漫画和电子书服务器）。",
 "第三期候选，作为 files 模块的扩展。")
S['G',14] = ("Go net/http（下载、上传、往返延迟三项测试）+ ICMP ping（普通身份可用）。",
 "LibreSpeed（网页测速的前后端实现）。",
 "hub 第一期的 speed 模块。iperf3 兼容测试作为第二期可选项。")
S['G',15] = ("Go net/http + WebSocket（gorilla/websocket 或 nhooyr.io/websocket）+ 系统 input 命令；触控板需要通过 /dev/uinput 注入虚拟鼠标。",
 "scrcpy（按键和触摸的事件模型）。",
 "第一期在 control 模块实现网页按键和宏；第二期验证虚拟鼠标后再加触控板，按键延迟不满足时改用 z6x-tools 的 key 命令。")
S['G',16] = ("Go 实现 UPnP MediaRenderer：SSDP 发现（UDP 1900 组播）+ 设备描述 XML + AVTransport 的 SOAP 接口；收到链接后用 am start 交给播放器。",
 "anacrolix/dms（Go 编写的 DLNA 媒体服务器）、gmrender-resurrect（C 编写的 DLNA 渲染端）。",
 "第二期的 dlna 模块。前提：先装好播放器并设为视频的默认打开方式，并处理 UDP 1900 被系统占用的问题。")
S['G',17] = ("Go 标准库 net：构造魔术包（6 个 0xFF + 16 次 MAC 地址）并 UDP 广播；定时 ping 判断在线状态。",
 "wakeonlan、etherwake（命令行唤醒工具）。",
 "hub 第一期的 wol 模块，同时包含跨网段唤醒。")
S['G',18] = ("Go net/http + 系统命令 cmd notification post。",
 "Gotify（自建通知服务）。",
 "第二期的 notify 模块。先验证电视的系统界面会不会显示通知；不显示就放弃。")
S['G',19] = ("原方案：Go 接收音频流后直接写声卡。",
 "Snapcast、shairport-sync。",
 "不可行。命令行进程碰不到声卡；如果要做，只能做成安卓 App，用系统的音频接口播放。")
S['G',20] = ("Go + creack/pty（伪终端）+ WebSocket + 网页端 xterm.js。",
 "ttyd、GoTTY。",
 "第二期的 webshell 模块，默认关闭、必须鉴权。")
S['G',21] = ("SMB 服务端。go-smb2 只是客户端库，Go 生态里没有成熟的 SMB 服务端，实际只能用 Samba 等 C 实现。",
 "Samba。",
 "不做。Windows 资源管理器只连 445 端口，而 1024 以下的端口不能绑定；文件共享由 WebDAV 覆盖。")
S['G',22] = ("Go + fsnotify（监听目录变化）+ TMDB 接口（需要外网）。",
 "tinyMediaManager、Jellyfin 的元数据刮削。",
 "不做。依赖外网，收益低；如果以后用 Kodi 或 Jellyfin 这类播放器，它们自带刮削。")
S['G',23] = ("Go + mochi-mqtt/server（纯 Go 的 MQTT 服务端库）。",
 "Mosquitto、mochi-mqtt。",
 "第三期候选。家里出现 MQTT 设备时再做，作为 hub 的一个模块。")
S['G',24] = ("Go 标准库 net：TCP 双向转发 + SOCKS5 协议解析。",
 "armon/go-socks5。",
 "不做。Clash 已经在局域网上提供代理（这本身还是一个需要收紧的安全问题，见「安全检查」页）。")
S['G',25] = ("Go + mmcdole/gofeed（RSS/Atom 解析）+ bbolt（记录已处理的条目）。",
 "gofeed、Miniflux。",
 "不做，收益低。")
S['G',26] = ("Go 实现 S3 协议的一个子集 + AWS Signature V4 签名校验。",
 "MinIO、SeaweedFS。",
 "不做。WebDAV 已覆盖，备份类工具大多也支持 WebDAV。")
S['G',27] = ("Go + huin/goupnp（UPnP IGD 客户端），向路由器申请端口映射。",
 "miniupnpc。",
 "不做：会把投影仪上的服务暴露到公网。")
S['G',28] = ("Go + prometheus/client_golang，或直接手写 Prometheus 文本格式。",
 "node_exporter。",
 "并入 metrics 模块，路径为 /metrics。")
S['G',29] = ("Go net/http 中间件：Bearer token 校验。原方案的反向代理 + JWT 对单进程 hub 来说过重。",
 "oauth2-proxy。",
 "简化为 hub core 的 token 鉴权，第一期实现。")
S['G',30] = ("Go + go-git（纯 Go 的 Git 实现），或直接部署 Gitea。",
 "Gitea、Soft Serve。",
 "不做，收益低。")
S['G',31] = ("原方案：Go 标准库 net/rpc/jsonrpc（它实现的是 JSON-RPC 1.0，并非 2.0）。",
 "Kodi 的 JSON-RPC 接口设计。",
 "并入 control 模块，改用更简单的 REST 接口。")
S['G',32] = ("视频切片需要转封装工具（ffmpeg）；原方案选的 gortsplib 是 RTSP 库，不负责 HLS 切片。",
 "MediaMTX、ffmpeg。",
 "不做。本地视频直接用 HTTP Range 点播即可。")
S['G',33] = ("tailscale.com/derp（Tailscale 的中继协议）+ TLS 证书。",
 "derper、Headscale。",
 "不做：需要公网可达和证书，本项目不对公网开放。")
S['G',34] = ("Go net/http + os/exec（参数以数组传入，不拼接 shell 字符串）。",
 "adnanh/webhook。",
 "并入 control 模块：只允许执行配置里预先定义的动作。")
S['G',35] = ("Go + golang.org/x/crypto/ssh + pkg/sftp。",
 "SFTPGo。",
 "不做。WebDAV 和现有 SSH 已覆盖；如果以后部署 Dropbear，它自带 scp。")
S['G',36] = ("Go + insomniacslk/dhcp（DHCP 协议库），需要监听 UDP 67。",
 "CoreDHCP、dnsmasq。",
 "不可行：67 端口低于 1024，不能绑定。")
S['G',37] = ("Go + 各 DNS 服务商的 API。",
 "ddns-go。",
 "不做：本项目不对公网开放。如果需要，直接单独部署 ddns-go。")
S['G',38] = ("Go net/http 输出 PAC 文件（一段 JavaScript 规则）。",
 "gfwlist2pac。",
 "不做，分流由 Clash 负责。")
S['G',39] = ("Go + anacrolix/torrent（BitTorrent 和 DHT 协议实现）。",
 "anacrolix/torrent。",
 "不做，收益低；离线下载方案里的 aria2 也能直接处理磁力链接。")
S['G',40] = ("原方案：Go 实现 RTSP 状态机去修改 Miracast 信令。",
 "MiracleCast（Linux 上的 Miracast 实现）。",
 "不可行：Miracast 由系统投屏应用通过 Wi-Fi 直连建立，外部进程无法介入。")
S['G',41] = ("原方案：Go 分发音频流 + FLAC 编码。",
 "Snapcast。",
 "不可行：命令行进程既拿不到音频来源，也无法播放。")
S['G',42] = ("Go net/http + 视频文件哈希（字幕站常用文件特征匹配）+ 字幕站 API。",
 "ChineseSubFinder、subliminal。",
 "不做：依赖外网字幕站。")
S['G',43] = ("Go 标准库 log/slog + 文件轮转 + 查看日志的 HTTP 接口。",
 "Grafana Loki（多机日志聚合，对单进程来说过重）。",
 "简化为 hub core 的日志功能，第一期实现。")
S['G',44] = ("Go + brutella/hap（HomeKit 配件协议实现）+ mDNS 广播。",
 "Homebridge、brutella/hap。",
 "第三期候选。前提是解决 UDP 5353 与系统 mdnsd 的共存问题。")
S['G',45] = ("Go + showwin/speedtest-go（Speedtest 协议客户端）+ 本地记录。",
 "speedtest-tracker。",
 "不做：定时测速持续消耗流量。")
S['G',46] = ("Go 标准库 net：向目标网段的广播地址（如 192.168.1.255）发魔术包，需要 SO_BROADCAST。",
 "wakeonlan。",
 "并入 wol 模块。")
S['G',47] = ("Go + disintegration/imaging（纯 Go）；WebP 输出需要 CGO 绑定 libwebp，静态编译不方便，用 JPEG 即可。",
 "imgproxy、imagor。",
 "随相册一起，作为第三期候选。")
S['G',48] = ("Go net/http + M3U 文本解析与去重。",
 "iptv-org/iptv。",
 "第三期候选，并入「直播源整理」。")
S['G',49] = ("实现 iperf3 的控制协议（TCP 5201），让现成的 iperf3 客户端可以连接。",
 "iperf3。",
 "第二期可选，作为 speed 模块的补充。")
S['G',50] = ("AList（Go 编写，自带 WebDAV 和多种网盘驱动）。",
 "AList、rclone。",
 "不重写。需要时单独部署 AList。")
S['G',51] = ("Go + go-acme/lego（ACME 协议客户端，自动申请 Let's Encrypt 证书）。",
 "lego、certbot。",
 "不做：本项目不对公网开放。")
S['G',52] = ("Go：调用直播平台接口拿到流地址，再用 HTTP 拉流写文件。",
 "BililiveRecorder。",
 "不做，收益低。")
S['G',53] = ("Go + koron/go-ssdp：发送 M-SEARCH 组播，解析设备描述 XML。",
 "gssdp。",
 "第二期的 lanscan 模块。只发送、不绑定 1900 端口。")
S['G',54] = ("Go + MQTT 客户端（eclipse/paho.mqtt.golang）+ 时序数据存储。",
 "InfluxDB、VictoriaMetrics。",
 "不做，没有需求。")
S['G',55] = ("tailscale.com/tsnet（以用户态网络方式嵌入 Tailscale，不需要 TUN 设备）。",
 "Tailscale。",
 "第三期候选。以后需要在外面访问家里时，这是首选方案；先验证用户态模式能否在这台机器上运行。")
S['G',56] = ("Go + pion/webrtc + WebSocket 信令。",
 "Pion、LiveKit。",
 "不做：电视端要用浏览器解码显示，发起端要求 HTTPS，整体复杂。")
S['G',57] = ("Go + cespare/xxhash：按大小分组，再比对哈希。",
 "jdupes、rmlint。",
 "并入 z6x-tools 的 hash 命令（Rust 实现），只报告、不自动删除或硬链接。")
S['G',58] = ("Go html/template 生成表单页面，修改后写回 YAML 并重新加载。",
 "无直接对应的项目。",
 "第一期只读展示当前配置；第二期允许在网页上修改。")
S['G',59] = ("Go encoding/xml 生成 XMLTV 节目单。",
 "XMLTV 格式规范。",
 "第三期候选，并入「直播源整理」。")
S['G',60] = ("Go：单进程 + context.Context 管理各模块生命周期 + 一个 YAML 配置文件。",
 "Caddy（模块化、按配置启用功能的单进程服务）。",
 "已采纳为 z6x-hub 的整体架构，详见 hub 规格。")

# ---------------- Rust ----------------
S['R',1] = ("Rust 标准库（std::process、libc::kill），编译目标 aarch64-unknown-linux-musl（静态链接）。",
 "tini、daemontools。",
 "并入 z6x-tools 的 run 命令，第二期实现。")
S['R',2] = ("Rust + evdev crate：读取遥控器事件，通过 uinput 输出重映射后的按键。",
 "kanata、keyd。",
 "z6x-tools 的 keymap 命令，第一期实现。这是 tools 里唯一需要常驻的命令。")
S['R',3] = ("原方案：Rust + alsa crate 直接写声卡 + rtrb 无锁环形缓冲。",
 "cpal、tinyalsa。",
 "不可行：shell 不在 audio 组，碰不到声卡。")
S['R',4] = ("Rust + xxhash-rust（xxh3 算法）+ memmap2（内存映射读文件）。",
 "czkawka、jdupes。",
 "z6x-tools 的 hash 命令，第一期实现；同时承担「重复文件查找」。")
S['R',5] = ("Rust + boringtun（用户态 WireGuard）+ smoltcp（用户态 TCP/IP 协议栈）。",
 "boringtun、wireguard-go。",
 "不做：Clash 已提供 VPN。")
S['R',6] = ("Rust + memmap2 + zerocopy（把文件字节直接当结构体读取）。",
 "LMDB / heed。",
 "不做，没有需求。")
S['R',7] = ("原方案：Rust + btleplug 或 AF_BLUETOOTH 原始套接字。",
 "Theengs Gateway。",
 "不可行：内核里没有 hci 蓝牙设备。")
S['R',8] = ("Rust：固定大小的环形缓冲，异常时写盘。",
 "Linux pstore/ramoops。",
 "不做：内核日志读不到，logcat 已有 crash 缓冲区。")
S['R',9] = ("原方案：Rust + pcap 捕获未开放端口的数据包。",
 "knockd。",
 "不可行：需要原始套接字权限。")
S['R',10] = ("Rust + rustls（TLS）+ hyper/h2（HTTP/2）+ hickory-dns（原名 trust-dns）。",
 "dnscrypt-proxy、hickory-dns。",
 "不做：53 端口不能绑定，Clash 已提供加密 DNS。")
S['R',11] = ("Rust + zstd crate（绑定官方 C 库）+ tar crate。",
 "zstd、tar-rs。",
 "z6x-tools 的 pack 命令，第二期实现；LZ4 解压一并放进来。")
S['R',12] = ("Rust + aho-corasick crate（多模式匹配）+ 按大小轮转的文件写入。",
 "Vector。",
 "不做。更简单的做法是服务自己不写敏感信息。")
S['R',13] = ("Rust + tokio-tungstenite + tokio::sync::broadcast。",
 "Centrifugo。",
 "不单独做：需要时在 hub 里用 Go 实现。")
S['R',14] = ("Rust 标准库读取 /proc、/sys 文本。",
 "procfs crate、node_exporter。",
 "z6x-tools 的 sys 命令，第一期实现；包含温度监控。")
S['R',15] = ("Rust + libc（O_DIRECT 直接读写、对齐内存分配）。",
 "fio、f3（检测 U 盘真实容量）。",
 "并入 z6x-tools 的 iobench 命令，第二期实现。")
S['R',16] = ("Rust 令牌桶限速（可用 governor crate）。",
 "governor。",
 "不做；以后可作为 files 模块的限速选项。")
S['R',17] = ("Rust + hyper + tokio + dav-server crate。",
 "dufs（Rust 编写的文件服务，支持 WebDAV）。",
 "并入 hub 的 files 模块（Go 实现），不重复做。")
S['R',18] = ("Rust + libc：直接向 /dev/input/eventN 写入按键事件，或通过 /dev/uinput 创建虚拟键盘。",
 "evemu、uinput。",
 "z6x-tools 的 key 命令，第一期实现；hub 的 control 模块在 input 命令延迟不够时调用它。")
S['R',19] = ("Rust + tokio::process + 信号处理。",
 "supervisord、s6、runit。",
 "与「进程看门狗」合并为 z6x-tools 的 run 命令，第二期实现。")
S['R',20] = ("原方案：Rust + AF_PACKET 原始套接字 + etherparse。",
 "netsniff-ng。",
 "不可行：需要 CAP_NET_RAW。需要抓包时在 Deck 上抓。")
S['R',21] = ("Rust + rumqttd。",
 "rumqttd、NanoMQ。",
 "不做：与 Go 版 MQTT 重复。")
S['R',22] = ("Rust + sendfile 系统调用（nix crate）+ 简单的 HTTP/1.1 实现。",
 "miniserve。",
 "并入 hub 的 files 模块，不重复做。")
S['R',23] = ("Rust + serialport crate + termios。",
 "serialport-rs、ser2net。",
 "不可行：没有串口设备节点，串口通常只有 root 能读写。")
S['R',24] = ("原方案：Rust + TUN 设备 + smoltcp。",
 "tun2socks。",
 "不可行：/dev/net/tun 对 shell 不可访问。")
S['R',25] = ("原方案：Rust + tinyalsa 读取录音设备。",
 "tinyalsa。",
 "不可行：声卡不可访问。")
S['R',26] = ("Rust + tokio：根据连接的第一个字节区分 SOCKS5 和 HTTP。",
 "shadowsocks-rust、tinyproxy。",
 "不做：Clash 已提供混合端口。")
S['R',27] = ("Rust + rusqlite（启用 FTS5 全文索引）+ walkdir（遍历目录）。",
 "rusqlite、ripgrep。",
 "不做，暂无需求。")
S['R',28] = ("Rust：时间轮 + timerfd。",
 "tokio 的定时器实现。",
 "不做：hub 内部的定时功能即可。")
S['R',29] = ("Rust 标准库定时读取 /sys/class/thermal。",
 "lm-sensors。",
 "并入 z6x-tools 的 sys 命令。")
S['R',30] = ("Rust + reed-solomon-erasure（前向纠错）+ socket2。",
 "KCP。",
 "不做：需要发送端配合，没有使用方。")
S['R',31] = ("Rust + inotify crate。",
 "inotify-tools、watchexec。",
 "z6x-tools 的 watch 命令，第一期实现。")
S['R',32] = ("原方案：Rust + memmap2 读取 /dev/graphics/fb0。",
 "fbcat。",
 "不可行：fb0 属于 graphics 组。截图用 adb exec-out screencap。")
S['R',33] = ("Rust + memmap2 + 原子变量（core::sync::atomic）。",
 "无直接对应的项目。",
 "不做，没有需求。")
S['R',34] = ("Rust + socket2：普通身份的 ICMP 套接字（SOCK_DGRAM + IPPROTO_ICMP）+ 单调时钟计时。",
 "mtr、gping。",
 "z6x-tools 的 ping 命令，第一期实现。")
S['R',35] = ("Rust + mdns-sd crate。",
 "Avahi。",
 "第三期候选。前提是解决与系统 mdnsd 争用 UDP 5353 的问题。")
S['R',36] = ("Rust + libc（O_DIRECT、posix_memalign）+ std::time::Instant 计时。",
 "fio。",
 "z6x-tools 的 iobench 命令，第二期实现。")
S['R',37] = ("Rust + nix 的 pty 功能 + Unix 域套接字。",
 "tmux、abduco。",
 "第三期候选。也可以直接部署静态编译的 tmux。")
S['R',38] = ("Rust + lz4_flex（纯 Rust 的 LZ4 实现）。",
 "lz4。",
 "并入 z6x-tools 的 pack 命令。")
S['R',39] = ("原方案：Rust + Linux CEC 接口（ioctl /dev/cec0）。",
 "cec-ctl、libcec。",
 "不可行：没有 /dev/cec0。")
S['R',40] = ("原方案：Rust + alsa 混音器控件接口。",
 "amixer。",
 "不可行：声卡控制节点不可访问。")
S['R',41] = ("Rust：写 /dev/hidg0（USB 设备模式）或 /dev/uinput。",
 "Linux 内核文档中的 hid gadget 示例。",
 "/dev/hidg0 不存在；uinput 部分并入 z6x-tools 的 key 命令。")
S['R',42] = ("Rust + hickory-dns + tokio。",
 "SmartDNS。",
 "不做：53 端口不能绑定，也无法把系统 DNS 指向它。")
S['R',43] = ("Rust + tungstenite + nix pty + 内嵌网页资源。",
 "ttyd。",
 "并入 hub 的 webshell（Go 实现），不重复做。")
S['R',44] = ("Rust + memmap2 + 原子变量实现单生产者单消费者环形队列。",
 "rtrb、LMAX Disruptor。",
 "不做，没有需求。")
S['R',45] = ("原方案：Rust + AF_BLUETOOTH 原始套接字抓 HCI 包。",
 "BlueZ 的 btmon。",
 "不可行：内核没有 hci 设备。")
S['R',46] = ("Rust + socket2：UDP 组播收发。",
 "无直接对应的项目。",
 "不做，没有使用方。")
S['R',47] = ("Rust + blake3（分块哈希）+ UDP 传输 + 按偏移写文件。",
 "BitTorrent、Syncthing。",
 "不做，收益低。")
S['R',48] = ("原方案：Rust + cgroup 文件接口。",
 "cgroups-rs。",
 "不可行：cgroup 对 shell 不可写。")
S['R',49] = ("原方案：Rust + tokio 的常驻底座，各功能作为异步任务运行。",
 "BusyBox（一个程序、多个子命令）。",
 "改为 z6x-tools 命令集：用完即退出，只有 keymap 常驻。详见 tools 规格。")
