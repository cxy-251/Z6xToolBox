package z6x.content.proposals

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val DropbearShell = module("dropbear-shell", "以 shell 身份跑 SSH 服务（Dropbear）") {
    keywords = "dropbear · uid 2000 · scp · /data/local/tmp"
    overview = """
        现在的 SSH 是 SimpleSSHD，身份是 App（uid 10068），碰不到 /data/local/tmp。提案：用 ADB 启动一个静态编译的 Dropbear，让 SSH 以 shell（uid 2000）身份登录，`scp` 可以直接把程序传进 /data/local/tmp。
    """
    proposal()

    why("要解决什么") {
        text("""
            • SimpleSSHD 的 SSH 会话是 App 身份，**连 /data/local/tmp 都列不出来**（实测 Permission denied），没法在 SSH 里部署和运行自己的程序。
            • 想要一个 shell 身份的 SSH：登录后就能直接管理 /data/local/tmp，用 `scp` 传文件，不必每次都走 `adb push`。
            • Dropbear 是专为嵌入式设备写的小型 SSH 服务端，静态编译只有一两 MB，SimpleSSHD 内部用的也是它。
            • 为什么不用 OpenSSH：它依赖更多系统库和配置文件，移植到安卓麻烦得多、体积也大。
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
                `-p 2223`：2222 已被 SimpleSSHD 占用，换一个。`-s` 禁止密码登录，`-g` 禁止 root 密码登录。
                密钥类型取决于拿到的 dropbear 版本：2020.79 以上才支持 ed25519，旧版本要用 ecdsa（SimpleSSHD 就是在这上面栽的跟头）。
                必须**从 ADB 启动**，才是 shell 身份、oom 分 -1000。
            """
        }
    }

    story("可能遇到的问题（原方案）") {
        text("""
            1. 公钥登录被拒：dropbear 要求 .ssh 目录 700、authorized_keys 600，权限太宽会拒绝。
            2. 登录后找不到 shell：安卓的 shell 在 `/system/bin/sh`，不是 /bin/sh，可能要在启动参数或环境里指定。
        """)
    }

    verify("可行性审核（2026-10-01）") {
        facts(
            "需求成立" to "SSH（uid 10068）访问 /data/local/tmp：Permission denied（实测）",
            "能运行" to "/data/local/tmp 可执行、静态程序能跑（BusyBox、Go 服务已验证）",
            "缺什么" to "**dropbear 静态二进制**：设备上和 shared 目录里都没有，需要找一个 arm/arm64 静态编译版",
            "端口" to "2222 被 SimpleSSHD 占用，用 2223 等",
            "重启" to "重启后要重新从 ADB 启动",
            "SimpleSSHD 内存" to "实测两个进程约 109MB + 35MB",
        )
        text("""
            **结论：可行，价值中等。** 现在 `adb push` + `adb shell` 已经能做同样的事；它的好处是只用一个 `ssh`/`scp` 就能管理，适合以后频繁部署 Go 服务时再做。
        """)
    }

    audit {
        claim("SimpleSSHD 应用运行在 Android 沙盒权限下（用户身份为 u0_a68），物理内存占用高达 60MB，且无法自由读写和执行 /data/local/tmp 目录下的开发二进制。", Verdict.Disproved,
            "u0_a68、访问不了 /data/local/tmp **成立**；内存实测约 109MB + 35MB，不是 60MB。")
        claim("部署原生静态 Dropbear 后，SSH 守护进程直接以 shell（UID 2000）身份常驻后台，内存占用降至 2MB ~ 3MB；单文件约 1.8MB。", Verdict.Unverified, "没有部署，无法验证。")
        claim("Dropbear 是专为嵌入式 Linux 设计的超轻量 SSH 服务，支持单文件运行、ED25519/RSA 公钥认证，资源消耗仅为 OpenSSH 的五分之一；OpenSSH 移植体积超 20MB。", Verdict.Unverified,
            "ED25519 要看版本：SimpleSSHD 自带的 dropbear 2019.78 就不支持（踩过坑）。体积和资源对比没有出处。")
        claim("非 Root 用户不可监听标准 TCP 22 端口，故绑定 :2222。", Verdict.Confirmed, "1024 以下不能绑定；但 2222 已被 SimpleSSHD 占用，要换别的端口。")
    }

    related("ssh-key-login", "native-exec", "go-server")
}

