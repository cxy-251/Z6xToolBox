package z6x.content.records

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val SshProbe = module("ssh-probe", "通过 SSH 获取硬件与系统信息") {
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
            note = "这是平台代号。具体芯片型号要交叉验证，见「核查：真实型号与芯片」。"
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
        read("品牌和固件版本号", "getprop ro.product.brand; getprop ro.build.display.id", Host.Ssh) {
            captured("2026-10-01", """
                XGIMI
                tv_hi3751v660 HuanglongV200R006C00SPC009B020
            """)
            note = "固件版本号里也带着芯片型号 hi3751v660。序列号 `getprop ro.serialno` 属于个人设备信息，不记录。"
        }
        read("系统预装的极米包", "pm list packages -s | grep -c xgimi", Host.Ssh) {
            captured("2026-10-01", "55")
            note = "`-s` 只列系统预装的。现在 55 个，加上对当前用户卸载的 2 个，出厂时是 57 个。"
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

    audit("旧记录核对（本页命令都在 2026-10-01 通过 SSH 实际跑过）") {
        claim("芯片平台：联发科 MT9669（开发代号 huanglong），4 核 Cortex-A73 处理器", Verdict.Disproved,
            "海思 Hi3751V660，8 核 Cortex-A55（`ro.product.product.name` = tv_hi3751v660，`/proc/cpuinfo` CPU part 0xd05 ×8）。huanglong 是海思平台代号。")
        claim("架构分离：Linux 5.4.180 内核为 64 位（armv8l），用户空间系统库裁剪为 32 位（armeabi-v7a）", Verdict.Disproved,
            "64 位内核 + 32 位用户空间**成立**；但内核版本是 **5.10.43**，不是 5.4.180。")
        claim("系统版本：Android 12，API 级别 31", Verdict.Confirmed, "`12` / `31`。")
        claim("物理内存：总内存 3.5 GB（3630528 kB），空闲可用约 1.8 GB", Verdict.Confirmed,
            "MemTotal 3630528 kB 一致；可用量随使用变化，实测 1.4~1.6 GB。")
        claim("存储空间：/data 分区总容量 50 GB，系统预装后剩余 48 GB", Verdict.Confirmed, "50G，当前剩余 46G（已用 3.5G）。")
        claim("光机分辨率：0.33 英寸 DMD 芯片，物理点对点 1920x1080@60Hz，屏幕密度 240 DPI", Verdict.Unverified,
            "1920x1080、240 dpi 已实测；DMD 芯片尺寸命令行查不到。")
        claim("调试接口：网络 ADB 端口 5555（默认开放且无需授权指纹，uid=2000）", Verdict.Disproved,
            "端口 5555 和免授权是**预设**好的，但 adbd 默认**不运行**，要从 SSH 手动拉起。见「通过 SSH 启动网络 ADB」。")
        claim("getprop ro.product.model 输出 `Z6X Pro`；ro.build.display.id 输出 `GMUI_...`", Verdict.Disproved,
            "ro.product.model 是 `XGIMI TV`；display.id 是 `tv_hi3751v660 HuanglongV200R006C00SPC009B020`。型号在 `xgimi.bt.name` 里。")
        claim("总包数: 183；极米内置包数: 57", Verdict.Disproved,
            "总包数 111（加上对当前用户卸载的也只有 113）。极米预装 57 **成立**：现在 55 个，加卸载的 home、stream.video 共 57。")
        claim("为什么查询命令能执行：/proc/meminfo、getprop、df 对所有普通应用开放只读权限；为什么修改与删除执行不了：调用系统特权服务时会校验 UID 是否为 0 或 2000，普通 UID 10068 会被拦截。", Verdict.Confirmed,
            "查询全部可用；修改被拒见「SSH 的权限边界」的实验。")
    }

    related("ssh-permission-wall", "find-real-model", "ssh-key-login")
}

val SshPermissionWall = module("ssh-permission-wall", "SSH 的权限边界：可查询，不可修改") {
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
            卸载没有"对已卸载的再卸一次"这种无害的做法：对真实应用测试，万一放行就真的卸掉了。所以**这条不做实验**。能确定的是：停用都被拒，卸载更不可能放行。
        """)
    }

    audit {
        danger("旧版：在 SSH 中执行卸载自带应用命令", "pm uninstall -k --user 0 com.xgimi.minitvfactory", Host.Ssh) {
            verdict = Verdict.Unverified
            captured("（旧记录）", """
                Exception occurred while dumping:
                java.lang.NullPointerException: Attempt to invoke virtual method 'int java.lang.String.length()' on a null object reference
                  at com.android.server.appop.AppOpsService.checkPackage(AppOpsService.java:3212)
                  at com.android.server.pm.PackageInstallerService.uninstall(PackageInstallerService.java:1011)
            """)
            note = "上面是旧记录写的输出，**没有复现**：对真实应用测试有被真的卸掉的风险。另外，实验 2 实测 pm 出错时开头是 `Exception occurred while executing '命令名':`，而旧记录写的是 `while dumping`（那是 dumpsys 出错时的说法），这段输出很可能不是原样记录。"
        }
        claim("报错根因: SimpleSSHD 运行在普通沙箱（UID 10068），不是 root 也不是 shell；Android 12 校验调用方包名为空，直接抛出空指针异常崩溃", Verdict.Unverified,
            "uid 10068 **成立**；「包名为空导致空指针」没有复现，无法确认。")
        claim("am start 报错：java.lang.SecurityException: Permission Denial: start at ... from pid=... uid=10068 not allowed；根因：am 脚本内部指定调用者身份为 com.android.shell (UID 2000)，系统校验 SimpleSSHD 的真实 UID 10068 与声明的 2000 不一致，强制拒绝", Verdict.Confirmed,
            "原因**成立**。实际报错原文是 `Permission Denial: package=com.android.shell does not belong to uid=10068`，旧记录的措辞不是原样。")
    }

    verify("结论") {
        facts(
            "SSH（uid 10068）" to "查询：可以 · 启动其他应用界面：拒绝 · 停用应用：拒绝",
            "ADB（uid 2000）" to "以上全部可以。深度定制里停用的 29 个组件都是用 ADB 做的",
            "所以" to "要接管系统，必须拿到 ADB，见「通过 SSH 启动网络 ADB」",
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
