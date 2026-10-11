package com.bu.paperclip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE4E4E7),
    onPrimary = Color(0xFF18181B),
    primaryContainer = Color(0xFF3F3F46),
    onPrimaryContainer = Color(0xFFF4F4F5),
    secondary = Color(0xFFA1A1AA),
    onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF27272A),
    onSecondaryContainer = Color(0xFFE4E4E7),
    tertiary = Color(0xFF60A5FA),
    background = Color(0xFF111113),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF111113),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF27272A),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainerLowest = Color(0xFF0C0C0E),
    surfaceContainerLow = Color(0xFF18181B),
    surfaceContainer = Color(0xFF1C1C1F),
    surfaceContainerHigh = Color(0xFF232326),
    surfaceContainerHighest = Color(0xFF2B2B2F),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A),
    error = Color(0xFFF87171),
    onError = Color(0xFF18181B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF18181B),
    onPrimary = Color(0xFFFAFAFA),
    primaryContainer = Color(0xFFE4E4E7),
    onPrimaryContainer = Color(0xFF18181B),
    secondary = Color(0xFF52525B),
    onSecondary = Color(0xFFFAFAFA),
    secondaryContainer = Color(0xFFF4F4F5),
    onSecondaryContainer = Color(0xFF18181B),
    tertiary = Color(0xFF2563EB),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF09090B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF09090B),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF71717A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF4F4F5),
    surfaceContainerHigh = Color(0xFFEFEFF1),
    surfaceContainerHighest = Color(0xFFE4E4E7),
    outline = Color(0xFFD4D4D8),
    outlineVariant = Color(0xFFE4E4E7),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
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
    blue = Color(0xFF60A5FA), violet = Color(0xFFA78BFA), orange = Color(0xFFFB923C), gray = Color(0xFFA1A1AA),
)
private val LightStatus = StatusColors(
    green = Color(0xFF16A34A), yellow = Color(0xFFCA8A04), red = Color(0xFFDC2626),
    blue = Color(0xFF2563EB), violet = Color(0xFF7C3AED), orange = Color(0xFFEA580C), gray = Color(0xFF71717A),
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

private val AppTypography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

@Composable
fun PaperclipTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
    }
}
