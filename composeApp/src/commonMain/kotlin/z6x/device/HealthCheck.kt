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
        "ADB 的身份不是 shell，大部分定制命令将无法执行。"),
    HealthCheck("SELinux 宽容模式", "getenforce", Expect.Equals("Permissive"), Level.Must, "force-adb",
        "SELinux 变为 Enforcing 通常是系统升级所致。届时通过 SSH 启动 ADB 的方法将失效，切勿停止 adbd。"),
    HealthCheck("ADB 开机自启", "getprop persist.sys.usb.config", Expect.Equals("adb"), Level.Must, "adb-autostart",
        "下次重启后 ADB 将不会自动运行。从 SSH 执行 setprop ctl.start adbd 即可恢复。"),
    HealthCheck("停用的预装组件", "pm list packages -d | wc -l", Expect.Equals("29"), Level.Must, "debloat-list",
        "停用数量不符，定制可能已被系统升级还原。请运行一键精简脚本。"),
    HealthCheck("官方桌面和影视推荐已卸载", "pm list packages | grep -cE '^package:com.xgimi.(home|stream.video)${'$'}'", Expect.Equals("0"), Level.Must, "projectivy-launcher",
        "官方桌面已恢复。请运行一键精简脚本。"),
    HealthCheck("桌面是 Projectivy", "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1", Expect.Contains("com.spocky.projengmenu"), Level.Must, "projectivy-launcher",
        "按 Home 键将不会回到 Projectivy。"),
    HealthCheck("输入法是搜狗", "settings get secure default_input_method", Expect.Contains("sogou"), Level.Must, "input-method",
        "更换输入法后，遥控器操作可能出现迟滞。"),
    HealthCheck("Clash 的 VPN 在运行", "dumpsys vpn_management 2>/dev/null | grep -c clash", Expect.Between(1, 99, "≥ 1"), Level.Info, "clash-proxy",
        "代理未开启。如有需要，请在电视上打开 Clash Meta 并启动。"),
    HealthCheck("z6x-hub 在运行", "pidof z6x-hub", Expect.NotEmpty, Level.Info, "spec-hub",
        "hub 未运行。投影仪重启后需要重新部署：在本页「z6x-hub」区域点击「部署并启动」，或在 Deck 上执行 ./hub/deploy.sh。"),
    HealthCheck("CPU 温度", "cat /sys/class/thermal/thermal_zone0/temp", Expect.Between(0, 80_000, "< 80℃（单位千分之一度）"), Level.Must, "proc-metrics",
        "温度偏高，请检查进风口和出风口是否被遮挡。"),
    HealthCheck("可用内存", "grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2", Expect.Between(500_000, Long.MAX_VALUE, "> 500MB（单位 KB）"), Level.Must, "process-memory",
        "可用内存很低，请查看占用内存较多的应用。"),
    HealthCheck("/data 剩余空间", "df /data | tail -1 | tr -s ' ' | cut -d' ' -f4", Expect.Between(2_000_000, Long.MAX_VALUE, "> 2GB（单位 KB）"), Level.Must, "storage-partitions",
        "存储空间即将用尽。"),
    HealthCheck("停用的常驻组件仍在运行（已知问题）", "for p in com.xgimi.xgimihilink com.xgimi.mateservice com.xgimi.soundermodeservice; do pidof ${'$'}p >/dev/null && echo -n x; done", Expect.Equals(""), Level.Info, "debloat-list",
        "每个 x 代表一个停用后仍在运行的 PERSISTENT 组件。disable-user 无法阻止它们运行，属于已知问题。"),
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
