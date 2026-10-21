package com.example.voxara.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.theme.LocalAmbient
import com.example.voxara.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * ORBIT VISUALISATIONS — one round-native picture per page, all drawn on the same ring (the 250°
 * bezel, gap at the bottom) so the pages read as one family. The ring's OUTER edge sits on one shared
 * rim, a small constant inset from the display edge ([Face.RING_INSET]), on every page; stroke widths
 * and radii are fractions of the display ([Face]), so a 192 dp and a 240 dp case are the same picture.
 *
 *   Now   the live level on a dBA scale whose four zones light up in their own colours; in headphones
 *         mode the scale opens at the top into two ear-cup arcs that rise with the listening level,
 *         with the outside level as a small moon in the gap
 *   Dose  Today: 24 hourly bars from the ledger, hanging from the rim (midnight bottom-left, noon at the
 *         top). Week: one segment per day, oldest first and today last. The two MORPH into each other
 *
 * Each draws itself in when its page becomes the current one (the "reveal", 0..1) and follows its
 * data with a spring afterwards. Nothing here allocates per frame outside a draw that a state change
 * triggered; no blur.
 */

private const val START = Face.START
private const val SWEEP = Face.SWEEP

/** The two ear-cup arcs, when fully split: the gap between them at the top (degrees). */
private const val SPLIT_GAP_DEG = 42f

/** How far a page's picture rotates (degrees) per page of swipe distance: the ring turns as pages change. */
private const val SHIFT_DEGREES = 40f

/** 0..1 draw-in of the current page's picture. Provided by the pager; absent (null) outside it. */
val LocalPageReveal = compositionLocalOf<State<Float>?> { null }

/** This page's distance from the visible page, in pages (0 at rest, ±1 a full swipe away). Null outside the pager. */
val LocalPageShift = compositionLocalOf<State<Float>?> { null }

/**
 * The draw-in progress for the picture on screen: the pager's (replayed each time the page becomes
 * current), or, outside the pager, a one-off sweep when the screen first appears. 1 when motion is
 * reduced or in ambient.
 */
@Composable
internal fun rememberReveal(): State<Float> {
    LocalPageReveal.current?.let { return it }
    val still = LocalReduceMotion.current || LocalAmbient.current
    val a = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(still) { if (still) a.snapTo(1f) else a.animateTo(1f, VMotion.sweep()) }
    return a.asState()
}

/** [target] followed with a spring (snapped under reduced motion). */
@Composable
private fun follow(target: Float, label: String): State<Float> = animateFloatAsState(
    targetValue = target,
    animationSpec = if (LocalReduceMotion.current || LocalAmbient.current) snap() else VMotion.value(),
    label = label,
)

/** The ring's rotation while pages move, so changing page turns the picture instead of only sliding it. */
@Composable
private fun Modifier.turnWithPager(): Modifier {
    val shift = LocalPageShift.current ?: return this
    return graphicsLayer { rotationZ = shift.value.coerceIn(-1f, 1f) * SHIFT_DEGREES }
}

// ------------------------------------------------------------------------------------ geometry

/**
 * Stroke objects by width and cap, so drawing a frame allocates none (a `Stroke` is an object, unlike
 * `Offset` and `Color`, which are inline). Each picture keeps one for its lifetime.
 */
internal class StrokeCache {
    private val widths = FloatArray(8)
    private val rounds = BooleanArray(8)
    private val strokes = arrayOfNulls<Stroke>(8)
    private var count = 0

    fun get(width: Float, round: Boolean): Stroke {
        for (i in 0 until count) if (widths[i] == width && rounds[i] == round) return strokes[i]!!
        if (count == widths.size) count = 0
        val made = Stroke(width, cap = if (round) StrokeCap.Round else StrokeCap.Butt)
        widths[count] = width; rounds[count] = round; strokes[count] = made
        count++
        return made
    }
}

/** The ring's base stroke: a fraction of the display, times [mult]. */
private fun DrawScope.bezelW(mult: Float = 1f): Float = size.minDimension * Face.BEZEL * mult

