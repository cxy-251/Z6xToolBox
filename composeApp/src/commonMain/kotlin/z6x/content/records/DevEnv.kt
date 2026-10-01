package z6x.content.records

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val NativeExec = module("native-exec", "不 root 也能跑自己的程序") {
    keywords = "/data/local/tmp · noexec · 静态编译 · 32/64 位"
    overview = """
        有了 ADB 的 shell 身份，就能把自己编译的 Linux 程序放到 `/data/local/tmp` 里直接运行，不需要 root、不需要装 App。后面的 BusyBox 和 Go 服务都是这样跑起来的。
    """
    verified("2026-10-01")

    why {
        text("""
            • 安卓的内核就是 Linux，能直接执行 ELF 格式的程序，前提是文件所在的分区允许执行（没有 `noexec`）、你对文件有执行权限。
            • `/data/local/tmp` 是专门留给 shell 身份的目录，ADB 可以随意读写执行。
            • 程序要**静态编译**：安卓的 C 库（bionic）和普通 Linux 的 glibc 不一样，动态链接的 Linux 程序在安卓上找不到库。静态编译把依赖全部打包进一个文件。
        """)
    }

    steps {
        read("/data 的挂载参数", "mount | grep ' /data '", Host.Adb) {
            captured("2026-10-01", "/dev/block/mmcblk0p53 on /data type ext4 (rw,seclabel,nosuid,nodev,noatime,journal_checksum,noauto_da_alloc,resgid=1065,data=ordered)")
            note = """
                括号里是挂载选项。有 `nosuid`（setuid 程序不提权）、`nodev`（不认设备文件），**没有 `noexec`**，所以可以执行程序。
            """
        }
        read("目录归属", "ls -ld /data/local/tmp", Host.Adb) {
            varies = true
            captured("2026-10-01", "drwxrwx--x 3 shell shell 4096 2026-10-01 12:31 /data/local/tmp")
            note = "属于 shell 用户和组，shell 有读写执行（rwx）权限。"
        }
        read("系统用户空间是 32 位", "getprop ro.product.cpu.abi", Host.Adb) {
            captured("2026-10-01", "armeabi-v7a")
            note = "但内核是 64 位，**静态编译的 64 位程序也能跑**（Go 服务就是 arm64 的）。32 位只限制 App 的原生库，因为 App 要和系统的 32 位库链接。"
        }
    }

    story("另外两种思路（没有采用）") {
        text("""
            • **Termux**：一个提供完整 Linux 软件包的 App，能 apt 安装工具。但它以 App 身份运行（权限同 SSH 那样受限），占用空间大。
            • **PRoot 跑 Debian 等发行版**：在 App 里模拟一个完整发行版，系统调用要经过一层转换，性能和内存开销都大，不适合 3.5G 内存、还要放视频的投影仪。
            • **自己编译静态程序放进 /data/local/tmp**（采用）：零依赖、占用最小，以 shell 身份运行，权限也比 App 高。
        """)
    }

    consequences {
        text("""
            • 不能绑定 1024 以下的端口（实测 `/proc/sys/net/ipv4/ip_unprivileged_port_start` 是 1024），服务要用 8088 这类高端口。
            • 恢复出厂设置会清空 /data/local/tmp。
            • **重启后进程都没了**，要重新启动。没有 root 就没法注册开机自启。
        """)
    }

    audit {
        read("旧版：查看 /data 分区挂载参数与 tmp 目录权限", "mount | grep ' /data '\nls -ld /data/local/tmp", Host.Adb) {
            verdict = Verdict.Disproved
            captured("（旧记录）", """
                /dev/block/platform/bootdevice/by-name/userdata on /data type ext4 (rw,seclabel,nosuid,nodev,noatime,discard,noauto_da_alloc,data=ordered)
                drwxrwx--x 4 shell shell 4096 ... /data/local/tmp
            """)
            note = "结论（没有 noexec、shell 有 rwx）**成立**，但输出是编的：实机设备名是 `/dev/block/mmcblk0p53`，挂载参数里没有 discard，有 journal_checksum 和 resgid=1065。"
        }
        claim("SELinux 规则：ADB shell 运行于 u:r:shell:s0 域，允许访问 /proc、/sys、网络 socket 绑定与连接，但禁止修改系统底层属性（如 setprop 核心配置）或读取应用私有加密数据。", Verdict.Disproved,
            "shell 域 **成立**；但这台机器是 Permissive，SELinux 什么都不拦。shell 读不了应用私有数据是因为文件权限（DAC），不是 SELinux。")
        claim("端口监听边界：Linux 标准内核限制非 root 用户不可绑定 < 1024 特权端口。开发的原生服务需绑定 1024 以上非特权端口（如 8080, 8088, 9090）。", Verdict.Confirmed,
            "`ip_unprivileged_port_start` = 1024。")
        claim("轻量原生架构（采用方案）：物理内存占用小于 10MB。Termux 套件方案：约占 200MB 存储，但部分包缺乏电视版 ARM64 支持。Debian / PRoot 容器方案：系统调用开销大（I/O 速度下降 30%-60%），常驻内存 150MB-300MB。", Verdict.Unverified,
            "Go 服务实测 2~4MB **成立**。Termux、PRoot 的数字没有出处，也没有在这台机器上装过。")
    }

    related("busybox", "go-server", "force-adb")
}

