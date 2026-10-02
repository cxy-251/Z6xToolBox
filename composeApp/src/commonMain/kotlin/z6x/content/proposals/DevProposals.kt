package z6x.content.proposals

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val DropbearShell = module("dropbear-shell", "以 shell 身份运行 SSH 服务（已改由 hub 内置实现）") {
    keywords = "dropbear · uid 2000 · x/crypto/ssh · 8022 · /data/local/tmp"
    overview = """
        原先的 SSH 由 SimpleSSHD 提供，身份为普通应用（uid 10068），无法访问 /data/local/tmp。原提案：通过 ADB 启动一个静态编译的 Dropbear，使 SSH 以 shell（uid 2000）身份登录。
        **2026-10-02 已实现，但改用其他方式**：在 z6x-hub 中内置 SSH 服务（Go 官方的 golang.org/x/crypto/ssh），随 hub 一起部署，端口 8022。原因与实测结果见下文「实际实现」。
    """
    verified("2026-10-02")

    why("要解决的问题") {
        text("""
            • SimpleSSHD 的 SSH 会话为应用身份，**甚至无法列出 /data/local/tmp**（实测 Permission denied），因此无法在 SSH 中部署和运行自己的程序。
            • 目标是获得 shell 身份的 SSH：登录后可直接管理 /data/local/tmp，并用 `scp` 传输文件，无需每次使用 `adb push`。
            • Dropbear 是专为嵌入式设备编写的小型 SSH 服务端，静态编译后只有一两 MB，SimpleSSHD 内部使用的也是它。
            • 不选用 OpenSSH 的原因：它依赖更多系统库和配置文件，移植到安卓更加困难，体积也更大。
        """)
    }

    steps("原方案的命令（未实施）") {
        change("生成主机密钥、登记公钥、启动", """
            mkdir -p /data/local/tmp/.ssh && chmod 700 /data/local/tmp/.ssh
            /data/local/tmp/bin/dropbearkey -t ecdsa -f /data/local/tmp/.ssh/host_key_ecdsa
            cat /data/local/tmp/z6x_ecdsa.pub >> /data/local/tmp/.ssh/authorized_keys
            chmod 600 /data/local/tmp/.ssh/authorized_keys
            nohup /data/local/tmp/bin/dropbear -r /data/local/tmp/.ssh/host_key_ecdsa -p 2223 -s -g > /data/local/tmp/dropbear.log 2>&1 &
        """, Host.Adb) {
            note = """
                `-p 2223`：2222 已被 SimpleSSHD 占用，因此改用其他端口。`-s` 禁止密码登录，`-g` 禁止 root 密码登录。
                密钥类型取决于所用 dropbear 的版本：2020.79 及以上才支持 ed25519，旧版本需使用 ecdsa（SimpleSSHD 公钥登录时正是在此出现问题）。
                必须**从 ADB 启动**，才能获得 shell 身份和 -1000 的 oom 分值。
            """
        }
    }

    story("可能遇到的问题（原方案）") {
        text("""
            1. 公钥登录被拒绝：dropbear 要求 .ssh 目录权限为 700、authorized_keys 为 600，权限过宽时会拒绝登录。
            2. 登录后找不到 shell：安卓的 shell 位于 `/system/bin/sh` 而非 /bin/sh，可能需要在启动参数或环境变量中指定。
        """)
    }

    verify("可行性审核（2026-10-01）") {
        facts(
            "需求成立" to "SSH（uid 10068）访问 /data/local/tmp：Permission denied（实测）",
            "能运行" to "/data/local/tmp 可执行、静态程序能跑（BusyBox、Go 服务已验证）",
            "缺少的条件" to "**dropbear 静态二进制**：设备和 shared 目录中均没有，需要另行获取 arm/arm64 静态编译版",
            "端口" to "2222 已被 SimpleSSHD 占用，可使用 2223 等",
            "重启" to "重启后需要重新从 ADB 启动",
            "SimpleSSHD 内存" to "实测两个进程约 109MB + 35MB",
        )
        text("""
            **结论：可行，价值中等。** 目前 `adb push` 与 `adb shell` 已能完成同样的工作；其优势在于只需 `ssh`/`scp` 即可管理，适合今后频繁部署 Go 服务时再实施。
        """)
    }

    audit {
        claim("SimpleSSHD 应用运行在 Android 沙盒权限下（用户身份为 u0_a68），物理内存占用高达 60MB，且无法自由读写和执行 /data/local/tmp 目录下的开发二进制。", Verdict.Disproved,
            "u0_a68、无法访问 /data/local/tmp **成立**；内存实测约为 109MB 加 35MB，而非 60MB。")
        claim("部署原生静态 Dropbear 后，SSH 守护进程直接以 shell（UID 2000）身份常驻后台，内存占用降至 2MB ~ 3MB；单文件约 1.8MB。", Verdict.Unverified, "未部署，无法验证。")
        claim("Dropbear 是专为嵌入式 Linux 设计的超轻量 SSH 服务，支持单文件运行、ED25519/RSA 公钥认证，资源消耗仅为 OpenSSH 的五分之一；OpenSSH 移植体积超 20MB。", Verdict.Unverified,
            "是否支持 ED25519 取决于版本：SimpleSSHD 自带的 dropbear 2019.78 即不支持（实际遇到过）。体积和资源对比没有出处。")
        claim("非 Root 用户不可监听标准 TCP 22 端口，故绑定 :2222。", Verdict.Confirmed, "1024 以下的端口无法绑定；但 2222 已被 SimpleSSHD 占用，需改用其他端口。")
    }


    story("实际实现：hub 内置 SSH（2026-10-02）") {
        text("""
            • **为什么不用 Dropbear**：Dropbear 是 C 程序，需要为 aarch64 交叉编译 C 代码，而 Deck 上只有 Rust 自带的链接器，没有 C 交叉编译器（z6x-tools 的 pack 子命令因同样的原因放弃了 zstd）。
            • **改为在 hub 中实现**：Go 官方有成熟的 SSH 库，网页终端模块已有伪终端代码可以复用；投影仪上的 hub 本来就以 shell 身份运行，内置的 SSH 自然也是 shell 身份。代码位于 `hub/internal/modules/sshd/`，伪终端代码移到了公共的 `hub/internal/pty/`。
            • **安全**：只接受配置中列出的公钥，不支持密码；端口由网络守卫管理，只在局域网 IPv4 地址上监听；主机密钥（ed25519）首次启动时生成，保存在 hub 的数据目录中。
            • **支持**：交互式终端（窗口大小随客户端调整）与执行单条命令（返回真实的退出码）。**不支持** sftp、scp、端口转发：文件传输用 hub 的文件管理与 WebDAV，或 `adb push`。
        """)
        change("登录", "ssh -i ~/.ssh/z6x_ecdsa -p 8022 192.168.0.109", Host.Deck)
    }

    verify("实测结果（2026-10-02）") {
        facts(
            "身份" to "✓ uid 2000（shell），工作目录 /data/local/tmp；可读取 12 个输入设备，可执行 z6x、dumpsys 等",
            "认证" to "✓ 未授权的公钥、密码登录均被拒绝（Permission denied (publickey)）",
            "伪终端" to "✓ 交互式会话分配到 /dev/pts/0",
            "单元测试" to "✓ 在 Deck 上用进程内的 SSH 客户端测试：拒绝未授权公钥、命令输出与退出码正确、伪终端会话有 tty",
            "测试中的问题" to "工作目录写死为 /data/local/tmp，在 Deck 上运行单元测试时该目录不存在，进程启动失败；改为目录存在时使用，否则使用根目录",
        )
    }

    consequences("之后") {
        text("""
            SimpleSSHD 不再需要，可以卸载；遥控器芒果键目前设为打开 SimpleSSHD，卸载前应改成其他功能。与 hub 一样，投影仪重启后需要由 Deck 重新部署启动。
        """)
    }

    related("ssh-key-login", "native-exec", "go-server")
}

