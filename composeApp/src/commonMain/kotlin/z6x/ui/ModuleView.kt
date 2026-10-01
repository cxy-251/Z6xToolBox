package z6x.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import z6x.framework.Host
import z6x.framework.Item
import z6x.framework.Module
import z6x.framework.Section
import z6x.framework.Status
import z6x.framework.Step

private val Rounded = RoundedCornerShape(8.dp)

/** 小标签。 */
@Composable
fun Chip(text: String, color: Color = Palette.TextMuted) {
    Text(
        text,
        fontSize = 11.sp,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * 一个模块的完整页面。
 * key(module.id) 让切换模块时整页重建，滚动位置也随之回到顶部。
 */
@Composable
fun ModuleView(
    module: Module,
    breadcrumb: String,
    relatedModules: List<Pair<Module, String>>,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
) = key(module.id) {
    var number = 0
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 28.dp, top = 22.dp, bottom = 32.dp).widthIn(max = 1100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(module, breadcrumb)
        for (section in module.sections) {
            SectionCard(section) { step ->
                number++
                StepCard(step, number, onCopy)
            }
        }
        if (relatedModules.isNotEmpty()) RelatedCard(relatedModules, onOpen)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(module: Module, breadcrumb: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(breadcrumb, fontSize = 12.sp, color = Palette.TextMuted)
            val status = if (module.verifiedOn.isNotEmpty()) "${module.status.label} ${module.verifiedOn}" else module.status.label
            Chip(status, module.status.color)
        }
        Text(module.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.TextStrong)
        if (module.keywords.isNotEmpty()) Text(module.keywords, fontSize = 12.sp, color = Palette.Accent, fontFamily = MonoFont)
        if (module.overview.isNotEmpty()) Markup(module.overview, Palette.TextMuted, Modifier.padding(top = 4.dp))
        if (module.status == Status.Unverified) {
            Text(
                "这一页沿用旧版内容，还没有在实机上核对，命令和输出可能有误。",
                fontSize = 12.sp, color = Palette.Amber,
            )
        }
    }
}

@Composable
private fun SectionCard(section: Section, stepContent: @Composable (Step) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Palette.Bg1, Rounded).border(1.dp, Palette.Line, Rounded).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${section.kind.icon} ${section.title}",
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = section.kind.color,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        for (item in section.items) {
            // when 配合 sealed interface：编译器知道只有这三种情况，漏写会报错
            when (item) {
                is Item.Text -> Markup(item.markup)
                is Item.Cmd -> stepContent(item.step)
                is Item.Facts -> FactsTable(item.rows)
            }
        }
    }
}

@Composable
private fun FactsTable(rows: List<Pair<String, String>>) {
    SelectionContainer {
        Column(Modifier.fillMaxWidth().background(Palette.Bg2, Rounded).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((k, v) in rows) {
                Row {
                    Text(k, Modifier.widthIn(min = 120.dp).padding(end = 16.dp), fontSize = 13.sp, color = Palette.TextMuted)
                    Text(inlineMarkup(v), fontSize = 13.sp, color = Palette.TextBody)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepCard(step: Step, number: Int, onCopy: (String) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(20.dp).background(Palette.Bg3, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("$number", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Palette.TextBody)
            }
            Text(step.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextStrong)
            Chip(step.risk.label, step.risk.color)
            Chip(step.host.label, if (step.host == Host.Deck) Palette.TextMuted else Palette.Purple)
            if (step.expectsError) Chip("演示报错", Palette.Red)
        }

        if (step.command.isNotEmpty()) {
            val codeColor = if (step.risk == z6x.framework.Risk.Danger) Palette.Red else Palette.Line
            Row(
                Modifier.fillMaxWidth().background(Palette.Bg0, Rounded).border(1.dp, codeColor, Rounded).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionContainer(Modifier.weight(1f)) {
                    Text(step.command, fontFamily = MonoFont, fontSize = 13.sp, color = Palette.CodeText, lineHeight = 20.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onCopy(step.command) }) { Text("复制", fontSize = 12.sp) }
                    if (step.host == Host.Adb && !step.command.contains('\n')) {
                        // 在 Deck 终端直接执行：adb shell '命令'
                        TextButton(onClick = { onCopy(adbOneLiner(step.command)) }) { Text("复制为 adb 单行", fontSize = 11.sp) }
                    }
                }
            }
        }

        if (step.note.isNotEmpty()) Markup(step.note, Palette.TextMuted)

        if (step.outcome.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(Palette.Green.copy(alpha = 0.07f), Rounded).padding(10.dp)) {
                Text("▶ 执行后", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.Green, modifier = Modifier.padding(end = 10.dp, top = 2.dp))
                Markup(step.outcome)
            }
        }

        if (step.output.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Palette.Bg2, Rounded).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val label = if (step.capturedOn.isNotEmpty()) "实测输出 · ${step.capturedOn}" else "实测输出"
                Text(label, fontSize = 11.sp, color = Palette.TextMuted)
                SelectionContainer {
                    Text(step.output, fontFamily = MonoFont, fontSize = 12.sp, color = Palette.TextBody, lineHeight = 18.sp)
                }
            }
        }
    }
}

/** 把设备端命令包成 Deck 上可直接执行的一行：单引号内的单引号要转义成 '\'' */
fun adbOneLiner(command: String) = "adb shell '" + command.replace("'", "'\\''") + "'"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedCard(related: List<Pair<Module, String>>, onOpen: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Palette.Bg1, Rounded).border(1.dp, Palette.Line, Rounded).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🔗 相关模块", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Accent)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((m, location) in related) {
                TextButton(onClick = { onOpen(m.id) }, modifier = Modifier.background(Palette.Bg2, Rounded)) {
                    Column {
                        Text(m.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextBody)
                        Text(location, fontSize = 11.sp, color = Palette.TextMuted)
                    }
                }
            }
        }
    }
}
