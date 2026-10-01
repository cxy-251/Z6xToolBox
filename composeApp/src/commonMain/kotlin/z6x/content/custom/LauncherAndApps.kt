package z6x.content.custom

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val ProjectivyLauncher = module("projectivy-launcher", "替换官方桌面：Projectivy Launcher") {
    keywords = "HOME · com.xgimi.home · 无障碍服务 · resolve-activity"
    overview = """
        官方桌面充斥影视推荐和广告。安装开源的 Projectivy Launcher 后，将官方桌面和影视推荐对当前用户卸载，按 Home 键即只会回到 Projectivy。
    """
    verified("2026-10-01")

    why {
        text("""
            按下 Home 键时，系统查找所有声明了 **HOME** 类别的界面：只有一个时直接打开；有多个时需要选择默认桌面，或由某个应用通过无障碍服务拦截 Home 键。
            因此最简洁的方法是让 Projectivy 成为**唯一**的桌面，即将官方桌面 `com.xgimi.home` 对当前用户卸载。
        """)
    }

    steps("安装") {
        change("用 ADB 安装", "adb install -r ProjectivyLauncher-4.71.apk", Host.Deck) {
            note = "安装包位于 shared 目录。Projectivy 是 GitHub 上的开源项目，可从其 Release 页面下载 APK。"
            outcome = "输出 Success。"
        }
        change("手动启动", "am start -n com.spocky.projengmenu/.ui.home.MainActivity", Host.Adb)
    }

    story("经过：最初尝试无障碍服务方案") {
        text("""
            1. 安装 Projectivy（4.71）后，官方桌面仍然存在，按 Home 键仍回到官方桌面。
            2. Projectivy 提供「通过无障碍服务接管 Home 键」的功能，但点击其开启按钮**没有任何反应**。
        """)
        read("按钮无反应的原因", "cmd package resolve-activity --brief -a android.settings.ACCESSIBILITY_SETTINGS", Host.Adb) {
            captured("2026-10-01", "No activity found")
            note = "该按钮需要打开系统的无障碍设置页面，而极米的设置中没有此页面，请求无人响应，因此没有任何反应。"
        }
        text("""
            3. 既然无法通过界面开启，就**绕过界面**，用 ADB 命令直接写入系统设置，开启 Projectivy 的无障碍服务：
        """)
        change("命令行开启无障碍服务（当时的做法）", """
            settings put secure enabled_accessibility_services com.xgimi.duertts/com.xgimi.duertts.MonitorService:com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService
            settings put secure accessibility_enabled 1
        """, Host.Adb) {
            note = """
                多个服务之间用冒号 `:` 分隔。原有的极米语音服务 duertts 必须保留，否则会被覆盖。
                **目前已不需要此步骤**，原因见下文。
            """
        }
        text("""
            4. **更彻底的方法：** 将官方桌面对当前用户卸载。Projectivy 成为唯一的桌面，Home 键自然回到它，也就不再需要无障碍服务。影视推荐 `com.xgimi.stream.video` 一并卸载。
        """)
        danger("卸载官方桌面和影视推荐", """
            pm uninstall -k --user 0 com.xgimi.home
            pm uninstall -k --user 0 com.xgimi.stream.video
        """, Host.Adb) {
            note = "恢复：`cmd package install-existing com.xgimi.home`（stream.video 同理）。原理见「精简预装应用：停用与卸载的取舍」。"
        }
    }

    verify("当前状态") {
        read("当前的桌面应用", "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1", Host.Adb) {
            captured("2026-10-01", "com.spocky.projengmenu/.ui.home.MainActivity")
            note = "向系统查询响应 HOME 的界面，结果只有 Projectivy。`tail -1` 只取最后一行（前几行是匹配优先级等细节）。"
        }
        read("无障碍服务", "settings get secure enabled_accessibility_services", Host.Adb) {
            captured("2026-10-01", "com.xgimi.duertts/com.xgimi.duertts.MonitorService")
            note = "（重启前显示为简写 `com.xgimi.duertts/.MonitorService`，是同一个服务。）当时曾用 ADB 开启 Projectivy 的服务，但 2026-10-01 查看时列表中只剩极米语音服务，关闭时间不明。由于官方桌面已卸载，Home 键仍会回到 Projectivy，因此未再开启。"
        }
        change("按下 Home 键后检查焦点", "input keyevent 3; sleep 1; dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'", Host.Adb) {
            note = "模拟按下 Home 键，1 秒后查询焦点窗口，应为 com.spocky.projengmenu。此操作会切换屏幕画面。见「查看当前焦点窗口」。"
        }
        read("Projectivy 版本", "dumpsys package com.spocky.projengmenu 2>/dev/null | grep -m1 versionName", Host.Adb) {
            captured("2026-10-01", "    versionName=4.71")
        }
    }

    consequences {
        text("""
            • 更换桌面不影响其他应用的数据和登录状态：各应用的数据位于各自的私有目录中，桌面只负责显示图标和启动应用。
            • 官方桌面自带的「儿童模式」随之不可用（它属于官方桌面的一部分）。
            • 信号源切换、画面设置等官方入口，需要在 Projectivy 中找到对应的应用（如 com.android.newsettings），或使用遥控器上的设置键。
        """)
    }

    lesson("经验") {
        text("""
            • 按钮无反应时，先检查它要打开的目标**是否存在**：用 `resolve-activity` 即可查明。
            • 能用「减法」（移除竞争者）解决的问题，就不用「加法」（再增加一个拦截服务）。后者多一层依赖，也多一个故障点。
            • 旧版一键脚本中注入无障碍服务的步骤已删除，见「一键精简与恢复脚本」。
        """)
    }

    audit {
        claim("UI 无法开启根因：Projectivy 尝试调起 android.settings.ACCESSIBILITY_SETTINGS，极米系统无此活动组件，点击直接被静默忽略。", Verdict.Confirmed,
            "用 `resolve-activity` 查询该动作，结果为 No activity found。")
        claim("多服务共存机制：极米系统默认启用了 com.xgimi.duertts 语音服务，命令行写入时必须用冒号 : 连接多个服务，避免顶掉系统必要监听。", Verdict.Confirmed,
            "当前的无障碍服务列表即为 `com.xgimi.duertts/.MonitorService`。")
        claim("命令行强制注入并开启 Projectivy 无障碍服务后：无障碍服务强制激活成功，遥控器 Home 键捕获就绪。", Verdict.Unverified,
            "当时确实用 ADB 开启过；2026-10-01 查看时列表中已无 Projectivy，关闭时间不明。")
        change("旧版：停用官方桌面（开机直达第三方桌面）", "pm disable-user --user 0 com.xgimi.home", Host.Adb) {
            verdict = Verdict.Unverified
            note = "后来改为对当前用户卸载（`pm uninstall -k --user 0`），停用的做法未再测试。"
        }
        read("旧版：推送安装并查询主活动入口", "cmd package resolve-activity --brief com.spocky.projengmenu", Host.Adb) {
            verdict = Verdict.Disproved
            captured("（旧记录）", """
                Success
                com.spocky.projengmenu/.ui.home.MainActivity
            """)
            note = "不带 `-a` 动作参数时查不到入口，应写为 `-a android.intent.action.MAIN`（见上方「当前的桌面应用」）。入口名 `.ui.home.MainActivity` 本身正确。"
        }
        claim("发送 Home 键后：mCurrentFocus=Window{... com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity}", Verdict.Confirmed,
            "Projectivy 是唯一的 HOME 界面，`resolve-activity` 和输入窗口列表中都能看到它。")
        claim("桌面仅是系统 Intent 调用器，各应用的用户登录 Token、Session 保存在各自的 /data/data/<package>/ 隔离沙盒内，更换启动器不会清除任何应用私有数据。", Verdict.Confirmed,
            "这是安卓的基本机制：每个应用的数据位于各自的私有目录中，桌面无法访问。")
    }

    related("debloat-method", "debloat-scripts", "system-packages")
}

