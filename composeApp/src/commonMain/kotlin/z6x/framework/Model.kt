package z6x.framework

/** 指令的风险等级，决定界面上的标签颜色。 */
enum class Risk(val label: String) {
    /** 只查询，不改变任何状态。 */
    Read("只读"),
    /** 改变设置或应用状态，可以撤销（如 pm disable-user 可用 pm enable 恢复）。 */
    Change("修改 · 可撤销"),
    /** 删除数据或难以撤销（卸载、清数据、刷写），执行前务必看清。 */
    Danger("高危 · 难以撤销"),
}

/**
 * 指令在哪里执行。同一条命令在 SSH 和 ADB 里权限完全不同，这是整个折腾过程的主线，所以每条都要标清。
 * enum class 可以带构造参数和属性，比 C# 的 enum 更像一个"固定实例的类"。
 */
enum class Host(val label: String, val hint: String) {
    Deck("Deck 终端", "在 Steam Deck 桌面模式的 Konsole 里执行"),
    Adb("ADB shell", "先 adb connect 连上投影仪，再在 adb shell 里执行（uid 2000，shell 身份）"),
    Ssh("SSH · App 权限", "在 SimpleSSHD 的 SSH 会话里执行（uid 10068，普通 App 身份）"),
    Tv("电视界面", "在投影仪上用遥控器操作，不是命令"),
    Windows("Windows", "在 Windows 电脑的 CMD 或 PowerShell 里执行"),
    Remote("其他", "见说明"),
}

/** 模块内容的可信度。 */
enum class Status(val label: String) {
    /** 内容已在实机上逐条核对过。 */
    Verified("✓ 实机核实"),
    /** 部分核对，剩余待查。 */
    Partial("◐ 部分核实"),
    /** 沿用旧内容，尚未核对。 */
    Unverified("⚠ 待核实"),
    /** 提案 / 设想，并未实施。 */
    Proposal("💡 提案"),
}

/**
 * 一条可复制的操作指令。
 * data class 自动生成 equals / hashCode / toString / copy，对应 C# 的 record。
 */
data class Step(
    val title: String,
    val command: String,
    val risk: Risk,
    val host: Host,
    /** 怎么用、参数什么意思。 */
    val note: String = "",
    /** 执行后会发生什么：屏幕上看到什么、系统里改变了什么。 */
    val outcome: String = "",
    /** 从实机上抓下来的真实输出（不是编的"预期输出"）。 */
    val output: String = "",
    /** output 的抓取日期，例如 "2026-10-01"。 */
    val capturedOn: String = "",
    /** 这一步本来就会报错（演示权限不足等），自动试跑时不算问题。 */
    val expectsError: Boolean = false,
    /** 只读但不适合自动试跑（持续运行、需要交互、耗时很长）。 */
    val manual: Boolean = false,
    /** 输出每次都会变（内存、磁盘占用、温度），试跑时不和实测记录对比。 */
    val varies: Boolean = false,
)

/** 段落里的一项：要么是一段文字，要么是一条指令，要么是一张键值表。 */
sealed interface Item {
    data class Text(val markup: String) : Item
    data class Cmd(val step: Step) : Item
    data class Facts(val rows: List<Pair<String, String>>) : Item
}

/** 段落类型决定标题图标与配色。 */
enum class SectionKind(val icon: String) {
    Why("📌"),        // 原理
    Story("📖"),      // 经过：尝试、报错、转折
    Consequence("⚠️"), // 后果与边界
    Steps("💻"),      // 操作步骤
    Verify("🔍"),     // 验证
    Lesson("💡"),     // 经验教训 / 核实案例
}

data class Section(val kind: SectionKind, val title: String, val items: List<Item>)

class Module(
    /** 全局唯一，kebab-case。 */
    val id: String,
    val title: String,
    val keywords: String,
    val overview: String,
    val status: Status,
    /** 核实日期，status 为 Verified / Partial 时填写。 */
    val verifiedOn: String,
    val sections: List<Section>,
    /** 相关模块的 id，显示在页面末尾。 */
    val related: List<String>,
) {
    /** 按出现顺序取出全部指令，供搜索、检查和试跑使用。 */
    val steps: List<Step> get() = sections.flatMap { s -> s.items.filterIsInstance<Item.Cmd>().map { it.step } }
}

data class Category(val name: String, val icon: String, val description: String, val modules: List<Module>)

data class Scope(
    val id: String,
    val icon: String,
    val label: String,
    val tagline: String,
    val categories: List<Category>,
)
