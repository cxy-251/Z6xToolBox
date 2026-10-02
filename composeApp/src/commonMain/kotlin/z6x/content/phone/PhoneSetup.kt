package z6x.content.phone

import z6x.framework.Host
import z6x.framework.module

val PhoneAdb = module("phone-adb", "无线调试：连接、开关与自动关闭") {
    keywords = "adb pair · mDNS · USB 安装 · USB 调试（安全设置） · Wi-Fi 断开"
    overview = """
        手机没有常开的 ADB，只能使用安卓 11 起提供的无线调试：首次需配对，之后每次开启端口都不同。
        HyperOS 另有两个开关，分别控制通过 ADB 安装应用，以及模拟按键、授予权限。
    """
    verified("2026-10-02")

    steps("连接") {
        change("首次配对", "adb pair 192.168.0.104:<配对端口> <配对码>", Host.Deck) {
            note = "在手机「开发者选项 → 无线调试 → 使用配对码配对设备」中查看配对端口和配对码。配对一次即可，Deck 的密钥会被手机记住。"
        }
        read("查找当前端口", "adb mdns services", Host.Deck) {
            varies = true
            captured("2026-10-02", "adb-3255e642-X3mb5O\t_adb-tls-connect._tcp\t192.168.0.104:41857")
            note = "无需到手机上查看端口：已配对的手机会通过 mDNS 广播当前端口。记录可能过时（关闭后旧端口仍会显示一段时间），连接被拒绝时以最新一条为准。"
        }
        change("连接", "adb connect 192.168.0.104:<端口>", Host.Deck)
    }

    why("无线调试为何自动关闭") {
        facts(
            "Wi-Fi 断开" to "安卓的设计：Wi-Fi 一断开（哪怕只有一瞬间），无线调试即自动关闭。2026-10-02 实测：日志中先出现 `ConnectivityServiceImpl: teardown wifi`，随即 `adbd: adb wifi stopped`。手机连接 2.4GHz 时信号不稳，更容易发生",
            "切换到第二空间" to "无线调试只属于主空间，切换空间后连接中断，需回到主空间重新开启",
            "重启" to "重启后无线调试为关闭状态",
            "adbd 是否随之退出" to "Wi-Fi 断开时只是无线监听停止，adbd 进程本身不变（进程号相同）；但关闭开关会停止 adbd，并结束它启动的全部进程（见「hub 改在 Termux 中运行」）",
        )
    }

    why("HyperOS 的两个附加开关") {
        facts(
            "USB 安装" to "开发者选项 →「USB 安装」（`persist.security.adbinstall`）。关闭时 `adb install` 报 `INSTALL_FAILED_USER_RESTRICTED`。开启后每次安装仍会在手机上弹出确认窗口（手机管家的 AdbInstallActivity），未确认则同样失败（日志为 `Install canceled by user`）",
            "USB 调试（安全设置）" to "`persist.security.adbinput`。开启后 ADB 才能模拟按键（`input`）和授予权限（`pm grant`）。Termux 的初始化命令就是用 `input text` 自动输入的",
            "病毒扫描组件" to "通过 ADB 安装前，HyperOS 会用 `com.miui.guardprovider` 扫描安装包。该组件被移除后安装失败，报 `Invalid apk`。因此它不在精简名单中",
        )
    }
    related("phone-facts", "phone-debloat", "phone-hub")
}

