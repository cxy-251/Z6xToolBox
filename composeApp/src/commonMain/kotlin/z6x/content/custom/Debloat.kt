package z6x.content.custom

import z6x.framework.Host
import z6x.framework.SectionBuilder
import z6x.framework.module

val DebloatMethod = module("debloat-method", "精简预装应用：停用还是卸载") {
    keywords = "pm disable-user · pm uninstall --user 0 · install-existing"
    overview = """
        有了 ADB（shell 身份）就能处理预装应用了。这台机器一共处理了 31 个：29 个**停用**，2 个**对当前用户卸载**。两种都能随时恢复，不需要刷机。
    """
    verified("2026-10-01")

    why {
        text("""
            预装应用装在只读的系统分区里，shell 身份删不掉 APK 文件本身，只能改"这个用户能不能用它"：
            • **停用** `pm disable-user --user 0 包名`：应用还在、数据还在，只是不能启动，开机也不会运行。启动器里不再显示。
            • **对当前用户卸载** `pm uninstall -k --user 0 包名`：从用户 0（唯一的用户）里移除，系统里看起来就像没装过；`-k` 保留数据。APK 仍在系统分区，可以装回来。
            • 两者都**不释放系统分区空间**，省下的是内存、CPU 和后台联网。
        """)
        text("""
            **为什么大部分用停用：** 停用最容易恢复（`pm enable`），状态也一目了然（`pm list packages -d`）。
            **为什么桌面和视频推荐用卸载：** 官方桌面停用后，「儿童模式」等它内部的入口还会出现在 Activity Launcher 列表里，点了报错；卸载后连这些残留入口也一起消失。
        """)
    }

    steps("查看当前状态") {
        read("被停用的包", "pm list packages -d", Host.Adb) {
            varies = true
            note = "`-d` 只列出被停用的。现在是 29 个，全部是 com.xgimi.*，详见「停用清单」。"
        }
        read("数一下", "pm list packages -d | wc -l", Host.Adb) {
            captured("2026-10-01", "29")
        }
        read("对当前用户卸载了哪些", "pm list packages -u | grep -vxF \"\$(pm list packages)\"", Host.Adb) {
            captured("2026-10-01", """
                package:com.xgimi.home
                package:com.xgimi.stream.video
            """)
            note = """
                `-u` 列出包括已卸载在内的所有包，不带 `-u` 只列当前装着的。两者的差就是被卸载的。
                `grep -vxF "${'$'}(...)"`：`${'$'}(...)` 把当前装着的列表当成一组匹配模式，`-F` 按纯文本匹配，`-x` 整行匹配，`-v` 反选，剩下的就是差集。
            """
        }
        read("查某个包的状态", "dumpsys package com.xgimi.doubanfm 2>/dev/null | grep -m1 -oE 'enabled=[0-9]'", Host.Adb) {
            captured("2026-10-01", "enabled=3")
            note = "0 默认（启用）、1 启用、2 停用、3 被用户停用（disable-user）、4 用到时才启用。"
        }
    }

    steps("停用、卸载与恢复") {
        change("停用", "pm disable-user --user 0 com.xgimi.doubanfm", Host.Adb) {
            outcome = "输出 `Package com.xgimi.doubanfm new state: disabled-user`，正在运行的进程会被结束。"
        }
        change("恢复停用的", "pm enable com.xgimi.doubanfm", Host.Adb) {
            outcome = "输出 `… new state: enabled`。"
        }
        danger("对当前用户卸载", "pm uninstall -k --user 0 com.xgimi.home", Host.Adb) {
            note = "标为高危是因为它从用户视角彻底移除应用。被移除的若是系统运行必需的组件，可能导致开机异常；处理前先确认它是做什么的。"
            outcome = "输出 Success。"
        }
        change("把卸载的装回来", "cmd package install-existing com.xgimi.home", Host.Adb) {
            note = "从系统分区里把这个应用重新给用户 0 装上，不需要 APK 文件。"
            outcome = "输出 `Package com.xgimi.home installed for user: 0`。"
        }
    }

    consequences {
        text("""
            • 停用和卸载都会在**重启后保持**。
            • **系统 OTA 升级**可能把它们恢复，甚至改掉 SELinux 模式让 ADB 打不开。所以 OTA 也被停用了，见「停用清单」。
            • **恢复出厂设置**会清掉这些状态，一切回到原样。
            • 不要碰没把握的包：系统 UI、输入、网络、蓝牙之类的一旦停用，可能连遥控器都没法用。最坏的情况只能靠 ADB 恢复，所以要确保 ADB 能连上。
        """)
    }

    related("debloat-list", "debloat-scripts", "projectivy-launcher")
}

