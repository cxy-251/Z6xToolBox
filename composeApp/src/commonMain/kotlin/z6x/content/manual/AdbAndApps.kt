package z6x.content.manual

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val AdbBasics = module("adb-basics", "ADB 基础：连接、安装、传文件、按键") {
    keywords = "adb connect · install · push/pull · forward · input · reboot"
    overview = """
        ADB 由三部分组成：Deck 上的 `adb` 命令（客户端）、Deck 后台的 adb server、投影仪上的 adbd。下面是日常最常用的命令，在 **Deck 终端**执行。
    """
    verified("2026-10-01")

    steps("连接") {
        read("连接投影仪", "adb connect 192.168.0.109:5555", Host.Deck) {
            captured("2026-10-01", "already connected to 192.168.0.109:5555")
        }
        read("看连上了哪些设备", "adb devices -l", Host.Deck) {
            captured("2026-10-01", """
                List of devices attached
                192.168.0.109:5555     device product:Product_raptor model:XGIMI_TV device:snake_deep transport_id:3
            """)
            note = "状态 `device` 表示正常；`offline` 表示连接断了，`unauthorized` 表示设备端没授权（这台免授权，不会出现）。"
        }
        change("断开", "adb disconnect 192.168.0.109:5555", Host.Deck)
        change("ADB 卡住时重启 Deck 端的 adb server", "adb kill-server && adb start-server", Host.Deck) {
            note = "重启后要重新 `adb connect`。"
        }
        read("连了多台设备时指定一台", "adb -s 192.168.0.109:5555 shell id", Host.Deck) {
            note = "`-s 设备` 放在子命令前面。只连一台时可以省略。"
        }
    }

    steps("安装与传文件") {
        change("安装 APK", "adb install -r app.apk", Host.Deck) {
            note = """
                • `-r` 覆盖安装（升级），保留数据
                • `-d` 允许降级（装旧版本）
                • `-t` 允许装测试版 APK
                • `-g` 安装时授予全部运行时权限
            """
        }
        change("从 Deck 传文件到投影仪", "adb push config.yaml /data/local/tmp/", Host.Deck) {
            note = "shell 能写的地方主要是 /data/local/tmp 和 /sdcard。"
        }
        change("从投影仪取文件到 Deck", "adb pull /sdcard/Download/app.apk ./", Host.Deck)
    }

    steps("端口转发") {
        change("Deck 的端口 → 投影仪的端口", "adb forward tcp:8088 tcp:8088", Host.Deck) {
            note = "之后访问 Deck 的 localhost:8088 就等于访问投影仪的 8088。投影仪服务只监听本机地址时有用。`adb forward --list` 查看，`--remove-all` 清除。"
        }
        change("投影仪的端口 → Deck 的端口", "adb reverse tcp:9090 tcp:9090", Host.Deck) {
            note = "反过来：投影仪访问自己的 localhost:9090 时，实际连到 Deck 的 9090。适合让电视上的 App 用 Deck 上的开发服务器。"
        }
    }

    steps("远程输入") {
        change("往当前输入框里打字", "adb shell input text 'hello'", Host.Deck) {
            note = "解决遥控器打长字的问题。空格要写成 `%s`；**不支持中文**和部分特殊符号。"
        }
        change("模拟遥控器按键", "adb shell input keyevent 3", Host.Deck) {
            note = "常用键值：3 主页、4 返回、19/20/21/22 上下左右、23 确认、82 菜单、26 电源（休眠/唤醒）。详见「屏幕上现在是谁」。"
        }
    }

    steps("重启") {
        danger("重启投影仪", "adb reboot", Host.Deck) {
            note = "重启后 adbd 可能不会自动运行（见「在 SSH 里强开网络 ADB」），要有从 SSH 恢复的准备。自己部署的服务也要重新启动。"
        }
        danger("重启进入 Recovery 模式", "adb reboot recovery", Host.Deck) {
            note = "Recovery 里可以恢复出厂设置（清空一切定制）。没有明确目的不要进。"
        }
    }

    lesson("说明") {
        text("""
            • 有个意外发现：系统设置里的 ADB 开关 `settings get global adb_enabled` 读出来是 **0**（关），但 ADB 一直能用。因为 adbd 是绕过设置、直接用 `setprop ctl.start adbd` 拉起来的，设置里的开关根本不知道它在跑。
            • 旧版的 `adb logcat`、`adb bugreport` 移到了「日志与崩溃」。
        """)
    }

    related("force-adb", "pm-am", "logs-crash", "focus-window")
}

