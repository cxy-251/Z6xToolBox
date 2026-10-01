package z6x

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** 桌面端实现：用 dadb 直接走 ADB 协议，不依赖 adb 可执行文件。复用 ~/.android/adbkey 作为身份。 */
class DadbShell : DeviceShell {
    override suspend fun run(address: String, command: String): String =
        // dadb 是阻塞 IO，切到 IO 线程池执行
        withContext(Dispatchers.IO) {
            val (host, port) = address.split(":").let { it[0] to (it.getOrNull(1)?.toInt() ?: 5555) }
            // use { } 结束时自动关闭连接，相当于 C# 的 using
            Dadb.create(host, port, AdbKeyPair.readDefault()).use { it.shell(command).allOutput.trim() }
        }
}

fun main(args: Array<String>) {
    // 命令行模式：./gradlew :composeApp:run --args="--probe [地址]"，不开窗口，直接打印设备信息
    if (args.firstOrNull() == "--probe") {
        val address = args.getOrElse(1) { "192.168.0.109:5555" }
        runBlocking {
            for (p in probes) println("%-14s %s".format(p.label, DadbShell().run(address, p.command)))
        }
        return
    }
    gui()
}

private fun gui() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Z6xToolBox",
        state = rememberWindowState(width = 900.dp, height = 600.dp),
    ) {
        App(DadbShell())
    }
}