val EnvProfile = module("env-profile", "Shell 环境自动加载（env.sh）") {
    keywords = "PATH · alias · mksh · source"
    overview = """
        每次进 `adb shell` 都要手动 `export PATH=/data/local/tmp/bin:${'$'}PATH` 才能用 BusyBox 的命令。提案：写一个 env.sh 放常用设置，进 shell 时自动加载。
    """
    proposal()

    why("要解决什么") {
        text("""
            • 安卓的 shell 是 **mksh**，每次登录只有很短的默认 PATH，不读 ~/.bashrc 之类的配置。
            • `adb shell` 以非登录模式启动，什么配置都不加载。
            • 想要：登录即可直接用 396 个 BusyBox 命令，再加几个常用别名。
        """)
    }

    steps("原方案的命令（未实施）") {
        change("写 env.sh", """
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
            note = "`psmem` 按内存占用从大到小列进程（toybox 的 ps 用 `-k -RSS` 排序，实测可用）。"
        }
        change("在 Deck 上做一个快捷命令", "alias z6x-sh=\"adb shell -t 'source /data/local/tmp/env.sh; sh'\"", Host.Deck) {
            note = "加到 Deck 的 ~/.bashrc。`-t` 分配终端，进去后先加载 env.sh 再给一个交互 shell。"
        }
    }

    story("可能遇到的问题（原方案）") {
        text("""
            1. `adb shell` 不会自动执行脚本：所以要用上面的快捷命令。
            2. 换行符：脚本在 Windows 上编辑过会带 CRLF，mksh 会报语法错误。要用 LF。
        """)
    }

    verify("可行性审核（2026-10-01）") {
        facts(
            "source" to "mksh 支持（`type source` → source is a shell builtin）",
            "ps 排序" to "`ps -A -o PID,RSS,NAME -k -RSS` 实测可用",
            "风险" to "只是一个文本文件，删掉即撤销",
        )
        text("**结论：可行，成本很低，建议做。**")
    }

    audit {
        claim("Android 原生系统的 sh（基于 mksh）在每次建立连接后，仅提供极简的默认环境变量（PATH=/system/bin）。", Verdict.Disproved,
            "mksh **成立**；但默认 PATH 不止 /system/bin，有 /product/bin、/apex/…、/system/bin、/vendor/bin 等九项（见「存储与分区」）。")
        claim("定制安全环境变量：预设 TMPDIR=/data/local/tmp 与 GOGC=50，保护系统稳定。", Verdict.Unverified, "GOGC=50 会让 Go 程序更频繁地回收内存，「保护系统稳定」没有依据，所以方案里去掉了。")
        claim("`adb shell` 命令默认以非登录（non-login）交互模式启动，不加载任何配置文件。", Verdict.Confirmed, "每次进 adb shell 都要手动 export PATH 才能用 BusyBox 命令。")
        claim("脚本必须确保为 Unix (LF) 换行符，若在 Windows 下编辑引入 CRLF 会导致 mksh 报错 syntax error: unexpected word。", Verdict.Unverified, "通用经验，没有专门测试。")
    }

    related("busybox", "native-exec")
}

val StraceDebug = module("strace-debug", "用 strace 排查程序为什么挂") {
    keywords = "strace · ptrace · 系统调用 · ENOENT"
    overview = """
        自己的程序推到投影仪上闪退、报权限错误或卡住，又没有调试环境。提案：放一个静态编译的 strace，看程序和内核之间的每一次交互，哪一步出错一目了然。
    """
    proposal()

    why("原理") {
        text("""
            • 程序读文件、开网络连接、申请内存，都要通过**系统调用**请内核帮忙。
            • strace 用内核的 ptrace 机制在每次系统调用前后拦一下，打印调用名、参数和返回值。失败的调用会显示错误码，比如 `ENOENT`（文件不存在）、`EACCES`（权限不够）。
            • 不需要源码，也不用改程序。
        """)
    }

    steps("原方案的常用命令（未实施）") {
        read("只看文件访问（找不到配置文件时）", "/data/local/tmp/bin/strace -e trace=openat,access /data/local/tmp/my_app", Host.Adb) {
            manual = true
            note = "典型输出：`openat(AT_FDCWD, \"config.yaml\", O_RDONLY) = -1 ENOENT (No such file or directory)`，一眼看出是缺文件。"
        }
        read("只看网络（端口绑不上时）", "/data/local/tmp/bin/strace -e trace=network /data/local/tmp/my_app", Host.Adb) { manual = true }
        read("统计各系统调用耗时（找慢在哪）", "/data/local/tmp/bin/strace -c /data/local/tmp/my_app", Host.Adb) { manual = true }
        read("输出写进文件", "/data/local/tmp/bin/strace -o /data/local/tmp/trace.log /data/local/tmp/my_app", Host.Adb) {
            manual = true
            note = "输出量很大时用。"
        }
    }

    story("可能遇到的问题（原方案）") {
        text("""
            1. `strace -p 进程号` 附加到已经在跑的进程可能被拒绝。直接用 strace 启动程序（父进程追踪子进程）最稳。
            2. shell 身份只能追踪自己启动的进程，追踪不了系统服务。
        """)
    }

    verify("可行性审核（2026-10-01）") {
        facts(
            "缺什么" to "设备上**没有** strace，需要找静态编译版（arm 或 arm64 都行）",
            "yama 限制" to "`/proc/sys/kernel/yama/ptrace_scope` **不存在**，没有 yama",
            "SELinux" to "Permissive，不拦 ptrace",
        )
        text("**结论：可行，排错时非常有用。** 等以后写 Go 服务真遇到问题时再部署即可。")
    }

    audit {
        claim("报错 ptrace: Operation not permitted。原因：Android 内核开启了 yama 安全限制。当尝试通过 strace -p <PID> 挂载一个已经运行的后台进程时容易被拦截。", Verdict.Disproved,
            "这台的内核没有 yama（`/proc/sys/kernel/yama/ptrace_scope` 不存在）。strace 没有部署，附加别的进程会不会被拒没有测试。")
        claim("非 Root 状态下，UID 2000 的 strace 只能挂载并追踪由自身启动的子进程，无法跨用户追踪 Android 系统服务（system_server）。", Verdict.Unverified,
            "没有部署 strace。同样的权限边界在 `kill -0` 实验里看到过：shell 碰不到其他用户的进程。")
        claim("技术栈：musl-gcc 静态交叉编译的 ARM64 strace 二进制（单文件约 1.5MB）。", Verdict.Unverified, "设备上和 shared 目录里都没有 strace。")
    }

    related("native-exec", "go-server")
}

val PacketCapture = module("packet-capture", "在投影仪上抓包（tcpdump）") {
    keywords = "tcpdump · AF_PACKET · pcap · Wireshark"
    overview = """
        调试网络服务时想看数据包到底有没有到达投影仪。提案是在投影仪上用 tcpdump 抓包。**实测不可行**：shell 身份没有抓包权限。
    """
    proposal()

    why("原方案的思路") {
        text("""
            • tcpdump 通过 AF_PACKET 原始套接字和 BPF 过滤器，在网卡收到数据时复制一份，能看到每个 TCP 握手、HTTP 请求头、DNS 应答。
            • 可以导出 .pcap 文件，用电脑上的 Wireshark 图形化分析。
            • 用来排查：请求到底有没有到投影仪、投屏协议（SSDP 组播）的交互、HTTP 头。
        """)
    }

    steps("原方案的命令（在这台机器上跑不通）") {
        read("看某个端口的通信", "tcpdump -i wlan0 -nn -s0 -A 'tcp port 8088'", Host.Adb) { manual = true }
        read("抓投屏的 SSDP 发现报文", "tcpdump -i wlan0 -nn 'udp port 1900'", Host.Adb) { manual = true }
        read("抓 100 个包存文件", "tcpdump -i wlan0 -c 100 -w /data/local/tmp/traffic.pcap 'port 8088'", Host.Adb) {
            manual = true
            note = "`-c` 限制包数，避免写满存储；`-C 兆字节` 可以按大小滚动写入。"
        }
    }

    verify("可行性审核（2026-10-01）") {
        read("系统自带的 tcpdump 能不能抓", "timeout 5 tcpdump -i wlan0 -c 1 -nn", Host.Adb) {
            expectsError = true
            captured("2026-10-01", """
                tcpdump: wlan0: You don't have permission to capture on that device
                (socket: Operation not permitted)
            """)
            note = "系统里其实自带 `/system/bin/tcpdump`，不用另外部署。但抓包需要 CAP_NET_RAW 能力，shell 身份没有（CapEff 全为 0），和 SELinux 无关。"
        }
        text("""
            **结论：不可行（没有 root）。** 替代办法：
            • 在 **Deck 上**抓包：Deck 是请求的发送方，`sudo tcpdump` 能看到发出和收到的包。
            • 在服务**自己的代码里**记日志：收到请求就打印。
            • 用 `nc` 或 `curl -v` 从 Deck 测试连通性。
        """)
    }

    audit {
        claim("报错 socket: Operation not permitted。原因：Android 内核严格限制普通 UID 创建 AF_PACKET 原始套接字。若内核 SELinux 策略阻止了 shell 域使用 raw socket，可通过测试端口监听（nc -l）结合外部 PC 发送端单向抓包作为补充验证手段。", Verdict.Confirmed,
            "报错原文一致。但原因不是 SELinux（这台是 Permissive），是 shell 没有 CAP_NET_RAW 能力。")
        claim("技术栈：musl 静态链接编译的 ARM64 tcpdump + libpcap（单文件约 2.2MB）。", Verdict.Disproved, "不需要自己部署：系统自带 `/system/bin/tcpdump`。只是没权限用。")
    }

    related("port-owner", "native-exec")
}

val OomWatchdog = module("oom-watchdog", "服务保活：看门狗与 oom 分") {
    keywords = "oom_score_adj · LMK · watchdog · 重启"
    overview = """
        担心自己的服务在看 4K 视频、内存吃紧时被系统杀掉，提案写一个看门狗脚本定时检查、挂了就重启，并调低 oom 分。**实测发现被杀的风险本来就不存在**，真正要解决的是重启后自动启动。
    """
    proposal()

    why("原方案的思路") {
        text("""
            • 安卓内存不够时，lmkd 按 `oom_score_adj`（-1000 到 1000）从高到低杀进程，前台 App 是 0，后台缓存 App 是 900 以上，-1000 永不被杀。
            • 原方案：shell 脚本死循环，每 10 秒用 `pidof` 检查服务，没了就重启，并把新进程的 oom 分写成 -500。
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
            note = "已去掉原方案里写 oom 分那一步，原因见下面的审核。"
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
            note = "用一个临时的 `sleep` 进程做实验，测完就结束它，不影响别的进程。能调高，也能调回 -1000（下限就是继承来的值）。"
        }
        facts(
            "被内存回收杀掉" to "从 ADB 启动的进程继承 adbd 的 **-1000**，永不被杀。不需要调 oom 分",
            "程序自己崩溃" to "看门狗有用：崩溃后 10 秒内重启",
            "看门狗自己" to "同样从 ADB 启动，也是 -1000，不会被内存回收杀掉",
            "重启投影仪" to "所有进程都没了，看门狗自己也没了。没有 root 就不能开机自启，**这才是真正的问题**",
        )
        text("""
            **结论：看门狗可选（只管崩溃重启）；oom 调整不需要。**
            重启后的恢复：2026-10-01 实测重启后 **adbd 会自动运行**，所以只要在 Deck 上重新用 ADB 启动服务即可（可以写成一个脚本一键恢复）。SimpleSSHD 要在电视上手动点 Start。
        """)
    }

    audit {
        claim("非 Root shell 虽不能设为 -1000，但可将其调整为比普通后台 App 更低的保护级别。", Verdict.Disproved,
            "ADB 启动的进程**本来就是 -1000**（继承 adbd），调高后也能再调回 -1000。")
        claim("写入 /proc/<PID>/oom_score_adj 报错 Permission denied。原因：Android 内核仅允许进程自身调整自身的 score 分值，或由其父进程降级。", Verdict.Disproved,
            "shell 给自己启动的 sleep 进程改 oom 分成功了（调高、调回都行）。")
        claim("看门狗使用纯 Shell 运行，物理内存常驻仅 800KB，处于系统查杀优先级的最底层，只要内存不低于 50MB 绝不会被触发查杀。", Verdict.Unverified,
            "看门狗没有部署。它从 ADB 启动的话是 -1000，确实不会被杀；800KB、50MB 这两个数字没有依据。")
        claim("普通 Android 前台 App 分值为 0，后台缓存 App 为 900+；-1000 代表完全免疫 OOM 查杀。", Verdict.Confirmed, "`dumpsys meminfo --oom` 的分组和 SSH（App 身份）里看到的 0 都符合。")
    }

    related("go-server", "force-adb", "native-exec")
}
