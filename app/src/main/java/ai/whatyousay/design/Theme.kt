package ai.whatyousay.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Token-driven design system. One source of truth for color, type, and structure.
 *
 * Rules: 1px borders create structure, backgrounds stay quiet, one confident accent,
 * no purple-gradient-rounded-card aesthetic. The accent is a high-visibility signal
 * orange, fitting a tool built for the dead zone. Swap BrandFont for a bundled
 * typeface (a geometric like Space Grotesk) by dropping a font into res/font and
 * referencing it here; the default below avoids the generic Roboto look on the
 * elements that matter most.
 */

val SignalOrange = Color(0xFFFF5A1F)

private val DarkColors = darkColorScheme(
    primary = SignalOrange,
    onPrimary = Color(0xFF0A0A0A),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFB5B5B5),
    outline = Color(0xFF2C2C2C),
)

private val LightColors = lightColorScheme(
    primary = SignalOrange,
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF0A0A0A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFF1F1EC),
    onSurfaceVariant = Color(0xFF55554F),
    outline = Color(0xFFE0E0DA),
)

/** Language codes and model identifiers render in mono so they read as data, not prose. */
val MonoFamily: FontFamily = FontFamily.Monospace

private val BrandTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    labelSmall = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.sp),
)

@Composable
fun WhatYouSayTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BrandTypography,
        content = content,
    )
}