val PhoneTermux = module("phone-termux", "Termux：升级、国内镜像与 SSH") {
    keywords = "Termux 0.119 · 签名 · 清华镜像 · openssh · 8022 · run-as"
    overview = """
        Termux 提供完整的 Linux 软件包环境，以普通应用身份运行。手机上原有的是 2024 年安装的 0.119.0-beta.1，现改为 GitHub 发布的 0.119.0-beta.3（2025-05-22），并开启只允许密钥登录的 SSH。
    """
    verified("2026-10-02")

    story("经过") {
        text("""
            1. **原地升级失败**：GitHub 版的签名与已安装版本不一致（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。签名不一致时安卓只会拒绝安装，不会删除数据。用户确认 Termux 中没有需要保留的内容后，卸载重装。
            2. **无法从 ADB 查看 Termux 内部**：只有可调试（debuggable）的应用才能 `run-as`，Termux 的发布版不可调试。
            3. **初始化不需要在手机上打字**：开启「USB 调试（安全设置）」后，用 `input text` 向 Termux 输入一行命令，从 Deck 临时提供的下载地址取得初始化脚本并执行。
            4. **下载极慢**：默认软件源在海外，国内速度只有几 B/s。改用清华镜像后，升级与安装 OpenSSH 用时 45 秒，无需开启代理。
            5. **镜像设置被覆盖**：升级时使用了 `--force-confnew`（遇到配置冲突时采用新版配置），`sources.list` 被软件包自带的默认配置覆盖，回到了海外源。升级完成后需再次写入。
        """)
    }

    steps("配置") {
        change("改用清华镜像", "echo 'deb https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main stable main' > \$PREFIX/etc/apt/sources.list && apt update", Host.Termux)
        change("安装 OpenSSH 并只允许密钥登录", "pkg install -y openssh && sed -i -E 's/^#?PasswordAuthentication .*/PasswordAuthentication no/' \$PREFIX/etc/ssh/sshd_config && sshd", Host.Termux) {
            note = "Deck 的公钥写入 `~/.ssh/authorized_keys`。手机使用单独的密钥 `~/.ssh/phone_ed25519`，不与投影仪共用。sshd 监听 8022 端口。"
        }
        read("从 Deck 登录", "id -un; echo \$TERMUX_VERSION", Host.Termux) {
            varies = true
            captured("2026-10-02", "u0_a639\n0.119.0-beta.3")
            note = "u0_a639 即 uid 10639，是 Termux 这个应用的身份，权限与普通应用相同。"
        }
        change("授予存储权限", "pm grant com.termux android.permission.READ_EXTERNAL_STORAGE; pm grant com.termux android.permission.WRITE_EXTERNAL_STORAGE", Host.PhoneAdb) {
            note = "Termux 的 targetSdk 为 28，获得这两项权限即可读写 `/storage/emulated/0`（资源库所在位置）。"
        }
        change("免于省电限制", "dumpsys deviceidle whitelist +com.termux; cmd appops set com.termux RUN_ANY_IN_BACKGROUND allow", Host.PhoneAdb) {
            note = "避免灭屏后 Termux 及其中的 hub 被系统休眠或结束。恢复：`dumpsys deviceidle whitelist -com.termux`。"
        }
    }

    consequences("普通应用身份的限制（实测）") {
        facts(
            "netlink" to "不能使用：`ip addr`、`ip route`、`ip neigh` 均报 `Cannot bind netlink socket: Permission denied`",
            "faccessat2" to "安卓应用的 seccomp 过滤不允许这个系统调用。Go 程序查找可执行文件（exec.LookPath）时会用到它，进程直接收到 SIGSYS 而退出，而不是得到「不支持」的错误后回退",
            "/proc/stat" to "不可读，hub 的 CPU 使用率显示为 0",
            "可以做到的" to "监听 1024 以上端口、读写机身存储（授予权限后）、UDP 组播发现（SSDP）、读取温度和内存",
        )
    }
    related("phone-adb", "phone-hub")
}

val PhoneTransfer = module("phone-transfer", "向手机传输大量文件") {
    keywords = "MTP · adb push · .nomedia · 媒体库扫描 · USB 2.0"
    overview = """
        用户曾从电脑向手机传输数万首音乐，一次传输大概率卡死，只能分批进行；在手机上直接解压 50GB 以上的压缩包也失败了。本节说明原因与更可靠的做法。
    """
    partial("2026-10-02")

    why("卡死的原因（分析，未专门测试）") {
        facts(
            "MTP 协议" to "电脑经数据线访问手机存储默认使用 MTP：一次只传一个文件，每个文件都有多次往返确认，文件数以万计时开销巨大，电脑端（尤其是 Windows）与手机端都容易卡住或断开。分批传输能成功也符合这一特点",
            "媒体库扫描" to "每写入一个音频或视频，系统都要读取标签、封面并写入媒体数据库。实测媒体库中收录了 24671 条音频，与该目录中的 mp3 数量基本一致；连续写入数万个文件时，后台扫描持续占用处理器和存储",
            "手机上解压超大压缩包" to "超过 4GB 或 65535 个文件的压缩包需要 Zip64 格式，手机上的解压工具不一定支持；解压还需要再占用同样大小的空间",
        )
    }

    steps("推荐做法") {
        change("先放 .nomedia，阻止媒体库扫描该目录", "adb -d shell touch /storage/emulated/0/omni_library/.nomedia", Host.Deck) {
            note = "`-d` 表示「经数据线连接的设备」，无需输入设备序列号。hub 直接读取文件，不受 .nomedia 影响；附带效果是这些视频、音频不出现在相册和音乐应用中。"
        }
        change("用 adb push 传输整个目录", "adb -d push <Deck 上的目录> /storage/emulated/0/omni_library/media_library/<分区>/", Host.Deck) {
            note = "`adb push` 使用 ADB 自己的传输协议，可连续发送整个目录，不存在 MTP 的逐个确认问题。需先在开发者选项中打开「USB 调试」（与无线调试是两个开关）。这款手机按公开规格为 USB 2.0（未实测），实际约 30～40MB/s，300GB 约需 2.5～3 小时；同期 Wi-Fi 只有约 1MB/s。"
        }
        change("在手机上解压大压缩包", "pkg install unzip && unzip <文件>.zip -d <目标目录>", Host.Termux) {
            note = "Termux 的 unzip 支持 Zip64。"
        }
    }

    consequences("不能传到第二空间") {
        text("""
            从 ADB 访问第二空间的存储 `/storage/emulated/10` 被拒绝（Permission denied，2026-10-02 实测）；hub 运行在主空间的 Termux 中，也读不到第二空间的文件。媒体资源因此放在主空间。切换空间不改变手机的 IP（Wi-Fi 为整机共用），但会使无线调试关闭，重新开启后端口改变。
        """)
    }
    related("phone-hub", "phone-adb")
}
