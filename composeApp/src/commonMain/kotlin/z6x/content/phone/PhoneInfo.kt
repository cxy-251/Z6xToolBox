package z6x.content.phone

import z6x.framework.Host
import z6x.framework.module

/*
 * 手机（Redmi Note 12 Turbo）的设备信息与参数获取。所有输出均为 2026-10-02 实测。
 * 不记录序列号、硬件地址、Wi-Fi 名称和公网 IPv6 地址。
 */
val PhoneFacts = module("phone-facts", "设备概况：Redmi Note 12 Turbo") {
    keywords = "marble · 骁龙 7+ Gen 2 · HyperOS 3 · Android 15 · 两个空间"
    overview = """
        用户的手机，作为 z6x-hub 的资源库设备（网页游戏、漫画、短视频），并按开发设备改造：Termux、SSH、精简预装。
        与投影仪相比，它的系统更新、权限限制更严格（SELinux 为 Enforcing），Bootloader 锁定且**不解锁**：解锁会清空数据，并使支付、银行类应用失效。
    """
    verified("2026-10-02")

    why("概况") {
        facts(
            "型号" to "Redmi Note 12 Turbo（国行，23049RAD8C），代号 marble，与海外的 POCO F5 为同一硬件",
            "芯片" to "高通 SM7475（骁龙 7+ Gen 2），arm64",
            "内存与存储" to "16GB 内存（另有 12GB 内存扩展，即用闪存模拟的交换空间）；机身存储约 939GB",
            "系统" to "HyperOS 3（OS3.0.5.0.VMRCNXM），Android 15，内核 5.10",
            "安全状态" to "SELinux Enforcing；验证启动 green，Bootloader 锁定",
            "用户空间" to "两个：主空间（用户 0，机主）与第二空间（用户 10）。两者的应用、数据、默认应用相互独立，没有第三个空间，也没有应用双开所用的用户 999",
            "屏幕" to "1080 × 2400，密度 440",
            "网络" to "IP 192.168.0.104，Wi-Fi 当前为 2.4GHz（11 信道）",
        )
    }

    consequences("与投影仪的主要区别") {
        text("""
            • **ADB 不常开**：只能使用无线调试，端口每次开启都不同；Wi-Fi 断开、切换空间、重启都会使其关闭（见「无线调试为何自动关闭」）。
            • **SELinux 为 Enforcing**：但 hub 的全部功能仍可正常工作。
            • **HyperOS 额外限制**：shell 不能停用系统应用；通过 ADB 安装应用须开启「USB 安装」并在手机上逐次确认；模拟按键与授予权限须开启「USB 调试（安全设置）」。
            • **随身携带**：会连接其他 Wi-Fi 和移动数据网络，hub 必须限定只在家里的 Wi-Fi 上服务。
        """)
    }
    related("phone-params", "phone-adb", "phone-hub")
}

val PhoneParams = module("phone-params", "参数获取：常用查询命令") {
    keywords = "getprop · dumpsys battery · meminfo · df · wm · role"
    overview = """
        通过 ADB 读取手机的型号、系统、电池、内存、存储、屏幕和默认应用。全部为只读命令，`./run.sh --try-read` 可在实机上逐条核对。
    """
    verified("2026-10-02")

    steps("系统与硬件") {
        read("型号与销售名称", "getprop ro.product.model; getprop ro.product.marketname", Host.PhoneAdb) {
            captured("2026-10-02", "23049RAD8C\nRedmi Note 12 Turbo")
        }
        read("芯片", "getprop ro.soc.model", Host.PhoneAdb) { captured("2026-10-02", "SM7475") }
        read("系统版本", "getprop ro.mi.os.version.name; getprop ro.build.version.incremental; getprop ro.build.version.release", Host.PhoneAdb) {
            captured("2026-10-02", "OS3.0\nOS3.0.5.0.VMRCNXM\n15")
            note = "依次为 HyperOS 大版本、完整版本号、Android 版本。系统更新后会变化。"
        }
        read("安全状态", "getenforce; getprop ro.boot.verifiedbootstate", Host.PhoneAdb) {
            captured("2026-10-02", "Enforcing\ngreen")
            note = "green 表示验证启动通过、Bootloader 锁定。解锁后会变为 orange。"
        }
        read("用户空间", "pm list users", Host.PhoneAdb) {
            captured("2026-10-02", "Users:\n\tUserInfo{0:机主:4c13} running\n\tUserInfo{10:security space:413} running")
            note = "10 即第二空间。对它执行的命令都要加 `--user 10`，否则只作用于主空间或全部用户。"
        }
        read("屏幕", "wm size; wm density", Host.PhoneAdb) {
            captured("2026-10-02", "Physical size: 1080x2400\nPhysical density: 440")
        }
    }

    steps("资源与状态") {
        read("电池", "dumpsys battery | grep -E 'level|temperature|health|status|voltage'", Host.PhoneAdb) {
            varies = true
            captured("2026-10-02", "  Max charging voltage: 0\n  status: 3\n  health: 2\n  level: 51\n  voltage: 3847\n  temperature: 307")
            note = "level 为电量百分比；temperature 单位为 0.1 ℃（307 即 30.7 ℃）；voltage 单位为 mV；status 3 表示未充电，2 为充电中；health 2 表示良好。"
        }
        read("内存与交换空间", "grep -E 'MemTotal|MemAvailable|SwapTotal' /proc/meminfo", Host.PhoneAdb) {
            varies = true
            captured("2026-10-02", "MemTotal:       15515720 kB\nMemAvailable:    6472540 kB\nSwapTotal:      12582908 kB")
            note = "SwapTotal 约 12GB，来自 HyperOS 的「内存扩展」（用闪存模拟内存）。16GB 内存通常用不到，关闭可减少闪存写入：设置 → 更多设置 → 内存扩展（需用户在手机上操作）。"
        }
        read("存储空间", "df -h /data | tail -1", Host.PhoneAdb) {
            varies = true
            captured("2026-10-02", "/dev/block/dm-60 939G 561G  378G  60% /storage/emulated/0/Android/obb")
            note = "显示的挂载点是 obb 目录，但统计的是整个 /data 分区。"
        }
        read("温区数量", "cat /sys/class/thermal/thermal_zone*/type | wc -l", Host.PhoneAdb) {
            captured("2026-10-02", "83")
            note = "其中一部分不是真实温度（如 bcl-warn 读数为 -273），hub 的系统状态模块会过滤 -40～150 ℃ 以外的读数。"
        }
    }

    steps("默认应用与开关") {
        read("默认浏览器", "cmd role get-role-holders android.app.role.BROWSER", Host.PhoneAdb) {
            captured("2026-10-02", "com.microsoft.emmx")
            note = "主空间为 Edge；第二空间加 `--user 10` 查询，为 Chrome。"
        }
        read("当前输入法", "settings get secure default_input_method", Host.PhoneAdb) {
            captured("2026-10-02", "com.sohu.inputmethod.sogou.xiaomi/.SogouIME")
        }
        read("「USB 安装」是否开启", "getprop persist.security.adbinstall", Host.PhoneAdb) {
            captured("2026-10-02", "1")
            note = "1 为开启。「USB 调试（安全设置）」对应 `persist.security.adbinput`。"
        }
        read("主空间的第三方应用数量", "pm list packages -3 --user 0 | wc -l", Host.PhoneAdb) {
            varies = true
            captured("2026-10-02", "143")
        }
    }
    related("phone-facts", "phone-adb")
}
