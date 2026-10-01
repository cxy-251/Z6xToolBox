package z6x.content.inspect

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val FindRealModel = module("find-real-model", "核查：真实型号与芯片") {
    keywords = "getprop · /proc/cpuinfo · CPU part"
    overview = """
        旧文档写的是"联发科 MT9669、4 核 A73"。用 adb 查了几条属性和 /proc/cpuinfo，实际是**海思 Hi3751V660、8 核 Cortex-A55**。
        这一页演示怎么查，以及为什么不能只看一个属性就下结论。
    """
    verified("2026-10-01")

    story("第一步：型号属性只写了 XGIMI TV") {
        read("标准的型号属性", "getprop ro.product.model", Host.Adb) {
            captured("2026-10-01", "XGIMI TV")
            note = "`ro.product.model` 是安卓规定的型号字段，但厂商可以随便填。极米只写了品牌，看不出是哪一款。"
        }
        read("在全部属性里搜型号", "getprop | grep -iE 'z6x|modelname'", Host.Adb) {
            captured("2026-10-01", """
                [ro.boot.xgimi.modelname]: [G0073]
                [ro.xgimi.modelname]: [G0073]
                [xgimi.bt.name]: [XGIMI Z6X Pro 三色激光 旗舰版]
            """)
            note = """
                不带参数的 `getprop` 列出全部属性（几百条），再用 `grep` 过滤。
                `-i` 忽略大小写，`-E` 允许用 `|` 表示"或"。
                型号藏在**蓝牙名称** `xgimi.bt.name` 里；G0073 是极米内部的机型编号。
                （2026-10-01 重启前这里曾多出一行 `[debug.z6x.test]: []`，是「强开网络 ADB」里做实验留下的空属性，重启后已消失。）
            """
        }
    }

    story("第二步：芯片平台") {
        read("平台代号", "getprop ro.board.platform", Host.Adb) {
            captured("2026-10-01", "huanglong")
            note = "这是平台代号，不是芯片型号，光看它查不出是哪家的芯片。"
        }
        read("产品名里带着芯片型号", "getprop ro.product.product.name", Host.Adb) {
            captured("2026-10-01", "tv_hi3751v660")
            note = "**Hi3751** 是海思（HiSilicon）电视芯片的型号前缀，V660 是具体型号。和 MT9669（联发科）完全不是一家。"
        }
    }

    story("第三步：CPU 核心数和型号") {
        read("数 CPU 核心", "grep -c ^processor /proc/cpuinfo", Host.Adb) {
            captured("2026-10-01", "8")
            note = "`/proc/cpuinfo` 每个核心一段，段首是 `processor : N`。`grep -c` 只输出匹配的行数。"
        }
        read("看核心的型号编号", "grep 'CPU part' /proc/cpuinfo | sort | uniq -c", Host.Adb) {
            captured("2026-10-01", "      8 CPU part\t: 0xd05")
            note = """
                `sort | uniq -c` 合并相同的行并计数：8 个核心都是同一个编号。
                ARM 的核心编号是公开的：**0xd05 = Cortex-A55**（0xd03 是 A53，0xd09 是 A73）。
            """
        }
    }

    verify("结论") {
        facts(
            "型号" to "XGIMI Z6X Pro 三色激光 旗舰版（内部编号 G0073）",
            "芯片" to "海思 Hi3751V660（平台代号 huanglong）",
            "CPU" to "8 核 Cortex-A55",
            "旧文档" to "联发科 MT9669、4 核 A73：**错误**",
        )
    }

    lesson("经验") {
        text("""
            • 一个属性不够，要**交叉验证**：型号字段、平台代号、产品名、/proc/cpuinfo 互相印证，才能下结论。
            • `/proc/cpuinfo` 是内核直接给出的硬件信息，厂商不好改，比 getprop 里的文字更可靠。
            • AI 写的"实测数据"要自己跑一遍。旧文档的 MT9669 很可能是 AI 按"极米投影仪常见芯片"猜的。
        """)
    }

    audit {
        claim("芯片平台：联发科 MT9669（开发代号 huanglong），4 核 Cortex-A73 处理器（旧版「通过 SSH 获取硬件与系统信息」）", Verdict.Disproved,
            "海思 Hi3751V660，8 核 Cortex-A55。查法就是本页。")
        claim("设备型号：getprop ro.product.model 输出 Z6X Pro", Verdict.Disproved,
            "输出是 `XGIMI TV`；型号在 `xgimi.bt.name` 里。")
    }

    related("force-adb")
}
