package z6x.content.proposals

import z6x.framework.Verdict
import z6x.framework.module

// 由 notes/gen.py 根据 notes/proposals.py 生成，不要直接改这个文件。

val ReviewSummary = module("review-summary", "提案总表：109 个小项目的去向") {
    keywords = "Go 60 个 · Rust 49 个 · 第一期 / 第二期 / 第三期 / 不纳入 / 不可行"
    overview = """
        agy 推荐过 109 个可以在投影仪上跑的小项目（Go 60 个、Rust 49 个）。审核原则：只做这台投影仪上**实测可行**、日常**用得上**、且**不重复**的；Go 的并入 z6x-hub（常驻服务），Rust 的并入 z6x-tools（命令集）。
        每个项目的用途、做法和判断依据在五个分类页里。
    """
    proposal()

    why("统计") {
        facts(
            "第一期" to "hub：core、files、paste、control、wol、metrics、speed；tools：sys、ports、key、keymap、hash、watch、ping",
            "第二期" to "hub：notify、lanscan、webshell、dlna、配置页、iperf3；tools：iobench、pack、run",
            "第三期候选" to "离线下载、相册、阅读、直播源、MQTT、HomeKit、Tailscale、mDNS、终端会话保持等",
            "不可行" to "需要 root、1024 以下端口、抓包权限、声卡、tun、cgroup、蓝牙 hci、CEC、hidg0、fb0 的项目",
            "重复合并" to "WebDAV、网页终端、MQTT、监控、DNS、代理等在 Go 和 Rust 里各写过一遍，只保留一份",
        )
    }

    story("「不纳入」的都是些什么") {
        text("""
            1. 已经有现成的在做：DNS 去广告、加密 DNS、局域网代理、PAC、用户态隧道（Clash 已负责）；SMB、S3、SFTP、Git 仓库（WebDAV 已覆盖文件共享）；Go 和 Rust 的重复实现。
            2. 要把投影仪暴露到公网：路由器端口映射、动态域名、HTTPS 证书、Tailscale 中继。本项目只在家里局域网用。
            3. 依赖外网服务，或者目前用不上：影视刮削、字幕下载、RSS 追更、磁力转种子、直播录制、定时测速、网盘挂载、传感器数据存储、视频切片。需求变了可以从第三期候选里提上来。
            4. 偏技术演示，日常没有使用场景：共享内存、只读索引库、日志脱敏、UDP 纠错、组播信令、P2P 分发、限速代理、浏览器网页投屏。
        """)
    }

    verify("逐个去向") {
        facts(
            "Go-01 局域网文件共享（WebDAV）" to "hub · files（第一期）",
            "Go-02 离线下载" to "第三期候选（作为外部程序部署，不在 hub 里重写）",
            "Go-03 DNS 缓存与去广告" to "不纳入（Clash 已负责 DNS 和分流）",
            "Go-04 多画面短视频墙" to "第三期候选",
            "Go-05 家庭音乐服务器（Navidrome）" to "不纳入 hub（需要时单独部署）",
            "Go-06 智能家居控制入口" to "hub · control（第一期）",
            "Go-07 让后台服务 24 小时在线" to "hub 运行前提（已验证，写入规格）",
            "Go-08 大屏相册" to "第三期候选（作为 files 的扩展）",
            "Go-09 Deck 存档与截图自动备份" to "hub · files 的上传接口（第一期）",
            "Go-10 系统状态面板" to "hub · metrics（第一期）",
            "Go-11 手机把文字和链接发给电视" to "hub · paste（第一期）",
            "Go-12 直播源整理" to "第三期候选（与 Go-48、Go-59 合成一个模块）",
            "Go-13 漫画与电子书阅读" to "第三期候选",
            "Go-14 局域网测速" to "hub · speed（第一期）",
            "Go-15 手机当遥控器和触控板" to "hub · control（第一期按键与宏，第二期触控板）",
            "Go-16 DLNA 投屏接收" to "第二期 · dlna",
            "Go-17 网络唤醒" to "hub · wol（第一期）",
            "Go-18 大屏通知" to "第二期 · notify（先验证能不能显示）",
            "Go-19 把投影仪当无线音箱" to "不可行",
            "Go-20 网页终端" to "第二期 · webshell",
            "Go-21 Windows 网络共享（SMB）" to "不纳入（WebDAV 已覆盖文件共享）",
            "Go-22 影视海报刮削" to "不纳入（依赖外网，收益低）",
            "Go-23 物联网消息中心（MQTT）" to "第三期候选",
            "Go-24 局域网代理" to "不纳入（Clash 已有）",
            "Go-25 RSS 追更" to "不纳入（收益低）",
            "Go-26 S3 对象存储" to "不纳入",
            "Go-27 路由器自动端口映射" to "不纳入（安全）",
            "Go-28 Prometheus 监控接口" to "并入 hub · metrics（第一期）",
            "Go-29 统一登录保护" to "简化为 hub · core 的 token 鉴权（第一期）",
            "Go-30 私人 Git 仓库" to "不纳入",
            "Go-31 统一控制接口（JSON-RPC）" to "并入 hub · control（第一期）",
            "Go-32 视频切片点播（HLS）" to "不纳入",
            "Go-33 Tailscale 中继节点" to "不纳入（要对公网开放）",
            "Go-34 Webhook 触发动作" to "并入 hub · control（第一期）",
            "Go-35 SFTP 文件传输" to "不纳入",
            "Go-36 应急 DHCP" to "不可行",
            "Go-37 动态域名" to "不纳入",
            "Go-38 PAC 自动代理" to "不纳入",
            "Go-39 磁力链接转种子" to "不纳入",
            "Go-40 Miracast 投屏信令修正" to "不可行",
            "Go-41 多房间同步音频" to "不可行",
            "Go-42 字幕自动下载" to "不纳入",
            "Go-43 日志集中查看" to "简化为 hub · core 的日志（第一期）",
            "Go-44 接入苹果「家庭」" to "第三期候选",
            "Go-45 宽带定时测速" to "不纳入",
            "Go-46 跨网段网络唤醒" to "并入 hub · wol（第一期）",
            "Go-47 缩略图生成" to "第三期候选（随相册一起）",
            "Go-48 IPTV 列表清洗" to "第三期候选（并入 Go-12）",
            "Go-49 iperf3 测速" to "hub · speed（第二期，可选）",
            "Go-50 网盘挂载" to "不纳入（不必重写）",
            "Go-51 HTTPS 证书自动申请" to "不纳入",
            "Go-52 直播录制" to "不纳入",
            "Go-53 局域网设备扫描" to "第二期 · lanscan",
            "Go-54 传感器数据存储" to "不纳入",
            "Go-55 Tailscale 子网路由" to "第三期候选",
            "Go-56 浏览器网页投屏" to "不纳入",
            "Go-57 重复文件查找" to "并入 tools · hash（第一期）",
            "Go-58 网页改配置" to "hub · core 配置页（第二期；第一期只读展示）",
            "Go-59 节目单生成" to "第三期候选（并入 Go-12）",
            "Go-60 把 Go 服务合成一个程序（Z6X Hub）" to "hub 架构",
            "Rust-01 进程看门狗" to "tools · run（第二期）",
            "Rust-02 遥控器按键重映射" to "tools · keymap（第一期）",
            "Rust-03 低延迟音频混音" to "不可行",
            "Rust-04 文件哈希与查重" to "tools · hash（第一期）",
            "Rust-05 用户态加密隧道" to "不纳入",
            "Rust-06 只读索引库" to "不纳入",
            "Rust-07 蓝牙传感器网关" to "不可行",
            "Rust-08 崩溃黑匣子" to "不纳入",
            "Rust-09 端口敲门" to "不可行",
            "Rust-10 加密 DNS 转发" to "不纳入",
            "Rust-11 快速压缩归档" to "tools · pack（第二期）",
            "Rust-12 日志脱敏与轮转" to "不纳入",
            "Rust-13 WebSocket 广播" to "不单独做",
            "Rust-14 系统指标" to "tools · sys（第一期）",
            "Rust-15 U 盘健康检测" to "tools · iobench（第二期）",
            "Rust-16 限速代理" to "不纳入（以后可作为 files 的限速选项）",
            "Rust-17 Rust 版 WebDAV" to "并入 hub · files",
            "Rust-18 快速按键注入" to "tools · key（第一期）",
            "Rust-19 进程守护" to "tools · run（第二期，与 Rust-01 合并）",
            "Rust-20 抓包分析" to "不可行",
            "Rust-21 Rust 版 MQTT" to "不纳入",
            "Rust-22 Rust 版文件下载服务" to "并入 hub · files",
            "Rust-23 USB 串口网关" to "不可行",
            "Rust-24 TUN 分流" to "不可行",
            "Rust-25 录音探针" to "不可行",
            "Rust-26 双协议代理" to "不纳入",
            "Rust-27 媒体全文检索" to "不纳入",
            "Rust-28 定时任务调度" to "不纳入",
            "Rust-29 温度监控" to "并入 tools · sys",
            "Rust-30 UDP 视频纠错转发" to "不纳入",
            "Rust-31 目录变化监听" to "tools · watch（第一期）",
            "Rust-32 显存直读截图" to "不可行",
            "Rust-33 共享内存状态" to "不纳入",
            "Rust-34 网络延迟雷达" to "tools · ping（第一期）",
            "Rust-35 局域网名字发现（mDNS）" to "第三期候选",
            "Rust-36 存储读写测速" to "tools · iobench（第二期）",
            "Rust-37 终端会话保持" to "第三期候选",
            "Rust-38 LZ4 解压" to "并入 tools · pack",
            "Rust-39 HDMI-CEC 联动" to "不可行",
            "Rust-40 精细音量调节" to "不可行",
            "Rust-41 虚拟 USB 键盘" to "部分并入 tools · key",
            "Rust-42 DNS 竞速" to "不纳入",
            "Rust-43 Rust 版网页终端" to "并入 hub · webshell（第二期）",
            "Rust-44 共享内存队列" to "不纳入",
            "Rust-45 蓝牙底层抓包" to "不可行",
            "Rust-46 多屏组播信令" to "不纳入",
            "Rust-47 局域网 P2P 分发" to "不纳入",
            "Rust-48 资源限额沙箱" to "不可行",
            "Rust-49 Rust 底座（Z6X Rust Core）" to "改为 z6x-tools 命令集（见规格）",
        )
    }

    related("spec-hub", "spec-tools", "review-files", "review-media", "review-network", "review-control", "review-system")
}

