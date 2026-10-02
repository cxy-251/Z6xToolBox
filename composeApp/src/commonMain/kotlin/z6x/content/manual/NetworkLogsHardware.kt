package z6x.content.manual

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val NetworkCmds = module("network-cmds", "网络：地址、路由、端口、代理、证书") {
    keywords = "ip addr · ip route · ip rule · netstat · ip neigh · wifi · http_proxy · cacerts"
    overview = """
        查询投影仪的网络状态。由于 Clash 的 VPN（tun0）正在运行，路由比普通设备复杂。
    """
    verified("2026-10-01")

    steps("地址与路由") {
        read("Wi-Fi 网卡地址", "ip -4 addr show wlan0", Host.Adb) { varies = true }
        read("主路由表", "ip route show", Host.Adb) {
            captured("2026-10-01", """
                172.19.0.0/30 dev tun0 proto kernel scope link src 172.19.0.1
                192.168.0.0/24 dev wlan0 proto kernel scope link src 192.168.0.109
            """)
            note = "主路由表中**没有默认网关**。安卓按网络划分了多张路由表，由「策略路由」决定查询哪一张。"
        }
        read("策略路由规则", "ip rule show | head -8", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                0:	from all lookup local
                9000:	from all lookup main
                10000:	from all fwmark 0xc0000/0xd0000 lookup legacy_system
                11000:	from all iif lo oif wlan0 uidrange 0-0 lookup wlan0
                12000:	from all iif tun0 lookup local_network
                16000:	from all fwmark 0x10063/0x1ffff iif lo lookup local_network
                16000:	from all fwmark 0x10065/0x1ffff iif lo uidrange 0-99999 lookup tun0
                16000:	from all fwmark 0x10065/0x1ffff iif lo uidrange 0-0 lookup tun0
            """)
            note = "从上往下依次匹配。`uidrange 0-99999 lookup tun0` 表示所有应用的流量查询 tun0 表，即经过 Clash 的 VPN。"
        }
        read("wlan0 的路由表", "ip route show table wlan0", Host.Adb) {
            captured("2026-10-01", """
                default via 192.168.0.1 dev wlan0 proto static
                192.168.0.0/24 dev wlan0 proto static scope link
            """)
            note = "默认网关（路由器 192.168.0.1）位于此表中。"
        }
        read("局域网邻居（ARP 表）", "ip neigh show", Host.Adb) {
            varies = true
            note = "同一局域网内最近通信过的设备的 IP 和 MAC 地址。REACHABLE 表示正在通信，STALE 表示已有一段时间未通信。（输出包含其他设备的 MAC 地址，因此不在此记录。）"
        }
    }

    steps("端口") {
        read("正在监听的 TCP 端口", "netstat -tln", Host.Adb) {
            varies = true
            note = "`-p` 在 shell 下无法显示其他进程的名称，查询端口归属见「核查：端口的所属进程」。5555 为 ADB，2222 为 SimpleSSHD，7890 为 Clash，8088 为 Go 服务，8080、7100 等属于系统组件。"
        }
    }

    steps("Wi-Fi 与当前网络") {
        read("Wi-Fi 开关状态", "cmd wifi status | head -2", Host.Adb) {
            captured("2026-10-01", """
                Wifi is enabled
                Wifi scanning is always available
            """)
        }
        read("信号与速率", "dumpsys wifi | grep -m1 mWifiInfo | grep -oE 'RSSI: -?[0-9]+|Link speed: [0-9]+Mbps|Frequency: [0-9]+MHz'", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                RSSI: -73
                Link speed: 351Mbps
                Link speed: 351Mbps
                Frequency: 5785MHz
            """)
            note = """
                **RSSI** 为信号强度，越接近 0 越好，-73 偏弱；**Link speed** 为协商速率；**Frequency** 5785MHz 属于 5GHz 频段（157 信道）。
                只提取这几项，是因为完整的 mWifiInfo 中包含 Wi-Fi 名称和 MAC 地址。速率随信号变化（同一天曾测得 468 和 351Mbps）。
            """
        }
        read("连接过程（状态机）", "dumpsys wifi | grep -i SUPPLICANT_STATE_CHANGE_EVENT | tail -6", Host.Adb) {
            varies = true
            note = "Wi-Fi 连接需经过 SCANNING → ASSOCIATING → 4WAY_HANDSHAKE → COMPLETED 等阶段。无法连接时，查看它停在哪个阶段。"
        }
        change("断开后重新连接 Wi-Fi", "cmd wifi set-wifi-enabled disabled && sleep 2 && cmd wifi set-wifi-enabled enabled", Host.Adb) {
            note = "**通过 Wi-Fi 连接 ADB 时会断开**，需等待自动重连后再执行 `adb connect`。"
        }
        read("当前默认网络", "dumpsys connectivity | grep -m1 'Active default network'", Host.Adb) {
            varies = true
            note = "VPN 的情况见「代理：Clash Meta 以 VPN 模式运行」。"
        }
    }

    steps("代理与证书") {
        change("全局 HTTP 代理 / 清除", "settings put global http_proxy 192.168.0.10:7890\nsettings get global http_proxy\nsettings put global http_proxy :0", Host.Adb) {
            note = "地址可以是本机（127.0.0.1）或局域网中的代理。使用 Clash VPN 时无需设置。"
        }
        read("系统信任的 CA 证书数", "ls /system/etc/security/cacerts/ | wc -l", Host.Adb) {
            captured("2026-10-01", "125")
            note = "HTTPS 依据这些根证书判断网站是否可信。用户自行安装的证书位于 `/data/misc/user/0/cacerts-added/`，shell 无权查看。安卓 7 以后应用默认不信任用户证书，因此在电视上抓取 HTTPS 流量十分困难。"
        }
    }

    steps("防火墙与抓包（需要 root）") {
        read("防火墙规则", "iptables -L -n", Host.Adb) {
            expectsError = true
            captured("2026-10-01", """
                iptables v1.8.7 (legacy): can't initialize iptables table `filter': Permission denied (you must be root)
                Perhaps iptables or your kernel needs to be upgraded.
            """)
        }
        read("抓包", "timeout 5 tcpdump -i wlan0 -c 1 -nn", Host.Adb) {
            expectsError = true
            note = "shell 没有抓包权限，见提案「在投影仪上抓包（tcpdump）」。"
        }
    }

    steps("从 Deck 登录 SSH") {
        read("SimpleSSHD", "ssh z6x", Host.Deck) {
            manual = true
            note = "别名配置见「SimpleSSHD 公钥登录」。不使用别名时：`ssh -p 2222 192.168.0.109`。"
        }
    }

    audit {
        read("旧版：查看正在监听的 TCP 端口（排查 5555 与 2222）", "netstat -tlpn 2>/dev/null || ss -tlpn", Host.Adb) {
            verdict = Verdict.Confirmed
            varies = true
            note = "命令可用，5555、2222 均在列；但 shell 下 `-p` 的进程列显示为 `-`。"
        }
        read("旧版：查看投屏包名与 8080 端口", "pm list packages | grep wirelessscreen && netstat -tlpn | grep 8080", Host.Adb) {
            verdict = Verdict.Confirmed
            varies = true
            note = "8080 确实在监听，属于 uid 1000（系统组件，无法细分是否为投屏应用）。"
        }
        read("旧版：查看当前 Wi-Fi 频段、信道与物理协商速率", "dumpsys wifi | grep -iE 'mWifiInfo|Link speed|Frequency' | head -n 4", Host.Adb) {
            verdict = Verdict.Confirmed
            varies = true
            note = "命令可用，但输出中包含 Wi-Fi 名称和 MAC 地址，上文改为只提取部分字段的写法。"
        }
        read("旧版：查看当前系统默认活动网络与 VPN 接口（tun0）", "dumpsys connectivity | grep -E 'Active network|tun0' | head -n 3", Host.Adb) {
            verdict = Verdict.Confirmed
            varies = true
        }
        change("旧版：使用 tcpdump 抓取电视网络数据包", "tcpdump -i any -s 0 -w /sdcard/capture.pcap -c 1000 && adb pull /sdcard/capture.pcap ./", Host.Adb) {
            verdict = Verdict.Disproved
            note = "shell 没有抓包权限（socket: Operation not permitted）。"
        }
        claim("iptables -L -n -v：查看 Linux 内核防火墙规则过滤链。", Verdict.Disproved, "需要 root：`can't initialize iptables table 'filter': Permission denied (you must be root)`。")
    }

    related("clash-proxy", "port-owner", "packet-capture")
}

