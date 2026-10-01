package z6x.content.custom

import z6x.framework.Host
import z6x.framework.SectionBuilder
import z6x.framework.Verdict
import z6x.framework.module

val DebloatMethod = module("debloat-method", "精简预装应用：停用与卸载的取舍") {
    keywords = "pm disable-user · pm uninstall --user 0 · install-existing"
    overview = """
        获得 ADB（shell 身份）后即可处理预装应用。本机共处理 31 个：29 个**停用**，2 个**对当前用户卸载**。两种方式均可随时恢复，无需刷机。
    """
    verified("2026-10-01")

    why {
        text("""
            预装应用位于只读的系统分区中，shell 身份无法删除 APK 文件本身，只能改变「当前用户能否使用它」：
            • **停用** `pm disable-user --user 0 包名`：应用和数据都保留，但无法启动（PERSISTENT 系统组件除外，见「停用清单」），启动器中也不再显示。
            • **对当前用户卸载** `pm uninstall -k --user 0 包名`：从用户 0（唯一的用户）中移除，在系统中看起来如同未安装；`-k` 表示保留数据。APK 仍在系统分区中，可以重新安装。
            • 两种方式都**不释放系统分区空间**，节省的是内存、CPU 和后台网络流量。
        """)
        text("""
            **大部分采用停用的原因：** 停用最容易恢复（`pm enable`），状态也便于查看（`pm list packages -d`）。
            **官方桌面和影视推荐采用卸载的原因：** 官方桌面停用后，「儿童模式」等内部入口仍会出现在 Activity Launcher 列表中，点击会报错；卸载后这些残留入口一并消失。
        """)
    }

    steps("查看当前状态") {
        read("被停用的包", "pm list packages -d", Host.Adb) {
            varies = true
            note = "`-d` 只列出已停用的包。当前为 29 个，均为 com.xgimi.*，详见「停用清单」。"
        }
        read("统计数量", "pm list packages -d | wc -l", Host.Adb) {
            captured("2026-10-01", "29")
        }
        read("已对当前用户卸载的包", "pm list packages -u | grep -vxF \"\$(pm list packages)\"", Host.Adb) {
            captured("2026-10-01", """
                package:com.xgimi.home
                package:com.xgimi.stream.video
            """)
            note = """
                `-u` 列出包括已卸载在内的所有包，不带 `-u` 则只列出当前已安装的包，二者之差即为已卸载的包。
                `grep -vxF "${'$'}(...)"`：`${'$'}(...)` 将当前已安装的列表作为一组匹配模式，`-F` 按纯文本匹配，`-x` 要求整行匹配，`-v` 反向选择，剩余结果即为差集。
            """
        }
        read("查询单个包的状态", "dumpsys package com.xgimi.doubanfm 2>/dev/null | grep -m1 -oE 'enabled=[0-9]'", Host.Adb) {
            captured("2026-10-01", "enabled=3")
            note = "0 为默认（启用）、1 为启用、2 为停用、3 为被用户停用（disable-user）、4 为按需启用。"
        }
    }

    steps("停用、卸载与恢复") {
        change("停用", "pm disable-user --user 0 com.xgimi.doubanfm", Host.Adb) {
            outcome = "输出 `Package com.xgimi.doubanfm new state: disabled-user`。普通应用的进程会被结束；但实测带 **PERSISTENT** 标记的系统组件在停用后进程**仍在运行**（见「停用清单」）。"
        }
        change("恢复已停用的包", "pm enable com.xgimi.doubanfm", Host.Adb) {
            outcome = "输出 `… new state: enabled`。"
        }
        danger("对当前用户卸载", "pm uninstall -k --user 0 com.xgimi.home", Host.Adb) {
            note = "标为高危，是因为它从用户视角彻底移除应用。若移除的是系统运行必需的组件，可能导致开机异常；处理前应先确认其用途。"
            outcome = "输出 Success。"
        }
        change("重新安装已卸载的包", "cmd package install-existing com.xgimi.home", Host.Adb) {
            note = "从系统分区将该应用重新安装给用户 0，无需 APK 文件。"
            outcome = "输出 `Package com.xgimi.home installed for user: 0`。"
        }
    }

    consequences {
        text("""
            • 停用和卸载在**重启后均保持有效**。
            • **系统 OTA 升级**可能恢复这些包，甚至更改 SELinux 模式导致 ADB 无法启动，因此 OTA 也已停用，见「停用清单」。
            • **恢复出厂设置**会清除这些状态，系统回到初始状态。
            • 不要处理用途不明的包：系统界面、输入、网络、蓝牙等组件一旦停用，可能导致遥控器无法使用。最坏情况下只能通过 ADB 恢复，因此务必确保 ADB 可以连接。
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
    ) { note = "可整段粘贴到 adb shell 中执行，每行一条。恢复时将 `disable-user --user 0` 替换为 `enable`。" }
}

val DebloatList = module("debloat-list", "停用清单：31 个预装组件") {
    keywords = "广告 · 上报 · IoT · OTA · 皮肤 · 伴生服务"
    overview = """
        按用途分组列出已处理的 31 个极米组件。用途依据包名、应用名和停用后的变化判断，未经反编译确认；标注「推测」的条目把握较小。
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
            "com.xgimi.bootwizard" to "开机向导（仅首次开机使用）",
            "com.xgimi.payview" to "会员付费界面",
        ))
    }
    story("推荐、推送与应用市场") {
        group(listOf(
            "com.xgimi.msgcenter" to "消息中心（推送弹窗）",
            "com.xgimi.newappmarket" to "极米应用市场",
        ))
        text("此外，`com.xgimi.stream.video`（影视聚合推荐）采用的是**卸载**而非停用，见「替换官方桌面：Projectivy Launcher」。")
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
        text("官方桌面 `com.xgimi.home` 本身采用**卸载**处理，见「替换官方桌面：Projectivy Launcher」。")
    }
    story("系统升级") {
        group(listOf(
            "com.xgimi.upgrade" to "系统 OTA 升级",
            "com.xgimi.ota.accessories" to "配件（遥控器等）固件升级",
        ))
        text("""
            **停用 OTA 的原因：** 升级可能恢复已停用的应用、将 SELinux 改回 Enforcing（使通过 SSH 启动 ADB 的方法失效），甚至移除 adbd 的预设配置。
            代价是不再收到官方修复和新功能。如需升级，先对这两个包执行 `pm enable`，升级后检查 ADB 是否仍可使用。
        """)
        change("立即结束正在运行的升级进程", "am force-stop com.xgimi.upgrade; am force-stop com.xgimi.ota.accessories", Host.Adb) {
            note = "disable-user 本身会结束进程，此步骤作为保险。force-stop 只结束当前进程，不阻止其下次启动。"
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
        read("检查它们是否仍在运行", "ps -A -o PID,USER,STIME,TIME,NAME | grep -E 'hilink|iotserver|vcontrol|mateservice|soundermode'", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                 3594 system       19:33:30 00:00:01 com.xgimi.soundermodeservice
                 3801 system       19:33:31 00:00:00 com.xgimi.mateservice
                24688 system       01:14:09 00:33:29 com.xgimi.xgimihilink
            """)
            note = """
                **已停用，但其中三个仍在运行。** STIME 为启动时间，TIME 为累计 CPU 时间。
                soundermode 和 mateservice 自开机（9-30 19:33）起一直运行；hilink 在 10-01 凌晨 01:14 被重新启动，累计已占用 33 分钟 CPU 时间。原因见下一条。
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
                逐个检查已停用的包是否有进程（`pidof`），若有，再检查其是否带 PERSISTENT 标记。29 个包中只有这 3 个在运行，且**均为 PERSISTENT**，即系统级常驻组件（以 system 身份运行，系统会保持其始终在线）。
                停用操作发生在本次开机之后（脚本写于 10-01 12:14，`dumpsys package` 中 lastDisabledCaller 为 shell），因此它们是开机时启动、停用后未被结束。
                **重启后验证（2026-10-01）：三者仍然启动。** 对于 PERSISTENT 系统组件，`pm disable-user` 无法阻止其开机启动。
            """
        }
        text("""
            **无法停止的三个组件的处理方案（未实施）：** 对当前用户卸载（`pm uninstall -k --user 0`）或许能阻止其启动，但它们是 system 身份的常驻组件，卸载后的影响尚无把握（官方桌面也是这样处理的，未出现问题）。hilink 持续占用 CPU，值得今后尝试：先只对它测试，并准备好用 `cmd package install-existing` 恢复。
        """)
        text("今后如需使用极米手机 App 遥控、蓝牙音箱模式或智能家居联动，需要恢复本组中对应的包。")
    }

    verify {
        read("核对总数", "pm list packages -d | grep -c xgimi", Host.Adb) {
            captured("2026-10-01", "29")
            note = "29 个停用加 2 个卸载（home、stream.video），共 31 个。"
        }
    }

    audit("旧记录核对（旧版分 8 篇，这里合并为一份清单）") {
        claim("停用 adservice / datareporter / bugreportsender：消除开机全屏推送并减少后台无用唤醒。", Verdict.Unverified,
            "三个包已确认停用。「消除开机推送」「减少唤醒」的效果未做前后对比测试。")
        claim("停用内置豆瓣 FM、动态画中画壁纸与氛围组件，释放约 320MB 运行时常驻内存。", Verdict.Unverified,
            "这些包早已停用，无法复测停用前的内存占用。")
        read("旧版：查看 IoT 服务运行态进程", "ps -ef | grep -E 'hilink|iotserver|vcontrol'", Host.Adb) {
            verdict = Verdict.Confirmed
            captured("（旧记录）", """
                system  4270  2665 6 19:33:35 ? 00:19:35 com.xgimi.xgimihilink        # 华为协议常驻扫描
                system  4804  2665 0 19:33:43 ? 00:00:28 com.xgimi.xgimiiotserver       # 极米 IoT 守护
                system  6244  2665 1 19:33:57 ? 00:01:55 com.xgimi.vcontrol:miot       # 米家联动进程
            """)
            note = "旧记录称 hilink「后台累计消耗 CPU 调度时间近 20 分钟」。实测 hilink 虽已停用但**仍在运行**，10-01 晚间累计 33 分钟（它在凌晨 01:14 重启过），说明它确实持续消耗 CPU。iotserver、vcontrol 当前没有进程。"
        }
        claim("底层生命周期特性：上述组件以 system (UID 1000) 权限运行，且部分带有 persistent 标记，停用后在下次开机重启时生效，系统将不再派发并启动其服务树。", Verdict.Disproved,
            "前半句**成立**：hilink 等是 system 身份、带 PERSISTENT 标记，停用后进程仍在运行。后半句**不成立**：2026-10-01 重启后，这三个照样启动了。")
        claim("com.xgimi.home：GMUI 官方桌面，负责主屏渲染、顶部轮播海报与爱奇艺/芒果影视推荐流。com.xgimi.screensaver：闲置时展示壁纸与商推海报，停用后由 Projectivy 自带屏保引擎接管。skinmanager / skinconfig / skin.*：桌面主题样式的分发、配置和内置壁纸包。", Verdict.Unverified,
            "用途与包名、应用名相符，细节未逐个验证。")
        claim("com.xgimi.upgrade：周期性联网向官方服务器轮询新固件包、静默后台下载并弹出强制升级提示。升级可能覆写 system 分区并重置 pm disable 状态。com.xgimi.ota.accessories：常驻后台轮询检测蓝牙遥控器、3D 眼镜等硬件的新固件，产生不必要的网络请求与唤醒锁。", Verdict.Unverified,
            "未抓取升级流量，也未经历过升级。「升级可能恢复停用状态」属于合理推测，停用 OTA 是出于谨慎。")
        claim("com.xgimi.iot：极米 IoT 物联中枢，负责与极米生态硬件及华为 HiLink 互联。smartconnect：局域网跨设备协同与发现握手。mobilebridgeservice：手机无屏助手 App 专用桥接通道。smartaccessories：极米转盘、专用环境光感支架等外设控制。mateservice：极米官方无线麦克风及外置低音炮伴生服务。soundermodeservice：关屏独立蓝牙音箱模式。", Verdict.Unverified,
            "用途由包名推断，未逐个验证。")
        claim("com.xgimi.user：极米会员与第三方账号绑定。常驻后台执行 YoukuBindService 与 MgBindService 优酷芒果账号轮询。", Verdict.Confirmed,
            "这两个服务确实存在于包中（`dumpsys package com.xgimi.user`）。「常驻轮询」未验证（包已停用）。")
    }

    related("debloat-method", "debloat-scripts", "projectivy-launcher")
}
