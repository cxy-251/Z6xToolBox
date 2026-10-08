package z6x.content.phone

import z6x.framework.Host
import z6x.framework.module

val PhoneDebloat = module("phone-debloat", "精简预装：两个空间分别处理") {
    keywords = "pm uninstall --user · install-existing · Cannot disable system packages · 第二空间"
    overview = """
        按用户要求移除不使用的系统预装：浏览器、应用商店、文件管理、广告与数据上报、小爱与推荐内容、管家附属功能、主题、扫一扫。
        电话、短信、SIM 卡、支付、银行、手机管家本体一律不动；第三方应用由用户自行处理。脚本：`scripts/phone_debloat.sh`。
    """
    verified("2026-10-02")

    why("为什么改用「为当前空间卸载」") {
        text("""
            • 在投影仪上用 `pm disable-user` 停用系统应用，但 HyperOS 禁止 shell 这样做：`SecurityException: Cannot disable system packages`（小米在 PackageManagerServiceImpl 中加入的限制）。用户类的预装（如内容中心、锁屏画报）仍可停用。
            • 改用 `pm uninstall -k --user <空间>`：只为该空间卸载，安装包仍保留在系统分区，`-k` 保留数据。用 `cmd package install-existing --user <空间> <包名>` 即可装回，与停用同样可完全恢复。
            • 每个空间的应用状态相互独立：主空间处理过的应用，在第二空间中仍保持原样，需分别处理。
        """)
    }

    steps {
        change("主空间：精简", "./scripts/phone_debloat.sh apply", Host.Deck) {
            outcome = "28 个：6 个停用，22 个为主空间卸载。"
        }
        change("第二空间：精简（另含日历、时钟、录音机、计算器、指南针、笔记、天气、预装输入法等）", "GROUP=space2 USERID=10 ./scripts/phone_debloat.sh apply", Host.Deck) {
            outcome = "36 个。保留相机、联系人、短信、设置、Play 商店、系统文件选择器、手机管家、桌面和当前输入法 Gboard，否则第二空间无法正常使用。"
        }
        read("查看状态", "./scripts/phone_debloat.sh status", Host.Deck) { varies = true }
        change("恢复", "./scripts/phone_debloat.sh restore", Host.Deck) {
            note = "第二空间加 `GROUP=space2 USERID=10`。"
        }
    }

    story("替代应用") {
        text("""
            • **文件管理**：Material Files（开源，GitHub 发布的 F-Droid 版）。
            • **相册**：Fossify Gallery（开源，无广告）。装好后再移除小米相册和相册编辑器（`GROUP=gallery`）。
            • **浏览器**：主空间保留 Edge 为默认浏览器；第二空间卸载 Edge Dev 与 Beta，改用 Chrome。
            • 已安装在主空间的应用，可用 `cmd package install-existing --user 10 <包名>` 直接装到第二空间，无需重新下载。
        """)
        change("第二空间默认浏览器设为 Chrome", "cmd role add-role-holder --user 10 android.app.role.BROWSER com.android.chrome 0", Host.PhoneAdb)
    }

    consequences("保留且不可移除的组件") {
        facts(
            "手机管家（securitycenter）" to "负责权限弹窗、自启动管理和应用安装确认，移除后可能无法开机",
            "病毒扫描（guardprovider）" to "通过 ADB 安装应用时 HyperOS 依赖它，移除后安装报 `Invalid apk`（2026-10-02 实测后装回）",
            "黄页、AI 通话（aiasst.service）" to "分别提供来电号码识别与通话中的 AI 功能，属于电话相关，按用户要求保留",
            "钱包、银联、NFC 卡包、指纹支付、虚拟 SIM" to "支付与电话卡相关，保留",
        )
    }

    lesson("盘点时的错误") {
        text("""
            最初列出的「从未打开过的应用」中有 Instagram、快手、Edge Dev 等，用户表示早已卸载。核实发现它们安装在第二空间：`pm list packages -3` 默认列出所有空间的应用，`notLaunched` 等状态也要按空间查看（`dumpsys package` 中 `User 0:`、`User 10:` 各有一行）。凡涉及应用的操作都应显式指定 `--user`。
        """)
    }
    related("phone-adb", "phone-facts")
}