val LogsCrash = module("logs-crash", "日志与崩溃") {
    keywords = "logcat · -b crash · -b kernel · dmesg · tombstones · bugreport"
    overview = """
        应用闪退或系统异常时，在日志中查找原因。安卓的日志都在 logcat 中；查看内核日志的 dmesg 在 shell 下不可用，但 logcat 的 kernel 缓冲区可以替代。
    """
    verified("2026-10-01")

    steps("logcat 基础") {
        read("各缓冲区大小", "logcat -g", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                main: ring buffer is 2 MiB (1 MiB consumed, 8 MiB readable), max entry is 5120 B, max payload is 4068 B
                system: ring buffer is 2 MiB (1 MiB consumed, 32 MiB readable), max entry is 5120 B, max payload is 4068 B
                crash: ring buffer is 2 MiB (0 B consumed, 0 B readable), max entry is 5120 B, max payload is 4068 B
                kernel: ring buffer is 2 MiB (1 MiB consumed, 17 MiB readable), max entry is 5120 B, max payload is 4068 B
            """)
            note = "日志分为若干环形缓冲区，写满后覆盖最旧的记录：main 为应用日志，system 为系统日志，crash 为崩溃，kernel 为内核日志。"
        }
        read("仅查看崩溃", "logcat -b crash -d | grep -E 'FATAL|Process:|Caused by'", Host.Adb) {
            varies = true
            logOutput = true
            captured("2026-10-01", """
                10-01 22:47:34.263  4050  4050 E AndroidRuntime: FATAL EXCEPTION: main
                10-01 22:47:34.263  4050  4050 E AndroidRuntime: Process: com.xgimi.xrmservice, PID: 4050
                10-01 22:47:34.263  4050  4050 E AndroidRuntime: Caused by: java.lang.NullPointerException: Attempt to invoke interface method 'int com.xgimi.aidl.IGimiSound.setCommonCmd(java.lang.String)' on a null object reference
                10-01 22:49:13.983 23245 23245 E AndroidRuntime: FATAL EXCEPTION: main
                10-01 22:49:13.983 23245 23245 E AndroidRuntime: Process: com.xgimi.xrmservice, PID: 23245
                10-01 22:49:13.983 23245 23245 E AndroidRuntime: Caused by: java.lang.NullPointerException: Attempt to invoke interface method 'int com.xgimi.aidl.IGimiSound.setCommonCmd(java.lang.String)' on a null object reference
            """)
            note = """
                `-d` 表示输出后即退出（不加则持续等待新日志）；grep 只保留崩溃的标题、进程和根本原因。无输出说明最近没有应用崩溃。
                **2026-10-01 查到两次真实崩溃**：都是 xrmservice 接收开关屏广播时，调用的声音接口 IGimiSound 为 null。后续实验见「停用清单」中的「xrmservice 在开关屏时崩溃」。
            """
        }
        read("实时查看错误级别及以上的日志", "adb logcat -v time '*:E' | grep -iE 'AndroidRuntime|FATAL|Exception'", Host.Deck) {
            manual = true
            note = "在 Deck 上执行，然后在电视上复现问题。`*:E` 只显示 Error 级别，按 Ctrl+C 停止。先执行 `adb logcat -c` 清空旧日志，结果更清晰。"
        }
        change("扩大缓冲区 / 清空", "logcat -G 16M\nlogcat -c", Host.Adb) {
            note = "缓冲区只有 2MB，问题发生较早时日志可能已被覆盖。排查前应先调大；重启后恢复默认值。"
        }
        change("导出全部日志到 Deck", "adb logcat -d -v threadtime > tv.log", Host.Deck)
        change("完整的诊断包", "adb bugreport ./bugreport.zip", Host.Deck) {
            note = "包含日志、各服务状态等，大小为数十 MB，需等待一至两分钟。"
        }
    }

    steps("内核日志") {
        read("dmesg：shell 下不可用", "dmesg | head -2", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "dmesg: klogctl: Operation not permitted")
        }
        read("改用 logcat 的 kernel 缓冲区", "logcat -b kernel -d | tail -3", Host.Adb) {
            logOutput = true
            varies = true
            note = """
                **调整方案：** 读取内核日志的系统调用被拒绝，但 logd 会将内核日志复制一份到 kernel 缓冲区，shell 可以读取。
                注意其内容不一定是最新的（2026-10-01 晚间查看时，最新一条停留在中午 12:38）；而且内核日志数量庞大，可能因限速被丢弃（日志中有 `audit: rate limit exceeded`）。
            """
        }
        read("查找 HDMI 和内存回收相关的记录", "logcat -b kernel -d | grep -iE 'hdmi|hpd|edid|lowmemorykiller|oom' | tail -5", Host.Adb) {
            logOutput = true
            varies = true
            note = "旧版直接用 dmesg 查询 HDMI 热插拔和 OOM，在本机上需改为此写法。"
        }
    }

    steps("原生崩溃") {
        read("崩溃转储目录", "ls -l /data/tombstones/", Host.Adb) {
            captured("2026-10-01", "total 0")
            note = "C/C++ 程序崩溃时会在此留下 tombstone 文件。目录为空，说明没有原生崩溃记录。"
        }
        read("打印进程线程堆栈（需要 root）", "debuggerd -b \$(pidof z6x_go_server)", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "debuggerd: root is required")
            note = "即使是自己启动的进程也无法执行。"
        }
    }

    audit {
        read("旧版：查看 HDMI 物理热插拔与 EDID 协商日志", "dmesg | grep -iE 'hdmi|edid|drm|hpd' | tail -n 5", Host.Adb) {
            verdict = Verdict.Disproved
            note = "dmesg 在 shell 下报 Operation not permitted。应改用 `logcat -b kernel`（见上文）。"
        }
        read("旧版：排查系统是否发生过内存耗尽杀进程", "dmesg | grep -iE 'oom-killer|killed process' | tail -n 5", Host.Adb) {
            verdict = Verdict.Disproved
            note = "同上。"
        }
        read("旧版：打印卡死进程的线程底层堆栈", "debuggerd -b 1130", Host.Adb) {
            verdict = Verdict.Disproved
            note = "需要 root（`debuggerd: root is required`）。1130 是旧记录中的进程号，在本机上没有对应意义。"
        }
        read("旧版：清空历史缓冲区并精准捕获崩溃与异常堆栈", "adb logcat -c && adb logcat -v time *:E | grep -iE 'AndroidRuntime|FATAL|Exception'", Host.Deck) {
            verdict = Verdict.Unverified
            manual = true
            note = "命令写法正确。需要有应用崩溃才能看到效果，未专门演示。"
        }
        claim("查看系统生成的底层 Native 崩溃转储日志列表：ls -lt /data/tombstones/ | head -n 5", Verdict.Confirmed, "目录可读，内容为空。")
    }

    related("selinux-cmds", "adb-basics", "process-memory")
}

val SelinuxCmds = module("selinux-cmds", "SELinux：模式与安全标签") {
    keywords = "getenforce · setenforce · ls -Z · ps -Z · avc · restorecon"
    overview = """
        SELinux 是 Linux 内核的强制访问控制机制：每个进程和文件都有一个安全标签，由策略规定哪个标签可以对哪个标签执行哪些操作。本机处于 **Permissive（宽容）**模式：违规只记录日志，不拦截。
    """
    verified("2026-10-01")

    steps {
        read("当前模式", "getenforce", Host.Adb) {
            captured("2026-10-01", "Permissive")
            note = "Enforcing 拦截违规，Permissive 只做记录。量产设备通常为 Enforcing，本机为 Permissive，这正是普通应用能够启动 adbd 的原因。"
        }
        read("文件的安全标签", "ls -Z /data/local/tmp | head -3", Host.Adb) {
            captured("2026-10-01", """
                u:object_r:shell_data_file:s0 bin
                u:object_r:shell_data_file:s0 busybox
                u:object_r:shell_data_file:s0 go_server.log
            """)
        }
        read("进程的安全标签", "ps -AZ | grep adbd", Host.Adb) {
            varies = true
            captured("2026-10-01", "u:r:adbd:s0                    shell        18717     1   56416   8948 0                   0 S adbd")
            note = "adbd 位于 `adbd` 域，ADB shell 位于 `shell` 域，SimpleSSHD 位于 `untrusted_app` 域（见「SimpleSSHD 公钥登录」）。"
        }
        change("切换模式（需要 root）", "setenforce 0", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "setenforce: Couldn't set enforcing status to '0': Permission denied")
            note = "即使处于 Permissive 模式，切换模式本身也需要 root。本机原本就是 0（Permissive），无需切换。"
        }
        read("查找违规记录", "logcat -b kernel -d | grep 'avc:' | tail -5", Host.Adb) {
            logOutput = true
            varies = true
            note = """
                违规记录的格式为：`avc: denied { 操作 } for … scontext=主体 tcontext=客体 permissive=1`。`permissive=1` 表示只记录、未拦截。
                2026-10-01 查询结果为 0 条。内核审计日志因限速被大量丢弃（日志中 `audit_lost` 数值很大），因此找不到记录并不代表没有发生。
            """
        }
        change("恢复文件的默认标签", "restorecon -Rv /data/local/tmp/", Host.Adb) {
            note = "用于文件标签被改乱的情况，`-R` 表示递归，`-v` 显示修改内容。"
        }
    }

    audit {
        read("旧版：从内核缓冲区或事件日志检索拦截事件", "dmesg | grep -i 'avc: denied' | tail -n 5 || logcat -b events -d | grep avc", Host.Adb) {
            verdict = Verdict.Disproved
            note = "前半句的 dmesg 没有权限；后半句的 events 缓冲区中也没有 avc 记录。应使用 `logcat -b kernel`。"
        }
        claim("查询当前 SELinux 状态与尝试设为 Permissive：getenforce && setenforce 0。setenforce 失败的根因是 SELinux 策略拦截。", Verdict.Disproved,
            "本机**原本就是 Permissive**。setenforce 失败是因为需要 root，而非 SELinux 拦截。")
        claim("ADB shell 运行于 u:r:shell:s0 域，禁止修改系统底层属性。", Verdict.Disproved, "shell 域成立；Permissive 模式下 SELinux 不拦截任何操作。")
    }

    related("force-adb", "props-init", "logs-crash")
}

val HardwareCmds = module("hardware-cmds", "硬件：温度、CPU、显示、解码、音频、输入、蓝牙、USB") {
    keywords = "thermal · cpufreq · SurfaceFlinger · MediaCodec · ALSA · getevent · bluetooth · lsusb"
    overview = """
        查询本机的硬件状态与能力。大部分只读信息 shell 都可以查看。
    """
    verified("2026-10-01")

    steps("温度与 CPU 频率") {
        read("温度", "for z in /sys/class/thermal/thermal_zone*; do echo \"\$(cat \$z/type) \$(cat \$z/temp)\"; done", Host.Adb) {
            varies = true
            note = "单位为千分之一摄氏度。见「在非 root 环境下读取系统指标」。"
        }
        read("CPU 当前频率与调频策略", "cd /sys/devices/system/cpu/cpu0/cpufreq && cat scaling_cur_freq scaling_governor scaling_available_frequencies", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                900000
                schedutil
                400000 900000 1000000 1200000 1400000 1450000
            """)
            note = "单位为 kHz：当前 0.9GHz，最高 1.45GHz。`schedutil` 表示按负载自动调频。"
        }
    }

    steps("显示") {
        read("显示能力", "dumpsys display | grep -iE 'hdr|colormode' | head -4", Host.Adb) {
            note = "可查看支持的分辨率与刷新率列表、HDR 能力和色彩模式。2026-10-01：`mSupportedColorModes=[]`，没有可切换的色彩模式。"
        }
        read("正在合成的图层", "dumpsys SurfaceFlinger --list | head -5", Host.Adb) {
            varies = true
            note = "屏幕上的每个窗口和浮层都是一个图层，由 SurfaceFlinger 叠加后输出。"
        }
        read("刷新间隔", "dumpsys SurfaceFlinger --latency | head -1", Host.Adb) {
            captured("2026-10-01", "16666667")
            note = "第一行为刷新周期（纳秒）：16.67ms，即 60Hz。其后各行为每帧的时间戳，用于分析掉帧。"
        }
    }

    steps("视频解码") {
        read("硬件解码器", "dumpsys media.player | grep -iE 'hevc|avc|av1|vp9' | head -6", Host.Adb) {
            captured("2026-10-01", """
                Media type 'video/avc':
                  Decoder "OMX.uapi.video.decoder.avc" supports
                  Decoder "OMX.uapi.video.decoder.avc.secure" supports
                Media type 'video/hevc':
                  Decoder "OMX.uapi.video.decoder.hevc" supports
                  Decoder "OMX.uapi.video.decoder.hevc.secure" supports
            """)
            note = "avc 即 H.264，hevc 即 H.265。`.secure` 用于受版权保护的视频。查看全部支持的格式：`dumpsys media.player | grep '^Media type' | sort -u`；2026-10-01 的结果为 H.264、H.265、VP8、VP9、MPEG-2、MPEG-4、AVS / AVS2（国产标准）、Motion JPEG、3GPP，**不支持 AV1**。"
        }
        read("DRM（Widevine）", "dumpsys media.drm", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "Can't find service: media.drm")
            note = "本机没有该服务，旧版查询 Widevine 等级的命令无法使用。"
        }
        read("正在播放的媒体会话", "dumpsys media_session | grep -iE 'Sessions Stack|package='", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                  Sessions Stack - have 2 sessions:
                      package=org.smarttube.stable
                      package=com.android.bluetooth
            """)
        }
    }

    steps("音频") {
        read("声卡", "cat /proc/asound/cards", Host.Adb) {
            captured("2026-10-01", """
                 0 [AUDIOAIAO      ]: AUDIO-AIAO - AUDIO-AIAO
                                      AUDIO-AIAO
            """)
        }
        read("声卡的播放 / 录音通道", "cat /proc/asound/pcm", Host.Adb) {
            captured("2026-10-01", """
                00-00: audio-device0 aiao-hifi-0 :  : playback 1 : capture 1
                00-01: audio-device1 aiao-hifi-mc-1 :  : playback 1
                00-02: audio-device2 aiao-hifi-2 :  : playback 1
                00-03: audio-device3 aiao-hifi-dma-3 :  : playback 1
                00-04: audio-device4 aiao-hifi-user-4 :  : playback 1
            """)
            note = "`/dev/snd/` 下的节点属于 audio 组，shell 不在该组中，无法直接读写声卡（见「原理：SSH 与 ADB 的权限差异」）。"
        }
        read("当前输出设备", "dumpsys audio | grep -iE 'Devices:' | head -2", Host.Adb) {
            varies = true
            note = "2026-10-01 的结果为 speaker（内置扬声器）。"
        }
        read("音频输出线程", "dumpsys media.audio_flinger | grep -A 3 'Output thread' | head -4", Host.Adb) {
            varies = true
            note = "可查看采样率（48000 Hz）、是否处于待机等信息。"
        }
        read("tinymix（混音器）", "which tinymix", Host.Adb) {
            expectsError = true
            note = "系统中没有该工具。"
        }
    }

    steps("输入设备与遥控器") {
        read("所有输入设备", "dumpsys input | grep -E '^    [0-9]+: |Path:' | head -30", Host.Adb) {
            varies = true
            note = """
                2026-10-01 共有 15 个节点（event0~event14），包括：遥控器 **XGIMI RC Consumer Control（event13）** 和 XGIMI RC Keyboard（event12）、机身按键 XGIMI KEYPAD（event11）、虚拟加速度传感器 Virtual AccSensor（event10，不在 dumpsys input 列表中，需用 `getevent -i` 查看）、HW_HILINK、罗技 USB 接收器以及三个音频插孔检测节点。
            """
        }
        read("遥控器的设备信息", "timeout 3 getevent -i /dev/input/event13 | head -6", Host.Adb) {
            note = "bus 0005 表示蓝牙。（输出中的 id 为遥控器的蓝牙地址。）"
        }
        read("实时查看遥控器按键", "getevent -lt /dev/input/event13", Host.Adb) {
            manual = true
            note = "`-l` 显示按键名，`-t` 附带时间戳。按下遥控器按键即可看到事件，按 Ctrl+C 停止。shell 属于 input 组，因此可以读取。"
        }
        read("开关类状态", "timeout 2 getevent -S", Host.Adb) {
            manual = true
            note = "输出所有设备的开关状态（如耳机是否插入）后会继续等待事件，因此加上了 timeout。"
        }
    }

    steps("蓝牙") {
        read("蓝牙状态", "dumpsys bluetooth_manager | grep -E '^  (enabled|state):'", Host.Adb) {
            captured("2026-10-01", """
                  enabled: true
                  state: ON
            """)
        }
        read("已配对的设备", "dumpsys bluetooth_manager | grep -A 5 'Bonded devices'", Host.Adb) {
            varies = true
            note = "列出 MAC 地址、类型（LE 低功耗 / BR/EDR 经典 / DUAL）和名称。遥控器为 LE 设备 XGIMI RC。（包含个人设备名称，因此不在此记录。）"
        }
        change("重启蓝牙", "cmd bluetooth_manager disable && sleep 2 && cmd bluetooth_manager enable", Host.Adb) {
            note = "**遥控器会断开**，数秒后自动重新连接。遥控器失灵时可尝试此操作。"
        }
    }

    steps("USB") {
        read("USB 设备", "lsusb", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                Bus 003 Device 001: ID 1d6b:0002
                Bus 001 Device 001: ID 1d6b:0002
                Bus 001 Device 002: ID 0e8d:7663
                Bus 004 Device 001: ID 1d6b:0003
            """)
            note = """
                格式为 `厂商:产品`。1d6b 是 Linux 自身的 USB 根集线器。**0e8d:7663**：0e8d 为联发科，7663 通常对应 MT7663（Wi-Fi 与蓝牙二合一芯片），连接在内部 USB 总线上。
                若确为同一颗芯片，「投屏时遥控器卡顿」可能是 Wi-Fi 与蓝牙争用芯片所致（见「无线投屏：AirPlay、Miracast 与 DLNA」）。
            """
        }
    }

    steps("USB 模式") {
        read("USB 连接模式", "dumpsys usb | grep -iE 'mCurrentFunctions|connected'", Host.Adb) {
            expectsError = true
            note = "旧版用它查看 USB 处于充电、传输文件还是调试模式。本机无输出，推测是因为投影仪的 USB 接口只作为主机使用（连接 U 盘、键鼠），不作为设备连接电脑。"
        }
    }

    steps("看门狗") {
        read("硬件看门狗节点", "ls /dev/watchdog*", Host.Adb) {
            captured("2026-10-01", """
                /dev/watchdog
                /dev/watchdog0
            """)
            note = "系统卡死、无法定时「喂狗」时，硬件看门狗会强制重启。普通用户无需关注。"
        }
    }

    audit {
        claim("遥控器：XGIMI RC Consumer Control，实时监听遥控器导航事件流（以 event13 为例）；全机 15 个输入设备节点。", Verdict.Confirmed,
            "event13 为遥控器；event0~event14 恰好 15 个。")
        read("旧版：枚举所有输入事件设备节点与驱动名称", "getevent -S", Host.Adb) {
            verdict = Verdict.Disproved
            manual = true
            captured("（旧记录）", """
                add device 1: /dev/input/event14
                  name: "XGIMI RC Consumer Control"           # 蓝牙遥控器主按键
                add device 4: /dev/input/event11              # 机身实体电源/快捷按键
                add device 5: /dev/input/event10              # 虚拟重力加速度计（用于跌落保护与倾斜校准）
                add device 10: /dev/input/event0              # MT9669 (黄龙) SoC 内核键盘驱动
            """)
            note = """
                逐项对照实测：遥控器位于 **event13**，而非 event14（event14 为 HW_HILINK）；event11 为 XGIMI KEYPAD，**成立**；event10 为 Virtual AccSensor，**成立**（用途「跌落保护与倾斜校准」未验证）；event0 为 HL keyboard，而「MT9669」本身就是错误的芯片型号。
                另外，`getevent -S` 是输出开关状态的参数，并非列出设备；列出设备应使用 `getevent -i` 或 `dumpsys input`。
            """
        }
        read("旧版：实时捕获指定事件节点的物理扫描码与按键名", "getevent -l /dev/input/event2", Host.Adb) {
            verdict = Verdict.Disproved
            manual = true
            note = "event2 为虚拟键盘 qwerty，按遥控器不会产生输出。遥控器应使用 event13。"
        }
        read("旧版：查询 Widevine DRM 等级与厂商安全库", "dumpsys media.drm | grep -iE 'security level|vendor|description|crypto'", Host.Adb) {
            verdict = Verdict.Disproved
            captured("（旧记录）", """
                Security Level: L1   # L1 级别：硬件级安全芯片解密，允许 4K 流媒体播放
                Description: Widevine CDM
            """)
            note = "实测报 `Can't find service: media.drm`，无法用此方法查询。Widevine 是否为 L1 等级未知。"
        }
        claim("mHdrCapabilities: HdrCapabilities{mSupportedHdrTypes=[2, 3]}   # 2: HDR10, 3: HLG 模式支持；supportedModes: [{id=1, width=1920, height=1080, fps=60.0}]", Verdict.Confirmed,
            "HDR 类型 [2, 3] 一致（另有 mMaxLuminance=1000）。supportedModes 实际有多个（含 1280x720 等），而非一个。")
        read("旧版：列出底层混音控制项", "tinymix | head -n 8", Host.Adb) {
            verdict = Verdict.Disproved
            note = "系统中没有 tinymix。"
        }
        claim("""
            cat /proc/asound/cards：
             0 [MT8516 ]: MT8516 - MT8516   # 0号声卡：联发科 MT9669 SoC 内置主音频编解码芯片
             1 [Dummy ]: Dummy - Dummy ALSA Card   # 1号声卡：虚拟环回测试音频卡
        """, Verdict.Disproved, "实际只有一张声卡 `AUDIO-AIAO`，没有 MT8516，也没有 Dummy。")
        claim("Devices: speaker   # 当前主输出设备为内置扬声器（哈曼卡顿调音单元）；STREAM_MUSIC Index: 11 (range: 0-15)", Verdict.Disproved,
            "输出设备为 speaker，**成立**（「哈曼卡顿」未验证）；音量范围实测为 0~100，当前为 16，而非 0~15。")
        claim("""
            lsusb：
            Bus 001 Device 002: ID 0e8d:0616 MediaTek Inc.   # 联发科内置无线/蓝牙复合芯片
            Bus 002 Device 003: ID 0951:1666 Kingston Technology   # 金士顿外接 USB 闪存盘
        """, Verdict.Disproved, "实际为 **0e8d:7663**（联发科；「无线与蓝牙复合芯片」这一判断可能成立）；未连接金士顿 U 盘。")
        claim("mCurrentFunctions: none   # 当前未接宿主控制模式", Verdict.Disproved, "`dumpsys usb` 中查不到 mCurrentFunctions，无输出。")
        claim("通过 /sys/class/thermal/ 读取光机温度。", Verdict.Disproved, "温度区只有 cpu_thermal 和 vou_thermal。")
    }

    related("proc-metrics", "screen-cast", "focus-window")
}

