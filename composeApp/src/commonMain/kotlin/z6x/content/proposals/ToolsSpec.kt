package z6x.content.proposals

import z6x.framework.Host
import z6x.framework.module

/*
 * z6x-tools（Rust）的规格与实现状态。代码位于仓库的 tools/ 目录。
 */
val ToolsSpec = module("spec-tools", "规格：z6x-tools（Rust 命令集）") {
    keywords = "Rust · BusyBox 式 · 子命令 · musl 静态 · 验收"
    overview = """
        **状态（2026-10-02）：第一期 7 个子命令与第二期的 iobench、run 已实现；pack 不实现（原因见下文）。** 曾因 Deck 空间不足暂缓，后由用户清理空间后恢复；编译参数专门为 Deck 调整过。
        z6x-tools 设计为 Rust 编写的命令集，形式与 BusyBox 相同：一个静态二进制 `z6x`，以子命令区分功能（`z6x sys`、`z6x ports` 等），执行完毕即退出。
        只有极少数子命令带 `--daemon` 常驻。它负责底层操作：读取 /proc、写入输入设备、计算哈希。网络服务归 z6x-hub（Go）负责。
    """
    proposal()

    why("分工依据") {
        text("""
            • 原方案把 Rust 也做成常驻底座（提案「Rust 底座」），与 Go hub 并列为两个常驻进程。审核后改为**命令集**：Rust 提案中在本机可行的几乎都是「运行一次即给出结果」的工具（查询端口、查询温度、计算哈希、注入按键），无需常驻。
            • 需要常驻的只有按键重映射（监听遥控器），使用 `z6x keymap --daemon`。
            • hub 需要底层能力时调用 `z6x` 子命令，并解析其 JSON 输出。
        """)
        facts(
            "编译目标" to "**aarch64-unknown-linux-musl**，静态链接，`opt-level=\"z\"`、`lto=true`、`strip=true`、`panic=\"abort\"`（目标体积 < 2MB）",
            "运行身份" to "从 ADB shell 运行：uid 2000，所属组包括 input、uhid（可读写 /dev/input/event* 和 /dev/uinput）",
            "存放位置" to "`/data/local/tmp/z6x-tools/z6x`；可像 BusyBox 一样建立链接：`z6x-ports` 等同于 `z6x ports`",
            "不可用的资源" to "/dev/snd（声卡）、/dev/net/tun、HCI 蓝牙套接字（内核没有 hci 设备）、/dev/cec0、/dev/hidg0、/dev/graphics/fb0、cgroup、/proc/kmsg、原始套接字",
        )
    }

    story("命令约定") {
        text("""
            1. 每个子命令都支持 `--json`：输出一个 JSON 对象，供 hub 和工具箱解析；不加此参数时输出便于阅读的表格。
            2. 退出码：0 为成功，1 为一般错误，2 为参数错误，3 为权限不足（例如无法打开设备节点）。错误信息写入 stderr。
            3. `z6x --help` 列出全部子命令；`z6x <子命令> --help` 列出参数。
            4. 不依赖任何系统库或配置文件；需要设备节点的命令，节点路径可通过参数覆盖。
        """)
    }

    story("第一期子命令") {
        text("""
            1. `z6x sys`（「系统指标」「温度监控」）：一次性输出 CPU 使用率（间隔 500ms 读取两次 /proc/stat）、MemTotal/MemAvailable、所有 thermal_zone 的 type 和温度、/data 剩余空间以及运行时长。`--watch 2` 表示每 2 秒刷新一次。
            2. `z6x ports`（对应「核查：端口的所属进程」）：读取 /proc/net/tcp、tcp6、udp、udp6，列出监听端口、uid 和包名（调用 `pm list packages -U`；/data/system/packages.list 对 shell 不可读，已实测）。
            3. `z6x key`（「快速按键注入」「虚拟 USB 键盘」）：注入按键。默认通过 /dev/uinput 创建虚拟键盘设备（不依赖遥控器节点号）。/dev/uinput 可写已实测，**但系统是否接受该虚拟键盘的按键尚未验证**，实现时应先验证这一点，若不可行则改为直接写入遥控器节点（/dev/input/event13 同样可写）。示例：`z6x key home`、`z6x key volup --repeat 5 --interval 50ms`；`z6x key --bench` 测量单次注入耗时。键名表包括方向、确认、返回、主页、菜单、音量、静音、电源和媒体键。
            4. `z6x keymap --daemon`（「遥控器按键重映射」）：监听遥控器节点（默认按设备名 "XGIMI RC" 查找，不固定为 event13），识别长按和双击，执行配置中的动作（发送按键序列或运行命令）。**不独占设备**（不调用 EVIOCGRAB），原有按键功能不受影响。遥控器重连导致节点变化时，应能自动重新绑定。
            5. `z6x hash`（「文件哈希与查重」「重复文件查找」）：查找目录中的重复文件：先按大小分组，再比较首尾 64KB，最后计算全文件 xxh3；输出重复组和可节省的空间。**只报告，不删除**，是否删除由用户决定。
            6. `z6x watch`（「目录变化监听」）：用 inotify 监听目录，输出 CLOSE_WRITE / MOVED_TO 事件（每行一个 JSON），可选 `--exec` 对每个事件执行命令。注意 max_user_watches 只有 8192。
            7. `z6x ping`（「网络延迟雷达」）：对目标连续发送 ICMP ping（使用普通身份的 ICMP 套接字，已验证可用），输出最小、平均、最大延迟以及抖动和丢包率。
        """)
    }

    story("第二期子命令") {
        text("""
            • `z6x iobench`（「U 盘健康检测」「存储读写测速」）：对 U 盘上的测试文件进行顺序和随机读写测速，测试完毕后删除测试文件。FAT32 / exFAT 可能不支持 O_DIRECT，此时应自动退回普通读写并在结果中注明。
            • `z6x pack`（「快速压缩归档」「LZ4 解压」）：用 zstd 打包和解包目录（tar + zstd），用于备份存档和日志。
            • `z6x run`（「进程守护」「进程看门狗」）：极简的进程守护：启动一个命令，崩溃后按退避间隔重启，并记录退出原因。用于守护 hub。
        """)
    }

    why("在 Deck 上编译（已实测）") {
        facts(
            "命令" to "`./tools/build.sh test`（单元测试）、`release`（日常）、`dist`（开启链接时优化，CI 使用）、`deploy <设备名>`（编译并推送）",
            "为 Deck 做的设置" to "以最低优先级编译（nice 19、ionice idle），编译时 Deck 仍可流畅使用；并行数限制为 4（4 核 8 线程留一半）；开发编译不生成调试信息、不做增量编译",
            "依赖" to "只依赖 libc。命令行解析、JSON 输出、xxHash64 均为自行实现，无需下载或编译其他依赖",
            "结果" to "首次 release 编译 8 秒、之后增量约 2 秒；release 640KB、dist 532KB，均为静态链接的 aarch64 程序（规格要求小于 2MB）；整个 target 目录 29MB；12 个单元测试全部通过",
            "musl 与 glibc 的差别" to "ioctl 的请求号参数在 glibc 中为 unsigned long、在 musl 中为 int，在 Deck 上测试通过而交叉编译失败；调用处改为 `as _` 适配两者",
        )
    }

    verify("实测结果（2026-10-02，投影仪）") {
        facts(
            "sys" to "✓ 可用内存 1.6GB，与 /proc/meminfo 一致；--json 输出可正常解析",
            "ports" to "✓ 5555 为 shell、7890 为 Clash、8090/8091 为 hub。系统 uid 1000 由约 50 个系统包共用，最初全部列出导致无法阅读，改为系统 uid 只显示固定名称（system、mdnsr 等）。2222 未出现，因当时 SimpleSSHD 未运行",
            "ping" to "✓ 对路由器 5 次：平均 22.8ms，系统 ping 为 26.4ms，处于同一量级",
            "hash" to "✓ 三个 300KB 文件中两个完全相同、一个仅中间 1 字节不同（首尾采样相同），只报告真正重复的两个；不删除任何文件",
            "watch" to "✓ 每个写入的文件只报告一次；开始监听后新建的子目录自动加入；移入的文件报告为 moved_to",
            "run" to "✓ 记录退出码，重启间隔 1、2、4 秒（最初为 2 秒起，已修正），达到 --max-restarts 后停止",
            "key、keymap、iobench" to "**待测**：前两项会在电视上产生按键，iobench 会大量读写存储；当时用户正在观看视频，留待用户同意后再测",
            "测试中的问题" to "结束后台 watch 时用了 `pkill -f \"z6x watch\"`，模式同时匹配到执行测试的 shell，把它也结束了（退出码 143）。这是第三次因 pkill -f 误杀自身，改为一律按 PID 结束（见「方案调整记录」）",
        )
    }

    consequences("与规格的差异") {
        facts(
            "hash 的哈希算法" to "规格写 xxh3，实现用同系列的 xxHash64：可以自行实现、不引入依赖，碰撞概率同样可忽略（已用官方已知值校验）",
            "ping" to "只接受 IP 地址：静态程序在安卓上无法解析域名（安卓没有 /etc/resolv.conf，域名解析走 netd）",
            "key 的方式" to "默认用 /dev/uinput 创建虚拟键盘（安卓按 Generic.kl 解释按键）；--via event 直接写遥控器节点（按遥控器自己的按键表解释，主页等键可能无效）。哪种被系统接受待实测",
            "pack" to "不实现：zstd 的 Rust 库需要 C 编译器交叉编译，而 Deck 上只有 Rust 自带的链接器；需求已由 hub 文件管理的「打包下载」（zip，支持 Zip64）覆盖",
        )
    }

    verify("验收") {
        text("""
            • 整体：`file z6x` 显示 ARM aarch64、statically linked；体积小于 2MB；在投影仪上能运行 `z6x --help`。
            • sys：数值与 `cat /proc/meminfo`、thermal 节点的手工读数一致；`--json` 输出能被 `jq` 解析。
            • ports：列出的 5555 对应 shell、2222 对应 org.galexander.sshd、7890 对应 Clash，与「核查：端口的所属进程」的结果一致。
            • key：执行 `z6x key home` 后电视回到桌面；`--bench` 报告的单次耗时应明显低于 `input keyevent`（后者每次都要启动 Java 进程）。
            • keymap：配置「长按返回键打开 Activity Launcher」后，长按生效，短按返回仍正常；遥控器关闭后再打开，仍然有效。
            • hash：在预先准备的测试目录（含已知重复文件）上报告正确，且不删除任何文件。
            • watch：向被监听目录写入一个文件，写入完成时输出一条事件，写入过程中不重复输出。
            • ping：对路由器的 ping 结果与系统 `ping` 处于同一量级。
        """)
    }

    related("review-summary", "spec-hub", "port-owner", "proc-metrics")
}
