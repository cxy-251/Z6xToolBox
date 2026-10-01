package z6x.framework

/*
 * 写内容用的 DSL。用法：
 *
 *   val X = module("force-adb", "强开网络 ADB") {
 *       keywords = "setprop · ctl.start · adbd"
 *       overview = "……"
 *       verified("2026-10-01")
 *       story("经过") {
 *           text("……")
 *           read("查看属性", "getprop ro.adb.secure", Host.Ssh) {
 *               output = "0"
 *           }
 *       }
 *   }
 *
 * 原理：module(...) { ... } 的最后一个参数是"带接收者的 lambda"（ModuleBuilder.() -> Unit），
 * 花括号里的代码以 ModuleBuilder 为 this 执行，所以能直接写 keywords = ...、story(...)。
 * @DslMarker 防止在内层 lambda 里误调用外层 builder 的方法。
 */

@DslMarker
annotation class ContentDsl

fun module(id: String, title: String, block: ModuleBuilder.() -> Unit): Module =
    ModuleBuilder(id, title).apply(block).build()

@ContentDsl
class ModuleBuilder(private val id: String, private val title: String) {
    var keywords = ""
    var overview = ""
    private var status = Status.Unverified
    private var verifiedOn = ""
    private val sections = mutableListOf<Section>()
    private val related = mutableListOf<String>()

    fun verified(date: String) { status = Status.Verified; verifiedOn = date }
    fun partial(date: String) { status = Status.Partial; verifiedOn = date }
    fun proposal() { status = Status.Proposal }

    fun why(title: String = "原理：为什么是这样", block: SectionBuilder.() -> Unit) = section(SectionKind.Why, title, block)
    fun story(title: String = "经过", block: SectionBuilder.() -> Unit) = section(SectionKind.Story, title, block)
    fun consequences(title: String = "后果与边界", block: SectionBuilder.() -> Unit) = section(SectionKind.Consequence, title, block)
    fun steps(title: String = "操作步骤", block: SectionBuilder.() -> Unit) = section(SectionKind.Steps, title, block)
    fun verify(title: String = "如何验证", block: SectionBuilder.() -> Unit) = section(SectionKind.Verify, title, block)
    fun lesson(title: String = "经验", block: SectionBuilder.() -> Unit) = section(SectionKind.Lesson, title, block)

    // vararg 相当于 C# 的 params
    fun related(vararg ids: String) { related += ids }

    private fun section(kind: SectionKind, title: String, block: SectionBuilder.() -> Unit) {
        sections += Section(kind, title, SectionBuilder().apply(block).items)
    }

    fun build() = Module(id, title, keywords, overview.trimIndent(), status, verifiedOn, sections.toList(), related.toList())
}

@ContentDsl
class SectionBuilder {
    internal val items = mutableListOf<Item>()

    /** 一段文字。支持 **加粗**、`代码`；行首 "• " / "- " 按列表缩进。多行字符串会自动去掉公共缩进。 */
    fun text(markup: String) { items += Item.Text(markup.trimIndent()) }

    /** 键值表，例如 facts("芯片" to "Hi3751V660", "内核" to "5.10.43")。 */
    fun facts(vararg rows: Pair<String, String>) { items += Item.Facts(rows.toList()) }

    fun read(title: String, command: String, host: Host, block: StepBuilder.() -> Unit = {}) = step(title, command, Risk.Read, host, block)
    fun change(title: String, command: String, host: Host, block: StepBuilder.() -> Unit = {}) = step(title, command, Risk.Change, host, block)
    fun danger(title: String, command: String, host: Host, block: StepBuilder.() -> Unit = {}) = step(title, command, Risk.Danger, host, block)

    private fun step(title: String, command: String, risk: Risk, host: Host, block: StepBuilder.() -> Unit) {
        items += Item.Cmd(StepBuilder().apply(block).build(title, command.trimIndent(), risk, host))
    }
}

@ContentDsl
class StepBuilder {
    var note = ""
    var outcome = ""
    var output = ""
    var capturedOn = ""
    var expectsError = false
    var manual = false

    /** 记录实测输出：captured("2026-10-01", "...")。 */
    fun captured(date: String, text: String) {
        capturedOn = date
        // 多行原始字符串 """...""" 以换行开头，去掉公共缩进；单行保持原样（输出里的前导空格也是数据）
        output = if (text.startsWith("\n")) text.trimIndent() else text
    }

    internal fun build(title: String, command: String, risk: Risk, host: Host) =
        Step(title, command, risk, host, note.trimIndent(), outcome.trimIndent(), output, capturedOn, expectsError, manual)
}