/** Radius of the shared rim: where the OUTER edge of every page's ring sits. */
private fun DrawScope.rimOuter(): Float = size.minDimension * (0.5f - Face.RING_INSET)

/** Centre-line radius of a stroke [w] wide whose outer edge sits on the shared rim. */
private fun DrawScope.ringRadius(w: Float): Float = rimOuter() - w / 2

/** A stretch [from]..[to] (fractions of the 270° sweep) of the circle of radius [r]. */
private fun DrawScope.ringArc(c: StrokeCache, r: Float, from: Float, to: Float, color: Color, width: Float, round: Boolean = true) {
    if (to <= from) return
    arcDeg(c, r, START + SWEEP * from, SWEEP * (to - from), color, width, round)
}

/** [sweepDeg] degrees of the circle of radius [r] from [startDeg]. */
private fun DrawScope.arcDeg(c: StrokeCache, r: Float, startDeg: Float, sweepDeg: Float, color: Color, width: Float, round: Boolean = true) {
    if (sweepDeg <= 0f) return
    drawArc(
        color, startDeg, sweepDeg, false,
        Offset(center.x - r, center.y - r), Size(2 * r, 2 * r),
        style = c.get(width, round),
    )
}

private fun DrawScope.ringPoint(r: Float, fraction: Float): Offset = pointAt(r, START + SWEEP * fraction)

private fun DrawScope.pointAt(r: Float, deg: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
}

private fun DrawScope.markerAt(p: Offset, ring: Color, alpha: Float = 1f) {
    val u = size.minDimension / 240f
    drawCircle(Color.Black.copy(alpha = alpha), 8.dp.toPx() * u, p)
    drawCircle(ring.copy(alpha = ring.alpha * alpha), 6.5.dp.toPx() * u, p)
    drawCircle(Color.White.copy(alpha = alpha), 3.5.dp.toPx() * u, p)
}

// ------------------------------------------------------------------------------------ Now

/** Zone boundaries on the dBA scale (RiskZone: Moderate from 70, Loud from 85, Dangerous from 95). */
private val ZONE_STARTS = floatArrayOf(30f, 70f, 85f, 95f, 110f)

private val ZONE_FRACTIONS = FloatArray(5) { gaugeFraction(ZONE_STARTS[it]) }

private fun zoneIndexOf(level: Float): Int = when {
    level >= ZONE_STARTS[3] -> 3
    level >= ZONE_STARTS[2] -> 2
    else -> 0
}

/** The four zone colours of the scale (accent, accent, amber, crimson) and their dim versions. */
@Composable
private fun rememberScaleColors(): Pair<Array<Color>, Array<Color>> = remember {
    val accent = VColor.tone(RiskZone.OK).fill
    val lit = arrayOf(accent, accent, VColor.tone(RiskZone.LOUD).fill, VColor.tone(RiskZone.DANGEROUS).fill)
    lit to Array(4) { lit[it].copy(alpha = 0.26f) }
}

/** A stretch [from]..[to] (fractions of the sweep) of the ring with the top gap open by [gapDeg] (0 = one ring). */
private fun DrawScope.splitArc(
    c: StrokeCache, r: Float, from: Float, to: Float, gapDeg: Float, color: Color, width: Float, round: Boolean = true,
) {
    if (to <= from) return
    val len = SWEEP / 2 - gapDeg / 2
    val rightStart = START + SWEEP / 2 + gapDeg / 2
    val leftEnd = minOf(to, 0.5f)
    if (leftEnd > from) arcDeg(c, r, START + from * 2f * len, (leftEnd - from) * 2f * len, color, width, round)
    val rightFrom = maxOf(from, 0.5f)
    if (to > rightFrom) arcDeg(c, r, rightStart + (rightFrom - 0.5f) * 2f * len, (to - rightFrom) * 2f * len, color, width, round)
}

