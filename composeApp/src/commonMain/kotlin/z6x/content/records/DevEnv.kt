package z6x.content.records

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val NativeExec = module("native-exec", "在非 root 环境下运行自编译程序") {
    keywords = "/data/local/tmp · noexec · 静态编译 · 32/64 位"
    overview = """
        获得 ADB 的 shell 身份后，即可将自行编译的 Linux 程序放入 `/data/local/tmp` 直接运行，无需 root，也无需安装应用。本节的 BusyBox 和 Go 服务都以这种方式运行。
    """
    verified("2026-10-01")

    why {
        text("""
            • 安卓的内核就是 Linux，可以直接执行 ELF 格式的程序，前提是文件所在分区允许执行（未设置 `noexec`），且当前用户对文件有执行权限。
            • `/data/local/tmp` 是专为 shell 身份保留的目录，ADB 可以自由读写和执行其中的文件。
            • 程序需要**静态编译**：安卓的 C 库（bionic）与普通 Linux 的 glibc 不同，动态链接的 Linux 程序在安卓上找不到所需的库。静态编译会将全部依赖打包进一个文件。
        """)
    }

    steps {
        read("/data 的挂载参数", "mount | grep ' /data '", Host.Adb) {
            captured("2026-10-01", "/dev/block/mmcblk0p53 on /data type ext4 (rw,seclabel,nosuid,nodev,noatime,journal_checksum,noauto_da_alloc,resgid=1065,data=ordered)")
            note = """
                括号内为挂载选项：有 `nosuid`（setuid 程序不提权）和 `nodev`（不识别设备文件），**没有 `noexec`**，因此可以执行程序。
            """
        }
        read("目录归属", "ls -ld /data/local/tmp", Host.Adb) {
            varies = true
            captured("2026-10-01", "drwxrwx--x 3 shell shell 4096 2026-10-01 12:31 /data/local/tmp")
            note = "属于 shell 用户和组，shell 拥有读、写、执行（rwx）权限。"
        }
        read("系统用户空间是 32 位", "getprop ro.product.cpu.abi", Host.Adb) {
            captured("2026-10-01", "armeabi-v7a")
            note = "但内核是 64 位，**静态编译的 64 位程序同样可以运行**（Go 服务即为 arm64 程序）。32 位的限制只针对应用的原生库，因为应用需要与系统的 32 位库链接。"
        }
    }

    story("方案比较") {
        text("""
            • **Termux**（未采用）：提供完整 Linux 软件包的应用，可用 apt 安装工具。但它以应用身份运行（权限与 SSH 一样受限），且占用空间较大。
            • **PRoot 运行 Debian 等发行版**（未采用）：在应用内模拟完整的发行版，系统调用需经过一层转换，性能和内存开销较大，不适合内存仅 3.5GB 且需播放视频的投影仪。
            • **自行编译静态程序放入 /data/local/tmp**（采用）：无依赖、占用最小，以 shell 身份运行，权限也高于应用。
        """)
    }

    consequences {
        text("""
            • 无法绑定 1024 以下的端口（实测 `/proc/sys/net/ipv4/ip_unprivileged_port_start` 为 1024），服务须使用 8088 等高位端口。
            • 恢复出厂设置会清空 /data/local/tmp。
            • **重启后所有进程都会消失**，需要重新启动。没有 root 无法注册开机自启。
        """)
    }

    audit {
        read("旧版：查看 /data 分区挂载参数与 tmp 目录权限", "mount | grep ' /data '\nls -ld /data/local/tmp", Host.Adb) {
            verdict = Verdict.Disproved
            captured("（旧记录）", """
                /dev/block/platform/bootdevice/by-name/userdata on /data type ext4 (rw,seclabel,nosuid,nodev,noatime,discard,noauto_da_alloc,data=ordered)
                drwxrwx--x 4 shell shell 4096 ... /data/local/tmp
            """)
            note = "结论（无 noexec、shell 有 rwx 权限）**成立**，但输出并非实测：实机设备名为 `/dev/block/mmcblk0p53`，挂载参数中没有 discard，而有 journal_checksum 和 resgid=1065。"
        }
        claim("SELinux 规则：ADB shell 运行于 u:r:shell:s0 域，允许访问 /proc、/sys、网络 socket 绑定与连接，但禁止修改系统底层属性（如 setprop 核心配置）或读取应用私有加密数据。", Verdict.Disproved,
            "shell 域**成立**；但本机处于 Permissive 模式，SELinux 不拦截任何操作。shell 无法读取应用私有数据是由于文件权限（DAC），而非 SELinux。")
        claim("端口监听边界：Linux 标准内核限制非 root 用户不可绑定 < 1024 特权端口。开发的原生服务需绑定 1024 以上非特权端口（如 8080, 8088, 9090）。", Verdict.Confirmed,
            "`ip_unprivileged_port_start` = 1024。")
        claim("轻量原生架构（采用方案）：物理内存占用小于 10MB。Termux 套件方案：约占 200MB 存储，但部分包缺乏电视版 ARM64 支持。Debian / PRoot 容器方案：系统调用开销大（I/O 速度下降 30%-60%），常驻内存 150MB-300MB。", Verdict.Unverified,
            "Go 服务实测 2~4MB **成立**。Termux、PRoot 的数字没有出处，也未在本机上安装过。")
    }

    related("busybox", "go-server", "force-adb")
}

