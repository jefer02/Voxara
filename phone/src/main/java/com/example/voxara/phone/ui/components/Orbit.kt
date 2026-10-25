package com.example.voxara.phone.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.R
import com.example.voxara.phone.ui.theme.LocalReduceMotion
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.theme.figureStyle
import com.example.voxara.phone.ui.theme.wordsStyle
import com.example.voxara.ui.design.RollingDigits
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.animateTone
import com.example.voxara.ui.design.companionStyle
import com.example.voxara.ui.design.wordRes
import kotlin.math.roundToInt

/*
 * THE ORBIT PIECES OF THE PHONE: the 250° ring of the watch (same 110° gap at the bottom), the zone as a word with its
 * icon, the meter to the 100 % line, and the share tile. Colour carries the zone together with an icon and a word, never alone.
 */

/** The ring starts at 145° (bottom left) and sweeps 250°, leaving the 110° gap at the bottom, as on the watch. */
const val RING_START = 145f
const val RING_SWEEP = 250f

// ------------------------------------------------------------------------------------ shared element

/** The scopes a destination is shown in, so the ring on Home can become the ring in Today. Null in previews and tests. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }
val LocalDestinationScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Marks this as the same element across two destinations ([key]); a plain modifier when there is no transition. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedAcrossScreens(key: String): Modifier {
    val shared = LocalSharedTransition.current ?: return this
    val destination = LocalDestinationScope.current ?: return this
    return with(shared) { this@sharedAcrossScreens.sharedBounds(rememberSharedContentState(key), destination) }
}

// ------------------------------------------------------------------------------------ motion helpers

/** 0 → 1 once, when the screen first draws (a chart growing in); 1 at once when motion is reduced. Read it in a draw lambda. */
@Composable
fun rememberDrawIn(durationMs: Int = 650, delayMs: Int = 0): State<Float> {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) {
        if (reduce) progress.snapTo(1f)
        else progress.animateTo(1f, tween(durationMs, delayMs, FastOutSlowInEasing))
    }
    return progress.asState()
}

/** The density with the font scale held at [max]: text drawn on a canvas keeps its place at the largest system setting. */
@Composable
fun cappedDensity(max: Float = 1.3f): Density {
    val d = LocalDensity.current
    return if (d.fontScale <= max) d else Density(d.density, max)
}

// ------------------------------------------------------------------------------------ zone word

/** The zone as an icon and a word in the tier's ink (no pill): colour never carries a zone alone. */
@Composable
fun ZoneWord(zone: RiskZone, modifier: Modifier = Modifier, sizeSp: Int = 15) {
    val c = Vox.colors
    val tone = animateTone(c.level(zone), instant = LocalReduceMotion.current)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(VoxIcons.zone(zone), null, tint = tone.ink, modifier = Modifier.size((sizeSp + 3).dp))
        Text(stringResource(zone.wordRes()), style = wordsStyle(sizeSp, FontWeight.Bold), color = tone.ink)
    }
}

// ------------------------------------------------------------------------------------ meter

/**
 * A linear meter to the daily/weekly limit: the track is the whole allowance, so the fill reaches the end at 100 % and
 * stays full above it; the fill takes the zone's colour. No tick: the end of the track IS the limit.
 */
@Composable
fun Meter(percent: Double, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val c = Vox.colors
    val zone = RiskZone.ofPercent(percent)
    val grow by rememberDrawIn(500)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(c.track, size = size, cornerRadius = r)
        val w = (size.width * (percent / 100.0).coerceIn(0.0, 1.0) * grow).toFloat()
        if (w > 0f) drawRoundRect(c.zoneFill(zone), size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
    }
}

// ------------------------------------------------------------------------------------ the ring

/**
 * THE RING — today's share of the allowance on the watch's 250° arc: a track, a soft glow and the arc in the zone's
 * colour, over a halo of the same colour (depth as light, not shadow). The arc draws in when the screen opens and
 * eases to a new value; [content] sits in the middle. [percent] above 100 fills the arc and the colour says so.
 */
