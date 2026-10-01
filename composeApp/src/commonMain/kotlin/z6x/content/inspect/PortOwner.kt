package z6x.content.inspect

import z6x.framework.Host
import z6x.framework.module

val PortOwner = module("port-owner", "核查：端口的所属进程") {
    keywords = "netstat · /proc/net/tcp · uid · pm list packages -U"
    overview = """
        投影仪上开放着十余个网络端口。要确定每个端口属于哪个应用，常用的 `netstat -p` 在 shell 身份下无法显示，经过两次调整方法才查明。
    """
    verified("2026-10-01")

    story("方法一：netstat -p 无法显示进程") {
        read("列出监听中的 TCP 端口", "netstat -tlnp | grep LISTEN", Host.Adb) {
            varies = true
            note = """
                `-t` 表示 TCP，`-l` 只看监听状态，`-n` 显示数字而不解析名称，`-p` 显示进程。
                最后一列本应是「进程号/程序名」，但除 shell 自己启动的进程外都显示为 `-`：netstat 需要读取各进程打开的文件（/proc/<pid>/fd）才能把端口对应到进程，而 shell 无权读取其他用户的进程文件。
            """
        }
    }

    story("方法二：从 /proc/net/tcp 读取 uid") {
        text("""
            内核在 `/proc/net/tcp`（IPv6 为 tcp6）中列出所有 TCP 连接，每行有一列 **uid**，所有用户都能读取。
            第 2 列为「本地地址:端口」（十六进制），第 4 列为状态（`0A` 表示 LISTEN），第 8 列为 uid。
        """)
        read("查看原始数据", "head -3 /proc/net/tcp", Host.Adb) {
            varies = true
        }
        text("""
            原计划在设备上用 awk 的 `strtonum` 将十六进制端口转换为十进制，但设备上的 awk **没有这个函数**，因此没有任何输出。
            **调整方法：** 设备只负责输出原始数据，换算改在 Deck 上完成（Deck 的 bash 支持 `${'$'}((16#十六进制))`）。
        """)
        read("在 Deck 上换算：端口 → uid", """
            adb shell 'cat /proc/net/tcp /proc/net/tcp6' \
              | awk 'NR>1 && ${'$'}4=="0A" {split(${'$'}2,a,":"); print a[2], ${'$'}8}' \
              | while read hex uid; do echo "${'$'}((16#${'$'}hex)) ${'$'}uid"; done | sort -un
        """, Host.Deck) {
            note = """
                `adb shell` 只负责取回文件内容，其余处理都在 Deck 上完成：
                awk 选出状态为 0A 的行，输出十六进制端口和 uid；`while read` 逐行换算为十进制；`sort -un` 按数值排序并去重。
            """
        }
        read("查询 uid 对应的包", "pm list packages -U | grep -E 'uid:(10068|10069)\$'", Host.Adb) {
            captured("2026-10-01", """
                package:org.galexander.sshd uid:10068
                package:com.github.metacubex.clash.meta uid:10069
            """)
            note = "`-U` 在包名后附加 uid。"
        }
    }

    verify("2026-10-01 的结果") {
        facts(
            "2222" to "uid 10068 · SimpleSSHD",
            "5555" to "uid 2000 · adbd（ADB）",
            "8088" to "uid 2000 · 「开发环境」中手动启动的 Go 测试服务",
            "7890 / 7891" to "uid 10069 · Clash Meta 代理端口",
            "1458、7001、7002、7100、8080、12583-12585、62110" to "uid 1000 · 某个系统组件（无法细分）",
            "15735、15737、28081" to "uid 0 · root 进程",
        )
        text("""
            **uid 1000 由所有 system 应用共用**（数十个包），因此只能确定「由某个系统组件开放」。进一步细分需要 root 权限查看进程。
            7100 是 AirPlay 镜像的常用端口，结合无线投屏应用推测与其相关，未验证。
        """)
    }

    lesson("经验") {
        text("""
            • 工具无法给出答案时，去查找**它背后的数据源**：netstat 本身就是读取 /proc/net/tcp。
            • 设备上的工具是精简版，缺少功能时可将数据取回电脑处理。
            • uid 可以定位到具体应用，但对共享 uid 的系统组件则无法细分，应清楚权限的边界。
        """)
    }

    related("clash-proxy", "screen-cast", "ssh-probe")
}