val Busybox = module("busybox", "部署 BusyBox：补充 396 个 Linux 命令") {
    keywords = "busybox --install · toybox · PATH"
    overview = """
        安卓自带的 toybox 命令较为精简，缺少 vi、wget 等，部分命令功能也不完整（如 awk 没有 strtonum）。部署一个静态编译的 BusyBox，即可一次补充 396 个命令。
    """
    verified("2026-10-01")

    steps("部署") {
        change("推送到投影仪并加执行权限", """
            adb push busybox /data/local/tmp/busybox
            adb shell chmod 755 /data/local/tmp/busybox
        """, Host.Deck) {
            note = "busybox 文件位于 shared 目录。在 Deck 上执行。"
        }
        change("为每个命令创建链接", "mkdir -p /data/local/tmp/bin && /data/local/tmp/busybox --install -s /data/local/tmp/bin", Host.Adb) {
            note = "BusyBox 在一个文件中包含数百个命令，根据被调用时的名称决定执行哪个命令。`--install -s` 为每个命令创建一个指向它的符号链接。"
        }
    }

    verify {
        read("版本", "/data/local/tmp/busybox | head -1", Host.Adb) {
            captured("2026-10-01", "BusyBox v1.31.0 (2019-06-10 15:54:51 CEST) multi-call binary.")
        }
        read("文件类型（在 Deck 上看）", "file busybox", Host.Deck) {
            manual = true
            captured("2026-10-01", "busybox:       ELF 32-bit LSB executable, ARM, EABI5 version 1 (SYSV), statically linked, stripped")
            note = "在 shared 目录中执行。结果为 **32 位** ARM、静态链接。32 位程序在 64 位内核上同样可以运行。"
        }
        read("链接数量", "ls /data/local/tmp/bin | wc -l", Host.Adb) {
            captured("2026-10-01", "396")
        }
        read("加入 PATH 后试用", "export PATH=/data/local/tmp/bin:\$PATH; which wget vi nc awk tar", Host.Adb) {
            captured("2026-10-01", """
                /data/local/tmp/bin/wget
                /data/local/tmp/bin/vi
                /data/local/tmp/bin/nc
                /data/local/tmp/bin/awk
                /data/local/tmp/bin/tar
            """)
            note = "`export` 只对当前 shell 会话有效，退出后失效。自动加载的方法见提案「Shell 环境自动加载（env.sh）」。"
        }
    }

    story("补充的命令") {
        facts(
            "网络" to "nc、wget、ping、traceroute、nslookup、netstat、arp、route",
            "文本" to "awk、sed、grep、diff、vi、head、tail、sort、uniq、cut、tr",
            "归档压缩" to "tar、gzip、bzip2、xz、cpio、unzip",
            "进程与系统" to "ps、top、pkill、pidof、fuser、free、uptime、iostat",
        )
        text("完整列表可用 `ls /data/local/tmp/bin` 查看。与系统自带的同名命令（toybox）功能可能存在差别，实际使用哪一个取决于 PATH 中的顺序。")
    }

    audit {
        claim("部署 1.1MB 静态编译 ARM64 BusyBox 二进制", Verdict.Disproved,
            "大小 1148524 字节（1.1MB）**成立**；但 `file` 显示为 **32 位** ARM（EABI5），而非 ARM64。")
        claim("生成 396 个独立 Linux 命令软链接；PATH 加入 /data/local/tmp/bin 后 which wget / vi / nc 都能找到。", Verdict.Confirmed,
            "共 396 个，wget、vi、nc 均存在。")
        claim("Android 原生仅附带功能极简的 Toybox，缺少 wget、vi、tar、nc、awk 等大量日常运维与开发工具。", Verdict.Disproved,
            "部分成立。系统中可直接使用 tar、nc（由 toybox 提供）和 awk（/system/bin/awk，独立程序）；vi 包含在 toybox 中但没有独立命令入口，需写作 `toybox vi`；wget 确实没有。")
    }

    related("native-exec", "go-server", "port-owner")
}

