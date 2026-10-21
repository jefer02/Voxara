package com.example.voxara.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.voxara.core.design.Accent
import com.example.voxara.core.design.Palette
import com.example.voxara.core.risk.RiskZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * TEMPLATE VISUALS SHARED BY BOTH APPS (docs/design/STYLE.md §4–5): colour roles for Compose, the
 * level-reactive accent, the wave clock, wave lines, harmonic rings, the capsule meter and the
 * pulsing dot. Drawing reuses one Path per visual (rewind, never re-allocate) so an animated frame
 * allocates nothing; every animation stops when the caller says so or the screen is not resumed.
 */

/** A colour role in Compose: [fill] for graphics, [ink] for text and icons, [onFill] for text on the fill. */
@Immutable
data class VoxTone(val fill: Color, val ink: Color, val onFill: Color) {
    /** Badge, icon-tile and nav-pill background. */
    val soft: Color get() = fill.copy(alpha = Palette.SOFT_ALPHA)

    /** Glows: number shadow, switch glow, halo. */
    val glow: Color get() = fill.copy(alpha = Palette.GLOW_ALPHA)

    companion object {
        fun of(t: Palette.Tone) = VoxTone(Color(t.fill), Color(t.ink), Color(t.onFill))

        /** The chosen accent in a theme. */
        fun accent(a: Accent, dark: Boolean) = of(Palette.accent(a, dark))

        /** The level-reactive accent: accent for OK/MODERATE, amber for LOUD, crimson for DANGEROUS. */
        fun level(zone: RiskZone, a: Accent, dark: Boolean) = of(Palette.level(zone, a, dark))
    }
}

/** [target] with its colours animated between zones (400 ms; instant when [instant]). */
@Composable
fun animateTone(target: VoxTone, instant: Boolean): VoxTone {
    val spec = if (instant) snap<Color>() else tween(400)
    val fill by animateColorAsState(target.fill, spec, label = "toneFill")
    val ink by animateColorAsState(target.ink, spec, label = "toneInk")
    return VoxTone(fill, ink, target.onFill)
}

/** True while [enabled] and the screen is resumed (visible and interactive). */
@Composable
fun animationsActive(enabled: Boolean): Boolean {
    if (!enabled) return false
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/** Waves repeat after this phase: every multiplier used below (1, 1.25, 1.3, 1.5) divides it whole. */
private const val PHASE_PERIOD = (2 * PI * 20).toFloat()

/**
 * THE WAVE CLOCK — a phase that advances with the frame clock only while [running]; otherwise it
 * stays at [still], so the waves are drawn as a static frame. Read the returned State inside a draw
 * lambda only: the phase then redraws the Canvas without recomposing anything.
 * [speed] is in radians per second (the template's 0.032 + 0.055·intensity per 60 fps frame).
 */
@Composable
fun rememberWavePhase(running: Boolean, speed: Float, still: Float = 0.9f): State<Float> {
    val phase = remember { mutableFloatStateOf(still) }
    val currentSpeed by rememberUpdatedState(speed)
    val active = animationsActive(running)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                phase.floatValue = (phase.floatValue + (now - last) / 1e9f * currentSpeed) % PHASE_PERIOD
                last = now
            }
        }
    }
    return phase
}

/** Template wave speed for a 0..1 intensity, in radians per second. */
fun waveSpeed(intensity: Float): Float = 1.9f + 3.3f * intensity.coerceIn(0f, 1f)

/**
 * WAVE LINES — the phone hero's three horizontal sine lines with a soft envelope at the edges.
 * [intensity] 0..1 comes from real data (today's dose); the first line is stronger.
 */
class WaveLines {
    private val path = Path()
    private var strong = Stroke(0f)
    private var faint = Stroke(0f)

    fun draw(scope: DrawScope, phase: Float, intensity: Float, color: Color) = with(scope) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@with
        val strongPx = 2.dp.toPx()
        if (strong.width != strongPx) {
            strong = Stroke(strongPx, cap = StrokeCap.Round)
            faint = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round)
        }
        val i = intensity.coerceIn(0.05f, 1f)
        val unit = h / 210f                       // the template's canvas was 340 × 210
        val freq = 0.018f * 340f / w
        val step = w / 60f
        for (k in 0 until 3) {
            path.rewind()
            val amp = (6f + i * 24f) * (1f - k * 0.22f) * unit
            var x = 0f
            var first = true
            while (x <= w + 0.5f) {
                val env = sin(x / w * PI.toFloat())
                val y = h * 0.56f + k * 6f * unit +
                    sin(x * freq + phase * (1f + k * 0.25f)) * amp * env +
                    cos(x * freq * 1.6f - phase) * amp * 0.4f * env
                if (first) path.moveTo(x, y) else path.lineTo(x, y)
                first = false
                x += step
            }
            drawPath(path, color.copy(alpha = if (k == 0) 0.27f else 0.125f), style = if (k == 0) strong else faint)
        }
    }
}

