package z6x.content.manual

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val AdbBasics = module("adb-basics", "ADB 基础：连接、安装、传文件、按键") {
    keywords = "adb connect · install · push/pull · forward · input · reboot"
    overview = """
        ADB 由三部分组成：Deck 上的 `adb` 命令（客户端）、Deck 后台的 adb server，以及投影仪上的 adbd。以下为日常最常用的命令，在 **Deck 终端**中执行。
    """
    verified("2026-10-01")

    why("ADB 的三个组成部分") {
        text("""
            • **adb 客户端**：在 Deck 终端中输入的 `adb` 命令。它本身不直接连接投影仪，而是将命令交给本机的 adb server。
            • **adb server**：首次执行 adb 时在 Deck 后台自动启动，监听本机 **5037** 端口，负责维持与所有设备的连接。多个终端同时使用 adb 时，共用同一个 server。
            • **adbd**：投影仪上的守护进程，监听 5555 端口，收到命令后以 shell 身份（uid 2000）执行。
            投影仪上只有 adbd，没有 adb 客户端，因此在 SSH 中无法使用 `adb` 命令。adb 始终由 Deck 向投影仪发送命令。
        """)
        read("Deck 上的 adb server", "ss -ltnp | grep 5037", Host.Deck) {
            varies = true
            captured("2026-10-01", "LISTEN 0      128             127.0.0.1:5037       0.0.0.0:*    users:((\"adb\",pid=1288885,fd=15))")
            note = "只监听 127.0.0.1，局域网中的其他机器无法连接该 server。连接异常时可执行 `adb kill-server` 使其重新启动。"
        }
        read("投影仪上是否有 adb 命令", "which adb || echo 无adb", Host.Adb) {
            captured("2026-10-01", "无adb")
        }
    }

    steps("连接") {
        read("连接投影仪", "adb connect 192.168.0.109:5555", Host.Deck) {
            captured("2026-10-01", "already connected to 192.168.0.109:5555")
        }
        read("查看已连接的设备", "adb devices -l", Host.Deck) {
            captured("2026-10-01", """
                List of devices attached
                192.168.0.109:5555     device product:Product_raptor model:XGIMI_TV device:snake_deep transport_id:3
            """)
            note = "状态 `device` 表示正常；`offline` 表示连接已断开；`unauthorized` 表示设备端未授权（本机免授权，不会出现）。"
        }
        change("断开", "adb disconnect 192.168.0.109:5555", Host.Deck)
        change("ADB 无响应时重启 Deck 端的 adb server", "adb kill-server && adb start-server", Host.Deck) {
            note = "重启后需要重新执行 `adb connect`。"
        }
        read("连接多台设备时指定其中一台", "adb -s 192.168.0.109:5555 shell id", Host.Deck) {
            note = "`-s 设备` 应放在子命令之前。只连接一台设备时可以省略。"
        }
    }

    steps("安装与传文件") {
        change("安装 APK", "adb install -r app.apk", Host.Deck) {
            note = """
                • `-r` 覆盖安装（升级），保留数据
                • `-d` 允许降级（安装旧版本）
                • `-t` 允许安装测试版 APK
                • `-g` 安装时授予全部运行时权限
            """
        }
        change("从 Deck 传文件到投影仪", "adb push config.yaml /data/local/tmp/", Host.Deck) {
            note = "shell 可写入的位置主要是 /data/local/tmp 和 /sdcard。"
        }
        change("从投影仪取文件到 Deck", "adb pull /sdcard/Download/app.apk ./", Host.Deck)
    }

    steps("端口转发") {
        change("Deck 的端口 → 投影仪的端口", "adb forward tcp:8088 tcp:8088", Host.Deck) {
            note = "此后访问 Deck 的 localhost:8088 即等同于访问投影仪的 8088 端口，适用于投影仪上只监听本机地址的服务。`adb forward --list` 查看已有转发，`--remove-all` 全部清除。"
        }
        change("投影仪的端口 → Deck 的端口", "adb reverse tcp:9090 tcp:9090", Host.Deck) {
            note = "方向相反：投影仪访问自身的 localhost:9090 时，实际连接到 Deck 的 9090 端口。适用于让电视上的应用使用 Deck 上的开发服务器。"
        }
    }

    steps("远程输入") {
        change("向当前输入框输入文字", "adb shell input text 'hello'", Host.Deck) {
            note = "用于解决遥控器难以输入长文本的问题。空格需写作 `%s`；**不支持中文**和部分特殊符号。"
        }
        change("模拟遥控器按键", "adb shell input keyevent 3", Host.Deck) {
            note = "常用键值：3 主页、4 返回、19/20/21/22 上下左右、23 确认、82 菜单、26 电源（休眠/唤醒）。详见「查看当前焦点窗口」。"
        }
    }

    steps("重启") {
        danger("重启投影仪", "adb reboot", Host.Deck) {
            note = "实测（2026-10-01）重启后 adbd 会自动运行，约 1 分钟后即可重新 `adb connect`。自行部署的服务需要重新启动。"
        }
        danger("重启进入 Recovery 模式", "adb reboot recovery", Host.Deck) {
            note = "Recovery 中可以恢复出厂设置（清除全部定制）。无明确目的时不要进入。"
        }
    }

    lesson("说明") {
        text("""
            • 一个意外发现：系统设置中的 ADB 开关 `settings get global adb_enabled` 读数为 **0**（关闭），但 ADB 始终可用。这是因为 adbd 是绕过设置、直接通过 `setprop ctl.start adbd` 启动的，设置中的开关并不知道它正在运行。
            • **重启后该值变为 1**，adbd 也在开机时自动运行。adbd 自动运行的原因已查明（极米补丁将运行状态记录为持久化属性），见「核查：ADB 开机自动运行的原因」；adb_enabled 何时被同步为 1 尚未查证。
            • 旧版中的 `adb logcat`、`adb bugreport` 已移至「日志与崩溃」。
        """)
    }

    related("force-adb", "pm-am", "logs-crash", "focus-window")
}

