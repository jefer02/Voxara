package com.example.voxara.phone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.voxara.core.design.Accent
import com.example.voxara.core.design.Palette
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.design.JakartaFamily
import com.example.voxara.ui.design.MonoFamily
import com.example.voxara.ui.design.TABULAR
import com.example.voxara.ui.design.VoxTone

/**
 * VOXARA PHONE THEME — the template style (docs/design/STYLE.md) on Material 3: paper-white and
 * deep blue-black neutrals, one user-chosen accent (Purple by default) and the level-reactive
 * tiers. Dynamic colour is off by design, so zones and the accent never change with the wallpaper.
 */
@Immutable
data class VoxColors(
    val dark: Boolean,
    val background: Color,
    /** Cards. */
    val surface: Color,
    /** Insets inside cards, tracks, chips. */
    val surface2: Color,
    /** 1 dp card borders and hairline dividers. */
    val outline: Color,
    /** Inactive capsules and ring tracks. */
    val track: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    /** The chosen accent (fill, ink, on-fill, soft, glow). */
    val accent: VoxTone,
    /** Second chart series (headphones). */
    val secondary: Color,
    val accentChoice: Accent,
) {
    /** Accent for text and icons (readable on every surface of this theme). */
    val brand: Color get() = accent.ink

    /** The level-reactive tone of a zone: the accent for OK and MODERATE, amber for LOUD, crimson for DANGEROUS. */
    fun level(z: RiskZone): VoxTone = VoxTone.level(z, accentChoice, dark)

    /** Zone tier fill: rings, bars, capsules. */
    fun zoneFill(z: RiskZone): Color = level(z).fill

    /** Zone tier colour for text and icons. */
    fun zoneInk(z: RiskZone): Color = level(z).ink

    /** Caution notices (pending, stale, uncalibrated). */
    val caution: Color get() = Color(Palette.caution(dark).ink)

    /** Destructive actions and errors. */
    val danger: Color get() = Color(Palette.danger(dark).ink)

    companion object {
        fun of(dark: Boolean, accent: Accent): VoxColors = if (dark) VoxColors(
            dark = true,
            background = Color(Palette.DARK_BG),
            surface = Color(Palette.DARK_CARD),
            surface2 = Color(Palette.DARK_CARD_SUBTLE),
            outline = Color(Palette.DARK_BORDER),
            track = Color(Palette.DARK_TRACK),
            text = Color(Palette.DARK_TEXT),
            text2 = Color(Palette.DARK_TEXT_2),
            text3 = Color(Palette.DARK_MUTED),
            accent = VoxTone.accent(accent, true),
            secondary = Color(Palette.SECONDARY_INK_DARK),
            accentChoice = accent,
        ) else VoxColors(
            dark = false,
            background = Color(Palette.LIGHT_BG),
            surface = Color(Palette.LIGHT_CARD),
            surface2 = Color(Palette.LIGHT_CARD_SUBTLE),
            outline = Color(Palette.LIGHT_BORDER),
            track = Color(Palette.LIGHT_TRACK),
            text = Color(Palette.LIGHT_TEXT),
            text2 = Color(Palette.LIGHT_TEXT_2),
            text3 = Color(Palette.LIGHT_MUTED),
            accent = VoxTone.accent(accent, false),
            secondary = Color(Palette.SECONDARY),
            accentChoice = accent,
        )
    }
}

val LocalVox = staticCompositionLocalOf { VoxColors.of(dark = false, accent = Accent.DEFAULT) }

/** Reduced motion (the system "remove animations" setting): springs become instant, waves stand still. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * The zone that sets the level-reactive accent of headers and heroes: today's dose zone (the phone
 * never shows a live level). OK until the app provides it.
 */
val LocalLevelZone = staticCompositionLocalOf { RiskZone.OK }

object Vox {
    val colors: VoxColors @Composable get() = LocalVox.current
}

private fun style(
    size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0,
    family: FontFamily = JakartaFamily, tabular: Boolean = false,
) = TextStyle(
    fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.em, fontFeatureSettings = if (tabular) TABULAR else null,
)