val Busybox = module("busybox", "BusyBox：补齐 396 个 Linux 命令") {
    keywords = "busybox --install · toybox · PATH"
    overview = """
        安卓自带的 toybox 命令很精简，缺 vi、wget 等，有的命令功能也不全（比如 awk 没有 strtonum）。放一个静态编译的 BusyBox 进去，一次补齐 396 个命令。
    """
    verified("2026-10-01")

    steps("部署") {
        change("推送到投影仪并加执行权限", """
            adb push busybox /data/local/tmp/busybox
            adb shell chmod 755 /data/local/tmp/busybox
        """, Host.Deck) {
            note = "busybox 文件在 shared 目录。在 Deck 上执行。"
        }
        change("给每个命令建一个链接", "mkdir -p /data/local/tmp/bin && /data/local/tmp/busybox --install -s /data/local/tmp/bin", Host.Adb) {
            note = "BusyBox 是一个文件包含几百个命令，按「被叫成什么名字」决定扮演哪个命令。`--install -s` 为每个命令建一个指向它的符号链接。"
        }
    }

    verify {
        read("版本", "/data/local/tmp/busybox | head -1", Host.Adb) {
            captured("2026-10-01", "BusyBox v1.31.0 (2019-06-10 15:54:51 CEST) multi-call binary.")
        }
        read("文件类型（在 Deck 上看）", "file busybox", Host.Deck) {
            manual = true
            captured("2026-10-01", "busybox:       ELF 32-bit LSB executable, ARM, EABI5 version 1 (SYSV), statically linked, stripped")
            note = "在 shared 目录执行。**32 位** ARM、静态链接。32 位程序在 64 位内核上照样能跑。"
        }
        read("链接数量", "ls /data/local/tmp/bin | wc -l", Host.Adb) {
            captured("2026-10-01", "396")
        }
        read("加进 PATH 后试用", "export PATH=/data/local/tmp/bin:\$PATH; which wget vi nc awk tar", Host.Adb) {
            captured("2026-10-01", """
                /data/local/tmp/bin/wget
                /data/local/tmp/bin/vi
                /data/local/tmp/bin/nc
                /data/local/tmp/bin/awk
                /data/local/tmp/bin/tar
            """)
            note = "`export` 只对当前这次 shell 有效，退出就没了。每次自动加载的办法见提案「Shell 环境自动加载」。"
        }
    }

    story("补了哪些命令") {
        facts(
            "网络" to "nc、wget、ping、traceroute、nslookup、netstat、arp、route",
            "文本" to "awk、sed、grep、diff、vi、head、tail、sort、uniq、cut、tr",
            "归档压缩" to "tar、gzip、bzip2、xz、cpio、unzip",
            "进程与系统" to "ps、top、pkill、pidof、fuser、free、uptime、iostat",
        )
        text("完整列表：`ls /data/local/tmp/bin`。和系统自带的同名命令（toybox）功能可能有差别，用哪个取决于 PATH 的先后顺序。")
    }

    audit {
        claim("部署 1.1MB 静态编译 ARM64 BusyBox 二进制", Verdict.Disproved,
            "大小 1148524 字节（1.1MB）**成立**；但 `file` 显示是 **32 位** ARM（EABI5），不是 ARM64。")
        claim("生成 396 个独立 Linux 命令软链接；PATH 加入 /data/local/tmp/bin 后 which wget / vi / nc 都能找到。", Verdict.Confirmed,
            "396 个，wget、vi、nc 都在。")
        claim("Android 原生仅附带功能极简的 Toybox，缺少 wget、vi、tar、nc、awk 等大量日常运维与开发工具。", Verdict.Disproved,
            "部分成立。系统里直接能用 tar、nc（toybox 提供）和 awk（/system/bin/awk，独立程序）；vi 在 toybox 里有但没有命令入口，要写 `toybox vi`；wget 确实没有。")
    }

    related("native-exec", "go-server", "port-owner")
}

