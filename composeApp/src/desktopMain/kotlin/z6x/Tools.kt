package z6x

import z6x.device.DefaultAddress
import z6x.framework.Host
import z6x.framework.Item
import z6x.framework.Module
import z6x.framework.Risk
import z6x.framework.Scope
import z6x.framework.Status
import z6x.framework.Step
import java.util.concurrent.TimeUnit

/** 开发用的命令行工具：./run.sh --check、./run.sh --try-read [模块id...] [-v] */
object Tools {
    private fun all(scopes: List<Scope>) = scopes.flatMap { it.categories }.flatMap { it.modules }

    // 只读步骤里不该出现的修改操作（命令位置：行首或 ; & | 之后）
    private const val CMD = """(?:^|[;&|(\n]\s*)"""
    private val modifying = Regex(
        CMD + """(setprop|rm|mv|cp|ln|chmod|chown|touch|mkdir|dd|reboot|tee)\s""" +
            // kill -l 列出信号、kill -0 只检查权限，都不改变状态
            "|" + CMD + """(kill|pkill|killall)\s+(?!-[l0]\b)""" +
            """|\bpm\s+(uninstall|install|disable|disable-user|enable|clear|grant|revoke|hide|unhide)\b""" +
            """|\bsettings\s+(put|delete)\b|\bam\s+(start|force-stop|kill|broadcast)\b""" +
            """|\bsvc\s+\w+\s+(enable|disable)\b|\bime\s+(enable|disable|set)\b|\binput\s+(keyevent|tap|text)\b""" +
            """|\bwm\s+(size|density)\s+\S|\btar\s+x|\bcurl\b[^|;]*\s-o\s|(?<=\s)>>?(?!&|\s*/dev/null)\s*[~/$\w.]""",
    )
    private val dangerous = Regex("""\bpm\s+(uninstall|clear)\b|\brm\s+-\w*r|\breboot\b|\bdd\s|\bwipe\b|\bformat\b""")
    private val secret = Regex("""(vless|vmess|trojan|ss|hysteria2?)://""", RegexOption.IGNORE_CASE)
    private val errorText = Regex("""not found|No such file|Permission denied|Exception|syntax error|Unknown option|inaccessible""", RegexOption.IGNORE_CASE)

    /** 静态检查，返回退出码（0 = 通过）。 */
    fun check(scopes: List<Scope>): Int {
        val modules = all(scopes)
        val ids = modules.map { it.id }.toSet()
        val problems = mutableListOf<String>()

        for (m in modules) {
            fun p(msg: String) { problems += "[${m.id}] $msg" }
            if (m.overview.isBlank()) p("缺少 overview")
            if (m.status in setOf(Status.Verified, Status.Partial) && m.verifiedOn.isBlank()) p("标了核实但没写日期")
            m.related.filter { it !in ids }.forEach { p("相关模块不存在：$it") }

            val texts = m.sections.flatMap { s -> s.items.filterIsInstance<Item.Text>().map { it.markup } } +
                m.overview + m.steps.flatMap { listOf(it.note, it.outcome) }
            for (t in texts) for (line in t.lines()) {
                if (line.split("**").size % 2 == 0) p("加粗标记 ** 不成对：$line")
                if (line.count { it == '`' } % 2 != 0) p("代码标记 ` 不成对：$line")
            }

            for (s in m.steps) {
                val all = listOf(s.command, s.note, s.outcome, s.output).joinToString("\n")
                if (secret.containsMatchIn(all)) p("「${s.title}」疑似包含代理节点链接，不能写进内容")
                if (s.risk == Risk.Read && modifying.containsMatchIn(s.command)) p("「${s.title}」标为只读，但命令会修改状态")
                if (s.risk != Risk.Danger && dangerous.containsMatchIn(s.command)) p("「${s.title}」包含高危操作，应标为 danger")
                if (s.capturedOn.isEmpty() != s.output.isEmpty()) p("「${s.title}」实测输出和日期要同时填写")
            }
        }

        if (problems.isEmpty()) {
            println("✓ 检查通过：${modules.size} 个模块，${modules.sumOf { it.steps.size }} 条指令")
            return 0
        }
        problems.forEach { println("✗ $it") }
        println("共 ${problems.size} 个问题")
        return 1
    }

    /**
     * 在实机上试跑只读指令：ADB 步骤通过 dadb 发给投影仪，Deck 步骤在本机 bash 执行。
     * 只跑 Risk.Read 且未标 manual 的步骤；SSH、电视界面等其他位置的步骤跳过。
     * 有实测记录的，会对比现在的输出，提示哪里变了。
     */
    fun tryRead(scopes: List<Scope>, args: List<String>): Int {
        val verbose = "-v" in args
        val wanted = args.filter { it != "-v" }.toSet()
        val modules = all(scopes).filter { wanted.isEmpty() || it.id in wanted }
        var failed = 0
        var changed = 0

        for (m in modules) {
            println("━━ ${m.id}  ${m.title}")
            for (s in m.steps) {
                val skip = skipReason(s)
                if (skip != null) { if (verbose) println("  · 跳过「${s.title}」：$skip"); continue }

                val (code, out) = runCatching { execute(s) }.getOrElse { -1 to "执行失败：${it.message}" }
                val bad = (code != 0 || errorText.containsMatchIn(out)) && !s.expectsError
                val diff = s.output.isNotEmpty() && !s.varies && normalize(out) != normalize(s.output)
                when {
                    bad -> { failed++; println("  ✗ 「${s.title}」退出码 $code") }
                    diff -> { changed++; println("  △ 「${s.title}」输出与 ${s.capturedOn} 的实测记录不同") }
                    else -> println("  ✓ 「${s.title}」")
                }
                if (bad || diff || verbose) {
                    println(out.prependIndent("      │ "))
                    if (diff) println(s.output.prependIndent("      ┆ "))
                }
            }
        }
        println("\n失败 $failed，输出有变化 $changed（│ 现在的输出，┆ 记录的输出）")
        return if (failed == 0) 0 else 1
    }

    private fun skipReason(s: Step): String? = when {
        s.risk != Risk.Read -> "不是只读步骤"
        s.manual -> "标记为手动执行"
        s.host !in setOf(Host.Adb, Host.Deck, Host.Ssh) -> "执行位置是 ${s.host.label}"
        Regex("""<[^<>\s]+>""").containsMatchIn(s.command) -> "含占位符"
        else -> null
    }

    private fun execute(s: Step): Pair<Int, String> = when (s.host) {
        Host.Adb -> runBlockingShell(DefaultAddress, s.command)
        // SSH 步骤：用 ~/.ssh/config 里的 z6x 别名免密登录（见「SSH 免密登录 SimpleSSHD」）；BatchMode 防止卡在密码提示
        Host.Ssh -> process(listOf("ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=5", "z6x", s.command))
        else -> process(listOf("bash", "-c", s.command))
    }

    private fun process(cmd: List<String>): Pair<Int, String> {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        if (!p.waitFor(20, TimeUnit.SECONDS)) { p.destroyForcibly(); return -1 to "超时（20 秒）" }
        return p.exitValue() to p.inputStream.bufferedReader().readText().trim()
    }

    // 只比较内容，忽略每行首尾空白（输出的缩进对比较没有意义）
    private fun normalize(s: String) = s.lines().joinToString("\n") { it.trim() }.trim()
}
