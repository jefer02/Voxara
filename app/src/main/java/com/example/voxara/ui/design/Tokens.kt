package com.example.voxara.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.voxara.core.design.Accent
import com.example.voxara.core.design.Palette
import com.example.voxara.core.risk.RiskZone

/**
 * VOXARA WATCH DESIGN TOKENS — colour, type, spacing, radius, motion (docs/design/STYLE.md). Every
 * screen and component reads from here; nothing hard-codes a value. The watch is dark only, on
 * pure black, and its accent is always Purple.
 */
object VColor {
    val Black = Color(Palette.BLACK)
    /** Lifted panels on black. */
    val Card = Color(Palette.DARK_CARD)
    /** Insets inside cards, chips, inactive bars. */
    val CardSubtle = Color(Palette.DARK_CARD_SUBTLE)
    /** Ring tracks and inactive capsules (white 12 %). */
    val Track = Color(Palette.DARK_TRACK)
    /** The bezel arc's track (white 7 %). */
    val BezelTrack = Color(Palette.WATCH_TRACK)
    /** 1 dp borders (white 8 %). */
    val Border = Color(Palette.DARK_BORDER)
    val Text = Color(Palette.DARK_TEXT)
    val TextSecondary = Color(Palette.DARK_TEXT_2)
    val TextTertiary = Color(Palette.DARK_MUTED)

    /** The watch accent (Purple, dark variants). */
    val accent: VoxTone = VoxTone.accent(Accent.PURPLE, dark = true)
    /** Accent for text and icons. */
    val AccentInk = accent.ink
    /** Chart second series (headphones) and Tono's mark. */
    val Secondary = Color(Palette.SECONDARY_INK_DARK)
    /** The same indigo as a graphic fill (arcs, rings, the moon's headphones side): 4.4:1 on black. */
    val SecondaryFill = Color(Palette.SECONDARY)
    /** Caution notices (power saving, uncalibrated) and the LOUD tier. */
    val Caution = Color(Palette.caution(true).ink)
    /** Errors and the DANGEROUS tier. */
    val Danger = Color(Palette.danger(true).ink)

    /** The level-reactive tone of a zone: accent (OK, MODERATE), caution (LOUD), danger (DANGEROUS). */
    fun tone(z: RiskZone): VoxTone = VoxTone.level(z, Accent.PURPLE, dark = true)

    /** The zone's tier colour, safe for text and graphics on black and on cards. */
    fun zone(z: RiskZone): Color = tone(z).ink
}

/**
 * TYPE SCALE — Plus Jakarta Sans for words and numbers. JetBrains Mono is not used for numerals on
 * the watch: its zero carries a dot that no OpenType setting removes ("30" reads "30·"). Standalone
 * figures are laid out in fixed-width digit cells ([TabularNumber]) so they stay tabular, which is why
 * no style asks for `tnum`: Plus Jakarta Sans' tabular alternates give "1" a slab foot, and every
 * numeral on the watch must be the same plain glyph set. Nothing below 12 sp; all sizes are sp, so the
 * wearer's font-size setting scales them.
 */
object VType {
    val Hero = TextStyle(
        fontFamily = JakartaFamily, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, lineHeight = 54.sp,
        letterSpacing = (-0.03).em, textAlign = TextAlign.Center,
    )
    val Display = TextStyle(
        fontFamily = JakartaFamily, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 40.sp,
        letterSpacing = (-0.02).em, textAlign = TextAlign.Center,
    )
    /** Editorial titles: extra-bold, tight. */
    val Title = TextStyle(
        fontFamily = JakartaFamily, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 23.sp,
        letterSpacing = (-0.02).em, textAlign = TextAlign.Center,
    )
    val TitleSmall = TextStyle(
        fontFamily = JakartaFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp,
        letterSpacing = (-0.01).em, textAlign = TextAlign.Center,
    )
    val Body = TextStyle(
        fontFamily = JakartaFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp,
        textAlign = TextAlign.Center,
    )
    val Label = TextStyle(
        fontFamily = JakartaFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp,
        textAlign = TextAlign.Center,
    )
    /** The minimum size anywhere in the app. */
    val Caption = TextStyle(
        fontFamily = JakartaFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp,
        textAlign = TextAlign.Center,
    )
    val Numeral = TextStyle(
        fontFamily = JakartaFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 24.sp,
        letterSpacing = (-0.01).em, textAlign = TextAlign.Center,
    )
    /** Small numbers in rows, chips and scale labels. */
    val Value = TextStyle(
        fontFamily = JakartaFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp,
        textAlign = TextAlign.Center,
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
    val card = 22.dp
    val tile = 10.dp
    val pill = 50            // percent: fully round
    val bar = 6.dp
}

object VSize {
    /** Wear OS / Material minimum touch target. */
    val touch = 48.dp
    /** The bezel arc (template: 5.5 px on a 340 px ring). */
    val bezel = 5.dp
    val iconTile = 32.dp
    val border = 1.dp
}

/**
 * Motion. Springs for values that follow data, short tweens for appear/disappear, 400 ms between
 * zone tiers, a longer decelerating sweep when an arc first draws. Every use is gated by
 * `LocalReduceMotion` / `LocalAnimate` (static fallback) and never runs in ambient.
 */
object VMotion {
    /** The bezel arc sweeping from empty to its value when a screen opens. */
    const val SWEEP_MS = 650
    /** A page picture retracting when its page stops being the current one (faster than it draws in). */
    const val EXIT_MS = 250
    /** One digit rolling to the next. */
    const val DIGIT_MS = 220
    /** The zone tag swapping its icon and word. */
    const val TAG_MS = 150
    /** Chip / button press feedback. */
    const val PRESS_MS = 100
    /** The limit alert's entrance pulse (matches the first haptic beat). */
    const val PULSE_MS = 420
    fun sweep() = tween<Float>(durationMillis = SWEEP_MS, easing = FastOutSlowInEasing)
    fun <T> value() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    fun <T> enter() = tween<T>(durationMillis = 250, easing = FastOutSlowInEasing)
    fun <T> exit() = tween<T>(durationMillis = 200, easing = FastOutSlowInEasing)
}
