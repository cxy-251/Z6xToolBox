package z6x.device

/** 一项检查的期望值。judge 判断实际输出是否符合；text 是给人看的期望描述。 */
sealed interface Expect {
    val text: String
    fun judge(output: String): Boolean

    data class Equals(val value: String) : Expect {
        override val text get() = if (value.isEmpty()) "无输出" else "= $value"
        override fun judge(output: String) = output == value
    }

    data class Contains(val value: String) : Expect {
        override val text get() = "含 $value"
        override fun judge(output: String) = output.contains(value)
    }

    /** 输出是一个整数，并且在 min..max 之间。 */
    data class Between(val min: Long, val max: Long, override val text: String) : Expect {
        override fun judge(output: String) = output.trim().toLongOrNull()?.let { it in min..max } ?: false
    }

    data object NotEmpty : Expect {
        override val text get() = "有输出"
        override fun judge(output: String) = output.isNotBlank()
    }
}

enum class Level { Must, Info }

/**
 * 一项体检。不通过时：Must 显示为异常，Info 显示为提示。
 * module 是出问题时该看的知识库页面 id（那里有原因和恢复办法）。
 * 期望值全部来自 2026-10-01 的实机状态。
 */
data class HealthCheck(
    val title: String,
    val command: String,
    val expect: Expect,
    val level: Level,
    val module: String,
    val hint: String,
)

val healthChecks = listOf(
    HealthCheck("ADB 是 shell 身份", "id -un", Expect.Equals("shell"), Level.Must, "force-adb",
        "ADB 身份不对，大部分定制命令会失败。"),
    HealthCheck("SELinux 宽容模式", "getenforce", Expect.Equals("Permissive"), Level.Must, "force-adb",
        "变成 Enforcing 多半是系统升级了。从 SSH 强开 ADB 的办法会失效，请小心不要停掉 adbd。"),
    HealthCheck("ADB 开机自启", "getprop persist.sys.usb.config", Expect.Equals("adb"), Level.Must, "adb-autostart",
        "下次重启后 ADB 不会自动运行。从 SSH 执行 setprop ctl.start adbd 即可恢复。"),
    HealthCheck("停用的预装组件", "pm list packages -d | wc -l", Expect.Equals("29"), Level.Must, "debloat-list",
        "数量不对，定制可能被系统升级还原了。运行一键精简脚本。"),
    HealthCheck("官方桌面和影视推荐已卸载", "pm list packages | grep -cE '^package:com.xgimi.(home|stream.video)${'$'}'", Expect.Equals("0"), Level.Must, "projectivy-launcher",
        "官方桌面回来了。运行一键精简脚本。"),
    HealthCheck("桌面是 Projectivy", "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1", Expect.Contains("com.spocky.projengmenu"), Level.Must, "projectivy-launcher",
        "按 Home 不会回到 Projectivy。"),
    HealthCheck("输入法是搜狗", "settings get secure default_input_method", Expect.Contains("sogou"), Level.Must, "input-method",
        "换过输入法后遥控器可能变卡。"),
    HealthCheck("Clash 的 VPN 在运行", "dumpsys vpn_management 2>/dev/null | grep -c clash", Expect.Between(1, 99, "≥ 1"), Level.Info, "clash-proxy",
        "代理没开。需要时在电视上打开 Clash Meta 启动。"),
    HealthCheck("Go 测试服务在运行", "pidof z6x_go_server", Expect.NotEmpty, Level.Info, "go-server",
        "重启后要重新启动（见应急手册「情况 6」）。"),
    HealthCheck("CPU 温度", "cat /sys/class/thermal/thermal_zone0/temp", Expect.Between(0, 80_000, "< 80℃（单位千分之一度）"), Level.Must, "proc-metrics",
        "温度偏高，检查进出风口是否被挡住。"),
    HealthCheck("可用内存", "grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2", Expect.Between(500_000, Long.MAX_VALUE, "> 500MB（单位 KB）"), Level.Must, "process-memory",
        "可用内存很低，看看是哪个 App 占得多。"),
    HealthCheck("/data 剩余空间", "df /data | tail -1 | tr -s ' ' | cut -d' ' -f4", Expect.Between(2_000_000, Long.MAX_VALUE, "> 2GB（单位 KB）"), Level.Must, "storage-partitions",
        "存储快满了。"),
    HealthCheck("停用的常驻组件仍在运行（已知问题）", "for p in com.xgimi.xgimihilink com.xgimi.mateservice com.xgimi.soundermodeservice; do pidof ${'$'}p >/dev/null && echo -n x; done", Expect.Equals(""), Level.Info, "debloat-list",
        "每个 x 是一个停用后仍在运行的 PERSISTENT 组件。disable-user 挡不住它们，属于已知问题。"),
)

/** 一项检查的结果。 */
data class HealthResult(val check: HealthCheck, val output: String, val ok: Boolean, val error: String? = null)

/** 依次执行全部检查。单项出错不影响其他项。 */
suspend fun runHealthChecks(shell: DeviceShell, address: String): List<HealthResult> =
    healthChecks.map { c ->
        runCatching { shell.run(address, c.command).trim() }.fold(
            onSuccess = { out -> HealthResult(c, out, c.expect.judge(out)) },
            onFailure = { e -> HealthResult(c, "", false, e.message ?: e.toString()) },
        )
    }
