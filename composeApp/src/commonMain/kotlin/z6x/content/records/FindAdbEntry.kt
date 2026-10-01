package z6x.content.records

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val FindAdbEntry = module("find-adb-entry", "寻找开发者模式入口（均未成功）") {
    keywords = "开发者选项 · 工厂模式 · minitvfactory · Activity"
    overview = """
        普通安卓设备在「关于本机」中连续点击版本号即可开启开发者选项，进而打开 ADB 调试。在这台投影仪上尝试了三种途径均未成功，最终转向「通过 SSH 启动网络 ADB」。
    """
    partial("2026-10-01")

    story("尝试 1：遥控器按键组合") {
        change("网上流传的各种组合键", "", Host.Tv) {
            note = """
                • 在「关于」页面连按确定键 5~7 次；
                • 方向键「左、确认、右」等组合，长按或快速连按；
                • 长按遥控器上的设置键。
            """
            outcome = "均无反应。"
        }
        text("这些方法大多来自旧版固件或其他机型，缺乏可靠来源。**调整思路：** 既然界面上找不到，就用命令行检查系统中是否存在开发者选项。")
    }

    story("尝试 2：检查系统设置中是否有开发者选项") {
        read("查找与设置相关的包", "pm list packages | grep -iE 'setting|developer'", Host.Ssh) {
            captured("2026-10-01", """
                package:com.android.settings.intelligence
                package:com.android.providers.settings
                package:com.android.newsettings
            """)
            note = """
                原生安卓的设置应用是 `com.android.settings`，电视版是 `com.android.tv.settings`，两者**均不存在**。
                极米替换为自有的 `com.android.newsettings`，其中没有「开发者选项」。
                另外两个包不是设置界面：settings.intelligence 负责设置搜索，providers.settings 是保存设置值的数据库。
            """
        }
        text("**结论：** 原生开发者选项已被整体移除。**调整思路：** 厂商通常保留供产线调试使用的工厂模式，其中可能包含调试开关。")
    }

    story("尝试 3：工厂模式") {
        read("查找工厂模式的启动界面", "cmd package resolve-activity --brief -a android.intent.action.MAIN com.xgimi.minitvfactory", Host.Adb) {
            captured("2026-10-01", """
                priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
                com.xgimi.minitvfactory/.ui.MainFactoryMenuActivity
            """)
            note = """
                `resolve-activity` 向系统查询该包中响应 MAIN（启动）意图的界面，`--brief` 只输出结论。
                最后一行即入口，格式为 `包名/.界面类名`，可用 Activity Launcher 或 `am start -n` 打开。
            """
        }
        text("""
            用 Activity Launcher 打开工厂菜单，检查了两页工程选项（老化、光机参数、色温、按键测试、网络测试等），均无 ADB 或网络调试开关。**此途径同样无效。**
        """)
    }

    story("后续发现：系统中有一个「远程调试」应用") {
        read("在所有可启动的界面中搜索 debug", "pm query-activities --brief -a android.intent.action.MAIN | grep -iE 'developer|debug|remote'", Host.Adb) {
            captured("2026-10-01", "    com.xgimi.remote/.RemoteActivity")
        }
        read("查看对应的安装包", "pm list packages -f com.xgimi.remote", Host.Adb) {
            captured("2026-10-01", "package:/vendor/app/XgimiRemoteDebug/XgimiRemoteDebug.apk=com.xgimi.remote")
            note = "`-f` 同时显示安装包路径。文件名为 **XgimiRemoteDebug**（极米远程调试），位于 vendor 分区。"
        }
        text("""
            实测系统中有极米自有的远程调试应用 `com.xgimi.remote`（版本 2.0.1），带启动界面，并能接收开关 WebRTC 的广播。
            它可能与属性 `xgimi.remoteDebug.on`（当前为 false）相关，看起来是官方的远程协助工具。**未打开验证**：无法确定它是否会联网上报或改变调试状态，待有需要时再研究。
        """)
    }

    lesson("经验") {
        text("""
            • 界面上找不到时，用命令行列出系统中实际存在的内容：`pm list packages`、`pm query-activities`、`resolve-activity`。
            • 一条途径无效时，先思考该功能**通常**位于何处：开发者选项在设置中 → 设置已被替换 → 厂商调试功能通常在工厂模式 → 工厂模式中也没有 → 放弃寻找开关，直接检查底层属性（这一步成功）。
            • 要完整阅读输出。不加 `--brief` 时，搜索 debug 命中的是安装包路径中的 XgimiRemoteDebug.apk，而组件名 RemoteActivity 并不包含 debug，因此这里补充了 remote 关键词。
        """)
    }

    audit {
        claim("尝试在系统「关于本机」界面连续按确定键 5 到 7 次；按方向键「左键、确认、右键」、长按或快速连按左右键；长按遥控器顶部的设置键等。测试结果：在新版 Android 12 固件下，以上所有按键组合均无任何响应，新版 GMUI 已屏蔽此类隐蔽入口。", Verdict.Unverified,
            "这是当时的经历，未重新尝试。「GMUI 已屏蔽隐蔽入口」属于推断。")
        claim("系统删除了原生的 com.android.settings 和 com.android.tv.settings，因此原生通过 Android 版本号开开发者选项的入口不存在。（package:com.android.newsettings  # 极米深度重构的定制版设置，删除了原生关于与开发者选项）", Verdict.Confirmed,
            "两个原生设置包均不存在，只有 com.android.newsettings。")
        read("旧版：查找工厂模式的主 Activity", "dumpsys package com.xgimi.minitvfactory | grep -i activity", Host.Adb) {
            verdict = Verdict.Disproved
            captured("（旧记录）", "ActivityRecord{... com.xgimi.minitvfactory/.MainActivity}")
            note = "`dumpsys package` 的输出中不会出现 ActivityRecord（这是 `dumpsys activity` 的格式）。实际入口为 `.ui.MainFactoryMenuActivity`，可用 `cmd package resolve-activity` 查询（见上方）。"
        }
        claim("在电视上拉起工厂模式界面后，翻遍了两页工程菜单（包含老化模式、光机参数、色温调整、按键测试、网络测试等），未发现任何直观的 ADB 或网络调试开关。", Verdict.Unverified,
            "这是当时的经历。工厂模式入口确认存在，未重新打开检查。")
        read("旧版：全局检索包含 developer 或 debug 的隐藏 Activity", "pm query-activities -a android.intent.action.MAIN | grep -iE 'developer|debug'", Host.Adb) {
            verdict = Verdict.Disproved
            captured("（旧记录）", "仅检索到 WebView DevTools，不存在系统级的开发者选项 Activity")
            note = "实测搜索结果为 `/vendor/app/XgimiRemoteDebug/XgimiRemoteDebug.apk`（com.xgimi.remote），其中没有 WebView DevTools。"
        }
    }

    related("force-adb", "ssh-permission-wall")
}
