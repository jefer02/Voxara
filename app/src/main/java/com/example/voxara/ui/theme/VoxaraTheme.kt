package com.example.voxara.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * STYLE GUIDE.
 *
 *  #04060A VOID / BASE    #070A10 PANEL
 *  #2BFF88 SAFE / NEON    #FFC531 CAUTION    #FF8A1F INCANDESCENT
 *  #FF2E6B CRITICAL       #35E8FF SIGNAL / AI
 *  INK 3 — F2F7FF / 93A6BD / 4E6178
 *
 * Non-negotiable 01: accent colour is state, never decoration. A screen with nothing wrong
 * has no accent on it.
 */
object Vox {
    val Void = Color(0xFF04060A)
    val Panel = Color(0xFF070A10)
    val Safe = Color(0xFF2BFF88)
    val Caution = Color(0xFFFFC531)
    val Incandescent = Color(0xFFFF8A1F)
    val Critical = Color(0xFFFF2E6B)
    val CriticalMax = Color(0xFFFF1050)
    val Signal = Color(0xFF35E8FF)

    val Ink1 = Color(0xFFF2F7FF)
    val Ink2 = Color(0xFF93A6BD)
    val Ink3 = Color(0xFF4E6178)
    val Ink2Dim = Color(0xFF8A9CB3)
    val Hairline = Color(0x14FFFFFF)
}

/**
 * TYPE SYSTEM. The dossier specifies Oxanium 800 for numerals, Saira for body, JetBrains Mono
 * for all-caps metadata. None of those ship on Wear OS and downloadable fonts need Play
 * Services at first paint, so the app maps them onto the platform families and keeps the
 * metrics — weight, tracking, line height, tabular feel — which is what carries the voice.
 */
data class VoxTypography(
    /** OXANIUM 800 · 84 dp · LH 0.82 · TRACK -4.5% · TABULAR */
    val hero: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 62.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.045).em,
        textAlign = TextAlign.Center,
    ),
    val display: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.04).em,
        textAlign = TextAlign.Center,
    ),
    val numeral: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.02).em,
    ),
    /** SAIRA 600 / 400 — titles and body, 14 dp minimum. */
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        textAlign = TextAlign.Center,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
    ),
    /** JETBRAINS MONO 500 · 10 dp · TRACK +0.18em · ALL CAPS METADATA ONLY. */
    val meta: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.18.em,
        textAlign = TextAlign.Center,
    ),
    val metaSmall: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        letterSpacing = 0.20.em,
        textAlign = TextAlign.Center,
    ),
)

val LocalVoxTypography = staticCompositionLocalOf { VoxTypography() }

/** Ambient variant: numerals drop to weight 400, no glow, hairline geometry. */
val LocalAmbient = staticCompositionLocalOf { false }

private val VoxColorScheme = ColorScheme(
    primary = Vox.Safe,
    onPrimary = Vox.Void,
    primaryContainer = Color(0xFF0B2A19),
    onPrimaryContainer = Vox.Safe,
    secondary = Vox.Signal,
    onSecondary = Vox.Void,
    secondaryContainer = Color(0xFF07222B),
    onSecondaryContainer = Vox.Signal,
    error = Vox.Critical,
    onError = Vox.Ink1,
    background = Vox.Void,
    onBackground = Vox.Ink1,
    surfaceContainerLow = Vox.Panel,
    surfaceContainer = Vox.Panel,
    surfaceContainerHigh = Color(0xFF101722),
    onSurface = Vox.Ink1,
    onSurfaceVariant = Vox.Ink2,
    outline = Vox.Ink3,
    outlineVariant = Color(0xFF1B2531),
)

@Composable
fun VoxaraTheme(
    ambient: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalVoxTypography provides VoxTypography(),
        LocalAmbient provides ambient,
    ) {
        MaterialTheme(colorScheme = VoxColorScheme, content = content)
    }
}