val ReviewFiles = module("review-files", "提案：文件与存储") {
    keywords = "文件共享、备份、压缩、查重、对象存储……"
    overview = """
        agy 推荐的小项目中属于这一类的 21 个。每个小节：用途、做法、在这台投影仪上行不行、去向。agy 原方案里说错的具体事实，用旧记录卡片标出。
    """
    proposal()

    story("Go-01 · 局域网文件共享（WebDAV）") {
        facts(
            "用途" to "在 Deck、手机、电脑之间传文件，不用插拔 U 盘，也不用装 App。",
            "做法" to "投影仪上跑一个 WebDAV 服务，把机身存储和 U 盘目录共享出去；各系统自带的文件管理器都能把它挂成网络盘。",
            "在这台投影仪上" to "可行。静态 Go 程序能跑、高位端口能绑定都已实测；/data 剩余约 46G。U 盘挂载在 /storage/<卷ID>/，shell 可以访问。",
            "去向" to "**hub · files（第一期）**",
        )
    }
    story("Go-08 · 大屏相册") {
        facts(
            "用途" to "在电视上流畅浏览 U 盘里成千上万张照片，不卡、不闪退。",
            "做法" to "后端预先把大图缩成小图缓存起来，电视浏览器打开一个按时间排列的相册网页。",
            "在这台投影仪上" to "可行，缩图需要 CPU，并发要限制在 2 以内。",
            "去向" to "**第三期候选（作为 files 的扩展）**",
        )
    }
    story("Go-09 · Deck 存档与截图自动备份") {
        facts(
            "用途" to "Deck 一连上家里 Wi-Fi，就自动把游戏存档和截图备份到投影仪的 U 盘。",
            "做法" to "投影仪提供一个上传接口，Deck 上的脚本打包后上传；边收边写盘，先写临时文件再改名。",
            "在这台投影仪上" to "可行。",
            "去向" to "**hub · files 的上传接口（第一期）**",
        )
    }
    story("Go-13 · 漫画与电子书阅读") {
        facts(
            "用途" to "直接在手机或电视浏览器里看 U 盘上的漫画压缩包和电子书，不用先解压拷贝。",
            "做法" to "后端只解压当前要看的那一页，网页翻页阅读，记住进度。",
            "在这台投影仪上" to "可行。",
            "去向" to "**第三期候选**",
        )
    }
    story("Go-21 · Windows 网络共享（SMB）") {
        facts(
            "用途" to "让只认 Windows 共享的设备（老电视盒、部分播放器）读投影仪的 U 盘。",
            "做法" to "实现一个不需要 root 的 SMB 服务。",
            "在这台投影仪上" to "能绑定 1445 这类高位端口，但 Windows 资源管理器只认 445，打不开。",
            "去向" to "**不纳入（WebDAV 已覆盖文件共享）**",
        )
        claim("所有设备打开网络邻居即可直接访问", Verdict.Disproved, "非 445 端口在 Windows 网络邻居里打不开。")
    }
    story("Go-26 · S3 对象存储") {
        facts(
            "用途" to "给支持 S3 协议的备份软件当存储目标。",
            "做法" to "实现 S3 的几个基本接口，把 U 盘目录当存储桶。",
            "在这台投影仪上" to "可行，但 WebDAV 已覆盖。",
            "去向" to "**不纳入**",
        )
        claim("-dir /mnt/media_rw/USB_DISK/s3_storage", Verdict.Disproved, "/mnt/media_rw 对 shell 不可访问（实测）；U 盘要走 /storage/<卷ID>/。")
    }
    story("Go-30 · 私人 Git 仓库") {
        facts(
            "用途" to "把脚本、配置备份到投影仪 U 盘上的 Git 仓库。",
            "做法" to "实现 Git 的 HTTP 传输协议。",
            "在这台投影仪上" to "可行，收益低。",
            "去向" to "**不纳入**",
        )
    }
    story("Go-35 · SFTP 文件传输") {
        facts(
            "用途" to "用 FileZilla、WinSCP 加密传文件。",
            "做法" to "实现 SSH 的 SFTP 子协议。",
            "在这台投影仪上" to "可行，但 WebDAV 和 SSH 已覆盖。",
            "去向" to "**不纳入**",
        )
    }
    story("Go-47 · 缩略图生成") {
        facts(
            "用途" to "海报墙、相册加载快不卡。",
            "做法" to "按需把大图缩成小图并缓存。",
            "在这台投影仪上" to "可行。",
            "去向" to "**第三期候选（随相册一起）**",
        )
    }
    story("Go-50 · 网盘挂载") {
        facts(
            "用途" to "把阿里云盘、百度网盘挂成 WebDAV，电视播放器直接点播。",
            "做法" to "调用网盘接口获取文件直链，对外提供 WebDAV。",
            "在这台投影仪上" to "可行，但现成的 AList 本身就是 Go 静态程序，可以直接单独部署。",
            "去向" to "**不纳入（不必重写）**",
        )
    }
    story("Go-57 · 重复文件查找") {
        facts(
            "用途" to "找出 U 盘里重复的大文件，释放空间。",
            "做法" to "先按大小分组，再比较部分内容，最后算完整哈希。",
            "在这台投影仪上" to "可行。U 盘常见的 FAT32/exFAT 不支持硬链接，所以只报告、不自动处理。",
            "去向" to "**并入 tools · hash（第一期）**",
        )
    }
    story("Rust-04 · 文件哈希与查重") {
        facts(
            "用途" to "快速找出重复文件、校验文件完整性。",
            "做法" to "按大小分组后计算 xxh3 哈希（利用 CPU 的向量指令）。",
            "在这台投影仪上" to "可行。",
            "去向" to "**tools · hash（第一期）**",
        )
        claim("MediaTek MT9669 的 4 个 Cortex-A73 核心内部均集成了 NEON 向量处理单元", Verdict.Disproved, "实际是海思 Hi3751V660 的 8 个 Cortex-A55（同样有 NEON）。")
    }
    story("Rust-06 · 只读索引库") {
        facts(
            "用途" to "给大量媒体文件建一个查询极快的索引。",
            "做法" to "把索引文件映射进内存直接读取。",
            "在这台投影仪上" to "可行，没有需求。",
            "去向" to "**不纳入**",
        )
    }
    story("Rust-11 · 快速压缩归档") {
        facts(
            "用途" to "备份存档、日志时压缩得又快又小。",
            "做法" to "tar + zstd 流式压缩。",
            "在这台投影仪上" to "可行。",
            "去向" to "**tools · pack（第二期）**",
        )
    }
    story("Rust-15 · U 盘健康检测") {
        facts(
            "用途" to "提前发现 U 盘坏块、慢块。",
            "做法" to "绕过缓存直接读写，统计每块的延迟。",
            "在这台投影仪上" to "U 盘的块设备 shell 打不开，只能对文件测；FAT/exFAT 是否支持直接读写未验证。",
            "去向" to "**tools · iobench（第二期）**",
        )
    }
    story("Rust-17 · Rust 版 WebDAV") {
        facts(
            "用途" to "同 Go-01。",
            "做法" to "同 Go-01。",
            "在这台投影仪上" to "可行，重复。",
            "去向" to "**并入 hub · files**",
        )
    }
    story("Rust-22 · Rust 版文件下载服务") {
        facts(
            "用途" to "同 Go-01 的下载部分。",
            "做法" to "用零拷贝方式发送文件。",
            "在这台投影仪上" to "可行，重复。",
            "去向" to "**并入 hub · files**",
        )
    }
    story("Rust-27 · 媒体全文检索") {
        facts(
            "用途" to "在上万个媒体文件里秒搜文件名。",
            "做法" to "扫描后建 SQLite 全文索引。",
            "在这台投影仪上" to "可行，暂无需求。",
            "去向" to "**不纳入**",
        )
    }
    story("Rust-36 · 存储读写测速") {
        facts(
            "用途" to "测 U 盘的真实读写速度。",
            "做法" to "绕过缓存做顺序和随机读写。",
            "在这台投影仪上" to "可行（对文件测）。",
            "去向" to "**tools · iobench（第二期）**",
        )
    }
    story("Rust-38 · LZ4 解压") {
        facts(
            "用途" to "极快的流式解压。",
            "做法" to "LZ4 帧格式解码。",
            "在这台投影仪上" to "可行；zstd 够用。",
            "去向" to "**并入 tools · pack**",
        )
    }
    story("Rust-47 · 局域网 P2P 分发") {
        facts(
            "用途" to "多台设备之间分块同步大文件。",
            "做法" to "按块哈希，从多个设备并发拉取缺少的块。",
            "在这台投影仪上" to "可行，收益低。",
            "去向" to "**不纳入**",
        )
    }

    related("review-summary", "spec-hub", "spec-tools")
}

