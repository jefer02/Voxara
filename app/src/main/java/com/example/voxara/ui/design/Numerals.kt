package com.example.voxara.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import androidx.wear.compose.material3.Text
import com.example.voxara.ui.theme.LocalAmbient
import com.example.voxara.ui.theme.LocalReduceMotion

/*
 * NUMERALS — standalone figures (the dBA level, a percentage) are drawn one fixed-width cell per
 * digit. The cell is as wide as the widest digit of the style, so a figure never jitters as it
 * changes (tabular by construction: the bundled fonts give no reliable `tnum`), and the same cells
 * let a digit roll to the next instead of jumping.
 */

/** Width of the widest digit in [style], measured once per style and display density. */
@Composable
private fun rememberDigitCellWidth(style: TextStyle): androidx.compose.ui.unit.Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density.density, density.fontScale) {
        val px = ('0'..'9').maxOf { measurer.measure(it.toString(), style).size.width }
        with(density) { px.toDp() }
    }
}

/** [text] with every digit centred in a cell of equal width. Not animated. */
@Composable
fun TabularNumber(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val cell = rememberDigitCellWidth(style)
    Row(
        modifier.clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text.forEach { ch ->
            if (ch.isDigit()) {
                Box(Modifier.width(cell), contentAlignment = Alignment.Center) {
                    Text(ch.toString(), style = style.copy(textAlign = TextAlign.Center), color = color, maxLines = 1, softWrap = false)
                }
            } else {
                Text(ch.toString(), style = style, color = color, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * ROLLING NUMBER — [value] as tabular digits; when it changes, each changed digit rolls up (value
 * rose) or down (value fell) for [VMotion.DIGIT_MS]. [suffix] ("dBA", "%") and [prefix] ("≈") are
 * smaller companions on the same baseline, never below 12 sp. With reduced motion or in ambient the
 * digits are plain.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RollingNumber(
    value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier, suffix: String = "", prefix: String = "",
) {
    val still = LocalReduceMotion.current || LocalAmbient.current
    val companionSize = maxOf(style.fontSize.value * 0.42f, 12f).sp   // never below the 12 sp floor
    val companion = style.copy(
        fontSize = companionSize, lineHeight = companionSize * 1.2f,
        letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified, textAlign = TextAlign.Start,
    )
    val lift = Modifier.padding(bottom = style.fontSize.value.dp * 0.11f)
    val cell = rememberDigitCellWidth(style)
    var previous by remember { mutableIntStateOf(value) }
    val rising = value >= previous
    SideEffect { previous = value }
    val digits = value.toString()
    val spoken = prefix + digits + (if (suffix.isNotEmpty()) " $suffix" else "")
    Row(modifier.clearAndSetSemantics { contentDescription = spoken }, verticalAlignment = Alignment.Bottom) {
        if (prefix.isNotEmpty()) Text(prefix, style = companion, color = color, maxLines = 1, softWrap = false, modifier = lift.padding(end = 3.dp))
        digits.forEach { ch ->
            Box(Modifier.width(cell).clipToBounds(), contentAlignment = Alignment.Center) {
                if (still) {
                    Text(ch.toString(), style = style.copy(textAlign = TextAlign.Center), color = color, maxLines = 1, softWrap = false)
                } else {
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = {
                            val dir = if (rising) 1 else -1
                            (slideInVertically(tween(VMotion.DIGIT_MS)) { dir * it / 2 } + fadeIn(tween(VMotion.DIGIT_MS))) togetherWith
                                (slideOutVertically(tween(VMotion.DIGIT_MS)) { -dir * it / 2 } + fadeOut(tween(VMotion.DIGIT_MS / 2)))
                        },
                        label = "digit",
                    ) { c ->
                        Text(c.toString(), style = style.copy(textAlign = TextAlign.Center), color = color, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        if (suffix.isNotEmpty()) Text(suffix, style = companion, color = color, maxLines = 1, softWrap = false, modifier = lift.padding(start = 3.dp))
    }
}
