package z6x.content.records

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val DeckHdmi = module("deck-hdmi", "Steam Deck 外接投影仪无信号") {
    keywords = "kscreen-doctor · 分辨率 · 刷新率"
    overview = """
        Steam Deck 用 Type-C 转 HDMI 接投影仪，投影仪提示无信号。把 Deck 的外接输出改成 1920x1080@60 后正常显示。
    """
    partial("2026-10-01")

    story("经过") {
        text("""
            这台 Deck 平时接一台 2K/144Hz 显示器。换接投影仪后黑屏无信号，手动把外接输出设成 **1920x1080@60** 就亮了。
            **推测**原因：KDE 会记住外接屏的分辨率和刷新率配置，换了显示设备后沿用了不合适的模式，投影仪不接受。
            投影仪的光机是 1080p，设成 1080p@60 正好点对点显示，字也最清楚。
        """)
    }

    steps("用 kscreen-doctor 调整外接屏") {
        read("列出所有显示输出", "kscreen-doctor -o", Host.Deck) {
            note = """
                先看清**接口叫什么名字**。每个 Output 下面列出 enabled/connected 状态、支持的模式和当前位置（Geometry）。
                Deck 的 Type-C 口输出的是 DisplayPort 信号，外接屏一般叫 **DP-1**，转接成 HDMI 后名字也不变。
            """
            outcome = "2026-10-01 在扩展坞接 2K 显示器时：内置屏 eDP-1（1280x800），外接屏 DP-1（当前 2560x1440@60，可选模式里有 1920x1080@60）。"
        }
        change("把外接屏设成 1080p@60", "kscreen-doctor output.DP-1.mode.1920x1080@60", Host.Deck) {
            note = "`DP-1` 换成上一步查到的名字。模式写法是 `宽x高@刷新率`，必须是上一步列出的模式之一。"
            outcome = "外接屏重新同步，投影仪亮起。设置会被 KDE 记住，下次接入自动使用。"
        }
        change("把外接屏放在 Deck 屏幕右边", "kscreen-doctor output.DP-1.position.1280,0", Host.Deck) {
            note = "坐标是外接屏左上角在整个虚拟桌面里的位置。Deck 屏幕宽 1280，所以 1280,0 就是紧贴右边，鼠标往右移就进入投影画面。"
        }
        change("暂时关掉外接屏（不拔线）", "kscreen-doctor output.DP-1.disable", Host.Deck) {
            note = "重新打开：`kscreen-doctor output.DP-1.enable`"
        }
        change("缩放设为 100%", "kscreen-doctor output.DP-1.scale.1", Host.Deck)
        change("旋转外接屏", "kscreen-doctor output.DP-1.rotation.left", Host.Deck) {
            note = "可选值（来自 `kscreen-doctor --help`）：`none` 不旋转、`left` 逆时针 90°、`right` 顺时针 90°、`inverted` 180°。投影仪吊装倒挂时可能用得上。"
        }
    }

    audit {
        claim(
            "Steam Deck 此前曾连接过 2K/144Hz 外部显示器，KDE 桌面服务（KWin）在本地持久化缓存了该输出端口的配置时序。……Z6X Pro 的 HDMI 接口最大仅支持 1080p@60Hz（或 4K@60Hz 降采样），无法识别 144Hz 高刷时序，导致 EDID 握手超时、投影仪直接提示「无信号」并黑屏。",
            Verdict.Unverified,
            "「改成 1080p@60 就亮了」是亲身经历。KDE 沿用旧配置、EDID 握手超时这套原因没法复现验证，页面里写成推测。",
        )
        claim(
            "对焦距离限制：Z6X Pro 激光/TOF 最低有效对焦距离为 0.8 米，若距离过近会导致对焦算法拉风箱且画面模糊。",
            Verdict.Unverified,
            "命令行查不到对焦参数，也没有找到官方说明。",
        )
        claim(
            "避免 4K 缩放：虽然投影仪支持接收 4K@60Hz 信号，但由于物理光机是 1080p，4K 降采样会导致字体边缘发虚，日常当作副屏务必保持 1080p 点对点输出。",
            Verdict.Unverified,
            "系统分辨率 1920x1080 已实测（`wm size`）。能否接收 4K 信号、字体发虚程度没有测试。",
        )
        read("旧版：查看显示接口，预期 HDMI-A-1 已连接", "kscreen-doctor -o", Host.Deck) {
            verdict = Verdict.Disproved
            note = "旧版写的预期输出是 `Output: 2 HDMI-A-1 enabled connected priority 2 ... 1920x1080@60`。实测 Deck 的外接口叫 **DP-1**（Type-C 输出的是 DP 信号），没有 HDMI-A-1。"
        }
        change("旧版：强制外接 HDMI 输出 1080p@60Hz", "kscreen-doctor output.HDMI-A-1.mode.1920x1080@60", Host.Deck) {
            verdict = Verdict.Disproved
            note = "接口名不存在，命令无效。正确写法见上面的 `output.DP-1.mode.1920x1080@60`。"
        }
        change("旧版：旋转外接屏", "kscreen-doctor output.HDMI-A-1.rotation.normal", Host.Deck) {
            verdict = Verdict.Disproved
            note = "接口名不对；旋转参数也没有 `normal`。`kscreen-doctor --help` 列出的是 `none, left, right, inverted`。"
        }
    }

    related("usb-apk1")
}