val ReviewMedia = module("review-media", "提案：下载、影音与直播") {
    keywords = "离线下载、音乐、视频墙、投屏、字幕、直播源……"
    overview = """
        agy 推荐的小项目中属于这一类的 20 个。每个小节：用途、做法、在这台投影仪上行不行、去向。agy 原方案里说错的具体事实，用旧记录卡片标出。
    """
    proposal()

    story("Go-02 · 离线下载") {
        facts(
            "用途" to "把下载链接或种子交给投影仪，它在后台下载到 U 盘，电脑不用整夜开着。",
            "做法" to "在投影仪上运行 aria2（一个命令行下载器），手机或电脑通过网页（AriaNg）添加任务。",
            "在这台投影仪上" to "下载目录可写。需要另找静态编译的 aria2c。关屏时系统一直醒着（实测），可以持续下载；选「关机」会进入睡眠，下载暂停，开机后继续。",
            "去向" to "**第三期候选（作为外部程序部署，不在 hub 里重写）**",
        )
        claim("关闭投影仪画面后下载停止……需配合执行模块 11 中的网络防休眠配置（`settings put global wifi_sleep_policy 2`）", Verdict.Unverified, "关屏 30 分钟实测网络一直在线，并没有断。wifi_sleep_policy 实测本来就是 2。")
    }
    story("Go-04 · 多画面短视频墙") {
        facts(
            "用途" to "在大屏上同时循环播放 2~4 个短视频，像监控墙一样浏览 U 盘里大量的短视频。",
            "做法" to "Go 后端扫描视频目录并提供网页，电视浏览器打开网页，网格里同时播放几个视频，遥控器移动焦点。",
            "在这台投影仪上" to "依赖电视浏览器能同时硬解几路视频，没有验证。",
            "去向" to "**第三期候选**",
        )
        claim("极米 Z6X Pro 搭载联发科 MT9669 芯片……通常支持 2~4 路 1080p（或 1 路 4K）同时硬解", Verdict.Disproved, "芯片是海思 Hi3751V660（8 核 A55），不是联发科 MT9669。硬解路数没有测过。")
    }
    story("Go-05 · 家庭音乐服务器（Navidrome）") {
        facts(
            "用途" to "把 U 盘里的无损音乐变成家里的「私人音乐 App」，手机随时点播。",
            "做法" to "部署 Navidrome（一个开源音乐服务器），手机用支持 Subsonic 协议的播放器连接。",
            "在这台投影仪上" to "Navidrome 本身就是 Go 静态程序，可以按 Go 测试服务的方式单独部署。",
            "去向" to "**不纳入 hub（需要时单独部署）**",
        )
    }
    story("Go-12 · 直播源整理") {
        facts(
            "用途" to "把网上收集的 IPTV 直播源自动测试、去掉失效的，生成一份干净的播放列表，并配上节目单。",
            "做法" to "定时逐个探测直播地址，合并去重，输出 m3u 播放列表和 XMLTV 节目单。",
            "在这台投影仪上" to "可行（纯网络任务）。",
            "去向" to "**第三期候选（与 Go-48、Go-59 合成一个模块）**",
        )
    }
    story("Go-16 · DLNA 投屏接收") {
        facts(
            "用途" to "手机视频 App 里点「投屏」，用电视播放，不经过厂商自带的投屏应用。",
            "做法" to "实现一个 DLNA 渲染端：在局域网宣告自己，收到视频地址后让电视播放。",
            "在这台投影仪上" to "有前提：UDP 1900 已被系统组件占用（实测）；视频链接没有默认播放器，要先装好播放器设为默认。",
            "去向" to "**第二期 · dlna**",
        )
    }
    story("Go-19 · 把投影仪当无线音箱") {
        facts(
            "用途" to "不开光机时，把 Deck 或电脑的声音通过 Wi-Fi 传到投影仪的扬声器播放。",
            "做法" to "接收网络音频流，写入声卡播放。",
            "在这台投影仪上" to "不可行：shell 不在 audio 组，碰不到声卡（实测），系统也没有 tinyplay；放声音要在 App 里做。",
            "去向" to "**不可行**",
        )
        claim("Go 服务在本地通过管道调用系统的 `tinyplay` 工具或 Android AudioTrack API 直接写入硬件音频设备节点", Verdict.Disproved, "系统里没有 tinyplay；/dev/snd 对 shell 不可读写；AudioTrack 只能在 App 里用。")
    }
    story("Go-22 · 影视海报刮削") {
        facts(
            "用途" to "下载的电影自动配上海报、简介，播放器里显示成海报墙。",
            "做法" to "监听下载目录，按文件名查 TMDB，生成海报图和 .nfo 描述文件。",
            "在这台投影仪上" to "可行，但要访问 TMDB（外网，经常连不上）。",
            "去向" to "**不纳入（依赖外网，收益低）**",
        )
    }
    story("Go-25 · RSS 追更") {
        facts(
            "用途" to "订阅的剧集、播客更新后自动下载。",
            "做法" to "定时读 RSS，发现新条目就交给下载器。",
            "在这台投影仪上" to "可行。",
            "去向" to "**不纳入（收益低）**",
        )
    }
    story("Go-32 · 视频切片点播（HLS）") {
        facts(
            "用途" to "让 iPhone 等设备能播放 U 盘里的 MKV 等格式。",
            "做法" to "把视频实时切成小段，生成 m3u8 播放列表。",
            "在这台投影仪上" to "技术选型有问题（见下），收益低。",
            "去向" to "**不纳入**",
        )
        claim("Go 1.27 + `aler9/gortsplib` 底层流解析", Verdict.Unverified, "gortsplib 是 RTSP 流协议库，并不负责 MP4/MKV 的解封装，这个选型站不住。")
    }
    story("Go-39 · 磁力链接转种子") {
        facts(
            "用途" to "加快磁力链接开始下载的速度。",
            "做法" to "加入 BT 的 DHT 网络，下载种子元数据，生成 .torrent 文件。",
            "在这台投影仪上" to "可行，收益低。",
            "去向" to "**不纳入**",
        )
    }
    story("Go-41 · 多房间同步音频") {
        facts(
            "用途" to "家里多台设备同步播放同一段音乐。",
            "做法" to "实现 Snapcast 协议，向各房间的客户端分发带时间戳的音频。",
            "在这台投影仪上" to "不可行：命令行进程既拿不到音频来源，也放不出声音。",
            "去向" to "**不可行**",
        )
    }
    story("Go-42 · 字幕自动下载") {
        facts(
            "用途" to "没有中文字幕的电影自动下载字幕。",
            "做法" to "根据视频文件特征去字幕网站查找、下载、改名。",
            "在这台投影仪上" to "可行，依赖外网字幕站。",
            "去向" to "**不纳入**",
        )
    }
    story("Go-48 · IPTV 列表清洗") {
        facts(
            "用途" to "同 Go-12：去掉失效直播源，合并去重。",
            "做法" to "并发探测，生成干净的 m3u。",
            "在这台投影仪上" to "可行。",
            "去向" to "**第三期候选（并入 Go-12）**",
        )
    }
    story("Go-52 · 直播录制") {
        facts(
            "用途" to "夜里自动录制指定主播的直播和弹幕。",
            "做法" to "监测开播，拉流写入 U 盘，同时记录弹幕。",
            "在这台投影仪上" to "可行，收益低。",
            "去向" to "**不纳入**",
        )
    }
    story("Go-56 · 浏览器网页投屏") {
        facts(
            "用途" to "访客不装软件，用浏览器就能把电脑屏幕投到投影仪。",
            "做法" to "WebRTC 信令服务 + 电视端网页接收显示。",
            "在这台投影仪上" to "电视端要开浏览器页面解码显示，发起端还要求 HTTPS，整体复杂。",
            "去向" to "**不纳入**",
        )
    }
    story("Go-59 · 节目单生成") {
        facts(
            "用途" to "同 Go-12：给直播源配节目单。",
            "做法" to "定时抓取节目表，生成 XMLTV 文件。",
            "在这台投影仪上" to "可行。",
            "去向" to "**第三期候选（并入 Go-12）**",
        )
    }
    story("Rust-03 · 低延迟音频混音") {
        facts(
            "用途" to "Wi-Fi 传来的音频直接送进声卡播放，避免爆音。",
            "做法" to "无锁环形缓冲 + 直接写声卡。",
            "在这台投影仪上" to "不可行：/dev/snd 对 shell 不可读写（实测）。",
            "去向" to "**不可行**",
        )
    }
    story("Rust-25 · 录音探针") {
        facts(
            "用途" to "排查音频爆音时直接录声卡的数据。",
            "做法" to "直接读声卡的录音节点。",
            "在这台投影仪上" to "不可行：/dev/snd 不可访问。",
            "去向" to "**不可行**",
        )
    }
    story("Rust-30 · UDP 视频纠错转发") {
        facts(
            "用途" to "投屏丢包时就地恢复画面。",
            "做法" to "在转发的数据里加纠错冗余。",
            "在这台投影仪上" to "需要发送端配合，没有使用方。",
            "去向" to "**不纳入**",
        )
    }
    story("Rust-40 · 精细音量调节") {
        facts(
            "用途" to "比系统音量步进更细地调音量。",
            "做法" to "直接写声卡的音量控制。",
            "在这台投影仪上" to "不可行：声卡控制节点属于 audio 组，shell 不可访问。",
            "去向" to "**不可行**",
        )
    }

    related("review-summary", "spec-hub", "spec-tools")
}

