package com.example.voxara.phone.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.derivedStateOf
import com.example.voxara.phone.ui.theme.VoxMotion
import com.example.voxara.phone.ui.theme.VoxRadius
import com.example.voxara.phone.ui.theme.VoxSpace
import com.example.voxara.ui.design.rememberDigitCellWidth
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
 * THE ORBIT PIECES OF THE PHONE: the watch's dose ring (one arc from 12 o'clock, clockwise), the zone as a word with its
 * icon, the meter to the 100 % line, the share tile, the counting figure and the shared-element and entrance motion.
 * Colour carries the zone together with an icon and a word, never alone.
 */

// ------------------------------------------------------------------------------------ shared element

/** The scopes a destination is shown in, so Home's cards can become their screens. Null in previews and tests. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }
val LocalDestinationScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks this as the same element across two destinations ([key]): a container transform that moves on the expressive slow
 * spring and clips to [shape] while it flies. A plain modifier when there is no transition or motion is reduced.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedAcrossScreens(key: String, shape: Shape = RoundedCornerShape(VoxRadius.hero)): Modifier {
    val shared = LocalSharedTransition.current ?: return this
    val destination = LocalDestinationScope.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(shared) {
        this@sharedAcrossScreens.sharedBounds(
            rememberSharedContentState(key), destination,
            boundsTransform = { _, _ -> VoxMotion.heroSlow() },
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/** Shared-element keys: the Home card and the screen it grows into. */
object SharedKeys {
    const val DOSE = "dose-card"
    const val HEADPHONES = "headphones-card"
}

/** When each screen first showed its content in this process ([entrance]). */
private object FirstLoad {
    private val started = HashMap<String, Long>()
    fun startOf(screen: String): Long = synchronized(started) { started.getOrPut(screen) { SystemClock.uptimeMillis() } }
}

/**
 * The first-load entrance: the block fades in and rises 12 dp, [index] steps after the first ([VoxMotion.STAGGER_MS]).
 * Only for blocks that compose within the first moment [screen] shows its content, once per process: coming back to a
 * screen, or scrolling to a block later, shows it as it is. Nothing with reduced motion. Read in the draw phase.
 */
@Composable
fun Modifier.entrance(screen: String, index: Int): Modifier {
    val reduce = LocalReduceMotion.current
    val play = remember { !reduce && SystemClock.uptimeMillis() - FirstLoad.startOf(screen) < ENTRANCE_WINDOW_MS }
    if (!play) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(VoxMotion.ENTRANCE_MS, delayMillis = index * VoxMotion.STAGGER_MS, easing = EntranceEasing))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}

private const val ENTRANCE_WINDOW_MS = 400L
private val EntranceEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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

// ------------------------------------------------------------------------------------ the dose ring

/**
 * THE DOSE RING — the watch's Dose ring at phone size, with the same meaning: one arc that starts at 12 o'clock and fills
 * clockwise over a faint full track; full = 100 % of today's limit, and it stays full above it (the colour, the word and the
 * figure say how far). The fill moves on the expressive slow spring when it first shows and when the value changes; the
 * sweep is read in the draw phase, so the fill never recomposes. [content] sits in the middle.
 */
