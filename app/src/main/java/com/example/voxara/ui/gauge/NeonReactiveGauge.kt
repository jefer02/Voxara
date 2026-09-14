package com.example.voxara.ui.gauge

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TWO_PI = (2.0 * PI).toFloat()
private const val TICKS = 72

/** Risk ramp: neon green holds to the WHO line, then amber, incandescent, magenta. */
private val RAMP = listOf(
    30f to Color(0xFF2BFF88),
    79f to Color(0xFF2BFF88),
    85f to Color(0xFFFFC531),   // NIOSH REL
    95f to Color(0xFFFF8A1F),
    106f to Color(0xFFFF2E6B),
    120f to Color(0xFFFF1050),
)

fun levelColor(dba: Float): Color {
    val i = RAMP.indexOfLast { dba >= it.first }.coerceIn(0, RAMP.size - 2)
    val (l0, c0) = RAMP[i]
    val (l1, c1) = RAMP[i + 1]
    return lerp(c0, c1, ((dba - l0) / (l1 - l0)).coerceIn(0f, 1f))
}

/**
 * The Neon Reactive Gauge.
 *
 * @param dba live A-weighted level straight off the DSP chain (100 ms frames)
 * @param dosePercent NIOSH daily dose, 0..100+
 * @param ambient always-on display: no glow, hairline geometry, under 5% pixels lit
 * @param fluid draw the organic aura (ring style "both" / "fluid")
 * @param ticks draw the 72-tick ring (ring style "both" / "ticks")
 */
@Composable
fun NeonReactiveGauge(
    dba: Float,
    dosePercent: Float,
    modifier: Modifier = Modifier,
    ambient: Boolean = false,
    fluid: Boolean = true,
    ticks: Boolean = true,
    glow: Float = 1f,
) {
    // The eye hates raw frames: spring the level, tween the dose.
    val level by animateFloatAsState(
        targetValue = dba,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "level",
    )
    val dose by animateFloatAsState(
        targetValue = dosePercent,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "dose",
    )

    // Colour rises fast and forgives slowly — the ring is reluctant to say you are safe.
    val tint by animateColorAsState(
        targetValue = levelColor(level),
        animationSpec = tween(if (level > 85f) 300 else 1400, easing = FastOutSlowInEasing),
        label = "tint",
    )

    val intensity = ((level - 40f) / 76f).coerceIn(0f, 1f)
    // Louder room, faster breath: 5.2 s calm down to 1.8 s critical. Never faster than 1.2 Hz.
    val phase by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = (5200 - 3400 * intensity).toInt(), easing = LinearEasing)
        ),
        label = "phase",
    )

    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        if (!ambient) {
            drawWash(c, r, tint, intensity)
            if (fluid) drawAura(c, r, tint, intensity, phase, glow)
        }
        if (ticks) drawTickRing(c, r, level, ambient)
        drawDoseArc(c, r, dose, tint, ambient)
    }
}

private fun DrawScope.drawWash(c: Offset, r: Float, tint: Color, i: Float) = drawCircle(
    brush = Brush.radialGradient(
        0f to tint.copy(alpha = 0.09f + 0.06f * i),
        0.62f to tint.copy(alpha = 0.02f),
        1f to Color.Transparent,
        center = c,
        radius = r,
    ),
    radius = r,
    center = c,
)

/** The organic part: three harmonics deform a circle, blurred into an aura. */
private fun DrawScope.drawAura(c: Offset, r: Float, tint: Color, i: Float, phase: Float, glow: Float) {
    val base = r * (0.47f + 0.11f * i) * (1f + 0.035f * sin(phase))
    val amp = r * (0.012f + 0.08f * i)
    val path = Path()
    val step = TWO_PI / 160f
    var a = 0f
    while (a <= TWO_PI) {
        val rr = base + amp * (
            sin(a * 3f + phase * 1.7f) +
                0.6f * sin(a * 5f - phase * 2.4f) +
                0.8f * sin(a * 2f + phase)
            )
        val x = c.x + cos(a) * rr
        val y = c.y + sin(a) * rr
        if (a == 0f) path.moveTo(x, y) else path.lineTo(x, y)
        a += step
    }
    path.close()

    drawIntoCanvas { canvas ->
        val paint = Paint()
        paint.asFrameworkPaint().apply {
            isAntiAlias = true
            color = tint.copy(alpha = 0.10f + 0.20f * i).toArgb()
            // Non-negotiable 03: glow is a shadow, not a fill. blurRadius <= 18 dp.
            maskFilter = BlurMaskFilter(
                (r * (0.05f + 0.16f * i) * glow).coerceIn(1f, 18.dp.toPx()),
                BlurMaskFilter.Blur.NORMAL,
            )
        }
        canvas.drawPath(path, paint)
    }
}

/** 72 ticks, lit proportionally from 30 to 120 dBA, each tick carrying its own band colour. */
private fun DrawScope.drawTickRing(c: Offset, r: Float, level: Float, ambient: Boolean) {
    val lit = (TICKS * ((level - 30f) / 90f).coerceIn(0f, 1f)).roundToInt()
    repeat(TICKS) { idx ->
        val a = -PI.toFloat() / 2f + idx * TWO_PI / TICKS
        val major = idx % 6 == 0
        val on = idx < lit
        // Ambient: hairline geometry only, major ticks, under 5% of pixels lit.
        if (ambient && !major && !on) return@repeat
        val outer = r * when {
            on -> 0.962f
            major -> 0.935f
            else -> 0.922f
        }
        val color = when {
            ambient -> Color.White.copy(alpha = if (on) 0.55f else 0.14f)
            on -> levelColor(30f + idx * 90f / TICKS)
                .copy(alpha = 0.5f + 0.5f * (idx / lit.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f))
            else -> Color.White.copy(alpha = 0.085f)
        }
        drawLine(
            color = color,
            start = Offset(c.x + cos(a) * r * 0.855f, c.y + sin(a) * r * 0.855f),
            end = Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
            strokeWidth = if (ambient) 1.dp.toPx() else if (major) 2.4.dp.toPx() else 1.3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Dose as a sweep with a travelling head — the only element that never resets on its own. */
private fun DrawScope.drawDoseArc(c: Offset, r: Float, dose: Float, tint: Color, ambient: Boolean) {
    val rad = r * 0.785f
    val topLeft = Offset(c.x - rad, c.y - rad)
    val box = Size(rad * 2f, rad * 2f)
    val stroke = Stroke(width = if (ambient) 2.dp.toPx() else r * 0.032f, cap = StrokeCap.Round)

    drawArc(
        color = Color.White.copy(alpha = 0.055f),
        startAngle = 0f, sweepAngle = 360f, useCenter = false,
        topLeft = topLeft, size = box, style = stroke,
    )

    val sweep = 360f * (dose / 100f).coerceIn(0f, 1f)
    if (sweep < 0.5f) return
    drawArc(
        brush = Brush.sweepGradient(
            listOf(tint.copy(alpha = 0.28f), tint, tint),
            center = c,
        ),
        startAngle = -90f, sweepAngle = sweep, useCenter = false,
        topLeft = topLeft, size = box, style = stroke,
    )
    if (!ambient) {
        val head = (-90f + sweep) * PI.toFloat() / 180f
        drawCircle(
            color = Color.White,
            radius = r * 0.022f,
            center = Offset(c.x + cos(head) * rad, c.y + sin(head) * rad),
        )
    }
}