val ReviewNetwork = module("review-network", "提案：网络、代理与远程访问") {
    keywords = "DNS、代理、测速、端口映射、穿透、组播……"
    overview = """
        agy 推荐的小项目中属于这一类的 25 个。每个小节：用途、做法、在这台投影仪上行不行、去向。agy 原方案里说错的具体事实，用旧记录卡片标出。
    """
    proposal()

    story("Go-03 · DNS 缓存与去广告") {
        facts(
            "用途" to "让家里所有设备的域名解析更快，并在解析阶段拦截广告和上报域名。",
            "做法" to "投影仪上跑一个 DNS 服务（如 mosdns），家里设备把 DNS 指向它。",
            "在这台投影仪上" to "DNS 标准端口 53 不能绑定（1024 以下都不行，实测），只能用 5353 等高位端口，再靠路由器把 53 转过来，很多家用路由器做不到。",
            "去向" to "**不纳入（Clash 已负责 DNS 和分流）**",
        )
        claim("极米 Z6X Pro 拥有充裕的可用内存（1.5GB）与 Wi-Fi 6 低延迟连接", Verdict.Disproved, "可用内存约 1.5GB 成立；但网卡的 USB ID 是联发科 0e8d:7663，一般对应 MT7663，是 Wi-Fi 5，不是 Wi-Fi 6。")
    }
    story("Go-14 · 局域网测速") {
        facts(
            "用途" to "串流游戏或投屏卡顿时，测出手机 / Deck 和投影仪之间的真实网速与延迟。",
            "做法" to "投影仪提供测速网页：下载、上传、往返延迟三项。",
            "在这台投影仪上" to "可行。普通身份就能发 ICMP ping（实测），不必调用外部命令。",
            "去向" to "**hub · speed（第一期）**",
        )
        claim("由于 Linux 限制普通非特权用户创建原始套接字（Raw Socket），探针通过调用系统自带的 `/system/bin/ping` 命令", Verdict.Disproved, "这台机器对普通身份开放了 ICMP（ping_group_range 是 0~2147483647），程序可以直接发 ping。")
    }
    story("Go-24 · 局域网代理") {
        facts(
            "用途" to "让 Deck 等设备通过投影仪上网。",
            "做法" to "投影仪上跑一个 SOCKS5 / HTTP 代理。",
            "在这台投影仪上" to "可行，但没必要：Clash 已在所有地址上监听 7890，局域网设备本来就能直接用（实测，安全隐患见「安全」页）。",
            "去向" to "**不纳入（Clash 已有）**",
        )
    }
    story("Go-27 · 路由器自动端口映射") {
        facts(
            "用途" to "从外网访问家里投影仪上的服务，不用手动配路由器。",
            "做法" to "通过 UPnP 协议让路由器把外网端口转到投影仪。",
            "在这台投影仪上" to "可行，但会把服务暴露到公网。",
            "去向" to "**不纳入（安全）**",
        )
    }
    story("Go-33 · Tailscale 中继节点") {
        facts(
            "用途" to "外出时访问家里，改善打洞失败时的速度。",
            "做法" to "在投影仪上运行 Tailscale 的 DERP 中继。",
            "在这台投影仪上" to "需要公网可达和证书。",
            "去向" to "**不纳入（要对公网开放）**",
        )
    }
    story("Go-36 · 应急 DHCP") {
        facts(
            "用途" to "路由器坏了或网线直连电脑时，由投影仪分配 IP。",
            "做法" to "运行 DHCP 服务。",
            "在这台投影仪上" to "不可行：DHCP 要用 67 端口（1024 以下不能绑定，实测）。",
            "去向" to "**不可行**",
        )
        claim("需通过 Linux 能力集授权 `setcap cap_net_bind_service=+ep`", Verdict.Disproved, "setcap 需要 root，这台机器没有。")
    }
    story("Go-37 · 动态域名") {
        facts(
            "用途" to "家里公网 IP 变了，自动更新域名解析。",
            "做法" to "定时检查公网 IPv6，调用 DNS 服务商接口更新。",
            "在这台投影仪上" to "可行（投影仪确实有公网 IPv6，实测），但本项目不对公网开放。",
            "去向" to "**不纳入**",
        )
    }
    story("Go-38 · PAC 自动代理") {
        facts(
            "用途" to "给手机电脑下发「哪些网站走代理」的规则。",
            "做法" to "生成并提供 PAC 脚本。",
            "在这台投影仪上" to "可行，Clash 已负责分流。",
            "去向" to "**不纳入**",
        )
    }
    story("Go-45 · 宽带定时测速") {
        facts(
            "用途" to "长期记录宽带速度，晚高峰被限速有据可查。",
            "做法" to "定时连接公网测速节点测速并存档。",
            "在这台投影仪上" to "可行，但每次测速消耗不少流量。",
            "去向" to "**不纳入**",
        )
    }
    story("Go-46 · 跨网段网络唤醒") {
        facts(
            "用途" to "在外面或访客网络里也能唤醒家里的电脑。",
            "做法" to "接收唤醒请求，在家里的网段里广播魔术包。",
            "在这台投影仪上" to "可行。",
            "去向" to "**并入 hub · wol（第一期）**",
        )
    }
    story("Go-49 · iperf3 测速") {
        facts(
            "用途" to "用标准测速工具 iperf3 测投影仪的无线吞吐。",
            "做法" to "实现 iperf3 的服务端协议。",
            "在这台投影仪上" to "可行。",
            "去向" to "**hub · speed（第二期，可选）**",
        )
    }
    story("Go-51 · HTTPS 证书自动申请") {
        facts(
            "用途" to "外网用域名访问时有正规证书。",
            "做法" to "用 ACME 协议自动申请、续期证书。",
            "在这台投影仪上" to "可行，本项目不对公网开放。",
            "去向" to "**不纳入**",
        )
    }
    story("Go-53 · 局域网设备扫描") {
        facts(
            "用途" to "看家里网络里有哪些设备在线、各是什么。",
            "做法" to "发送 SSDP 搜索，收集设备回复的名称和型号。",
            "在这台投影仪上" to "可行。UDP 1900 已被系统占用（实测），只发不绑定即可。",
            "去向" to "**第二期 · lanscan**",
        )
    }
    story("Go-55 · Tailscale 子网路由") {
        facts(
            "用途" to "外出时访问家里所有设备。",
            "做法" to "用 Tailscale 的用户态模式，把家里网段宣告到虚拟网络。",
            "在这台投影仪上" to "用户态模式在这台机器上能否运行未验证。如果以后需要外出访问，这是首选方案。",
            "去向" to "**第三期候选**",
        )
    }
    story("Rust-05 · 用户态加密隧道") {
        facts(
            "用途" to "不需要 root 也能建立 WireGuard 这样的加密组网。",
            "做法" to "在程序内部实现整套网络协议栈，对外只用普通 UDP 端口。",
            "在这台投影仪上" to "理论可行；/dev/net/tun 对 shell 不可访问（实测），Clash 已经在提供 VPN。",
            "去向" to "**不纳入**",
        )
    }
    story("Rust-09 · 端口敲门") {
        facts(
            "用途" to "平时把管理端口藏起来，按约定顺序「敲」几个端口才打开。",
            "做法" to "监听未开放端口上的连接尝试，识别敲门顺序后放行。",
            "在这台投影仪上" to "不可行：要捕获未开放端口上的数据包，需要原始套接字权限，shell 没有。",
            "去向" to "**不可行**",
        )
    }
    story("Rust-10 · 加密 DNS 转发") {
        facts(
            "用途" to "防止运营商篡改 DNS 结果。",
            "做法" to "把 DNS 查询改成 HTTPS 加密发出。",
            "在这台投影仪上" to "53 端口不能绑定；Clash 已经在做加密 DNS。",
            "去向" to "**不纳入**",
        )
    }
    story("Rust-16 · 限速代理") {
        facts(
            "用途" to "后台下载时限速，不影响看视频、打游戏。",
            "做法" to "数据经过一个按固定速率放行的代理。",
            "在这台投影仪上" to "可行，没有需求。",
            "去向" to "**不纳入（以后可作为 files 的限速选项）**",
        )
    }
    story("Rust-20 · 抓包分析") {
        facts(
            "用途" to "分析投屏、串流时的丢包和抖动。",
            "做法" to "用 AF_PACKET 直接读网卡数据包。",
            "在这台投影仪上" to "不可行：需要 CAP_NET_RAW 能力，shell 没有。",
            "去向" to "**不可行**",
        )
    }
    story("Rust-24 · TUN 分流") {
        facts(
            "用途" to "自己做一个 VPN 式的流量分流。",
            "做法" to "打开 /dev/tun 读写数据包。",
            "在这台投影仪上" to "不可行：/dev/net/tun 对 shell 不可访问（实测）。",
            "去向" to "**不可行**",
        )
    }
    story("Rust-26 · 双协议代理") {
        facts(
            "用途" to "同 Go-24。",
            "做法" to "一个端口同时支持 SOCKS5 和 HTTP。",
            "在这台投影仪上" to "可行；Clash 已有混合端口。",
            "去向" to "**不纳入**",
        )
    }
    story("Rust-34 · 网络延迟雷达") {
        facts(
            "用途" to "连续测量到路由器的延迟和抖动，判断 Wi-Fi 质量。",
            "做法" to "高频 ICMP ping，统计最小 / 平均 / 最大和抖动。",
            "在这台投影仪上" to "可行：普通身份能发 ICMP（实测）。",
            "去向" to "**tools · ping（第一期）**",
        )
        claim("实测前应通过 `cmd wifi set-low-latency-mode enabled` 开启低延迟模式", Verdict.Disproved, "这台机器的 cmd wifi 没有这个子命令。")
    }
    story("Rust-35 · 局域网名字发现（mDNS）") {
        facts(
            "用途" to "用 z6x.local 代替 IP 访问投影仪。",
            "做法" to "在局域网宣告主机名和服务。",
            "在这台投影仪上" to "UDP 5353 已被系统 mdnsd 占用（实测），能否共存未验证。",
            "去向" to "**第三期候选**",
        )
    }
    story("Rust-42 · DNS 竞速") {
        facts(
            "用途" to "同时问几个 DNS，用最快的结果。",
            "做法" to "并发查询多个上游。",
            "在这台投影仪上" to "53 端口不能绑定；也没法把系统 DNS 指过来（见错误卡片）。",
            "去向" to "**不纳入**",
        )
        claim("需在 shell 中通过 `setprop net.dns1 127.0.0.1` 引导系统优先走本地代理", Verdict.Disproved, "安卓 8 以后不再使用 net.dns1，这台机器上它是空的（实测）。")
    }
    story("Rust-46 · 多屏组播信令") {
        facts(
            "用途" to "多台设备之间用组播快速同步状态。",
            "做法" to "UDP 组播收发定长消息。",
            "在这台投影仪上" to "可行，没有使用方。",
            "去向" to "**不纳入**",
        )
    }

    related("review-summary", "spec-hub", "spec-tools")
}

