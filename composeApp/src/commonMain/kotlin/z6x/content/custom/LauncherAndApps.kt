package z6x.content.custom

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val ProjectivyLauncher = module("projectivy-launcher", "换掉官方桌面：Projectivy Launcher") {
    keywords = "HOME · com.xgimi.home · 无障碍服务 · resolve-activity"
    overview = """
        官方桌面满屏影视推荐和广告。装上开源的 Projectivy Launcher 后，把官方桌面和影视推荐对当前用户卸载，按 Home 键就只会回到 Projectivy。
    """
    verified("2026-10-01")

    why {
        text("""
            按 Home 键时，系统找所有声明了 **HOME** 类别的界面。只有一个就直接打开它；有多个时要选默认桌面，或者由某个 App 用无障碍服务拦截 Home 键。
            所以最干净的办法是让 Projectivy 成为**唯一**的桌面：把官方桌面 `com.xgimi.home` 卸载（对当前用户）。
        """)
    }

    steps("安装") {
        change("用 ADB 安装", "adb install -r ProjectivyLauncher-4.71.apk", Host.Deck) {
            note = "安装包在 shared 目录。Projectivy 是 GitHub 上的开源项目，下载 Release 里的 APK。"
            outcome = "输出 Success。"
        }
        change("手动打开它", "am start -n com.spocky.projengmenu/.ui.home.MainActivity", Host.Adb)
    }

    story("经过：先走了无障碍这条路") {
        text("""
            1. 装好 Projectivy（4.71）后，官方桌面还在，按 Home 仍回到官方桌面。
            2. Projectivy 提供了"用无障碍服务接管 Home 键"的功能，但点它的开启按钮**没有任何反应**。
        """)
        read("为什么按钮没反应", "cmd package resolve-activity --brief -a android.settings.ACCESSIBILITY_SETTINGS", Host.Adb) {
            captured("2026-10-01", "No activity found")
            note = "按钮要打开系统的无障碍设置页，而极米的设置里没有这个页面，请求发出去没人接，于是什么也不发生。"
        }
        text("""
            3. 界面开不了，就**绕过界面**，用 ADB 命令直接写系统设置，开启 Projectivy 的无障碍服务：
        """)
        change("命令行开启无障碍服务（当时的做法）", """
            settings put secure enabled_accessibility_services com.xgimi.duertts/com.xgimi.duertts.MonitorService:com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService
            settings put secure accessibility_enabled 1
        """, Host.Adb) {
            note = """
                多个服务用冒号 `:` 隔开。原来已有的极米语音服务 duertts 要保留，否则会被覆盖掉。
                **现在不需要这一步了**，原因见下。
            """
        }
        text("""
            4. **更彻底的办法：** 把官方桌面对当前用户卸载。Projectivy 成了唯一的桌面，Home 键自然回到它，无障碍服务也就不需要了。影视推荐 `com.xgimi.stream.video` 也一起卸载。
        """)
        danger("卸载官方桌面和影视推荐", """
            pm uninstall -k --user 0 com.xgimi.home
            pm uninstall -k --user 0 com.xgimi.stream.video
        """, Host.Adb) {
            note = "恢复：`cmd package install-existing com.xgimi.home`（stream.video 同理）。原理见「精简预装应用：停用还是卸载」。"
        }
    }

    verify("现在的状态") {
        read("现在谁是桌面", "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1", Host.Adb) {
            captured("2026-10-01", "com.spocky.projengmenu/.ui.home.MainActivity")
            note = "问系统：响应 HOME 的是哪个界面。只剩 Projectivy 一个。`tail -1` 只取最后一行（前面是匹配优先级之类的细节）。"
        }
        read("无障碍服务", "settings get secure enabled_accessibility_services", Host.Adb) {
            captured("2026-10-01", "com.xgimi.duertts/com.xgimi.duertts.MonitorService")
            note = "（重启前显示为简写 `com.xgimi.duertts/.MonitorService`，是同一个服务。）当时用 ADB 开启过 Projectivy 的服务，但 2026-10-01 查看时列表里只剩极米语音服务，什么时候被关掉的不清楚。官方桌面已卸载，Home 键照样回到 Projectivy，所以没有再开。"
        }
        change("按一下 Home 再看焦点", "input keyevent 3; sleep 1; dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'", Host.Adb) {
            note = "模拟按 Home 键，等 1 秒再查焦点窗口，应该是 com.spocky.projengmenu。会切换屏幕画面。见「屏幕上现在是谁」。"
        }
        read("Projectivy 版本", "dumpsys package com.spocky.projengmenu 2>/dev/null | grep -m1 versionName", Host.Adb) {
            captured("2026-10-01", "    versionName=4.71")
        }
    }

    consequences {
        text("""
            • 换桌面不影响其他 App 的数据和登录状态，各 App 的数据在各自的私有目录里，桌面只负责显示图标、启动应用。
            • 官方桌面里自带的「儿童模式」也随之不可用（它是官方桌面的一部分）。
            • 官方的信号源切换、画面设置等入口要从 Projectivy 里找对应的 App（如 com.android.newsettings），或者用遥控器的设置键。
        """)
    }

    lesson("经验") {
        text("""
            • 按钮没反应时，先查它想打开的东西**存不存在**：`resolve-activity` 一查就知道。
            • 能用"减法"（去掉竞争者）解决的，就不用"加法"（再加一个拦截服务）。后者多一层依赖，多一个出问题的地方。
            • 旧版一键脚本还在注入无障碍服务，已经去掉，见「一键精简与恢复脚本」。
        """)
    }

    audit {
        claim("UI 无法开启根因：Projectivy 尝试调起 android.settings.ACCESSIBILITY_SETTINGS，极米系统无此活动组件，点击直接被静默忽略。", Verdict.Confirmed,
            "`resolve-activity` 查这个动作：No activity found。")
        claim("多服务共存机制：极米系统默认启用了 com.xgimi.duertts 语音服务，命令行写入时必须用冒号 : 连接多个服务，避免顶掉系统必要监听。", Verdict.Confirmed,
            "当前的无障碍列表就是 `com.xgimi.duertts/.MonitorService`。")
        claim("命令行强制注入并开启 Projectivy 无障碍服务后：无障碍服务强制激活成功，遥控器 Home 键捕获就绪。", Verdict.Unverified,
            "当时确实用 ADB 开启过；2026-10-01 查看时列表里已没有 Projectivy，什么时候被关掉的不清楚。")
        change("旧版：停用官方桌面（开机直达第三方桌面）", "pm disable-user --user 0 com.xgimi.home", Host.Adb) {
            verdict = Verdict.Unverified
            note = "后来改成了对当前用户卸载（`pm uninstall -k --user 0`），停用这种做法现在没有再测。"
        }
        read("旧版：推送安装并查询主活动入口", "cmd package resolve-activity --brief com.spocky.projengmenu", Host.Adb) {
            verdict = Verdict.Disproved
            captured("（旧记录）", """
                Success
                com.spocky.projengmenu/.ui.home.MainActivity
            """)
            note = "不带 `-a` 动作参数时查不出入口；要写成 `-a android.intent.action.MAIN`（见上面「现在谁是桌面」）。入口名 `.ui.home.MainActivity` 本身是对的。"
        }
        claim("发送 Home 键后：mCurrentFocus=Window{... com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity}", Verdict.Confirmed,
            "Projectivy 是唯一的 HOME，`resolve-activity` 和输入窗口列表里都能看到它。")
        claim("桌面仅是系统 Intent 调用器，各应用的用户登录 Token、Session 保存在各自的 /data/data/<package>/ 隔离沙盒内，更换启动器不会清除任何应用私有数据。", Verdict.Confirmed,
            "这是安卓的基本机制：每个应用的数据在自己的私有目录里，桌面碰不到。")
    }

    related("debloat-method", "debloat-scripts", "system-packages")
}

