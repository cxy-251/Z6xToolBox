package z6x.content.proposals

import z6x.framework.Verdict
import z6x.framework.module

// 由 notes/gen.py 根据 notes/decisions.py 生成：每张卡片的原文标题和摘要直接取自 agy 的旧 C# 文件。
// 要改审核结论，改 decisions.py 后运行 python3 notes/gen.py notes 重新生成，不要直接改这个文件。

val ReviewSummary = module("review-summary", "审核总表：109 篇提案的去向") {
    keywords = "Go 60 篇 · Rust 49 篇 · 纳入 / 第二期 / 第三期 / 不纳入 / 不可行"
    overview = """
        agy 推荐了 109 个小项目（Go 60、Rust 49）。审核原则：只做这台投影仪上**实测可行**、对日常使用**有价值**、且**不重复**的；Go 的进 z6x-hub（常驻服务），Rust 的进 z6x-tools（命令集）。
        每篇的原文摘录、实测依据在五个分类审核页里。
    """
    proposal()

    why("统计") {
        facts(
            "纳入第一期" to "hub：core、files、paste、control、wol、metrics、speed；tools：sys、ports、key、keymap、hash、watch、ping",
            "第二期" to "hub：notify、lanscan、webshell、dlna、配置页、iperf3；tools：iobench、pack、run",
            "第三期候选" to "离线下载、相册、阅读、直播源、MQTT、HomeKit、Tailscale、mDNS、终端复用等",
            "不可行（实测依据）" to "需要 root / 1024 以下端口 / CAP_NET_RAW / 声卡 / tun / cgroup / hci / cec / hidg0 / fb0 的提案",
            "重复合并" to "WebDAV、网页终端、MQTT、监控导出、DNS、代理等在 Go 和 Rust 里各写过一遍，只保留一份",
        )
    }

    verify("逐篇去向") {
        facts(
            "Go-01 局域网大容量存储中枢" to "hub·files（一期）",
            "Go-02 脱机离线下载节点" to "第三期候选（外部程序）",
            "Go-03 家庭网络辅助中枢" to "不纳入",
            "Go-04 大屏多联短视频与媒体画廊端到端架构" to "第三期候选",
            "Go-05 全天候低功耗音乐串流中枢" to "不纳入 hub（可单独部署）",
            "Go-06 智能家居控制桥接网关" to "hub·control（一期）",
            "Go-07 24 小时后台常驻保障" to "hub 运行前提（已实测：关屏在线、关机睡眠）",
            "Go-08 自研大屏相册服务" to "第三期候选（files 扩展）",
            "Go-09 自研掌机同步网关" to "hub·files（一期，上传接口）",
            "Go-10 自研硬件看板与系统自愈探针" to "hub·metrics（一期）",
            "Go-11 自研局域网中继" to "hub·paste（一期）",
            "Go-12 自研直播源代理与 EPG 聚合" to "第三期候选（iptv，合并 12/48/59）",
            "Go-13 自研漫画与电子书阅览中枢" to "第三期候选",
            "Go-14 自研内网测速与网络抖动探针" to "hub·speed（一期）",
            "Go-15 自研手机虚拟触控板与按键宏中继" to "hub·control（一期按键与宏，二期触控板）",
            "Go-16 自研轻量 DLNA 投屏渲染中继" to "第二期·dlna",
            "Go-17 自研局域网网络唤醒与设备巡检网关" to "hub·wol（一期）",
            "Go-18 自研大屏悬浮通知中心" to "第二期·notify（先验证）",
            "Go-19 自研熄屏无线音频接收端" to "不可行",
            "Go-20 自研网页直连终端" to "第二期·webshell",
            "Go-21 自研原生 Samba 共享服务" to "不纳入",
            "Go-22 自研影视刮削与 NFO 自动生成器" to "不纳入",
            "Go-23 自研本地轻量 MQTT Broker" to "第三期候选",
            "Go-24 自研局域网流量转发代理" to "不纳入",
            "Go-25 自研 RSS/Atom 播客与影视追更聚合器" to "不纳入",
            "Go-26 自研嵌入式 S3 兼容对象存储网关" to "不纳入",
            "Go-27 自研 UPnP/IGD 与 NAT-PMP 自动端口映射网关" to "不纳入（安全）",
            "Go-28 自研 Prometheus 规范系统与存储指标导出器" to "hub·metrics（一期，合并）",
            "Go-29 自研局域网统一认证与反向代理网关" to "hub·core（一期，鉴权）",
            "Go-30 自研微型 Git HTTP 仓库与 LFS 备份镜像节点" to "不纳入",
            "Go-31 自研 JSON-RPC 2.0 远程控制与自动化调度服务" to "hub·control（一期，合并）",
            "Go-32 自研 HLS 实时流切片与 m3u8 索引生成器" to "不纳入",
            "Go-33 自研私有 Tailscale DERP 与网状穿透中继节点" to "不纳入",
            "Go-34 自研 Webhook 事件监听与自动化动作分发器" to "hub·control（一期，合并）",
            "Go-35 自研纯 Go SFTP 文件传输服务端" to "不纳入",
            "Go-36 自研局域网轻量 DHCP 备用分配与静态 IP 绑定服务" to "不可行",
            "Go-37 自研多平台 DDNS 动态域名解析同步客户端" to "不纳入",
            "Go-38 自研 PAC 自动代理分流配置生成与分发服务" to "不纳入",
            "Go-39 自研 DHT 磁力链接解析与种子元数据提取器" to "不纳入",
            "Go-40 自研 RTSP/RTP 投屏会话协商与 SDP 信令中继代理" to "不可行",
            "Go-41 自研多房间音频流同步分发服务" to "不可行",
            "Go-42 自研本地影视字幕自动检索与哈希匹配器" to "不纳入",
            "Go-43 自研嵌入式 Loki 规范日志聚合与查询端点" to "hub·core（一期，简化）",
            "Go-44 自研智能家居 Matter 与 Zigbee2MQTT 状态中继网关" to "第三期候选",
            "Go-45 自研家庭宽带定时测速与链路质量追踪器" to "不纳入",
            "Go-46 自研跨 VLAN 网络唤醒魔术包转发代理" to "hub·wol（一期，合并）",
            "Go-47 自研海报墙与相册 WebP 高速缩略图生成器" to "第三期候选（与 Go-08 一起）",
            "Go-48 自研 IPTV M3U 播放列表聚合与频道有效性探测器" to "第三期候选（iptv，合并到 Go-12）",
            "Go-49 自研局域网 iperf3 协议兼容测速服务端" to "hub·speed（二期，可选）",
            "Go-50 自研轻量云盘多存储挂载与聚合网关" to "不纳入",
            "Go-51 自研 ACME 证书自动签发与轮转服务" to "不纳入",
            "Go-52 自研直播间弹幕协议监听与直播流录制器" to "不纳入",
            "Go-53 自研局域网 SSDP 设备发现与硬件拓扑探测器" to "第二期·lanscan",
            "Go-54 自研 MQTT 传感器时序数据收集与轻量归档服务" to "不纳入",
            "Go-55 自研 Tailscale 用户态子网路由广播网关" to "第三期候选",
            "Go-56 自研 WebRTC 浏览器免插件低延迟信令服务端" to "不纳入",
            "Go-57 自研 U 盘跨目录重复文件排查与硬链接去重器" to "tools·hash（一期，只报告）",
            "Go-58 自研配置项可视化 Web 动态表单生成器" to "hub·core（二期，配置页）",
            "Go-59 自研电视节目指南 XMLTV 定时抓取与生成服务" to "第三期候选（iptv，合并到 Go-12）",
            "Go-60 架构整合" to "hub 架构（规格基础）",
            "Rust-01 自研极限低内存系统看门狗" to "tools·run（二期，改为进程守护）",
            "Rust-02 自研底层输入事件拦截与按键重映射" to "tools·keymap（一期）",
            "Rust-03 自研无 GC 抖动音频环形混音器" to "不可行",
            "Rust-04 自研大文件高速哈希校验与去重工具" to "tools·hash（一期）",
            "Rust-05 自研纯用户态轻量加密隧道" to "不纳入",
            "Rust-06 自研零拷贝内存映射 KV 存储引擎" to "不纳入",
            "Rust-07 自研低功耗蓝牙与原始串口数据中继" to "不可行",
            "Rust-08 自研系统日志环形缓冲与崩溃黑匣子" to "不纳入",
            "Rust-09 自研快速端口敲门与非特权端口隐蔽守护器" to "不可行",
            "Rust-10 自研轻量 DoH/DoT 加密 DNS 中继" to "不纳入",
            "Rust-11 自研 U 盘大容量 Zstd 实时流式压缩工具" to "tools·pack（二期）",
            "Rust-12 自研零开销日志轮转与脱敏器" to "不纳入",
            "Rust-13 自研低延迟 WebSocket 广播引擎" to "不单独做",
            "Rust-14 自研极简硬件指标导出器" to "tools·sys（一期）",
            "Rust-15 自研 U 盘坏道与介质健康度检测探针" to "tools·iobench（二期）",
            "Rust-16 自研用户态带宽整形与令牌桶限速器" to "不纳入（第三期可作为 files 限速选项）",
            "Rust-17 自研微型嵌入式 WebDAV 与文件直链引擎" to "合并到 hub·files",
            "Rust-18 自研高频事件注入与微秒级按键模拟器" to "tools·key（一期）",
            "Rust-19 自研服务编排与热重载管理器" to "tools·run（二期）",
            "Rust-20 自研零拷贝网络数据包嗅探与流特征分析器" to "不可行",
            "Rust-21 自研微型异步 MQTT 协议解析与网关中继" to "不纳入",
            "Rust-22 自研静态 Web 资源与固件更新 HTTP 缓存分发器" to "合并到 hub·files",
            "Rust-23 自研硬件串口与 USB 虚拟串口通信守护器" to "不可行",
            "Rust-24 自研动态虚拟 Tun 网卡 IP 流量分流器" to "不可行",
            "Rust-25 自研无锁环形队列音频流录制与回放探针" to "不可行",
            "Rust-26 自研 Epoll 驱动轻量 Socks5/HTTP 混合反向代理" to "不纳入",
            "Rust-27 自研嵌入式 SQLite 元数据管理与轻量全文检索工具" to "不纳入",
            "Rust-28 自研系统定时微任务与毫秒级事件调度器" to "不纳入",
            "Rust-29 自研光机与 CPU 动态温控调频巡检器" to "合并到 tools·sys",
            "Rust-30 自研低开销 UDP 视频流快速转发与 FEC 纠错中继" to "不纳入",
            "Rust-31 自研轻量级 inotify 增量文件变动监听器" to "tools·watch（一期）",
            "Rust-32 自研直接读写 /dev/graphics/fb0 截图探针" to "不可行",
            "Rust-33 自研跨进程共享内存 mmap 配置同步总线" to "不纳入",
            "Rust-34 自研微秒级 ICMP/UDP 网络时延与抖动雷达" to "tools·ping（一期）",
            "Rust-35 自研轻量 mDNS/DNS-SD 局域网服务发现与广播器" to "第三期候选",
            "Rust-36 自研底层存储吞吐与随机读写 IOPS 评测工具" to "tools·iobench（二期）",
            "Rust-37 自研微型 PTY 虚拟终端会话保持与复用器" to "第三期候选",
            "Rust-38 自研无内存分配 LZ4 实时数据帧解压与转码流" to "合并到 tools·pack",
            "Rust-39 自研 HDMI-CEC 协议监听与设备联动控制器" to "不可行",
            "Rust-40 自研 ALSA 底层声卡 Mixer 增益与音量调节器" to "不可行",
            "Rust-41 自研 USB 虚拟 HID 键鼠协议模拟注入器" to "hidg0 部分不可行，uinput 部分合并到 tools·key",
            "Rust-42 自研多上游 DNS 并发竞速与内存缓存解析器" to "不纳入",
            "Rust-43 自研零动态库 Web 控制台网关" to "合并到 hub·webshell（二期）",
            "Rust-44 自研跨进程无锁共享内存环形缓冲区 IPC" to "不纳入",
            "Rust-45 自研蓝牙 HCI 原始套接字高速扫描探针" to "不可行",
            "Rust-46 自研多屏协同与低延迟投屏组播信令中继" to "不纳入",
            "Rust-47 自研局域网大文件 P2P 分片差分加速探针" to "不纳入",
            "Rust-48 自研 Linux Cgroups 轻量资源隔离沙箱" to "不可行",
            "Rust-49 架构演进" to "改为命令集（见 z6x-tools 规格）",
        )
    }

    related("spec-hub", "spec-tools", "review-files", "review-media", "review-network", "review-control", "review-system")
}