val AppStorePivot = module("app-store-pivot", "应用商店选型：Aurora 与 Aptoide 均不适用") {
    keywords = "Aurora Store · Aptoide TV · GMS · 局域网安装"
    overview = """
        为了在电视上直接搜索和安装应用，先后尝试了 Aurora Store 和 Aptoide TV，均不适用。最终放弃应用商店，改为在 Deck 上下载，再通过 ADB 或局域网安装。
    """
    partial("2026-10-01")

    story("经过") {
        text("""
            1. **Aurora Store**（第三方 Google Play 客户端）：启动后闪退，未深入排查原因。
            2. **Aptoide TV**（为电视设计的第三方商店）：可以使用，界面也适合遥控器操作。但从中安装的 YouTube Music 一打开即退出，日志显示缺少 Google Play 服务。商店中的许多海外应用依赖 Google 服务，而本机没有，安装后也无法使用。
            当时也考虑过 **F-Droid**（不依赖 Google 的开源应用商店），但它收录的多为开源工具，而非电视上需要的应用，因此未采用。
            3. **放弃应用商店**：两个商店均已卸载（YouTube Music 一并卸载），改为在 Deck 上下载 APK，确认为 32 位版本后，通过 `adb install` 或局域网下载安装。
        """)
    }

    steps("当前的安装方式") {
        read("下载后检查架构", "unzip -l app.apk | grep -oE 'lib/[^/]+/' | sort | uniq -c", Host.Deck) {
            note = "包含 armeabi-v7a，或没有任何 lib 目录，才能安装。详见「应用的安装顺序与兼容性」。"
        }
        change("用 ADB 安装", "adb install -r app.apk", Host.Deck) {
            note = "`-r` 表示覆盖安装（升级）并保留数据。"
            outcome = "输出 Success。"
        }
        change("当时安装 Aptoide TV", "adb install -r aptoide_tv.apk", Host.Deck) {
            note = "安装包仍在 shared 目录中，包名为 `cm.aptoidetv.pt`。"
        }
        danger("卸载第三方应用", "adb uninstall cm.aptoidetv.pt", Host.Deck) {
            note = "第三方应用会被彻底卸载，数据一并删除，这与预装应用的「对当前用户卸载」不同。"
        }
        read("核对当前已安装的第三方应用", "pm list packages -3 | grep -iE 'aurora|aptoide|youtube'", Host.Adb) {
            expectsError = true
            note = "无输出（grep 未匹配，退出码为 1）表示均已卸载。"
        }
    }

    lesson("经验") {
        text("""
            • 选择工具时先确认其**前提条件**：Google 系应用大多需要 Google Play 服务，而国内电视普遍没有。
            • 一个方案两次尝试都不顺利时，应回到最简单、最可控的做法（自行下载、自行安装），而不是继续在工具上耗费时间。
            • 以上是当时的经历，涉及的应用已卸载，未重新演示。
        """)
    }

    audit {
        claim("Aurora Store 闪退根因（经日志审计定位）：GMS 库缺失，Aurora Store 依赖 Google Play Services 分发协议，极米底层完全阉割了 GMS 核心，通信时抛出空指针或 API 异常；DPI 布局冲突：极米投影仪默认为 240 DPI，部分依赖手机竖屏特性的页面渲染时在 Android TV 宽屏上触发窗口测量崩溃。", Verdict.Unverified,
            "未见到当时的日志。Aurora Store 本身面向没有 Google 服务的设备，「依赖 GMS」一说存疑；240 DPI 已实测，但与闪退的关联缺乏依据。")
        claim("Aptoide TV：专为 Android TV 盒子和投影仪定制的大屏应用商店，所有卡片均经过遥控器导航优化，无需 Google 框架。F-Droid：开源安全市场，收录纯净无广告开源工具，完全与 GMS 解耦，适合安装网络与系统工具。", Verdict.Unverified,
            "这是对两个商店的一般性介绍，未在本机上专门验证。")
        read("旧版：Aptoide TV 组件入口查询", "cmd package resolve-activity --brief cm.aptoidetv.pt", Host.Adb) {
            verdict = Verdict.Unverified
            captured("（旧记录）", "cm.aptoidetv.pt/.activity.MainActivity")
            note = "Aptoide TV 已卸载，无法复查。"
        }
        claim("实测从 Aptoide TV 下载安装 YouTube Music，启动即闪退。抓取 PID 8637 日志显示 `GooglePlayServices not available due to error 9` 与 `requires the Google Play Store, but it is missing`，因缺失 GMS 握手失败直接退出。", Verdict.Unverified,
            "这是当时的经历和日志，YouTube Music 已卸载，无法复查。本机确实没有 Google Play 服务。")
        claim("卸载 cm.aptoidetv.pt、com.aurora.store、com.google.android.apps.youtube.music 后，保留 7 个第三方应用：Activity Launcher、Projectivy、SmartTube、TV Bro、CX 文件浏览器、SimpleSSHD、Clash Meta。", Verdict.Confirmed,
            "`pm list packages -3` 的结果恰好是这 7 个。")
    }

    related("app-install-order", "lan-share")
}