val EnvProfile = module("env-profile", "Shell 环境自动加载（env.sh）") {
    keywords = "PATH · alias · mksh · source"
    overview = """
        每次进入 `adb shell` 都需要手动执行 `export PATH=/data/local/tmp/bin:${'$'}PATH` 才能使用 BusyBox 的命令。提案：编写 env.sh 存放常用设置，进入 shell 时自动加载。
    """
    proposal()

    why("要解决的问题") {
        text("""
            • 安卓的 shell 是 **mksh**，每次登录只有默认 PATH，不读取 ~/.bashrc 之类的配置。
            • `adb shell` 以非登录模式启动，不加载任何配置。
            • 目标：登录后即可直接使用 396 个 BusyBox 命令，并提供若干常用别名。
        """)
    }

    steps("原方案的命令（未实施）") {
        change("编写 env.sh", """
            cat > /data/local/tmp/env.sh <<'EOF'
            export PATH=/data/local/tmp/bin:/data/local/tmp:${'$'}PATH
            export HOME=/data/local/tmp
            export TMPDIR=/data/local/tmp
            export PS1='[z6x:\w]\${'$'} '
            alias ll='ls -la'
            alias ports='netstat -tln'
            alias meminfo='grep -E "MemTotal|MemFree|MemAvailable" /proc/meminfo'
            alias psmem='ps -A -o PID,RSS,NAME -k -RSS | head -15'
            EOF
        """, Host.Adb) {
            note = "`psmem` 按内存占用从大到小列出进程（toybox 的 ps 用 `-k -RSS` 排序，实测可用）。"
        }
        change("在 Deck 上设置快捷命令", "alias z6x-sh=\"adb shell -t 'source /data/local/tmp/env.sh; sh'\"", Host.Deck) {
            note = "添加到 Deck 的 ~/.bashrc 中。`-t` 表示分配终端，进入后先加载 env.sh，再启动交互式 shell。"
        }
    }

    story("可能遇到的问题（原方案）") {
        text("""
            1. `adb shell` 不会自动执行脚本，因此需要使用上述快捷命令。
            2. 换行符：在 Windows 上编辑过的脚本会带有 CRLF，mksh 会报语法错误，必须使用 LF。
        """)
    }

    verify("可行性审核（2026-10-01）") {
        facts(
            "source" to "mksh 支持（`type source` → source is a shell builtin）",
            "ps 排序" to "`ps -A -o PID,RSS,NAME -k -RSS` 实测可用",
            "风险" to "只是一个文本文件，删除即可撤销",
        )
        text("**结论：可行，成本很低，建议实施。**")
    }

    audit {
        claim("Android 原生系统的 sh（基于 mksh）在每次建立连接后，仅提供极简的默认环境变量（PATH=/system/bin）。", Verdict.Disproved,
            "mksh **成立**；但默认 PATH 不止 /system/bin，而是包括 /product/bin、/apex/…、/system/bin、/vendor/bin 等九项（见「存储与分区」）。")
        claim("定制安全环境变量：预设 TMPDIR=/data/local/tmp 与 GOGC=50，保护系统稳定。", Verdict.Unverified, "GOGC=50 会使 Go 程序更频繁地回收内存，「保护系统稳定」缺乏依据，因此方案中已删除。")
        claim("`adb shell` 命令默认以非登录（non-login）交互模式启动，不加载任何配置文件。", Verdict.Confirmed, "每次进入 adb shell 都需要手动 export PATH 才能使用 BusyBox 命令。")
        claim("脚本必须确保为 Unix (LF) 换行符，若在 Windows 下编辑引入 CRLF 会导致 mksh 报错 syntax error: unexpected word。", Verdict.Unverified, "属于通用经验，未专门测试。")
    }

    related("busybox", "native-exec")
}

