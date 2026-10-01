package z6x.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import z6x.device.DeviceShell
import z6x.framework.Module

private val Rounded = RoundedCornerShape(6.dp)

@Composable
fun AppShell(state: AppState, shell: DeviceShell, onCopy: (String) -> Unit) {
    val copy: (String) -> Unit = { onCopy(it); state.toast = "已复制到剪贴板" }

    Column(Modifier.fillMaxSize().background(Palette.Bg0)) {
        TopBar(state)
        Row(Modifier.weight(1f)) {
            Sidebar(state, Modifier.width(320.dp).fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val located = state.current?.let { state.index[it.id] }
                when {
                    state.showDevice -> DevicePanel(shell, state::open)
                    located != null -> ModuleView(
                        module = located.module,
                        breadcrumb = located.breadcrumb,
                        relatedModules = located.module.related.mapNotNull { id ->
                            state.index[id]?.let { it.module to "${it.scope.label} › ${it.category.name}" }
                        },
                        onCopy = copy,
                        onOpen = state::open,  // 函数引用，相当于 C# 的方法组
                    )
                    else -> Text("这个专区还没有内容。", Modifier.padding(32.dp), color = Palette.TextMuted)
                }
                Toast(state)
            }
        }
    }
}

@Composable
private fun TopBar(state: AppState) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Bg1).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(Color(0xFF238636), Rounded), contentAlignment = Alignment.Center) {
            Text("📽", fontSize = 18.sp)
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text("Z6xToolBox", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Palette.TextStrong)
            Text("极米 Z6X Pro 折腾手册", fontSize = 11.sp, color = Palette.TextMuted)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.background(Palette.Bg3, RoundedCornerShape(8.dp)).border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (s in state.scopes) {
                Tab("${s.icon} ${s.label}", active = s == state.scope && !state.showDevice) { state.selectScope(s) }
            }
        }
        Spacer(Modifier.weight(1f))
        Tab("📡 设备", active = state.showDevice) { state.showDevice = true }
        Text("${state.moduleCount} 个模块", Modifier.padding(start = 12.dp), fontSize = 12.sp, color = Palette.TextMuted)
    }
    Box(Modifier.fillMaxWidth().size(1.dp).background(Palette.Line))
}

@Composable
private fun Tab(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) Palette.TextStrong else Palette.TextMuted,
        modifier = Modifier
            .background(if (active) Palette.AccentSoft else Color.Transparent, Rounded)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Sidebar(state: AppState, modifier: Modifier) {
    Column(modifier.background(Palette.Bg1)) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.scope.tagline, fontSize = 12.sp, color = Palette.TextMuted)
        }
        LazyColumn(Modifier.padding(start = 8.dp, end = 10.dp)) {
            for (c in state.scope.categories) {
                    item {
                        Text(
                            "${c.icon} ${c.name}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.TextStrong,
                            modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(c.modules) { m ->
                        NavItem(m, m.keywords, selected = m == state.current && !state.showDevice) { state.open(m.id) }
                    }
            }
        }
    }
}

@Composable
private fun NavItem(module: Module, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Palette.AccentSoft else Color.Transparent, Rounded)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(module.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 11.sp, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(module.status.label.take(1), Modifier.padding(start = 6.dp), fontSize = 12.sp, color = module.status.color)
    }
}

@Composable
private fun Toast(state: AppState) {
    if (state.toast.isEmpty()) return
    // LaunchedEffect：toast 内容变化时启动协程，2 秒后清空
    LaunchedEffect(state.toast) {
        delay(2000)
        state.toast = ""
    }
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopEnd) {
        Text(
            state.toast, fontSize = 12.sp, color = Palette.Accent,
            modifier = Modifier.background(Palette.Bg3, Rounded).border(1.dp, Palette.Line, Rounded).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
