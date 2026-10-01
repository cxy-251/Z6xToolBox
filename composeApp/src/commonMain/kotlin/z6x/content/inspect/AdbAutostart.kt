package z6x.content.inspect

import z6x.framework.Host
import z6x.framework.module

val AdbAutostart = module("adb-autostart", "核查：ADB 开机自动运行的原因") {
    keywords = "init.rc · persist.sys.usb.config · xgimi patch · on property"
    overview = """
        ADB 最初是在 SSH 中用 `setprop ctl.start adbd` 手动启动的，但重启后它会自动运行。通过阅读系统启动配置（init.rc）找到了原因：极米添加的一段补丁会把「adbd 正在运行」记录为持久化属性。
    """
    verified("2026-10-01")

    why("init 和 .rc 文件") {
        text("""
            安卓开机后的第一个进程是 **init**，它按照 `.rc` 文件中的规则启动服务。规则有两种形式：
            • `service 名称 程序路径`：声明一个服务；`disabled` 表示不自动启动，需由其他规则 `start`。
            • `on 触发条件` 加若干命令：条件满足时执行。条件可以是开机阶段（`on boot`），也可以是属性变化（`on property:某属性=某值`）。
            .rc 文件分布在 /system/etc/init、/vendor/etc/init 等目录中，shell 身份可以读取。
        """)
    }

    story("排查过程") {
        read("哪些 .rc 文件提到了 adbd", "grep -lE 'adbd|sys\\.usb\\.config' /system/etc/init/*.rc /system/etc/init/hw/*.rc /vendor/etc/init/hw/*.rc 2>/dev/null", Host.Adb) {
            captured("2026-10-01", """
                /system/etc/init/init.input_transfer.rc
                /system/etc/init/init.system.rc
                /system/etc/init/hw/init.usb.configfs.rc
                /system/etc/init/hw/init.usb.rc
                /vendor/etc/init/hw/init.huanglong.rc
            """)
            note = "`grep -l` 只列出包含关键词的文件名。huanglong 是本机的平台代号，`init.huanglong.rc` 是厂商（海思与极米）的配置文件。"
        }
        read("adbd 服务本身", "grep -n -A4 '^service adbd' /system/etc/init/init.system.rc", Host.Adb) {
            captured("2026-10-01", """
                174:service adbd /sbin/adbd --root_seclabel=u:r:su:s0
                175-    class core
                176-    socket adbd stream 660 system system
                177-    disabled
                178-    seclabel u:r:adbd:s0
            """)
            note = "`disabled`：adbd 默认不自动启动，需要由其他规则执行 `start adbd`。"
        }
        read("极米添加的补丁", "grep -n -A1 'init.svc.adbd=' /vendor/etc/init/hw/init.huanglong.rc", Host.Adb) {
            captured("2026-10-01", """
                211:on property:init.svc.adbd=stopped
                212-    setprop persist.sys.usb.config mtp
                --
                214:on property:init.svc.adbd=running
                215-    setprop persist.sys.usb.config adb
            """)
            note = """
                **这是关键所在。** `init.svc.adbd` 是 init 报告的 adbd 状态。adbd 一旦变为 running，就将 `persist.sys.usb.config` 设为 adb；停止后则设回 mtp。
                以 `persist.` 开头的属性会写入存储，**重启后仍然保留**。这段规则前后有 `# xgimi patch begin / end` 注释，由极米添加。
            """
        }
        read("开机时用到这个属性的规则", "grep -n -A1 'on boot && property:persist.sys.usb.config' /system/etc/init/hw/init.usb.rc", Host.Adb) {
            captured("2026-10-01", """
                108:on boot && property:persist.sys.usb.config=*
                109-    setprop sys.usb.config ${'$'}{persist.sys.usb.config}
            """)
            note = "开机时将持久化的 USB 模式复制到 `sys.usb.config`。"
        }
        read("USB 模式是 adb 时启动 adbd", "grep -n -A1 'sys.usb.config=adb && property:sys.usb.configfs=1' /system/etc/init/hw/init.usb.configfs.rc | head -2", Host.Adb) {
            captured("2026-10-01", """
                17:on property:sys.usb.config=adb && property:sys.usb.configfs=1
                18-    start adbd
            """)
            note = "本机的 `sys.usb.configfs` 为 1，因此生效的是 configfs 版本的规则。"
        }
        read("当前的属性值", "getprop persist.sys.usb.config; getprop sys.usb.config; getprop sys.usb.configfs", Host.Adb) {
            captured("2026-10-01", """
                adb
                adb
                1
            """)
        }
    }

    verify("完整链条") {
        text("""
            1. 最初在 SSH 中执行 `setprop ctl.start adbd` → adbd 运行。
            2. 极米补丁：adbd 为 running → `persist.sys.usb.config=adb`（持久化）。
            3. 重启：`on boot` → `sys.usb.config = persist.sys.usb.config = adb`。
            4. `sys.usb.config=adb` 且 configfs=1 → `start adbd`。
            5. adbd 为 running → 补丁再次写入 `persist.sys.usb.config=adb`，形成循环。
        """)
    }

    consequences {
        text("""
            • **启动一次即长期有效**：这是补丁的副作用，而非有意设计的功能。
            • **反之亦然**：只要 adbd 被停止一次（`setprop ctl.stop adbd`、某些系统设置切换 USB 模式等），补丁就会将持久化属性改回 mtp，**ADB 立即断开，下次开机也不再自动启动**。恢复方法：从 SSH 再执行一次 `setprop ctl.start adbd`。因此不要卸载 SimpleSSHD。
            • 恢复出厂设置会清除 persist 属性，ADB 随之回到默认的不运行状态。
            • 「ADB 基础」中设置项 `adb_enabled` 重启前为 0、重启后为 1，很可能是开机时系统根据 USB 模式同步的结果（未进一步查证）。
        """)
    }

    lesson("经验") {
        text("""
            • 系统行为的原因往往就写在配置文件中。.rc 文件是纯文本，shell 可以读取，用 `grep` 按关键词逐步追查即可。
            • 阅读规则时先看**触发条件**（on property:…），再查找是谁设置了该属性。
        """)
    }

    related("force-adb", "adb-basics", "props-init")
}