val StraceDebug = module("strace-debug", "使用 strace 排查程序故障") {
    keywords = "strace · ptrace · 系统调用 · ENOENT"
    overview = """
        自行编译的程序在投影仪上闪退、报权限错误或卡住时，缺少调试环境。提案：部署一个静态编译的 strace，观察程序与内核之间的每一次交互，从而快速定位出错的步骤。
    """
    proposal()

    why("原理") {
        text("""
            • 程序读取文件、建立网络连接、申请内存，都需要通过**系统调用**请求内核完成。
            • strace 利用内核的 ptrace 机制在每次系统调用前后进行拦截，输出调用名、参数和返回值。失败的调用会显示错误码，例如 `ENOENT`（文件不存在）、`EACCES`（权限不足）。
            • 无需源码，也无需修改程序。
        """)
    }

    steps("原方案的常用命令（未实施）") {
        read("仅跟踪文件访问（找不到配置文件时）", "/data/local/tmp/bin/strace -e trace=openat,access /data/local/tmp/my_app", Host.Adb) {
            manual = true
            note = "典型输出：`openat(AT_FDCWD, \"config.yaml\", O_RDONLY) = -1 ENOENT (No such file or directory)`，可直接看出缺少文件。"
        }
        read("仅跟踪网络（端口无法绑定时）", "/data/local/tmp/bin/strace -e trace=network /data/local/tmp/my_app", Host.Adb) { manual = true }
        read("统计各系统调用耗时（定位性能瓶颈）", "/data/local/tmp/bin/strace -c /data/local/tmp/my_app", Host.Adb) { manual = true }
        read("将输出写入文件", "/data/local/tmp/bin/strace -o /data/local/tmp/trace.log /data/local/tmp/my_app", Host.Adb) {
            manual = true
            note = "适用于输出量很大的情况。"
        }
    }

    story("可能遇到的问题（原方案）") {
        text("""
            1. 用 `strace -p 进程号` 附加到正在运行的进程可能被拒绝。直接用 strace 启动程序（由父进程追踪子进程）最为可靠。
            2. shell 身份只能追踪自己启动的进程，无法追踪系统服务。
        """)
    }

    verify("可行性审核（2026-10-01）") {
        facts(
            "缺少的条件" to "设备上**没有** strace，需要获取静态编译版（arm 或 arm64 均可）",
            "yama 限制" to "`/proc/sys/kernel/yama/ptrace_scope` **不存在**，即没有 yama",
            "SELinux" to "Permissive 模式，不拦截 ptrace",
        )
        text("**结论：可行，排错时非常有用。** 待今后开发 Go 服务确实遇到问题时再部署即可。")
    }

    audit {
        claim("报错 ptrace: Operation not permitted。原因：Android 内核开启了 yama 安全限制。当尝试通过 strace -p <PID> 挂载一个已经运行的后台进程时容易被拦截。", Verdict.Disproved,
            "本机内核没有 yama（`/proc/sys/kernel/yama/ptrace_scope` 不存在）。strace 尚未部署，附加到其他进程是否会被拒绝未测试。")
        claim("非 Root 状态下，UID 2000 的 strace 只能挂载并追踪由自身启动的子进程，无法跨用户追踪 Android 系统服务（system_server）。", Verdict.Unverified,
            "strace 尚未部署。在 `kill -0` 实验中观察到同样的权限边界：shell 无法操作其他用户的进程。")
        claim("技术栈：musl-gcc 静态交叉编译的 ARM64 strace 二进制（单文件约 1.5MB）。", Verdict.Unverified, "设备上和 shared 目录中都没有 strace。")
    }

    related("native-exec", "go-server")
}