val ReviewFiles = module("review-files", "审核：文件与存储") {
    keywords = "WebDAV、上传、压缩、哈希、对象存储……"
    overview = """
        agy 提案逐篇审核（21 篇）。每张卡片：原文标题和摘要照录；有具体技术说法的另外摘一句原文；「实测」写去向和依据。全文见旧 C# 文件。
    """
    proposal()

    audit("逐篇审核") {
        claim(
            """**Go-01《局域网大容量存储中枢：Go 静态 WebDAV / 文件直链服务》**
基于 Go 纯静态编译的 WebDAV 服务，将 47GB 空闲闪存与外接 100GB+ U 盘挂载为局域网微盘，支持多设备免客户端读写。
原文说：「U 盘重新拔插后路径失效……服务启动参数建议直接挂载上一级的 `/storage`」""",
            Verdict.Confirmed,
            """**去向：hub·files（一期）**
可行，纳入 hub 的 files 模块。静态 Go 程序能跑、8085 这类高位端口能绑定都已实测；/data 实测剩余 46G（原文说 47GB，接近）。U 盘实际在 /storage/<卷ID>/ 下，shell 可访问。
原文：`Z6xToolBox.App/Content/Modules/GoServices/01_WebDavFileHubServiceData.cs`""",
        )
        claim(
            """**Go-08《自研大屏相册服务：Go 缩略图缓存与瀑布流展示（Z6X Gallery Hub）》**
纯 Go 自研的大容量 U 盘相册服务，生成 WebP 缩略图并缓存，内嵌 HTML5 瀑布流供电视浏览器全屏流畅浏览。""",
            Verdict.Unverified,
            """**去向：第三期候选（files 扩展）**
可行但优先级低：缩略图要 CPU，原文要求并发限 2 合理。原文的「4 核 A73」是错的。
原文：`Z6xToolBox.App/Content/Modules/GoServices/08_GalleryHubPhotoServiceData.cs`""",
        )
        claim(
            """**Go-09《自研掌机同步网关：Steam Deck 存档与截图自动归档（DeckSync Hub）》**
基于 Go 自研的轻量 HTTP 归档接收网关，配合 Steam Deck 本地脚本，连上家庭 Wi-Fi 自动增量备份游戏存档与 4K 截图至外接 U 盘。""",
            Verdict.Confirmed,
            """**去向：hub·files（一期，上传接口）**
合并为 files 模块的上传接口：流式落盘、先写 .tmp 再改名，这两条原文的设计是对的。
原文：`Z6xToolBox.App/Content/Modules/GoServices/09_DeckSyncArchiveGatewayData.cs`""",
        )
        claim(
            """**Go-13《自研漫画与电子书阅览中枢：U 盘流式解压与大屏翻页（Z6X Reader Hub）》**
纯 Go 自研的轻量漫画与电子书阅读服务，对 U 盘内的 CBZ/ZIP/EPUB 压缩包进行流式随机读取，内嵌 HTML5 阅读器供手机或电视大屏翻页。""",
            Verdict.Confirmed,
            """**去向：第三期候选**
可行（纯 Go 解析 zip），优先级低。
原文：`Z6xToolBox.App/Content/Modules/GoServices/13_ComicEbookReaderServerData.cs`""",
        )
        claim(
            """**Go-21《自研原生 Samba 共享服务：纯用户态 SMBv2 协议栈与 U 盘直通（Z6X SMBHub）》**
纯 Go 自研的用户态 SMBv2 共享服务，无需 Root 权限与系统 Samba 守护进程，将外接 U 盘直接暴露为 Windows 原生网络共享文件夹。
原文说：「所有设备打开网络邻居即可直接访问」""",
            Verdict.Disproved,
            """**去向：不纳入**
1445 端口能绑定，但原文的核心卖点「所有设备打开网络邻居即可直接访问」不成立：Windows 资源管理器只认 445 端口（原文自己在排坑里也承认了）。WebDAV 已覆盖文件共享。
原文：`Z6xToolBox.App/Content/Modules/GoServices/21_UserSpaceSmbFileShareData.cs`""",
        )
        claim(
            """**Go-26《自研嵌入式 S3 兼容对象存储网关：U 盘存储标准化 API 暴露（Z6X S3Lite）》**
纯 Go 编写的微型 S3 兼容对象存储服务，将极米外接 U 盘转为轻量 S3 桶端点，供局域网设备按标准协议备份与存取，常驻内存仅 18MB。
原文说：「-dir /mnt/media_rw/USB_DISK/s3_storage」""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，但 WebDAV 已覆盖。原文命令里的 IP（192.168.1.100）和路径（/mnt/media_rw）都不对：投影仪是 192.168.0.109，/mnt/media_rw 对 shell 不可访问（实测）。
原文：`Z6xToolBox.App/Content/Modules/GoServices/26_S3CompatibleMinioLiteStorageData.cs`""",
        )
        claim(
            """**Go-30《自研微型 Git HTTP 仓库与 LFS 备份镜像节点：U 盘私有代码仓（Z6X GitMirror）》**
纯 Go 编写的轻量 Git Smart HTTP 与 LFS 大文件存储服务端，把极米外接 U 盘变成家庭私有 Git 备份节点，常驻内存仅 15MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，收益低。原文端口 9418 是 git 协议端口却跑 HTTP，路径用了不可访问的 /mnt/media_rw。
原文：`Z6xToolBox.App/Content/Modules/GoServices/30_GitRepositoryLfsMirrorData.cs`""",
        )
        claim(
            """**Go-35《自研纯 Go SFTP 文件传输服务端：安全加密文件通道（Z6X SftpNode）》**
纯 Go 实现的轻量 SFTP 子系统服务，免安装 OpenSSH 守护即可通过 FileZilla/WinSCP 安全管理极米 U 盘，常驻内存仅 14MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，但 WebDAV 和 SSH 已覆盖。
原文：`Z6xToolBox.App/Content/Modules/GoServices/35_SftpServerStorageGatewayData.cs`""",
        )
        claim(
            """**Go-47《自研海报墙与相册 WebP 高速缩略图生成器：内存缓存分发（Z6X ThumbGen）》**
纯 Go 编写的微型图片缩略图生成与 WebP 转换服务，流式缩放 U 盘相册与海报墙大图并提供本地缓存，常驻内存仅 18MB。""",
            Verdict.Confirmed,
            """**去向：第三期候选（与 Go-08 一起）**
可行，跟相册一起做。
原文：`Z6xToolBox.App/Content/Modules/GoServices/47_WebpThumbnailGeneratorData.cs`""",
        )
        claim(
            """**Go-50《自研轻量云盘多存储挂载与聚合网关：网盘转本地 WebDAV（Z6X CloudMount）》**
纯 Go 编写的微型云盘聚合挂载服务，把阿里云盘/百度网盘等主流网盘转为本地 WebDAV 目录供电视播放器直接点播，常驻内存仅 20MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，但 AList 本身就是 Go 静态程序，可以直接单独部署，不必重写。
原文：`Z6xToolBox.App/Content/Modules/GoServices/50_AlistMultiCloudDriveMountData.cs`""",
        )
        claim(
            """**Go-57《自研 U 盘跨目录重复文件排查与硬链接去重器：哈希抽样（Z6X HardlinkOpt）》**
纯 Go 编写的文件查重与硬链接去重工具，通过快速分段哈希扫描 U 盘中重复的剧集或照片并建立硬链接节省空间，常驻内存仅 12MB。""",
            Verdict.Confirmed,
            """**去向：tools·hash（一期，只报告）**
合并到 z6x-tools 的 hash 子命令，只报告重复、不建硬链接：U 盘常见的 FAT32/exFAT 不支持硬链接（原文排坑里自己也写了）。
原文：`Z6xToolBox.App/Content/Modules/GoServices/57_FileDeduplicationHardlinkOptimizerData.cs`""",
        )
        claim(
            """**Rust-04《自研大文件高速哈希校验与去重工具：ARM64 NEON 指令集加速（Z6X RustHash）》**
利用 Rust 对底层硬件指令集的零成本控制能力，调用 ARM64 NEON 向量指令集做 xxHash3 极速哈希计算与 mmap 内存映射，秒级完成几百 GB U 盘媒体去重。
原文说：「MediaTek MT9669 的 4 个 Cortex-A73 核心内部均集成了 NEON 向量处理单元」""",
            Verdict.Confirmed,
            """**去向：tools·hash（一期）**
可行。原文「MT9669 的 4 个 Cortex-A73」不对，实际是 8 核 A55（同样有 NEON）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/04_SimdFastFileChecksumData.cs`""",
        )
        claim(
            """**Rust-06《自研零拷贝内存映射 KV 存储引擎：mmap 驱动与纳秒级只读检索（Z6X RustKV）》**
利用 Rust 零成本类型转换自研的嵌入式只读键值存储引擎，基于 Linux mmap 内存映射，为几百 GB U 盘媒体索引提供纳秒级查询与零堆分配。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，没有需求。
原文：`Z6xToolBox.App/Content/Modules/RustServices/06_ZeroCopyMmapKvEngineData.cs`""",
        )
        claim(
            """**Rust-11《自研 U 盘大容量 Zstd 实时流式压缩工具：ARM64 向量优化与极速归档（Z6X RustZstd）》**
纯 Rust 包装的 Zstandard 流式压缩工具，针对 ARM64 指令优化，以极低 CPU 占用与零中间文件写入实现几百 GB 媒体与日志压缩。""",
            Verdict.Confirmed,
            """**去向：tools·pack（二期）**
可行。原文「4 核 A73」不对。
原文：`Z6xToolBox.App/Content/Modules/RustServices/11_ZstdStreamCompressorData.cs`""",
        )
        claim(
            """**Rust-15《自研 U 盘坏道与介质健康度检测探针：直接 I/O 与延迟抖动分析（Z6X RustDiskDoctor）》**
纯 Rust 自研的存储介质检测探针，基于 O_DIRECT 绕过操作系统缓存直接探测 U 盘扇区读写延迟与隐形坏块，提前预防数据损坏。""",
            Verdict.Unverified,
            """**去向：tools·iobench（二期）**
块设备不可访问，只能对文件测；FAT/exFAT 是否支持 O_DIRECT 未验证（原文自己也提到会降级）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/15_UDiskHealthCheckInspectorData.cs`""",
        )
        claim(
            """**Rust-17《自研微型嵌入式 WebDAV 与文件直链引擎：纯 Rust 实现（Z6X RustWebDav）》**
纯 Rust 打造的超轻量 WebDAV 与 HTTP 静态直链服务，单文件仅 1.1MB，常驻内存低于 2.5MB，适合在极端内存受限环境下替代 Go WebDAV 服务。""",
            Verdict.Confirmed,
            """**去向：合并到 hub·files**
与 Go-01 重复，WebDAV 只做一份（Go）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/17_EmbeddedUltraLightWebDavData.cs`""",
        )
        claim(
            """**Rust-22《自研静态 Web 资源与固件更新 HTTP 缓存分发器：sendfile 零拷贝分发（Z6X RustEdgeCache）》**
纯 Rust 编写的轻量静态资源 HTTP 分发服务，直接利用 Linux sendfile 系统调用将 U 盘中的安装包与固件直通网卡，常驻内存仅 2MB。""",
            Verdict.Confirmed,
            """**去向：合并到 hub·files**
与 Go-01 的下载功能重复。
原文：`Z6xToolBox.App/Content/Modules/RustServices/22_ZeroCopyHttpStaticCacheData.cs`""",
        )
        claim(
            """**Rust-27《自研嵌入式 SQLite 元数据管理与轻量全文检索工具：FTS5 倒排索引（Z6X RustSqliteIndex）》**
纯 Rust 静态链接嵌入式 SQLite FTS5 的轻量全文检索工具，专为 U 盘数万本地影视音乐建立秒级搜索索引，常驻内存仅 3MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，暂无需求。
原文：`Z6xToolBox.App/Content/Modules/RustServices/27_EmbeddedFtsMediaIndexData.cs`""",
        )
        claim(
            """**Rust-36《自研底层存储吞吐与随机读写 IOPS 评测工具：O_DIRECT 裸盘基准（Z6X RustIoBench）》**
纯 Rust 编写的嵌入式存储基准测试工具，通过 O_DIRECT 绕过 Linux 页缓存测量 U 盘与 eMMC 读写性能，常驻内存仅 800KB。""",
            Verdict.Confirmed,
            """**去向：tools·iobench（二期）**
可行（对文件测）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/36_ZeroCopyDirectIoBenchmarkData.cs`""",
        )
        claim(
            """**Rust-38《自研无内存分配 LZ4 实时数据帧解压与转码流：ARM64 向量高速解压（Z6X RustLz4Stream）》**
纯 Rust 编写的流式 LZ4 帧解压与转码引擎，基于零堆内存分配与 NEON 向量复制，常驻内存仅 1.2MB。""",
            Verdict.Confirmed,
            """**去向：合并到 tools·pack**
zstd 足够，lz4 不单独做。
原文：`Z6xToolBox.App/Content/Modules/RustServices/38_EmbeddedLz4FrameTranscoderData.cs`""",
        )
        claim(
            """**Rust-47《自研局域网大文件 P2P 分片差分加速探针：内容寻址分发（Z6X RustChunkP2P）》**
纯 Rust 开发的局域网内容寻址大文件分片同步探针，基于定长分块哈希仅传输差异数据块，常驻内存仅 2MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，收益低。
原文：`Z6xToolBox.App/Content/Modules/RustServices/47_StaticBinaryP2PChunkDistributorData.cs`""",
        )
    }

    related("review-summary", "spec-hub", "spec-tools")
}

