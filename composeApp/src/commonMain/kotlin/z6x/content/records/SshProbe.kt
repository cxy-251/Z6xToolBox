package z6x.content.records

import z6x.framework.Host
import z6x.framework.module

val SshProbe = module("ssh-probe", "SSH 连上后摸清硬件") {
    keywords = "SimpleSSHD · getprop · /proc · df · wm"
    overview = """
        用 U 盘装上 SimpleSSHD 后，第一次有了命令行。虽然只是普通 App 权限，但查询类命令几乎都能用，先把这台机器摸了个底。
    """
    verified("2026-10-01")

    story("经过") {
        text("""
            SimpleSSHD 在电视上启动一个 SSH 服务（端口 2222），Deck 用 `ssh` 登录进去，就得到一个 Linux shell。
            这个 shell 的身份是 SimpleSSHD 这个 App（**uid 10068**）。系统属性、/proc、磁盘用量这些对所有 App 都开放读取，所以能查清大部分硬件信息。
        """)
    }

    steps("在 SSH 里查到的信息") {
        read("CPU 架构", "getprop ro.product.cpu.abi; uname -m", Host.Ssh) {
            captured("2026-10-01", """
                armeabi-v7a
                armv8l
            """)
            note = """
                `armeabi-v7a`：系统只提供 **32 位** ARM 的运行库，App 里的原生库必须有 32 位版本。
                `armv8l`：CPU 是 64 位 ARMv8，但当前进程以 32 位兼容模式运行（l = little-endian 32 位）。
            """
        }
        read("芯片平台", "getprop ro.board.platform", Host.Ssh) {
            captured("2026-10-01", "huanglong")
            note = "这是平台代号。具体芯片型号要交叉验证，见「案例：查出真实型号和芯片」。"
        }
        read("Android 版本", "getprop ro.build.version.release; getprop ro.build.version.sdk", Host.Ssh) {
            captured("2026-10-01", """
                12
                31
            """)
            note = "Android 12，API 级别 31。以后写安卓 App 时 minSdk 不能高于 31。"
        }
        read("内存", "grep -E 'MemTotal|MemAvailable' /proc/meminfo", Host.Ssh) {
            varies = true
            captured("2026-10-01", """
                MemTotal:        3630528 kB
                MemAvailable:    1501876 kB
            """)
            note = "标称 4G，系统可用 3.5G（其余被显示、视频解码等硬件预留）。MemAvailable 是当前还能分给程序用的量，每次都不一样。"
        }
        read("存储", "df -h /data", Host.Ssh) {
            varies = true
            captured("2026-10-01", """
                Filesystem            Size Used Avail Use% Mounted on
                /dev/block/mmcblk0p53  50G 3.5G   46G   8% /data/user/0
            """)
            note = "/data 是存放 App 和用户数据的分区，共 50G。"
        }
        read("屏幕分辨率和密度", "wm size; wm density", Host.Ssh) {
            captured("2026-10-01", """
                Physical size: 1920x1080
                Physical density: 240
            """)
            note = "1080p，240 dpi。手机 App 按这个密度排版会显得偏小或错位，TV 版 App 才会针对大屏优化。"
        }
        read("投影仪的局域网 IP", "ip -4 addr show wlan0 | grep inet", Host.Ssh) {
            captured("2026-10-01", "    inet 192.168.0.109/24 brd 192.168.0.255 scope global wlan0")
        }
        read("已安装的应用数", "pm list packages | wc -l", Host.Ssh) {
            varies = true
            captured("2026-10-01", "111")
            note = "列包名是查询，App 权限也能执行。111 是精简之后的数量。"
        }
    }

    verify("这台机器的底") {
        facts(
            "型号" to "XGIMI Z6X Pro 三色激光 旗舰版（G0073）",
            "芯片" to "海思 Hi3751V660，8 核 Cortex-A55",
            "系统" to "Android 12（API 31），内核 5.10.43",
            "架构" to "64 位内核 + **32 位用户空间**（armeabi-v7a）",
            "内存 / 存储" to "3.5G 可用 / /data 50G",
            "显示" to "1920x1080，240 dpi",
            "SELinux" to "**Permissive**（宽容模式）",
        )
    }

    lesson("核对旧记录时发现的问题") {
        text("""
            • 旧版写的芯片（联发科 MT9669、4 核 A73）和内核（5.4.180）是错的，已按实测改正。
            • 旧版"总包数 183"不对：加上已卸载的也只有 113 个。"极米内置包 57 个"是对的（现在装着 55 个，加已卸载的 2 个）。
            • 旧版把"网络 ADB 默认开放"写进了这一篇，与事实不符，见「在 SSH 里强开网络 ADB」。
            • 本页命令都在 2026-10-01 通过 SSH（uid 10068）实际跑过。
        """)
    }

    related("ssh-permission-wall", "find-real-model", "ssh-key-login")
}

