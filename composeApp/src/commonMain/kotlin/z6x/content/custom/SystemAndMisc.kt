package z6x.content.custom

import z6x.framework.Host
import z6x.framework.module

val SystemPackages = module("system-packages", "系统里还剩什么：组件与入口") {
    keywords = "Activity Launcher · resolve-activity · 系统组件 · uid 1000"
    overview = """
        精简之后，系统里还装着哪些极米组件、各自大概管什么、哪些入口打不开。用 Activity Launcher 能看到并打开各个 App 里隐藏的界面。
    """
    verified("2026-10-01")

    steps("用 Activity Launcher 打开隐藏界面") {
        read("Activity Launcher 的入口", "cmd package resolve-activity --brief -a android.intent.action.MAIN de.szalkowski.activitylauncher.oss | tail -1", Host.Adb) {
            captured("2026-10-01", "de.szalkowski.activitylauncher.oss/de.szalkowski.activitylauncher.entrypoint.MainActivity")
            note = "Activity Launcher 列出每个 App 的全部界面（Activity），包括没放到桌面上的。工厂模式就是用它打开的。"
        }
        change("用命令打开某个界面", "am start -n com.xgimi.minitvfactory/.ui.MainFactoryMenuActivity", Host.Adb) {
            note = "`-n 包名/界面类名`。界面名用 `cmd package resolve-activity` 或 Activity Launcher 查。工厂模式里有光机校准等选项，**只看不改**。"
        }
    }

    story("打不开的入口") {
        text("系统设置被换成了极米自己的 `com.android.newsettings`，很多安卓标准设置页不存在，App 里跳转到这些页面的按钮会没反应：")
        read("无障碍设置页", "cmd package resolve-activity --brief -a android.settings.ACCESSIBILITY_SETTINGS", Host.Adb) {
            captured("2026-10-01", "No activity found")
        }
        read("应用详情页", "cmd package resolve-activity --brief -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:org.smarttube.stable", Host.Adb) {
            captured("2026-10-01", "No activity found")
            note = "所以看某个 App 的权限、存储，要用命令：`dumpsys package 包名`。"
        }
        read("系统设置主页", "cmd package resolve-activity --brief -a android.settings.SETTINGS", Host.Adb) {
            captured("2026-10-01", "No activity found")
        }
        read("原生设置包", "pm list packages | grep -xE 'package:com.android.(tv.)?settings'", Host.Adb) {
            expectsError = true
            note = "没有输出：原生手机版和电视版设置都不存在。"
        }
    }

    story("还在运行的极米组件") {
        text("下面这些都**确认已安装**（2026-10-01）。用途是根据包名推断的，没有逐个验证：")
        facts(
            "com.android.newsettings" to "极米的系统设置（画面、梯形校正、对焦、声音等）。以 system 身份运行",
            "com.xgimi.config" to "硬件配置（推测）",
            "com.xgimi.autokst / com.xgimi.tof" to "自动梯形校正 / ToF 测距对焦（推测）",
            "com.xgimi.xrmservice" to "资源 / 温控管理（推测）",
            "com.xgimi.windowsystem" to "画面几何变换（推测）",
            "com.xgimi.tvinput" to "HDMI 信号源",
            "com.xgimi.bluetoothservice" to "蓝牙（遥控器连接）",
            "com.xgimi.duertts" to "语音（带一个系统默认开启的无障碍服务）",
            "com.xgimi.gimiplayer" to "本地视频播放器",
            "com.xgimi.wirelessscreen" to "无线投屏，见「无线投屏」",
            "com.xgimi.filemanager" to "文件管理器（仍启用）",
            "com.xgimi.systemui" to "音量条、信号源浮窗等系统界面（推测）",
            "com.xgimi.shutdown" to "关机菜单 / 定时关机（推测）",
            "com.xgimi.manager" to "极米管家（清理、加速）",
            "com.xgimi.minitvfactory" to "工厂模式",
            "com.xgimi.remote" to "XgimiRemoteDebug 远程调试，见「找开发者模式入口」",
            "com.sohu.inputmethod.sogou.tv" to "搜狗输入法，见「输入法」",
        )
        read("看某个包以什么身份运行", "dumpsys package com.android.newsettings 2>/dev/null | grep -m1 sharedUser", Host.Adb) {
            captured("2026-10-01", "    sharedUser=SharedUserSetting{3506e1b android.uid.system/1000}")
            note = "`android.uid.system/1000`：和系统框架共用 uid 1000。这类组件权限比 shell（2000）还高，shell 改不动它们内部的东西。"
        }
    }

    consequences {
        text("""
            • 这一页里的组件**不要停用**，否则可能失去画面校正、遥控器、信号源等基本功能。
            • 开机时出现的梯形校正提示框来自 newsettings（它有开机广播接收器 BootBroadcastReceiver），按返回键关闭即可。
        """)
    }

    lesson("核对旧记录时发现的问题") {
        text("""
            • 旧版说可以用 `am start -n com.android.tv.settings/.MainSettings` 直达原生电视设置，还说"原生设置可直达开发者选项"。实测原生设置包**根本不存在**，删除。
            • 旧版说 `com.xgimi.remote` 是"遥控器按键码映射"，实际是 XgimiRemoteDebug（远程调试），改正。
            • 旧版对各组件的描述（风扇调速、3D 转码等）很具体，但没有依据，这里只保留能从包名判断的用途，并标出推测。
        """)
    }

    related("find-adb-entry", "projectivy-launcher", "port-owner")
}