val ReviewMedia = module("review-media", "审核：下载、影音与直播") {
    keywords = "离线下载、音乐、视频、投屏、字幕、直播源……"
    overview = """
        agy 提案逐篇审核（20 篇）。每张卡片：原文标题和摘要照录；有具体技术说法的另外摘一句原文；「实测」写去向和依据。全文见旧 C# 文件。
    """
    proposal()

    audit("逐篇审核") {
        claim(
            """**Go-02《脱机离线下载节点：静态 ARM64 aria2c + JSON-RPC 调度》**
部署静态编译的 ARM64 aria2c 客户端，开启 JSON-RPC 接口配合外部 Web UI，利用外接 U 盘实现夜间静音脱机下载。
原文说：「关闭投影仪画面后下载停止……需配合执行模块 11 中的网络防休眠配置（`settings put global wifi_sleep_policy 2`）」""",
            Verdict.Unverified,
            """**去向：第三期候选（外部程序）**
aria2c 需另找静态 arm64 版本，不在 hub 里重写。下载目录 /storage/emulated/0/Download 可写（实测）。关键前提「关机待机时下载不中断」未验证；原文要求的 wifi_sleep_policy 实测已经是 2。
原文：`Z6xToolBox.App/Content/Modules/GoServices/02_Aria2HeadlessDownloadNodeData.cs`""",
        )
        claim(
            """**Go-04《大屏多联短视频与媒体画廊端到端架构》**
Go 静态流媒体后端 + 内嵌 CSS Grid 响应式 Web 前端 + Android Chromium WebView 硬解容器，针对几百 GB U 盘大容量内容与 MT9669 硬解限制设计。
原文说：「极米 Z6X Pro 搭载联发科 MT9669 芯片……通常支持 2~4 路 1080p（或 1 路 4K）同时硬解」""",
            Verdict.Unverified,
            """**去向：第三期候选**
需要电视浏览器同时硬解多路视频，路数没有验证。原文的芯片说法是错的：实际是海思 Hi3751V660、8 核 A55，不是联发科 MT9669、4 核 A73。
原文：`Z6xToolBox.App/Content/Modules/GoServices/04_MultiPaneVideoStreamingArchitectureData.cs`""",
        )
        claim(
            """**Go-05《全天候低功耗音乐串流中枢：静态 Navidrome 原生部署》**
在极米后台部署静态编译的 Navidrome 音乐服务器，挂载 U 盘音乐库，对外提供 Subsonic 协议串流。""",
            Verdict.Confirmed,
            """**去向：不纳入 hub（可单独部署）**
Navidrome 本身是 Go 静态程序，可以按 Go 服务的方式单独部署；不在 hub 里重写。
原文：`Z6xToolBox.App/Content/Modules/GoServices/05_SubsonicMusicStreamingServerData.cs`""",
        )
        claim(
            """**Go-12《自研直播源代理与 EPG 聚合：Go 测活与内网分流（Z6X LiveProxy）》**
基于 Go 自研的轻量 IPTV 代理服务，定时并发检测直播源连通性并剔除死链，聚合 XMLTV 节目单输出统一局域网播放源。""",
            Verdict.Confirmed,
            """**去向：第三期候选（iptv，合并 12/48/59）**
可行（纯网络任务）。直播源、节目单三篇合成一个可选模块，优先级低。
原文：`Z6xToolBox.App/Content/Modules/GoServices/12_LiveProxyStreamingHubData.cs`""",
        )
        claim(
            """**Go-16《自研轻量 DLNA 投屏渲染中继：UPnP/AVTransport 与系统播放器调度（Z6X DlnaBridge）》**
纯 Go 自研的轻量 DLNA 接收端，将手机投屏的视频/音频直链提取并通过系统原生硬件解码器全屏播放，零广告弹窗干扰。""",
            Verdict.Unverified,
            """**去向：第二期·dlna**
可行性有前提：UDP 1900 已被系统组件占用（实测，uid 1000）；视频链接没有默认播放器，会弹选择框。要先装好播放器并设为默认。
原文：`Z6xToolBox.App/Content/Modules/GoServices/16_DlnaMediaRendererData.cs`""",
        )
        claim(
            """**Go-19《自研熄屏无线音频接收端：HTTP/RTSP 音频流与系统混音直推（Z6X AudioStreamer）》**
纯 Go 自研的局域网无线音频流接收端，在不点亮投影光机的待机状态下，将 Steam Deck 或 PC 的游戏声音无线推送到极米音箱播放。
原文说：「Go 服务在本地通过管道调用系统的 `tinyplay` 工具或 Android AudioTrack API 直接写入硬件音频设备节点」""",
            Verdict.Disproved,
            """**去向：不可行**
shell 不在 audio 组，/dev/snd 不可读写（实测）；系统里没有 tinyplay；AudioTrack 要在 App 里用。shell 进程放不出声音。
原文：`Z6xToolBox.App/Content/Modules/GoServices/19_ScreenOffWirelessAudioStreamerData.cs`""",
        )
        claim(
            """**Go-22《自研影视刮削与 NFO 自动生成器：目录监听与 TMDB 元数据补全（Z6X Scraper）》**
纯 Go 自研的轻量媒体刮削探针，监听 U 盘下载目录，检测到新增视频自动调用 TMDB 抓取海报、简介并生成标准 .nfo 元数据文件。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行但依赖访问 TMDB（外网），收益低。
原文：`Z6xToolBox.App/Content/Modules/GoServices/22_MovieScraperNfoGeneratorData.cs`""",
        )
        claim(
            """**Go-25《自研 RSS/Atom 播客与影视追更聚合器：定时拉取与离线下载联动（Z6X FeedSync）》**
纯 Go 编写的轻量 RSS/Atom 订阅聚合引擎，负责周期性解析影视追更与播客音频 Feed，自动联动本地 Aria2 推送下载，常驻内存仅 12MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，收益低。
原文：`Z6xToolBox.App/Content/Modules/GoServices/25_RssFeedMediaAggregatorData.cs`""",
        )
        claim(
            """**Go-32《自研 HLS 实时流切片与 m3u8 索引生成器：TS 分片与跨端点播（Z6X HlsServer）》**
纯 Go 编写的微型 HLS 流媒体切片与分发服务，将本地视频实时拆解为 TS/fMP4 切片并输出 m3u8 列表，常驻内存仅 20MB。
原文说：「Go 1.27 + `aler9/gortsplib` 底层流解析」""",
            Verdict.Unverified,
            """**去向：不纳入**
原文说用 gortsplib 做 MP4/MKV 解复用——gortsplib 是 RTSP 库，不负责 MP4/MKV 解封装，技术选型存疑；收益低。
原文：`Z6xToolBox.App/Content/Modules/GoServices/32_HttpLiveStreamingSegmenterData.cs`""",
        )
        claim(
            """**Go-39《自研 DHT 磁力链接解析与种子元数据提取器：快速转 .torrent 文件（Z6X MagnetHub）》**
纯 Go 编写的微型 Mainline DHT 节点与 BEP 0009 扩展客户端，快速将磁力链接 InfoHash 解析并下载为本地 .torrent 文件，常驻内存仅 18MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，收益低。
原文：`Z6xToolBox.App/Content/Modules/GoServices/39_TorrentMetadataMagnetIndexerData.cs`""",
        )
        claim(
            """**Go-41《自研多房间音频流同步分发服务：Snapcast 协议兼容（Z6X SnapSync）》**
纯 Go 编写的多房间多设备音频同步分发服务端，兼容 Snapcast 客户端协议，实现全屋毫秒级音频对齐广播，常驻内存仅 16MB。""",
            Verdict.Disproved,
            """**去向：不可行**
服务端需要音频输入源、客户端需要出声，shell 进程两头都碰不到音频设备。
原文：`Z6xToolBox.App/Content/Modules/GoServices/41_MultiRoomAudioStreamSyncData.cs`""",
        )
        claim(
            """**Go-42《自研本地影视字幕自动检索与哈希匹配器：射手与 SubHD 接口（Z6X SubSync）》**
纯 Go 编写的影视字幕自动检索工具，通过提取视频特征哈希自动从射手网与 SubHD 下载中文字幕并重命名归档，常驻内存仅 12MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，依赖外网字幕站，收益低。
原文：`Z6xToolBox.App/Content/Modules/GoServices/42_SubtitleAutoDownloaderData.cs`""",
        )
        claim(
            """**Go-48《自研 IPTV M3U 播放列表聚合与频道有效性探测器：自动去重清洗（Z6X M3uFilter）》**
纯 Go 编写的 IPTV 直播源列表清洗与聚合分发服务，自动探测多源频道可用性与首包延迟，剔除失效流并生成纯净订阅源，常驻内存仅 15MB。""",
            Verdict.Confirmed,
            """**去向：第三期候选（iptv，合并到 Go-12）**
合并。
原文：`Z6xToolBox.App/Content/Modules/GoServices/48_M3uPlaylistAggregatorEditorData.cs`""",
        )
        claim(
            """**Go-52《自研直播间弹幕协议监听与直播流录制器：长连接解析（Z6X LiveRecord）》**
纯 Go 编写的直播流与弹幕同步录制服务，解析主流直播平台 WebSocket 协议并直接保存原始 FLV/HLS 流至 U 盘，常驻内存仅 18MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，收益低。
原文：`Z6xToolBox.App/Content/Modules/GoServices/52_BilibiliLiveDanmuStreamRecorderData.cs`""",
        )
        claim(
            """**Go-56《自研 WebRTC 浏览器免插件低延迟信令服务端：网页投屏网关（Z6X WebRtcHub）》**
纯 Go 编写的 WebRTC 房间与 SDP 信令交换网关，实现电脑/手机浏览器免安装任何客户端直接向电视低延迟投屏，常驻内存仅 18MB。""",
            Verdict.Unverified,
            """**去向：不纳入**
接收端要在电视上解码显示（得开浏览器页面），浏览器发起屏幕共享还要求 HTTPS；整体复杂、收益低。
原文：`Z6xToolBox.App/Content/Modules/GoServices/56_WebRtcSignalingScreenShareHubData.cs`""",
        )
        claim(
            """**Go-59《自研电视节目指南 XMLTV 定时抓取与生成服务：EPG 节目单补齐（Z6X EpgHub）》**
纯 Go 编写的微型 EPG 节目预告定时生成服务，每日自动抓取央视与卫视节目单并输出 XMLTV/Gz 格式供电视播放器调用，常驻内存仅 12MB。""",
            Verdict.Confirmed,
            """**去向：第三期候选（iptv，合并到 Go-12）**
合并。
原文：`Z6xToolBox.App/Content/Modules/GoServices/59_EpgXmlTvCrawlerAggregatorData.cs`""",
        )
        claim(
            """**Rust-03《自研无 GC 抖动音频环形混音器：低延迟 Wi-Fi 串流与 ALSA 硬件直写（Z6X RustAudio）》**
利用 Rust 确定性时延与无锁队列特性自研的实时音频混音服务，接收局域网 PCM 音频流直接推送到 ALSA 驱动，彻底消除 GC 引起的爆音。""",
            Verdict.Disproved,
            """**去向：不可行**
/dev/snd 不可读写（实测，shell 不在 audio 组）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/03_JitterFreeAudioPcmMixerData.cs`""",
        )
        claim(
            """**Rust-25《自研无锁环形队列音频流录制与回放探针：TinyALSA 原始 PCM 监听（Z6X RustAudioCapture）》**
纯 Rust 开发的底层 PCM 音频捕获与低延迟环形缓冲探针，通过 TinyALSA 裸读声卡节点，常驻内存仅 1.6MB。""",
            Verdict.Disproved,
            """**去向：不可行**
/dev/snd 不可读写（实测）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/25_LockFreeAudioCaptureProbeData.cs`""",
        )
        claim(
            """**Rust-30《自研低开销 UDP 视频流快速转发与 FEC 纠错中继：Reed-Solomon 丢包补偿（Z6X RustUdpRelay）》**
纯 Rust 开发的局域网 UDP 视频流转发中继器，集成前向纠错（FEC）机制，在 5%~10% Wi-Fi 偶发丢包下无重传恢复画质，常驻内存仅 2MB。""",
            Verdict.Unverified,
            """**去向：不纳入**
没有发送端配合，原文要求的 sysctl 调整需要 root。
原文：`Z6xToolBox.App/Content/Modules/RustServices/30_LowLatencyUdpScreenCastingRelayData.cs`""",
        )
        claim(
            """**Rust-40《自研 ALSA 底层声卡 Mixer 增益与音量调节器：/dev/snd/controlC0 硬件控制（Z6X RustAlsaMixer）》**
纯 Rust 编写的声卡控制接口混音器调节器，直接操作 Linux /dev/snd/controlC0，实现比系统更精细的音量增益与声道平衡，常驻内存仅 600KB。""",
            Verdict.Disproved,
            """**去向：不可行**
/dev/snd/controlC0 属于 audio 组，shell 不可访问（实测）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/40_RawAlsaHardwareMixerData.cs`""",
        )
    }

    related("review-summary", "spec-hub", "spec-tools")
}

