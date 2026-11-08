package site.zhizhang.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 统一设计系统：薄荷绿主色 + 语义色（浅色 / 深色双套）。
 * 旧代码里的 Ink / Muted / Green / Mint / Background / Line 保留同名，
 * 但改为随主题读取，避免深色模式下出现「深色字配深色底」。
 */
data class AppColors(
    val ink: Color,
    val muted: Color,
    val green: Color,
    val mint: Color,
    val background: Color,
    val line: Color,
    val surface: Color,
    val amber: Color,
    val red: Color,
    val onGreen: Color
)

private val LightColors = AppColors(
    ink = Color(0xFF19231F),
    muted = Color(0xFF76817B),
    green = Color(0xFF16745F),
    mint = Color(0xFFE5F3ED),
    background = Color(0xFFF4F6F2),
    line = Color(0xFFE3E9E3),
    surface = Color.White,
    amber = Color(0xFFA76A20),
    red = Color(0xFFBA4940),
    onGreen = Color.White
)

private val DarkColors = AppColors(
    ink = Color(0xFFE8F0EB),
    muted = Color(0xFF96A99F),
    green = Color(0xFF6FC7A4),
    mint = Color(0xFF203A31),
    background = Color(0xFF111A16),
    line = Color(0xFF2B3B33),
    surface = Color(0xFF18251F),
    amber = Color(0xFFE2B36B),
    red = Color(0xFFE68C84),
    onGreen = Color(0xFF0B1F18)
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

val Ink: Color @Composable get() = LocalAppColors.current.ink
val Muted: Color @Composable get() = LocalAppColors.current.muted
val Green: Color @Composable get() = LocalAppColors.current.green
val Mint: Color @Composable get() = LocalAppColors.current.mint
val Background: Color @Composable get() = LocalAppColors.current.background
val Line: Color @Composable get() = LocalAppColors.current.line
val AppSurface: Color @Composable get() = LocalAppColors.current.surface
val Amber: Color @Composable get() = LocalAppColors.current.amber
val Danger: Color @Composable get() = LocalAppColors.current.red
val OnGreen: Color @Composable get() = LocalAppColors.current.onGreen

private fun scheme(dark: Boolean, c: AppColors) = if (dark) {
    darkColorScheme(
        primary = c.green, onPrimary = c.onGreen,
        primaryContainer = c.mint, onPrimaryContainer = c.green,
        secondary = Color(0xFF8FB3A3), background = c.background, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink,
        surfaceVariant = Color(0xFF223129), onSurfaceVariant = c.muted,
        outline = c.line, error = c.red
    )
} else {
    lightColorScheme(
        primary = c.green, onPrimary = c.onGreen,
        primaryContainer = c.mint, onPrimaryContainer = c.green,
        secondary = Color(0xFF527366), background = c.background, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink,
        surfaceVariant = Color(0xFFF0F4F0), onSurfaceVariant = c.muted,
        outline = c.line, error = c.red
    )
}

@Composable
fun ZhizhangTheme(darkMode: Boolean = false, content: @Composable () -> Unit) {
    val app = if (darkMode) DarkColors else LightColors
    CompositionLocalProvider(LocalAppColors provides app) {
        MaterialTheme(
            colorScheme = scheme(darkMode, app),
            typography = MaterialTheme.typography.copy(
                headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = app.ink),
                titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, color = app.ink),
                titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = app.ink),
                titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = app.ink),
                bodyLarge = TextStyle(fontSize = 16.sp, color = app.ink),
                bodyMedium = TextStyle(fontSize = 14.sp, color = app.ink),
                bodySmall = TextStyle(fontSize = 12.sp, color = app.muted),
                labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            ),
            content = content
        )
    }
}