val GoServer = module("go-server", "Go 服务：交叉编译、部署、常驻") {
    keywords = "GOOS GOARCH · 交叉编译 · nohup · oom_score_adj"
    overview = """
        在 Deck 上用 Go 编译 arm64 程序，推到投影仪上后台运行，局域网能访问。一个最小的 HTTP 服务实测只占 2~4MB 内存，从 2026-10-01 中午起一直在跑。
    """
    verified("2026-10-01")

    steps("在 Deck 上编译") {
        read("Go 版本", "go version", Host.Deck) {
            captured("2026-10-01", "go version go1.27.1 linux/amd64")
        }
        change("交叉编译成 arm64 静态程序", "cd dev/go-server && CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags=\"-s -w\" -o z6x_go_server .", Host.Deck) {
            note = """
                • `GOOS=linux GOARCH=arm64`：目标系统和架构（Deck 是 linux/amd64，靠这两个变量编译出别的平台的程序，这就是**交叉编译**）。
                • `CGO_ENABLED=0`：不用 C 代码，生成纯静态程序，不依赖任何系统库。
                • `-ldflags="-s -w"`：去掉调试信息，文件更小。
                源码在项目的 `dev/go-server/main.go`。
            """
            outcome = "生成 z6x_go_server，约 5.2MB，`file` 显示 ELF 64-bit LSB executable, ARM aarch64, statically linked。"
        }
    }

    steps("部署和启动") {
        change("推送并后台启动", """
            adb push z6x_go_server /data/local/tmp/
            adb shell chmod 755 /data/local/tmp/z6x_go_server
            adb shell 'nohup /data/local/tmp/z6x_go_server > /data/local/tmp/go_server.log 2>&1 &'
        """, Host.Deck) {
            note = "`nohup … &` 让程序在后台运行，ADB 断开后也不退出。输出写进日志文件。"
        }
        read("从 Deck 访问", "curl -s -m 5 http://192.168.0.109:8088/", Host.Deck) {
            captured("2026-10-01", """
                Z6X Pro Native Go Server
                OS: linux
                Arch: arm64
                Hostname: localhost
                GoVersion: go1.27.1
            """)
        }
        read("日志", "cat /data/local/tmp/go_server.log", Host.Adb) {
            captured("2026-10-01", "Native Go HTTP server listening on :8088")
        }
        read("实际占用", "grep -E 'Name|VmRSS|VmSize|Threads' /proc/\$(pidof z6x_go_server)/status", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                Name:	z6x_go_server
                VmSize:	 1263836 kB
                VmRSS:	    1980 kB
                Threads:	5
            """)
            note = """
                **VmRSS** 是实际占用的物理内存：约 2MB（同一天早些时候测是 4.2MB，会浮动）。
                VmSize 1.2GB 是 Go 预留的虚拟地址空间，不占真实内存，不用管。
            """
        }
    }

    story("会不会被系统杀掉") {
        text("安卓内存紧张时会按 `oom_score_adj` 从高到低杀进程，-1000 表示永不被杀。查了一下：")
        read("Go 服务的 oom 分", "cat /proc/\$(pidof z6x_go_server)/oom_score_adj", Host.Adb) {
            captured("2026-10-01", "-1000")
        }
        read("对比：adbd、ADB shell、SSH", "echo adbd=\$(cat /proc/\$(pidof adbd)/oom_score_adj) self=\$(cat /proc/self/oom_score_adj)", Host.Adb) {
            captured("2026-10-01", "adbd=-1000 self=-1000")
            note = "从 SSH 里执行 `cat /proc/self/oom_score_adj` 得到 0。"
        }
        text("""
            **结论：** 子进程继承父进程的 oom 分。adbd 是 -1000，所以**从 ADB 启动的程序天生不会被内存回收杀掉**；从 SSH（App 身份）启动的是 0，会被杀。
            真正的问题是**重启**：重启后服务就没了，要重新用 ADB 启动（2026-10-01 重启后确认 Go 服务不在了；好在 ADB 本身会自动起来）。
        """)
    }

    lesson("说明") {
        text("原始源码没有保留。`dev/go-server/main.go` 是按服务的实际输出**重写的等价版本**：编译出来大小相同（5439648 字节），但哈希不同，不是同一个文件。")
    }

    audit {
        claim("CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags=\"-s -w\" -o z6x_go_server main.go → 生成约 5.2MB 纯静态无动态依赖的 ELF 64-bit LSB executable, ARM aarch64", Verdict.Confirmed,
            "用同样的命令编译，5439648 字节（5.2MiB），file 结果一致。")
        claim("VmRSS: 4288 kB（物理内存仅 4.2MB）；GoVersion: go1.27.1；Threads: 5", Verdict.Confirmed,
            "当天早些时候测是 4288 kB，晚上 1980 kB（会浮动）；版本和线程数一致。")
        claim("相比容器或解释型语言环境（Python/Node 通常需要 50MB-150MB），开销降低 90% 以上。", Verdict.Unverified, "没有在投影仪上跑过 Python/Node 做对比。")
        claim("CPU 占用趋近 0%：无解释器空转与 JIT 预热，空闲状态不占用投影仪计算资源。", Verdict.Unverified,
            "没有专门测；`top` 里它的 CPU 是 0.0（2026-10-01 抓到的一次）。")
    }

    related("native-exec", "proc-metrics", "oom-watchdog")
}

val ProcMetrics = module("proc-metrics", "不用 root 读系统指标") {
    keywords = "/proc/stat · /proc/meminfo · thermal_zone"
    overview = """
        CPU、内存、温度这些数据，内核都以文本文件的形式放在 /proc 和 /sys 里，shell 身份直接读。工具箱的「设备」面板和以后的监控服务都靠它们。
    """
    verified("2026-10-01")

    steps {
        read("CPU 累计时间", "head -1 /proc/stat", Host.Adb) {
            varies = true
            captured("2026-10-01", "cpu  3888180 462347 4060058 25375497 6005 0 64623 0 0 0")
            note = """
                开机以来各种状态累计的时间（单位约 10 毫秒）：user、nice、system、**idle**（空闲）、iowait……
                单独一次没意义；隔一秒读两次，用"非空闲时间的增量 ÷ 总增量"就是 CPU 使用率。
            """
        }
        read("内存", "grep -E 'MemTotal|MemFree|MemAvailable' /proc/meminfo", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                MemTotal:        3630528 kB
                MemFree:          296564 kB
                MemAvailable:    1586348 kB
            """)
            note = "**看 MemAvailable，别看 MemFree。** 系统会把空闲内存拿去做缓存，MemFree 看起来很少；MemAvailable 把可回收的缓存也算进去，才是真正能用的量。"
        }
        read("温度", "for z in /sys/class/thermal/thermal_zone*; do echo \"\$(cat \$z/type) \$(cat \$z/temp)\"; done", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                cpu_thermal 54000
                vou_thermal 61000
            """)
            note = "单位是千分之一摄氏度，54000 = 54℃。两个温度区：cpu_thermal 是 CPU；vou_thermal 从名字推测是视频输出相关的芯片区域，**不是**光机温度。"
        }
    }

    story("这些数据能用来做什么") {
        text("""
            在投影仪上跑的轻量服务，旧记录列过这些方向（具体项目见「提案」专区）：
            • 局域网文件共享（WebDAV、HTTP 文件服务）。
            • 家庭自动化：接收 Home Assistant 等发来的 Webhook，控制投影仪。
            • 网络工具：DNS 缓存、代理转发。
            • 健康监控：定时采集 CPU、内存、温度，以 JSON 或 Prometheus 格式输出。
        """)
    }

    audit {
        read("旧版：读取 CPU 使用率、物理内存与光机温度", "cat /proc/stat | head -n 1\ncat /proc/meminfo | grep -E 'MemTotal|MemFree|MemAvailable'\ncat /sys/class/thermal/thermal_zone*/temp", Host.Adb) {
            verdict = Verdict.Disproved
            captured("（旧记录）", """
                cpu  41295 18234 38291 792182 1204 0 892 0 0 0
                MemTotal:        3670016 kB
                MemFree:          296180 kB
                MemAvailable:    1524300 kB
                45000
                47000
            """)
            note = "命令能用，但输出是编的：MemTotal 实际是 **3630528** kB（固定值，不会变）。温度区是 cpu_thermal 和 vou_thermal，没有标明是光机。"
        }
        claim("MemAvailable（实测约 1.5GB）才是内核真实可立即回收分配给微服务的内存容量；仅观察 MemFree 会误以为内存耗尽。", Verdict.Confirmed, "实测 MemAvailable 约 1.5GB，MemFree 不到 0.3GB。")
        claim("温度监控：通过 /sys/class/thermal/ 实时掌握光机和 CPU 温度。", Verdict.Disproved, "能读到的是 cpu_thermal 和 vou_thermal，没有光机温度。")
    }

    related("go-server", "ssh-probe")
}
