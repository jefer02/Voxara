package com.example.voxara.ui.gauge

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** A free-running clock in seconds, shared by every time-driven gauge. */
@Composable
fun rememberSeconds(periodSeconds: Int = 60): Float {
    val t by rememberInfiniteTransition(label = "clock").animateFloat(
        initialValue = 0f,
        targetValue = periodSeconds.toFloat(),
        animationSpec = infiniteRepeatable(
            tween(periodSeconds * 1000, easing = LinearEasing)
        ),
        label = "t",
    )
    return t
}

/**
 * CONCERT MODE — true-black field, lit pixels only. Spectrum bars instead of fills, so an
 * OLED panel draws a fraction of the current.
 */
@Composable
fun ConcertSpectrum(
    dba: Float,
    modifier: Modifier = Modifier,
    glow: Float = 1f,
) {
    val t = rememberSeconds()
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f
        val n = 84
        val lvl = ((dba - 60f) / 55f).coerceIn(0f, 1f)
        val beat = max(0f, sin(t * 3.1f)).pow(6)

        for (i in 0 until n) {
            val a = -PI.toFloat() / 2f + i.toFloat() / n * 2f * PI.toFloat()
            val band = i.toFloat() / n
            val e = 0.22f + 0.78f * abs(
                sin(band * 9f + t * 2.2f) * 0.5f +
                    sin(band * 23f - t * 5.7f) * 0.3f +
                    sin(t * 1.3f + band) * 0.4f
            )
            val len = r * (0.10f + 0.34f * e * (0.35f + lvl) + 0.10f * beat * (1f - band))
            val r1 = r * 0.44f
            val tint = levelColor(70f + e * (dba - 62f))
            drawLine(
                color = tint.copy(alpha = (0.35f + 0.65f * e).coerceIn(0f, 1f)),
                start = Offset(cx + cos(a) * r1, cy + sin(a) * r1),
                end = Offset(cx + cos(a) * (r1 + len), cy + sin(a) * (r1 + len)),
                strokeWidth = 2.6.dp.toPx() * glow.coerceIn(0.6f, 1.4f),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = Color.White.copy(alpha = 0.07f),
            radius = r * 0.40f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

/**
 * VOICE / AI — the vector wave tracks your own amplitude in real time. The wave is your voice,
 * not a loading spinner.
 */
@Composable
fun VoiceWave(
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val t = rememberSeconds()
    Canvas(modifier) {
        val cy = size.height / 2f
        val n = 38
        val gap = size.width * 0.82f / n
        val x0 = size.width / 2f - (n - 1) * gap / 2f
        val drive = amplitude.coerceIn(0f, 1f)
        for (i in 0 until n) {
            val p = i.toFloat() / (n - 1)
            val win = sin(p * PI.toFloat()).pow(0.7f)
            val amp = win * (0.10f + 0.34f * abs(
                sin(t * 7.4f + i * 0.55f) * 0.6f + sin(t * 3.1f - i * 0.23f) * 0.4f
            )) * size.height * (0.45f + 0.75f * drive)
            val tint = if (i % 7 == 0) Color(0xFF35E8FF) else Color(0xFF2BFF88)
            drawLine(
                color = tint.copy(alpha = (0.55f + 0.45f * win).coerceIn(0f, 1f)),
                start = Offset(x0 + i * gap, cy - amp / 2f),
                end = Offset(x0 + i * gap, cy + amp / 2f),
                strokeWidth = max(2.dp.toPx(), gap * 0.34f),
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * DAY LEDGER — 24 hourly spikes on a polar clock. Loud hours glow; quiet hours stay hairline.
 */
@Composable
fun DayPolarClock(
    profile: List<Float>,
    modifier: Modifier = Modifier,
    ambient: Boolean = false,
) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = r * 0.34f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx()),
        )
        profile.take(24).forEachIndexed { i, v ->
            val a = -PI.toFloat() / 2f + i.toFloat() / 24f * 2f * PI.toFloat()
            val e = ((v - 28f) / 70f).coerceIn(0f, 1f)
            val r1 = r * 0.36f
            val len = r * 0.52f * e
            if (len <= 0.5f) return@forEachIndexed
            val tint = levelColor(v)
            drawLine(
                color = if (ambient) Color.White.copy(alpha = if (v >= 80f) 0.6f else 0.18f)
                else tint.copy(alpha = if (v >= 80f) 1f else 0.55f),
                start = Offset(cx + cos(a) * r1, cy + sin(a) * r1),
                end = Offset(cx + cos(a) * (r1 + len), cy + sin(a) * (r1 + len)),
                strokeWidth = if (ambient) 2.dp.toPx() else 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * TILE / DOSE — segmented dose donut, 30 ticks = 30 clicks of the day.
 * Renders from cached state; never wakes the mic.
 */
@Composable
fun DoseDonut(
    dosePercent: Float,
    dba: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f
        val n = 30
        val lit = (n * (dosePercent / 100f).coerceIn(0f, 1f)).roundToInt()
        val radius = r * 0.76f
        val stroke = Stroke(width = r * 0.14f, cap = StrokeCap.Butt)
        val topLeft = Offset(cx - radius, cy - radius)
        val box = Size(radius * 2f, radius * 2f)
        val segment = 360f / n
        val padDeg = 1.4f
        for (i in 0 until n) {
            val start = -90f + i * segment + padDeg
            val sweep = segment - padDeg * 2f
            drawArc(
                color = if (i < lit) levelColor(80f + (i.toFloat() / n) * 30f).copy(alpha = 0.95f)
                else Color.White.copy(alpha = 0.08f),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = box,
                style = stroke,
            )
        }
        if (dba >= 80f) {
            drawCircle(
                color = levelColor(dba).copy(alpha = 0.10f),
                radius = radius * 0.82f,
                center = Offset(cx, cy),
            )
        }
    }
}
