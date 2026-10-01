package z6x.content.proposals

import z6x.framework.Host
import z6x.framework.module

/*
 * 给实现者（agy）的说明：
 * 这一页是 z6x-hub（Go）的规格，是唯一的原始版本。仓库在别处，按这里实现；有疑问或要改规格，先改这一页。
 * 每个模块都有「验收」：实现完必须能在投影仪上按验收步骤跑通，否则不算完成。
 */
val HubSpec = module("spec-hub", "规格：z6x-hub（Go 常驻服务）") {
    keywords = "Go · 单进程 · 配置开关模块 · HTTP API · 验收"
    overview = """
        z6x-hub 是一个 Go 写的常驻服务：一个静态二进制、一个进程、一个配置文件，按配置开关各个功能模块。
        整体架构来自提案「把 Go 服务合成一个程序」，各模块从 Go 提案中筛选（审核结论见「提案总表」）。本页是供实现者使用的规格。
    """
    proposal()

    why("运行环境（全部实测，2026-10-01）") {
        facts(
            "设备" to "极米 Z6X Pro，海思 Hi3751V660，8 核 A55，Android 12，可用内存约 1.5GB",
            "架构" to "编译目标 **linux/arm64**，`CGO_ENABLED=0` 静态编译（64 位静态程序已验证可运行）",
            "运行身份" to "从 ADB 启动：uid 2000（shell），oom_score_adj 继承 **-1000**，不会被内存回收杀掉",
            "端口" to "只能用 **1024 以上**。已被占用：2222 SSH、5555 ADB、7890/7891 Clash、8080/7100 等系统组件、UDP 1900/5353",
            "文件位置" to "程序和配置放 `/data/local/tmp/z6x-hub/`；用户文件在 `/storage/emulated/0`（可写）；U 盘在 `/storage/<卷ID>/`。**不要用 /mnt/media_rw**（shell 无权访问）",
            "能用的系统能力" to "`input keyevent/text`、`am start`、`cmd notification post`、`settings get`、读 /proc 和 /sys/class/thermal、写 /dev/input/event* 和 /dev/uinput、打开 /dev/ptmx、普通身份 ICMP ping",
            "不能用的" to "绑定 <1024 端口、root、CAP_NET_RAW（抓包/原始套接字）、/dev/snd、/dev/net/tun、cgroup、/proc/kmsg",
            "重启" to "只有「重启」和拔电源才是真重启：ADB 自动可用，但 hub 要由 Deck 上的工具箱重新启动",
            "关屏时" to "**已验证（2026-10-01）**：电源菜单选「关屏」后，安卓认为屏幕仍开着（Awake、显示 ON），30 分钟内进程、Wi-Fi、ADB/SSH/8088 端口全程在线。关屏状态下 hub 可以持续服务",
            "关机时" to "**已验证（2026-10-01）**：「关机」其实是挂起到内存的睡眠。约 14 秒后断网，hub 对外不可达；开机后进程原样恢复（同一 PID），**不需要**重新启动。所以 hub 要能经受「突然冻结一段时间再醒来」：定时任务按实际时间补算、网络连接断了要能重连。见「案例：关屏和关机到底做了什么」",
            "已知未验证" to "视频链接没有默认播放器（`am start` 会弹选择框）",
        )
    }

    steps("构建与部署") {
        change("编译", "CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath -ldflags=\"-s -w\" -o z6x-hub ./cmd/z6x-hub", Host.Deck)
        change("部署并启动", """
            adb shell mkdir -p /data/local/tmp/z6x-hub
            adb push z6x-hub hub.yaml /data/local/tmp/z6x-hub/
            adb shell 'cd /data/local/tmp/z6x-hub && chmod 755 z6x-hub && nohup ./z6x-hub -c hub.yaml > hub.log 2>&1 &'
        """, Host.Deck) {
            note = "必须从 ADB 启动（身份和 oom 分）。以后由工具箱的「部署」按钮做这件事。"
        }
        read("健康检查", "curl -s http://192.168.0.109:8090/api/health", Host.Deck) {
            manual = true
            note = "返回 JSON：版本、运行时长、各模块状态。工具箱用它判断 hub 是否在跑。"
        }
    }

    story("架构要求") {
        text("""
            1. 一个进程：所有模块是同一进程里的 goroutine，共享一个 HTTP 服务（默认端口 **8090**），各模块挂在 `/api/<模块>/` 和 `/ui/<模块>/` 下。确实需要独立端口的（WebDAV 方便挂载用 8091）在配置里单独给。
            2. 模块接口：`Name() string`、`Start(ctx) error`、`Stop(ctx) error`、`Routes(mux)`。配置里 `enable: false` 的模块不启动、不注册路由。
            3. 隔离：每个模块在自己的 goroutine 里 `recover`，一个模块 panic 只让它自己进入 failed 状态，在 /api/health 里报告，其他模块不受影响。
            4. 配置：单个 YAML。启动时校验（端口冲突、路径存在、必填项），出错就拒绝启动并打印明确的错误，不要带病运行。
            5. 日志：写 `hub.log`，单文件超过 5MB 轮转，保留 3 份。日志里不能出现 token、密码。
            6. 鉴权：所有 `/api/` 和 `/ui/` 默认要求 `Authorization: Bearer <token>`（token 写在配置里）。可以按模块配置局域网免鉴权，但 shell 类模块（webshell）**不允许**免鉴权。
            7. 资源：空闲时常驻内存目标 < 30MB；CPU 密集的操作（缩略图、哈希）限制并发 ≤ 2。
            8. 外部命令：调用 `input`、`am`、`cmd` 时参数一律用参数数组传给 exec，不拼接 shell 字符串（防注入）；每次调用带超时。
        """)
        text("配置文件示例：")
        change("hub.yaml", """
            listen: ":8090"
            token: "换成随机长字符串"
            data_dir: /data/local/tmp/z6x-hub/data
            modules:
              files:   { enable: true, port: 8091, roots: [/storage/emulated/0] }
              paste:   { enable: true }
              control: { enable: true }
              wol:     { enable: true, devices: { pc: "AA:BB:CC:DD:EE:FF" } }
              metrics: { enable: true }
              speed:   { enable: true }
              notify:  { enable: false }
              lanscan: { enable: false }
              webshell: { enable: false }
        """, Host.Remote)
    }

    story("第一期模块（必须实现）") {
        text("""
            1. core：配置加载、模块注册、`/api/health`、`/api/modules`（列出模块和状态）、日志轮转、Bearer 鉴权、一个首页 `/` 列出已启用模块的入口。
            2. files（来源：「局域网文件共享（WebDAV）」「Deck 存档与截图自动备份」「Rust 版文件下载服务」）：WebDAV（golang.org/x/net/webdav）挂在独立端口；HTTP 下载支持 Range；上传接口 `POST /api/files/upload?path=` 流式落盘（先写 .tmp 再改名）。只允许访问配置的 roots 及其子目录，拒绝 `..` 越界。
            3. paste（来源：「手机把文字和链接发给电视」、局域网传文字）：网页上贴文字 → 存内存（不落盘，最多 100 条）；按钮「打到电视」= 当前输入框 `input text`（只限 ASCII，中文给出明确提示）；「在电视上打开」= `am start -a VIEW -d <URL>`。
            4. control（来源：「智能家居控制入口」「手机当遥控器和触控板」「统一控制接口（JSON-RPC）」「Webhook 触发动作」）：HTTP 接口 `POST /api/control/key`（键名或键值）、`/api/control/text`、`/api/control/app`（包名）、`/api/control/macro`（预设的按键序列）；一个手机网页遥控器（方向键、确认、返回、主页、音量）。按键先用 `input keyevent`；若延迟不可接受，第二期改为调用 z6x-tools 的 `key` 子命令。
            5. wol（来源：「网络唤醒」「跨网段网络唤醒」）：`POST /api/wol/wake?name=` 发魔术包到子网广播地址；配置里的设备列表定时 ping（普通身份 ICMP 可用），返回在线状态。
            6. metrics（来源：「系统状态面板」「Prometheus 监控接口」）：`/api/metrics` 返回 JSON（CPU 使用率、MemAvailable、两个温度区、/data 剩余、wlan0 收发字节），`/metrics` 返回 Prometheus 文本格式。读 /proc 和 /sys，采样间隔 ≥ 5 秒。
            7. speed（来源：「局域网测速」「iperf3 测速」）：网页测速：下载（内存生成数据）、上传（丢弃）、延迟（往返时间）三项，测投影仪和手机 / Deck 之间的局域网速度。
        """)
    }

    story("第二期模块（第一期验收后再做）") {
        text("""
            • notify（「大屏通知」）：`POST /api/notify` → `cmd notification post`。**先验证**通知在电视上能不能显示出来（电视的系统界面可能不显示通知）；显示不了就放弃这个模块。
            • lanscan（「局域网设备扫描」）：SSDP M-SEARCH 扫描局域网设备，列出名称、型号、IP。注意 UDP 1900 已被系统占用，只发不绑或用 SO_REUSEADDR。
            • webshell（「网页终端」）：网页终端（/dev/ptmx 可用）。**等于把 shell 权限开放给网页**：必须鉴权、默认关闭、只监听用户指定的地址，页面上明确提示风险。
            • dlna（「DLNA 投屏接收」）：DLNA 渲染端。**前提**是先装一个播放器并设为视频默认打开方式，否则每次投屏都会弹选择框。
        """)
    }

    verify("验收（每个模块都要在投影仪上跑通）") {
        text("""
            • core：启动后 `/api/health` 列出所有启用模块为 running；改坏配置（端口重复）时拒绝启动并说明原因；不带 token 访问 /api 返回 401。
            • files：Windows / Deck 的文件管理器能挂载 WebDAV 并读写；`curl -r 0-99` 返回 100 字节；`../` 越界请求返回 403；上传 1GB 文件时 hub 内存增长 < 20MB。
            • paste：手机贴一段英文点「打到电视」，电视当前输入框出现这段文字；贴中文时页面提示不支持。
            • control：手机网页按「主页」，电视回到 Projectivy；连按 10 次音量加，音量确实增加。
            • wol：对一台开了网络唤醒的电脑发唤醒，电脑开机；关机的设备显示离线，开机后 1 分钟内显示在线。
            • metrics：数值和 `adb shell cat /proc/meminfo` 等手工读取的结果一致；`/metrics` 能被 Prometheus 解析。
            • speed：Deck 上网页测速结果和 `iperf3` / 文件拷贝速度同一量级。
            • 全体：空闲 10 分钟 RSS < 30MB；kill 掉一个模块的 goroutine 内部故障（用测试开关触发 panic）后其他模块仍工作。
        """)
    }

    related("review-summary", "spec-tools", "go-server", "native-exec")
}