val ClashProxy = module("clash-proxy", "代理：Clash Meta 以 VPN 模式运行") {
    keywords = "Clash Meta · VPN · tun0 · 7890 · http_proxy"
    overview = """
        Clash Meta 在本机以系统 VPN 模式正常运行，所有应用的流量都经过它，无需为每个应用单独设置代理。
    """
    verified("2026-10-01")

    why {
        text("""
            安卓应用若要接管全部网络流量，需要使用系统的 **VpnService**：创建虚拟网卡 `tun0`，系统将所有应用的数据包交给它，再由 Clash 按规则决定直连还是经过代理。
            首次开启时，系统会弹窗请求授权（由 `com.android.vpndialogs` 负责）。
        """)
    }

    verify("实测：VPN 正在运行") {
        read("虚拟网卡", "ip -4 addr show tun0 | grep inet", Host.Adb) {
            captured("2026-10-01", "    inet 172.19.0.1/30 scope global tun0")
            note = "172.19.0.1 是 Clash Meta TUN 模式的默认地址。"
        }
        read("系统登记的 VPN", "dumpsys vpn_management 2>/dev/null | head -2", Host.Adb) {
            captured("2026-10-01", """
                VPNs:
                  0: com.github.metacubex.clash.meta
            """)
        }
        read("VPN 会话名", "dumpsys connectivity 2>/dev/null | grep -m1 -oE 'sessionId=[A-Za-z]+'", Host.Adb) {
            captured("2026-10-01", "sessionId=Clash")
        }
        read("Clash 的进程", "ps -A | grep -i clash", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                u0_a69        7091  2665 1168048  94624 0                   0 S com.github.metacubex.clash.meta
                u0_a69        7124  2665 1784584 123820 0                   0 S com.github.metacubex.clash.meta:background
            """)
            note = "共两个进程：界面进程和 `:background` 后台服务进程（VPN 和代理核心位于后者）。第 5 列为实际占用内存（KB），约 120MB。"
        }
        read("混合代理端口", "netstat -tln | grep 7890", Host.Adb) {
            captured("2026-10-01", "tcp6       0      0 [::]:7890               [::]:*                  LISTEN")
            note = "7890 是 Clash 的 HTTP/SOCKS 混合端口。`[::]` 表示监听所有地址（IPv4 同样可以连接），因此局域网内的其他设备也能使用，见「安全检查：对局域网开放的服务」。"
        }
        read("VPN 附带的 HTTP 代理", "dumpsys connectivity 2>/dev/null | grep -m1 -oE 'HttpProxy: \\[[^]]*\\] [0-9]+'", Host.Adb) {
            varies = true
            captured("2026-10-01", "HttpProxy: [127.33.187.145] 37855")
            note = "Clash 还为 VPN 网络设置了一个本机 HTTP 代理（地址和端口每次启动时随机生成），并附带不经过代理的排除列表（局域网、国内常用站点等）。支持系统代理的应用会直接使用它。"
        }
        read("全局 http_proxy 设置", "settings get global http_proxy", Host.Adb) {
            captured("2026-10-01", "null")
            note = "未另行设置全局代理。VPN 运行时无需此设置。"
        }
    }

    steps("备用：不使用 VPN 时的代理方法") {
        change("设置全局 HTTP 代理", "settings put global http_proxy 127.0.0.1:7890", Host.Adb) {
            note = "只对遵循系统代理设置的应用有效（浏览器、部分视频应用）。VPN 正常运行时无需使用。"
        }
        change("清除全局代理", "settings put global http_proxy :0", Host.Adb) {
            note = "`:0` 表示不使用代理。"
        }
        change("为单个应用设置代理", "", Host.Tv) {
            note = "SmartTube、TV Bro 等应用的设置中有代理选项，填写 `127.0.0.1`、端口 `7890` 即可，只影响该应用。"
        }
    }

    consequences {
        text("""
            • Clash 规则中的局域网地址需设为直连（DIRECT），否则 Deck 与投影仪之间的 ADB、SSH 和局域网共享可能受到影响。上述 HTTP 代理排除列表中已包含 192.168.*。
            • 不要将订阅链接和节点信息写入笔记或通过截图分享。
        """)
    }

    lesson("经验") {
        text("在截图中注意到 SimpleSSHD 列出的 IP 中有一个 172.19.0.1，循此查到 tun0，才发现 VPN 实际上正在运行。**留意不寻常的细节**，往往能推翻错误的假设。")
    }

    audit {
        claim("极米系统删减了 VpnDialogs.apk，导致应用内点击「启动 VPN (TUN 模式)」必定崩溃。", Verdict.Disproved,
            "`com.android.vpndialogs` 存在且已启用；Clash Meta 正以 VPN 模式运行（tun0 和 vpn_management 中均可看到）。")
        claim("方案 1（第三方应用内独立代理）：如 SmartTube 或 TV Bro 内置网络设置直接填入代理地址 127.0.0.1 端口 7890，完全不调用系统 VPN 接口。方案 2：命令行注入系统级 HTTP 代理 settings put global http_proxy 127.0.0.1:7890。", Verdict.Unverified,
            "两种方法本身可行（7890 端口正在监听），但本机一直使用 VPN，未改用这两种方式测试。")
        read("旧版：查看 Clash Meta 后台进程与监听端口", "ps -ef | grep -i metacubex && netstat -tlpn | grep 7890", Host.Adb) {
            verdict = Verdict.Confirmed
            varies = true
            captured("（旧记录）", """
                u0_a69  7124  2665 3 19:34:05 ? 00:08:19 com.github.metacubex.clash.meta:background
                tcp6       0      0 [::]:7890               [::]:*                  LISTEN      -
            """)
            note = "与实测一致：`:background` 进程的 uid 为 u0_a69（10069），7890 端口正在监听。"
        }
    }

    related("app-install-order", "lan-share")
}
