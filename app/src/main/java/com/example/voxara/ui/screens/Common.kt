package com.example.voxara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.example.voxara.ui.theme.LocalVoxTypography
import com.example.voxara.ui.theme.Vox

/**
 * Every screen is built for a two-second glance from a raised wrist.
 *
 * Text never sits in the outer 12% of the display, where round-screen clipping and wrist tilt
 * eat it — so the content column is inset by 12% of the shorter edge on every side.
 */
@Composable
fun VoxScreen(
    modifier: Modifier = Modifier,
    background: Color = Vox.Void,
    behind: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        behind()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(SafeInset),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** 12% of a 192 dp small round screen. Keeps every glyph inside the legible disc. */
val SafeInset = PaddingValues(horizontal = 26.dp, vertical = 18.dp)

/** ALL-CAPS METADATA ONLY — JetBrains Mono 500, +0.18em tracking. */
@Composable
fun Meta(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Vox.Ink3,
    small: Boolean = false,
) {
    val type = LocalVoxTypography.current
    Text(
        text = text,
        color = color,
        style = if (small) type.metaSmall else type.meta,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = modifier,
    )
}

@Composable
fun Body(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Vox.Ink2,
    maxLines: Int = 3,
) {
    Text(
        text = text,
        color = color,
        style = LocalVoxTypography.current.body,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        modifier = modifier,
    )
}

@Composable
fun TitleLine(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Vox.Ink1,
) {
    Text(
        text = text,
        color = color,
        style = LocalVoxTypography.current.title,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = modifier,
    )
}

/** The 46 dp hairline divider the dossier puts under the primary number. */
@Composable
fun Hairline(width: Int = 46) {
    Box(
        Modifier
            .padding(top = 6.dp, bottom = 4.dp)
            .width(width.dp)
            .height(1.dp)
            .background(Vox.Hairline)
    )
}

/**
 * No rectangles in the primary hierarchy — arcs, rings, capsules and the bezel itself.
 * This is the capsule.
 */
@Composable
fun Capsule(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val type = LocalVoxTypography.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(percent = 50))
            .let { if (onClick != null) it.clickableCapsule(onClick) else it }
            .padding(horizontal = 13.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Vox.Ink1,
            style = type.title,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Nothing interactive smaller than 48 dp — enforced by the row height, not by the glyph. */
@Composable
fun StatRow(
    label: String,
    value: String,
    valueColor: Color = Vox.Ink1,
) {
    val type = LocalVoxTypography.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Vox.Ink3, style = type.metaSmall)
        Text(text = value, color = valueColor, style = type.numeral)
    }
}

@Composable
fun ColumnCenter(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

private fun Modifier.clickableCapsule(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
