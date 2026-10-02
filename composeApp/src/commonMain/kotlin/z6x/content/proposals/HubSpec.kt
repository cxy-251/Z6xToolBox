package z6x.content.proposals

import z6x.framework.Host
import z6x.framework.module

/*
 * z6x-hub（Go）的规格与实现状态。代码位于仓库的 hub/ 目录。
 * 修改功能时先改本页，再改代码；验收结果以实测为准。
 */
val HubSpec = module("spec-hub", "规格：z6x-hub（Go 常驻服务）") {
    keywords = "Go · 单进程 · 配置开关模块 · HTTP API · 验收"
    overview = """
        z6x-hub 是用 Go 编写的常驻服务：一个静态二进制、一个进程、一个配置文件，按配置开关各功能模块。代码位于本仓库的 `hub/` 目录。
        **状态（2026-10-02）：第一期六个模块已实现并部署到投影仪，验收结果见下文。** 整体架构来自提案「把 Go 服务合成一个程序」，各模块从 Go 提案中筛选（审核结论见「提案总表」）。
    """
    partial("2026-10-02")

    why("运行环境（均已实测）") {
        facts(
            "设备" to "极米 Z6X Pro，海思 Hi3751V660，8 核 A55，Android 12，可用内存约 1.5GB",
            "架构" to "编译目标 **linux/arm64**，`CGO_ENABLED=0` 静态编译（64 位静态程序在本机可以运行）",
            "运行身份" to "从 ADB 启动：uid 2000（shell），oom_score_adj 继承 **-1000**，不会因内存回收被结束（2026-10-02 部署后实测）",
            "端口" to "只能使用 **1024 以上**的端口。已被占用：2222 SSH、5555 ADB、7890/7891 Clash、8080/7100 等系统组件、UDP 1900/5353。hub 使用 8090（主端口）和 8091（WebDAV）",
            "文件位置" to "程序和配置位于 `/data/local/tmp/z6x-hub/`；用户文件在 `/storage/emulated/0`（可写）；U 盘在 `/storage/<卷ID>/`。**不要使用 /mnt/media_rw**（shell 无权访问）",
            "可用的系统能力" to "`input keyevent/text`、`am start`、`cmd package`、读取 /proc 和 /sys/class/thermal、普通身份的 ICMP ping、UDP 广播",
            "不可用的能力" to "绑定 1024 以下端口、root、CAP_NET_RAW（抓包、原始套接字）、/dev/snd、/dev/net/tun、cgroup、/proc/kmsg",
            "时区" to "安卓没有 /etc/localtime，Go 默认使用 UTC。hub 内嵌时区数据，并读取 `persist.sys.timezone` 设置本地时区",
            "关屏" to "电源菜单选择「关屏」后，安卓认为屏幕仍处于开启状态，hub 持续提供服务（实测）",
            "关机" to "「关机」实际上是挂起到内存的睡眠：约 14 秒后断网，开机后进程原样恢复（同一 PID），无需重新启动。hub 的定时任务按实际时间运行，可以承受冻结",
            "重启" to "只有「重启」和断电才是真正的重启：ADB 会自动运行，但 hub 需要由 Deck 重新部署启动（`hub/deploy.sh`）",
        )
    }

    steps("构建与部署") {
        change("一键编译、部署并启动", "./hub/deploy.sh", Host.Deck) {
            note = """
                在项目根目录执行。首次运行时从 `hub.example.yaml` 生成 `hub.yaml`，并写入随机 token（该文件已加入 .gitignore，不会提交）。
                脚本依次完成：交叉编译 arm64 静态程序 → 推送程序和配置 → 校验配置 → 结束旧进程 → 用 `setsid` 启动新进程 → 请求健康检查。
                必须**从 ADB 启动**，才能获得 shell 身份和 -1000 的 oom 分值。
            """
            outcome = "最后输出 /api/health 的 JSON，各模块状态均为 running。整个过程约 5 秒。"
        }
        read("健康检查", "curl -s http://192.168.0.109:8090/api/health", Host.Deck) {
            varies = true
            note = "无需 token：只返回版本、运行时长和各模块状态，不含敏感信息，供工具箱判断 hub 是否在运行。其余所有接口都需要 token。"
        }
        read("查看日志", "tail -20 /data/local/tmp/z6x-hub/hub.log", Host.Adb) {
            varies = true
            note = "日志超过 5MB 时轮转，保留 3 份旧文件。日志中不记录请求参数和请求头，因此不会出现 token。"
        }
        read("确认运行身份与内存", "p=${'$'}(pidof z6x-hub); grep -E '^Uid|VmRSS' /proc/${'$'}p/status; cat /proc/${'$'}p/oom_score_adj", Host.Adb) {
            varies = true
            captured("2026-10-02", """
                Uid:	2000	2000	2000	2000
                VmRSS:	    8752 kB
                -1000
            """)
        }
    }

    story("架构") {
        text("""
            1. **单进程**：所有模块是同一进程中的协程，共用一个 HTTP 服务（端口 **8090**），各模块挂在 `/api/<模块>/` 和 `/ui/<模块>/` 下。需要独立端口的模块（WebDAV 使用 8091）在配置中单独指定。
            2. **模块接口**：`Name()`、`Start(ctx, env)`、`Stop(ctx)`、`Routes(router)`。配置中未启用的模块既不启动，也不注册路由。代码见 `hub/internal/core/module.go`。
            3. **隔离**：每个模块的处理函数都包裹 recover，某个模块 panic 只会使它自己进入 failed 状态并在 /api/health 中报告，其他模块不受影响（单元测试 `TestPanicIsolation` 覆盖）。
            4. **配置**：单个 YAML 文件。启动时校验端口冲突、低位端口、token 强度、共享目录是否存在等；校验失败则拒绝启动并说明原因，不带病运行。`z6x-hub -c hub.yaml -check` 可只做校验。
            5. **日志**：写入 `hub.log`，单文件超过 5MB 时轮转，保留 3 份。时间使用本地时区。
            6. **鉴权**：除 /api/health 外，所有接口和页面都需要 token。支持三种方式：`Authorization: Bearer <token>`（脚本）、登录后的 Cookie（浏览器）、Basic 认证密码（WebDAV 客户端，用户名任意）。token 比较采用定长时间算法。
            7. **资源**：空闲常驻内存目标小于 30MB；测速等大流量操作不经过磁盘。
            8. **外部命令**：调用 `input`、`am`、`cmd` 时，参数一律以数组传给 exec，不拼接 shell 字符串；每次调用都有超时；按键名、包名、链接协议在调用前校验（单元测试覆盖）。
        """)
    }

    story("第一期模块（已实现）") {
        text("""
            1. **core**：配置加载与校验、模块注册、`/api/health`、`/api/modules`、登录页、首页（列出已启用模块的入口）、日志轮转、鉴权。
            2. **files**（来源：「局域网文件共享（WebDAV）」「Deck 存档与截图自动备份」「Rust 版文件下载服务」）：WebDAV 位于 8091 端口；`/api/files/get` 下载，支持 Range（断点续传、视频拖动）；`/api/files/upload` 流式上传，先写临时文件，完整收到后再改名；`/api/files/list` 列目录；`/ui/files/` 网页浏览与上传。共享目录在配置中列出，并自动加入插入的 U 盘；路径先规范化再匹配共享目录，`..` 无法越出共享范围；不允许删除或改名共享根目录。
            3. **paste**（来源：「手机把文字和链接发给电视」）：文字只保存在内存中（最多 100 条，重启即清空）；「输入到电视」调用 `input text`，只支持可打印的 ASCII 字符，中文会给出明确提示；链接可在电视上打开。
            4. **control**（来源：「智能家居控制入口」「手机当遥控器和触控板」「统一控制接口」「Webhook 触发动作」）：`/api/control/key`（按键名或键值，可重复）、`/text`、`/app`（包名，自动查找电视或普通启动入口）、`/macro`（配置中预设的按键序列）；`/ui/control/` 为手机网页遥控器。
            5. **wol**（来源：「网络唤醒」「跨网段网络唤醒」）：`/api/wol/wake?name=` 向网段广播地址发送魔术包（可为每台设备指定广播地址以跨网段唤醒）；配置了 IP 的设备每隔一段时间 ping 一次，显示在线状态。
            6. **metrics**（来源：「系统状态面板」「Prometheus 监控接口」）：`/api/metrics/` 返回 JSON（CPU 使用率、可用内存、各温度区、/data 剩余空间、网卡收发字节、运行时长），`/metrics` 返回 Prometheus 文本格式；采样间隔不少于 5 秒。
            7. **speed**（来源：「局域网测速」「iperf3 测速」）：下载（服务端内存中的伪随机数据）、上传（接收后丢弃）、延迟三项；`/ui/speed/` 网页测速。
        """)
        text("配置示例见 `hub/hub.example.yaml`。")
    }

    verify("第一期验收结果（2026-10-02，投影仪实测）") {
        facts(
            "core" to "✓ 6 个模块均为 running；无 token 或 token 错误访问 /api 返回 401，正确 token 返回 200；未登录打开首页跳转登录页；端口冲突的配置被拒绝启动（退出码 2，并说明「端口 8090 冲突：listen 与 files」）",
            "core · 隔离" to "✓ 单元测试：模块 a panic 后返回 500 并进入 failed，模块 b 仍返回 200",
            "files · Range" to "✓ `curl -r 0-99` 返回 100 字节，状态码 206",
            "files · 越界" to "✓ `../` 和 `%2e%2e` 形式的越界请求均返回 404：路径规范化后不属于任何共享目录。原规格要求 403，实际返回 404，同样无法访问共享以外的文件",
            "files · WebDAV" to "✓ PROPFIND 列出共享顶层；不带密码返回 401",
            "files · 上传 1GB" to "✓ 上传前内存 10.2MB，上传过程中峰值 12.2MB，增长 2MB（要求小于 20MB）",
            "paste" to "✓ 接口可保存文字；中文「输入到电视」返回明确提示。**待人工确认**：电视当前输入框中出现所发送的英文",
            "control" to "✓ 按 Home 后焦点从 SmartTube 回到 Projectivy；音量加 2 次由 6 变为 10，减 2 次恢复为 6；未知按键被拒绝；单次按键约 0.1~0.24 秒",
            "wol" to "✓ 在线检测：Deck 显示在线（4ms），不存在的地址显示离线；魔术包格式由单元测试验证，广播地址自动计算为 192.168.0.255。**待验证**：实际唤醒一台开启了网络唤醒的电脑",
            "metrics" to "✓ 内存、温度、/data 剩余与 /proc/meminfo、thermal 节点、df 的手工读数一致；/metrics 的 9 行指标格式均合法",
            "speed" to "✓ 下载 4.7MB/s、上传 17Mbps，与 `adb push` 的 4.4MB/s 处于同一量级。瓶颈在 Deck 的无线连接（2.4GHz，链路速率 108Mbps），投影仪本身为 5GHz、520Mbps，本地写盘 92MB/s",
            "资源" to "✓ 空闲运行 10 分 15 秒后内存为 12.0MB（刚部署时约 8.7MB；目标小于 30MB）",
        )
    }

    story("第二期模块（第一期验收完成后再做）") {
        text("""
            • notify（「大屏通知」）：`POST /api/notify` 调用 `cmd notification post`。**需先验证**通知能否在电视界面上显示；若不显示则放弃该模块。
            • lanscan（「局域网设备扫描」）：通过 SSDP M-SEARCH 扫描局域网设备，列出名称、型号和 IP。UDP 1900 已被系统占用，只发送、不绑定该端口。
            • webshell（「网页终端」）：网页终端（/dev/ptmx 可用）。**这相当于把 shell 权限开放给网页**：必须鉴权、默认关闭，页面上明确提示风险。
            • dlna（「DLNA 投屏接收」）：DLNA 渲染端。**前提**是先安装播放器并设为视频的默认打开方式，否则每次投屏都会弹出选择框。
            • 配置页（「网页改配置」）：第一期只读展示，第二期允许在网页上修改。
            • iperf3 兼容（「iperf3 测速」）：作为 speed 的补充，可选。
        """)
    }

    related("review-summary", "spec-tools", "go-server", "native-exec")
}