val AppStorePivot = module("app-store-pivot", "找个应用商店：Aurora、Aptoide 都放弃了") {
    keywords = "Aurora Store · Aptoide TV · GMS · 局域网安装"
    overview = """
        想找一个能在电视上直接搜索安装 App 的商店，先后试了 Aurora Store 和 Aptoide TV，都不好用，最后放弃商店，改成在 Deck 上下载、用 ADB 或局域网安装。
    """
    partial("2026-10-01")

    story("经过") {
        text("""
            1. **Aurora Store**（第三方 Google Play 客户端）：启动后闪退。没有深究原因。
            2. **Aptoide TV**（为电视设计的第三方商店）：能用，界面也适合遥控器。但从它装的 YouTube Music 一打开就退出，日志显示缺少 Google Play 服务。商店里很多海外 App 依赖 Google 服务，这台机器没有，装了也用不了。
            当时也考虑过 **F-Droid**（开源应用商店，不依赖 Google），它收录的多是开源工具，不是电视上想装的那些 App，没有用。
            3. **放弃商店**：两个都卸载了（YouTube Music 一起卸载），改为在 Deck 上下载 APK、确认是 32 位版本后，用 `adb install` 或局域网下载安装。
        """)
    }

    steps("现在的装 App 方式") {
        read("下载前查架构", "unzip -l app.apk | grep -oE 'lib/[^/]+/' | sort | uniq -c", Host.Deck) {
            note = "有 armeabi-v7a 或没有任何 lib 目录才能装。详见「App 安装顺序与兼容」。"
        }
        change("用 ADB 安装", "adb install -r app.apk", Host.Deck) {
            note = "`-r` 覆盖安装（升级）并保留数据。"
            outcome = "输出 Success。"
        }
        change("当时安装 Aptoide TV", "adb install -r aptoide_tv.apk", Host.Deck) {
            note = "安装包还在 shared 目录。包名是 `cm.aptoidetv.pt`。"
        }
        danger("卸载第三方应用", "adb uninstall cm.aptoidetv.pt", Host.Deck) {
            note = "第三方应用是真正卸载，数据一起删除，和预装应用的「对当前用户卸载」不同。"
        }
        read("核对现在装着的第三方应用", "pm list packages -3 | grep -iE 'aurora|aptoide|youtube'", Host.Adb) {
            expectsError = true
            note = "什么都不输出（grep 没匹配到，退出码 1）说明都已卸载。"
        }
    }

    lesson("经验") {
        text("""
            • 选工具先看它的**前提条件**：Google 系的 App 大多需要 Google Play 服务，国内电视都没有。
            • 一个方案试两次都不顺，就退回最朴素、最可控的做法（自己下载、自己装），不在工具上继续耗时间。
            • 这几步是当时的经历，涉及的 App 已卸载，没有重新演示。
        """)
    }

    audit {
        claim("Aurora Store 闪退根因（经日志审计定位）：GMS 库缺失，Aurora Store 依赖 Google Play Services 分发协议，极米底层完全阉割了 GMS 核心，通信时抛出空指针或 API 异常；DPI 布局冲突：极米投影仪默认为 240 DPI，部分依赖手机竖屏特性的页面渲染时在 Android TV 宽屏上触发窗口测量崩溃。", Verdict.Unverified,
            "没有看到当时的日志。Aurora Store 本身是给没有 Google 服务的设备用的，「依赖 GMS」这一点存疑；240 DPI 已实测，但和闪退的关系没有依据。")
        claim("Aptoide TV：专为 Android TV 盒子和投影仪定制的大屏应用商店，所有卡片均经过遥控器导航优化，无需 Google 框架。F-Droid：开源安全市场，收录纯净无广告开源工具，完全与 GMS 解耦，适合安装网络与系统工具。", Verdict.Unverified,
            "对两个商店的一般介绍，没有在这台机器上专门验证。")
        read("旧版：Aptoide TV 组件入口查询", "cmd package resolve-activity --brief cm.aptoidetv.pt", Host.Adb) {
            verdict = Verdict.Unverified
            captured("（旧记录）", "cm.aptoidetv.pt/.activity.MainActivity")
            note = "Aptoide TV 已卸载，无法复查。"
        }
        claim("实测从 Aptoide TV 下载安装 YouTube Music，启动即闪退。抓取 PID 8637 日志显示 `GooglePlayServices not available due to error 9` 与 `requires the Google Play Store, but it is missing`，因缺失 GMS 握手失败直接退出。", Verdict.Unverified,
            "当时的经历和日志，YouTube Music 已卸载，无法复查。这台机器确实没有 Google Play 服务。")
        claim("卸载 cm.aptoidetv.pt、com.aurora.store、com.google.android.apps.youtube.music 后，保留 7 个第三方应用：Activity Launcher、Projectivy、SmartTube、TV Bro、CX 文件浏览器、SimpleSSHD、Clash Meta。", Verdict.Confirmed,
            "`pm list packages -3` 正好是这 7 个。")
    }

    related("app-install-order", "lan-share")
}

