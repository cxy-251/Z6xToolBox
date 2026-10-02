package z6x

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import z6x.content.Content
import z6x.device.DefaultAddress
import z6x.device.hostOf
import z6x.device.DeviceShell
import z6x.device.Level
import z6x.device.probes
import z6x.device.runHealthChecks
import z6x.ui.AppState
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** 桌面端实现：用 dadb 直接走 ADB 协议，不依赖 adb 可执行文件。复用 ~/.android/adbkey 作为身份。 */
class DadbShell : DeviceShell {
    override suspend fun run(address: String, command: String): String =
        // dadb 是阻塞 IO，切到 IO 线程池执行
        withContext(Dispatchers.IO) { runBlockingShell(address, command).second }
}

/** 返回 (退出码, 输出)。供界面和命令行工具共用。 */
fun runBlockingShell(address: String, command: String): Pair<Int, String> {
    val (host, port) = address.split(":").let { it[0] to (it.getOrNull(1)?.toInt() ?: 5555) }
    // use { } 结束时自动关闭连接，相当于 C# 的 using
    return Dadb.create(host, port, AdbKeyPair.readDefault()).use {
        val r = it.shell(command)
        r.exitCode to r.allOutput.trim()
    }
}

/** 用 AWT 系统剪贴板复制文本。 */
private fun copyToClipboard(text: String) =
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        // ./run.sh --probe [地址]：不开窗口，直接打印设备信息
        "--probe" -> runBlocking {
            val address = args.getOrElse(1) { DefaultAddress }
            for (p in probes) println("${p.label.padEnd(8, '　')} ${DadbShell().run(address, p.command)}")
        }
        // ./run.sh --health [地址]：命令行版一键体检
        "--health" -> runBlocking {
            val results = runHealthChecks(DadbShell(), args.getOrElse(1) { DefaultAddress })
            for (r in results) {
                val mark = if (r.ok) "✓" else if (r.check.level == Level.Info) "⚠" else "✗"
                println("$mark ${r.check.title}：${r.error ?: r.output.ifEmpty { "（无输出）" }}" + if (r.ok) "" else "（期望 ${r.check.expect.text}）")
            }
            val bad = results.count { !it.ok && it.check.level == Level.Must }
            kotlin.system.exitProcess(if (bad == 0) 0 else 1)
        }
        // ./run.sh --hub [地址]：查询 z6x-hub 状态（与「📡 设备」页使用同一段代码）
        "--hub" -> runBlocking {
            val host = hostOf(args.getOrElse(1) { DefaultAddress })
            val h = runCatching { DesktopHub().health(host) }.getOrElse {
                println("✗ z6x-hub 未运行或无法连接：${it.message}"); kotlin.system.exitProcess(1)
            }
            println("z6x-hub ${h.version}，已运行 ${h.uptimeSec} 秒")
            for (m in h.modules) println("  ${if (m.state == "running") "✓" else "✗"} ${m.name.padEnd(8)} ${m.error ?: m.state}")
            kotlin.system.exitProcess(if (h.modules.all { it.state == "running" }) 0 else 1)
        }
        // ./run.sh --hub-deploy [地址]：编译并部署 z6x-hub（与「📡 设备」页的部署按钮使用同一段代码）
        "--hub-deploy" -> runBlocking {
            val code = DesktopHub().deploy(args.getOrElse(1) { DefaultAddress }) { println(it) }
            kotlin.system.exitProcess(code)
        }
        "--check" -> kotlin.system.exitProcess(Tools.check(Content.scopes))
        "--try-read" -> kotlin.system.exitProcess(Tools.tryRead(Content.scopes, args.drop(1)))
        // ./run.sh --device：启动后直接打开「📡 设备」页
        "--device" -> gui(openDevice = true)
        else -> gui()
    }
}

private fun gui(openDevice: Boolean = false) = application {
    val state = remember { AppState(Content.scopes).also { it.showDevice = openDevice } }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Z6xToolBox · 极米 Z6X Pro 实践手册",
        state = rememberWindowState(width = 1280.dp, height = 800.dp, position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center)),
    ) {
        App(state, DadbShell(), DesktopHub(), ::copyToClipboard)
    }
}