val PmAm = module("pm-am", "应用管理：pm、am、appops") {
    keywords = "pm list · pm path · pm clear · am start · am force-stop · appops"
    overview = """
        `pm` 管理已安装的应用（查询、停用、卸载、权限），`am` 管理运行中的应用（启动界面、结束进程、发送广播），`appops` 管理应用的细分行为（后台运行、悬浮窗）。查询类命令在 SSH 中也可使用，修改类命令需要 ADB。
    """
    verified("2026-10-01")

    steps("查询（SSH 与 ADB 均可）") {
        read("列出包：常用过滤", "pm list packages -3", Host.Adb) {
            varies = true
            note = """
                • `-3` 第三方（用户安装）　• `-s` 系统预装　• `-d` 已停用　• `-e` 已启用
                • `-u` 包括对当前用户已卸载的包　• `-f` 显示 APK 路径　• `-i` 显示安装来源　• `-U` 显示 uid
            """
        }
        read("显示安装来源", "pm list packages -i | grep xgimi.vcontrol", Host.Adb) {
            captured("2026-10-01", "package:com.xgimi.vcontrol  installer=com.xgimi.upgrade")
            note = "部分预装应用由极米的升级服务安装（更新）。"
        }
        read("查询 APK 路径", "pm path com.xgimi.minitvfactory", Host.Adb) {
            captured("2026-10-01", "package:/vendor/priv-app/minitvfactory_release_838/minitvfactory_release_838.apk")
            note = "`priv-app` 是特权应用目录，其中的应用可获得普通应用无法获得的系统权限。"
        }
        read("应用的详细信息", "dumpsys package com.spocky.projengmenu | grep -E 'versionName|firstInstallTime|enabled='", Host.Adb) {
            varies = true
            note = "版本、安装时间、启用状态、权限、组件等信息都在 `dumpsys package` 中。系统没有「应用详情」设置页，可用它代替。"
        }
    }

    steps("停用、卸载、清除数据（需 ADB）") {
        change("停用 / 恢复", "pm disable-user --user 0 包名\npm enable 包名", Host.Adb) {
            note = "详见「精简预装应用：停用与卸载的取舍」。"
        }
        danger("对当前用户卸载 / 重新安装", "pm uninstall -k --user 0 包名\ncmd package install-existing 包名", Host.Adb) {
            note = "`--user 0` 只影响用户 0（本机唯一的用户），`-k` 表示保留数据。"
        }
        danger("清空应用的全部数据", "pm clear 包名", Host.Adb) {
            note = "恢复到刚安装时的状态：登录信息和设置全部清除。SSH 无法登录时即通过它恢复，见「SimpleSSHD 公钥登录」。"
        }
        change("授予 / 撤销运行时权限", "pm grant 包名 android.permission.POST_NOTIFICATIONS\npm revoke 包名 android.permission.POST_NOTIFICATIONS", Host.Adb) {
            note = "无需弹窗即可直接授权，仅对应用已声明的「危险权限」有效。"
        }
    }

    steps("运行中的应用（需 ADB）") {
        change("打开某个界面", "am start -n com.xgimi.minitvfactory/.ui.MainFactoryMenuActivity", Host.Adb) {
            note = "格式为 `-n 包名/界面类名`。`-W` 等待启动完成并输出耗时；`-S` 先结束旧进程再启动。界面名可用 `cmd package resolve-activity` 查询。"
        }
        change("用浏览器打开网址", "am start -a android.intent.action.VIEW -d 'http://192.168.0.21:8000/'", Host.Adb) {
            note = "由系统选择默认处理网址的应用打开（本机为 TV Bro）。"
        }
        change("结束应用的全部进程", "am force-stop 包名", Host.Adb)
        change("发送广播（测试用）", "am broadcast -a 广播动作", Host.Adb) {
            note = "旧版以开机广播 BOOT_COMPLETED 为例。这是受系统保护的广播，通常只允许系统发出；**未测试**，因为一旦发出会使所有应用误以为刚刚开机。测试自己的应用时，应使用其自定义的广播动作。"
        }
    }

    steps("appops：细分行为管控（需 ADB）") {
        read("查看应用的各项行为权限", "appops get org.smarttube.stable", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                Uid mode: RECORD_AUDIO: foreground
                LEGACY_STORAGE: ignore
                SYSTEM_ALERT_WINDOW: ignore; rejectTime=+9h34m41s465ms ago
                TAKE_AUDIO_FOCUS: allow; time=+4m38s359ms ago
            """)
            note = "每项的取值为 allow（允许）、ignore（静默拒绝）、deny 等。其后的时间为最近一次使用或被拒绝的时间。"
        }
        change("禁止在后台运行", "appops set 包名 RUN_IN_BACKGROUND ignore", Host.Adb) {
            note = "抑制应用的后台自动启动。恢复时将 ignore 改为 allow。"
        }
        change("允许显示悬浮窗", "appops set 包名 SYSTEM_ALERT_WINDOW allow", Host.Adb)
    }

    steps("预编译（cmd package compile）") {
        change("将应用完整编译为机器码", "cmd package compile -m speed -f 包名", Host.Adb) {
            note = """
                安卓应用通常在运行中逐步编译热点代码。`-m speed` 表示一次性全部编译，`-f` 表示强制重新编译。启动和运行可能略快，但会占用更多存储。
                不带 `-m` 时会报错：`Cannot run without any of compilation filter ("-m") and compilation reason ("-r")`（实测）。
            """
        }
    }

    audit("旧记录核对：包名的说明") {
        text("以下部分包名在本机查不到。**极米预装包**位于系统分区，没有 root 无法彻底删除，若 `pm list packages -u` 中也没有，说明该固件本来就没有它；**第三方应用**卸载后会彻底消失，查不到并不代表旧记录有误，只是目前未安装。")
    }

    audit("旧记录核对：pm / am / appops 的例子") {
        change("旧版：启动工厂模式", "am start -n com.xgimi.minitvfactory/.MainActivity", Host.Adb) {
            verdict = Verdict.Disproved
            note = "入口名有误，实际为 `.ui.MainFactoryMenuActivity`。"
        }
        change("旧版：授予 / 撤销通知权限", "pm grant com.v2ray.ang android.permission.POST_NOTIFICATIONS\npm revoke com.v2ray.ang android.permission.POST_NOTIFICATIONS", Host.Adb) {
            verdict = Verdict.Unverified
            note = "com.v2ray.ang 是 v2rayNG 的包名，该应用**已卸载**，目前无法测试。命令写法本身是标准的。"
        }
        change("旧版：禁止某应用后台驻留", "appops set com.xgimi.doubtservice RUN_IN_BACKGROUND ignore\nappops get com.xgimi.doubtservice", Host.Adb) {
            verdict = Verdict.Disproved
            note = "com.xgimi.doubtservice **固件中不存在**（`-u` 中也查不到）。appops 的写法本身正确，见上文。"
        }
        change("旧版：授权悬浮窗 / 全量预编译", "appops set com.github.catvod SYSTEM_ALERT_WINDOW allow\ncmd package compile -m speed -f com.github.catvod", Host.Adb) {
            verdict = Verdict.Unverified
            note = "com.github.catvod 是第三方应用，**目前未安装**（可能安装后又卸载），无法测试。"
        }
    }

    audit("旧记录核对：旧版「极米专有应用图谱」") {
        change("旧版：停用极米开机广告与数据埋点", "pm disable-user --user 0 com.xgimi.advert\npm disable-user --user 0 com.xgimi.tracker", Host.Adb) {
            verdict = Verdict.Disproved
            captured("（旧记录）", "Package com.xgimi.advert new state: disabled-user   # 成功禁用，开机不再拉取广告")
            note = "这两个包**固件中不存在**。实际负责广告和上报的是 com.xgimi.adservice、com.xgimi.datareporter（见「停用清单」）。"
        }
        change("旧版：停用自带应用市场与静默下载服务", "pm disable-user --user 0 com.xgimi.appstore\npm disable-user --user 0 com.xgimi.downloader", Host.Adb) {
            verdict = Verdict.Disproved
            note = "这两个包**固件中不存在**。应用市场为 com.xgimi.newappmarket。"
        }
        claim("""
            绝对不可停用的关键硬件守护组件（高危红线）：
            • com.xgimi.deviceservice：核心设备服务。负责蓝牙遥控器底层按键映射、语音键透传、电动对焦马达控制。绝对不可禁用。
            • com.xgimi.remoteservice：极米遥控器蓝牙协议栈。禁用后蓝牙遥控器将断连，且无法重新配对。
            • com.xgimi.keystone：智能梯形校正与避障算法服务。禁用后开机无法完成几何畸变校准，画面会倾斜变形。
            • com.xgimi.minitvfactory：工厂模式与底层 HDMI 输入信号源切换中枢。禁用后切换 HDMI 输入会黑屏崩溃。
        """, Verdict.Disproved,
            "前三个包**固件中均不存在**；只有 minitvfactory 存在，但「HDMI 信号源切换中枢」缺乏依据（信号源由 com.xgimi.tvinput 负责）。**不要依据这份清单判断哪些组件可以停用**，真正不应改动的组件见「精简后的系统组件与入口」。")
        claim("pm disable-user / pm enable：SSH（UID 10068）调用会抛出 java.lang.SecurityException: Neither user 10068 nor current process has android.permission.CHANGE_COMPONENT_ENABLED_STATE。", Verdict.Disproved,
            "确实会被拒绝，但报错原文为 `Attempt to change component state; pid=…, uid=10068, package=…`（见「SSH 的权限边界」）。")
        claim("梳理极米 Z6X Pro 系统预装 57 个 com.xgimi.* 应用组件。", Verdict.Confirmed, "55 个已安装加 2 个对当前用户卸载，共 57 个。")
    }

    related("debloat-method", "debloat-list", "system-packages", "adb-basics")
}

val PropsInit = module("props-init", "系统属性与 init 服务") {
    keywords = "getprop · setprop · ro. · persist. · ctl. · init.svc · IceSea"
    overview = """
        系统属性是安卓的全局键值表，其中记录了版本、型号和各种开关。init（1 号进程）管理所有系统服务，并通过属性对外报告服务状态、接收启停命令。
    """
    verified("2026-10-01")

    why("属性的命名规则") {
        text("""
            • `ro.*`：只读，开机时确定，之后无法修改（型号、版本、平台）。
            • `persist.*`：持久化，写入存储，重启后仍然保留。
            • `ctl.*`：向 init 发送命令：`ctl.start 服务名`、`ctl.stop`、`ctl.restart`。
            • `init.svc.服务名`：init 报告的服务状态（running / stopped）。
            • 其他（如 `service.*`、`debug.*`）：普通属性，重启后丢失。
            哪些身份能读写哪些属性由 SELinux 决定。本机处于 Permissive 模式，因此普通应用也能写入许多属性。
        """)
        text("""
            **属性的存储方式：** 属性由 init 维护，存放在一块共享内存中（/dev/__properties__ 下的文件）。每个进程启动时将其映射到自身，因此读取属性非常快，无需与 init 通信。写入属性则需要向 init 发送请求，由 init 检查后修改。`persist.*` 的改动还会被 init 另存到 /data/property，下次开机时读回。
        """)
        read("共享内存所在的目录", "ls /dev/__properties__ | head -3", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "ls: /dev/__properties__: Permission denied")
            note = "shell 无权列出该目录，但其中的文件已映射到每个进程，getprop 仍可读取（实测可读出 930 条）。"
        }
        read("持久化属性保存的位置", "ls -ld /data/property", Host.Adb) {
            varies = true
            captured("2026-10-01", "drwx------  2 root root 4096 2026-10-01 23:30 /data/property")
            note = "只有 root 可以进入，shell 无法查看其内容，只能通过 getprop 读取。"
        }
    }

    steps("常用属性") {
        read("芯片与架构", "getprop ro.board.platform; getprop ro.product.cpu.abi; getprop ro.product.cpu.abilist", Host.Adb) {
            captured("2026-10-01", """
                huanglong
                armeabi-v7a
                armeabi-v7a,armeabi
            """)
        }
        read("系统版本与固件", "getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.build.display.id", Host.Adb) {
            captured("2026-10-01", """
                12
                31
                tv_hi3751v660 HuanglongV200R006C00SPC009B020
            """)
        }
        read("ADB 相关", "getprop service.adb.tcp.port; getprop ro.adb.secure; getprop xgimi.remoteDebug.on", Host.Adb) {
            captured("2026-10-01", """
                5555
                0
                false
            """)
        }
        read("在全部属性中搜索", "getprop | grep -i <关键词>", Host.Adb) {
            note = "型号即通过这种方式查到，见「核查：真实型号与芯片」。"
        }
    }

    steps("init 服务") {
        read("init 服务的数量", "getprop | grep -c '\\[init.svc\\.'", Host.Adb) {
            varies = true
            captured("2026-10-01", "102")
        }
        read("查看若干服务的状态", "getprop | grep -E '\\[init.svc\\.(adbd|IceSea|surfaceflinger)\\]'", Host.Adb) {
            captured("2026-10-01", """
                [init.svc.IceSea]: [running]
                [init.svc.adbd]: [running]
                [init.svc.surfaceflinger]: [running]
            """)
        }
        read("极米的 IceSea 服务", "ps -A | grep IceSea; getprop | grep -i icesea", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                root          4227     1   25028   5152 0                   0 S IceSea
                [init.svc.IceSea]: [running]
                [init.svc_debug_pid.IceSea]: [4227]
                [ro.boottime.IceSea]: [19948703384]
            """)
            note = """
                IceSea 是极米自有的 init 服务，**以 root 身份**运行，父进程为 1（init）。
                旧版称其为「极米硬件守护中枢」，具体功能未经查证。`ro.boottime.IceSea` 表示它在开机后第几纳秒启动（约 19.9 秒）。
            """
        }
        change("重启 adbd（ADB 卡死时）", "setprop ctl.restart adbd", Host.Ssh) {
            note = "此操作会断开当前所有 ADB 连接，因此应从 **SSH** 执行，数秒后重新执行 `adb connect`。原理见「通过 SSH 启动网络 ADB」。"
        }
    }

    audit {
        read("旧版：查询正在运行与已退出的系统核心服务", "getprop | grep '\\[init.svc\\.' | head -n 10", Host.Adb) {
            verdict = Verdict.Confirmed
            varies = true
            captured("（旧记录）", """
                [init.svc.IceSea]: [running]   # 极米光机硬件控制中枢（运行中）
                [init.svc.adbd]: [running]     # ADB 守护进程（运行中）
                [init.svc.audioserver]: [running] # 音频处理服务（运行中）
                [init.svc.bootanim]: [stopped]    # 开机动画服务（已停止退出）
            """)
            note = "四个状态均与实测一致。"
        }
        read("旧版：查看 IceSea 进程", "ps -ef | grep -i icesea", Host.Adb) {
            verdict = Verdict.Confirmed
            varies = true
            captured("（旧记录）", "root          4227     1 0 19:33:34 ?     00:00:18 IceSea   # UID 0 root 运行，PPID 1，切勿强杀")
            note = "root 身份、父进程 1 乃至 PID 4227 均与实测一致（同一次开机）。"
        }
        claim("""
            IceSea 是 PPID=1（init）直接派生的 root 级别专有守护进程，负责：
            • 光机激光光源与 RGB 色轮物理点亮与亮度控制。
            • 机身电动步进马达对焦微调与 TOF 测距联动。
            • 内置散热风扇多级 PWM 温控调速。
            • 自动梯形校正陀螺仪姿态算法驱动。
        """, Verdict.Unverified, "root 身份、父进程 1 **成立**。其具体职责需查看它打开的设备文件，而没有 root 无法查看，因此无法验证。")
        claim("setprop ctl.*：SSH 无权触发（抛出 Permission denied 或被 SELinux 直接拦截）；ADB（UID 2000）具备操作受限 init 服务（如 adbd）的权限。", Verdict.Disproved,
            "在 SSH 中执行 `setprop ctl.start adbd` 返回成功，ADB 最初正是这样开启的；SELinux 处于 Permissive 模式，不会拦截。")
        change("旧版：强开 ADB", "setprop service.adb.tcp.port 5555 && setprop ctl.start adbd", Host.Ssh) {
            verdict = Verdict.Unverified
            note = "前半句多余（该值本来就是 5555）。见「通过 SSH 启动网络 ADB」。"
        }
    }

    related("force-adb", "find-real-model", "selinux-cmds")
}
