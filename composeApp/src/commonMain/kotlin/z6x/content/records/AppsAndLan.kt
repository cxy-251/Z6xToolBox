package z6x.content.records

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val LanShare = module("lan-share", "局域网传递文本：解决电视端无法复制粘贴") {
    keywords = "python http.server · TV Bro · 剪贴板"
    overview = """
        代理订阅链接、Token 这类长达数十乃至上百个字符的内容，无法用遥控器逐字输入。
        解决方法：在 Deck 上开启一个局域网网页，电视用浏览器打开后选中并复制，内容即进入电视的剪贴板。
    """
    partial("2026-10-01")

    story("经过") {
        text("""
            1. 在 Deck 上把要传递的文本写入文件，用 Python 自带的 HTTP 服务在局域网内共享。
            2. 电视上安装 TV Bro 浏览器（通过 U 盘安装，见「U 盘安装应用」），它提供由遥控器控制的虚拟鼠标指针。
            3. 在 TV Bro 中打开 Deck 的网页，用指针长按选中文本并复制。
            4. 切换到目标应用（如 Clash），在输入框中粘贴。
        """)
        text("""
            此方法后来改进为 shared 目录中的 `index.html`：页面上的大按钮可一键复制，也可直接下载 .apk1 安装包。
            该页面包含代理节点信息，因此**不收录于此**。
        """)
    }

    steps("在 Deck 上开启共享") {
        read("查 Deck 的局域网 IP", "ip -4 addr show | grep inet", Host.Deck) {
            captured("2026-10-01", """
                    inet 127.0.0.1/8 scope host lo
                    inet 192.168.0.21/24 brd 192.168.0.255 scope global noprefixroute wlan0
            """)
            note = "wlan0 一行中的 192.168.0.21 是 Deck 的局域网地址。127.0.0.1 是本机回环地址，电视无法访问。"
        }
        change("准备要传递的文本", "mkdir -p ~/share && echo '要传的长文本' > ~/share/text.txt", Host.Deck)
        read("在 8000 端口开启共享", "python3 -m http.server 8000 --directory ~/share", Host.Deck) {
            manual = true
            note = "`-m http.server` 运行 Python 自带的静态文件服务。该命令持续运行，按 Ctrl+C 停止；它只读取文件，不做任何修改。"
            outcome = "显示 Serving HTTP on 0.0.0.0 port 8000。在电视浏览器中打开 http://192.168.0.21:8000/text.txt 即可看到文本。"
        }
        read("在 Deck 上测试能否访问", "curl -s http://127.0.0.1:8000/text.txt", Host.Deck) {
            manual = true
            note = "在共享运行期间另开一个终端执行。`-s` 表示不显示进度条。"
        }
        read("仅查看响应头，测试服务是否可达", "curl -I http://192.168.0.21:8000/", Host.Deck) {
            manual = true
            note = "`-I` 只请求响应头，第一行为 `HTTP/1.0 200 OK` 即表示服务可达。`-v` 可显示完整的连接过程。"
        }
        change("断点续传下载大文件", "wget -c http://192.168.0.21:8000/app.apk1 -O /tmp/app.apk1", Host.Deck) {
            manual = true
            note = "`-c` 表示中断后重新执行时从断点继续，`-O` 指定保存路径。适用于在另一台 Linux 设备上从 Deck 下载文件。"
        }
    }

    consequences {
        text("""
            • 共享开启期间，同一局域网内的**任何设备**都能读取该目录。传递敏感内容（订阅链接、密码）后应立即按 Ctrl+C 关闭共享，并删除文件。
            • 获得 ADB 后有更直接的方法：`adb shell input text '文字'` 可将文本直接输入电视当前的输入框（不支持中文和部分符号）。
        """)
    }

    audit {
        claim(
            "电视端安装的 TV Bro 浏览器按遥控器菜单键可开启「虚拟鼠标指针」模式；在 TV Bro 中打开局域网网页，用指针模式长按选中文本点击复制，该长文本便直接进入了电视系统的全局剪贴板；切换到目标应用（如 Clash），在输入框中长按确定键即可直接粘贴完成。",
            Verdict.Unverified,
            "这是当时的操作经历，未重新演示。已确认 TV Bro（com.phlox.tvwebbrowser）已安装，并且是系统默认浏览器。",
        )
        claim(
            "Serving HTTP on 0.0.0.0 port 8000 (http://0.0.0.0:8000/) ...   # 局域网服务已启动",
            Verdict.Unverified,
            "这是 `python3 -m http.server` 的标准启动提示。由于开启共享会把目录暴露给局域网，本次未重新运行。",
        )
        claim(
            "inet 192.168.0.21/24 ...   # 记录此 IP 供电视端访问",
            Verdict.Confirmed,
            "Deck 的 wlan0 地址确为 192.168.0.21（见上方实测输出）。",
        )
    }

    related("usb-apk1", "app-install-order")
}