val ClashProxy = module("clash-proxy", "代理：Clash Meta 以 VPN 模式运行") {
    keywords = "Clash Meta · VPN · tun0 · 7890 · http_proxy"
    overview = """
        Clash Meta 在这台投影仪上以系统 VPN 模式正常运行，所有 App 的流量都经过它，不需要每个 App 单独设代理。
    """
    verified("2026-10-01")

    why {
        text("""
            安卓 App 想接管全部网络流量，要用系统的 **VpnService**：创建一个虚拟网卡 `tun0`，系统把所有 App 的数据包交给它，Clash 再按规则决定直连还是走代理。
            第一次开启时系统会弹窗请求授权（由 `com.android.vpndialogs` 负责）。
        """)
    }

    verify("实测：VPN 在运行") {
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
            note = "两个进程：界面进程和 `:background` 后台服务进程（VPN 和代理核心在这里）。第 5 列是实际占用内存（KB），约 120MB。"
        }
        read("混合代理端口", "netstat -tln | grep 7890", Host.Adb) {
            captured("2026-10-01", "tcp6       0      0 [::]:7890               [::]:*                  LISTEN")
            note = "7890 是 Clash 的 HTTP/SOCKS 混合端口。`[::]` 表示监听所有地址（IPv4 也能连）。"
        }
        read("VPN 附带的 HTTP 代理", "dumpsys connectivity 2>/dev/null | grep -m1 -oE 'HttpProxy: \\[[^]]*\\] [0-9]+'", Host.Adb) {
            varies = true
            captured("2026-10-01", "HttpProxy: [127.33.187.145] 37855")
            note = "Clash 还给 VPN 网络设置了一个本机 HTTP 代理（地址和端口每次启动随机），并附带不走代理的名单（局域网、国内常用站点等）。支持系统代理的 App 会直接用它。"
        }
        read("全局 http_proxy 设置", "settings get global http_proxy", Host.Adb) {
            captured("2026-10-01", "null")
            note = "没有另外设置全局代理。有 VPN 就不需要它。"
        }
    }

    steps("备用：不用 VPN 时的代理办法") {
        change("设置全局 HTTP 代理", "settings put global http_proxy 127.0.0.1:7890", Host.Adb) {
            note = "只对遵守系统代理设置的 App 有效（浏览器、部分视频 App）。VPN 正常时用不着。"
        }
        change("清除全局代理", "settings put global http_proxy :0", Host.Adb) {
            note = "`:0` 表示不使用代理。"
        }
        change("只给某个 App 设代理", "", Host.Tv) {
            note = "SmartTube、TV Bro 等 App 自己的设置里有代理选项，填 `127.0.0.1` 端口 `7890` 即可，只影响这个 App。"
        }
    }

    consequences {
        text("""
            • Clash 的规则里局域网地址要设成直连（DIRECT），否则 Deck 和投影仪之间的 ADB、SSH、局域网共享可能受影响。上面的 HTTP 代理排除名单里已经有 192.168.*。
            • 订阅链接和节点信息不要写进笔记或截图分享。
        """)
    }

    lesson("经验") {
        text("截图里看到 SimpleSSHD 列出的 IP 有一个 172.19.0.1，顺着它查到 tun0，才发现 VPN 其实在运行。**留意不寻常的细节**往往能推翻错误的假设。")
    }

    audit {
        claim("极米系统删减了 VpnDialogs.apk，导致应用内点击「启动 VPN (TUN 模式)」必定崩溃。", Verdict.Disproved,
            "`com.android.vpndialogs` 存在且启用；Clash Meta 正以 VPN 模式运行（tun0、vpn_management 都能看到）。")
        claim("方案 1（第三方应用内独立代理）：如 SmartTube 或 TV Bro 内置网络设置直接填入代理地址 127.0.0.1 端口 7890，完全不调用系统 VPN 接口。方案 2：命令行注入系统级 HTTP 代理 settings put global http_proxy 127.0.0.1:7890。", Verdict.Unverified,
            "两个办法本身可行（7890 端口在监听），但这台机器上 VPN 一直在用，没有改用这两种方式测试。")
        read("旧版：查看 Clash Meta 后台进程与监听端口", "ps -ef | grep -i metacubex && netstat -tlpn | grep 7890", Host.Adb) {
            verdict = Verdict.Confirmed
            varies = true
            captured("（旧记录）", """
                u0_a69  7124  2665 3 19:34:05 ? 00:08:19 com.github.metacubex.clash.meta:background
                tcp6       0      0 [::]:7890               [::]:*                  LISTEN      -
            """)
            note = "实测一致：`:background` 进程 uid 是 u0_a69（10069），7890 在监听。"
        }
    }

    related("app-install-order", "lan-share")
}
