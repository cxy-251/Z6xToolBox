package z6x.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import z6x.framework.Risk
import z6x.framework.SectionKind
import z6x.framework.Status

/** 与 DeckToolBox 同一套 GitHub Dark 配色。object 是单例，相当于 C# 的 static class。 */
object Palette {
    val Bg0 = Color(0xFF0D1117)
    val Bg1 = Color(0xFF161B22)
    val Bg2 = Color(0xFF1C2128)
    val Bg3 = Color(0xFF21262D)
    val Line = Color(0xFF30363D)
    val TextStrong = Color(0xFFF0F6FC)
    val TextBody = Color(0xFFC9D1D9)
    val TextMuted = Color(0xFF8B949E)
    val Accent = Color(0xFF58A6FF)
    val AccentSoft = Color(0xFF1A2B45)
    val Green = Color(0xFF3FB950)
    val Amber = Color(0xFFD29922)
    val Red = Color(0xFFF85149)
    val Purple = Color(0xFFBC8CFF)
    val CodeText = Color(0xFF79C0FF)
    val CodeBg = Color(0xFF1F2A37)
}

val MonoFont = FontFamily.Monospace

// 扩展属性：给已有类型"加"属性而不用改它的源码
val Risk.color: Color
    get() = when (this) {
        Risk.Read -> Palette.Green
        Risk.Change -> Palette.Amber
        Risk.Danger -> Palette.Red
    }

val Status.color: Color
    get() = when (this) {
        Status.Verified -> Palette.Green
        Status.Partial -> Palette.Accent
        Status.Unverified -> Palette.Amber
        Status.Proposal -> Palette.Purple
    }

val SectionKind.color: Color
    get() = when (this) {
        SectionKind.Why -> Palette.Accent
        SectionKind.Story -> Palette.TextStrong
        SectionKind.Consequence -> Palette.Amber
        SectionKind.Steps -> Palette.TextStrong
        SectionKind.Verify -> Palette.Green
        SectionKind.Lesson -> Palette.Purple
    }

@Composable
fun Z6xTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            background = Palette.Bg0,
            surface = Palette.Bg1,
            onSurface = Palette.TextBody,
            outline = Palette.Line,
        ),
        content = content,
    )
}