/** Where fraction [f] of the sweep lands when the top gap is [gapDeg] open. */
private fun splitAngle(f: Float, gapDeg: Float): Float {
    val len = SWEEP / 2 - gapDeg / 2
    return if (f <= 0.5f) START + f * 2f * len else START + SWEEP / 2 + gapDeg / 2 + (f - 0.5f) * 2f * len
}

/**
 * The dBA scale (30 to 110) as four zone segments: OK and Moderate in the accent, Loud in amber,
 * Dangerous in crimson. The part up to [lit] (0..1 on the scale) is lit, the rest dim, and a marker rides
 * at the level. [t] (0..1) is headphones mode taking the ring over: the scale's top opens by [gapDeg],
 * it slims down to the arcs' stroke and fades out.
 */
private fun DrawScope.levelScale(
    c: StrokeCache, colors: Array<Color>, dim: Array<Color>, lit: Float, level: Float?, t: Float = 0f, gapDeg: Float = 0f,
) {
    val base = bezelW()
    val w = base * (1.5f - 0.5f * t)
    val r = ringRadius(w * (1f + 0.1f * t))
    val fade = 1f - t
    if (fade <= 0.003f) return
    for (i in 0 until 4) {
        val a = ZONE_FRACTIONS[i]
        val b = ZONE_FRACTIONS[i + 1]
        // A hair of gap between zones so the boundaries read.
        val ea = if (i == 0) a else a + 0.004f
        val eb = if (i == 3) b else b - 0.004f
        val d = dim[i]
        splitArc(c, r, ea, eb, gapDeg, if (t == 0f) d else d.copy(alpha = d.alpha * fade), w, round = false)
        val k = colors[i]
        splitArc(c, r, ea, minOf(eb, maxOf(ea, lit)), gapDeg, if (t == 0f) k else k.copy(alpha = fade), w, round = false)
    }
    if (level != null) markerAt(pointAt(r, splitAngle(lit, gapDeg)), colors[zoneIndexOf(level)], fade)
}

/**
 * The live level on its dBA scale, on its own (design-system renders). The part up to [level] is lit, the
 * rest dim, and a marker rides at the level; the zone is also said in words elsewhere, colour is never the
 * only carrier.
 */
@Composable
fun LevelScale(level: Float?, modifier: Modifier = Modifier) {
    val reveal = rememberReveal()
    val shown = follow(level?.let(::gaugeFraction) ?: 0f, "level")
    val c = remember { StrokeCache() }
    val (colors, dim) = rememberScaleColors()
    Canvas(modifier.fillMaxSize().turnWithPager()) {
        levelScale(c, colors, dim, shown.value * reveal.value, level)
    }
}

/**
 * NOW — the ring of the first page: the live level on its scale, and, in headphones mode, two ear-cup arcs.
 *
 * Normally it is the dBA scale ([LevelScale]). As [layer] turns on, the ring opens at the top (where the
 * Moderate zone begins) into two arcs (left and right, with a spring), the scale slims and fades out, and a
 * stereo meter takes its place: both arcs rise from the bottom with the estimated listening level
 * ([listenFraction], 0..1 on the dBA scale, or the week's share of the listening allowance when nothing
 * plays), in indigo (the zone's tier colour when the listening is loud, never colour alone: the zone tag
 * says it too). The outside level is the moon, a small disc in the gap at the top that keeps its own zone
 * colour and number, and that, on its way there, leaves the centre in a half orbit.
 */