@Composable
fun OrbitRing(percent: Double, modifier: Modifier = Modifier, strokeWidth: Dp = 16.dp, content: @Composable BoxScope.() -> Unit) {
    val c = Vox.colors
    val reduce = LocalReduceMotion.current
    val zone = RiskZone.ofPercent(percent)
    val tone = animateTone(c.level(zone), instant = reduce)
    val target = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    val shown = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target, reduce) {
        if (reduce) shown.snapTo(target)
        else shown.animateTo(target, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessVeryLow))
    }
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = strokeWidth.toPx()
            val inset = w * 1.2f
            val d = minOf(size.width, size.height) - 2 * inset
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            // the halo: a soft wash of the zone's colour behind the ring
            drawCircle(
                Brush.radialGradient(
                    listOf(tone.fill.copy(alpha = if (c.dark) 0.20f else 0.13f), Color.Transparent),
                    center = center, radius = size.minDimension * 0.58f,
                ),
            )
            drawArc(c.track, RING_START, RING_SWEEP, false, topLeft, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            val sweep = RING_SWEEP * shown.value
            if (sweep > 0.5f) {
                drawArc(tone.fill.copy(alpha = 0.16f), RING_START, sweep, false, topLeft, Size(d, d), style = Stroke(w * 1.7f, cap = StrokeCap.Round))
                drawArc(tone.fill, RING_START, sweep, false, topLeft, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/**
 * A figure that always fits: the largest of [sizes] (sp) whose digits and "%" fit in [maxWidth], drawn in the watch's
 * fixed-width digit cells and rolling to a new value. Held at 1.3 times the font scale, like the watch's.
 */
@Composable
fun FitFigure(
    value: Int,
    maxWidth: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    suffix: String = "%",
    sizes: List<Int> = listOf(92, 80, 68, 58, 48),
    spoken: String? = null,
    appear: Boolean = false,
) {
    com.example.voxara.ui.design.CappedFontScale(1.3f) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val digits = value.toString().length
        val size = remember(digits, maxWidth, density.density, density.fontScale, suffix, sizes) {
            val limit = with(density) { maxWidth.toPx() }
            sizes.firstOrNull { sp ->
                val st = figureStyle(sp)
                val cell = value.toString().maxOf { measurer.measure(it.toString(), st).size.width } * DISPLAY_CELL
                val unit = if (suffix.isEmpty()) 0f else measurer.measure(suffix, companionStyle(st)).size.width + with(density) { 6.dp.toPx() }
                digits * cell + unit <= limit
            } ?: sizes.last()
        }
        val reduce = LocalReduceMotion.current
        // With [appear] the figure rolls up from 0 to its value when it first shows.
        var shown by remember { mutableIntStateOf(if (appear && !reduce) 0 else value) }
        LaunchedEffect(value) { shown = value }
        val said = spoken ?: (value.toString() + if (suffix.isNotEmpty()) " $suffix" else "")
        RollingDigits(shown, figureStyle(size), color, modifier, suffix = suffix, still = reduce, spoken = said, cellScale = DISPLAY_CELL, fitCells = true)
    }
}

/** Display-size digit cells sit a little tighter than the widest digit, so "83" reads as one number, not two. */
const val DISPLAY_CELL = 0.92f

// ------------------------------------------------------------------------------------ tile

/**
 * A quiet tile for one share (the last 7 days, the headphones): icon and label, the figure, a line of what it is a
 * share of, and a meter to the limit. The whole tile is the target (48 dp or more) and gives press feedback.
 */
@Composable
fun ShareTile(
    label: String,
    caption: String,
    percent: Double,
    icon: ImageVector,
    iconTint: Color,
    spoken: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = Vox.colors
    val source = remember { MutableInteractionSource() }
    val body = @Composable {
        Column(
            Modifier.padding(16.dp).semantics(mergeDescendants = true) { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = c.text2)
            }
            FitFigure(percent.roundToInt(), maxWidth = 200.dp, color = c.text, sizes = listOf(36, 30, 26), spoken = "")
            Text(caption, style = MaterialTheme.typography.bodySmall, color = c.text2)
            Meter(percent)
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick, modifier = modifier.pressFeedback(source), shape = RoundedCornerShape20, color = c.surface2,
            contentColor = c.text, interactionSource = source,
        ) { body() }
    } else {
        Surface(modifier = modifier, shape = RoundedCornerShape20, color = c.surface2, contentColor = c.text) { body() }
    }
}

private val RoundedCornerShape20 = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)

/** A text style for annotation text inside charts. */
internal val AnnotationStyle: TextStyle get() = wordsStyle(12, FontWeight.SemiBold, 1.2f)