val BackgroundCmds = module("background-cmds", "后台任务、唤醒、时间与其他") {
    keywords = "wakelock · deviceidle · jobscheduler · alarm · broadcasts · users · inotifyd · date · ntp · screenrecord · toybox"
    overview = """
        查看哪些程序在后台运行、系统时间是否正确，以及若干实用的小工具。
    """
    verified("2026-10-01")

    steps("后台活动") {
        read("唤醒锁（阻止系统休眠的程序）", "dumpsys power | grep -A 4 'Wake Locks: size='", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                Wake Locks: size=0

                Suspend Blockers: size=4
                  PowerManagerService.WakeLocks: ref count=0
                  PowerManagerService.Display: ref count=1
            """)
            note = "size=0 表示没有应用持有唤醒锁。待机时发热或耗电异常，应首先检查此项。"
        }
        read("省电模式白名单", "dumpsys deviceidle whitelist | head -4", Host.Adb) {
            varies = true
            note = "列表中的应用不受 Doze（低功耗模式）限制。投影仪通常接通电源，该机制影响不大。"
        }
        read("后台任务数", "dumpsys jobscheduler | grep -cE 'JOB #'", Host.Adb) {
            varies = true
            captured("2026-10-01", "22")
            note = "应用登记的定时或条件触发任务。去掉 `-c` 可查看具体的应用。"
        }
        read("闹钟排行（唤醒次数最多的程序）", "dumpsys alarm | grep -A3 'Top Alarms:'", Host.Adb) {
            varies = true
            note = "排名靠前的触发次数最多。2026-10-01 的结果均为系统自身的闹钟（如网络统计）。"
        }
        read("登记开机自启的数量", "dumpsys activity broadcasts | grep -c BOOT_COMPLETED", Host.Adb) {
            varies = true
            captured("2026-10-01", "8")
            note = "去掉 `-c` 并改为 `grep -A 4`，可查看具体是哪些。"
        }
        read("当前通知数", "dumpsys notification --noredact | grep -c NotificationRecord", Host.Adb) {
            varies = true
            captured("2026-10-01", "13")
        }
    }

    steps("用户") {
        read("系统里的用户", "pm list users", Host.Adb) {
            captured("2026-10-01", """
                Users:
                	UserInfo{0:机主:c13} running
            """)
            note = "只有一个用户 0，因此 `--user 0` 即指本机。"
        }
    }

    steps("时间") {
        read("当前时间、时区与对时设置", "date; getprop persist.sys.timezone; settings get global auto_time; settings get global ntp_server", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                Thu Oct  1 20:16:53 CST 2026
                Asia/Shanghai
                1
                null
            """)
            note = "auto_time=1 表示自动联网对时；ntp_server 为 null 表示使用系统默认的时间服务器。"
        }
        read("是否有硬件时钟（RTC）", "ls /dev/rtc*", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "ls: /dev/rtc*: No such file or directory")
            note = "**没有 RTC**：断电后无法保存时间，开机后需要联网才能校准。无网络时时间可能有误，HTTPS 也可能因此报证书错误。"
        }
        change("用 Deck 的时间设置投影仪", "adb shell \"date \$(date +%m%d%H%M%Y.%S)\"", Host.Deck) {
            note = "`${'$'}(...)` 在 Deck 上展开为当前时间后再发送。**未测试**：shell 没有 CAP_SYS_TIME 能力，`date` 预计会失败（见「原理：SSH 与 ADB 的权限差异」）。"
        }
        change("指定局域网内的 NTP 服务器", "settings put global ntp_server 192.168.0.1", Host.Adb) {
            note = "仅在路由器提供 NTP 服务时有效。恢复：`settings delete global ntp_server`。"
        }
    }

    steps("小工具") {
        read("toybox 自带的命令", "toybox | head -3", Host.Adb) {
            note = "安卓自带的精简命令集。功能不足时可使用 BusyBox，见「部署 BusyBox：补充 396 个 Linux 命令」。"
        }
        read("监听目录变化", "inotifyd - /data/local/tmp", Host.Adb) {
            manual = true
            note = "目录中有文件被创建、修改或删除时输出事件，按 Ctrl+C 停止。可用于部署时确认文件是否已写入。"
        }
        change("录屏", "screenrecord --time-limit 10 /sdcard/demo.mp4\nadb pull /sdcard/demo.mp4 ./", Host.Adb) {
            note = "最长 180 秒。第二行在 Deck 上执行，用于取回视频。受版权保护的画面录制结果为黑屏。"
        }
        danger("重启 / 关机 / 进入 Recovery", "reboot\nreboot -p\nreboot recovery", Host.Adb) {
            note = "`-p` 表示关机。实测重启后 ADB 会自动运行；自行部署的服务需要重新启动，SimpleSSHD 需要在电视上手动点击 Start。（2026-10-02 起 hub 与 keymap 开机自动启动，SimpleSSHD 已卸载，见「开机自动启动 hub 与 keymap」。）"
        }
    }

    audit {
        claim("极米 Z6X Pro 内部未配备 RTC 硬件时钟电池，完全依赖断电前保存的时间戳与开机网络 NTP。断网关机拔掉电源后，重新上电系统时间会回退至 Linux 内核编译基准时间（1970 年或出厂固件打包时间）。时间错乱会导致 HTTPS/SSL 握手证书直接校验失败。", Verdict.Unverified,
            "没有 /dev/rtc* 设备，「未配备 RTC」成立。「回退到 1970 年」和证书报错需要断电且断网才能验证，未测试。")
        claim("date -s：修改系统时间需要 CAP_SYS_TIME 能力。SSH（UID 10068）调用报 date: settimeofday: Operation not permitted；ADB（UID 2000）同样受限，但可通过 settings put global 触发系统的自动同步逻辑。", Verdict.Unverified,
            "未测试修改时间（会影响证书校验和定时任务）。shell 的能力集为空，因此 ADB 同样无法修改，这一点与旧记录一致。")
        change("旧版：实时监听 /data/local/tmp 目录下的写入与创建事件", "toybox inotifyd - /data/local/tmp 2>/dev/null", Host.Adb) {
            verdict = Verdict.Confirmed
            manual = true
            note = "toybox 中包含 inotifyd，系统也直接提供 `inotifyd` 命令，两种写法均可。"
        }
    }

    related("dumpsys-settings", "process-memory", "busybox")
}
