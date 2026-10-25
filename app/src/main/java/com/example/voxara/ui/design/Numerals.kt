package com.example.voxara.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.example.voxara.ui.theme.LocalAmbient
import com.example.voxara.ui.theme.LocalReduceMotion

/*
 * NUMERALS (watch) — standalone figures (the dBA level, a percentage) are drawn one fixed-width cell per digit. The
 * cells, the roll and the companion style live in :shared-ui (DigitCells.kt) and are the same on the phone; these two
 * wrappers only add the watch's motion switches (reduced motion and ambient mode make the digits plain).
 */

/** [text] with every digit centred in a cell of equal width. Not animated. */
@Composable
fun TabularNumber(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) =
    TabularDigits(text, style, color, modifier)

/**
 * ROLLING NUMBER — [value] as tabular digits; when it changes, each changed digit rolls up (value rose) or down (value
 * fell). [suffix] ("dBA", "%") and [prefix] ("≈") are smaller companions on the same baseline, never below 12 sp. With
 * reduced motion or in ambient the digits are plain.
 */
@Composable
fun RollingNumber(
    value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier, suffix: String = "", prefix: String = "",
) = RollingDigits(value, style, color, modifier, suffix, prefix, still = LocalReduceMotion.current || LocalAmbient.current)