val ReviewControl = module("review-control", "提案：遥控、自动化与通知") {
    keywords = "按键、遥控网页、Webhook、通知、智能家居、蓝牙、串口……"
    overview = """
        agy 推荐的小项目中属于这一类的 20 个。每个小节：用途、做法、在这台投影仪上行不行、去向。agy 原方案里说错的具体事实，用旧记录卡片标出。
    """
    proposal()

    story("Go-06 · 智能家居控制入口") {
        facts(
            "用途" to "让 Home Assistant 这类智能家居平台能通过网络命令控制投影仪：开关、音量、切信号源。",
            "做法" to "投影仪上提供几个 HTTP 接口，收到请求后在本机执行按键或打开应用。",
            "在这台投影仪上" to "按键控制可行（input keyevent 实测可用）。切换 HDMI 信号源的办法还没找到。",
            "去向" to "**hub · control（第一期）**",
        )
        claim("am start -a android.intent.action.VIEW -d \"xgimi://com.xgimi.home/hdmi\"", Verdict.Disproved, "这个链接实测没有任何界面响应（官方桌面已卸载）。")
    }
    story("Go-11 · 手机把文字和链接发给电视") {
        facts(
            "用途" to "解决电视上打长文字太痛苦的问题：在手机上贴好，一键发到电视。",
            "做法" to "投影仪提供一个网页，手机贴文字，按钮把文字打到电视当前的输入框，或者让电视打开这个链接。",
            "在这台投影仪上" to "可行。打字只支持英文和符号（input text 的限制）。打开视频链接会弹出选择播放器的框（没有默认播放器，实测）。",
            "去向" to "**hub · paste（第一期）**",
        )
        claim("Android 系统会自动调起内置的最佳播放器（或 VLC）进行硬件解码播放", Verdict.Disproved, "视频链接实测由一个「选择打开方式」的界面接管，不会自动播放。")
    }
    story("Go-15 · 手机当遥控器和触控板") {
        facts(
            "用途" to "遥控器只有方向键，有些 App 的按钮选不中；用手机当触控板和快捷键面板。",
            "做法" to "手机打开网页，滑动控制鼠标指针，按钮触发一串预设按键。",
            "在这台投影仪上" to "按键和按键宏可行；鼠标需要注入虚拟鼠标设备，/dev/uinput 可写（实测），但系统是否接受虚拟鼠标未验证。",
            "去向" to "**hub · control（第一期按键与宏，第二期触控板）**",
        )
    }
    story("Go-17 · 网络唤醒") {
        facts(
            "用途" to "躺在沙发上用手机把书房的电脑唤醒，然后串流游戏。",
            "做法" to "发送网络唤醒「魔术包」，并定时 ping 设备显示在线状态。",
            "在这台投影仪上" to "可行。",
            "去向" to "**hub · wol（第一期）**",
        )
    }
    story("Go-18 · 大屏通知") {
        facts(
            "用途" to "看电影时，门铃、下载完成这类提醒能在屏幕角落弹出来。",
            "做法" to "收到网络请求后在电视上发一条系统通知。",
            "在这台投影仪上" to "`cmd notification post` 存在（实测），但电视的系统界面会不会把通知显示出来，没有验证。",
            "去向" to "**第二期 · notify（先验证能不能显示）**",
        )
        claim("方案 B：通过极简的前端 WebView 浮窗（基于 `android.view.WindowManager` 的悬浮窗图层）", Verdict.Disproved, "悬浮窗必须由一个 App 来创建，命令行进程做不到。")
    }
    story("Go-23 · 物联网消息中心（MQTT）") {
        facts(
            "用途" to "给家里的传感器、智能开关提供一个消息中转站，不用另买树莓派。",
            "做法" to "运行一个 MQTT 服务端。",
            "在这台投影仪上" to "可行（1883 是高位端口）。目前家里没有这类设备的需求。",
            "去向" to "**第三期候选**",
        )
    }
    story("Go-31 · 统一控制接口（JSON-RPC）") {
        facts(
            "用途" to "把音量、切源、启动应用等控制统一成一套接口。",
            "做法" to "用 JSON-RPC 协议包装各种控制命令。",
            "在这台投影仪上" to "可行。用更简单的 REST 接口实现即可。",
            "去向" to "**并入 hub · control（第一期）**",
        )
    }
    story("Go-34 · Webhook 触发动作") {
        facts(
            "用途" to "NAS 备份完成、服务器报警时，让投影仪做点什么（弹提示、执行操作）。",
            "做法" to "接收外部 HTTP 回调，按规则执行预设动作。",
            "在这台投影仪上" to "可行。执行外部命令时参数要用数组传递、不拼字符串（防注入）。",
            "去向" to "**并入 hub · control（第一期）**",
        )
    }
    story("Go-40 · Miracast 投屏信令修正") {
        facts(
            "用途" to "解决 Windows 投屏偶尔黑屏、断开的问题。",
            "做法" to "在投屏连接中间插一个代理，修正协商信息。",
            "在这台投影仪上" to "不可行：Miracast 由系统投屏应用通过 Wi-Fi 直连建立，外部进程插不进去。",
            "去向" to "**不可行**",
        )
    }
    story("Go-44 · 接入苹果「家庭」") {
        facts(
            "用途" to "用 iPhone 的「家庭」App 和 Siri 控制投影仪。",
            "做法" to "实现 HomeKit 配件协议，在局域网宣告一个虚拟电视配件。",
            "在这台投影仪上" to "需要 mDNS，而 UDP 5353 已被系统 mdnsd 占用（实测），能否共存未验证。",
            "去向" to "**第三期候选**",
        )
    }
    story("Go-54 · 传感器数据存储") {
        facts(
            "用途" to "保存温湿度等传感器的历史数据，画曲线。",
            "做法" to "订阅 MQTT 消息，压缩后按时间存储。",
            "在这台投影仪上" to "可行，没有需求。",
            "去向" to "**不纳入**",
        )
    }
    story("Rust-02 · 遥控器按键重映射") {
        facts(
            "用途" to "给遥控器按键加长按、双击功能，比如长按返回打开某个 App。",
            "做法" to "监听遥控器的输入设备，识别长按和双击，触发预设动作；不拦截原来的按键。",
            "在这台投影仪上" to "可行：shell 在 input 组，能读遥控器节点（实测）。",
            "去向" to "**tools · keymap（第一期）**",
        )
        claim("启动 Rust 按键监听服务（绑定蓝牙遥控器 event2）", Verdict.Disproved, "event2 是虚拟键盘 qwerty；遥控器是 event13（XGIMI RC Consumer Control）。")
    }
    story("Rust-07 · 蓝牙传感器网关") {
        facts(
            "用途" to "投影仪接收周围蓝牙温湿度计的广播，转发给智能家居平台。",
            "做法" to "直接打开蓝牙底层（HCI）接口监听广播。",
            "在这台投影仪上" to "不可行：内核里没有 hci 蓝牙设备（/sys/class/bluetooth 是空的，实测），蓝牙由厂商自己的驱动层管理。",
            "去向" to "**不可行**",
        )
        claim("极米 Z6X Pro 配备了 Wi-Fi/BT 组合芯片（MT7921）", Verdict.Disproved, "USB ID 是 0e8d:7663，一般对应 MT7663，不是 MT7921。")
    }
    story("Rust-13 · WebSocket 广播") {
        facts(
            "用途" to "多台设备实时同步投影仪的状态。",
            "做法" to "一个常驻的推送服务。",
            "在这台投影仪上" to "可行；需要时在 hub 里用 Go 实现，不再起一个进程。",
            "去向" to "**不单独做**",
        )
    }
    story("Rust-18 · 快速按键注入") {
        facts(
            "用途" to "比系统的 input 命令更快地模拟按键，支持连按。",
            "做法" to "直接往输入设备写按键事件，或创建一个虚拟键盘。",
            "在这台投影仪上" to "可行：遥控器节点和 /dev/uinput 对 shell 都可写（实测）。系统 input 命令实测每次 79~106ms。",
            "去向" to "**tools · key（第一期）**",
        )
        claim("单次调用耗时长达 200ms ~ 400ms", Verdict.Disproved, "实测 input keyevent 三次分别是 106、79、93 毫秒。慢，但没有这么夸张。")
    }
    story("Rust-21 · Rust 版 MQTT") {
        facts(
            "用途" to "同 Go-23。",
            "做法" to "同 Go-23。",
            "在这台投影仪上" to "可行，重复。",
            "去向" to "**不纳入**",
        )
        claim("标准 MQTT 端口 1883 需要 root 权限", Verdict.Disproved, "1883 大于 1024，普通身份可以绑定。")
    }
    story("Rust-23 · USB 串口网关") {
        facts(
            "用途" to "通过 USB 转串口连接单片机、继电器，用网络控制。",
            "做法" to "打开串口设备，和网络端口互相转发。",
            "在这台投影仪上" to "目前没有串口设备节点；串口节点通常只有 root 能读写。",
            "去向" to "**不可行**",
        )
        claim("在非 root 的 shell 下启动前需执行 `chmod 666 /dev/ttyUSB0`", Verdict.Disproved, "改设备节点权限需要 root。")
    }
    story("Rust-39 · HDMI-CEC 联动") {
        facts(
            "用途" to "投影仪开关时联动功放、音响。",
            "做法" to "直接读写 /dev/cec0 收发 CEC 指令。",
            "在这台投影仪上" to "不可行：没有 /dev/cec0（实测）。",
            "去向" to "**不可行**",
        )
    }
    story("Rust-41 · 虚拟 USB 键盘") {
        facts(
            "用途" to "让系统以为插了一个真键盘，绕过软件层的按键拦截。",
            "做法" to "写 /dev/hidg0（USB 设备模式）或 /dev/uinput。",
            "在这台投影仪上" to "/dev/hidg0 不存在；/dev/uinput 可写，这部分并入 tools · key。",
            "去向" to "**部分并入 tools · key**",
        )
        claim("Android 下 `/dev/uinput` 默认可能只属于 `system` 或 `root`，在普通 shell 启动前需通过 `chmod 666 /dev/uinput` 赋予读写权限", Verdict.Disproved, "/dev/uinput 属于 uhid 组，shell 就在这个组里，直接可写（实测）。")
    }
    story("Rust-45 · 蓝牙底层抓包") {
        facts(
            "用途" to "排查遥控器断连时看蓝牙底层通信。",
            "做法" to "打开 HCI 原始套接字抓包。",
            "在这台投影仪上" to "不可行：内核没有 hci 设备（实测）。",
            "去向" to "**不可行**",
        )
    }

    related("review-summary", "spec-hub", "spec-tools")
}