val ReviewNetwork = module("review-network", "审核：网络、代理与远程访问") {
    keywords = "DNS、代理、测速、端口映射、穿透、组播……"
    overview = """
        agy 提案逐篇审核（25 篇）。每张卡片：原文标题和摘要照录；有具体技术说法的另外摘一句原文；「实测」写去向和依据。全文见旧 C# 文件。
    """
    proposal()

    audit("逐篇审核") {
        claim(
            """**Go-03《家庭网络辅助中枢：轻量 DNS 缓存与分流转发服务》**
部署静态编译的 Mosdns 或单文件版 AdGuard Home，通过高位端口转发实现内网 DNS 加速解析与规则拦截。
原文说：「极米 Z6X Pro 拥有充裕的可用内存（1.5GB）与 Wi-Fi 6 低延迟连接」""",
            Verdict.Unverified,
            """**去向：不纳入**
53 端口不能绑定（实测 1024 以下都不行），要靠路由器把 53 转到 5353，很多家用路由器做不到；DNS 分流和去广告已由 Clash 负责。原文说投影仪是「Wi-Fi 6」，USB ID 显示网卡是联发科 MT7663（Wi-Fi 5）。
原文：`Z6xToolBox.App/Content/Modules/GoServices/03_LocalDnsAdblockForwarderData.cs`""",
        )
        claim(
            """**Go-14《自研内网测速与网络抖动探针：HTML5 测速与延迟监控（Z6X SpeedProbe）》**
纯 Go 编写的轻量局域网测速与延迟监控探针，提供零 Flash/零 Java 依赖的 HTML5 测速页面，并在后台持续监测网络抖动与丢包率。
原文说：「由于 Linux 限制普通非特权用户创建原始套接字（Raw Socket），探针通过调用系统自带的 `/system/bin/ping` 命令」""",
            Verdict.Confirmed,
            """**去向：hub·speed（一期）**
可行。原文说非 root 不能发 ICMP 要调用系统 ping——实测普通身份就能直接发 ICMP（ping_group_range 放开了），不必调用外部命令。原文的「4 核 A73 / MT9669 单核跑满 800Mbps」芯片说法错误。
原文：`Z6xToolBox.App/Content/Modules/GoServices/14_LanSpeedLatencyProbeData.cs`""",
        )
        claim(
            """**Go-24《自研局域网流量转发代理：轻量 SOCKS5/HTTP 隧道与内网分流（Z6X ProxyNode）》**
纯 Go 自研的轻量本地转发代理节点，为 Steam Deck、手机等设备提供局域网中继跳板与流量调度，免除复杂客户端配置。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，但 Clash 已在 [::]:7890 监听（实测），局域网设备可以直接用 Clash 的混合端口，不需要再做一个。
原文：`Z6xToolBox.App/Content/Modules/GoServices/24_LanForwardingProxyNodeData.cs`""",
        )
        claim(
            """**Go-27《自研 UPnP/IGD 与 NAT-PMP 自动端口映射网关：路由器穿透（Z6X PortMap）》**
纯 Go 编写的局域网端口自动映射守护服务，通过 UPnP IGD 与 Apple NAT-PMP 协议向家庭路由器自动申请公网端口转发，常驻内存仅 10MB。""",
            Verdict.Confirmed,
            """**去向：不纳入（安全）**
可行，但会把服务暴露到公网，与本项目只在局域网使用的原则冲突。
原文：`Z6xToolBox.App/Content/Modules/GoServices/27_UPnPPortMappingManagerData.cs`""",
        )
        claim(
            """**Go-33《自研私有 Tailscale DERP 与网状穿透中继节点：家庭中转辅助（Z6X DerpNode）》**
纯 Go 编写的微型私有 DERP（Designated Encrypted Relay for Packets）转发节点，帮助家庭局域网与外网设备在对称型 NAT 下建立打洞穿透，常驻内存仅 16MB。""",
            Verdict.Unverified,
            """**去向：不纳入**
需要公网可达和证书，与局域网原则冲突。
原文：`Z6xToolBox.App/Content/Modules/GoServices/33_ZeroTierMoonPlanetaryRelayData.cs`""",
        )
        claim(
            """**Go-36《自研局域网轻量 DHCP 备用分配与静态 IP 绑定服务：应急网络分配（Z6X DhcpLite）》**
纯 Go 编写的微型 DHCPv4 协议服务，作为家庭主路由宕机或直连电脑调试时的应急 IP 分配网关，常驻内存仅 9MB。
原文说：「需通过 Linux 能力集授权 `setcap cap_net_bind_service=+ep`」""",
            Verdict.Disproved,
            """**去向：不可行**
DHCP 要绑定 67 端口（实测 1024 以下不能绑）；原文提的 `setcap` 需要 root。
原文：`Z6xToolBox.App/Content/Modules/GoServices/36_LanDnsDhcpServerData.cs`""",
        )
        claim(
            """**Go-37《自研多平台 DDNS 动态域名解析同步客户端：IPv6/IPv4 变动直更（Z6X DdnsGo）》**
纯 Go 编写的轻量多平台 DDNS 动态域名同步服务，自动探测极米外网 IPv6/IPv4 并在变动时更新阿里云/腾讯云/Cloudflare 解析，常驻内存仅 10MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行（实测投影仪有公网 IPv6 地址），但本项目不对公网开放。
原文：`Z6xToolBox.App/Content/Modules/GoServices/37_DynamicDnsClientUpdaterData.cs`""",
        )
        claim(
            """**Go-38《自研 PAC 自动代理分流配置生成与分发服务：动态智能分流（Z6X PacServer）》**
纯 Go 编写的 PAC（Proxy Auto-Config）脚本动态生成与 HTTP 分发服务，根据局域网客户端请求下发智能直连与代理规则，常驻内存仅 9MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，分流已由 Clash 负责。
原文：`Z6xToolBox.App/Content/Modules/GoServices/38_HttpProxyAdBlockPacServerData.cs`""",
        )
        claim(
            """**Go-45《自研家庭宽带定时测速与链路质量追踪器：周期时延测算（Z6X SpeedTracker）》**
纯 Go 编写的轻量网络测速与链路质量定期检测探针，周期性测算宽带上下行速率与抖动趋势并生成历史统计，常驻内存仅 12MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，但定时外网测速耗流量，收益低。
原文：`Z6xToolBox.App/Content/Modules/GoServices/45_SpeedtestTrackerCollectorData.cs`""",
        )
        claim(
            """**Go-46《自研跨 VLAN 网络唤醒魔术包转发代理：子网广播穿透（Z6X WolRelay）》**
纯 Go 编写的 Wake-on-LAN 代理转发器，接收来自公网 VPN 或不同 VLAN 的单播唤醒请求并以二层广播形式在本地局域网转发，常驻内存仅 8MB。""",
            Verdict.Confirmed,
            """**去向：hub·wol（一期，合并）**
合并到 wol 模块。
原文：`Z6xToolBox.App/Content/Modules/GoServices/46_UdpBroadcastWOLRelayData.cs`""",
        )
        claim(
            """**Go-49《自研局域网 iperf3 协议兼容测速服务端：无线空口吞吐压测（Z6X IperfNode）》**
纯 Go 编写的 iperf3 测速协议服务端，免装 Linux C 语言二进制即可配合电脑/手机 iperf3 客户端测试真实 Wi-Fi 吞吐，常驻内存仅 11MB。""",
            Verdict.Confirmed,
            """**去向：hub·speed（二期，可选）**
可行。第一期先做网页测速，iperf3 协议兼容放第二期。
原文：`Z6xToolBox.App/Content/Modules/GoServices/49_LocalSpeedMeasurementIperf3GatewayData.cs`""",
        )
        claim(
            """**Go-51《自研 ACME 证书自动签发与轮转服务：HTTPS 证书自动续期（Z6X CertGo）》**
纯 Go 编写的轻量 ACME 协议客户端，通过 DNS-01 验证为极米上的 WebDAV/Web 服务自动申请并轮转 Let's Encrypt 证书，常驻内存仅 10MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，本项目不对公网开放，用不到证书。
原文：`Z6xToolBox.App/Content/Modules/GoServices/51_TlsCertAutoRenewerData.cs`""",
        )
        claim(
            """**Go-53《自研局域网 SSDP 设备发现与硬件拓扑探测器：组播侦听（Z6X LanScanner）》**
纯 Go 编写的局域网 UPnP/SSDP 设备拓扑扫描服务，实时发现并列举局域网内的智能电视、音响、NAS 与路由器，常驻内存仅 10MB。""",
            Verdict.Confirmed,
            """**去向：第二期·lanscan**
可行。UDP 1900 已被系统占用（实测），只发 M-SEARCH 收单播回复即可，或用 SO_REUSEADDR。
原文：`Z6xToolBox.App/Content/Modules/GoServices/53_SsdpUpnpDeviceScannerData.cs`""",
        )
        claim(
            """**Go-55《自研 Tailscale 用户态子网路由广播网关：全屋设备穿透（Z6X SubnetGate）》**
纯 Go 编写的用户态子网路由代理，将极米作为家庭网络跳板向 Tailscale 宣告全国内网网段，常驻内存仅 20MB。""",
            Verdict.Unverified,
            """**去向：第三期候选**
tsnet 用户态可行性未验证；如果以后需要外出访问家里，这是首选方案。
原文：`Z6xToolBox.App/Content/Modules/GoServices/55_TailscaleSubnetRouterData.cs`""",
        )
        claim(
            """**Rust-05《自研纯用户态轻量加密隧道：smoltcp 协议栈与免 Root 组网（Z6X RustTunnel）》**
基于 Rust 纯用户态 TCP/IP 协议栈自研的轻量加密隧道，无需 Android 内核 TUN/TAP 虚拟网卡设备权限即可建立点对点安全互联。""",
            Verdict.Unverified,
            """**去向：不纳入**
用户态隧道理论可行，但 Clash 已提供 VPN；/dev/net/tun 对 shell 不可访问（实测）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/05_UserSpaceWireGuardTunnelData.cs`""",
        )
        claim(
            """**Rust-09《自研快速端口敲门与非特权端口隐蔽守护器：原始套接字序列认证（Z6X RustKnock）》**
纯 Rust 编写的轻量端口敲门与动态访问控制守护器，默认关闭高危管理端口，收到特定端口敲击序列后动态放行 IP，常驻物理内存仅约 500KB。""",
            Verdict.Disproved,
            """**去向：不可行**
端口敲门要捕获未开放端口上的包，需要原始套接字 / pcap（没有 CAP_NET_RAW）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/09_PortKnockSecurityDaemonData.cs`""",
        )
        claim(
            """**Rust-10《自研轻量 DoH/DoT 加密 DNS 中继：rustls 驱动与防运营商劫持（Z6X RustDoH）》**
纯 Rust 自研的轻量加密 DNS 转发器，基于纯内存 rustls 库实现 DNS-over-HTTPS 与 DNS-over-TLS 解析，杜绝电视端 DNS 污染且常驻内存低至 2.5MB。""",
            Verdict.Unverified,
            """**去向：不纳入**
53 端口不能绑定；Clash 已加密 DNS。
原文：`Z6xToolBox.App/Content/Modules/RustServices/10_DohDotEncryptedDnsProxyData.cs`""",
        )
        claim(
            """**Rust-16《自研用户态带宽整形与令牌桶限速器：无 GC 流量调度（Z6X RustBandwidthLimiter）》**
纯 Rust 自研的轻量用户态流量整形中继，基于令牌桶（Token Bucket）算法对后台下载与同步流量做平滑限速，杜绝占用大屏观影与串流游戏带宽。""",
            Verdict.Confirmed,
            """**去向：不纳入（第三期可作为 files 限速选项）**
可行，暂无需求。
原文：`Z6xToolBox.App/Content/Modules/RustServices/16_TokenBucketBandwidthLimiterData.cs`""",
        )
        claim(
            """**Rust-20《自研零拷贝网络数据包嗅探与流特征分析器：AF_PACKET 环形缓冲（Z6X RustSniff）》**
纯 Rust 开发的高效网络抓包与链路特征探测探针，基于 Linux AF_PACKET 环形内存映射，实时统计投屏与串流丢包抖动，内存常驻仅 1.8MB。""",
            Verdict.Disproved,
            """**去向：不可行**
AF_PACKET 需要 CAP_NET_RAW，shell 没有；原文提的 setcap 需要 root。
原文：`Z6xToolBox.App/Content/Modules/RustServices/20_ZeroCopyPacketSnifferData.cs`""",
        )
        claim(
            """**Rust-24《自研动态虚拟 Tun 网卡 IP 流量分流器：用户态报文分发（Z6X RustTunRouter）》**
纯 Rust 编写的用户态 TUN 虚拟网卡报文路由器，基于轻量网络栈 smoltcp 拦截并按目标 IP 分流，常驻内存仅 2.2MB。""",
            Verdict.Disproved,
            """**去向：不可行**
/dev/net/tun 对 shell 不可访问（实测）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/24_UserSpaceTunPacketRouterData.cs`""",
        )
        claim(
            """**Rust-26《自研 Epoll 驱动轻量 Socks5/HTTP 混合反向代理：透明分流与穿透（Z6X RustHybridProxy）》**
纯 Rust 开发的高并发 Socks5/HTTP 混合双协议代理，利用 Linux Epoll 单线程事件驱动，常驻内存仅 1.5MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，Clash 已提供混合端口。
原文：`Z6xToolBox.App/Content/Modules/RustServices/26_EpollHybridSocksHttpProxyData.cs`""",
        )
        claim(
            """**Rust-34《自研微秒级 ICMP/UDP 网络时延与抖动雷达：硬件时间戳与链路质检（Z6X RustPingRadar）》**
纯 Rust 编写的高精度网络时延与抖动探测探针，基于微秒级单调时钟与原始套接字，常驻内存仅 600KB。
原文说：「实测前应通过 `cmd wifi set-low-latency-mode enabled` 开启低延迟模式」""",
            Verdict.Confirmed,
            """**去向：tools·ping（一期）**
可行：普通身份可发 ICMP（实测）。原文建议的 `cmd wifi set-low-latency-mode enabled` 在这台机器上不存在（cmd wifi 帮助里没有）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/34_HighPrecisionNetworkLatencyJitterProbeData.cs`""",
        )
        claim(
            """**Rust-35《自研轻量 mDNS/DNS-SD 局域网服务发现与广播器：多播 UDP 零配置网络（Z6X RustMdnsAnnouncer）》**
纯 Rust 编写的零配置 mDNS 广播守护器，自动在局域网宣布极米上的 WebDAV、SSH 与媒体服务，常驻内存仅 1MB。""",
            Verdict.Unverified,
            """**去向：第三期候选**
UDP 5353 已被系统 mdnsd 占用（实测），能否共存未验证。
原文：`Z6xToolBox.App/Content/Modules/RustServices/35_MicroMdnsServiceDiscoveryData.cs`""",
        )
        claim(
            """**Rust-42《自研多上游 DNS 并发竞速与内存缓存解析器：最优链路择优（Z6X RustDnsRace）》**
纯 Rust 编写的多上游并发 DNS 竞速代理，同时向下发阿里、腾讯、Cloudflare 与网关发起查询并采信首包回包，常驻内存仅 1.5MB。
原文说：「需在 shell 中通过 `setprop net.dns1 127.0.0.1` 引导系统优先走本地代理」""",
            Verdict.Unverified,
            """**去向：不纳入**
53 端口不能绑定。原文让用 `setprop net.dns1` 引导系统 DNS——安卓 8 以后系统不再使用 net.dns1（实测这个属性为空），这个办法无效。
原文：`Z6xToolBox.App/Content/Modules/RustServices/42_DnsConcurrencyRacerData.cs`""",
        )
        claim(
            """**Rust-46《自研多屏协同与低延迟投屏组播信令中继：UDP 多播状态同步（Z6X RustCastSignal）》**
纯 Rust 编写的多屏协同微秒级信令中继服务，基于 UDP 局域网组播实现手机、Steam Deck 与极米投影仪之间的状态秒同步，常驻内存仅 800KB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，没有使用方。
原文：`Z6xToolBox.App/Content/Modules/RustServices/46_UdpBroadcastScreenMirrorSignalingData.cs`""",
        )
    }

    related("review-summary", "spec-hub", "spec-tools")
}