val PmAm = module("pm-am", "应用管理：pm、am、appops") {
    keywords = "pm list · pm path · pm clear · am start · am force-stop · appops"
    overview = """
        `pm` 管安装了哪些应用（查询、停用、卸载、权限），`am` 管运行中的应用（启动界面、结束进程、发广播），`appops` 管应用的细分行为（后台运行、悬浮窗）。查询类在 SSH 里也能用，修改类要 ADB。
    """
    verified("2026-10-01")

    steps("查询（SSH 和 ADB 都行）") {
        read("列出包：常用过滤", "pm list packages -3", Host.Adb) {
            varies = true
            note = """
                • `-3` 第三方（自己装的）　• `-s` 系统预装　• `-d` 已停用　• `-e` 已启用
                • `-u` 包括已卸载（对当前用户）的　• `-f` 显示 APK 路径　• `-i` 显示是谁安装的　• `-U` 显示 uid
            """
        }
        read("显示安装来源", "pm list packages -i | grep xgimi.vcontrol", Host.Adb) {
            captured("2026-10-01", "package:com.xgimi.vcontrol  installer=com.xgimi.upgrade")
            note = "有的预装应用是由极米的升级服务安装（更新）的。"
        }
        read("APK 在哪", "pm path com.xgimi.minitvfactory", Host.Adb) {
            captured("2026-10-01", "package:/vendor/priv-app/minitvfactory_release_838/minitvfactory_release_838.apk")
            note = "`priv-app` 是特权应用目录，里面的应用能拿到普通应用拿不到的系统权限。"
        }
        read("应用的详细信息", "dumpsys package com.spocky.projengmenu | grep -E 'versionName|firstInstallTime|enabled='", Host.Adb) {
            varies = true
            note = "版本、安装时间、启用状态、权限、组件……全在 `dumpsys package` 里。系统没有「应用详情」设置页，就用它。"
        }
    }

    steps("停用、卸载、清数据（需 ADB）") {
        change("停用 / 恢复", "pm disable-user --user 0 包名\npm enable 包名", Host.Adb) {
            note = "详见「精简预装应用：停用还是卸载」。"
        }
        danger("对当前用户卸载 / 装回", "pm uninstall -k --user 0 包名\ncmd package install-existing 包名", Host.Adb) {
            note = "`--user 0` 只影响用户 0（这台机器唯一的用户），`-k` 保留数据。"
        }
        danger("清空应用的全部数据", "pm clear 包名", Host.Adb) {
            note = "相当于刚装好的状态：登录、设置全没了。SSH 被锁在门外时就是靠它恢复的，见「SSH 免密登录」。"
        }
        change("授予 / 撤销运行时权限", "pm grant 包名 android.permission.POST_NOTIFICATIONS\npm revoke 包名 android.permission.POST_NOTIFICATIONS", Host.Adb) {
            note = "免去弹窗，直接授权。只对应用声明过的「危险权限」有效。"
        }
    }

    steps("运行中的应用（需 ADB）") {
        change("打开某个界面", "am start -n com.xgimi.minitvfactory/.ui.MainFactoryMenuActivity", Host.Adb) {
            note = "`-n 包名/界面类名`。`-W` 等待启动完成并打印耗时；`-S` 先结束旧进程再启动。界面名用 `cmd package resolve-activity` 查。"
        }
        change("用浏览器打开网址", "am start -a android.intent.action.VIEW -d 'http://192.168.0.21:8000/'", Host.Adb) {
            note = "系统选默认处理网址的应用打开（这台上是 TV Bro）。"
        }
        change("结束应用的全部进程", "am force-stop 包名", Host.Adb)
        change("发一个广播（测试用）", "am broadcast -a 广播动作", Host.Adb) {
            note = "旧版用开机广播 BOOT_COMPLETED 做例子。这是系统保护的广播，一般只允许系统发出；**没有测试**，因为万一发出去会让所有应用以为刚开机。测试自己的应用时，用它自定义的广播动作。"
        }
    }

    steps("appops：细分行为管控（需 ADB）") {
        read("看一个应用的各项行为权限", "appops get org.smarttube.stable", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                Uid mode: RECORD_AUDIO: foreground
                LEGACY_STORAGE: ignore
                SYSTEM_ALERT_WINDOW: ignore; rejectTime=+9h34m41s465ms ago
                TAKE_AUDIO_FOCUS: allow; time=+4m38s359ms ago
            """)
            note = "每项是 allow（允许）、ignore（静默拒绝）、deny 等。后面的时间是最近一次使用或被拒的时间。"
        }
        change("禁止在后台运行", "appops set 包名 RUN_IN_BACKGROUND ignore", Host.Adb) {
            note = "压制应用的后台自启。恢复：把 ignore 换成 allow。"
        }
        change("允许显示悬浮窗", "appops set 包名 SYSTEM_ALERT_WINDOW allow", Host.Adb)
    }

    steps("预编译（cmd package compile）") {
        change("把应用完整编译成机器码", "cmd package compile -m speed -f 包名", Host.Adb) {
            note = """
                安卓应用平时边运行边编译热点代码。`-m speed` 一次全部编译，`-f` 强制重新编译。可能让启动和运行稍快，但占用更多存储。
                不带 `-m` 会报错：`Cannot run without any of compilation filter ("-m") and compilation reason ("-r")`（实测）。
            """
        }
    }

    audit("旧记录核对：包名的说明") {
        text("下面有的包名找不到。**极米预装包**在系统分区里，没有 root 删不干净，`pm list packages -u` 里也没有就说明这个固件本来没有它；**第三方应用**卸载后会彻底消失，找不到不代表旧记录写错，只是现在没装。")
    }

    audit("旧记录核对：pm / am / appops 的例子") {
        change("旧版：启动工厂模式", "am start -n com.xgimi.minitvfactory/.MainActivity", Host.Adb) {
            verdict = Verdict.Disproved
            note = "入口名不对，实际是 `.ui.MainFactoryMenuActivity`。"
        }
        change("旧版：授予 / 撤销通知权限", "pm grant com.v2ray.ang android.permission.POST_NOTIFICATIONS\npm revoke com.v2ray.ang android.permission.POST_NOTIFICATIONS", Host.Adb) {
            verdict = Verdict.Unverified
            note = "com.v2ray.ang 是 v2rayNG 的包名，**已卸载**，现在没法测。命令写法本身是标准的。"
        }
        change("旧版：禁止某应用后台驻留", "appops set com.xgimi.doubtservice RUN_IN_BACKGROUND ignore\nappops get com.xgimi.doubtservice", Host.Adb) {
            verdict = Verdict.Disproved
            note = "com.xgimi.doubtservice **固件里没有**（`-u` 里也查不到）。appops 的写法本身是对的，见上面。"
        }
        change("旧版：授权悬浮窗 / 全量预编译", "appops set com.github.catvod SYSTEM_ALERT_WINDOW allow\ncmd package compile -m speed -f com.github.catvod", Host.Adb) {
            verdict = Verdict.Unverified
            note = "com.github.catvod 是第三方应用，**现在没装**（可能装过又卸了），没法测。"
        }
    }

    audit("旧记录核对：旧版「极米专有应用图谱」") {
        change("旧版：停用极米开机广告与数据埋点", "pm disable-user --user 0 com.xgimi.advert\npm disable-user --user 0 com.xgimi.tracker", Host.Adb) {
            verdict = Verdict.Disproved
            captured("（旧记录）", "Package com.xgimi.advert new state: disabled-user   # 成功禁用，开机不再拉取广告")
            note = "这两个包**固件里没有**。真正的广告和上报是 com.xgimi.adservice、com.xgimi.datareporter（见「停用清单」）。"
        }
        change("旧版：停用自带应用市场与静默下载服务", "pm disable-user --user 0 com.xgimi.appstore\npm disable-user --user 0 com.xgimi.downloader", Host.Adb) {
            verdict = Verdict.Disproved
            note = "这两个包**固件里没有**。应用市场是 com.xgimi.newappmarket。"
        }
        claim("""
            绝对不可停用的关键硬件守护组件（高危红线）：
            • com.xgimi.deviceservice：核心设备服务。负责蓝牙遥控器底层按键映射、语音键透传、电动对焦马达控制。绝对不可禁用。
            • com.xgimi.remoteservice：极米遥控器蓝牙协议栈。禁用后蓝牙遥控器将断连，且无法重新配对。
            • com.xgimi.keystone：智能梯形校正与避障算法服务。禁用后开机无法完成几何畸变校准，画面会倾斜变形。
            • com.xgimi.minitvfactory：工厂模式与底层 HDMI 输入信号源切换中枢。禁用后切换 HDMI 输入会黑屏崩溃。
        """, Verdict.Disproved,
            "前三个包**固件里都没有**；只有 minitvfactory 存在，但「HDMI 信号源切换中枢」没有依据（信号源是 com.xgimi.tvinput）。**不要用这份清单判断哪些能停**，真正不该动的组件见「系统里还剩什么」。")
        claim("pm disable-user / pm enable：SSH（UID 10068）调用会抛出 java.lang.SecurityException: Neither user 10068 nor current process has android.permission.CHANGE_COMPONENT_ENABLED_STATE。", Verdict.Disproved,
            "确实会被拒绝，但报错原文是 `Attempt to change component state; pid=…, uid=10068, package=…`（见「SSH 能查不能改」）。")
        claim("梳理极米 Z6X Pro 系统预装 57 个 com.xgimi.* 应用组件。", Verdict.Confirmed, "55 个已装 + 2 个对当前用户卸载 = 57。")
    }

    related("debloat-method", "debloat-list", "system-packages", "adb-basics")
}

val PropsInit = module("props-init", "系统属性与 init 服务") {
    keywords = "getprop · setprop · ro. · persist. · ctl. · init.svc · IceSea"
    overview = """
        系统属性是安卓的一张全局「键值表」：版本、型号、各种开关都在里面。init（1 号进程）管理所有系统服务，也通过属性对外报告服务状态、接收启停命令。
    """
    verified("2026-10-01")

    why("属性的命名规则") {
        text("""
            • `ro.*`：只读，开机时写定，之后改不了（型号、版本、平台）。
            • `persist.*`：持久化，写进存储，重启后还在。
            • `ctl.*`：给 init 下命令：`ctl.start 服务名`、`ctl.stop`、`ctl.restart`。
            • `init.svc.服务名`：init 报告服务状态（running / stopped）。
            • 其他（如 `service.*`、`debug.*`）：普通属性，重启后丢失。
            谁能读写哪些属性由 SELinux 决定。这台机器是 Permissive，所以连普通 App 都能写很多属性。
        """)
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
        read("全部属性里搜", "getprop | grep -i <关键词>", Host.Adb) {
            note = "型号就是这么搜出来的，见「案例：查出真实型号和芯片」。"
        }
    }

    steps("init 服务") {
        read("有多少 init 服务", "getprop | grep -c '\\[init.svc\\.'", Host.Adb) {
            varies = true
            captured("2026-10-01", "102")
        }
        read("看几个服务的状态", "getprop | grep -E '\\[init.svc\\.(adbd|IceSea|surfaceflinger)\\]'", Host.Adb) {
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
                IceSea 是极米自己的 init 服务，**以 root 身份**运行，父进程是 1（init）。
                旧版称它是「极米硬件守护中枢」，具体做什么没有查证。`ro.boottime.IceSea` 是它在开机后第几纳秒启动的（约 19.9 秒）。
            """
        }
        change("重启 adbd（ADB 卡死时）", "setprop ctl.restart adbd", Host.Ssh) {
            note = "会断开当前所有 ADB 连接，所以要从 **SSH** 执行，几秒后重新 `adb connect`。原理见「在 SSH 里强开网络 ADB」。"
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
            note = "四个状态实测都一致。"
        }
        read("旧版：查看 IceSea 进程", "ps -ef | grep -i icesea", Host.Adb) {
            verdict = Verdict.Confirmed
            varies = true
            captured("（旧记录）", "root          4227     1 0 19:33:34 ?     00:00:18 IceSea   # UID 0 root 运行，PPID 1，切勿强杀")
            note = "root、父进程 1、甚至 PID 4227 都和实测一致（同一次开机）。"
        }
        claim("""
            IceSea 是 PPID=1（init）直接派生的 root 级别专有守护进程，负责：
            • 光机激光光源与 RGB 色轮物理点亮与亮度控制。
            • 机身电动步进马达对焦微调与 TOF 测距联动。
            • 内置散热风扇多级 PWM 温控调速。
            • 自动梯形校正陀螺仪姿态算法驱动。
        """, Verdict.Unverified, "root、父进程 1 **成立**。它具体负责什么，没有 root 看不了它打开的设备文件，无法验证。")
        claim("setprop ctl.*：SSH 无权触发（抛出 Permission denied 或被 SELinux 直接拦截）；ADB（UID 2000）具备操作受限 init 服务（如 adbd）的权限。", Verdict.Disproved,
            "SSH 里执行 `setprop ctl.start adbd` 返回成功，ADB 当初就是这么开的；SELinux 是 Permissive，不会拦截。")
        change("旧版：强开 ADB", "setprop service.adb.tcp.port 5555 && setprop ctl.start adbd", Host.Ssh) {
            verdict = Verdict.Unverified
            note = "前半句多余（本来就是 5555）。见「在 SSH 里强开网络 ADB」。"
        }
    }

    related("force-adb", "find-real-model", "selinux-cmds")
}