val InputMethodPivot = module("input-method", "输入法：换了 LeanKeyboard 又换回搜狗") {
    keywords = "ime · LeanKeyboard · 搜狗 · 遥控器延迟"
    overview = """
        想用开源的 LeanKeyboard 替换预装的搜狗输入法，换完后遥控器操作明显变卡，于是卸掉 LeanKeyboard，恢复搜狗。
    """
    partial("2026-10-01")

    story("经过") {
        text("""
            1. 装了 LeanKeyboard（shared 目录里的 LeanKeyboard.apk），停用搜狗。
            2. 之后遥控器方向键、确认键反应变慢。
            3. 卸载 LeanKeyboard，重新启用搜狗并设为默认，操作恢复正常。
            **为什么会卡没有查清。** 旧记录里"按键都要先经过输入法""搜狗和遥控器有专属挂钩"的说法没有依据，不采用。
        """)
    }

    steps("恢复搜狗输入法") {
        change("启用并设为默认", """
            pm enable com.sohu.inputmethod.sogou.tv
            ime enable com.sohu.inputmethod.sogou.tv/.SogouIME
            ime set com.sohu.inputmethod.sogou.tv/.SogouIME
        """, Host.Adb) {
            note = "`ime enable` 把它加入可用输入法列表，`ime set` 设为当前输入法。"
        }
        danger("卸载 LeanKeyboard", "pm uninstall org.liskovsoft.androidtv.rukeyboard", Host.Adb)
    }

    verify {
        read("已启用的输入法", "ime list -s", Host.Adb) {
            captured("2026-10-01", "com.sohu.inputmethod.sogou.tv/.SogouIME")
            note = "`-s` 只列出名字。只有搜狗一个。"
        }
        read("当前默认输入法", "settings get secure default_input_method", Host.Adb) {
            captured("2026-10-01", "com.sohu.inputmethod.sogou.tv/.SogouIME")
        }
    }

    lesson("经验") {
        text("能用的预装组件不一定非要换。替换前想清楚换来的好处，出问题就退回原状，不必为一个「更干净」的替代品付出体验代价。")
    }

    related("system-packages", "debloat-scripts")
}