/**
 * HARMONIC RINGS + HALO — the watch's closed, sine-deformed rings around the main number and the
 * radial halo under them. [intensity] 0..1 comes from the live level. [rings] 3 (4 while listening
 * on headphones, with a finer pattern, as in the template). [strength] scales the ring opacity (the
 * watch lowers it behind text). [ringColor] tints the rings apart from the halo (headphones mode shifts the rings
 * to indigo while the halo keeps its colour, so no brush is rebuilt per frame). [morph] 0..1 turns the organic rings into a stereo, left-right shape
 * (headphones mode): two lobes with a waist at the top and bottom, the left half mirroring the right.
 */
class HarmonicRings {
    private val path = Path()
    private var lastHaloColor = Color.Unspecified
    private var lastHaloRadius = 0f
    private var halo: Brush? = null
    private var strong = Stroke(0f)
    private var faint = Stroke(0f)

    fun draw(
        scope: DrawScope, phase: Float, intensity: Float, color: Color, rings: Int = 3, fine: Boolean = false,
        strength: Float = 1f, morph: Float = 0f, ringColor: Color = color,
    ) = with(scope) {
        val r0 = size.minDimension / 2
        if (r0 <= 0f) return@with
        val c = center
        val unit = r0 / 173f                      // the template's watch canvas was 346 px
        // Halo: rebuilt only when its colour or size changes (it does not move with the phase).
        val haloRadius = 156f * unit
        if (color != lastHaloColor || haloRadius != lastHaloRadius) {
            halo = Brush.radialGradient(
                0f to color.copy(alpha = 0.16f), 0.65f to color.copy(alpha = 0.04f), 1f to Color.Transparent,
                center = c, radius = haloRadius,
            )
            lastHaloColor = color
            lastHaloRadius = haloRadius
        }
        drawCircle(halo!!, haloRadius, c)
        val strongPx = 2.2f * unit
        if (strong.width != strongPx) {
            strong = Stroke(strongPx)
            faint = Stroke(1.25f * unit)
        }
        val i = intensity.coerceIn(0.05f, 1f)
        val points = 96
        for (r in 0 until rings) {
            path.rewind()
            // Rings spread from about half the radius to the edge, so they fill the space inside the bezel.
            val base = (92f + r * 17f) * unit
            val amp = (5f + i * 17f) * (1f - r * 0.18f) * unit
            val freq = if (fine) 6f + r * 2f else 4f + r
            for (p in 0..points) {
                val a = p * (2 * PI.toFloat()) / points
                val ca = cos(a)
                // Stereo: the waves are drawn on the mirrored angle (the left ear echoes the right one)
                // and the ring swells into two lobes along the horizontal axis.
                val wa = if (morph > 0f) a + ((if (ca >= 0f) a else PI.toFloat() - a) - a) * morph else a
                val lobe = if (morph > 0f) 1f + morph * (-0.22f + 0.40f * abs(ca).pow(1.3f)) else 1f
                val radius = (base * lobe) + sin(wa * freq + phase + r) * amp + cos(wa * (freq - 2f) - phase * 1.3f) * amp * 0.45f
                val x = c.x + ca * radius
                val y = c.y + sin(a) * radius
                if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, ringColor.copy(alpha = (if (r == 0) 0.58f else 0.22f) * strength), style = if (r == 0) strong else faint)
        }
    }
}

/** How many of [count] capsules are lit for [fraction] 0..1. */
fun capsulesLit(fraction: Float, count: Int): Int = (fraction.coerceIn(0f, 1f) * count).roundToInt().coerceIn(0, count)

/**
 * SEGMENTED CAPSULE METER — [count] fully rounded capsules; the first [lit] take [colorAt] (the
 * tier of the zone each capsule stands for), the last lit one is 16 % taller, the rest are
 * [inactive] and 14 % shorter. Capsules stay slim (at most [maxWidth]) and spread over wide
 * screens, as in the template.
 */
fun DrawScope.drawCapsules(
    count: Int, lit: Int, inactive: Color, gap: Dp = 4.dp, maxWidth: Dp = 10.dp, colorAt: (Int) -> Color,
) {
    if (count <= 0) return
    val g = gap.toPx()
    val w = minOf((size.width - g * (count - 1)) / count, maxWidth.toPx())
    val step = if (count > 1) (size.width - w) / (count - 1) else 0f
    val base = size.height / 1.16f
    for (i in 0 until count) {
        val on = i < lit
        val h = base * when {
            on && i == lit - 1 -> 1.16f
            on -> 1f
            else -> 0.86f
        }
        drawRoundRect(
            color = if (on) colorAt(i) else inactive,
            topLeft = Offset(i * step, (size.height - h) / 2),
            size = Size(w, h),
            cornerRadius = CornerRadius(w / 2),
        )
    }
}

/** The editorial header's pulsing dot (static when [animate] is false). */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, size: Dp = 6.dp, animate: Boolean = true) {
    val active = animationsActive(animate)
    // States are read in the draw lambda, so the pulse redraws without recomposing.
    val pulse: State<Float>? = if (active) {
        rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p")
    } else null
    Canvas(modifier.size(size * 1.5f)) {
        val p = pulse?.value ?: 0f
        drawCircle(color.copy(alpha = color.alpha * (1f - 0.45f * p)), radius = size.toPx() / 2 * (1f + 0.45f * p))
    }
}