val SshPermissionWall = module("ssh-permission-wall", "SSH 能查不能改：撞上权限墙") {
    keywords = "uid 10068 · SecurityException · am · pm"
    overview = """
        想在 SSH 里直接停用、卸载预装应用，全被系统拒绝。这一页用实验弄清楚：被谁拒、为什么拒，以及为什么非得拿到 ADB。
    """
    verified("2026-10-01")

    why {
        text("""
            安卓用 **uid** 区分"你是谁"。每个 App 一个 uid（SimpleSSHD 是 10068），ADB 的 shell 是 2000，系统是 1000，root 是 0。
            `pm`、`am` 这些命令本身只是"传话的"，真正干活的是系统服务（PackageManagerService 等）。服务会检查**调用者的 uid** 有没有对应权限，普通 App 没有停用、卸载其他应用的权限。
        """)
    }

    story("实验 1：am start 被拒") {
        change("用 am 启动一个界面", "am start -n com.example.z6x.notexist/.Main", Host.Ssh) {
            expectsError = true
            captured("2026-10-01", """
                Starting: Intent { cmp=com.example.z6x.notexist/.Main }

                Exception occurred while executing 'start':
                java.lang.SecurityException: Permission Denial: package=com.android.shell does not belong to uid=10068
            """)
            note = """
                目标是个**不存在的包**，确保即使被放行也不会真的打开什么。
                报错的意思：`am` 工具对系统声称"我是 com.android.shell"，系统一查调用者是 uid 10068，对不上，直接拒绝。还没走到"目标存不存在"那一步。
            """
        }
    }

    story("实验 2：pm 停用应用，第一次没测出来") {
        text("想测 `pm disable-user` 会不会被拒。为了安全，先拿不存在的包名试：")
        change("对不存在的包执行停用", "pm disable-user --user 0 com.example.z6x.notexist", Host.Ssh) {
            expectsError = true
            captured("2026-10-01", """
                Exception occurred while executing 'disable-user':
                java.lang.IllegalArgumentException: Unknown package: com.example.z6x.notexist
            """)
            note = "系统**先检查包存不存在**，直接报 Unknown package，根本没走到权限检查。这个实验什么也没证明。"
        }
        text("""
            **换方案：** 必须用真实存在的包，但又不能真的改变它。
            办法是对一个**已经停用**的应用再执行一次停用。就算权限放行，它本来就是停用的，状态也不会变。
            先用 ADB 确认豆瓣 FM（com.xgimi.doubanfm）当前是停用状态：
        """)
        read("确认豆瓣 FM 已停用", "dumpsys package com.xgimi.doubanfm 2>/dev/null | grep -m1 -oE 'enabled=[0-9]'", Host.Adb) {
            captured("2026-10-01", "enabled=3")
            note = "`enabled=0` 默认（启用），`2` 被停用，`3` 被用户停用（pm disable-user 的结果）。`-m1` 只取第一个匹配，`-o` 只输出匹配的部分。grep 拿到结果就退出，dumpsys 还在往管道里写，会报 Broken pipe，所以用 `2>/dev/null` 丢掉错误输出。"
        }
        change("SSH 里对它再停用一次", "pm disable-user --user 0 com.xgimi.doubanfm", Host.Ssh) {
            expectsError = true
            varies = true
            captured("2026-10-01", """
                Exception occurred while executing 'disable-user':
                java.lang.SecurityException: Attempt to change component state; pid=16593, uid=10068, package=com.xgimi.doubanfm
            """)
            note = "这次走到了权限检查：uid 10068 无权改变其他应用的启用状态。事后再查，豆瓣 FM 仍是 enabled=3，没有任何变化。"
        }
    }

    story("实验 3：pm uninstall，没法安全地测") {
        danger("对不存在的包执行卸载", "pm uninstall -k --user 0 com.example.z6x.notexist", Host.Ssh) {
            expectsError = true
            captured("2026-10-01", "Failure [not installed for 0]")
            note = "和实验 2 一样，先检查了包在不在，测不到权限。"
        }
        text("""
            卸载没有"对已卸载的再卸一次"这种无害的做法：对真实应用测试，万一放行就真的卸掉了。所以**这条不做实验**。
            旧版记录里那段带行号的 NullPointerException 堆栈无法核实，已删除。能确定的是：停用都被拒，卸载更不可能放行。
        """)
    }

    verify("结论") {
        facts(
            "SSH（uid 10068）" to "查询：可以 · 启动其他应用界面：拒绝 · 停用应用：拒绝",
            "ADB（uid 2000）" to "以上全部可以。深度定制里停用的 29 个组件都是用 ADB 做的",
            "所以" to "要接管系统，必须拿到 ADB，见「在 SSH 里强开网络 ADB」",
        )
    }

    lesson("经验：怎么安全地做权限实验") {
        text("""
            • 用**不存在的目标**做实验最安全，但要注意系统可能先检查目标存不存在，根本走不到你想测的那一步（实验 2、3）。
            • 想测"会不会被拒"又不想改变状态，可以找一个**已经处于目标状态**的对象（对已停用的再停用）。
            • 动手前、动手后都用另一个通道（这里是 ADB）确认状态，证明实验没有副作用。
            • 测不了的就说测不了，不要编一个"预期输出"。
        """)
    }

    related("ssh-probe", "force-adb", "ssh-key-login")
}
