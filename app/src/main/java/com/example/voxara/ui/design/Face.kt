package com.example.voxara.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * THE FACE — every proportion of a watch page, as a fraction of the display, so the same layout
 * fills a 192 dp case and a 240 dp one instead of being drawn for one size and left small on the
 * other. Nothing in the pages' geometry is a fixed dp: the ring's radius and stroke, the wave rings,
 * the centre column and the foot all come from the display's width, and the figure and text sizes
 * follow it through [LocalTextScale] and [LocalFigureScale].
 */
internal object Face {
    /**
     * The ring's OUTER edge, in from the display edge (fraction of the display). Like a Wear system
     * progress indicator it hugs the bezel with one small, constant inset; it is the same on every page.
     */
    const val RING_INSET = 0.04f

    /**
     * Where the ring starts and how far it sweeps (degrees, 0 = 3 o'clock, clockwise): 250 degrees from
     * the bottom-left round the top to the bottom-right, leaving a 110 degree gap at the bottom for the
     * foot lines, the page name and the page indicator. (It was 270 degrees; with the ring now at the
     * display's edge a 90 degree gap put the ring's ends on top of the foot lines.)
     */
    const val START = 145f
    const val SWEEP = 250f

    /** The ring's base stroke (fraction of the display): 5.2 dp at 192, 6.5 dp at 240. */
    const val BEZEL = 0.027f

    /** Where the centre column starts under the ring's top (fraction of the display). */
    const val CENTER_TOP = 0.15f

    /** Room under the foot for the page indicator (fraction of the display). */
    const val FOOT_BOTTOM = 0.09f

    /** The wave rings' box inside the ring (padding on each side, fraction of the display). */
    const val WAVES_PAD = 0.035f

    /** The widest the centre column gets (fraction of the display). */
    const val CENTER_WIDTH = 0.72f

    /** The foot group's widest (fraction of the display): it sits in the ring's bottom gap. */
    const val FOOT_WIDTH = 0.66f
}

/** The design size of the pages: a 216 dp display (40 mm class) draws everything at 1.0. */
private const val DESIGN_DP = 216f

/** Multiplier for words in a hero (captions, tags, the page name): larger displays get larger words, small ones keep the 12 sp floor. */
internal fun textScaleFor(display: Dp): Float = (display.value / DESIGN_DP).coerceIn(1f, 1.12f)

/** Multiplier for the big figures: follows the display both ways, within reason. */
internal fun figureScaleFor(display: Dp): Float = (display.value / DESIGN_DP).coerceIn(0.9f, 1.15f)

/** Words in a hero scale with the display; lists and dialogs leave it at 1. */
internal val LocalTextScale = compositionLocalOf { 1f }

/** The display's size (the smaller side), as the page stage measured it. 0 outside a page stage. */
internal val LocalDisplay = compositionLocalOf { 0.dp }

/** The big figures scale with the display. */
internal val LocalFigureScale = compositionLocalOf { 1f }

/** [this] with its size and line height multiplied by [scale] (never below 12 sp for small text). */
internal fun TextStyle.scaled(scale: Float): TextStyle =
    if (scale == 1f) this else copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )

/**
 * [this] held at [maxFontScale] times the wearer's font size: up to there it follows the system font,
 * past it it stops growing (so a one-word button label never breaks mid-word at font 2.0).
 */
@Composable
internal fun TextStyle.capFont(maxFontScale: Float): TextStyle {
    val fontScale = LocalDensity.current.fontScale
    return if (fontScale > maxFontScale) scaled(maxFontScale / fontScale) else this
}

/** The largest the wearer's font size makes the words in a hero (tags, captions, the chip): past it they stop growing so they fit the ring. */
private const val HERO_WORDS_MAX_FONT_SCALE = 1.5f

/**
 * [this] scaled by the hero's text scale, and held at 1.5 times the wearer's font size: the words in a
 * hero keep growing with the system font up to there, then stop, so a tag or a caption never outgrows
 * the ring at font 2.0 (the figure stops at 1.3 times).
 */
@Composable
internal fun TextStyle.heroScaled(): TextStyle {
    val fontScale = LocalDensity.current.fontScale
    val cap = if (fontScale > HERO_WORDS_MAX_FONT_SCALE) HERO_WORDS_MAX_FONT_SCALE / fontScale else 1f
    return scaled(LocalTextScale.current * cap)
}