val ReviewSystem = module("review-system", "提案：系统、运维与架构") {
    keywords = "看门狗、监控、日志、终端、隔离、两个聚合架构……"
    overview = """
        agy 推荐的小项目中属于这一类的 23 个。每个小节：用途、做法、在这台投影仪上行不行、去向。agy 原方案里说错的具体事实，用旧记录卡片标出。
    """
    proposal()

    story("Go-07 · 让后台服务 24 小时在线") {
        facts(
            "用途" to "确保看完投影后，文件共享、下载这类服务仍然可用。",
            "做法" to "调整系统的休眠和网络策略，并确认投影仪在各种「关」的状态下服务是否还在。",
            "在这台投影仪上" to "已实测：电源菜单没有「待机」。「关屏」时安卓照常运行，30 分钟内服务全程在线；「关机」是挂起到内存的睡眠，约 14 秒后断网，开机后原样恢复。",
            "去向" to "**hub 运行前提（已验证，写入规格）**",
        )
        claim("按下遥控器关机后，系统通常在 15 分钟后进入深度睡眠（Doze Mode），切断 Wi-Fi 芯片供电并挂起 CPU", Verdict.Disproved, "「关屏」30 分钟没有进入睡眠；「关机」则是 14 秒内就挂起，都和这个说法不符。")
    }
    story("Go-10 · 系统状态面板") {
        facts(
            "用途" to "随时看投影仪的 CPU、内存、温度、存储。",
            "做法" to "读取 /proc 和 /sys 下的系统数据，通过接口返回。",
            "在这台投影仪上" to "可行（读这些文件实测可用）。",
            "去向" to "**hub · metrics（第一期）**",
        )
        claim("通过直接读取……`/sys/class/thermal/thermal_zone*/temp`（获取光机与核心摄氏度）", Verdict.Disproved, "温度区只有 cpu_thermal 和 vou_thermal，没有光机温度。")
    }
    story("Go-20 · 网页终端") {
        facts(
            "用途" to "不开电脑、不用 adb，用手机浏览器就能进投影仪的命令行。",
            "做法" to "后端开一个伪终端运行 shell，通过网页实时收发。",
            "在这台投影仪上" to "可行（/dev/ptmx 可用，实测）。等于把 shell 权限开放给网页，必须鉴权、默认关闭。",
            "去向" to "**第二期 · webshell**",
        )
    }
    story("Go-28 · Prometheus 监控接口") {
        facts(
            "用途" to "把投影仪接入家里已有的 Grafana 监控。",
            "做法" to "按 Prometheus 的文本格式输出系统指标。",
            "在这台投影仪上" to "可行。",
            "去向" to "**并入 hub · metrics（第一期）**",
        )
    }
    story("Go-29 · 统一登录保护") {
        facts(
            "用途" to "给投影仪上各种网页服务加一道密码，防止局域网里别人乱用。",
            "做法" to "所有请求先过一层鉴权再转给具体服务。",
            "在这台投影仪上" to "可行。",
            "去向" to "**简化为 hub · core 的 token 鉴权（第一期）**",
        )
    }
    story("Go-43 · 日志集中查看") {
        facts(
            "用途" to "投影仪上跑的多个服务，日志在一个地方查。",
            "做法" to "收集日志，提供查询接口。",
            "在这台投影仪上" to "可行。hub 是单进程，只需要自己的日志轮转和查看接口。",
            "去向" to "**简化为 hub · core 的日志（第一期）**",
        )
    }
    story("Go-58 · 网页改配置") {
        facts(
            "用途" to "在手机上改 hub 的配置，不用登录命令行改文件。",
            "做法" to "根据配置结构自动生成网页表单，保存前校验并备份。",
            "在这台投影仪上" to "可行。",
            "去向" to "**hub · core 配置页（第二期；第一期只读展示）**",
        )
    }
    story("Go-60 · 把 Go 服务合成一个程序（Z6X Hub）") {
        facts(
            "用途" to "几十个小服务各跑一个进程太浪费，合成一个。",
            "做法" to "一个二进制、一个进程、一个配置文件开关各模块，模块之间互不影响。",
            "在这台投影仪上" to "这个方向被采纳为 z6x-hub 的架构（见规格）。",
            "去向" to "**hub 架构**",
        )
        claim("storage_root: /mnt/media_rw/USB_DISK", Verdict.Disproved, "/mnt/media_rw 对 shell 不可访问（实测）。")
    }
    story("Rust-01 · 进程看门狗") {
        facts(
            "用途" to "自己部署的服务崩溃了能自动重启。",
            "做法" to "定时检查目标进程是否存活，不在就重新启动。",
            "在这台投影仪上" to "可行。原方案担心的「看门狗被低内存回收杀掉」不会发生（见错误卡片）。",
            "去向" to "**tools · run（第二期）**",
        )
        claim("当电视前台播放 4K 超高清电影……连看门狗本身都会被 Android TV 的 LMK（低内存查杀）机制一同杀死", Verdict.Disproved, "从 ADB 启动的进程 oom_score_adj 是 -1000（继承 adbd），不会被内存回收杀掉。")
    }
    story("Rust-08 · 崩溃黑匣子") {
        facts(
            "用途" to "系统死机或重启时，保留最后的关键日志。",
            "做法" to "持续读取内核日志和系统日志存在内存里，出事时写盘。",
            "在这台投影仪上" to "内核日志读不到（/proc/kmsg、dmesg 都没有权限，实测）；logcat 本身已有 crash 缓冲区。",
            "去向" to "**不纳入**",
        )
        claim("后台通过读取 `/proc/kmsg`（内核日志流）", Verdict.Disproved, "/proc/kmsg 对 shell 不可读（实测）。")
    }
    story("Rust-12 · 日志脱敏与轮转") {
        facts(
            "用途" to "日志里自动抹掉 IP、Token，并限制日志大小。",
            "做法" to "日志经过过滤程序再写盘，超过大小就切分。",
            "在这台投影仪上" to "可行；更简单的做法是服务自己不写敏感信息。",
            "去向" to "**不纳入**",
        )
    }
    story("Rust-14 · 系统指标") {
        facts(
            "用途" to "一条命令看清 CPU、内存、温度、存储。",
            "做法" to "读 /proc 和 /sys。",
            "在这台投影仪上" to "可行（实测）。",
            "去向" to "**tools · sys（第一期）**",
        )
    }
    story("Rust-19 · 进程守护") {
        facts(
            "用途" to "统一启动和守护多个服务，崩溃自动重启。",
            "做法" to "读取配置，按顺序启动子进程，退出就按退避间隔重启。",
            "在这台投影仪上" to "可行。",
            "去向" to "**tools · run（第二期，与 Rust-01 合并）**",
        )
    }
    story("Rust-28 · 定时任务调度") {
        facts(
            "用途" to "毫秒级精度的定时任务。",
            "做法" to "时间轮算法调度。",
            "在这台投影仪上" to "可行；hub 内部有定时功能即可。",
            "去向" to "**不纳入**",
        )
    }
    story("Rust-29 · 温度监控") {
        facts(
            "用途" to "温度过高时提前提醒。",
            "做法" to "定时读温度节点，超过阈值报警。",
            "在这台投影仪上" to "可行。",
            "去向" to "**并入 tools · sys**",
        )
        claim("追踪 CPU 核心、GPU 以及光学投影模组温度趋势", Verdict.Disproved, "只有 cpu_thermal 和 vou_thermal 两个温度区，没有光学模组温度。")
    }
    story("Rust-31 · 目录变化监听") {
        facts(
            "用途" to "U 盘里新增文件时自动触发处理（比如下载完就刮削）。",
            "做法" to "用内核的 inotify 机制监听，文件写完才通知。",
            "在这台投影仪上" to "可行。监听数量上限是 8192（实测）。",
            "去向" to "**tools · watch（第一期）**",
        )
    }
    story("Rust-32 · 显存直读截图") {
        facts(
            "用途" to "比系统 screencap 更快地截图。",
            "做法" to "直接映射 /dev/graphics/fb0。",
            "在这台投影仪上" to "不可行：fb0 属于 graphics 组，shell 不在其中（实测）。用 adb exec-out screencap 即可。",
            "去向" to "**不可行**",
        )
    }
    story("Rust-33 · 共享内存状态") {
        facts(
            "用途" to "多个程序之间极快地共享开关状态。",
            "做法" to "内存映射文件 + 原子变量。",
            "在这台投影仪上" to "可行，没有需求。",
            "去向" to "**不纳入**",
        )
    }
    story("Rust-37 · 终端会话保持") {
        facts(
            "用途" to "SSH 断开后正在跑的任务不中断，重连后接着看。",
            "做法" to "用伪终端托管会话，类似 tmux。",
            "在这台投影仪上" to "可行（/dev/ptmx 可用）。",
            "去向" to "**第三期候选**",
        )
    }
    story("Rust-43 · Rust 版网页终端") {
        facts(
            "用途" to "同 Go-20。",
            "做法" to "同 Go-20。",
            "在这台投影仪上" to "可行，重复。",
            "去向" to "**并入 hub · webshell（第二期）**",
        )
    }
    story("Rust-44 · 共享内存队列") {
        facts(
            "用途" to "程序之间零拷贝传大量数据。",
            "做法" to "共享内存环形缓冲。",
            "在这台投影仪上" to "可行，没有需求。",
            "去向" to "**不纳入**",
        )
    }
    story("Rust-48 · 资源限额沙箱") {
        facts(
            "用途" to "限制后台任务最多用多少 CPU 和内存，不影响看电影。",
            "做法" to "用 cgroup 给子进程设配额。",
            "在这台投影仪上" to "不可行：cgroup 对 shell 不可写（实测）。降低优先级（nice）可以，但不值得单独做。",
            "去向" to "**不可行**",
        )
    }
    story("Rust-49 · Rust 底座（Z6X Rust Core）") {
        facts(
            "用途" to "把 Rust 的底层模块合成一个常驻进程，和 Go hub 并列。",
            "做法" to "一个常驻 Rust 进程，按配置开关看门狗、按键拦截、音频、蓝牙、黑匣子等。",
            "在这台投影仪上" to "改为命令集：Rust 里可行的几乎都是「跑一次给结果」的工具，不需要常驻；原方案选的音频、蓝牙两个模块在这台机器上不可行。",
            "去向" to "**改为 z6x-tools 命令集（见规格）**",
        )
        claim("Rust 专注跑底层硬件（Watchdog、evdev 输入、音频硬件直写、蓝牙原始套接字）", Verdict.Disproved, "音频直写、蓝牙原始套接字在这台机器上都做不到（实测）。")
    }

    related("review-summary", "spec-hub", "spec-tools")
}