val ReviewControl = module("review-control", "审核：遥控、自动化与通知") {
    keywords = "按键、遥控网页、Webhook、通知、智能家居、蓝牙、串口……"
    overview = """
        agy 提案逐篇审核（20 篇）。每张卡片：原文标题和摘要照录；有具体技术说法的另外摘一句原文；「实测」写去向和依据。全文见旧 C# 文件。
    """
    proposal()

    audit("逐篇审核") {
        claim(
            """**Go-06《智能家居控制桥接网关：轻量 HTTP / MQTT Webhook 中继》**
基于 Go 编写的微型控制网关，接收智能家居（Home Assistant）网络指令，通过本地 Android 原生事件控制投影仪各项硬件功能。
原文说：「am start -a android.intent.action.VIEW -d "xgimi://com.xgimi.home/hdmi"」""",
            Verdict.Confirmed,
            """**去向：hub·control（一期）**
按键控制可行（input keyevent 实测可用）。但原文切 HDMI 用的 `xgimi://com.xgimi.home/hdmi` **实测没有任何界面响应**（官方桌面已卸载）；信号源切换要另找办法，备用键值 178 未测。
原文：`Z6xToolBox.App/Content/Modules/GoServices/06_HomeAssistantDeviceBridgeData.cs`""",
        )
        claim(
            """**Go-11《自研局域网中继：免登文本便签与投屏直调（Z6X LocalPaste）》**
纯 Go 自研的局域网轻量便签与指令中继，手机扫码粘贴文本或视频直链，一键向极米原生系统派发播放或输入指令。
原文说：「Android 系统会自动调起内置的最佳播放器（或 VLC）进行硬件解码播放」""",
            Verdict.Confirmed,
            """**去向：hub·paste（一期）**
可行。input text 只支持 ASCII，原文说对了。但原文说 `am start VIEW` 会「自动调起最佳播放器」——实测视频链接**没有默认播放器**，会弹出选择框。
原文：`Z6xToolBox.App/Content/Modules/GoServices/11_LocalClipboardTextRelayData.cs`""",
        )
        claim(
            """**Go-15《自研手机虚拟触控板与按键宏中继：WebSocket 手势与按键映射（Z6X RemoteBridge）》**
纯 Go 自研的 WebSocket 遥控与触控板中继服务，手机扫码即可化身触控板控制大屏鼠标指针，并支持一键触发预设按键宏指令。
原文说：「在本地调用 Linux shell `input swipe` 或注入鼠标指针事件」""",
            Verdict.Confirmed,
            """**去向：hub·control（一期按键与宏，二期触控板）**
按键宏可行。触控板需要注入鼠标事件：/dev/uinput 可写（实测），但系统是否接受虚拟鼠标未验证，放第二期。
原文：`Z6xToolBox.App/Content/Modules/GoServices/15_VirtualTouchpadKeyMacroData.cs`""",
        )
        claim(
            """**Go-17《自研局域网网络唤醒与设备巡检网关：UDP 魔术包与在线感知（Z6X WakeOnLanHub）》**
纯 Go 自研的常驻网络唤醒（WOL）网关，提供一键唤醒 PC/NAS 网页接口，并周期性巡检局域网主机在线状态。""",
            Verdict.Confirmed,
            """**去向：hub·wol（一期）**
可行。普通身份 ICMP ping 实测可用；魔术包是普通 UDP 广播。
原文：`Z6xToolBox.App/Content/Modules/GoServices/17_WakeOnLanDeviceInspectorData.cs`""",
        )
        claim(
            """**Go-18《自研大屏悬浮通知中心：Webhook 监听与原生 Toast 派发（Z6X NotifyHub）》**
纯 Go 编写的微型通知网关，接收 Home Assistant、手机或自动化服务的 HTTP Webhook，在投影屏幕右上角无感悬浮弹出半透明通知小贴条。
原文说：「方案 B：通过极简的前端 WebView 浮窗（基于 `android.view.WindowManager` 的悬浮窗图层）」""",
            Verdict.Unverified,
            """**去向：第二期·notify（先验证）**
`cmd notification post` 存在（实测），但电视上能不能显示出来未验证。原文的方案 B（悬浮窗 TYPE_APPLICATION_OVERLAY）需要一个 App，shell 做不到。
原文：`Z6xToolBox.App/Content/Modules/GoServices/18_BigScreenFloatingNotificationData.cs`""",
        )
        claim(
            """**Go-23《自研本地轻量 MQTT Broker：纯 Go 物联网消息总线与 WebSocket 桥接（Z6X MqttHub）》**
纯 Go 自研的轻量 MQTT 消息服务器，为家庭物联网传感器与智能家居提供本地消息发布/订阅中枢，无需独立服务器或 NAS 硬件。""",
            Verdict.Confirmed,
            """**去向：第三期候选**
可行（1883 是高位端口）。目前没有 MQTT 设备的需求，不做。
原文：`Z6xToolBox.App/Content/Modules/GoServices/23_LightweightMqttBrokerData.cs`""",
        )
        claim(
            """**Go-31《自研 JSON-RPC 2.0 远程控制与自动化调度服务：系统调用抽象（Z6X RpcBridge）》**
纯 Go 编写的标准 JSON-RPC 2.0 远程过程调用服务，统一暴露投影仪音量控制、信号源切换与应用启动等系统接口，常驻内存仅 11MB。""",
            Verdict.Confirmed,
            """**去向：hub·control（一期，合并）**
合并到 control 模块，用简单的 REST 接口代替 JSON-RPC。
原文：`Z6xToolBox.App/Content/Modules/GoServices/31_JsonRpcRemoteControlBridgeData.cs`""",
        )
        claim(
            """**Go-34《自研 Webhook 事件监听与自动化动作分发器：多源 HTTP 触发（Z6X HookHub）》**
纯 Go 编写的轻量 Webhook 接收与指令分发网关，接收外部自动化通知并联动极米本地弹窗、下载或休眠，常驻内存仅 10MB。""",
            Verdict.Confirmed,
            """**去向：hub·control（一期，合并）**
合并到 control：外部 Webhook 触发预设动作（按键序列、打开应用）。参数用数组传给 exec 不拼字符串，原文这条是对的。
原文：`Z6xToolBox.App/Content/Modules/GoServices/34_TimedTaskWebhookDispatcherData.cs`""",
        )
        claim(
            """**Go-40《自研 RTSP/RTP 投屏会话协商与 SDP 信令中继代理：投屏链路管理（Z6X CastRelay）》**
纯 Go 开发的 RTSP 会话控制与 SDP 信令中继代理，规范化管理投屏协议握手生命周期与心跳保活，常驻内存仅 14MB。""",
            Verdict.Disproved,
            """**去向：不可行**
Miracast 由系统投屏应用通过 Wi-Fi P2P 建立，外部进程插不进信令中间。
原文：`Z6xToolBox.App/Content/Modules/GoServices/40_ScreenMiracastSignalingRelayData.cs`""",
        )
        claim(
            """**Go-44《自研智能家居 Matter 与 Zigbee2MQTT 状态中继网关：协议桥接（Z6X MatterGate）》**
纯 Go 编写的微型 Matter/HomeKit 与 MQTT 协议转接网关，将投影仪开关机、输入源与亮度虚拟化为标准智能家居配件，常驻内存仅 16MB。""",
            Verdict.Unverified,
            """**去向：第三期候选**
HomeKit 需要 mDNS：UDP 5353 已被系统 mdnsd（uid 1020）占用（实测），能否共用未验证。
原文：`Z6xToolBox.App/Content/Modules/GoServices/44_SmartHomeMatterBridgeData.cs`""",
        )
        claim(
            """**Go-54《自研 MQTT 传感器时序数据收集与轻量归档服务：环境指标沉淀（Z6X MetricStore）》**
纯 Go 编写的微型时序数据接收与归档引擎，监听家庭 MQTT 传感器主题并以紧凑格式保存至 U 盘，常驻内存仅 14MB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，没有传感器数据需求。
原文：`Z6xToolBox.App/Content/Modules/GoServices/54_MqttInfluxDbMetricsCollectorData.cs`""",
        )
        claim(
            """**Rust-02《自研底层输入事件拦截与按键重映射：直读 /dev/input 与零延迟宏（Z6X RustEvdev）》**
基于 Rust 零成本 C-FFI 特性自研的输入设备事件监听器，直接读取 Linux 内核 /dev/input/event* 设备节点，实现极米遥控器按键双击/长按重映射。
原文说：「启动 Rust 按键监听服务（绑定蓝牙遥控器 event2）」""",
            Verdict.Confirmed,
            """**去向：tools·keymap（一期）**
可行：shell 在 input 组，遥控器节点可读（实测）。但原文写的遥控器节点 event2 不对：event2 是虚拟键盘 qwerty，遥控器是 event13。
原文：`Z6xToolBox.App/Content/Modules/RustServices/02_EvdevInputEventRemapperData.cs`""",
        )
        claim(
            """**Rust-07《自研低功耗蓝牙与原始串口数据中继：HCI 套接字直读与零拷贝过滤（Z6X RustBleBridge）》**
纯 Rust 自研的硬件层蓝牙 BLE 原始广播中继服务，直接监听 Linux HCI Socket，无感抓取家庭 BLE 传感器报文并低开销中转。
原文说：「极米 Z6X Pro 配备了 Wi-Fi/BT 组合芯片（MT7921）」""",
            Verdict.Disproved,
            """**去向：不可行**
内核里没有 hci 蓝牙设备（/sys/class/bluetooth 为空，实测），原始 HCI 套接字无从打开。原文说的网卡型号 MT7921 也不对，USB ID 是 0e8d:7663。
原文：`Z6xToolBox.App/Content/Modules/RustServices/07_BluetoothLowEnergyBridgeData.cs`""",
        )
        claim(
            """**Rust-13《自研低延迟 WebSocket 广播引擎：无锁并发与多端大屏同步（Z6X RustWsHub）》**
纯 Rust 自研的事件驱动 WebSocket 消息扇出中枢，专为家庭多设备同时监听极米状态设计，在多长连接下依然保持低于 2MB 的极简物理内存。""",
            Verdict.Confirmed,
            """**去向：不单独做**
hub 需要推送时在 Go 里实现，不再起一个 Rust 进程。
原文：`Z6xToolBox.App/Content/Modules/RustServices/13_LowLatencyWebSocketEngineData.cs`""",
        )
        claim(
            """**Rust-18《自研高频事件注入与微秒级按键模拟器：C-ABI 直写事件（Z6X RustInputInjector）》**
纯 Rust 编写的硬件级按键模拟器，绕过 Android 慢速的 app_process Java 虚拟机调用，直接向底层事件流直写二进制 input_event，实现微秒级高频连击与平滑手势。
原文说：「单次调用耗时长达 200ms ~ 400ms」""",
            Verdict.Confirmed,
            """**去向：tools·key（一期）**
可行：遥控器节点和 /dev/uinput 对 shell 都可写（实测）。原文说 input keyevent 每次 200~400ms，实测 79~106ms（三次）；仍然慢，值得做。节点 event2 不对，应为 event13。
原文：`Z6xToolBox.App/Content/Modules/RustServices/18_HighFrequencyInputInjectorData.cs`""",
        )
        claim(
            """**Rust-21《自研微型异步 MQTT 协议解析与网关中继：边缘传感器采集（Z6X RustMqttEdge）》**
纯 Rust 编写的超轻量 MQTT v3.1.1/v5.0 边缘代理与协议中继器，专为低开销传感器遥测与智能家居转发设计，常驻内存仅 1.2MB。
原文说：「标准 MQTT 端口 1883 需要 root 权限」""",
            Verdict.Confirmed,
            """**去向：不纳入**
与 Go-23 重复。原文说「标准 MQTT 端口 1883 需要 root 权限」不对：1883 大于 1024，普通身份可以绑定。
原文：`Z6xToolBox.App/Content/Modules/RustServices/21_LightweightMqttEdgeNodeData.cs`""",
        )
        claim(
            """**Rust-23《自研硬件串口与 USB 虚拟串口通信守护器：mio 非阻塞硬件总线（Z6X RustSerialHub）》**
纯 Rust 编写的非阻塞 USB/TTL 串口通信网关，利用 mio 与 termios C-ABI 对接单片机与传感器，常驻内存仅 800KB。
原文说：「在非 root 的 shell 下启动前需执行 `chmod 666 /dev/ttyUSB0`」""",
            Verdict.Disproved,
            """**去向：不可行**
目前没有串口设备节点（实测无 /dev/ttyUSB*、/dev/ttyACM*）；原文说的 `chmod 666 /dev/ttyUSB0` 需要 root。
原文：`Z6xToolBox.App/Content/Modules/RustServices/23_UsbSerialHardwareBridgeData.cs`""",
        )
        claim(
            """**Rust-39《自研 HDMI-CEC 协议监听与设备联动控制器：/dev/cec0 字符设备直连（Z6X RustCecHub）》**
纯 Rust 编写的 HDMI-CEC 消费电子控制总线守护器，通过裸调 /dev/cec0 字符设备实现开机联动功放音响与待机休眠，常驻内存仅 700KB。""",
            Verdict.Disproved,
            """**去向：不可行**
/dev/cec0 不存在（实测）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/39_HdmiCecBusControllerData.cs`""",
        )
        claim(
            """**Rust-41《自研 USB 虚拟 HID 键鼠协议模拟注入器：/dev/hidg0 硬件直写（Z6X RustHidEmu）》**
纯 Rust 开发的标准 USB HID 描述符驱动与键鼠模拟注入器，通过直接向内核 /dev/hidg0 写入标准报文模拟外接键盘，常驻内存仅 600KB。
原文说：「Android 下 `/dev/uinput` 默认可能只属于 `system` 或 `root`，在普通 shell 启动前需通过 `chmod 666 /dev/uinput` 赋予读写权限」""",
            Verdict.Disproved,
            """**去向：hidg0 部分不可行，uinput 部分合并到 tools·key**
/dev/hidg0 不存在（没有 USB gadget）。原文说 /dev/uinput 默认只属于 system 或 root、需要 chmod——实测 /dev/uinput 属于 uhid 组，shell 就在这个组里，可直接读写。
原文：`Z6xToolBox.App/Content/Modules/RustServices/41_UsbHidDeviceEmulatorData.cs`""",
        )
        claim(
            """**Rust-45《自研蓝牙 HCI 原始套接字高速扫描探针：内核协议栈抓包（Z6X RustHciSniff）》**
纯 Rust 编写的蓝牙底层 HCI 原始数据包捕获工具，直接通过 AF_BLUETOOTH 套接字抓取遥控器按键与外设广播包，常驻内存仅 900KB。""",
            Verdict.Disproved,
            """**去向：不可行**
内核没有 hci 设备（实测），原始 HCI 套接字打不开。
原文：`Z6xToolBox.App/Content/Modules/RustServices/45_BluetoothRawHciScannerData.cs`""",
        )
    }

    related("review-summary", "spec-hub", "spec-tools")
}

