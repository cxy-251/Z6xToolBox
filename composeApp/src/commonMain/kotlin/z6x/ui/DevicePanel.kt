package z6x.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import z6x.device.DefaultAddress
import z6x.device.DeviceShell
import z6x.device.probes

/** 设备面板：连接投影仪，执行一组只读命令，显示结果和所用命令。 */
@Composable
fun DevicePanel(shell: DeviceShell) {
    // remember：在重组之间保留的状态；离开面板再回来会重新创建
    var address by remember { mutableStateOf(DefaultAddress) }
    var results by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(32.dp).widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("📡 设备", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.TextStrong)
        Text(
            "通过 ADB 协议直接连接投影仪，只执行下面列出的只读命令。修改类操作不会在这里执行，请在模块页复制命令后自己运行。",
            fontSize = 13.sp, color = Palette.TextMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(address, { address = it }, label = { Text("ADB 地址") }, singleLine = true)
            Button(
                enabled = !busy,
                onClick = {
                    // launch 启动协程：网络操作在后台进行，界面不会卡住
                    scope.launch {
                        busy = true
                        results = probes.map { p ->
                            val value = runCatching { shell.run(address, p.command) }.getOrElse { "失败：${it.message}" }
                            Triple(p.label, value, p.command)
                        }
                        busy = false
                    }
                },
            ) { Text(if (busy) "读取中…" else "读取设备信息") }
        }
        if (results.isNotEmpty()) {
            SelectionContainer {
                Column(
                    Modifier.fillMaxWidth().background(Palette.Bg1, RoundedCornerShape(8.dp))
                        .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for ((label, value, command) in results) {
                        Row {
                            Text(label, Modifier.width(100.dp), fontSize = 13.sp, color = Palette.Accent)
                            Text(value, Modifier.weight(1f), fontSize = 13.sp, fontFamily = MonoFont, color = Palette.TextBody)
                            Text(command, fontSize = 11.sp, fontFamily = MonoFont, color = Palette.TextMuted)
                        }
                    }
                }
            }
            Text("CPU 温度单位是千分之一摄氏度，54000 即 54 ℃。", fontSize = 12.sp, color = Palette.TextMuted)
        }
    }
}