@Composable
internal fun NowRing(
    level: Float?,
    layer: LayerProgress,
    listenFraction: Float,
    listenColor: Color,
    outsideDba: Int?,
    outsideTone: VoxTone,
    modifier: Modifier = Modifier,
) {
    val reveal = rememberReveal()
    val shownLevel = follow(level?.let(::gaugeFraction) ?: 0f, "level")
    val shownListen = follow(listenFraction.coerceIn(0f, 1f), "listen")
    val c = remember { StrokeCache() }
    val (colors, dim) = rememberScaleColors()
    val indigo = VColor.SecondaryFill
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().turnWithPager()) {
            val p = layer.ring.value
            if (p <= 0.001f) {
                // The one ring: exactly the level scale.
                levelScale(c, colors, dim, shownLevel.value * reveal.value, level)
                return@Canvas
            }
            // Two arcs: the left one from the bottom-left up to the gap, the right one from the gap down
            // to the bottom-right. Each is (125 - gap/2) degrees long.
            val gap = SPLIT_GAP_DEG * p
            val len = SWEEP / 2 - gap / 2
            val leftStart = START
            val rightStart = START + SWEEP / 2 + gap / 2
            val t = p.coerceIn(0f, 1f)
            val w = bezelW()
            val r = ringRadius(w * 1.1f)
            // The scale opens, slims and fades while the arcs' tracks fade in under the stereo meter.
            levelScale(c, colors, dim, shownLevel.value * reveal.value, level, t, gap)
            val trackColor = VColor.BezelTrack.copy(alpha = VColor.BezelTrack.alpha * t)
            arcDeg(c, r, leftStart, len, trackColor, w)
            arcDeg(c, r, rightStart, len, trackColor, w)
            // The stereo meter: both arcs rise from the bottom with the same level.
            if (t > 0.01f) {
                val up = shownListen.value * reveal.value * len
                val color = listenColor.copy(alpha = t)
                if (up > 0.5f) {
                    // Left: from its bottom end upwards; right: from its bottom end (the arc's end) upwards.
                    arcDeg(c, r, leftStart, up, color.copy(alpha = 0.18f * t), w * 3f)
                    arcDeg(c, r, leftStart, up, color, w * 1.1f)
                    arcDeg(c, r, rightStart + len - up, up, color.copy(alpha = 0.18f * t), w * 3f)
                    arcDeg(c, r, rightStart + len - up, up, color, w * 1.1f)
                    markerAt(pointAt(r, leftStart + up), listenColor.copy(alpha = t))
                    markerAt(pointAt(r, rightStart + len - up), listenColor.copy(alpha = t))
                }
            }
            // Indigo accent ring edge: a hairline on each arc's top end so the split reads as two cups.
            val cap = indigo.copy(alpha = 0.55f * t)
            drawCircle(cap, w * 0.55f, pointAt(r, leftStart + len))
            drawCircle(cap, w * 0.55f, pointAt(r, rightStart))
        }
        Moon(layer, outsideDba, outsideTone)
    }
}

/**
 * THE MOON — the outside level as a small disc in the ring's top gap: a dark disc with a ring in the
 * outside zone's colour and the dBA figure in it. It starts as the centre's outside figure and leaves
 * in a half orbit (angle and radius both grow) while it shrinks, settling at the top; leaving
 * mode reverses it. Ambient or before its stage: not drawn.
 */
