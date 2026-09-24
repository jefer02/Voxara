package com.example.voxara.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.voxara.R
import com.example.voxara.core.design.Palette
import com.example.voxara.core.risk.RiskZone

/**
 * VOXARA DESIGN TOKENS — colour, type, spacing, radius, motion. Every screen and component reads
 * from here; nothing hard-codes a value.
 */
object VColor {
    val Black = Color(Palette.BLACK)
    val Surface1 = Color(Palette.SURFACE_1)
    val Surface2 = Color(Palette.SURFACE_2)
    val Surface3 = Color(Palette.SURFACE_3)
    val Outline = Color(Palette.OUTLINE)
    val Text = Color(Palette.TEXT_PRIMARY)
    val TextSecondary = Color(Palette.TEXT_SECONDARY)
    val TextTertiary = Color(Palette.TEXT_TERTIARY)
    val BrandStart = Color(Palette.BRAND_START)
    val BrandEnd = Color(Palette.BRAND_END)
    val OnZone = Color(Palette.ON_ZONE)

    fun zone(z: RiskZone): Color = Color(Palette.zone(z))

    val brand: Brush get() = Brush.linearGradient(listOf(BrandStart, BrandEnd))
}

/** Inter (SIL OFL 1.1, bundled): one variable font, weights by variation axis. */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) = Font(
    R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val InterFamily = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
    inter(FontWeight.ExtraBold),
)

/**
 * TYPE SCALE — nothing below 12 sp; numerals are tabular so a changing level never jitters.
 * All sizes are sp, so the wearer's font-size setting scales them.
 */
object VType {
    private const val TABULAR = "tnum"

    val Hero = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, lineHeight = 56.sp,
        letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR, textAlign = TextAlign.Center,
    )
    val Display = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 42.sp,
        letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR, textAlign = TextAlign.Center,
    )
    val Title = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp,
        textAlign = TextAlign.Center,
    )
    val TitleSmall = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp,
        textAlign = TextAlign.Center,
    )
    val Body = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp,
        textAlign = TextAlign.Center,
    )
    val Label = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp,
        fontFeatureSettings = TABULAR, textAlign = TextAlign.Center,
    )
    /** The minimum size anywhere in the app. */
    val Caption = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp,
        textAlign = TextAlign.Center,
    )
    val Numeral = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 26.sp,
        fontFeatureSettings = TABULAR, textAlign = TextAlign.Center,
    )
}

/** 4-point spacing grid. */
object VSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    /** Side inset that keeps content inside a round display (~6% of a 216-240 dp screen). */
    val roundInset = 14.dp
}

object VRadius {
    val card = 26.dp
    val pill = 50            // percent: fully round
    val bar = 6.dp
}

object VSize {
    /** Wear OS / Material minimum touch target. */
    val touch = 48.dp
    val ringStroke = 14.dp
    val ringStrokeSmall = 10.dp
}

/** Motion: springs for values, short tweens for appear/disappear; free-running clocks only on Ask. */
object VMotion {
    fun <T> value() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    fun <T> enter() = tween<T>(durationMillis = 250, easing = FastOutSlowInEasing)
    fun <T> exit() = tween<T>(durationMillis = 200, easing = FastOutSlowInEasing)
}