val ReviewSystem = module("review-system", "审核：系统、运维与架构") {
    keywords = "看门狗、监控、日志、终端、隔离、两个聚合架构……"
    overview = """
        agy 提案逐篇审核（23 篇）。每张卡片：原文标题和摘要照录；有具体技术说法的另外摘一句原文；「实测」写去向和依据。全文见旧 C# 文件。
    """
    proposal()

    audit("逐篇审核") {
        claim(
            """**Go-07《24 小时后台常驻保障：Wakelock 锁与网络待机配置》**
分析 Android TV 熄屏进入低功耗待机时的休眠与断网机制，提供 CPU 唤醒锁与网络保持策略，确保微服务全天候可用。
原文说：「按下遥控器关机后，系统通常在 15 分钟后进入深度睡眠（Doze Mode），切断 Wi-Fi 芯片供电并挂起 CPU」""",
            Verdict.Unverified,
            """**去向：hub 运行前提（已实测：关屏在线、关机睡眠）**
wifi_sleep_policy 实测已经是 2。极米电源菜单只有关屏、关机、重启、定时关机，没有「待机」。**关屏已实测**：安卓仍是 Awake、显示 ON，30 分钟内网络和所有端口在线，原文担心的「15 分钟后进入 Doze 断网」没有发生。「关机」也已实测：其实是挂起到内存的睡眠，约 14 秒后断网，开机后进程原样恢复。stay_on_while_plugged_in 实测是 0。
原文：`Z6xToolBox.App/Content/Modules/GoServices/07_NetworkKeepAliveStandbyData.cs`""",
        )
        claim(
            """**Go-10《自研硬件看板与系统自愈探针：/proc 解析与定时清理（Z6X SysExporter）》**
纯 Go 自研的系统健康度导出器与自愈看门狗，实时提取 Linux 内核温控与内存指标，并在夜间自动清理无用日志与缓存。
原文说：「通过直接读取……`/sys/class/thermal/thermal_zone*/temp`（获取光机与核心摄氏度）」""",
            Verdict.Confirmed,
            """**去向：hub·metrics（一期）**
读 /proc 和 /sys 可行（实测）。原文说能读「光机温度」不成立：温度区只有 cpu_thermal 和 vou_thermal。「夜间自愈清理」不需要，不做。
原文：`Z6xToolBox.App/Content/Modules/GoServices/10_HardwareTelemetryWatchdogData.cs`""",
        )
        claim(
            """**Go-20《自研网页直连终端：WebSocket PTY 桥接与免 ADB 管理（Z6X WebShell）》**
纯 Go 自研的网页轻量终端服务，基于 WebSocket 与 PTY 伪终端桥接 Android 原生 Shell，手机或平板浏览器即可直接执行 Linux 运维命令。""",
            Verdict.Confirmed,
            """**去向：第二期·webshell**
/dev/ptmx 可读写（实测），可行。等于把 shell 权限开放给网页，必须鉴权、默认关闭。
原文：`Z6xToolBox.App/Content/Modules/GoServices/20_WebTerminalDirectAccessData.cs`""",
        )
        claim(
            """**Go-28《自研 Prometheus 规范系统与存储指标导出器：/metrics 协程输出（Z6X NodeExporter）》**
纯 Go 编写的轻量 Prometheus 指标导出器，采集极米 CPU、内存、U 盘 I/O、光机温度与网络流量并暴露 /metrics 端点，常驻内存仅 11MB。""",
            Verdict.Confirmed,
            """**去向：hub·metrics（一期，合并）**
与 Go-10 合并：metrics 模块同时提供 JSON 和 Prometheus 文本格式。
原文：`Z6xToolBox.App/Content/Modules/GoServices/28_PrometheusMetricsNodeExporterData.cs`""",
        )
        claim(
            """**Go-29《自研局域网统一认证与反向代理网关：JWT 与 BasicAuth 鉴权（Z6X AuthProxy）》**
纯 Go 编写的微型反向代理与统一鉴权层，为本地无密码 Web 界面（Aria2、WebDAV、终端）提供统一登录保护，常驻内存仅 13MB。""",
            Verdict.Confirmed,
            """**去向：hub·core（一期，鉴权）**
简化为 core 的 Bearer token 鉴权，不做 JWT 单点登录。
原文：`Z6xToolBox.App/Content/Modules/GoServices/29_OAuth2LocalAuthProxyData.cs`""",
        )
        claim(
            """**Go-43《自研嵌入式 Loki 规范日志聚合与查询端点：轻量时间线索引（Z6X LogLoki）》**
纯 Go 编写的微型日志聚合服务，兼容 Grafana Loki 推送与查询协议，为电视上运行的数十个后台微服务提供统一排错检索接口，常驻内存仅 15MB。""",
            Verdict.Confirmed,
            """**去向：hub·core（一期，简化）**
简化为 core 的日志轮转和 `/api/logs`（最近 N 行），不做 Loki 协议。
原文：`Z6xToolBox.App/Content/Modules/GoServices/43_LogAggregationLokiLiteData.cs`""",
        )
        claim(
            """**Go-58《自研配置项可视化 Web 动态表单生成器：零前端依赖管理界面（Z6X ConfigWeb）》**
纯 Go 编写的微型可视化配置管理服务，根据 YAML/JSON 注释自动动态生成 Web 表单供手机或电脑修改电视后台参数，常驻内存仅 11MB。""",
            Verdict.Confirmed,
            """**去向：hub·core（二期，配置页）**
第一期先在首页只读展示配置，第二期再做网页修改。
原文：`Z6xToolBox.App/Content/Modules/GoServices/58_DynamicFormWebConfigUiData.cs`""",
        )
        claim(
            """**Go-60《架构整合：极米 Go 原生多服务单二进制聚合架构（Z6X Hub）》**
将 WebDAV、Aria2、MQTT、影视刮削、S3 与流媒体等各项 Go 原生服务整合成单一二进制程序，统一配置文件与生命周期调度，总常驻内存约 35MB。
原文说：「storage_root: /mnt/media_rw/USB_DISK」""",
            Verdict.Confirmed,
            """**去向：hub 架构（规格基础）**
单进程、配置开关模块、recover 隔离、端口集中规划——这些设计采纳为 z6x-hub 的架构。但原文的配置用了 /mnt/media_rw（不可访问），「CPU 上下文切换降低 70%」「59 项子服务」没有依据。
原文：`Z6xToolBox.App/Content/Modules/GoServices/60_UnifiedGoMonolithHubArchitectureData.cs`""",
        )
        claim(
            """**Rust-01《自研极限低内存系统看门狗：RAII 资源管理与 1MB 免杀守护（Z6X RustWatchdog）》**
基于 Rust 无 GC 特性自研的微型系统看门狗，常驻物理内存仅约 1MB，负责全天候监控 Go 服务集群存活并在系统内存紧张时保持自身免疫免杀。
原文说：「当电视前台播放 4K 超高清电影……连看门狗本身都会被 Android TV 的 LMK（低内存查杀）机制一同杀死」""",
            Verdict.Disproved,
            """**去向：tools·run（二期，改为进程守护）**
前提不成立：原文担心看门狗会被低内存回收杀掉，实测从 ADB 启动的进程 oom 分是 -1000，不会被杀。崩溃重启的功能保留，并入 tools 的 run 子命令。
原文：`Z6xToolBox.App/Content/Modules/RustServices/01_LowMemorySystemWatchdogData.cs`""",
        )
        claim(
            """**Rust-08《自研系统日志环形缓冲与崩溃黑匣子：固定内存池与断电落盘（Z6X RustLogBox）》**
纯 Rust 自研的固定尺寸环形内存日志收集器，实时捕获 Linux 内核 dmesg 与 logcat 关键崩溃堆栈，系统发生异常崩溃时秒级落盘存证。
原文说：「后台通过读取 `/proc/kmsg`（内核日志流）」""",
            Verdict.Disproved,
            """**去向：不纳入**
/proc/kmsg 不可读、dmesg 没权限（实测）。只剩 logcat 能读，系统自己就有 crash 缓冲区。
原文：`Z6xToolBox.App/Content/Modules/RustServices/08_CrashBlackboxLogRecorderData.cs`""",
        )
        claim(
            """**Rust-12《自研零开销日志轮转与脱敏器：SIMD 过滤与定额归档（Z6X RustLogSanitizer）》**
纯 Rust 编写的流式日志过滤器与自动轮转工具，基于 SIMD 文本匹配实时擦除日志中的 Token、MAC 地址与局域网 IP，并按定额大小自动分割压缩归档。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行；hub 自己保证不写敏感信息即可。
原文：`Z6xToolBox.App/Content/Modules/RustServices/12_LogSanitizerRotatorData.cs`""",
        )
        claim(
            """**Rust-14《自研极简硬件指标导出器：/proc 流式解析与 Prometheus 导出（Z6X RustSensorExporter）》**
纯 Rust 编写的超轻量硬件监控导出器，单文件仅 600KB，常驻物理内存低于 1MB，替代 15MB 且占用 12MB+ 内存的 Go 官方 node_exporter。""",
            Verdict.Confirmed,
            """**去向：tools·sys（一期）**
可行（读 /proc、/sys 实测可用）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/14_RustHardwareMetricsExporterData.cs`""",
        )
        claim(
            """**Rust-19《自研服务编排与热重载管理器：SIGHUP 信号与依赖拓扑（Z6X RustSupervisor）》**
纯 Rust 编写的嵌入式微型进程守护与编排总线，负责一键纳管极米上所有 Go/Rust 原生二进制的生命周期、依赖拓扑与热重载，自身内存仅 1.5MB。""",
            Verdict.Confirmed,
            """**去向：tools·run（二期）**
可行，和 Rust-01 合并为一个极简进程守护。
原文：`Z6xToolBox.App/Content/Modules/RustServices/19_MicroProcessSupervisorData.cs`""",
        )
        claim(
            """**Rust-28《自研系统定时微任务与毫秒级事件调度器：时间轮算法与低功耗巡检（Z6X RustMicroCron）》**
纯 Rust 开发的嵌入式时间轮微秒调度引擎，负责低开销硬件巡检、定时垃圾回收与定时投屏清理，常驻内存仅 600KB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
hub 内部有定时功能即可。
原文：`Z6xToolBox.App/Content/Modules/RustServices/28_TimerWheelMicroCronData.cs`""",
        )
        claim(
            """**Rust-29《自研光机与 CPU 动态温控调频巡检器：内核 sysfs 实时采样（Z6X RustThermalWatch）》**
纯 Rust 编写的光机温度与处理器降频监控探针，无动态内存分配轮询 sysfs 传感器，常驻内存仅 500KB。
原文说：「追踪 CPU 核心、GPU 以及光学投影模组温度趋势」""",
            Verdict.Confirmed,
            """**去向：合并到 tools·sys**
可行。原文说能读「光机温度」不成立：只有 cpu_thermal、vou_thermal。
原文：`Z6xToolBox.App/Content/Modules/RustServices/29_ThermalThrottleMonitorData.cs`""",
        )
        claim(
            """**Rust-31《自研轻量级 inotify 增量文件变动监听器：内核事件驱动触发（Z6X RustInotifySync）》**
纯 Rust 编写的 Linux inotify 目录增量变动监听守护器，实时捕获下载完成与 U 盘插拔写入，常驻内存仅 700KB。""",
            Verdict.Confirmed,
            """**去向：tools·watch（一期）**
可行。原文说 max_user_watches 是 8192，实测正是 8192；但扩大它的 echo 命令需要 root。
原文：`Z6xToolBox.App/Content/Modules/RustServices/31_InotifyFileEventSynchronizerData.cs`""",
        )
        claim(
            """**Rust-32《自研直接读写 /dev/graphics/fb0 截图探针：DRM/FB 内存映射（Z6X RustFbCapture）》**
纯 Rust 编写的硬件帧缓冲直读截图探针，通过 mmap 直接读取 Linux 显示缓冲区并做格式转换，常驻内存仅 1.5MB。""",
            Verdict.Disproved,
            """**去向：不可行**
/dev/graphics/fb0 属于 graphics 组，shell 不在这个组（实测）。截图用 adb exec-out screencap 即可（见「给投影仪截图」）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/32_RawFrameBufferScreenshotCaptureData.cs`""",
        )
        claim(
            """**Rust-33《自研跨进程共享内存 mmap 配置同步总线：无锁原子变量状态共享（Z6X RustShmConfig）》**
纯 Rust 编写的无锁共享内存配置与状态总线，基于 Linux tmpfs/ashmem 内存映射实现纳秒级跨进程标志位同步，常驻内存仅 400KB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，没有需求。
原文：`Z6xToolBox.App/Content/Modules/RustServices/33_MemoryMappedConfigStoreData.cs`""",
        )
        claim(
            """**Rust-37《自研微型 PTY 虚拟终端会话保持与复用器：POSIX 伪终端托管（Z6X RustMicroTmux）》**
纯 Rust 开发的嵌入式 PTY 终端会话托管器，免安装 tmux/screen 即可保持 SSH 后台会话与重新挂载，常驻内存仅 900KB。""",
            Verdict.Confirmed,
            """**去向：第三期候选**
/dev/ptmx 可用（实测），可行，优先级低。
原文：`Z6xToolBox.App/Content/Modules/RustServices/37_RawPtyTerminalMultiplexerData.cs`""",
        )
        claim(
            """**Rust-43《自研零动态库 Web 控制台网关：纯 Rust WebSocket 与 PTY 终端（Z6X RustWebTerm）》**
纯 Rust 编写的轻量 Web 终端网关，静态内嵌 HTML/JS 终端与 WebSocket PTY 桥接，无需外部 Node/Python 依赖，常驻内存仅 1.8MB。""",
            Verdict.Confirmed,
            """**去向：合并到 hub·webshell（二期）**
与 Go-20 重复，网页终端只做一份（Go）。
原文：`Z6xToolBox.App/Content/Modules/RustServices/43_LowOverheadWebTerminalData.cs`""",
        )
        claim(
            """**Rust-44《自研跨进程无锁共享内存环形缓冲区 IPC：单生产者单消费者管道（Z6X RustShmRing）》**
纯 Rust 编写的超高速跨进程环形缓冲区，基于 Linux 共享内存与无锁原子指针，实现音频与遥测数据零拷贝传输，常驻内存仅 500KB。""",
            Verdict.Confirmed,
            """**去向：不纳入**
可行，没有需求。
原文：`Z6xToolBox.App/Content/Modules/RustServices/44_ZeroCopyRingBufferIpcData.cs`""",
        )
        claim(
            """**Rust-48《自研 Linux Cgroups 轻量资源隔离沙箱：非特权子进程配额管理（Z6X RustJail）》**
纯 Rust 编写的嵌入式进程资源隔离沙箱，基于 Linux cgroups v1/v2 限制后台子进程 CPU 配额与最大内存，常驻内存仅 600KB。""",
            Verdict.Disproved,
            """**去向：不可行**
/sys/fs/cgroup 对 shell 不可写（实测）。原文的退路 setpriority（nice）可以，但不值得单独做工具。
原文：`Z6xToolBox.App/Content/Modules/RustServices/48_LinuxCgroupResourceJailData.cs`""",
        )
        claim(
            """**Rust-49《架构演进：极米全功能 Rust 原生微服务底座聚合工程架构（Z6X Rust Hub）》**
将系统看门狗、evdev 按键拦截、无锁音频混音、BLE 蓝牙中继与崩溃黑匣子整合成单二进制无 GC 底座，总常驻物理内存约 4MB。
原文说：「Rust 专注跑底层硬件（Watchdog、evdev 输入、音频硬件直写、蓝牙原始套接字）」""",
            Verdict.Unverified,
            """**去向：改为命令集（见 z6x-tools 规格）**
原文把 Rust 做成第二个常驻底座。审核后：Rust 里可行的几乎都是「跑一次给结果」的工具，改为 BusyBox 式命令集；只有按键重映射常驻。原文挑选的五个底座模块里，音频混音、BLE 中继在这台机器上不可行，黑匣子缺内核日志权限。
原文：`Z6xToolBox.App/Content/Modules/RustServices/49_UnifiedRustCoreDaemonData.cs`""",
        )
    }

    related("review-summary", "spec-hub", "spec-tools")
}
