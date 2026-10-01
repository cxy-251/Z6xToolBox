package z6x.device

/** 对设备执行 shell 命令的能力。commonMain 只定义接口，桌面端（dadb）和以后的安卓端各自实现。 */
interface DeviceShell {
    suspend fun run(address: String, command: String): String
}

const val DefaultAddress = "192.168.0.109:5555"

/** 一条要读取的设备信息：显示名 + 只读命令。 */
data class Probe(val label: String, val command: String)

/** 设备面板显示的基本信息。每一条都在实机上核对过，来源见「学会查设备」专区。 */
val probes = listOf(
    Probe("型号", "getprop xgimi.bt.name"),
    Probe("内部型号", "getprop ro.xgimi.modelname"),
    Probe("芯片平台", "getprop ro.board.platform"),
    Probe("Android", "getprop ro.build.version.release"),
    Probe("内核", "uname -r"),
    Probe("SELinux", "getenforce"),
    Probe("当前身份", "id -un"),
    Probe("可用内存", "grep MemAvailable /proc/meminfo"),
    Probe("CPU 温度", "cat /sys/class/thermal/thermal_zone0/temp"),
)
