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
        **状态（2026-10-02）：第一期六个模块、第二期的 lanscan、webshell、配置页、notify，以及第三期的资源库模块均已实现。同一个程序部署在投影仪和手机两台设备上，各自按配置启用不同模块，验收结果见下文。** 整体架构来自提案「把 Go 服务合成一个程序」，各模块从 Go 提案中筛选（审核结论见「提案总表」）。
    """
    partial("2026-10-02")

    why("设备分工") {
        facts(
            "投影仪" to "专门放电影，并提供遥控类功能：files、paste、control、wol、metrics、speed、notify、lanscan（8 个模块）。资源库模块关闭：漫画、短视频这类需要大量读取小文件的内容不适合放在投影仪上",
            "手机" to "Redmi Note 12 Turbo，机身存储 1TB，作为资源库：library、files、metrics、speed（4 个模块）。遥控、发送文字、网络唤醒只对电视有意义，不启用",
            "配置" to "每台设备一份配置 `hub/devices/<设备名>.yaml`，各有独立的 token（不入库）；模板为同目录下的 `<设备名>.example.yaml`；设备名与 ADB 地址的对应关系在 `hub/devices/devices.txt` 中。顶层 `name` 为设备的显示名称，用于首页标题和资源库名称",
        )
    }

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
        change("在工具箱中部署", "", Host.Deck) {
            note = "「📡 设备」页顶部选择设备（投影仪或手机），z6x-hub 区域会自动显示该设备的运行状态；点击「部署并启动」（已运行时为「重新部署」）即执行下方的部署脚本，输出实时显示在页面上。命令行等效：`./run.sh --hub-deploy <ADB 地址>`。"
        }
        change("一键编译、部署并启动", "./hub/deploy.sh phone", Host.Deck) {
            note = """
                在项目根目录执行，参数为设备名（`projector`、`phone`，省略时为投影仪）或 ADB 地址。手机的无线调试端口每次开启都会变化，`devices.txt` 中只记录 IP，脚本从 `adb devices` 中找出该 IP 当前已连接的端口；直接给出地址时，按 IP 反查设备名。
                首次部署某台设备时从 `devices/<设备名>.example.yaml` 生成 `devices/<设备名>.yaml`，并写入随机 token（已加入 .gitignore，不会提交；token 不在输出中显示）。
                脚本依次完成：交叉编译 arm64 静态程序 → 推送程序和配置 → 校验配置 → 结束旧进程 → 用 `setsid` 启动新进程 → 请求健康检查。
                必须**从 ADB 启动**，才能获得 shell 身份和 -1000 的 oom 分值。
            """
            outcome = "最后输出「hub 已启动：4 个模块运行中，0 个失败」（手机）或「8 个模块」（投影仪）。整个过程约 5 秒。"
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
            4. **配置**：每台设备一个 YAML 文件。启动时校验端口冲突、低位端口、token 强度、共享目录是否存在等；校验失败则拒绝启动并说明原因，不带病运行。`z6x-hub -c hub.yaml -check` 可只做校验。
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
            7. **speed**（来源：「局域网测速」「iperf3 测速」）：下载（服务端内存中的伪随机数据）、上传（接收后丢弃）、延迟三项；`/ui/speed/` 网页测速，下载和上传各测 8 秒，并实时显示进度。
        """)
        text("配置示例见 `hub/devices/projector.example.yaml` 和 `hub/devices/phone.example.yaml`。")
    }

    verify("第一期验收结果（2026-10-02，投影仪实测）") {
        facts(
            "core" to "✓ 6 个模块均为 running；无 token 或 token 错误访问 /api 返回 401，正确 token 返回 200；未登录打开首页跳转登录页；端口冲突的配置被拒绝启动（退出码 2，并说明「端口 8090 冲突：listen 与 files」）",
            "core · 隔离" to "✓ 单元测试：模块 a panic 后返回 500 并进入 failed，模块 b 仍返回 200",
            "files · Range" to "✓ `curl -r 0-99` 返回 100 字节，状态码 206",
            "files · 越界" to "✓ `../` 和 `%2e%2e` 形式的越界请求均返回 404：路径规范化后不属于任何共享目录。原规格要求 403，实际返回 404，同样无法访问共享以外的文件",
            "files · WebDAV" to "✓ PROPFIND 列出共享顶层；不带密码返回 401",
            "files · 上传 1GB" to "✓ 上传前内存 10.2MB，上传过程中峰值 12.2MB，增长 2MB（要求小于 20MB）",
            "paste" to "✓ 接口可保存文字；中文「输入到电视」返回明确提示。网页在 Deck 浏览器中使用正常（2026-10-02 用户确认）",
            "control" to "✓ 按 Home 后焦点从 SmartTube 回到 Projectivy；音量加 2 次由 6 变为 10，减 2 次恢复为 6；未知按键被拒绝；单次按键约 0.1~0.24 秒",
            "wol" to "✓ 在线检测：Deck 显示在线（4ms），不存在的地址显示离线；魔术包格式由单元测试验证，广播地址自动计算为 192.168.0.255。**未验证**：目前没有开启网络唤醒的电脑，暂不测试实际唤醒",
            "metrics" to "✓ 内存、温度、/data 剩余与 /proc/meminfo、thermal 节点、df 的手工读数一致；/metrics 的 9 行指标格式均合法",
            "speed" to "✓ 下载 4.7MB/s、上传 17Mbps，与 `adb push` 的 4.4MB/s 处于同一量级。网页测速最初固定传输 150MB 且无进度显示，在 Deck 上需近一分钟，看起来像没有结果；已改为按时间测量并实时显示，实测 16.5 秒完成（延迟 8.7ms，下载 50.9Mbps，上传 20.4Mbps）。瓶颈在 Deck 的无线连接（2.4GHz，链路速率 108Mbps），投影仪本身为 5GHz、520Mbps，本地写盘 92MB/s",
            "资源" to "✓ 空闲运行 10 分 15 秒后内存为 12.0MB（刚部署时约 8.7MB；目标小于 30MB）",
        )
    }

    story("第二期模块") {
        text("""
            • **lanscan（已实现）**（「局域网设备扫描」）：向 239.255.255.250:1900 发送 SSDP M-SEARCH，收集回复后读取各设备的描述文件，列出名称、厂商、型号和类型。UDP 1900 已被系统占用，因此只从随机端口发送、接收。只读取与回复来源 IP 相同的描述地址，避免被引导访问其他主机。
            • **webshell（已实现，默认关闭）**（「网页终端」）：打开 /dev/ptmx 得到伪终端，shell 以从端为控制终端，hub 在主端与 WebSocket 之间转发数据，终端界面为内嵌的 xterm.js。**这相当于把 shell 权限开放给网页**，因此：默认关闭；除 token 外还必须配置 `allow_from`（允许访问的 IP 或网段）；检查 WebSocket 的 Origin，防止其他网站借用浏览器中已登录的 Cookie 连入；同时打开的终端数有上限；页面顶部明确提示风险。
            • **配置页（已实现）**（「网页改配置」）：首页「编辑配置」进入。token 不在页面上显示；保存前按启动时的规则校验，不通过则不做任何修改；通过后旧配置备份为 hub.yaml.bak，hub 用 exec 在原进程中重启，进程号、shell 身份和 -1000 的 oom 分值保持不变。此后 `deploy.sh` 以投影仪上的配置为准，部署时先拉回本机，不会覆盖网页上的修改；需要推送本机配置时加 `--push-config`。
            • **notify（已实现）**（「大屏通知」）：`POST /api/notify`（JSON 的 title/text，兼容 Gotify 风格的 message，也接受表单参数）调用 `cmd notification post` 发送通知；`/ui/notify/` 为网页发送页。实测（2026-10-02）：通知归属 shell 的通知渠道，重要级别为默认的 3，**进入通知界面而不弹出提醒**，桌面和全屏播放时均不弹出。按用户确认，进入通知界面即可，不需要弹出。每条通知使用不同的 tag，新通知不覆盖旧通知。
            • **dlna（不再需要）**（「DLNA 投屏接收」）：lanscan 扫描发现投影仪自身已以 MediaRenderer 身份对外提供 DLNA 接收（来自系统的无线投屏应用），hub 再实现一个属于重复建设。
            • **iperf3 兼容（暂缓，可选）**（「iperf3 测速」）：网页测速已能满足日常需要。
        """)
    }

    verify("第二期验收结果（2026-10-02，投影仪实测）") {
        facts(
            "lanscan" to "✓ 3.2 秒完成扫描，发现路由器（TP-LINK，InternetGatewayDevice）和投影仪自身（MediaRenderer）",
            "webshell · 正常连接" to "✓ 在允许的地址上获得 shell：uid 2000，窗口大小消息生效（30 行 × 100 列）",
            "webshell · 拒绝" to "✓ Origin 与地址不一致、没有 token、来源 IP 不在 allow_from 中，三种情况均被拒绝，后者记录在日志中",
            "webshell · 测试中发现的问题" to "首次测试时 WebSocket 握手失败：hub 的请求日志包装层没有实现 http.Hijacker，WebSocket 无法接管连接。补上 Hijack 和 Unwrap 后正常，并加入回归测试。这次失败同时验证了 panic 隔离：只有 webshell 进入 failed，其他模块不受影响",
            "notify" to "✓ 通过接口发送的通知出现在系统通知列表中（dumpsys 可查），内容中的分号和引号原样显示；空内容被拒绝。用户确认通知界面中可见，不弹出",
            "配置页" to "✓ 读取时 token 被隐藏；端口冲突的配置被拒绝，文件未改动；有效修改保存后 hub 原地重启，进程号不变（18688），uid 2000，oom -1000，旧配置已备份，原 token 仍然有效",
        )
    }

    story("第三期：资源库模块（已实现）") {
        text("""
            来源：omni-deck 的存储压力较大，需要把不常玩、可在浏览器中直接运行的游戏和部分媒体转移出去，并能在其他设备的浏览器中访问。
            1. **兼容 omni-deck**：资源库以 `omnilibrary.json` 标记，目录结构与 omni-deck 相同（`standalone_games/<分类>_games/`、`media_library/...`），同一个文件夹可以在两边来回移动。游戏 ID 即文件夹名，RPG 的存档在 `www/save`，SLG 在游戏目录下的 `save`。
            2. **自动发现**：机身存储的 `omni_library/` 和 U 盘根目录（或其中的 `omni_library/`）上的资源库会自动出现，无需配置。
            3. **网页游戏**：`/game/<id>/...` 提供游戏文件，HTML 中在 `<head>` 后注入 omni-deck 的 `rpg-runtime.js`，存档读写走 `/save/` 和 `/api/save/`，文件名大小写不敏感（RPG Maker 素材常有大小写不一致的问题）。
            4. **漫画**：CBZ/ZIP 按页读取，不解压到磁盘；封面缩略图首次生成后缓存，同时最多生成 2 张。
            5. **短视频**：按「平台 / 作者」浏览，支持视频与图集，视频支持 Range 拖动。
            6. **导入**：`hub/library-import.sh` 把本机文件夹打包后流式上传，设备端边接收边解压；拒绝绝对路径、`..`、符号链接和多个顶层目录（单元测试覆盖）。目标设备默认为手机，`Z6X_DEVICE=projector` 可改为投影仪。
        """)
    }

    verify("第三期验收结果（2026-10-02）") {
        facts(
            "投影仪" to "✓ 建库、导入、游戏运行与存档、漫画、视频、越界防护均正常。随后按用户决定关闭：投影仪专门放电影",
            "缺陷 · 漫画页停在「读取中」" to "原因：资源库为空时，Go 中值为 nil 的切片被编码为 JSON 的 null，页面脚本按数组处理而出错。修复：列表一律初始化为空切片，并加入回归测试 `TestEmptyListsAreArrays`",
            "手机 · 环境" to "Android 15（HyperOS 3），arm64，**SELinux 为 Enforcing**（投影仪为 Permissive）。shell 属于 sdcard_rw 组，可以读写 `/storage/emulated/0`；写入的文件经安卓的 FUSE 层，属主显示为 u0_a223。hub 以 uid 2000、oom -1000 运行，启动后内存 12MB",
            "手机 · 功能" to "✓ 4 个模块均为 running；在机身存储建库（剩余约 400GB）；导入 111MB 的 SLG 游戏；游戏页面、图标、存档写入/读取/删除均正常；日志中无权限错误。SELinux Enforcing 并未妨碍 hub 的任何功能",
            "手机 · 网速偏慢" to "导入 111MB 用时 2 分 45 秒（约 0.7MB/s），下载测速 1.4MB/s。**最初判断为手机灭屏进入深度休眠（Doze）所致，复测后推翻**：手机亮屏后下载仍只有 0.6～0.9MB/s、上传 0.5MB/s，同时投影仪也从 5.3MB/s 降到 0.7～1.0MB/s。两台设备同时变慢，瓶颈在 Deck 一侧：Deck 连在 2.4GHz（11 信道）上，链路速率由 108Mbps 降到 27Mbps，到路由器的延迟在 3～110ms 之间波动，发送失败 4143 次。手机同样连在 2.4GHz 上，投影仪为 5GHz。教训：只测一台设备就下结论不可靠，应同时测一台对照设备。大批量转移建议让 Deck 和手机都连 5GHz，或改用数据线（`adb push`）",
            "手机 · 温度" to "手机有 83 个温区，其中部分读数不是真实温度（如 bcl-warn 为 -273）。metrics 已过滤 -40～150 ℃ 以外的读数",
            "手机 · 注意" to "无线调试在重启或切换 Wi-Fi 后会关闭，端口每次都不同，需要重新开启后 `adb connect`；手机重启后 hub 需重新部署。建议在路由器中为手机绑定固定 IP",
        )
    }

    related("review-summary", "spec-tools", "go-server", "native-exec")
}