val AppInstallOrder = module("app-install-order", "应用的安装顺序与兼容性") {
    keywords = "32 位 · armeabi-v7a · 闪退 · unzip -l"
    overview = """
        在没有 ADB 的阶段，按需求依次安装应用：先解决输入和文件传输，再获取命令行，最后配置网络。其间两个应用闪退，一个无法安装。
    """
    partial("2026-10-01")

    story("安装顺序") {
        text("""
            1. **TV Bro 浏览器**：提供虚拟鼠标指针，用于复制粘贴和下载安装包。运行正常。
            2. **SimpleSSHD**：在电视上运行 SSH 服务，获得命令行。运行正常。
            3. **v2rayNG**：用于配置代理。可以安装，但一连接即闪退。未深入排查，直接更换方案。
            4. **Clash Meta**：替代 v2rayNG。运行正常；局域网地址需设为直连（DIRECT），否则访问内网也会经过代理。
            5. **Aurora Store**：用于安装 Google Play 上的应用。启动后闪退，原因未查明。
            6. **仅含 64 位库的应用**：安装器报错 `INSTALL_FAILED_NO_MATCHING_ABIS`。原因明确：系统只有 32 位运行库。
            7. **Projectivy Launcher、SmartTube**：分别用于替换桌面和观看视频。运行正常。
        """)
        text("""
            之后有了 ADB，改为在 Deck 上下载、核对校验值后用 `adb install` 安装：
            8. **Termux、Termux:Boot**（2026-10-02）：用于投影仪开机自动启动 hub 与 keymap，见「开机自动启动 hub 与 keymap」。
            9. **Kodi 21.3**（2026-10-02）：从官方镜像 mirrors.kodi.tv 下载 armeabi-v7a 版（`kodi-21.3-Omega-armeabi-v7a.apk`），与官方提供的 sha256 一致后安装。运行正常，可从桌面启动；已加入 hub「遥控器按键」与「任务管理」的应用名称表。
        """)
    }

    steps("Kodi 的安装与核对") {
        change("安装", "adb -s 192.168.0.109:5555 install kodi-21.3-Omega-armeabi-v7a.apk", Host.Deck) {
            note = "必须选 armeabi-v7a（arm 目录）版本；arm64 目录的版本在这台投影仪上无法安装（见下文 abilist64 为空）。安装前用 `sha256sum` 与官方同名 .sha256 文件对比。"
        }
        read("确认架构、版本与桌面入口", "dumpsys package org.xbmc.kodi | grep -m2 -oE 'versionName=[^ ]+|primaryCpuAbi=[^ ]+'; cmd package resolve-activity --brief -c android.intent.category.LEANBACK_LAUNCHER org.xbmc.kodi | tail -1", Host.Adb) {
            captured("2026-10-02", """
                primaryCpuAbi=armeabi-v7a
                versionName=21.3
                org.xbmc.kodi/.Splash
            """)
        }
    }

    steps("安装前检查 APK 支持的架构") {
        read("查看 APK 里的原生库目录", "unzip -l app.apk | grep -oE 'lib/[^/]+/' | sort | uniq -c", Host.Deck) {
            note = """
                APK 本质上是 zip 文件。`unzip -l` 只列出内容而不解压；原生库位于 `lib/<架构>/` 目录下。
                `grep -oE` 只提取目录名，`sort | uniq -c` 去重并计数。
                **结果中有 `armeabi-v7a` 即可安装；只有 `arm64-v8a` 则无法安装。** 若无任何输出，说明是纯 Java/Kotlin 应用，同样可以安装。
            """
        }
        text("以下用 shared 目录中实际安装过的安装包验证（.apk1 文件同样可以用 unzip 查看）：")
        read("SmartTube（只带 32 位）", "unzip -l 01_YouTube_SmartTube_32bit.apk1 | grep -oE 'lib/[^/]+/' | sort | uniq -c", Host.Deck) {
            manual = true
            captured("2026-10-01", "      4 lib/armeabi-v7a/")
            note = "在 shared 目录中执行。文件名中的 32bit 表示下载时特意选择的 32 位版本。"
        }
        read("SimpleSSHD（通用包）", "unzip -l SimpleSSHD_RemoteTerminal.apk1 | grep -oE 'lib/[^/]+/' | sort | uniq -c", Host.Deck) {
            manual = true
            captured("2026-10-01", """
                      5 lib/arm64-v8a/
                      5 lib/armeabi-v7a/
                      5 lib/x86/
                      5 lib/x86_64/
            """)
            note = "通用包（universal）包含四种架构，安装时系统自动选择适用的一种。"
        }
        read("在投影仪上确认支持的架构", "getprop ro.product.cpu.abilist; getprop ro.product.cpu.abilist64", Host.Adb) {
            captured("2026-10-01", """
                armeabi-v7a,armeabi

            """)
            note = "第二行 abilist64 **为空**：系统不支持任何 64 位 ABI。这是纯 64 位应用无法安装的根本原因。"
        }
        read("当前已安装的第三方应用", "pm list packages -3", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                package:de.szalkowski.activitylauncher.oss
                package:com.spocky.projengmenu
                package:org.smarttube.stable
                package:com.phlox.tvwebbrowser
                package:com.cxinventor.file.explorer
                package:org.galexander.sshd
                package:com.github.metacubex.clash.meta
            """)
            note = "`-3` 只列出第三方（用户安装的）应用，依次为 Activity Launcher、Projectivy、SmartTube、TV Bro、Cx 文件浏览器、SimpleSSHD、Clash Meta。"
        }
    }

    audit {
        claim(
            "v2rayNG 闪退原因：极米系统在精简系统组件时，删除了 Android 原生的 VpnDialogs（VPN 权限授权确认弹窗）应用。v2rayNG 尝试调用系统 VpnService.prepare() 弹出授权窗口时，系统找不到该 Activity 抛出异常崩溃。",
            Verdict.Disproved,
            "`com.android.vpndialogs` **已安装且处于启用状态**；Clash Meta 正以系统 VPN 模式运行（见「代理：Clash Meta 以 VPN 模式运行」）。由于已改用 Clash Meta，真正原因不再追查。",
        )
        claim(
            "Aurora Store 闪退原因：系统完全缺少 GMS（Google 移动服务）核心框架，且 Z6X Pro 的 240 DPI 密度导致手机/平板版布局计算溢出崩溃。",
            Verdict.Unverified,
            "未抓取崩溃日志。Aurora Store 本身是为没有 Google 服务的设备设计的，「缺少 GMS」这一解释存疑；「240 DPI 布局溢出」缺乏依据。Aurora 已卸载。",
        )
        claim(
            "测试安装纯 64 位 APK：系统安装器直接报错 INSTALL_FAILED_NO_MATCHING_ABIS。原因：极米虽然使用了 64 位 Linux 内核（armv8l），但系统运行库全部裁剪为 32 位（armeabi-v7a）。",
            Verdict.Confirmed,
            "`ro.product.cpu.abilist64` 为空，`abi` 为 armeabi-v7a，内核为 64 位。报错本身是当时的经历。",
        )
        claim(
            "SimpleSSHD：安装成功，监听 2222 端口，Steam Deck 可通过终端直接远程登录。",
            Verdict.Confirmed,
            "2222 端口属于 uid 10068（SimpleSSHD），可通过 `ssh z6x` 登录。",
        )
        claim(
            "ClashMetaforAndroid：安装成功，正常运行。分流规则中需将局域网段设为 DIRECT 直连，避免内网访问失败。",
            Verdict.Confirmed,
            "Clash Meta 正在运行，其代理排除列表中包含 192.168.*。",
        )
        claim(
            "Projectivy Launcher 与 SmartTube：安装成功，运行流畅无异常。",
            Verdict.Confirmed,
            "两者均已安装；Projectivy 是当前桌面，SmartTube 正在使用。",
        )
    }

    related("usb-apk1", "ssh-probe", "lan-share")
}