/** 停用清单里的一组：标题、说明、包名和各自用途。 */
private fun SectionBuilder.group(packages: List<Pair<String, String>>) {
    facts(*packages.toTypedArray())
    change(
        "停用这一组",
        packages.joinToString("\n") { "pm disable-user --user 0 ${it.first}" },
        Host.Adb,
    ) { note = "可以整段粘贴进 adb shell，一行一个。恢复把 `disable-user --user 0` 换成 `enable`。" }
}

val DebloatList = module("debloat-list", "停用清单：31 个预装组件") {
    keywords = "广告 · 上报 · IoT · OTA · 皮肤 · 伴生服务"
    overview = """
        按用途分组列出处理过的 31 个极米组件。用途是根据包名、应用名和停用后的变化判断的，没有反编译确认；标「推测」的是把握不大的。
    """
    verified("2026-10-01")

    story("广告与数据上报") {
        group(listOf(
            "com.xgimi.adservice" to "广告服务（开机广告、推荐位广告）",
            "com.xgimi.datareporter" to "使用数据上报",
            "com.xgimi.bugreportsender" to "错误报告上传",
        ))
    }
    story("冗余内容") {
        group(listOf(
            "com.xgimi.doubanfm" to "豆瓣 FM",
            "com.xgimi.agilewall" to "动态壁纸 / 灵动墙（推测）",
            "com.xgimi.atmosphere" to "氛围模式（推测）",
            "com.xgimi.instruction30" to "电子说明书",
            "com.xgimi.bootwizard" to "开机向导（只在首次开机用）",
            "com.xgimi.payview" to "会员付费界面",
        ))
    }
    story("推荐、推送与应用市场") {
        group(listOf(
            "com.xgimi.msgcenter" to "消息中心（推送弹窗）",
            "com.xgimi.newappmarket" to "极米应用市场",
        ))
        text("另外 `com.xgimi.stream.video`（影视聚合推荐）是**卸载**而不是停用，见「换掉官方桌面」。")
    }
    story("桌面主题与屏保") {
        group(listOf(
            "com.xgimi.screensaver" to "屏保",
            "com.xgimi.skinmanager" to "桌面皮肤管理",
            "com.xgimi.skinconfig" to "桌面皮肤配置",
            "com.xgimi.skin.classicblue" to "皮肤：经典蓝",
            "com.xgimi.skin.black" to "皮肤：黑",
            "com.xgimi.skin.lightblue" to "皮肤：浅蓝",
        ))
        text("官方桌面 `com.xgimi.home` 本身是**卸载**的，见「换掉官方桌面」。")
    }
    story("系统升级") {
        group(listOf(
            "com.xgimi.upgrade" to "系统 OTA 升级",
            "com.xgimi.ota.accessories" to "配件（遥控器等）固件升级",
        ))
        text("""
            **为什么要停 OTA：** 升级可能把停用的应用恢复、把 SELinux 改回 Enforcing（强开 ADB 就失效了），甚至修掉 adbd 的预设配置。
            代价是不再收到官方的修复和新功能。想升级时先 `pm enable` 这两个，升级后检查 ADB 是否还能用。
        """)
    }
    story("智能家居与伴生设备") {
        group(listOf(
            "com.xgimi.xgimihilink" to "华为 HiLink 智能家居",
            "com.xgimi.xgimiiotserver" to "极米 IoT 服务",
            "com.xgimi.iot" to "极米 IoT",
            "com.xgimi.vcontrol" to "语音控制 / 米家联动（推测）",
            "com.xgimi.smartconnect" to "多设备互联",
            "com.xgimi.mobilebridgeservice" to "手机 App 遥控桥接",
            "com.xgimi.smartaccessories" to "智能配件管理",
            "com.xgimi.mateservice" to "伴生外设服务（推测）",
            "com.xgimi.user" to "极米账号与会员",
            "com.xgimi.soundermodeservice" to "音箱模式（推测）",
        ))
        text("这一组以后如果要用极米手机 App 遥控、蓝牙音箱模式或智能家居联动，需要恢复对应的包。")
    }

    verify {
        read("核对总数", "pm list packages -d | grep -c xgimi", Host.Adb) {
            captured("2026-10-01", "29")
            note = "29 个停用加上 2 个卸载（home、stream.video），共 31 个。"
        }
    }

    lesson("核对旧记录时发现的问题") {
        text("""
            • 旧版分了 8 篇，每篇停用三五个包，格式重复，这里合并成一份清单。
            • 旧版写的"释放约 320MB 内存""hilink 累计占用 CPU 近 20 分钟"无法复核（这些包早已停用），已删除。
            • 旧版说部分组件"以 system 身份运行、带 persistent 标记，重启后才生效"，未核实，已删除。实际上 disable-user 会立即结束进程。
        """)
    }

    related("debloat-method", "debloat-scripts", "projectivy-launcher")
}
