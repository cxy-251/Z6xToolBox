package z6x.content.manual

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val PermModel = module("perm-model", "原理：同一条命令，SSH 和 ADB 为什么结果不一样") {
    keywords = "uid · 用户组 · capability · SELinux · Binder · cmd · 系统权限 · DUMP"
    overview = """
        同一条 `pm disable-user`，在 ADB 里成功，在 SSH 里报 SecurityException；`kill` 在 ADB 里也杀不掉别的 App。原因是安卓有**两层**权限检查：Linux 内核管文件、设备和信号；安卓系统服务管 pm、am、settings 这类操作。ADB 和 SSH 在这两层上的身份不同。
    """
    verified("2026-10-01")

    why("两层权限") {
        text("""
            **第一层：Linux 内核。** 打开文件和设备、给进程发信号时，内核检查三样东西：
            • **用户和组**（uid / gid）：文件属于谁、哪个组能读写。比如遥控器的输入设备属于 input 组，ADB 的 shell 在 input 组里，所以能读；SSH 那个 App 身份不在，就读不了。
            • **能力**（capability）：root 的特权被拆成几十项，比如 CAP_NET_RAW（抓包）、CAP_SYS_TIME（改系统时间）、CAP_KILL（给别人的进程发信号）。ADB 的 shell 一项都没有。
            • **SELinux**：在用户和组之外再加一层规则（每个进程有一个「域」，比如 shell 是 `u:r:shell:s0`，普通 App 是 `untrusted_app`）。这台机器是 Permissive 模式，违规只记日志不拦截，所以这一层基本不起作用。

            **第二层：安卓系统服务。** `pm`、`am`、`wm`、`settings`、`input` 其实都是两行的 shell 脚本，内容就是 `cmd package`、`cmd activity` 这类命令。`cmd` 通过 **Binder**（安卓的进程间通信机制）把请求发给系统服务进程 system_server。system_server 看调用者的 uid 有没有对应的**安卓权限**（比如停用组件要 CHANGE_COMPONENT_ENABLED_STATE），没有就抛 SecurityException。

            uid 2000（shell）的安卓权限来自系统里一个叫 com.android.shell 的包，它在系统里预先登记了几百项调试用的权限。SSH 用的 SimpleSSHD 是普通 App（uid 10068），只有它自己申请的少数几项。
        """)
    }

    steps("自己查：身份和权限") {
        read("我是谁、在哪些组、什么 SELinux 域", "id", Host.Adb) {
            captured("2026-10-01", "uid=2000(shell) gid=2000(shell) groups=2000(shell),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),1078(ext_data_rw),1079(ext_obb_rw),3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc),3011(uhid) context=u:r:shell:s0")
            note = "input 组能读写遥控器设备；log 组能读全部日志；inet 组能联网；readproc 能看到所有进程。最后的 context 就是 SELinux 域。"
        }
        read("有哪些 capability", "grep Cap /proc/self/status", Host.Adb) {
            captured("2026-10-01", """
                CapInh:	0000000000000000
                CapPrm:	0000000000000000
                CapEff:	0000000000000000
                CapBnd:	00000000000000c0
                CapAmb:	0000000000000000
            """)
            note = "CapEff 是当前生效的能力，全 0 表示一项都没有。所以抓包、改系统时间、给别人的进程发信号都不行。"
        }
        read("pm 到底是什么", "cat /system/bin/pm", Host.Adb) {
            captured("2026-10-01", """
                #!/system/bin/sh
                cmd package "${'$'}@"
            """)
            note = "wm、settings、input 也一样（`cmd window`、`cmd settings`、`cmd input`）；am 多一个 instrument 分支。所以真正决定能不能做的是系统服务，不是这个脚本。"
        }
        read("shell 有多少项安卓权限", "dumpsys package com.android.shell | grep -c 'granted=true'", Host.Adb) {
            captured("2026-10-01", "677")
        }
        read("其中几项关键的", "dumpsys package com.android.shell | grep 'granted=true' | grep -oE 'permission\\.(DUMP|WRITE_SECURE_SETTINGS|CHANGE_COMPONENT_ENABLED_STATE|DELETE_PACKAGES|FORCE_STOP_PACKAGES|REBOOT|SET_TIME|READ_LOGS)' | sort -u", Host.Adb) {
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

    steps("第一层挡住的：ADB 也做不到") {
        read("给别的 App 进程发信号", "kill -0 ${'$'}(pidof com.spocky.projengmenu)", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "/system/bin/sh: kill: 4460: Operation not permitted")
            note = "`kill -0` 不真正发信号，只检查有没有权限发。Projectivy 的身份是 u0_a45，和 shell 不是同一个用户，shell 又没有 CAP_KILL，所以内核拒绝。要结束 App 用 `am force-stop 包名`：走第二层，由系统服务去结束。"
        }
        read("读别的进程的内存明细", "head -2 /proc/${'$'}(pidof system_server)/smaps_rollup", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "head: /proc/3309/smaps_rollup: Permission denied")
            note = "能看到所有进程（readproc 组），但看不了别的用户进程的内存明细。要看内存用 `dumpsys meminfo 包名`：同样走第二层（DUMP 权限）。"
        }
        read("netstat 能不能看到端口属于哪个进程", "netstat -tlpn | grep -E 'Program|8088|:1297 '", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program Name
                tcp        0      0 0.0.0.0:1297            0.0.0.0:*               LISTEN      -
                tcp6       0      0 [::]:8088               [::]:*                  LISTEN      9089/z6x_go_server
            """)
            note = "自己从 ADB 启动的 Go 服务能显示进程名；别人的端口显示 `-`。查别人的端口见「案例：查出端口属于哪个程序」。"
        }
        read("声卡设备的权限", "ls -l /dev/snd | head -3", Host.Adb) {
            captured("2026-10-01", """
                total 0
                crw-rw---- 1 system audio 116,   0 2026-10-01 21:01 controlC0
                crw-rw---- 1 system audio 116,  24 2026-10-01 21:01 pcmC0D0c
            """)
            note = "声卡节点（control 是混音器，pcm…c 是录音，pcm…p 是放音）属于 system 用户和 audio 组，权限 rw-rw----：别人既不能读也不能写。shell 不在 audio 组，所以 tinymix 这类直接调声卡的工具用不了。第一行 `total 0` 是这些文件占的磁盘块数，设备文件本来就是 0，不代表目录是空的。"
        }
        read("试着打开混音器", "cat /dev/snd/controlC0", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "cat: /dev/snd/controlC0: Permission denied")
        }
        read("硬件时钟", "ls /dev/rtc*", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "ls: /dev/rtc*: No such file or directory")
            note = "没有 RTC 设备：断电后系统时间要靠联网对时恢复。"
        }
    }

    verify("对照表（ADB 均为 2026-10-01 实测）") {
        facts(
            "pm list / getprop / 读 /proc" to "SSH ✓　ADB ✓（不需要特殊权限）",
            "pm disable-user、am start" to "SSH ✗ SecurityException（实测，见「SSH 能查不能改」）　ADB ✓（第二层）",
            "pm uninstall / clear" to "SSH 未验证（没法安全地测）　ADB ✓ 有 DELETE_PACKAGES 等权限",
            "settings put global、wm density" to "SSH 未验证　ADB ✓ 有 WRITE_SECURE_SETTINGS",
            "完整的 dumpsys、logcat" to "SSH 未验证　ADB ✓ 有 DUMP、READ_LOGS",
            "读遥控器 /dev/input/event*" to "SSH 未验证（设备属于 root:input，App 身份不在 input 组，按第一层规则应该读不了）　ADB ✓（input 组）",
            "访问 /data/local/tmp" to "SSH ✗ Permission denied　ADB ✓",
            "kill 别的 App、读别人的 smaps" to "**ADB 也 ✗**（第一层）；用 am force-stop、dumpsys meminfo 代替",
            "抓包、改系统时间（date -s）、声卡" to "**ADB 也 ✗**（没有 capability；声卡不对 shell 开放）",
            "通过系统服务设置时间" to "未验证：shell 有 SET_TIME 权限，理论上能走系统服务改时间",
        )
    }

    audit {
        claim("pm 与 am 本质上是两个 shell 脚本，底层通过 Binder 调用系统核心服务。", Verdict.Confirmed, "实测两个文件都是 `cmd …` 的包装脚本。")
        claim("ADB（uid=2000）：拥有完整的 shell 权限组，可自由卸载、冻结、授予权限、启动任意未加白名单保护的组件。", Verdict.Confirmed, "shell 有 677 项安卓权限，包括停用、卸载、强制停止。")
        claim("kill：Linux DAC 机制规定普通进程只能向同 UID 进程发送信号。SSH 向非本应用发送信号直接报 Operation not permitted；ADB（UID 2000）可向几乎所有应用级进程（UID 10000+）发送信号。", Verdict.Disproved,
            "前半句对。但 ADB 一样受这条规则限制：`kill -0` 一个 App 进程报 Operation not permitted。")
        claim("cat /proc/<pid>/smaps_rollup：SSH 只能读取自己进程的；ADB 可以读取全系统任意非 root-isolated 进程的内存分布。", Verdict.Disproved,
            "ADB 读 App 进程和 system_server 的 smaps_rollup 都是 Permission denied。")
        claim("netstat -tlpn：SSH 与 ADB 均可列出监听端口；但 SSH 无法看到非本 UID 进程的 PID/Program name 列。", Verdict.Disproved,
            "ADB 也看不到：别的进程的端口一样显示 `-`。")
        claim("tinymix 设置寄存器：SSH 无权写入 /dev/snd/* 字符设备；ADB（UID 2000 且拥有 audio 组权限）可直接调试。", Verdict.Disproved,
            "shell 不在 audio 组里（见上面的 id），声卡节点是 system:audio 660，ADB 打开也是 Permission denied。")
        claim("date -s：修改系统时间需要 CAP_SYS_TIME 能力。SSH 调用报 Operation not permitted；ADB 同样受限，但可通过 settings put global 触发系统的自动同步逻辑。", Verdict.Unverified,
            "shell 确实没有任何 capability（CapEff 全 0），date -s 改不了。但 shell 有 SET_TIME 安卓权限，可能可以通过系统服务直接设时间，没有实测。")
        claim("SSH 进程归属于 u:r:untrusted_app:s0 域，受 Android CTS 严格沙盒限制，无法访问 /data/local/tmp 甚至大部分 /data 目录。", Verdict.Confirmed,
            "域名和 /data/local/tmp 被拒绝都实测过（见「SSH 能查不能改」）。但拒绝来自第一层的用户和组：这台是 Permissive，SELinux 不拦截。")
        claim("极米 Z6X Pro 内部未配备 RTC 硬件时钟电池，断电后重新上电系统时间会回退至 1970 年或出厂固件打包时间。", Verdict.Unverified,
            "没有 /dev/rtc 设备，这一点符合。时间具体回退到哪里没有测（要断电又断网）。")
    }

    related("adb-basics", "pm-am", "selinux-cmds", "ssh-permission-wall", "port-owner")
}
