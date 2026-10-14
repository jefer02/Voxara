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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.voxara.core.design.Palette
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.design.InterFamily

/**
 * VOXARA PHONE THEME — the watch's tokens (palette, Inter, zone colours) extended with a light
 * theme. Material 3 underneath, with Voxara's own colour scheme: dynamic colour is off by design,
 * so zones and brand never change with the wallpaper.
 */
@Immutable
data class VoxColors(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val outline: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    /** Brand accent for text and icons (readable on [background]). */
    val brand: Color,
    val brandGradient: Brush,
) {
    /** Bright zone fill: rings, bars, strips. */
    fun zoneFill(z: RiskZone): Color = Color(Palette.zone(z))

    /** Zone colour for text and icons on this theme's backgrounds. */
    fun zoneInk(z: RiskZone): Color = if (dark) Color(Palette.zone(z)) else Color(Palette.zoneInk(z))
}

private val DarkVox = VoxColors(
    dark = true,
    background = Color(Palette.BLACK),
    surface = Color(Palette.SURFACE_1),
    surface2 = Color(Palette.SURFACE_2),
    outline = Color(Palette.OUTLINE),
    text = Color(Palette.TEXT_PRIMARY),
    text2 = Color(Palette.TEXT_SECONDARY),
    text3 = Color(Palette.TEXT_TERTIARY),
    brand = Color(Palette.BRAND_START),
    brandGradient = Brush.linearGradient(listOf(Color(Palette.BRAND_START), Color(Palette.BRAND_END))),
)

private val LightVox = VoxColors(
    dark = false,
    background = Color(Palette.LIGHT_BG),
    surface = Color(Palette.LIGHT_SURFACE_1),
    surface2 = Color(Palette.LIGHT_SURFACE_2),
    outline = Color(Palette.LIGHT_OUTLINE),
    text = Color(Palette.LIGHT_TEXT_PRIMARY),
    text2 = Color(Palette.LIGHT_TEXT_SECONDARY),
    text3 = Color(Palette.LIGHT_TEXT_TERTIARY),
    brand = Color(Palette.LIGHT_BRAND_INK),
    brandGradient = Brush.linearGradient(listOf(Color(Palette.LIGHT_BRAND_INK), Color(Palette.LIGHT_BRAND_INK_2))),
)

val LocalVox = staticCompositionLocalOf { DarkVox }

/** Reduced motion (the system "remove animations" setting): springs become instant. */
val LocalReduceMotion = staticCompositionLocalOf { false }

object Vox {
    val colors: VoxColors @Composable get() = LocalVox.current
}

private const val TABULAR = "tnum"

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0, tabular: Boolean = false) = TextStyle(
    fontFamily = InterFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.em, fontFeatureSettings = if (tabular) TABULAR else null,
)

/** Large collapsing titles, bold tabular numerals, calm body text. All in sp: the font scale applies. */
val VoxTypography = Typography(
    displayLarge = style(56, 60, FontWeight.ExtraBold, -0.03, tabular = true),
    displayMedium = style(44, 48, FontWeight.Bold, -0.02, tabular = true),
    displaySmall = style(34, 40, FontWeight.Bold, -0.02, tabular = true),
    headlineLarge = style(34, 41, FontWeight.Bold, -0.01),
    headlineMedium = style(28, 34, FontWeight.Bold, -0.01),
    headlineSmall = style(22, 28, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(17, 22, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.SemiBold),
    bodyLarge = style(17, 24, FontWeight.Normal),
    bodyMedium = style(15, 21, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(15, 20, FontWeight.Medium, tabular = true),
    labelMedium = style(13, 16, FontWeight.Medium, tabular = true),
    labelSmall = style(12, 16, FontWeight.Medium, 0.02, tabular = true),
)

val VoxShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun VoxaraPhoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val vox = if (darkTheme) DarkVox else LightVox
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = vox.brand, onPrimary = Color.Black,
            primaryContainer = vox.surface2, onPrimaryContainer = vox.text,
            secondary = Color(Palette.BRAND_END), onSecondary = Color.Black,
            secondaryContainer = vox.surface2, onSecondaryContainer = vox.text,
            background = vox.background, onBackground = vox.text,
            surface = vox.background, onSurface = vox.text,
            surfaceVariant = vox.surface2, onSurfaceVariant = vox.text2,
            surfaceContainerLowest = vox.background, surfaceContainerLow = vox.surface,
            surfaceContainer = vox.surface, surfaceContainerHigh = vox.surface2, surfaceContainerHighest = vox.surface2,
            outline = vox.outline, outlineVariant = vox.outline,
            error = Color(Palette.ZONE_DANGEROUS), onError = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = vox.brand, onPrimary = Color.White,
            primaryContainer = vox.surface2, onPrimaryContainer = vox.text,
            secondary = Color(Palette.LIGHT_BRAND_INK_2), onSecondary = Color.White,
            secondaryContainer = vox.surface2, onSecondaryContainer = vox.text,
            background = vox.background, onBackground = vox.text,
            surface = vox.background, onSurface = vox.text,
            surfaceVariant = vox.surface2, onSurfaceVariant = vox.text2,
            surfaceContainerLowest = vox.surface, surfaceContainerLow = vox.surface,
            surfaceContainer = vox.surface, surfaceContainerHigh = vox.surface2, surfaceContainerHighest = vox.surface2,
            outline = vox.outline, outlineVariant = vox.outline,
            error = Color(Palette.ZONE_DANGEROUS_INK), onError = Color.White,
        )
    }
    CompositionLocalProvider(LocalVox provides vox, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = scheme, typography = VoxTypography, shapes = VoxShapes, content = content)
    }
}