val GoServer = module("go-server", "Go 服务：交叉编译、部署与常驻运行") {
    keywords = "GOOS GOARCH · 交叉编译 · nohup · oom_score_adj"
    overview = """
        在 Deck 上用 Go 编译 arm64 程序，推送到投影仪后台运行，可从局域网访问。一个最小的 HTTP 服务实测仅占用 2~4MB 内存，自 2026-10-01 中午起持续运行。
    """
    verified("2026-10-01")

    steps("在 Deck 上编译") {
        read("Go 版本", "go version", Host.Deck) {
            captured("2026-10-01", "go version go1.27.1 linux/amd64")
        }
        change("交叉编译成 arm64 静态程序", "cd dev/go-server && CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags=\"-s -w\" -o z6x_go_server .", Host.Deck) {
            note = """
                • `GOOS=linux GOARCH=arm64`：指定目标系统和架构（Deck 是 linux/amd64，通过这两个变量为其他平台编译程序，即**交叉编译**）。
                • `CGO_ENABLED=0`：不使用 C 代码，生成纯静态程序，不依赖任何系统库。
                • `-ldflags="-s -w"`：去除调试信息，减小文件体积。
                源码位于项目的 `dev/go-server/main.go`。
            """
            outcome = "生成 z6x_go_server，约 5.2MB，`file` 显示为 ELF 64-bit LSB executable, ARM aarch64, statically linked。"
        }
    }

    steps("部署与启动") {
        change("推送并后台启动", """
            adb push z6x_go_server /data/local/tmp/
            adb shell chmod 755 /data/local/tmp/z6x_go_server
            adb shell 'nohup /data/local/tmp/z6x_go_server > /data/local/tmp/go_server.log 2>&1 &'
        """, Host.Deck) {
            note = "`nohup … &` 使程序在后台运行，ADB 断开后也不退出。输出写入日志文件。"
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
        read("实际资源占用", "grep -E 'Name|VmRSS|VmSize|Threads' /proc/\$(pidof z6x_go_server)/status", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                Name:	z6x_go_server
                VmSize:	 1263836 kB
                VmRSS:	    1980 kB
                Threads:	5
            """)
            note = """
                **VmRSS** 是实际占用的物理内存：约 2MB（同日较早时测得 4.2MB，数值会浮动）。
                VmSize 1.2GB 是 Go 预留的虚拟地址空间，不占用实际内存，无需关注。
            """
        }
    }

    story("是否会被系统结束") {
        text("安卓在内存紧张时按 `oom_score_adj` 从高到低结束进程，-1000 表示永不被结束。查询结果如下：")
        read("Go 服务的 oom 分", "cat /proc/\$(pidof z6x_go_server)/oom_score_adj", Host.Adb) {
            captured("2026-10-01", "-1000")
        }
        read("对比：adbd、ADB shell、SSH", "echo adbd=\$(cat /proc/\$(pidof adbd)/oom_score_adj) self=\$(cat /proc/self/oom_score_adj)", Host.Adb) {
            captured("2026-10-01", "adbd=-1000 self=-1000")
            note = "在 SSH 中执行 `cat /proc/self/oom_score_adj` 结果为 0。"
        }
        text("""
            **结论：** 子进程继承父进程的 oom 分值。adbd 为 -1000，因此**从 ADB 启动的程序不会因内存回收而被结束**；从 SSH（应用身份）启动的程序分值为 0，可能被结束。
            真正的问题在于**重启**：重启后服务不再运行，需要重新通过 ADB 启动（2026-10-01 重启后确认 Go 服务已不在；ADB 本身会自动运行）。
        """)
    }

    lesson("说明") {
        text("原始源码未保留。`dev/go-server/main.go` 是根据服务的实际输出**重写的等价版本**：编译结果大小相同（5439648 字节），但哈希不同，并非同一个文件。")
    }

    audit {
        claim("CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags=\"-s -w\" -o z6x_go_server main.go → 生成约 5.2MB 纯静态无动态依赖的 ELF 64-bit LSB executable, ARM aarch64", Verdict.Confirmed,
            "用相同命令编译得到 5439648 字节（5.2MiB），file 结果一致。")
        claim("VmRSS: 4288 kB（物理内存仅 4.2MB）；GoVersion: go1.27.1；Threads: 5", Verdict.Confirmed,
            "当日较早时测得 4288 kB，晚间为 1980 kB（数值会浮动）；版本和线程数一致。")
        claim("相比容器或解释型语言环境（Python/Node 通常需要 50MB-150MB），开销降低 90% 以上。", Verdict.Unverified, "未在投影仪上运行 Python/Node 进行对比。")
        claim("CPU 占用趋近 0%：无解释器空转与 JIT 预热，空闲状态不占用投影仪计算资源。", Verdict.Unverified,
            "未专门测试；2026-10-01 的一次 `top` 采样中其 CPU 占用为 0.0。")
    }

    related("native-exec", "proc-metrics", "oom-watchdog")
}

val ProcMetrics = module("proc-metrics", "在非 root 环境下读取系统指标") {
    keywords = "/proc/stat · /proc/meminfo · thermal_zone"
    overview = """
        CPU、内存、温度等数据由内核以文本文件的形式提供在 /proc 和 /sys 中，shell 身份可直接读取。工具箱的「设备」面板和今后的监控服务都依赖这些数据。
    """
    verified("2026-10-01")

    steps {
        read("CPU 累计时间", "head -1 /proc/stat", Host.Adb) {
            varies = true
            captured("2026-10-01", "cpu  3888180 462347 4060058 25375497 6005 0 64623 0 0 0")
            note = """
                开机以来各状态的累计时间（单位约为 10 毫秒）：user、nice、system、**idle**（空闲）、iowait 等。
                单次读数没有意义；间隔一秒读取两次，以「非空闲时间增量 ÷ 总时间增量」计算即为 CPU 使用率。
            """
        }
        read("内存", "grep -E 'MemTotal|MemFree|MemAvailable' /proc/meminfo", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                MemTotal:        3630528 kB
                MemFree:          296564 kB
                MemAvailable:    1586348 kB
            """)
            note = "**应参考 MemAvailable，而非 MemFree。** 系统会将空闲内存用作缓存，因此 MemFree 显得很少；MemAvailable 将可回收的缓存计算在内，才是实际可用的内存量。"
        }
        read("温度", "for z in /sys/class/thermal/thermal_zone*; do echo \"\$(cat \$z/type) \$(cat \$z/temp)\"; done", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                cpu_thermal 54000
                vou_thermal 61000
            """)
            note = "单位为千分之一摄氏度，54000 即 54℃。共两个温度区：cpu_thermal 为 CPU；vou_thermal 从名称推测为视频输出相关的芯片区域，**并非**光机温度。"
        }
    }

    story("应用方向") {
        text("""
            旧记录列出的投影仪轻量服务方向如下（具体项目见「提案」专区）：
            • 局域网文件共享（WebDAV、HTTP 文件服务）。
            • 家庭自动化：接收 Home Assistant 等发送的 Webhook，控制投影仪。
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
            note = "命令可用，但输出并非实测：MemTotal 实际为 **3630528** kB（固定值，不会变化）。温度区为 cpu_thermal 和 vou_thermal，并未标明光机。"
        }
        claim("MemAvailable（实测约 1.5GB）才是内核真实可立即回收分配给微服务的内存容量；仅观察 MemFree 会误以为内存耗尽。", Verdict.Confirmed, "实测 MemAvailable 约 1.5GB，MemFree 不到 0.3GB。")
        claim("温度监控：通过 /sys/class/thermal/ 实时掌握光机和 CPU 温度。", Verdict.Disproved, "可读取的是 cpu_thermal 和 vou_thermal，没有光机温度。")
    }

    related("go-server", "ssh-probe")
}