@Composable
fun DoseRing(percent: Double, modifier: Modifier = Modifier, strokeWidth: Dp = 14.dp, content: @Composable BoxScope.() -> Unit) {
    val c = Vox.colors
    val reduce = LocalReduceMotion.current
    val zone = RiskZone.ofPercent(percent)
    val tone = animateTone(c.level(zone), instant = reduce)
    val target = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    val shown = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target, reduce) {
        if (reduce) shown.snapTo(target) else shown.animateTo(target, VoxMotion.heroSlow())
    }
    Box(
        modifier.aspectRatio(1f).drawBehind {
            val w = strokeWidth.toPx()
            val d = size.minDimension - w
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arc = Size(d, d)
            drawArc(c.track, 0f, 360f, false, topLeft, arc, style = Stroke(w))
            // the spring may overshoot a little: the arc never draws past full or before empty
            val sweep = 360f * shown.value.coerceIn(0f, 1f)
            // a clean stroke over the faint track: no glow, no halo
            if (sweep > 0.5f) drawArc(tone.fill, -90f, sweep, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
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
    count: Boolean = false,
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
        val said = spoken ?: (value.toString() + if (suffix.isNotEmpty()) " $suffix" else "")
        if (count) {
            CountingDigits(value, figureStyle(size), color, modifier, suffix, still = reduce, spoken = said)
        } else {
            // With [appear] the figure rolls up from 0 to its value when it first shows.
            var shown by remember { mutableIntStateOf(if (appear && !reduce) 0 else value) }
            LaunchedEffect(value) { shown = value }
            RollingDigits(shown, figureStyle(size), color, modifier, suffix = suffix, still = reduce, spoken = said, cellScale = DISPLAY_CELL, fitCells = true)
        }
    }
}

/**
 * A figure that COUNTS to [value] (from 0 when it first shows, from the last value after that) in fixed digit cells sized
 * to the final figure, right-aligned, so nothing shifts while it runs. Only this composable recomposes while it counts, and
 * only when the shown integer changes. TalkBack hears the final value. Still with reduced motion.
 */
@Composable
private fun CountingDigits(value: Int, style: TextStyle, color: Color, modifier: Modifier, suffix: String, still: Boolean, spoken: String) {
    val anim = remember { Animatable(if (still) value.toFloat() else 0f) }
    LaunchedEffect(value, still) {
        if (still) anim.snapTo(value.toFloat())
        else anim.animateTo(value.toFloat(), tween(VoxMotion.COUNT_MS, easing = CountEasing))
    }
    val shown by remember { derivedStateOf { anim.value.roundToInt() } }
    val cell = rememberDigitCellWidth(style, DISPLAY_CELL, value.toString())
    val companion = companionStyle(style)
    val text = shown.toString()
    val cells = maxOf(text.length, value.toString().length)
    Row(modifier.clearAndSetSemantics { contentDescription = spoken }, verticalAlignment = Alignment.Bottom) {
        repeat(cells - text.length) { Spacer(Modifier.width(cell)) }
        text.forEach { ch ->
            Box(Modifier.width(cell), contentAlignment = Alignment.Center) {
                BasicText(ch.toString(), style = style.copy(color = color, textAlign = TextAlign.Center), maxLines = 1, softWrap = false)
            }
        }
        if (suffix.isNotEmpty()) {
            BasicText(
                suffix, style = companion.copy(color = color), maxLines = 1, softWrap = false,
                modifier = Modifier.padding(start = 3.dp, bottom = style.fontSize.value.dp * 0.11f),
            )
        }
    }
}

private val CountEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

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
    count: Boolean = false,
    sharedKey: String? = null,
) {
    val c = Vox.colors
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    val m = if (sharedKey != null) modifier.sharedAcrossScreens(sharedKey, shape) else modifier
    val body = @Composable {
        Column(
            Modifier.padding(VoxSpace.m).semantics(mergeDescendants = true) { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(VoxSpace.xs),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(VoxSpace.xs)) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = c.text2)
            }
            FitFigure(percent.roundToInt(), maxWidth = 200.dp, color = c.text, sizes = listOf(36, 30, 26), spoken = "", count = count)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = c.text2)
            Meter(percent)
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick, modifier = m.pressFeedback(source), shape = shape, color = c.surface,
            contentColor = c.text, interactionSource = source,
        ) { body() }
    } else {
        Surface(modifier = m, shape = shape, color = c.surface, contentColor = c.text) { body() }
    }
}

/** A text style for annotation text inside charts. */
internal val AnnotationStyle: TextStyle get() = wordsStyle(12, FontWeight.SemiBold, 1.2f)