val UsbApk1 = module("usb-apk1", "U 盘安装应用：修改扩展名绕过拦截") {
    keywords = "文件管理器 · PackageInstaller · 批量改名"
    overview = """
        还没有 ADB 的时候，只能用 U 盘装 App。系统文件管理器不让直接点 .apk 安装，把后缀改成 .apk1 再打开，就能选系统安装器装上。
    """
    partial("2026-10-01")

    story("经过") {
        text("""
            1. 在文件管理器里点 .apk：被拦下，装不了。
            2. 改名成 .apk1：文件管理器认不出类型，弹出"打开方式"。
            3. 选系统的**软件包安装程序**：安装器只看文件内容（APK 本质是 zip 包），不看后缀，于是正常进入安装界面。
        """)
        text("shared 目录里的安装包至今还保留着 .apk1 后缀，就是这个时期留下的。")
    }

    steps("在 Deck 上准备 U 盘") {
        read("确认 U 盘的文件系统", "lsblk -f", Host.Deck) {
            note = "FSTYPE 一列是 vfat（FAT32）或 exfat 最稳妥，电视对 NTFS 和 ext4 支持不一定好。MOUNTPOINTS 是 U 盘在 Deck 上的挂载路径。"
        }
        change("单个文件改名", "mv my_app.apk my_app.apk1", Host.Deck)
        change("当前目录全部 .apk 改成 .apk1", "for f in *.apk; do [ -f \"\$f\" ] && mv -- \"\$f\" \"\${f}1\"; done", Host.Deck) {
            note = """
                `for f in *.apk` 逐个处理匹配的文件；`[ -f "${'$'}f" ]` 防止没有匹配时把字面的 *.apk 当文件名；
                `--` 防止文件名以 - 开头被当成参数；`${'$'}{f}1` 就是在原名后面加个 1。
            """
        }
        change("全部改回 .apk", "for f in *.apk1; do [ -f \"\$f\" ] && mv -- \"\$f\" \"\${f%.apk1}.apk\"; done", Host.Deck) {
            note = "`${'$'}{f%.apk1}` 去掉结尾的 .apk1。"
        }
        change("Windows CMD 批量改名", "ren *.apk *.apk1", Host.Windows) {
            verdict = Verdict.Unverified
            note = "改回：`ren *.apk1 *.apk`。没有 Windows 电脑，没有实测。"
        }
        change("Windows PowerShell 批量改名", "Get-ChildItem *.apk | Rename-Item -NewName { \$_.Name + '1' }", Host.Windows) {
            verdict = Verdict.Unverified
            note = "`${'$'}_` 代表管道传进来的每个文件。没有 Windows 电脑，没有实测。"
        }
        change("拔盘前把缓存写入 U 盘", "sync", Host.Deck) {
            note = "直接拔盘可能丢掉还在缓存里的数据。"
        }
        change("在电视上安装", "", Host.Tv) {
            note = "插上 U 盘 → 文件管理器找到 .apk1 → 打开方式选「软件包安装程序」→ 安装。"
        }
    }

    consequences {
        text("""
            • 这只是绕过文件管理器的界面拦截，系统安装器本身没有被修改。
            • 装上之前先确认 APK 支持 32 位（armeabi-v7a），否则安装器会报错，见「App 安装顺序与兼容」。
            • 有了 ADB 以后就不用这么麻烦了：`adb install 文件.apk` 直接安装，后缀也无所谓。
        """)
    }

    audit {
        claim(
            "极米系统自带的文件管理器（GMUI 资源管理器）设置了安全白名单限制：直接点击 .apk 文件时，文件管理器会拦截安装意图，弹出提示「禁止安装未知来源应用」或直接无响应。改成 .apk1 后，选择系统「软件包安装程序」，安装器读取文件头（PK 压缩包结构并包含 AndroidManifest.xml），正常进入安装界面。",
            Verdict.Unverified,
            "这是当时的操作经历，现在有 ADB 了没有重新演示。「白名单」「安装器读文件头」是对现象的解释，没有查证。",
        )
        claim(
            "U 盘格式要求：建议格式化为 FAT32 或 exFAT，避免 NTFS 权限问题导致投影仪无法识别。",
            Verdict.Unverified,
            "没有拿 NTFS U 盘测试过。FAT32 / exFAT 兼容性最好是通用经验。",
        )
        claim(
            "安装后清理：安装完成后可在电视上直接删除 .apk1 文件释放内部空间，或通过文件管理器清理安装包缓存。",
            Verdict.Unverified,
            "常识性操作，没有专门测试。",
        )
    }

    related("app-install-order", "lan-share")
}
