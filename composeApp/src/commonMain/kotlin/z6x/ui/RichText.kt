package z6x.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 把一行里的 **加粗** 和 `代码` 转成带样式的 AnnotatedString。 */
fun inlineMarkup(line: String): AnnotatedString = buildAnnotatedString {
    var bold = false
    var code = false
    var i = 0
    val buf = StringBuilder()

    fun flush() {
        if (buf.isEmpty()) return
        val style = when {
            code -> SpanStyle(fontFamily = MonoFont, color = Palette.CodeText, background = Palette.CodeBg)
            bold -> SpanStyle(fontWeight = FontWeight.Bold, color = Palette.TextStrong)
            else -> null
        }
        if (style != null) withStyle(style) { append(buf.toString()) } else append(buf.toString())
        buf.clear()
    }

    while (i < line.length) {
        when {
            !code && line.startsWith("**", i) -> { flush(); bold = !bold; i += 2 }
            line[i] == '`' -> { flush(); code = !code; i++ }
            else -> { buf.append(line[i]); i++ }
        }
    }
    flush()
}

/** 序号行只加粗"1. 小标题："部分；没有冒号，或冒号落在 `代码` 里面时不加粗。 */
private fun boldLead(line: String): String {
    val colon = line.indexOfFirst { it == '：' || it == ':' }
    if (colon < 1) return line
    val lead = line.substring(0, colon + 1)
    if (lead.count { it == '`' } % 2 != 0) return line
    return "**" + lead.replace("**", "") + "**" + line.substring(colon + 1)
}

/**
 * 渲染一段多行文字：每行一个 Text。
 * 行首 "• " 一级列表、"- " 二级列表（缩进），"1. 小标题：" 这类序号行加粗冒号前的部分。
 */
@Composable
fun Markup(text: String, color: Color = Palette.TextBody, modifier: Modifier = Modifier) {
    Column(modifier) {
        for (raw in text.lines()) {
            val line = raw.trimStart()
            if (line.isEmpty()) {
                Text("", fontSize = 6.sp)
                continue
            }
            val (indent, body) = when {
                line.startsWith("- ") -> 32.dp to "◦ " + line.drop(2)
                line.startsWith("• ") -> 14.dp to line
                else -> 0.dp to line
            }
            val heading = Regex("""^\d{1,2}\. """).containsMatchIn(body)
            Text(
                inlineMarkup(if (heading) boldLead(body) else body),
                color = color,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(start = indent, top = if (heading) 6.dp else 1.dp, bottom = 1.dp),
            )
        }
    }
}
