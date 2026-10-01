package z6x.content.inspect

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val FindRealModel = module("find-real-model", "核查：真实型号与芯片") {
    keywords = "getprop · /proc/cpuinfo · CPU part"
    overview = """
        旧记录写的是「联发科 MT9669、4 核 A73」。通过 adb 查询若干属性和 /proc/cpuinfo，实际为**海思 Hi3751V660、8 核 Cortex-A55**。
        本页演示查询方法，并说明为什么不能仅凭一个属性下结论。
    """
    verified("2026-10-01")

    story("第一步：型号属性仅为 XGIMI TV") {
        read("标准的型号属性", "getprop ro.product.model", Host.Adb) {
            captured("2026-10-01", "XGIMI TV")
            note = "`ro.product.model` 是安卓规定的型号字段，但厂商可以任意填写。极米只填写了品牌，无法看出具体型号。"
        }
        read("在全部属性中搜索型号", "getprop | grep -iE 'z6x|modelname'", Host.Adb) {
            captured("2026-10-01", """
                [ro.boot.xgimi.modelname]: [G0073]
                [ro.xgimi.modelname]: [G0073]
                [xgimi.bt.name]: [XGIMI Z6X Pro 三色激光 旗舰版]
            """)
            note = """
                不带参数的 `getprop` 列出全部属性（数百条），再用 `grep` 过滤。
                `-i` 表示忽略大小写，`-E` 允许用 `|` 表示「或」。
                型号位于**蓝牙名称** `xgimi.bt.name` 中；G0073 是极米内部的机型编号。
                （2026-10-01 重启前，此处曾多出一行 `[debug.z6x.test]: []`，是「通过 SSH 启动网络 ADB」中实验遗留的空属性，重启后已消失。）
            """
        }
    }

    story("第二步：芯片平台") {
        read("平台代号", "getprop ro.board.platform", Host.Adb) {
            captured("2026-10-01", "huanglong")
            note = "这是平台代号而非芯片型号，仅凭它无法判断芯片厂商。"
        }
        read("产品名中包含芯片型号", "getprop ro.product.product.name", Host.Adb) {
            captured("2026-10-01", "tv_hi3751v660")
            note = "**Hi3751** 是海思（HiSilicon）电视芯片的型号前缀，V660 是具体型号，与联发科的 MT9669 并非同一厂商。"
        }
    }

    story("第三步：CPU 核心数和型号") {
        read("统计 CPU 核心数", "grep -c ^processor /proc/cpuinfo", Host.Adb) {
            captured("2026-10-01", "8")
            note = "`/proc/cpuinfo` 中每个核心占一段，段首为 `processor : N`。`grep -c` 只输出匹配的行数。"
        }
        read("查看核心的型号编号", "grep 'CPU part' /proc/cpuinfo | sort | uniq -c", Host.Adb) {
            captured("2026-10-01", "      8 CPU part\t: 0xd05")
            note = """
                `sort | uniq -c` 合并相同的行并计数：8 个核心的编号相同。
                ARM 的核心编号是公开的：**0xd05 为 Cortex-A55**（0xd03 为 A53，0xd09 为 A73）。
            """
        }
    }

    verify("结论") {
        facts(
            "型号" to "XGIMI Z6X Pro 三色激光 旗舰版（内部编号 G0073）",
            "芯片" to "海思 Hi3751V660（平台代号 huanglong）",
            "CPU" to "8 核 Cortex-A55",
            "旧记录" to "联发科 MT9669、4 核 A73：**错误**",
        )
    }

    lesson("经验") {
        text("""
            • 单个属性不足以定论，需要**交叉验证**：型号字段、平台代号、产品名和 /proc/cpuinfo 相互印证后才能下结论。
            • `/proc/cpuinfo` 是内核直接提供的硬件信息，厂商难以修改，比 getprop 中的文字更可靠。
            • 旧记录中的「实测数据」必须亲自复核。MT9669 很可能是依据「极米投影仪的常见芯片」推测出来的。
        """)
    }

    audit {
        claim("芯片平台：联发科 MT9669（开发代号 huanglong），4 核 Cortex-A73 处理器（旧版「通过 SSH 获取硬件与系统信息」）", Verdict.Disproved,
            "海思 Hi3751V660，8 核 Cortex-A55。查询方法即本页内容。")
        claim("设备型号：getprop ro.product.model 输出 Z6X Pro", Verdict.Disproved,
            "输出为 `XGIMI TV`；型号位于 `xgimi.bt.name` 中。")
    }

    related("force-adb")
}