val PhoneHub = module("phone-hub", "hub 改在 Termux 中运行") {
    keywords = "adbd cgroup · Termux · UPnP 指纹 · hub start / hub stop"
    overview = """
        手机上的 z6x-hub 最初与投影仪一样从 ADB 启动，后改为在 Termux 中运行：无线调试关闭不再导致 hub 消失，用户也可以在手机上自行开关。
    """
    partial("2026-10-08")

    why("ADB 方式在手机上不可行") {
        text("""
            • 从 ADB 启动的进程与 adbd 位于同一个 cgroup（实测 `/proc/<pid>/cgroup` 均为 `0::/uid_0/pid_19279`），`setsid` 只脱离会话，不脱离 cgroup。
            • 关闭无线调试时系统停止 adbd，并结束整个 cgroup 中的进程。hub 被直接杀死，日志中没有任何退出记录。
            • shell 身份无权将进程移出该 cgroup。投影仪的 ADB 常开，因此不受影响。
        """)
    }

    why("网络守卫在 Termux 中如何工作") {
        text("""
            hub 只在家里的 Wi-Fi 上对外服务（见「规格：z6x-hub」）。以 shell 身份运行时，它用 `ip` 命令读取网卡地址和网关的硬件地址；以普通应用身份运行时，这两条路都不通（netlink 被禁止，调用外部程序会触发 SIGSYS），因此改为：
            • **地址**：向公网地址建立 UDP「连接」（不发送数据），由系统路由选出的本机地址即当前网络的地址，只接受私有地址；
            • **网络指纹**：用 SSDP 找到局域网中的路由器，读取其描述文件中的 UDN（每台设备唯一的 uuid），与 token 一起计算哈希。实测耗时 0.065 秒，结果稳定。路由器关闭 UPnP 时取不到指纹，hub 不会对外服务。
            两种方式得到的指纹不同，可信列表中各保存一个。
        """)
    }

    steps("使用") {
        change("部署（经 SSH）", "./hub/deploy.sh phone", Host.Deck) {
            note = "`hub/devices/devices.txt` 中手机一行的第三列为 termux，部署和开关都改经 SSH 进行。程序位于 Termux 的 `~/z6x-hub/`。"
        }
        change("在手机上开关", "hub start\nhub stop\nhub status", Host.Termux) {
            note = "部署时在 `~/.bashrc` 中加入了别名 `hub`。默认不申请唤醒锁（见下方「唤醒锁实验」），需要时用 `Z6X_WAKELOCK=1 hub start`。"
        }
        change("在 Deck 上开关", "./hub/ctl.sh phone start | stop | status | trust", Host.Deck)
        change("短视频封面生成", "thumbs status\nthumbs log\nthumbs stop\nthumbs start", Host.Termux) {
            note = "后台循环为资源库中的短视频生成封面（放在作品目录的 `.thumbs/` 下），按机身温度调速、过热暂停，每轮只补新作品。一般在 hub 的「🔧 工具 → 后台任务」中开关，参数在「⚙️ 设置」中修改；这里的命令供排查使用。依赖 `pkg install python ffmpeg`。原理见「规格：z6x-hub」第四期。"
        }
        read("浏览器访问", "http://127.0.0.1:8090", Host.Remote) {
            note = "手机自己的浏览器用本机地址，不连网络也能使用；其他设备用手机的局域网地址（只在可信 Wi-Fi 上开放）。登录密码在 hub 设置页修改。"
        }
    }

    why("Termux 会被冻结或结束：两次实测") {
        facts(
            "被冻结（17:20 左右）" to "Termux 退到后台一段时间后，整个进程组被系统冻结（`/sys/fs/cgroup/uid_10639/pid_<pid>/cgroup.freeze` 为 1）：进程仍在、端口仍可建立连接，但 sshd 和 hub 都不回应，网页一直转圈。用 ADB 已加入的省电白名单（deviceidle）和后台运行许可（appops）都无效，这是 HyperOS 自己的冻结机制。把 Termux 调回前台即解除",
            "解决" to "在手机上把 Termux 的「省电策略」改为「无限制」，并打开「自启动」（设置 → 应用设置 → 应用管理 → Termux）。改后在后台观察 10 分钟（17:48～17:57），未再冻结",
            "被结束（17:32）" to "日志：`Force stopping com.termux ... from process:com.miui.securitycenter`，8 秒后 `Powerkeeper ... NoRestrictAppsList add: com.termux`。即修改省电策略的那一刻，手机管家会先强制停止该应用，不是后台清理所致，此后不会反复发生。需重新打开一次 Termux",
            "打开 Termux 自动启动" to "`~/.bashrc` 中检查：sshd 或 hub 未运行则启动。`hub status` 在未运行时返回退出码 1，供此判断（最初返回 0，导致自动启动不生效）",
            "手机重启后" to "已装 Termux:Boot：开机脚本（仓库 `hub/termux/phone-boot.sh`，部署时安装到 `~/.termux/boot/z6x-start.sh`）依次启动 sshd、hub 与封面生成。Termux:Boot 需在安装后手动打开一次，安卓才允许它接收开机广播；被强行停止后同样需要再打开一次（见「开机自动启动 hub 与 keymap」）。开机广播在第一次解锁后才发出，解锁前什么都不会启动",
            "开机自启第一次实测（2026-10-08）" to "✗ 22:50 重启、解锁后，开机脚本在 22:52 执行，hub 与封面生成都已启动，但几分钟内全部被结束。推测原因：开机脚本是 Termux 的一个后台任务，脚本执行完任务即结束，Termux 的服务随之退出，应用变为可回收，系统连同它启动的进程一起结束（手动打开 Termux 时有终端会话保持服务，因此不受影响）。修改：开机脚本最后不退出（`exec sleep`），让服务保持运行；不用唤醒锁。开机脚本只启动 sshd 与 hub，封面生成由 hub 的后台任务启动。**修改后尚待重启实测**",
        )
    }

    verify("唤醒锁实验（2026-10-02）") {
        facts(
            "持有唤醒锁" to "17:25 左右启动（hub start 中执行 termux-wake-lock），18:26 被 HyperOS 结束：日志为 PowerSaveService 判定 Termux 异常耗电（abnormalDataModel … paction=2），原因 AutoPowerKill。省电策略已设为「无限制」也未能避免",
            "不持有唤醒锁" to "19:00 启动后持续运行，20:20 检查仍是同一进程（PID 28622，80 分钟未被结束），期间网页一直可访问。结论：唤醒锁是触发异常耗电判定的原因，改为默认不申请",
        )
    }

    verify("网络守卫的误判与修正（2026-10-02）") {
        facts(
            "现象" to "日志中约每小时出现一次「当前 Wi-Fi 不是可信网络」，十几秒后恢复：手机读取路由器 UPnP 标识偶尔在 2 秒内收不到回复，指纹为空，被当成换了网络",
            "修正" to "取不到指纹且 IP 未变时继续服务，连续 3 次（约 45 秒）取不到才停止；指纹不同或 IP 改变时仍立即停止",
            "顺带发现的缺陷" to "新增的单元测试约四成概率失败：开始监听后马上停止时，http.Server 尚未接手监听器，Close 不会关闭它，端口遗留下来。改为保存每个监听器并在停止时直接关闭，此后连续 30 次通过",
        )
    }

    verify("实测结果（2026-10-02）") {
        facts(
            "运行身份" to "uid 10639（Termux），内存 13MB",
            "功能" to "资源库、游戏页面、存档读写、WebDAV、系统状态均正常；CPU 使用率因 /proc/stat 不可读显示为 0",
            "网络守卫" to "家里的 Wi-Fi 加入可信网络前不对外服务，加入后正常服务",
        )
    }

    lesson("问题记录") {
        text("""
            • **再次用 `pgrep -f` 结束进程，结果结束了执行命令的 shell 自身**（模式同时匹配到了命令行本身）。与「方案调整记录」第 9 条相同：一律按精确的 PID 结束进程。
            • **网速慢的原因曾判断错误**：手机导入资源时只有 0.7MB/s，最初归因于手机灭屏休眠；亮屏复测仍然很慢，且同期投影仪也从 5.3MB/s 降到约 1MB/s，瓶颈实为 Deck 的 2.4GHz 连接。测速应同时测一台对照设备。
        """)
    }
    related("spec-hub", "phone-termux", "phone-adb")
}
