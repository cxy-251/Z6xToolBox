package z6x

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 对设备执行 shell 命令的能力。commonMain 只定义接口，桌面端和安卓端各自实现。 */
interface DeviceShell {
    suspend fun run(address: String, command: String): String
}

/** 一条要读取的设备信息：显示名 + 只读命令。 */
data class Probe(val label: String, val command: String)

val probes = listOf(
    Probe("型号", "getprop xgimi.bt.name"),
    Probe("芯片平台", "getprop ro.board.platform"),
    Probe("Android 版本", "getprop ro.build.version.release"),
    Probe("内核", "uname -r"),
    Probe("SELinux", "getenforce"),
    Probe("当前身份", "id -un"),
)

// @Composable 函数描述"界面长什么样"；状态变了，Compose 会自动重新调用它（重组）
@Composable
fun App(shell: DeviceShell) {
    // remember + mutableStateOf：在重组之间保留的状态，修改它会触发界面刷新
    var address by remember { mutableStateOf("192.168.0.109:5555") }
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Z6xToolBox · 技术验证", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("ADB 地址") },
                        singleLine = true,
                    )
                    Button(
                        enabled = !busy,
                        onClick = {
                            // launch 启动协程：网络操作在后台跑，不卡界面
                            scope.launch {
                                busy = true
                                results = probes.map { p ->
                                    p.label to runCatching { shell.run(address, p.command) }
                                        .getOrElse { "失败：${it.message}" }
                                }
                                busy = false
                            }
                        },
                    ) { Text(if (busy) "读取中…" else "读取设备信息") }
                }
                for ((label, value) in results) {
                    Row {
                        Text(label, Modifier.width(120.dp), color = MaterialTheme.colorScheme.primary)
                        Text(value, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
