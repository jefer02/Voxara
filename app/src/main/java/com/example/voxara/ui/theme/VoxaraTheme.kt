package com.example.voxara.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.example.voxara.core.design.Palette
import com.example.voxara.ui.design.JakartaFamily
import com.example.voxara.ui.design.LocalAnimate
import com.example.voxara.ui.design.VColor

/**
 * THEME — Material 3 for Wear OS on top of the Voxara design tokens (ui/design/Tokens.kt,
 * docs/design/STYLE.md): pure black, dark cards, Purple as the accent. Tier colours (caution,
 * danger) are never Material roles: they belong to the level-reactive accent.
 */

/** Ambient (always-on): no fills, no glow, hairline geometry, no animation. */
val LocalAmbient = staticCompositionLocalOf { false }

/**
 * Reduce motion: true when the system "remove animations" setting is on (animator duration scale
 * 0). Free-running animations stop; value changes snap.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Accent soft over the card, as an opaque colour (Material containers must be opaque). */
private val accentContainer = Color(Palette.over(Palette.accent(com.example.voxara.core.design.Accent.PURPLE, true).fill, 0.22f, Palette.DARK_CARD))

/**
 * Material 3 colour roles mapped onto the tokens: black background, dark cards, the accent fill
 * with dark text on it as primary (the template's filled buttons).
 */
private val VoxColorScheme = ColorScheme(
    primary = VColor.accent.fill,
    primaryDim = VColor.accent.fill,
    onPrimary = VColor.accent.onFill,
    primaryContainer = accentContainer,
    onPrimaryContainer = VColor.Text,
    secondary = VColor.Secondary,
    onSecondary = VColor.accent.onFill,
    secondaryContainer = VColor.CardSubtle,
    onSecondaryContainer = VColor.Text,
    tertiary = VColor.AccentInk,
    onTertiary = VColor.accent.onFill,
    error = VColor.Danger,
    onError = VColor.accent.onFill,
    background = VColor.Black,
    onBackground = VColor.Text,
    surfaceContainerLow = VColor.Card,
    surfaceContainer = VColor.Card,
    surfaceContainerHigh = VColor.CardSubtle,
    onSurface = VColor.Text,
    onSurfaceVariant = VColor.TextSecondary,
    outline = Color(0xFF3A4152),
    outlineVariant = VColor.CardSubtle,
)

/** Plus Jakarta Sans everywhere Material 3 draws text (buttons, time text, dialogs). */
private val VoxM3Typography = Typography(JakartaFamily)

@Composable
fun VoxaraTheme(
    ambient: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAmbient provides ambient,
        LocalReduceMotion provides reduceMotion,
        // Free-running animation only while interactive and allowed (the lifecycle gate is in animationsActive).
        LocalAnimate provides (!ambient && !reduceMotion),
    ) {
        MaterialTheme(colorScheme = VoxColorScheme, typography = VoxM3Typography, content = content)
    }
}
