package z6x.content.records

import z6x.framework.Host
import z6x.framework.module

val LanShare = module("lan-share", "局域网传文字：解决电视上没法复制粘贴") {
    keywords = "python http.server · TV Bro · 剪贴板"
    overview = """
        代理订阅链接、Token 这类几十上百个字符的内容，用遥控器一个个按根本不现实。
        办法是在 Deck 上开一个局域网网页，电视用浏览器打开，选中复制，就进了电视的剪贴板。
    """
    partial("2026-10-01")

    story("经过") {
        text("""
            1. Deck 把要传的文字写进一个文件，用 Python 自带的 HTTP 服务在局域网共享出来。
            2. 电视上装好 TV Bro 浏览器（U 盘装的，见「U 盘装 App」），它支持用遥控器控制的虚拟鼠标指针。
            3. TV Bro 打开 Deck 的网页，用指针长按选中文字、复制。
            4. 切到目标 App（比如 Clash），在输入框里粘贴。
        """)
        text("""
            后来这个办法升级成了 shared 目录里的 `index.html`：一个大按钮的网页，点一下就复制，还能直接下载 .apk1 安装包。
            那个页面里有代理节点信息，所以**不放进这里**。
        """)
    }

    steps("在 Deck 上开共享") {
        read("查 Deck 的局域网 IP", "ip -4 addr show | grep inet", Host.Deck) {
            captured("2026-10-01", """
                    inet 127.0.0.1/8 scope host lo
                    inet 192.168.0.21/24 brd 192.168.0.255 scope global noprefixroute wlan0
            """)
            note = "wlan0 那行的 192.168.0.21 就是 Deck 在局域网里的地址。127.0.0.1 是本机回环，电视访问不到。"
        }
        change("准备要传的文字", "mkdir -p ~/share && echo '要传的长文本' > ~/share/text.txt", Host.Deck)
        read("在 8000 端口开共享", "python3 -m http.server 8000 --directory ~/share", Host.Deck) {
            manual = true
            note = "`-m http.server` 运行 Python 自带的静态文件服务。它会一直运行，按 Ctrl+C 停止。只读不改任何文件。"
            outcome = "显示 Serving HTTP on 0.0.0.0 port 8000。电视浏览器打开 http://192.168.0.21:8000/text.txt 即可看到文字。"
        }
        read("在 Deck 上自测能否访问", "curl -s http://127.0.0.1:8000/text.txt", Host.Deck) {
            manual = true
            note = "共享开着时另开一个终端执行。`-s` 不显示进度条。"
        }
    }

    consequences {
        text("""
            • 开着共享时，同一局域网**任何设备**都能读到这个目录。传完敏感内容（订阅链接、密码）立刻 Ctrl+C 关掉，并删除文件。
            • 有了 ADB 以后还有更直接的办法：`adb shell input text '文字'` 可以把文字直接"打"进电视当前的输入框（不支持中文和部分符号）。
        """)
    }

    lesson("核对说明") {
        text("Deck 端命令已核对；TV Bro 里复制粘贴的操作是当时的经历。")
    }

    related("usb-apk1", "app-install-order")
}

val AppInstallOrder = module("app-install-order", "App 安装顺序与兼容问题") {
    keywords = "32 位 · armeabi-v7a · 闪退 · unzip -l"
    overview = """
        没有 ADB 的阶段，按需要一个个装 App：先解决输入和传文件，再拿命令行，再弄网络。中间两个 App 闪退，一个装不上。
    """
    partial("2026-10-01")

    story("安装顺序") {
        text("""
            1. **TV Bro 浏览器**：有虚拟鼠标指针，解决复制粘贴和下载安装包。正常。
            2. **SimpleSSHD**：在电视上开 SSH 服务，拿到命令行。正常。
            3. **v2rayNG**：配代理。能装上，一连接就闪退。**原因待查**（见下面"核对"）。
            4. **Clash Meta**：替代 v2rayNG。正常，局域网地址要设成直连（DIRECT），否则访问内网会走代理。
            5. **Aurora Store**：想用它装 Google Play 上的应用。启动后闪退。原因待查。
            6. **只有 64 位库的 App**：安装器直接报 `INSTALL_FAILED_NO_MATCHING_ABIS`。原因明确：系统只有 32 位运行库。
            7. **Projectivy Launcher、SmartTube**：替换桌面、看视频。正常。
        """)
    }

    steps("装之前先查 APK 支持的架构") {
        read("查看 APK 里的原生库目录", "unzip -l app.apk | grep -oE 'lib/[^/]+/' | sort | uniq -c", Host.Deck) {
            note = """
                APK 本质是 zip。`unzip -l` 只列出内容不解压；原生库放在 `lib/<架构>/` 下。
                `grep -oE` 只取出目录名，`sort | uniq -c` 去重并计数。
                **结果里有 `armeabi-v7a` 就能装；只有 `arm64-v8a` 就装不了。** 什么都没列出说明是纯 Java/Kotlin 应用，也能装。
            """
        }
        text("用 shared 目录里实际装过的安装包跑了一遍（.apk1 一样能用 unzip 查看）：")
        read("SmartTube（只带 32 位）", "unzip -l 01_YouTube_SmartTube_32bit.apk1 | grep -oE 'lib/[^/]+/' | sort | uniq -c", Host.Deck) {
            manual = true
            captured("2026-10-01", "      4 lib/armeabi-v7a/")
            note = "在 shared 目录里执行。文件名里的 32bit 就是下载时特意选的 32 位版本。"
        }
        read("SimpleSSHD（通用包）", "unzip -l SimpleSSHD_RemoteTerminal.apk1 | grep -oE 'lib/[^/]+/' | sort | uniq -c", Host.Deck) {
            manual = true
            captured("2026-10-01", """
                      5 lib/arm64-v8a/
                      5 lib/armeabi-v7a/
                      5 lib/x86/
                      5 lib/x86_64/
            """)
            note = "通用包（universal）四种架构都带，安装时系统自动挑能用的那一种。"
        }
        read("在投影仪上确认支持的架构", "getprop ro.product.cpu.abilist; getprop ro.product.cpu.abilist64", Host.Adb) {
            captured("2026-10-01", """
                armeabi-v7a,armeabi

            """)
            note = "第二行 abilist64 是**空的**：系统不支持任何 64 位 ABI。这就是纯 64 位 App 装不上的根本原因。"
        }
        read("现在装着的第三方 App", "pm list packages -3", Host.Adb) {
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
            note = "`-3` 只列第三方（用户装的）应用。依次是 Activity Launcher、Projectivy、SmartTube、TV Bro、Cx 文件浏览器、SimpleSSHD、Clash Meta。"
        }
    }

    lesson("核对旧记录时发现的问题") {
        text("""
            • 旧版说 v2rayNG 闪退是因为"极米删除了 VpnDialogs（VPN 授权弹窗）"。实测 `com.android.vpndialogs` **装着且是启用状态**，这个解释不成立。真正原因还没查，需要重装 v2rayNG 后用 `logcat -b crash` 抓崩溃日志。
            • 旧版说 Aurora 闪退是因为"缺 GMS + 240 DPI 布局溢出"。Aurora Store 本身就是为没有 Google 服务的设备设计的，这个解释也站不住，原因待查。
            • 32 位限制、架构检查方法、现装 App 列表都已核实。
        """)
    }

    related("usb-apk1", "ssh-probe", "lan-share")
}
