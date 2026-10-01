package z6x.content.inspect

import z6x.framework.Host
import z6x.framework.module

val PortOwner = module("port-owner", "案例：这个端口是谁开的") {
    keywords = "netstat · /proc/net/tcp · uid · pm list packages -U"
    overview = """
        投影仪上开着十几个网络端口。想知道每个端口属于哪个应用，常用的 `netstat -p` 在 shell 身份下看不到。换了两次办法才查出来。
    """
    verified("2026-10-01")

    story("第一个办法：netstat -p 看不到") {
        read("列出监听中的 TCP 端口", "netstat -tlnp | grep LISTEN", Host.Adb) {
            varies = true
            note = """
                `-t` TCP，`-l` 只看监听，`-n` 显示数字不解析名字，`-p` 显示进程。
                最后一列本该是"进程号/程序名"，但全是 `-`：shell 身份不能查看别人的进程信息。
            """
        }
    }

    story("第二个办法：从 /proc/net/tcp 读 uid") {
        text("""
            内核在 `/proc/net/tcp`（IPv6 是 tcp6）里列出所有 TCP 连接，每行有一列 **uid**，所有人都能读。
            第 2 列是"本地地址:端口"（十六进制），第 4 列是状态（`0A` 表示 LISTEN），第 8 列是 uid。
        """)
        read("看原始数据", "head -3 /proc/net/tcp", Host.Adb) {
            varies = true
        }
        text("""
            本想在设备上用 awk 的 `strtonum` 把十六进制端口换成十进制，结果设备上的 awk（toybox 版）**没有这个函数**，什么也没输出。
            **换办法：** 设备只负责输出原始数据，换算放到 Deck 上做（Deck 的 bash 支持 `${'$'}((16#十六进制))`）。
        """)
        read("在 Deck 上换算：端口 → uid", """
            adb shell 'cat /proc/net/tcp /proc/net/tcp6' \
              | awk 'NR>1 && ${'$'}4=="0A" {split(${'$'}2,a,":"); print a[2], ${'$'}8}' \
              | while read hex uid; do echo "${'$'}((16#${'$'}hex)) ${'$'}uid"; done | sort -un
        """, Host.Deck) {
            note = """
                `adb shell` 只负责把文件内容拿回来，后面的处理都在 Deck 上：
                awk 挑出状态为 0A 的行，打印十六进制端口和 uid；`while read` 逐行换算成十进制；`sort -un` 按数字排序去重。
            """
        }
        read("uid 对应哪个包", "pm list packages -U | grep -E 'uid:(10068|10069)\$'", Host.Adb) {
            captured("2026-10-01", """
                package:org.galexander.sshd uid:10068
                package:com.github.metacubex.clash.meta uid:10069
            """)
            note = "`-U` 在包名后面附上 uid。"
        }
    }

    verify("2026-10-01 的结果") {
        facts(
            "2222" to "uid 10068 · SimpleSSHD",
            "5555" to "uid 2000 · adbd（ADB）",
            "8088" to "uid 2000 · 开发环境里手动启动的 Go 测试服务",
            "7890 / 7891" to "uid 10069 · Clash Meta 代理端口",
            "1458、7001、7002、7100、8080、12583-12585、62110" to "uid 1000 · 某个系统组件（无法细分）",
            "15735、15737、28081" to "uid 0 · root 进程",
        )
        text("""
            **uid 1000 是所有 system 应用共用的**（几十个包），所以只能知道"是某个系统组件开的"。要进一步细分需要 root 权限看进程。
            7100 是 AirPlay 镜像常用的端口，结合无线投屏应用推测和它有关，未验证。
        """)
    }

    lesson("经验") {
        text("""
            • 工具给不出答案时，去找**它背后的数据源**：netstat 本身就是读 /proc/net/tcp 的。
            • 设备上的工具是精简版（toybox），缺功能时把数据搬回电脑上处理。
            • uid 能定位到"哪个 App"，但对共享 uid 的系统组件就到头了，要知道权限的边界。
        """)
    }

    related("clash-proxy", "screen-cast", "ssh-probe")
}
