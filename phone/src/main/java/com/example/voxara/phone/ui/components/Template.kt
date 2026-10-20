package com.example.voxara.phone.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.voxara.core.design.Accent
import com.example.voxara.core.design.Palette
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.ui.theme.EyebrowStyle
import com.example.voxara.phone.ui.theme.LocalLevelZone
import com.example.voxara.phone.ui.theme.LocalReduceMotion
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.theme.VoxNum
import com.example.voxara.ui.design.PulseDot
import com.example.voxara.ui.design.WaveLines
import com.example.voxara.ui.design.animateTone
import com.example.voxara.ui.design.capsulesLit
import com.example.voxara.ui.design.drawCapsules
import com.example.voxara.ui.design.rememberWavePhase
import com.example.voxara.ui.design.waveSpeed

/*
 * TEMPLATE COMPONENTS (docs/design/STYLE.md §4): editorial label, icon tile, switch, hero card with
 * wave lines, capsule meter, metric triplet and the accent swatches. Colours come from Vox.colors;
 * level-reactive colours from LocalLevelZone or an explicit zone. Data always comes from the caller.
 */

/** The editorial label: pulsing dot + small uppercase, letter-spaced text in the level tone. */
@Composable
fun EditorialLabel(text: String, modifier: Modifier = Modifier, zone: RiskZone = LocalLevelZone.current) {
    val tone = animateTone(Vox.colors.level(zone), instant = LocalReduceMotion.current)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PulseDot(tone.fill, animate = !LocalReduceMotion.current)
        Text(text.uppercase(), style = EyebrowStyle, color = tone.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The template's icon tile: a rounded square in [tint]'s soft tint with the icon in [tint]. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 3)).background(tint.copy(alpha = Palette.SOFT_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size / 2))
    }
}

/**
 * The template switch (48 × 28): grey track with a white thumb when off; accent track with a glow
 * and a dark thumb when on. Visual only: the row around it is the toggleable target.
 */
@Composable
fun VoxSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = Vox.colors
    val instant = LocalReduceMotion.current
    val offset by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        animationSpec = if (instant) snap() else spring(dampingRatio = 0.6f),
        label = "thumb",
    )
    Box(
        modifier
            .size(48.dp, 28.dp)
            .clip(CircleShape)
            .background(if (checked) c.accent.fill else c.track)
            .border(if (checked) 3.dp else 0.dp, if (checked) c.accent.glow else Color.Transparent, CircleShape)
            .clearAndSetSemantics { },
    ) {
        Box(
            Modifier.offset { IntOffset((3.dp + offset).roundToPx(), 3.dp.roundToPx()) }.size(22.dp).clip(CircleShape)
                .background(if (checked) c.accent.onFill else Color.White)
                .border(if (checked) 0.dp else 0.5.dp, c.outline, CircleShape),
        )
    }
}

/**
 * HERO CARD — the template's hero: 26 dp card, deeper shadow, three wave lines drawn behind the
 * content. [intensity] 0..1 must come from real data (the caller's dose); [zone] sets their tier
 * colour. Waves stand still when motion is reduced or the screen is not resumed.
 */
@Composable
fun HeroCard(
    intensity: Float,
    zone: RiskZone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Vox.colors
    val reduce = LocalReduceMotion.current
    val tone = animateTone(c.level(zone), instant = reduce)
    val waves = remember { WaveLines() }
    val phase = rememberWavePhase(running = !reduce, speed = waveSpeed(intensity))
    val shape = MaterialTheme.shapes.extraLarge
    val body: @Composable () -> Unit = {
        Box {
            Canvas(Modifier.matchParentSize()) { waves.draw(this, phase.value, intensity, tone.fill) }
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
    val border = BorderStroke(1.dp, c.outline)
    val shadow = if (c.dark) 0.dp else 6.dp
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = c.surface, contentColor = c.text, border = border, shadowElevation = shadow) { body() }
    } else {
        Surface(modifier = modifier, shape = shape, color = c.surface, contentColor = c.text, border = border, shadowElevation = shadow) { body() }
    }
}

/** A capsule scale mark: [label] placed at [fraction] of the meter's width. */
data class ScaleMark(val fraction: Float, val label: String)

