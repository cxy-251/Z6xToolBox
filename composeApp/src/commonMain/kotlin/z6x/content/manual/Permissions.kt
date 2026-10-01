package z6x.content.manual

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val PermModel = module("perm-model", "原理：SSH 与 ADB 的权限差异") {
    keywords = "uid · 用户组 · capability · SELinux · Binder · cmd · 系统权限 · DUMP"
    overview = """
        同一条 `pm disable-user` 命令，在 ADB 中执行成功，在 SSH 中却报 SecurityException；而 `kill` 即使在 ADB 中也无法结束其他应用。原因在于安卓有**两层**权限检查：Linux 内核负责文件、设备和信号；安卓系统服务负责 pm、am、settings 等操作。ADB 与 SSH 在这两层上的身份各不相同。
    """
    verified("2026-10-01")

    why("两层权限") {
        text("""
            **第一层：Linux 内核。** 打开文件和设备、向进程发送信号时，内核检查以下三项：
            • **用户和组**（uid / gid）：文件属于哪个用户、哪个组可以读写。例如遥控器的输入设备属于 input 组，ADB 的 shell 在 input 组中，因此可以读取；SSH 的应用身份不在该组中，因此无法读取。
            • **能力**（capability）：root 的特权被拆分为数十项，例如 CAP_NET_RAW（抓包）、CAP_SYS_TIME（修改系统时间）、CAP_KILL（向其他用户的进程发送信号）。ADB 的 shell 一项也没有。
            • **SELinux**：在用户和组之外再增加一层规则（每个进程属于一个「域」，例如 shell 为 `u:r:shell:s0`，普通应用为 `untrusted_app`）。本机处于 Permissive 模式，违规只记录日志而不拦截，因此这一层基本不起作用。

            **第二层：安卓系统服务。** `pm`、`am`、`wm`、`settings`、`input` 实际上都是两行的 shell 脚本，内容就是 `cmd package`、`cmd activity` 之类的命令。`cmd` 通过 **Binder**（安卓的进程间通信机制）将请求发送给系统服务进程 system_server。system_server 检查调用者的 uid 是否拥有相应的**安卓权限**（例如停用组件需要 CHANGE_COMPONENT_ENABLED_STATE），没有则抛出 SecurityException。

            uid 2000（shell）的安卓权限来自系统中名为 com.android.shell 的包，它预先登记了数百项调试用权限。SSH 所用的 SimpleSSHD 是普通应用（uid 10068），只拥有它自己申请的少数几项。
        """)
    }

    steps("自行查询：身份与权限") {
        read("当前身份、所属用户组与 SELinux 域", "id", Host.Adb) {
            captured("2026-10-01", "uid=2000(shell) gid=2000(shell) groups=2000(shell),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),1078(ext_data_rw),1079(ext_obb_rw),3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc),3011(uhid) context=u:r:shell:s0")
            note = "input 组可读写遥控器设备；log 组可读取全部日志；inet 组可访问网络；readproc 组可查看所有进程。最后的 context 即 SELinux 域。"
        }
        read("拥有的 capability", "grep Cap /proc/self/status", Host.Adb) {
            captured("2026-10-01", """
                CapInh:	0000000000000000
                CapPrm:	0000000000000000
                CapEff:	0000000000000000
                CapBnd:	00000000000000c0
                CapAmb:	0000000000000000
            """)
            note = "CapEff 为当前生效的能力，全为 0 表示一项都没有。因此无法抓包、修改系统时间或向其他用户的进程发送信号。"
        }
        read("pm 的实际内容", "cat /system/bin/pm", Host.Adb) {
            captured("2026-10-01", """
                #!/system/bin/sh
                cmd package "${'$'}@"
            """)
            note = "wm、settings、input 同理（`cmd window`、`cmd settings`、`cmd input`）；am 多一个 instrument 分支。因此，真正决定操作能否执行的是系统服务，而不是这个脚本。"
        }
        read("shell 拥有的安卓权限数量", "dumpsys package com.android.shell | grep -c 'granted=true'", Host.Adb) {
            captured("2026-10-01", "677")
        }
        read("其中的关键权限", "dumpsys package com.android.shell | grep 'granted=true' | grep -oE 'permission\\.(DUMP|WRITE_SECURE_SETTINGS|CHANGE_COMPONENT_ENABLED_STATE|DELETE_PACKAGES|FORCE_STOP_PACKAGES|REBOOT|SET_TIME|READ_LOGS)' | sort -u", Host.Adb) {
            captured("2026-10-01", """
                permission.CHANGE_COMPONENT_ENABLED_STATE
                permission.DELETE_PACKAGES
                permission.DUMP
                permission.FORCE_STOP_PACKAGES
                permission.READ_LOGS
                permission.REBOOT
                permission.SET_TIME
                permission.WRITE_SECURE_SETTINGS
            """)
            note = """
                • DUMP：完整的 dumpsys　• WRITE_SECURE_SETTINGS：`settings put global/secure`、`wm density`
                • CHANGE_COMPONENT_ENABLED_STATE：`pm disable-user`　• DELETE_PACKAGES：`pm uninstall`
                • FORCE_STOP_PACKAGES：`am force-stop`　• REBOOT：`reboot`　• READ_LOGS：完整的 logcat
            """
        }
    }

    steps("被第一层拦截的操作：ADB 同样无法执行") {
        read("向其他应用的进程发送信号", "kill -0 ${'$'}(pidof com.spocky.projengmenu)", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "/system/bin/sh: kill: 4460: Operation not permitted")
            note = "`kill -0` 并不实际发送信号，只检查是否有权限发送。Projectivy 的身份为 u0_a45，与 shell 不是同一用户，而 shell 又没有 CAP_KILL，因此内核拒绝。结束应用应使用 `am force-stop 包名`：经由第二层，由系统服务执行。"
        }
        read("读取其他进程的内存明细", "head -2 /proc/${'$'}(pidof system_server)/smaps_rollup", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "head: /proc/3309/smaps_rollup: Permission denied")
            note = "shell 能看到所有进程（readproc 组），但无法读取其他用户进程的内存明细。查看内存应使用 `dumpsys meminfo 包名`，同样经由第二层（DUMP 权限）。"
        }
        read("netstat 能否显示端口所属进程", "netstat -tlpn | grep -E 'Program|8088|:1297 '", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program Name
                tcp        0      0 0.0.0.0:1297            0.0.0.0:*               LISTEN      -
                tcp6       0      0 [::]:8088               [::]:*                  LISTEN      9089/z6x_go_server
            """)
            note = "从 ADB 启动的 Go 服务可以显示进程名；其他进程的端口显示为 `-`。查询其他端口的归属见「核查：端口的所属进程」。"
        }
        read("声卡设备的权限", "ls -l /dev/snd | head -3", Host.Adb) {
            captured("2026-10-01", """
                total 0
                crw-rw---- 1 system audio 116,   0 2026-10-01 21:01 controlC0
                crw-rw---- 1 system audio 116,  24 2026-10-01 21:01 pcmC0D0c
            """)
            note = "声卡节点（control 为混音器，pcm…c 为录音，pcm…p 为放音）属于 system 用户和 audio 组，权限为 rw-rw----：其他用户既不能读也不能写。shell 不在 audio 组中，因此 tinymix 之类直接操作声卡的工具无法使用。第一行 `total 0` 是这些文件占用的磁盘块数，设备文件本来就是 0，并不表示目录为空。"
        }
        read("尝试打开混音器", "cat /dev/snd/controlC0", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "cat: /dev/snd/controlC0: Permission denied")
        }
        read("硬件时钟", "ls /dev/rtc*", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "ls: /dev/rtc*: No such file or directory")
            note = "没有 RTC 设备：断电后系统时间需要联网对时才能恢复。"
        }
    }

    steps("SSH 一侧（应用身份）") {
        read("SSH 中的身份", "id", Host.Ssh) {
            captured("2026-10-01", "uid=10068(u0_a68) gid=10068(u0_a68) groups=10068(u0_a68),3003(inet),9997(everybody),20068(u0_a68_cache),50068(all_a68) context=u:r:untrusted_app_27:s0:c68,c256,c512,c768")
            note = "只有联网（inet）组和该应用自身的几个组，没有 input、log、readproc。SELinux 域为 untrusted_app_27（普通应用），其后的 c68… 是用于隔离各应用的标签。"
        }
        read("SSH 可见的进程数", "ls /proc | grep -c '^[0-9]'", Host.Ssh) {
            varies = true
            captured("2026-10-01", "3")
            note = "只能看到自身的进程（SSH 服务、当前 shell 和这条命令）。ADB 中 `ps -A` 可列出三百多个：安卓为 /proc 设置了「隐藏其他用户的进程」，只有 readproc 组能看到全部，shell 属于该组。"
        }
        read("dumpsys", "dumpsys battery", Host.Ssh) {
            expectsError = true
            varies = true
            captured("2026-10-01", "Permission Denial: can't dump BatteryService from from pid=30506, uid=10068 due to missing android.permission.DUMP permission")
            note = "第二层直接拒绝：缺少 DUMP 权限。meminfo、wifi 等其他 dumpsys 同样如此。"
        }
        read("读取设置同样被拒绝", "settings get global adb_enabled 2>&1 | grep -m1 SecurityException", Host.Ssh) {
            expectsError = true
            varies = true
            captured("2026-10-01", "java.lang.SecurityException: Permission Denial: getCurrentUser() from pid=30531, uid=10068 requires android.permission.INTERACT_ACROSS_USERS")
            note = "被拒绝的并非读取设置本身：settings 命令需要先向系统查询当前用户，这一步需要跨用户权限，而应用没有该权限。"
        }
        read("logcat 可见的日志量", "logcat -d | wc -l", Host.Ssh) {
            varies = true
            captured("2026-10-01", "22")
            note = "只有 SimpleSSHD 自身的日志（数十行）。由于没有 READ_LOGS 权限且不在 log 组中，无法查看其他应用和系统的日志。`logcat -g`（查看缓冲区大小）可以执行。"
        }
        read("读取遥控器输入设备", "cat /dev/input/event0", Host.Ssh) {
            expectsError = true
            captured("2026-10-01", "cat: /dev/input/event0: Permission denied")
            note = "设备属于 root:input，应用不在 input 组中：由第一层拒绝。"
        }
    }

    verify("对照表（2026-10-01 实测）") {
        facts(
            "getprop、pm list、读 /proc/meminfo" to "SSH ✓　ADB ✓",
            "看到所有进程（ps -A）" to "SSH ✗ 只能看到自身　ADB ✓（readproc 组）",
            "dumpsys" to "SSH ✗ 缺 DUMP 权限　ADB ✓",
            "settings get / put" to "SSH ✗ 读取也被拒绝　ADB ✓（WRITE_SECURE_SETTINGS）",
            "logcat" to "SSH 仅自身日志　ADB 全部（log 组、READ_LOGS）",
            "pm disable-user、am start" to "SSH ✗ SecurityException（见「SSH 的权限边界」）　ADB ✓",
            "pm uninstall / clear" to "SSH 未验证（无法安全测试）　ADB ✓",
            "读遥控器 /dev/input/event*" to "SSH ✗　ADB ✓（input 组）",
            "访问 /data/local/tmp" to "SSH ✗　ADB ✓",
            "/cache/recovery" to "**两者均 ✗** Permission denied",
            "结束其他应用、读取其他进程的 smaps" to "**ADB 同样 ✗**；应改用 am force-stop、dumpsys meminfo",
            "抓包、改系统时间（date -s）、声卡" to "**两者均 ✗**（没有 capability；声卡属于 audio 组）",
            "通过系统服务设置时间" to "未验证：shell 拥有 SET_TIME 权限，理论上可行",
        )
    }

    audit {
        claim("pm 与 am 本质上是两个 shell 脚本，底层通过 Binder 调用系统核心服务。", Verdict.Confirmed, "实测两个文件均为 `cmd …` 的包装脚本。")
        claim("ADB（uid=2000）：拥有完整的 shell 权限组，可自由卸载、冻结、授予权限、启动任意未加白名单保护的组件。", Verdict.Confirmed, "shell 有 677 项安卓权限，包括停用、卸载、强制停止。")
        claim("一旦调用涉及系统特权的服务（如卸载自带应用、跨应用启动受限组件），Android 12 内核会强制校验 UID 是否为 0(root) 或 2000(shell)，普通 UID 10068 会被立即拦截并抛错中断。", Verdict.Disproved,
            "结果正确（SSH 中会被拒绝），机制有误：执行检查的是 system_server（系统服务）而非内核；检查的是调用者是否拥有相应的**安卓权限**，而非 uid 是否为 0 或 2000。任何被授予 CHANGE_COMPONENT_ENABLED_STATE 的应用同样可以停用组件。")
        claim("kill：Linux DAC 机制规定普通进程只能向同 UID 进程发送信号。SSH 向非本应用发送信号直接报 Operation not permitted；ADB（UID 2000）可向几乎所有应用级进程（UID 10000+）发送信号。", Verdict.Disproved,
            "前半句正确。但 ADB 同样受此规则限制：对应用进程执行 `kill -0` 报 Operation not permitted。")
        claim("cat /proc/<pid>/smaps_rollup：SSH 只能读取自己进程的；ADB 可以读取全系统任意非 root-isolated 进程的内存分布。", Verdict.Disproved,
            "ADB 读取应用进程和 system_server 的 smaps_rollup 均报 Permission denied。")
        claim("netstat -tlpn：SSH 与 ADB 均可列出监听端口；但 SSH 无法看到非本 UID 进程的 PID/Program name 列。", Verdict.Disproved,
            "ADB 同样无法显示：其他进程的端口一样显示为 `-`。")
        claim("tinymix 设置寄存器：SSH 无权写入 /dev/snd/* 字符设备；ADB（UID 2000 且拥有 audio 组权限）可直接调试。", Verdict.Disproved,
            "shell 不在 audio 组中（见上方的 id 输出），声卡节点为 system:audio 660，ADB 打开同样报 Permission denied。")
        claim("date -s：修改系统时间需要 CAP_SYS_TIME 能力。SSH 调用报 Operation not permitted；ADB 同样受限，但可通过 settings put global 触发系统的自动同步逻辑。", Verdict.Unverified,
            "shell 确实没有任何 capability（CapEff 全为 0），无法用 date -s 修改。但 shell 拥有 SET_TIME 安卓权限，或许能通过系统服务直接设置时间，未实测。")
        claim("SSH 进程归属于 u:r:untrusted_app:s0 域，受 Android CTS 严格沙盒限制，无法访问 /data/local/tmp 甚至大部分 /data 目录。", Verdict.Confirmed,
            "域名和 /data/local/tmp 被拒绝均已实测（见「SSH 的权限边界」）。但拒绝来自第一层的用户和组：本机处于 Permissive 模式，SELinux 不拦截。")
        claim("dumpsys battery：SSH（UID 10068）与 ADB 均可读取。", Verdict.Disproved, "SSH 中报 Permission Denial：缺少 DUMP 权限。只有 ADB 能够读取。")
        claim("dumpsys meminfo --oom：SSH 下执行会报安全限制（无 DUMP 权限）；ADB 拥有完整 DUMP 权限。", Verdict.Confirmed, "两侧均已实测，与描述一致。")
        claim("logcat：SSH 受限，只能读取属于本 App 的日志；ADB 拥有完整日志缓冲区读取权。", Verdict.Confirmed, "SSH 中 logcat -d 只有 22 行，均为 SimpleSSHD 自身的日志。")
        claim("/dev/input/event*：SSH 执行 getevent 报 Permission denied；ADB 拥有 input 组权限。", Verdict.Confirmed, "SSH 读取 event0 被拒绝；ADB 的 id 输出中包含 input 组。")
        claim("/cache/recovery/：属于 system:cache，模式 770，普通应用（SSH）无权读取，ADB 可正常查看。", Verdict.Disproved, "ADB 同样报 Permission denied（shell 不在 cache 组中）。")
        claim("/mnt/vendor/xgimiconfig：root:root 755，SSH 与 ADB 均可只读遍历目录，但均无写权限。", Verdict.Confirmed, "两侧均能列出 G0073 等目录；ADB 尝试创建文件被拒绝。")
        claim("settings put global http_proxy：SSH 无权写入全局设置（SecurityException）；ADB 拥有完整权限。", Verdict.Confirmed, "SSH 中连 `settings get` 也报 SecurityException（需要 INTERACT_ACROSS_USERS）。")
        claim("极米 Z6X Pro 内部未配备 RTC 硬件时钟电池，断电后重新上电系统时间会回退至 1970 年或出厂固件打包时间。", Verdict.Unverified,
            "没有 /dev/rtc 设备，这一点相符。时间具体回退到何时未测试（需要同时断电和断网）。")
    }

    related("adb-basics", "pm-am", "selinux-cmds", "ssh-permission-wall", "port-owner")
}
