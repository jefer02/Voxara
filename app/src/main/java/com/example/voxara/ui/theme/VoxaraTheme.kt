package com.example.voxara.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.design.InterFamily
import com.example.voxara.ui.design.VColor

/**
 * THEME — Material 3 for Wear OS on top of the Voxara design tokens (ui/design/Tokens.kt).
 * Colour is state, never decoration; zone colours are never used as Material roles.
 */

/** Ambient (always-on): no fills, no glow, hairline geometry, no animation. */
val LocalAmbient = staticCompositionLocalOf { false }

/**
 * Reduce motion: true when the system "remove animations" setting is on (animator duration scale
 * 0). Free-running animations stop; value changes snap.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Material 3 colour roles mapped onto the tokens: true-black background, tonal surfaces, brand
 * cyan as primary.
 */
private val VoxColorScheme = ColorScheme(
    primary = VColor.BrandStart,
    onPrimary = VColor.Black,
    primaryContainer = Color(0xFF0C3440),
    onPrimaryContainer = VColor.Text,
    secondary = VColor.BrandEnd,
    onSecondary = VColor.Black,
    secondaryContainer = VColor.Surface3,
    onSecondaryContainer = VColor.Text,
    tertiary = VColor.BrandEnd,
    onTertiary = VColor.Black,
    error = VColor.zone(RiskZone.DANGEROUS),
    onError = VColor.Black,
    background = VColor.Black,
    onBackground = VColor.Text,
    surfaceContainerLow = VColor.Surface1,
    surfaceContainer = VColor.Surface2,
    surfaceContainerHigh = VColor.Surface3,
    onSurface = VColor.Text,
    onSurfaceVariant = VColor.TextSecondary,
    outline = VColor.Outline,
    outlineVariant = VColor.Surface3,
)

/** Inter everywhere Material 3 draws text (buttons, time text, dialogs). */
private val VoxM3Typography = Typography(InterFamily)

@Composable
fun VoxaraTheme(
    ambient: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAmbient provides ambient,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(colorScheme = VoxColorScheme, typography = VoxM3Typography, content = content)
    }
}
