package z6x.content.inspect

import z6x.framework.Host
import z6x.framework.module

val AdbAutostart = module("adb-autostart", "案例：ADB 为什么开机就自动运行") {
    keywords = "init.rc · persist.sys.usb.config · xgimi patch · on property"
    overview = """
        当初 ADB 是在 SSH 里用 `setprop ctl.start adbd` 手动拉起的，可重启后它自己就起来了。读系统的启动配置（init.rc）找到了原因：极米加的一段补丁，把「ADB 在运行」记成了持久化属性。
    """
    verified("2026-10-01")

    why("init 和 .rc 文件") {
        text("""
            安卓开机后第一个进程是 **init**，它按 `.rc` 文件里的规则启动服务。规则的形式是：
            • `service 名字 程序路径`：声明一个服务，`disabled` 表示不自动启动，要等别人 `start` 它。
            • `on 触发条件` + 若干命令：条件满足时执行。条件可以是开机阶段（`on boot`），也可以是属性变化（`on property:某属性=某值`）。
            .rc 文件散落在 /system/etc/init、/vendor/etc/init 等目录，shell 身份可以读。
        """)
    }

    story("一步步找") {
        read("哪些 .rc 文件提到了 adbd", "grep -lE 'adbd|sys\\.usb\\.config' /system/etc/init/*.rc /system/etc/init/hw/*.rc /vendor/etc/init/hw/*.rc 2>/dev/null", Host.Adb) {
            captured("2026-10-01", """
                /system/etc/init/init.input_transfer.rc
                /system/etc/init/init.system.rc
                /system/etc/init/hw/init.usb.configfs.rc
                /system/etc/init/hw/init.usb.rc
                /vendor/etc/init/hw/init.huanglong.rc
            """)
            note = "`grep -l` 只列出包含关键词的文件名。huanglong 就是这台的平台代号，`init.huanglong.rc` 是厂商（海思 + 极米）自己的配置。"
        }
        read("adbd 服务本身", "grep -n -A4 '^service adbd' /system/etc/init/init.system.rc", Host.Adb) {
            captured("2026-10-01", """
                174:service adbd /sbin/adbd --root_seclabel=u:r:su:s0
                175-    class core
                176-    socket adbd stream 660 system system
                177-    disabled
                178-    seclabel u:r:adbd:s0
            """)
            note = "`disabled`：adbd 默认不自动启动，要有规则去 `start adbd`。"
        }
        read("极米加的补丁", "grep -n -A1 'init.svc.adbd=' /vendor/etc/init/hw/init.huanglong.rc", Host.Adb) {
            captured("2026-10-01", """
                211:on property:init.svc.adbd=stopped
                212-    setprop persist.sys.usb.config mtp
                --
                214:on property:init.svc.adbd=running
                215-    setprop persist.sys.usb.config adb
            """)
            note = """
                **关键在这里。** `init.svc.adbd` 是 init 报告的 adbd 状态。adbd 一变成 running，就把 `persist.sys.usb.config` 设成 adb；停了就设回 mtp。
                `persist.` 开头的属性会写进存储，**重启后还在**。这段规则前后有 `# xgimi patch begin / end` 注释，是极米自己加的。
            """
        }
        read("开机时用到这个属性的规则", "grep -n -A1 'on boot && property:persist.sys.usb.config' /system/etc/init/hw/init.usb.rc", Host.Adb) {
            captured("2026-10-01", """
                108:on boot && property:persist.sys.usb.config=*
                109-    setprop sys.usb.config ${'$'}{persist.sys.usb.config}
            """)
            note = "开机时把持久化的 USB 模式抄到 `sys.usb.config`。"
        }
        read("USB 模式是 adb 时启动 adbd", "grep -n -A1 'sys.usb.config=adb && property:sys.usb.configfs=1' /system/etc/init/hw/init.usb.configfs.rc | head -2", Host.Adb) {
            captured("2026-10-01", """
                17:on property:sys.usb.config=adb && property:sys.usb.configfs=1
                18-    start adbd
            """)
            note = "这台机器 `sys.usb.configfs` 是 1，所以生效的是 configfs 版本的规则。"
        }
        read("现在的属性值", "getprop persist.sys.usb.config; getprop sys.usb.config; getprop sys.usb.configfs", Host.Adb) {
            captured("2026-10-01", """
                adb
                adb
                1
            """)
        }
    }

    verify("完整链条") {
        text("""
            1. 当初在 SSH 里 `setprop ctl.start adbd` → adbd 运行。
            2. 极米补丁：adbd running → `persist.sys.usb.config=adb`（持久化）。
            3. 重启：`on boot` → `sys.usb.config = persist.sys.usb.config = adb`。
            4. `sys.usb.config=adb` 且 configfs=1 → `start adbd`。
            5. adbd running → 补丁再次写 `persist.sys.usb.config=adb`，循环保持。
        """)
    }

    consequences {
        text("""
            • **一次强开，长期有效**——这是补丁的副作用，不是设计好的功能。
            • **反过来也成立**：只要 adbd 被停掉一次（`setprop ctl.stop adbd`、某些系统设置切换 USB 模式等），补丁就把持久化属性改回 mtp，**ADB 立刻断开，下次开机也不会再自动启动**。恢复办法：从 SSH 再执行一次 `setprop ctl.start adbd`。所以 SimpleSSHD 不要卸载。
            • 恢复出厂会清掉 persist 属性，ADB 也就回到默认不运行。
            • 「ADB 基础」里看到设置项 `adb_enabled` 重启前是 0、重启后是 1，很可能是开机时系统按 USB 模式同步过去的（没有进一步查证）。
        """)
    }

    lesson("经验") {
        text("""
            • 「为什么会这样」的答案常常就写在系统配置里。.rc 文件是纯文本，shell 能读，`grep` 关键词顺藤摸瓜。
            • 看规则要看**触发条件**（on property:…），再找是谁设置了这个属性。
        """)
    }

    related("force-adb", "adb-basics", "props-init")
}