val PacketCapture = module("packet-capture", "在投影仪上抓包（tcpdump）") {
    keywords = "tcpdump · AF_PACKET · pcap · Wireshark"
    overview = """
        调试网络服务时，需要确认数据包是否到达投影仪。提案是在投影仪上用 tcpdump 抓包。**实测不可行**：shell 身份没有抓包权限。
    """
    proposal()

    why("原方案的思路") {
        text("""
            • tcpdump 通过 AF_PACKET 原始套接字和 BPF 过滤器，在网卡收到数据时复制一份，可以看到每次 TCP 握手、HTTP 请求头和 DNS 应答。
            • 可导出 .pcap 文件，在电脑上用 Wireshark 进行图形化分析。
            • 用于排查：请求是否到达投影仪、投屏协议（SSDP 组播）的交互过程以及 HTTP 头。
        """)
    }

    steps("原方案的命令（在本机上无法运行）") {
        read("看某个端口的通信", "tcpdump -i wlan0 -nn -s0 -A 'tcp port 8088'", Host.Adb) { manual = true }
        read("抓投屏的 SSDP 发现报文", "tcpdump -i wlan0 -nn 'udp port 1900'", Host.Adb) { manual = true }
        read("抓 100 个包存文件", "tcpdump -i wlan0 -c 100 -w /data/local/tmp/traffic.pcap 'port 8088'", Host.Adb) {
            manual = true
            note = "`-c` 限制包数，避免写满存储；`-C 兆字节` 可按大小滚动写入。"
        }
    }

    verify("可行性审核（2026-10-01）") {
        read("系统自带的 tcpdump 能不能抓", "timeout 5 tcpdump -i wlan0 -c 1 -nn", Host.Adb) {
            expectsError = true
            captured("2026-10-01", """
                tcpdump: wlan0: You don't have permission to capture on that device
                (socket: Operation not permitted)
            """)
            note = "系统其实自带 `/system/bin/tcpdump`，无需另行部署。但抓包需要 CAP_NET_RAW 能力，而 shell 身份没有（CapEff 全为 0），与 SELinux 无关。"
        }
        text("""
            **结论：不可行（没有 root）。** 替代方法：
            • 在 **Deck 上**抓包：Deck 是请求的发送方，`sudo tcpdump` 可以看到发出和收到的包。
            • 在服务**自身的代码中**记录日志：收到请求即输出。
            • 用 `nc` 或 `curl -v` 从 Deck 测试连通性。
        """)
    }

    audit {
        claim("报错 socket: Operation not permitted。原因：Android 内核严格限制普通 UID 创建 AF_PACKET 原始套接字。若内核 SELinux 策略阻止了 shell 域使用 raw socket，可通过测试端口监听（nc -l）结合外部 PC 发送端单向抓包作为补充验证手段。", Verdict.Confirmed,
            "报错原文一致。但原因不是 SELinux（本机为 Permissive），是 shell 没有 CAP_NET_RAW 能力。")
        claim("技术栈：musl 静态链接编译的 ARM64 tcpdump + libpcap（单文件约 2.2MB）。", Verdict.Disproved, "不需要自己部署：系统自带 `/system/bin/tcpdump`。只是没权限用。")
    }

    related("port-owner", "native-exec")
}