/**
 * SEGMENTED CAPSULE METER — 26 rounded capsules; capsule i takes the tier colour of the zone it
 * stands for ([zoneAt] of its centre, 0..1), the lit part animates, the last lit one is taller.
 * [marks] are drawn under it. Spoken as [description].
 */
@Composable
fun CapsuleMeter(
    fraction: Float,
    zoneAt: (Float) -> RiskZone,
    marks: List<ScaleMark>,
    description: String,
    modifier: Modifier = Modifier,
    count: Int = 26,
) {
    val c = Vox.colors
    val shown by animateFloatAsState(
        fraction.coerceIn(0f, 1f), if (LocalReduceMotion.current) snap() else tween(500), label = "capsules",
    )
    val colors = remember(c, count) { List(count) { i -> c.zoneFill(zoneAt((i + 0.5f) / count)) } }
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            drawCapsules(count, capsulesLit(shown, count), c.track) { colors[it] }
        }
        ScaleMarks(marks)
    }
}

/**
 * Scale labels centred under their position. [marks] are placed in the order given (most
 * important first); one that would overlap an already placed label is left out, so large fonts
 * keep the important marks instead of colliding.
 */
@Composable
private fun ScaleMarks(marks: List<ScaleMark>) {
    val c = Vox.colors
    Layout(
        content = { marks.forEach { Text(it.label, style = VoxNum.small, color = c.text3, maxLines = 1, softWrap = false) } },
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 6.dp.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val taken = mutableListOf<IntRange>()
        val xs = placeables.mapIndexed { i, pl ->
            val x = (marks[i].fraction * width - pl.width / 2f).toInt().coerceIn(0, maxOf(0, width - pl.width))
            val span = (x - gap)..(x + pl.width + gap)
            if (taken.any { it.first <= span.last && span.first <= it.last }) null else x.also { taken += span }
        }
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEachIndexed { i, pl -> xs[i]?.let { pl.place(it, 0) } }
        }
    }
}

/** One cell of a [MetricTriplet]. */
data class Metric(val label: String, val value: String, val caption: String? = null, val captionColor: Color? = null)

/**
 * METRIC TRIPLET — three small cards side by side: uppercase label, Mono value, caption. Stacks
 * vertically when the cells would be too narrow for the font scale.
 */
@Composable
fun MetricTriplet(metrics: List<Metric>, modifier: Modifier = Modifier) {
    val c = Vox.colors
    @Composable
    fun Cell(m: Metric, cell: Modifier) = VoxCard(cell, padding = 12.dp) {
        Text(m.label.uppercase(), style = EyebrowStyle, color = c.text3, maxLines = 3)
        Text(m.value, style = VoxNum.medium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        m.caption?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = m.captionColor ?: c.text3, maxLines = 2) }
    }
    BoxWithConstraints(modifier) {
        // Side by side only while a cell keeps ~96 dp per unit of font scale: labels never break mid-word.
        val cell = (maxWidth - 20.dp) / metrics.size
        if (cell < 96.dp * LocalDensity.current.fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { metrics.forEach { Cell(it, Modifier.fillMaxWidth()) } }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.forEach { Cell(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/**
 * ACCENT SWATCHES — the template's colour picker: four round swatches (48 dp targets); the
 * selected one is scaled up with a ring. Each swatch is a radio button named for TalkBack.
 */
@Composable
fun AccentSwatches(selected: Accent, onSelect: (Accent) -> Unit, names: Map<Accent, String>, modifier: Modifier = Modifier) {
    val c = Vox.colors
    val reduce = LocalReduceMotion.current
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Accent.entries.forEach { a ->
            val on = a == selected
            val swatch = Color(Palette.accent(a, c.dark).fill)
            val scale by animateFloatAsState(if (on) 1.22f else 1f, if (reduce) snap() else tween(200), label = "swatch")
            Box(
                Modifier
                    .size(48.dp)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(a) })
                    .semantics { contentDescription = names[a].orEmpty() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(28.dp).scale(scale).clip(CircleShape).background(swatch)
                        .border(2.dp, if (on) c.text else Color.White.copy(alpha = 0.25f), CircleShape),
                )
            }
        }
    }
}