@Composable
private fun Moon(layer: LayerProgress, dba: Int?, tone: VoxTone) {
    if (LocalAmbient.current || dba == null) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = minOf(maxWidth, maxHeight)
        val moonSize = d * 0.155f
        val density = LocalDensity.current
        // The moon's figure keeps to the design size at large fonts (13 sp): the number is also in the
        // foot line and in the spoken description, and it must stay inside its disc.
        val fixed = Density(density.density, fontScale = minOf(density.fontScale, 1f))
        val centerPx = with(density) { (d / 2).toPx() }
        // Where it settles: wholly inside the ring, in the gap between the two arcs.
        val restPx = with(density) { (d * 0.352f).toPx() }
        val sizePx = with(density) { moonSize.toPx() }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(moonSize)
                .offset {
                    val m = layer.moon.value
                    // It starts where the outside figure was (just above the centre) and flies up to the top gap
                    // on a curve that swings out to the right: an orbit, not a straight line.
                    val startY = centerPx - centerPx * 0.06f
                    val y = startY + ((centerPx - restPx) - startY) * m - sizePx / 2
                    val x = centerPx + sin(PI * m).toFloat() * centerPx * 0.30f - sizePx / 2
                    IntOffset(x.roundToInt(), y.roundToInt())
                }
                .graphicsLayer {
                    val m = layer.moon.value.coerceAtLeast(0f)
                    // The outside level starts as large as the figure it was, and shrinks into the moon.
                    val s = 1f + 0.7f * (1f - m.coerceIn(0f, 1f))
                    scaleX = s; scaleY = s
                    // There from the first moment it leaves (it stands in for the outside figure); gone once it is home again.
                    alpha = (layer.moon.value * 40f).coerceIn(0f, 1f)
                }
                .clip(androidx.compose.foundation.shape.CircleShape)
                .drawBehind {
                    drawCircle(Color.Black)
                    drawCircle(tone.fill.copy(alpha = 0.22f))
                    drawCircle(tone.fill, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalDensity provides fixed) {
                Text(
                    dba.toString(),
                    style = VType.Label.scaled(figureScaleFor(d)),
                    color = VColor.Text, maxLines = 1, softWrap = false,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------ Dose

/**
 * DOSE — one ring, two pictures that morph into each other as [week] goes from 0 (Today) to 1 (Week).
 *
 * Today: 24 hourly bars around the ring, from the minute ledger: midnight bottom-left, noon at the top,
 * 24:00 bottom-right. The bars hang inward from the shared rim; a bar's length and colour follow that
 * hour's level (40 to 110 dBA); an hour with no measurement is a dot; this hour gets a marker.
 *
 * Week: one segment per day, oldest first and today last (bottom-right). A segment fills in proportion to
 * that day's share of the weekly allowance ([values], 0..1) in the colour of that day's own dose zone
 * ([tones]); a day with measurements always shows at least a stub, so it is never mistaken for a day with
 * none ([measured] false: a thin dotted segment). Today is the one widened and underlined in white.
 *
 * Morphing: the bars retract into the rim one after another round the ring while the day segments fill in
 * behind them, one after another. With reduced motion ([LocalReduceMotion]) nothing moves: the two
 * pictures cross-fade. [week] is read while drawing, so a frame of the morph recomposes nothing.
 */
@Composable
fun DoseMorph(
    hourly: List<Float>,
    nowHour: Int,
    values: List<Float>,
    tones: List<VoxTone>,
    measured: List<Boolean>,
    week: () -> Float,
    modifier: Modifier = Modifier,
) {
    val reveal = rememberReveal()
    val still = LocalReduceMotion.current
    val c = remember { StrokeCache() }
    val colors = remember(hourly) {
        Array(24) { h ->
            val v = hourly.getOrElse(h) { 0f }
            if (v <= 0f) VColor.Track else VColor.zone(RiskZone.of(v.toDouble()))
        }
    }
    Canvas(modifier.fillMaxSize().turnWithPager()) {
        val m = week().coerceIn(0f, 1f)
        if (m < 0.999f) dayBars(c, colors, hourly, nowHour, reveal.value, m, still)
        if (m > 0.001f) weekSegments(c, values, tones, measured, reveal.value, m, still)
    }
}

/** Today's 24 bars. [m] is how far the morph to the week has gone: the bars retract into the rim in turn (or fade, [still]). */
private fun DrawScope.dayBars(c: StrokeCache, colors: Array<Color>, hourly: List<Float>, nowHour: Int, reveal: Float, m: Float, still: Boolean) {
    val d = size.minDimension
    val bar = d * 0.021f
    val r = ringRadius(bar)
    val longest = d * 0.075f
    // A faint full ring on the rim keeps the shape readable where the day has no data.
    ringArc(c, r, 0f, 1f, Color.White.copy(alpha = 0.06f * (1f - m)), bar * 0.35f)
    for (h in 0 until 24) {
        val f = (h + 0.5f) / 24f
        val v = hourly.getOrElse(h) { 0f }
        val grow = (reveal * 1.7f - f * 0.7f).coerceIn(0f, 1f)
        // The bars go in order round the ring: 0 = standing, 1 = gone into the rim. Cross-fading: all together.
        val gone = if (still) m else (m * 1.5f - f * 0.5f).coerceIn(0f, 1f)
        val keep = 1f - gone
        if (keep <= 0.003f) continue
        val len = if (v <= 0f) 0f else (d * 0.014f + (longest - d * 0.014f) * ((v - 40f) / 70f).coerceIn(0.1f, 1f)) * grow * (if (still) 1f else keep)
        drawLine(colors[h].copy(alpha = colors[h].alpha * keep), ringPoint(r, f), ringPoint(r - len, f), strokeWidth = bar, cap = StrokeCap.Round)
    }
    if (nowHour in 0..23) drawCircle(Color.White.copy(alpha = reveal * (1f - m)), bar * 0.6f, ringPoint(r - longest - bar * 1.1f, (nowHour + 0.5f) / 24f))
}

/** The week's seven segments. [m] is how far the morph has gone: they fill in turn as it rises (or fade in, [still]). */
private fun DrawScope.weekSegments(
    c: StrokeCache, values: List<Float>, tones: List<VoxTone>, measured: List<Boolean>, reveal: Float, m: Float, still: Boolean,
) {
    val w = bezelW() * 1.6f
    val r = ringRadius(w * 1.25f)
    val n = values.size.coerceAtLeast(1)
    val gap = 0.034f
    val seg = (1f - gap * (n - 1)) / n
    // How far the segments have been drawn in: the page's draw-in and the morph, one after another. Cross-fading: all at once.
    val drawn = if (still) reveal else reveal * m
    val alpha = if (still) m else 1f
    for (i in 0 until n) {
        val a = i * (seg + gap)
        val today = i == n - 1
        val has = measured.getOrElse(i) { true }
        val sw = if (today) w * 1.25f else w
        if (has) {
            ringArc(c, r, a, a + seg, Color.White.copy(alpha = 0.2f * alpha), sw)
            val p = (drawn * (n + 1.5f) - i).coerceIn(0f, 1f)
            val v = values.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            // At least a stub: the day was measured, however quiet.
            val fill = (maxOf(v, 0.10f) * p).coerceAtMost(1f)
            val tone = tones.getOrElse(i) { VColor.accent }.fill
            ringArc(c, r, a, a + seg * fill, tone.copy(alpha = tone.alpha * alpha), sw)
        } else {
            // No measurement that day: a thin dotted line, clearly not a bar at zero.
            val dots = 4
            for (k in 0 until dots) {
                val f = a + seg * (k + 0.5f) / dots
                drawCircle(Color.White.copy(alpha = 0.30f * drawn * alpha), w * 0.11f, ringPoint(r, f))
            }
        }
    }
    if (values.isNotEmpty()) {
        // Today, marked: a bright underline just inside its segment, so the last one is never mistaken for an empty day.
        val a = (n - 1) * (seg + gap)
        ringArc(c, r - w * 0.62f - bezelW() * 0.9f, a, a + seg, Color.White.copy(alpha = 0.9f * drawn * alpha), bezelW() * 0.32f)
    }
}

// ------------------------------------------------------------------------------------ labels

/** A page's identity: its icon and short name in its accent, sentence case. */
@Composable
fun PageName(name: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
    val ambient = LocalAmbient.current
    // The name follows the font size up to 1.5x: past that it would not fit the gap above the page dots.
    val scale = LocalDensity.current.fontScale
    val base = VType.Label.heroScaled()
    val style = if (scale <= 1.5f) base else base.copy(
        fontSize = base.fontSize * (1.5f / scale), lineHeight = base.lineHeight * (1.5f / scale),
    )
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, contentDescription = null, tint = if (ambient) VColor.TextSecondary else accent, modifier = Modifier.size(14.dp * LocalTextScale.current))
        Text(
            name, style = style, color = if (ambient) VColor.TextSecondary else accent,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
