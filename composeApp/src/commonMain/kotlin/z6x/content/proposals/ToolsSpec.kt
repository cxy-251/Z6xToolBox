package z6x.content.proposals

import z6x.framework.Host
import z6x.framework.module

/*
 * 给实现者（agy）的说明：
 * 这一页是 z6x-tools（Rust）的规格，是唯一的原始版本。仓库在别处，按这里实现；有疑问或要改规格，先改这一页。
 */
val ToolsSpec = module("spec-tools", "规格：z6x-tools（Rust 命令集）") {
    keywords = "Rust · BusyBox 式 · 子命令 · musl 静态 · 验收"
    overview = """
        z6x-tools 是一个 Rust 写的命令集，像 BusyBox 一样：一个静态二进制 `z6x`，用子命令区分功能（`z6x sys`、`z6x ports`……），用完即退出。
        只有极少数子命令带 `--daemon` 常驻。它负责「碰底层」的事：读 /proc、写输入设备、算哈希。网络服务归 z6x-hub（Go）。
    """
    proposal()

    why("为什么这样分工") {
        text("""
            • agy 原方案把 Rust 也做成一个常驻底座（Rust 第 49 篇 z6x_core），和 Go hub 并列两个常驻进程。审核后改成**命令集**：Rust 提案里能在这台机器上做的，几乎都是「跑一次给出结果」的工具（查端口、查温度、算哈希、注入按键），不需要常驻。
            • 需要常驻的只有按键重映射（监听遥控器），用 `z6x keymap --daemon`。
            • hub 需要底层能力时调用 `z6x` 子命令，并解析它的 JSON 输出。
        """)
        facts(
            "编译目标" to "**aarch64-unknown-linux-musl**，静态链接，`opt-level=\"z\"`、`lto=true`、`strip=true`、`panic=\"abort\"`（目标体积 < 2MB）",
            "运行身份" to "从 ADB shell 运行：uid 2000，所在组含 input、uhid（可读写 /dev/input/event*、/dev/uinput）",
            "放在哪" to "`/data/local/tmp/z6x-tools/z6x`；可以像 BusyBox 一样建链接：`z6x-ports` → 等同 `z6x ports`",
            "不能用的" to "/dev/snd（声卡）、/dev/net/tun、HCI 蓝牙套接字（内核没有 hci 设备）、/dev/cec0、/dev/hidg0、/dev/graphics/fb0、cgroup、/proc/kmsg、原始套接字",
        )
    }

    story("命令约定") {
        text("""
            1. 每个子命令都支持 `--json`：输出一个 JSON 对象，给 hub 和工具箱解析；不加时输出给人看的表格。
            2. 退出码：0 成功，1 一般错误，2 参数错误，3 权限不足（例如打不开设备节点）。错误信息写 stderr。
            3. `z6x --help` 列出全部子命令；`z6x <子命令> --help` 列出参数。
            4. 不依赖任何系统库或配置文件；需要设备节点的命令，节点路径可以用参数覆盖。
        """)
    }

    story("第一期子命令") {
        text("""
            1. `z6x sys`（Rust-14、Rust-29）：一次性输出 CPU 使用率（隔 500ms 读两次 /proc/stat）、MemTotal/MemAvailable、所有 thermal_zone 的 type 和温度、/data 剩余空间、运行时长。`--watch 2` 每 2 秒刷新一次。
            2. `z6x ports`（本项目「案例：这个端口是谁开的」）：读 /proc/net/tcp、tcp6、udp、udp6，列出监听端口 → uid → 包名（调用 `pm list packages -U`；/data/system/packages.list 对 shell 不可读，已实测）。
            3. `z6x key`（Rust-18、Rust-41）：注入按键。默认通过 /dev/uinput 创建一个虚拟键盘设备（不依赖遥控器节点号）。/dev/uinput 可写已实测，**但系统是否接受这个虚拟键盘的按键还没验证**，实现时先做这一步，不行就退回直接写遥控器节点（/dev/input/event13 也可写）；`z6x key home`、`z6x key volup --repeat 5 --interval 50ms`；`z6x key --bench` 测单次注入耗时。键名表包括方向、确认、返回、主页、菜单、音量、静音、电源、媒体键。
            4. `z6x keymap --daemon`（Rust-2）：监听遥控器节点（默认按设备名 "XGIMI RC" 查找，不写死 event13），识别长按 / 双击，执行配置里的动作（发按键序列或运行命令）。**不独占设备**（不调用 EVIOCGRAB），原有按键功能不受影响。遥控器重连后节点变化要能自动重新绑定。
            5. `z6x hash`（Rust-4、Go-57）：对目录求重复文件：先按大小分组，再比头尾 64KB，最后全文件 xxh3；输出重复组和可节省空间。**只报告不删除**，删除由用户自己决定。
            6. `z6x watch`（Rust-31）：inotify 监听目录，输出 CLOSE_WRITE / MOVED_TO 事件（每行一个 JSON），可选 `--exec` 对每个事件执行命令。注意 max_user_watches 只有 8192。
            7. `z6x ping`（Rust-34）：对目标连续 ICMP ping（普通身份的 ICMP 套接字，已验证可用），输出最小/平均/最大延迟、抖动、丢包率。
        """)
    }

    story("第二期子命令") {
        text("""
            • `z6x iobench`（Rust-15、Rust-36）：对 U 盘上的测试文件做顺序 / 随机读写测速，测完删除测试文件。FAT32 / exFAT 可能不支持 O_DIRECT，要自动退回普通读写并在结果里注明。
            • `z6x pack`（Rust-11、Rust-38）：zstd 打包 / 解包目录（tar + zstd），用于备份存档、日志。
            • `z6x run`（Rust-19、Rust-1）：极简进程守护——启动一个命令，崩溃后按退避间隔重启，记录退出原因。用来守护 hub。
        """)
    }

    verify("验收") {
        text("""
            • 全体：`file z6x` 显示 ARM aarch64、statically linked；体积 < 2MB；在投影仪上 `z6x --help` 能运行。
            • sys：数值和 `cat /proc/meminfo`、thermal 节点手工读数一致；`--json` 输出能被 `jq` 解析。
            • ports：列出的 5555 对应 shell、2222 对应 org.galexander.sshd、7890 对应 Clash，和「案例：这个端口是谁开的」的结果一致。
            • key：`z6x key home` 后电视回到桌面；`--bench` 报告单次耗时，要明显低于 `input keyevent`（后者每次都要启动 Java 进程）。
            • keymap：配置「长按返回 = 打开 Activity Launcher」，长按生效、短按返回仍然正常；关掉再打开遥控器后依然生效。
            • hash：在准备好的测试目录（含已知重复文件）上报告正确；不删除任何文件。
            • watch：往被监听目录写一个文件，写完时输出一条事件，写的过程中不重复输出。
            • ping：对路由器 ping 结果和系统 `ping` 同一量级。
        """)
    }

    related("review-summary", "spec-hub", "port-owner", "proc-metrics")
}