val OomWatchdog = module("oom-watchdog", "服务保活：看门狗与 OOM 优先级") {
    keywords = "oom_score_adj · LMK · watchdog · 重启"
    overview = """
        担心自行部署的服务在播放 4K 视频、内存紧张时被系统结束，提案编写看门狗脚本定时检查、异常退出时重启，并调低 oom 分值。**实测发现被结束的风险并不存在**，真正需要解决的是重启后的自动启动。
    """
    proposal()

    why("原方案的思路") {
        text("""
            • 安卓内存不足时，lmkd 按 `oom_score_adj`（-1000 到 1000）从高到低结束进程：前台应用为 0，后台缓存应用为 900 以上，-1000 永不被结束。
            • 原方案：shell 脚本无限循环，每 10 秒用 `pidof` 检查服务，服务不在时重新启动，并将新进程的 oom 分值写为 -500。
        """)
    }

    steps("原方案的看门狗脚本（未实施）") {
        change("watchdog.sh", """
            cat > /data/local/tmp/watchdog.sh <<'EOF'
            #!/system/bin/sh
            BIN=/data/local/tmp/z6x_go_server
            LOG=/data/local/tmp/watchdog.log
            while true; do
              if ! pidof z6x_go_server > /dev/null; then
                echo "${'$'}(date '+%F %T') 服务不在，重新启动" >> ${'$'}LOG
                nohup ${'$'}BIN > /data/local/tmp/go_server.log 2>&1 &
              fi
              sleep 10
            done
            EOF
            chmod 755 /data/local/tmp/watchdog.sh
            nohup /data/local/tmp/watchdog.sh > /dev/null 2>&1 &
        """, Host.Adb) {
            note = "已删除原方案中写入 oom 分值的步骤，原因见下文审核。"
        }
    }

    verify("可行性审核（2026-10-01）") {
        read("Go 服务现在的 oom 分", "cat /proc/\$(pidof z6x_go_server)/oom_score_adj", Host.Adb) {
            captured("2026-10-01", "-1000")
        }
        change("shell 能不能改子进程的 oom 分（用临时的 sleep 进程测试）", "sleep 30 & P=\$!; echo before=\$(cat /proc/\$P/oom_score_adj); echo 0 > /proc/\$P/oom_score_adj && echo raised=\$(cat /proc/\$P/oom_score_adj); echo -1000 > /proc/\$P/oom_score_adj && echo lowered=\$(cat /proc/\$P/oom_score_adj); kill \$P", Host.Adb) {
            captured("2026-10-01", """
                before=-1000
                raised=0
                lowered=-1000
            """)
            note = "用一个临时的 `sleep` 进程做实验，测试完毕即结束，不影响其他进程。分值可以调高，也可以调回 -1000（下限即继承而来的值）。"
        }
        facts(
            "因内存回收被结束" to "从 ADB 启动的进程继承 adbd 的 **-1000**，永不被结束，无需调整 oom 分值",
            "程序自身崩溃" to "看门狗有效：崩溃后 10 秒内重启",
            "看门狗自身" to "同样从 ADB 启动，分值也是 -1000，不会因内存回收被结束",
            "重启投影仪" to "所有进程都会消失，看门狗本身也不例外。没有 root 无法开机自启，**这才是真正的问题**",
        )
        text("""
            **结论：看门狗可选（仅负责崩溃后重启）；无需调整 oom 分值。**
            重启后的恢复：2026-10-01 实测重启后 **adbd 会自动运行**，因此只需在 Deck 上通过 ADB 重新启动服务即可（可编写脚本一键恢复）。SimpleSSHD 需要在电视上手动点击 Start。
        """)
    }

    audit {
        claim("非 Root shell 虽不能设为 -1000，但可将其调整为比普通后台 App 更低的保护级别。", Verdict.Disproved,
            "从 ADB 启动的进程**本来就是 -1000**（继承自 adbd），调高后也可以再调回 -1000。")
        claim("写入 /proc/<PID>/oom_score_adj 报错 Permission denied。原因：Android 内核仅允许进程自身调整自身的 score 分值，或由其父进程降级。", Verdict.Disproved,
            "shell 修改自己启动的 sleep 进程的 oom 分值成功（调高和调回均可）。")
        claim("看门狗使用纯 Shell 运行，物理内存常驻仅 800KB，处于系统查杀优先级的最底层，只要内存不低于 50MB 绝不会被触发查杀。", Verdict.Unverified,
            "看门狗尚未部署。若从 ADB 启动，其分值为 -1000，确实不会被结束；800KB、50MB 这两个数字缺乏依据。")
        claim("普通 Android 前台 App 分值为 0，后台缓存 App 为 900+；-1000 代表完全免疫 OOM 查杀。", Verdict.Confirmed, "`dumpsys meminfo --oom` 的分组以及 SSH（应用身份）中看到的 0 均与之相符。")
    }

    related("go-server", "force-adb", "native-exec")
}
