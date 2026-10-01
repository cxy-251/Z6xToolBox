package z6x.content.custom

import z6x.framework.Host
import z6x.framework.SectionBuilder
import z6x.framework.Verdict
import z6x.framework.module

val DebloatMethod = module("debloat-method", "精简预装应用：停用与卸载的取舍") {
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
            outcome = "输出 `Package com.xgimi.doubanfm new state: disabled-user`。普通应用的进程会被结束；但带 **PERSISTENT** 标记的系统组件，实测停用后进程**还在运行**（见「停用清单」）。"
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
        change("立刻结束正在运行的升级进程", "am force-stop com.xgimi.upgrade; am force-stop com.xgimi.ota.accessories", Host.Adb) {
            note = "disable-user 本身就会结束进程，这一步是保险。force-stop 只是结束当前进程，不阻止它下次被启动。"
        }
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
            "com.xgimi.user" to "极米账号与会员；含芒果、优酷账号绑定服务（MgBindService、YoukuBindService，已核实存在）",
            "com.xgimi.soundermodeservice" to "音箱模式（推测）",
        ))
        read("看看它们还在不在运行", "ps -A -o PID,USER,STIME,TIME,NAME | grep -E 'hilink|iotserver|vcontrol|mateservice|soundermode'", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                 3594 system       19:33:30 00:00:01 com.xgimi.soundermodeservice
                 3801 system       19:33:31 00:00:00 com.xgimi.mateservice
                24688 system       01:14:09 00:33:29 com.xgimi.xgimihilink
            """)
            note = """
                **停用了，但有三个还在运行。** STIME 是启动时间，TIME 是累计 CPU 时间。
                soundermode 和 mateservice 从开机（9-30 19:33）一直跑到现在；hilink 在 10-01 凌晨 01:14 又被重新拉起，已经用了 33 分钟 CPU。原因见下一条。
            """
        }
        read("找出所有「已停用却还在运行」的包", "for p in \$(pm list packages -d | sed s/package://); do if pid=\$(pidof \$p); then echo \"\$p \$pid \$(dumpsys package \$p | grep -m1 -o PERSISTENT)\"; fi; done", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                com.xgimi.xgimihilink 24688 PERSISTENT
                com.xgimi.mateservice 3801 PERSISTENT
                com.xgimi.soundermodeservice 3594 PERSISTENT
            """)
            note = """
                逐个检查停用的包有没有进程（`pidof`），有的话再看它有没有 PERSISTENT 标记。29 个里只有这 3 个，**全都是 PERSISTENT**：系统级常驻组件（以 system 身份运行，系统会保持它们一直在线）。
                停用发生在这次开机之后（脚本写于 10-01 12:14，`dumpsys package` 里 lastDisabledCaller 是 shell），所以它们是开机时启动、停用后没被结束。
                **重启后验证（2026-10-01）：三个照样启动了。** 对 PERSISTENT 系统组件，`pm disable-user` 挡不住开机启动。
            """
        }
        text("""
            **停不掉的三个怎么办（未实施）：** 对当前用户卸载（`pm uninstall -k --user 0`）或许能阻止它们启动，但这是 system 身份的常驻组件，卸载后的影响没有把握（官方桌面也是这样处理的，没出问题）。hilink 持续占用 CPU，值得以后试一下，先只对它试，并准备好 `cmd package install-existing` 恢复。
        """)
        text("这一组以后如果要用极米手机 App 遥控、蓝牙音箱模式或智能家居联动，需要恢复对应的包。")
    }

    verify {
        read("核对总数", "pm list packages -d | grep -c xgimi", Host.Adb) {
            captured("2026-10-01", "29")
            note = "29 个停用加上 2 个卸载（home、stream.video），共 31 个。"
        }
    }

    audit("旧记录核对（旧版分 8 篇，这里合并为一份清单）") {
        claim("停用 adservice / datareporter / bugreportsender：消除开机全屏推送并减少后台无用唤醒。", Verdict.Unverified,
            "三个包确认已停用。「消除开机推送」「减少唤醒」的效果没有前后对比测试。")
        claim("停用内置豆瓣 FM、动态画中画壁纸与氛围组件，释放约 320MB 运行时常驻内存。", Verdict.Unverified,
            "这些包早已停用，无法复测停用前的内存。")
        read("旧版：查看 IoT 服务运行态进程", "ps -ef | grep -E 'hilink|iotserver|vcontrol'", Host.Adb) {
            verdict = Verdict.Confirmed
            captured("（旧记录）", """
                system  4270  2665 6 19:33:35 ? 00:19:35 com.xgimi.xgimihilink        # 华为协议常驻扫描
                system  4804  2665 0 19:33:43 ? 00:00:28 com.xgimi.xgimiiotserver       # 极米 IoT 守护
                system  6244  2665 1 19:33:57 ? 00:01:55 com.xgimi.vcontrol:miot       # 米家联动进程
            """)
            note = "旧记录说 hilink「后台累计消耗 CPU 调度时间近 20 分钟」。实测 hilink 虽已停用但**仍在运行**，10-01 晚上累计 33 分钟（它在凌晨 01:14 重启过），说明它确实一直在消耗 CPU。iotserver、vcontrol 现在没有进程。"
        }
        claim("底层生命周期特性：上述组件以 system (UID 1000) 权限运行，且部分带有 persistent 标记，停用后在下次开机重启时生效，系统将不再派发并启动其服务树。", Verdict.Disproved,
            "前半句**成立**：hilink 等是 system 身份、带 PERSISTENT 标记，停用后进程仍在运行。后半句**不成立**：2026-10-01 重启后，这三个照样启动了。")
        claim("com.xgimi.home：GMUI 官方桌面，负责主屏渲染、顶部轮播海报与爱奇艺/芒果影视推荐流。com.xgimi.screensaver：闲置时展示壁纸与商推海报，停用后由 Projectivy 自带屏保引擎接管。skinmanager / skinconfig / skin.*：桌面主题样式的分发、配置和内置壁纸包。", Verdict.Unverified,
            "用途和包名、应用名吻合，没有逐个验证细节。")
        claim("com.xgimi.upgrade：周期性联网向官方服务器轮询新固件包、静默后台下载并弹出强制升级提示。升级可能覆写 system 分区并重置 pm disable 状态。com.xgimi.ota.accessories：常驻后台轮询检测蓝牙遥控器、3D 眼镜等硬件的新固件，产生不必要的网络请求与唤醒锁。", Verdict.Unverified,
            "没有抓过升级流量，也没有经历过升级。「升级可能恢复停用状态」是合理推测，停用 OTA 是出于谨慎。")
        claim("com.xgimi.iot：极米 IoT 物联中枢，负责与极米生态硬件及华为 HiLink 互联。smartconnect：局域网跨设备协同与发现握手。mobilebridgeservice：手机无屏助手 App 专用桥接通道。smartaccessories：极米转盘、专用环境光感支架等外设控制。mateservice：极米官方无线麦克风及外置低音炮伴生服务。soundermodeservice：关屏独立蓝牙音箱模式。", Verdict.Unverified,
            "用途是从包名推断的，没有逐个验证。")
        claim("com.xgimi.user：极米会员与第三方账号绑定。常驻后台执行 YoukuBindService 与 MgBindService 优酷芒果账号轮询。", Verdict.Confirmed,
            "这两个服务在包里确实存在（`dumpsys package com.xgimi.user`）。「常驻轮询」没有验证（包已停用）。")
    }

    related("debloat-method", "debloat-scripts", "projectivy-launcher")
}