/**
 * Plus Jakarta Sans for words (extra-bold, tight headings), JetBrains Mono with tabular figures for
 * the display numbers. All in sp: the font scale applies.
 */
val VoxTypography = Typography(
    displayLarge = style(56, 60, FontWeight.Bold, -0.04, MonoFamily, tabular = true),
    displayMedium = style(44, 48, FontWeight.Bold, -0.03, MonoFamily, tabular = true),
    displaySmall = style(32, 38, FontWeight.Bold, -0.03, MonoFamily, tabular = true),
    headlineLarge = style(32, 38, FontWeight.ExtraBold, -0.02),
    headlineMedium = style(26, 32, FontWeight.ExtraBold, -0.02),
    headlineSmall = style(22, 28, FontWeight.ExtraBold, -0.02),
    titleLarge = style(21, 27, FontWeight.ExtraBold, -0.02),
    titleMedium = style(17, 22, FontWeight.Bold, -0.01),
    titleSmall = style(15, 20, FontWeight.Bold),
    bodyLarge = style(16, 23, FontWeight.Medium),
    bodyMedium = style(15, 21, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(15, 20, FontWeight.Bold),
    labelMedium = style(13, 16, FontWeight.SemiBold),
    labelSmall = style(12, 16, FontWeight.SemiBold, 0.01),
)

/** Numbers outside the display styles: JetBrains Mono, tabular. */
object VoxNum {
    val large = style(24, 28, FontWeight.Bold, -0.02, MonoFamily, tabular = true)
    val medium = style(17, 22, FontWeight.Bold, -0.01, MonoFamily, tabular = true)
    val small = style(12, 16, FontWeight.SemiBold, 0.0, MonoFamily, tabular = true)
}

/** The template's small uppercase labels: extra-bold, letter-spaced (callers upper-case the text). */
val EyebrowStyle = style(12, 16, FontWeight.ExtraBold, 0.12)

/** Cards 22 dp, hero 26 dp, pills fully round. */
val VoxShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun VoxaraPhoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.DEFAULT,
    reduceMotion: Boolean = false,
    levelZone: RiskZone = RiskZone.OK,
    content: @Composable () -> Unit,
) {
    val vox = remember(darkTheme, accent) { VoxColors.of(darkTheme, accent) }
    val a = vox.accent
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = a.fill, onPrimary = a.onFill,
            primaryContainer = vox.surface2, onPrimaryContainer = vox.text,
            secondary = vox.secondary, onSecondary = a.onFill,
            secondaryContainer = vox.surface2, onSecondaryContainer = vox.text,
            background = vox.background, onBackground = vox.text,
            surface = vox.background, onSurface = vox.text,
            surfaceVariant = vox.surface2, onSurfaceVariant = vox.text2,
            surfaceContainerLowest = vox.background, surfaceContainerLow = vox.surface,
            surfaceContainer = vox.surface, surfaceContainerHigh = vox.surface2, surfaceContainerHighest = vox.surface2,
            outline = vox.text3, outlineVariant = vox.surface2,
            error = vox.danger, onError = a.onFill,
        )
    } else {
        lightColorScheme(
            primary = a.fill, onPrimary = a.onFill,
            primaryContainer = vox.surface2, onPrimaryContainer = vox.text,
            secondary = vox.secondary, onSecondary = Color.White,
            secondaryContainer = vox.surface2, onSecondaryContainer = vox.text,
            background = vox.background, onBackground = vox.text,
            surface = vox.background, onSurface = vox.text,
            surfaceVariant = vox.surface2, onSurfaceVariant = vox.text2,
            surfaceContainerLowest = vox.surface, surfaceContainerLow = vox.surface,
            surfaceContainer = vox.surface, surfaceContainerHigh = vox.surface2, surfaceContainerHighest = vox.surface2,
            outline = vox.text3, outlineVariant = vox.surface2,
            error = vox.danger, onError = Color.White,
        )
    }
    CompositionLocalProvider(
        LocalVox provides vox,
        LocalReduceMotion provides reduceMotion,
        LocalLevelZone provides levelZone,
    ) {
        MaterialTheme(colorScheme = scheme, typography = VoxTypography, shapes = VoxShapes, content = content)
    }
}
