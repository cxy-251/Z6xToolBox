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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import z6x.device.HealthResult
import z6x.device.HubControl
import z6x.device.HubHealth
import z6x.device.hostOf
import z6x.device.knownDevices
import z6x.device.Level
import z6x.device.probes
import z6x.device.runHealthChecks

/** 设备面板：连接投影仪，执行一组只读命令，显示结果和所用命令。 */
@Composable
fun DevicePanel(shell: DeviceShell, hub: HubControl, onOpen: (String) -> Unit) {
    // remember：在重组之间保留的状态；离开面板再回来会重新创建
    var address by remember { mutableStateOf(DefaultAddress) }
    var results by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var health by remember { mutableStateOf<List<HealthResult>>(emptyList()) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(32.dp).widthIn(max = 900.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("📡 设备", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.TextStrong)
        Text(
            "通过 ADB 协议直接连接设备，只执行下面列出的只读命令。修改类操作不会在这里执行，请在模块页复制命令后自己运行。",
            fontSize = 13.sp, color = Palette.TextMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("设备：", fontSize = 13.sp, color = Palette.TextMuted)
            for (d in knownDevices) {
                val selected = hostOf(d.address) == hostOf(address)
                TextButton(onClick = { if (!selected) { address = d.address; results = emptyList(); health = emptyList() } }) {
                    Text(if (selected) "● ${d.name}" else d.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        knownDevices.firstOrNull { hostOf(it.address) == hostOf(address) }?.let {
            Text(it.note, fontSize = 12.sp, color = Palette.TextMuted)
        }
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
            Text("CPU 温度的单位为千分之一摄氏度，54000 即 54 ℃。", fontSize = 12.sp, color = Palette.TextMuted)
        }

        HubSection(hub, address, onOpen)

        Text("🩺 一键体检", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.TextStrong, modifier = Modifier.padding(top = 18.dp))
        Text(
            "针对投影仪：对照 2026-10-01 核对过的正确状态逐项检查 ADB、定制、桌面、hub、温度和存储，全部为只读命令。异常项可点击「查看」，跳转到说明恢复方法的页面。",
            fontSize = 13.sp, color = Palette.TextMuted,
        )
        Button(
            enabled = !checking,
            onClick = {
                scope.launch {
                    checking = true
                    health = runHealthChecks(shell, address)
                    checking = false
                }
            },
        ) { Text(if (checking) "检查中…" else "开始体检") }
        if (health.isNotEmpty()) {
            val bad = health.count { !it.ok && it.check.level == Level.Must }
            val info = health.count { !it.ok && it.check.level == Level.Info }
            Text(
                if (bad == 0) "✓ 必检项全部正常" + (if (info > 0) "，另有 $info 条提示" else "") else "✗ $bad 项异常" + (if (info > 0) "，$info 条提示" else ""),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (bad == 0) Palette.Green else Palette.Red,
            )
            Column(
                Modifier.fillMaxWidth().background(Palette.Bg1, RoundedCornerShape(8.dp))
                    .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (r in health) HealthRow(r, onOpen)
            }
        }
    }
}

@Composable
private fun HealthRow(r: HealthResult, onOpen: (String) -> Unit) {
    val (icon, color) = when {
        r.ok -> "✓" to Palette.Green
        r.check.level == Level.Info -> "⚠" to Palette.Amber
        else -> "✗" to Palette.Red
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, Modifier.width(24.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            Text(r.check.title, Modifier.weight(1f), fontSize = 13.sp, color = Palette.TextBody)
            Text(
                r.error?.let { "出错：$it" } ?: r.output.ifEmpty { "（无输出）" },
                fontSize = 12.sp, fontFamily = MonoFont, color = color, maxLines = 1,
            )
            if (!r.ok) TextButton(onClick = { onOpen(r.check.module) }) { Text("查看", fontSize = 12.sp) }
        }
        if (!r.ok) {
            Text(
                "期望 ${r.check.expect.text}。${r.check.hint}",
                Modifier.padding(start = 24.dp), fontSize = 12.sp, color = Palette.TextMuted,
            )
        }
    }
}

/** z6x-hub 区域：显示运行状态，提供部署和打开网页的入口。 */
@Composable
private fun HubSection(hub: HubControl, address: String, onOpen: (String) -> Unit) {
    var status by remember { mutableStateOf<HubHealth?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var querying by remember { mutableStateOf(false) }
    var deploying by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        querying = true
        runCatching { hub.health(hostOf(address)) }
            .onSuccess { status = it; error = null }
            .onFailure { status = null; error = it.message ?: it::class.simpleName }
        querying = false
    }
    // 进入面板或修改地址后自动查询一次
    LaunchedEffect(address) { refresh() }

    Text("🛰 z6x-hub", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.TextStrong, modifier = Modifier.padding(top = 18.dp))
    Text(
        "运行在投影仪和手机上的常驻服务，同一个程序，各设备按 hub/devices/<设备名>.yaml 启用不同模块。部署会编译 hub/ 下的源码，推送到当前设备并重启服务。",
        fontSize = 13.sp, color = Palette.TextMuted,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(enabled = !querying && !deploying, onClick = { scope.launch { refresh() } }) { Text(if (querying) "查询中…" else "刷新状态") }
        Button(
            enabled = hub.canDeploy && !deploying,
            onClick = {
                scope.launch {
                    deploying = true
                    log = emptyList()
                    // 每收到一行输出就追加到列表；只保留最后 200 行
                    val code = runCatching { hub.deploy(address) { line -> log = (log + line).takeLast(200) } }
                        .getOrElse { log = log + "启动部署脚本失败：${it.message}"; -1 }
                    log = log + if (code == 0) "✓ 部署完成" else "✗ 部署失败（退出码 $code）"
                    deploying = false
                    refresh()
                }
            },
        ) { Text(if (deploying) "部署中…" else if (status == null) "部署并启动" else "重新部署") }
        TextButton(enabled = status != null, onClick = { hub.openInBrowser(hostOf(address)) }) { Text("在浏览器中打开") }
        TextButton(onClick = { onOpen("spec-hub") }) { Text("说明") }
    }
    if (!hub.canDeploy) Text("当前平台无法部署：需要项目源码中的 hub/deploy.sh 和 Go 工具链。", fontSize = 12.sp, color = Palette.TextMuted)

    val s = status
    when {
        s != null -> Column(
            Modifier.fillMaxWidth().background(Palette.Bg1, RoundedCornerShape(8.dp))
                .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val failed = s.modules.count { it.state != "running" }
            Text(
                (if (failed == 0) "✓ 运行中" else "✗ $failed 个模块异常") + "　版本 ${s.version}　已运行 ${formatDuration(s.uptimeSec)}",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (failed == 0) Palette.Green else Palette.Red,
            )
            for (m in s.modules) {
                Row {
                    Text(if (m.state == "running") "✓" else "✗", Modifier.width(24.dp), fontSize = 13.sp,
                        color = if (m.state == "running") Palette.Green else Palette.Red)
                    Text(m.name, Modifier.width(100.dp), fontSize = 13.sp, fontFamily = MonoFont, color = Palette.TextBody)
                    Text(m.error ?: m.state, fontSize = 12.sp, color = Palette.TextMuted)
                }
            }
        }
        error != null && !querying -> Text("未运行或无法连接（$error）", fontSize = 13.sp, color = Palette.Amber)
    }
    if (log.isNotEmpty()) {
        SelectionContainer {
            Text(
                log.joinToString("\n"),
                Modifier.fillMaxWidth().background(Palette.Bg1, RoundedCornerShape(8.dp))
                    .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(12.dp),
                fontSize = 12.sp, fontFamily = MonoFont, color = Palette.TextBody,
            )
        }
    }
}

/** 把秒数格式化为「x 天 x 小时」「x 小时 x 分」等。 */
private fun formatDuration(sec: Long): String = when {
    sec >= 86400 -> "${sec / 86400} 天 ${sec % 86400 / 3600} 小时"
    sec >= 3600 -> "${sec / 3600} 小时 ${sec % 3600 / 60} 分"
    sec >= 60 -> "${sec / 60} 分"
    else -> "$sec 秒"
}
