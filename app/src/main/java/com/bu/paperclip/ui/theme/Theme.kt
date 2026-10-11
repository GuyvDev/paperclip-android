package com.bu.paperclip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bu.paperclip.R

// The palette is deliberately monochrome, like Paperclip's web UI: chrome is neutral and colour only
// carries meaning (status, priority, live work, errors), so it stands out wherever it appears.

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFAFAFA),
    onPrimary = Color(0xFF111113),
    primaryContainer = Color(0xFFFAFAFA),
    onPrimaryContainer = Color(0xFF111113),
    inversePrimary = Color(0xFF18181B),
    secondary = Color(0xFFA1A1AA),
    onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF2A2A2E),
    onSecondaryContainer = Color(0xFFF4F4F5),
    tertiary = Color(0xFF7DB3FF),
    background = Color(0xFF0F0F11),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF0F0F11),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF232326),
    onSurfaceVariant = Color(0xFF9F9FA9),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF0A0A0B),
    surfaceContainerLow = Color(0xFF161618),
    surfaceContainer = Color(0xFF1B1B1E),
    surfaceContainerHigh = Color(0xFF222225),
    surfaceContainerHighest = Color(0xFF2B2B2F),
    surfaceBright = Color(0xFF2B2B2F),
    inverseSurface = Color(0xFFF4F4F5),
    inverseOnSurface = Color(0xFF18181B),
    outline = Color(0xFF3A3A40),
    outlineVariant = Color(0xFF242428),
    scrim = Color(0xFF000000),
    error = Color(0xFFF87171),
    onError = Color(0xFF18181B),
    errorContainer = Color(0xFF3B1618),
    onErrorContainer = Color(0xFFFECACA),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF111113),
    onPrimary = Color(0xFFFAFAFA),
    primaryContainer = Color(0xFF111113),
    onPrimaryContainer = Color(0xFFFAFAFA),
    inversePrimary = Color(0xFFE4E4E7),
    secondary = Color(0xFF52525B),
    onSecondary = Color(0xFFFAFAFA),
    secondaryContainer = Color(0xFFEDEDEF),
    onSecondaryContainer = Color(0xFF18181B),
    tertiary = Color(0xFF2563EB),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF09090B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF09090B),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF6B6B74),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F9FA),
    surfaceContainer = Color(0xFFF4F4F5),
    surfaceContainerHigh = Color(0xFFEEEEF0),
    surfaceContainerHighest = Color(0xFFE6E6E9),
    surfaceBright = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF18181B),
    inverseOnSurface = Color(0xFFFAFAFA),
    outline = Color(0xFFD4D4D8),
    outlineVariant = Color(0xFFE8E8EB),
    scrim = Color(0xFF000000),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

@Immutable
data class StatusColors(
    val green: Color,
    val yellow: Color,
    val red: Color,
    val blue: Color,
    val violet: Color,
    val orange: Color,
    val gray: Color,
)

private val DarkStatus = StatusColors(
    green = Color(0xFF4ADE80), yellow = Color(0xFFFACC15), red = Color(0xFFF87171),
    blue = Color(0xFF60A5FA), violet = Color(0xFFA78BFA), orange = Color(0xFFFB923C), gray = Color(0xFF8B8B94),
)
private val LightStatus = StatusColors(
    green = Color(0xFF16A34A), yellow = Color(0xFFCA8A04), red = Color(0xFFDC2626),
    blue = Color(0xFF2563EB), violet = Color(0xFF7C3AED), orange = Color(0xFFEA580C), gray = Color(0xFF71717A),
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Code, logs, commands and anything that should line up in columns. */
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/** Tabular figures, for numbers that change or sit in columns (costs, counts, durations). */
const val TABULAR = "tnum"

private val trimmed = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    lineHeightStyle = trimmed,
)

private val AppTypography = Typography(
    displaySmall = style(34, 40, FontWeight.SemiBold, -0.025),
    headlineLarge = style(30, 36, FontWeight.SemiBold, -0.022),
    headlineMedium = style(26, 32, FontWeight.SemiBold, -0.02),
    headlineSmall = style(22, 28, FontWeight.SemiBold, -0.017),
    titleLarge = style(20, 26, FontWeight.SemiBold, -0.014),
    titleMedium = style(16, 22, FontWeight.SemiBold, -0.008),
    titleSmall = style(14, 20, FontWeight.SemiBold, -0.004),
    bodyLarge = style(15, 22, FontWeight.Normal, -0.006),
    bodyMedium = style(14, 20, FontWeight.Normal, -0.004),
    bodySmall = style(12, 17, FontWeight.Normal, 0.0),
    labelLarge = style(14, 20, FontWeight.Medium, -0.004),
    labelMedium = style(12, 16, FontWeight.Medium, 0.0),
    labelSmall = style(11, 14, FontWeight.Medium, 0.01),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun PaperclipTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