val ScreenCast = module("screen-cast", "无线投屏") {
    keywords = "Miracast · AirPlay · DLNA · wirelessscreen"
    overview = """
        极米的无线投屏应用同时支持 Miracast（Windows、安卓镜像）、AirPlay（苹果）和 DLNA（视频 App 里的投屏按钮）。发送端用系统自带的投屏功能即可，不用装 App。
    """
    partial("2026-10-01")

    story("发送端怎么投") {
        text("""
            • **Windows 10/11**：Win + K，选投影仪（Miracast）。
            • **iPhone / Mac**：控制中心 → 屏幕镜像（AirPlay）。
            • **安卓手机**：下拉菜单里的"投屏 / 屏幕镜像"（Miracast），或视频 App 播放界面的投屏图标（DLNA）。
            以上是常规用法，各品牌叫法不同。
        """)
    }

    steps("在投影仪上查") {
        read("投屏应用版本", "dumpsys package com.xgimi.wirelessscreen 2>/dev/null | grep -m1 versionName", Host.Adb) {
            captured("2026-10-01", "    versionName=3.2.19.10")
        }
        read("它包含哪些投屏组件", "dumpsys package com.xgimi.wirelessscreen 2>/dev/null | grep -oiE '[A-Za-z0-9_.]*(airplay|dlna|miracast|wfd|cast)[A-Za-z0-9_.]*(Service|Activity|Receiver)' | sort -u", Host.Adb) {
            captured("2026-10-01", """
                com.waxrain.airplaydmr.action.SERVICE
                com.xgimi.castscreen.view.MainActivity
                com.xgimi.wfd.WifiDisplayActivity
                com.xgimi.wfd.WifiDisplayService
                com.xgimi.wfd.service
            """)
            note = "wfd = Wi-Fi Display，就是 Miracast。`com.waxrain.airplaydmr` 是第三方的 AirPlay/DLNA 接收组件。"
        }
        read("投屏主界面入口", "cmd package resolve-activity --brief -a android.intent.action.MAIN com.xgimi.wirelessscreen | tail -1", Host.Adb) {
            captured("2026-10-01", "com.xgimi.wirelessscreen/.safe.SafeModeActivity")
        }
        change("打开投屏界面（等待 Windows 连接时用）", "am start -n com.xgimi.wirelessscreen/.activity.NewMainActivity", Host.Adb) {
            note = "NewMainActivity 确认存在（dumpsys package 里能看到），但 MAIN 入口是上面的 SafeModeActivity，两者的区别没有查。"
        }
        change("用完关掉", "am force-stop com.xgimi.wirelessscreen", Host.Adb)
    }

    story("遥控器变卡（原记录）") {
        text("""
            当时停在 Miracast 等待连接的界面时，蓝牙遥控器明显卡顿、丢键，退出后恢复。
            **推测**是 Wi-Fi 搜索连接时和蓝牙争用无线芯片 / 天线（很多设备 Wi-Fi 和蓝牙共用一颗芯片）。没有验证，所以做法上只是"不用时别停在投屏界面"。
        """)
    }

    lesson("核对说明") {
        text("""
            • 旧版写的入口 `.activity.NewMainActivity` 存在，但系统的 MAIN 入口是 `.safe.SafeModeActivity`。
            • 旧版对蓝牙卡顿的解释（"射频开关主动挂起蓝牙接收"）写得很肯定，但没有依据，改为推测。
            • 发送端的操作是常规用法，没有逐个平台重新测试。
        """)
    }

    related("system-packages", "port-owner")
}

val DebloatScripts = module("debloat-scripts", "一键精简与恢复脚本") {
    keywords = "z6x_debloat_apply.sh · z6x_debloat_restore.sh · bash"
    overview = """
        把 31 个组件的处理写成两个脚本：一个一键应用，一个一键恢复原样。系统被重置或升级后，跑一下就能回到定制好的状态。脚本在项目的 `scripts/` 目录。
    """
    verified("2026-10-01")

    why {
        text("""
            • **apply**：卸载官方桌面和影视推荐 → 停用 29 个组件 → 确保搜狗输入法可用 → 按 Home → 打印核对结果。
            • **restore**：装回桌面和影视推荐 → 启用全部组件 → 恢复无障碍配置 → 打开官方桌面。
            两个脚本在 Deck 上运行，通过 `adb -s 设备` 把命令发给投影仪；设备地址作为第一个参数，默认 192.168.0.109:5555。
        """)
    }

    steps {
        change("应用定制", "./scripts/z6x_debloat_apply.sh 192.168.0.109:5555", Host.Deck) {
            note = "在项目根目录执行。每条命令后面都有 `|| true`，单条失败（比如已经停用过）不会中断整个脚本，所以可以重复运行。"
            outcome = "最后打印停用数量（应为 29）、已卸载的包（home、stream.video）和当前前台界面。"
        }
        change("恢复原样", "./scripts/z6x_debloat_restore.sh 192.168.0.109:5555", Host.Deck) {
            outcome = "官方桌面回来，所有组件启用。"
        }
        read("改脚本后先查语法", "bash -n scripts/z6x_debloat_apply.sh && bash -n scripts/z6x_debloat_restore.sh && echo ok", Host.Deck) {
            captured("2026-10-01", "ok")
            note = "`bash -n` 只检查语法、不执行，改完脚本先跑一下。"
        }
    }

    story("接手后改了什么") {
        text("""
            脚本原本在 shared 目录，2026-10-01 收进项目并核对，改了两处（原样版本保留在 git 历史里）：
            1. **apply 去掉了注入 Projectivy 无障碍服务那一步**：官方桌面已经卸载，Projectivy 是唯一桌面，不需要它；实机上这个服务也确实没开，Home 键正常。见「换掉官方桌面」。
            2. **restore 改正了官方桌面的入口名**：原来写的 `com.xgimi.home/.MainActivity` 不存在（命令失败被 `|| true` 吞掉了，所以一直没发现），改成 `dumpsys package` 查到的 `com.xgimi.module.cellview.home.ui.HomeActivity`。
            另外 apply 结尾加了核对：打印停用数和已卸载列表。
        """)
        text("""
            **教训：** `|| true` 让脚本"不出错"，也让错误**看不见**。容错的脚本要在最后核对结果，而不是只打印"完成"。
        """)
    }

    related("debloat-method", "debloat-list", "projectivy-launcher")
}
