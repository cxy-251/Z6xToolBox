package z6x.content.custom

import z6x.framework.Host
import z6x.framework.Verdict
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

    story("没有界面的系统组件") {
        text("Activity Launcher 里看不到、但系统离不开的包。按类别列出 2026-10-01 实机上确认存在的：")
        facts(
            "样式覆盖层（Overlay）" to "com.android.internal.display.cutout.emulation.*（刘海屏模拟）、com.android.internal.systemui.navbar.*（导航栏样式）、com.android.frameworkres.overlay、com.android.theme.font.notoserifsource。只有资源，没有代码",
            "数据提供者（Provider）" to "com.android.providers.settings（系统设置数据库）、providers.media.module（媒体索引）、providers.tv（电视频道数据）",
            "网络" to "com.android.networkstack、networkstack.tethering（热点）、pacprocessor 和 proxyhandler（代理自动配置）、captiveportallogin（Wi-Fi 认证页）、keychain（证书）、vpndialogs（VPN 授权弹窗）",
            "极米底层服务" to "com.xgimi.inuiserver、com.xgimiui.api、com.xgimi.appdb、com.xgimi.xgimiservice、com.xgimi.persistentservice、com.xgimi.rgbdupgrade。从名字看是界面框架、应用数据库、系统服务和固件升级相关，具体用途未查",
        )
        read("自己列一遍", "pm list packages | grep -iE 'overlay|cutout|navbar|providers|networkstack|xgimiservice|persistentservice|appdb|inuiserver'", Host.Adb) {
            varies = true
        }
        text("这些都**不要动**。停掉网络栈或设置数据库，系统可能直接无法正常工作。")
    }

    story("儿童模式去哪了") {
        text("儿童模式不是独立的 App，而是官方桌面 `com.xgimi.home` 里的一组界面：")
        read("官方桌面里的儿童模式组件", "dumpsys package com.xgimi.home 2>/dev/null | grep -oE 'com.xgimi.childmode.[A-Za-z]+' | sort -u", Host.Adb) {
            captured("2026-10-01", """
                com.xgimi.childmode.BaByListActivity
                com.xgimi.childmode.ParentSettingActivity
                com.xgimi.childmode.Setting
                com.xgimi.childmode.SettingActivity
                com.xgimi.childmode.provider
                com.xgimi.childmode.service
            """)
            note = "官方桌面虽然对当前用户卸载了，安装包还在系统分区里，dumpsys 仍能读到它的组件清单。"
        }
        text("当初官方桌面只是**停用**时，这些界面还出现在 Activity Launcher 的列表里，点了打不开。改成**对当前用户卸载**后，列表里也清净了。要恢复：`cmd package install-existing com.xgimi.home`。")
    }

    consequences {
        text("""
            • 这一页里的组件**不要停用**，否则可能失去画面校正、遥控器、信号源等基本功能。
            • 开机时出现的梯形校正提示框来自 newsettings（它有开机广播接收器 BootBroadcastReceiver），按返回键关闭即可。
        """)
    }

    audit {
        change("旧版：直接拉起原生 Android TV 设置中心", "am start -n com.android.tv.settings/.MainSettings", Host.Adb) {
            verdict = Verdict.Disproved
            note = "原生电视设置包 com.android.tv.settings **不存在**，命令只会报找不到。"
        }
        change("旧版：直接调起原生应用详情管理页（以 SmartTube 为例）", "am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:org.smarttube.stable", Host.Adb) {
            verdict = Verdict.Disproved
            note = "这个动作没有界面响应（resolve-activity：No activity found）。"
        }
        change("旧版：启动 Activity Launcher 主活动", "am start -n de.szalkowski.activitylauncher.oss/de.szalkowski.activitylauncher.entrypoint.MainActivity", Host.Adb) {
            verdict = Verdict.Confirmed
            note = "入口名和 resolve-activity 查到的一致。"
        }
        claim("com.android.settings（原生设置）：原生 Android 系统设置。可直达应用权限明细、存储占用及原生开发者选项。", Verdict.Disproved,
            "这个包不存在。")
        claim("com.xgimi.remote 与 com.xgimi.bluetoothservice（蓝牙协议栈）：极米遥控器专有按键码映射与语音按键交互。", Verdict.Disproved,
            "com.xgimi.remote 是 XgimiRemoteDebug（远程调试），和遥控器按键无关。bluetoothservice 存在，用途未验证。")
        claim("com.xgimi.wirelessscreen（无线投屏）：AirPlay、Miracast 与 DLNA 局域网投屏接收服务。", Verdict.Confirmed,
            "包里有 wfd（Miracast）和 airplaydmr 组件。")
        claim("com.xgimi.duertts（语音与 TTS）：百度语音引擎，兼具系统无障碍基础监听能力。", Verdict.Confirmed,
            "它的 MonitorService 是系统里唯一开启的无障碍服务。「百度语音引擎」从包名（duer = 度秘）推断，未细查。")
        claim("com.sohu.inputmethod.sogou.tv（搜狗输入法）：电视大屏遥控拼音输入法。", Verdict.Confirmed, "当前默认输入法。")
        claim("android / packageinstaller / permissioncontroller（系统核心）：AOSP 系统框架、应用安装器与权限对话框。com.android.bluetooth 与 inputdevices（外设基础）：AOSP 标准蓝牙通信与 USB 键鼠驱动。", Verdict.Confirmed,
            "这些包都存在，是安卓标准组件。")
        claim("""
            com.android.newsettings（极米设置）：包含画面微调、梯形校正、电动对焦、自动避障及音频输出配置。
            com.xgimi.config（硬件配置）：护眼距离感知、全局动画参数及快捷键长按映射。
            com.xgimi.autokst 与 com.xgimi.tof（光学校准）：驱动正面激光 ToF 传感器测距与对焦校正算法。
            com.xgimi.xrmservice（温控资源管理）：光机发热监控与风道风扇转速调度。
            com.xgimi.windowsystem（画面合成）：投影画面几何位移与梯形变换渲染层。
            com.xgimi.minitvfactory（工程模式）：老化测试、风扇转速监控与 HDMI EDID 切换。高危项，切勿点击校准重置。
            com.xgimi.tvinput（信号源）：HDMI 1 与 HDMI 2（eARC）硬件视频流接入桥接服务。
            com.xgimi.gimiplayer 与 xhplayer（本地硬解）：底层视频硬件硬解码器，支持 3D 左右/上下格式转码播放。
            com.xgimi.filemanager（文件浏览）：本地存储卡与外接 USB 移动硬盘浏览器。
            com.xgimi.systemui（系统 UI）：全局音量条 HUD、静音提示及信号源切换浮窗。
            com.xgimi.shutdown（电源管理）：关机选择菜单、定时休眠与电源事件分发器。
            com.xgimi.manager（极米管家）：垃圾扫描、内存优化与后台白名单管理。
        """, Verdict.Unverified,
            "这些包都**确认存在**（2026-10-01）。括号里的用途和包名吻合，但具体功能（风扇调速、3D 转码、eARC 等）没有逐个验证。")
        claim("""
            无界面系统底座：样式与切边叠加层（navbar.threebutton/gestural、cutout.emulation.*、frameworkres.overlay、font.notoserifsource）；
            底层数据提供者（providers.settings、providers.media.module、providers.tv）；
            网络协议与连接栈（networkstack/tethering、pacprocessor/proxyhandler、captiveportallogin、keychain、vpndialogs）。
        """, Verdict.Confirmed, "全部存在，分类和用途是安卓标准组件。")
        claim("极米底层无界面守护进程：inuiserver（IPC 进程通信通道）、ui.api（自定义视图库）、appdb（应用元数据存储）、xgimiservice/persistentservice（开机硬件自检与心跳）、rgbdupgrade（激光测距固件维护）。", Verdict.Unverified,
            "都存在，但 ui.api 的实际包名是 `com.xgimiui.api`。括号里的用途未验证。")
        claim("儿童模式实际为官方桌面内嵌组件（com.xgimi.home/com.xgimi.childmode.SettingActivity 与 ParentSettingActivity）。此前已停用 com.xgimi.home，该组件在底层已无法运行，点击报 class does not exist。", Verdict.Confirmed,
            "两个界面都在官方桌面的包里（dumpsys package 可见）。「点击报错」是当时的经历。")
        claim("开机校准微调弹窗由特权包 com.android.newsettings 内部的 BootBroadcastReceiver 接收开机广播触发。Android 12 安全机制严格限制普通 shell（UID 2000）禁用 platform 签名特权包内的单个组件，强制禁用会抛出 SecurityException。最佳处理：依赖已激活的 Projectivy 无障碍服务在开机完成后强制将第三方桌面置顶覆盖，或由遥控器按一次返回键直接关闭。", Verdict.Unverified,
            "BootBroadcastReceiver **存在**，newsettings 以 system（uid 1000）运行。「shell 停不掉它的组件」没有测试（测试就得真的去停用它）。Projectivy 无障碍服务现在没开。")
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
            **为什么会卡没有查清**，旧记录的解释见下面。
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
        danger("卸载 LeanKeyboard", "pm uninstall org.liskovsoft.androidtv.rukeyboard", Host.Adb) {
            verdict = Verdict.Unverified
            note = "包名来自旧记录。LeanKeyboard 已卸载，现在查不到它，包名没有核对。"
        }
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

    audit {
        claim("按键分发管道挂起：在 Android 12 TV 框架下，第三方 IME 挂载后，遥控器方向键（DPAD）与确认键均需优先经过 InputMethodService 进行文本框焦点探测与按键拦截，引入了额外的分发延迟。", Verdict.Unverified,
            "没有测量过按键延迟，这个机理无法确认。")
        claim("厂商驱动依赖：极米蓝牙遥控器协议栈（com.xgimi.remote 与 duertts）与原厂预装的搜狗输入法存在专属通信挂钩，替换为普通 AOSP 键盘后破坏了硬件按键的高优先级调度。", Verdict.Disproved,
            "前提就不对：com.xgimi.remote 是远程调试应用，不是遥控器协议栈。「专属挂钩」没有依据。")
        claim("恢复搜狗输入法：Package com.sohu.inputmethod.sogou.tv new state: enabled；Input method ... selected for user #0。实测遥控响应即刻恢复正常。", Verdict.Confirmed,
            "搜狗是当前唯一启用的输入法、也是默认输入法。「恢复正常」是亲身经历。")
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

    audit {
        claim("iOS / macOS：基于 AirPlay 协议。极米系统后台常驻 AirPlayInitService，苹果设备下拉控制中心可直接搜索投影仪镜像。", Verdict.Disproved,
            "投屏应用里没有叫 AirPlayInitService 的组件；AirPlay 接收由 `com.waxrain.airplaydmr` 组件提供。苹果设备投屏本身没有测试。")
        claim("Windows 10/11：Win + K 直连（Miracast）。安卓手机：影视 App 的 TV 图标（DLNA）或下拉菜单「无线投屏」（Miracast）。两端零安装。", Verdict.Unverified,
            "常规用法，没有逐个平台测试。投屏应用里确实有 Miracast（wfd）组件。")
        change("旧版：前台调起极米无线投屏接收界面（开启广播侦听）", "am start -n com.xgimi.wirelessscreen/.activity.NewMainActivity", Host.Adb) {
            verdict = Verdict.Confirmed
            note = "NewMainActivity 确实存在；不过系统登记的 MAIN 入口是 `.safe.SafeModeActivity`。"
        }
        claim("内部硬件共存仲裁（Coexistence Arbiter）：投影仪主板将 Wi-Fi 与蓝牙集成在同一颗双模 SoC 上并共用同一组天线。Wi-Fi 执行全频段 P2P 搜网时，芯片内部的射频开关（RF Switch）主动挂起蓝牙接收，属于机身内部硬件层面的时隙剥夺。按键信号物理级截断：遥控器发出的蓝牙信号在投影仪天线输入端即被硬件丢弃。", Verdict.Unverified,
            "`lsusb` 看到联发科 0e8d:7663，一般对应 MT7663 Wi-Fi + 蓝牙二合一芯片，「同一颗芯片」可能成立。射频开关挂起、天线端丢弃这些细节无法用命令验证。")
        claim("用毕终止 Miracast 搜网广播（am force-stop com.xgimi.wirelessscreen），释放 2.4GHz 射频天线给蓝牙遥控器独占。", Verdict.Unverified,
            "注意：投影仪的 Wi-Fi 实测连在 **5GHz**（5785MHz），「释放 2.4GHz 天线」的说法不一定适用。")
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

    audit {
        claim("z6x_debloat_apply.sh：一键卸载官方桌面与推荐流、批量冻结 29 个后台伴生包、注入 Projectivy 无障碍服务并校验桌面焦点。", Verdict.Confirmed,
            "脚本内容与描述一致，实机状态（29 停用 + 2 卸载）也一致。无障碍注入这一步已经去掉（见上）。")
        claim("z6x_debloat_restore.sh：一键重新挂载被卸载的系统应用（install-existing）、解除所有冻结状态并还原官方无障碍与启动器配置。", Verdict.Disproved,
            "前两项没问题；「还原官方启动器」那一步的入口名写错了，一直静默失败（已修正）。恢复脚本本身没有在实机上跑过（跑了会撤销全部定制）。")
    }

    related("debloat-method", "debloat-list", "projectivy-launcher")
}
