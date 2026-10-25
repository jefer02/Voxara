package com.example.voxara.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * NUMERALS, both apps. A standalone figure (the dose, a level, a share) is drawn one fixed-width cell per digit.
 * The cell is as wide as the widest digit of the style, so a figure never jitters as it changes, and the same cells
 * let a digit roll to the next instead of jumping. The fonts give no usable `tnum` (Plus Jakarta Sans' tabular
 * alternates give "1" a slab foot, JetBrains Mono's zero is dotted), so no figure asks for a font feature: the cells
 * do the job, in Plus Jakarta Sans with a plain zero and a plain one (docs/design/STYLE.md §2).
 *
 * Foundation only (no Material, no Wear): the watch and the phone wrap these with their own motion switches.
 */

/** How long a digit takes to roll to the next one. */
const val DIGIT_ROLL_MS = 220

/**
 * Width of the widest digit in [style], measured once per style, density and font scale. [scale] below 1 tightens the
 * cells (a display-size figure reads as one number, not as digits set apart); the glyph is centred and never clipped at rest.
 * [among] limits the measure to the digits a figure really shows ("11" then gets the narrow cell of a one).
 */
@Composable
fun rememberDigitCellWidth(style: TextStyle, scale: Float = 1f, among: String = "0123456789"): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, scale, among, density.density, density.fontScale) {
        val px = among.maxOf { measurer.measure(it.toString(), style).size.width }
        with(density) { (px * scale).toDp() }
    }
}

/** A companion ("%", "dBA", "≈") is 0.42 times its figure and never below 12 sp, with the figure's weight and family. */
fun companionStyle(figure: TextStyle): TextStyle {
    val size = maxOf(figure.fontSize.value * 0.42f, 12f).sp
    return figure.copy(fontSize = size, lineHeight = size * 1.2f, letterSpacing = TextUnit.Unspecified, textAlign = TextAlign.Start)
}

/**
 * [text] with every digit centred in a cell of equal width; everything else (a decimal comma, a colon) at its own
 * width. Read as [text] by a screen reader. Not animated.
 */
@Composable
fun TabularDigits(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, cellScale: Float = 1f) {
    val cell = rememberDigitCellWidth(style, cellScale)
    Row(modifier.clearAndSetSemantics { contentDescription = text }, verticalAlignment = Alignment.Bottom) {
        text.forEach { ch ->
            if (ch.isDigit()) {
                Box(Modifier.width(cell), contentAlignment = Alignment.Center) { Digit(ch, style, color) }
            } else {
                BasicText(ch.toString(), style = style.copy(color = color), maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * [value] as tabular digits that ROLL: when it changes, each changed digit goes up (the value rose) or down (it fell)
 * for [DIGIT_ROLL_MS]. [suffix] ("%", "dBA") and [prefix] ("≈") are smaller companions on the same baseline. With
 * [still] (reduced motion) the digits are plain. [spoken] replaces the description a screen reader hears. [cellScale]
 * tightens the cells and [fitCells] sizes them to the digits shown (see [rememberDigitCellWidth]).
 */
@Composable
fun RollingDigits(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    suffix: String = "",
    prefix: String = "",
    still: Boolean = false,
    spoken: String? = null,
    cellScale: Float = 1f,
    fitCells: Boolean = false,
) {
    val companion = companionStyle(style)
    val lift = Modifier.padding(bottom = style.fontSize.value.dp * 0.11f)
    val cell = rememberDigitCellWidth(style, cellScale, if (fitCells) value.toString() else "0123456789")
    var previous by remember { mutableIntStateOf(value) }
    val rising = value >= previous
    SideEffect { previous = value }
    val digits = value.toString()
    val said = spoken ?: (prefix + digits + (if (suffix.isNotEmpty()) " $suffix" else ""))
    Row(modifier.clearAndSetSemantics { contentDescription = said }, verticalAlignment = Alignment.Bottom) {
        if (prefix.isNotEmpty()) BasicText(prefix, style = companion.copy(color = color), maxLines = 1, softWrap = false, modifier = lift.padding(end = 3.dp))
        digits.forEach { ch ->
            // At rest a glyph may overhang a tightened cell; only a rolling digit is clipped to it.
            Box(if (still) Modifier.width(cell) else Modifier.width(cell).clipToBounds(), contentAlignment = Alignment.Center) {
                if (still) {
                    Digit(ch, style, color)
                } else {
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = {
                            val dir = if (rising) 1 else -1
                            (slideInVertically(tween(DIGIT_ROLL_MS)) { dir * it / 2 } + fadeIn(tween(DIGIT_ROLL_MS))) togetherWith
                                (slideOutVertically(tween(DIGIT_ROLL_MS)) { -dir * it / 2 } + fadeOut(tween(DIGIT_ROLL_MS / 2)))
                        },
                        label = "digit",
                    ) { c -> Digit(c, style, color) }
                }
            }
        }
        if (suffix.isNotEmpty()) BasicText(suffix, style = companion.copy(color = color), maxLines = 1, softWrap = false, modifier = lift.padding(start = 3.dp))
    }
}

@Composable
private fun Digit(ch: Char, style: TextStyle, color: Color) {
    BasicText(ch.toString(), style = style.copy(color = color, textAlign = TextAlign.Center), maxLines = 1, softWrap = false)
}

/**
 * Holds the font scale at [max] for [content]: a hero figure keeps its proportions at the largest system setting while
 * the words around it keep growing (the watch caps its figure at 1.3 times; the phone does the same).
 */
@Composable
fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    if (d.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(d.density, max), content = content)
    }
}
