package com.example.voxara.phone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.R
import com.example.voxara.phone.model.ZoneSegment
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.wordRes
import kotlin.math.roundToInt

/** Bar height per zone, so the strip reads without colour: taller = louder. */
private fun zoneHeight(z: RiskZone): Float = when (z) {
    RiskZone.OK -> 0.4f
    RiskZone.MODERATE -> 0.6f
    RiskZone.LOUD -> 0.8f
    RiskZone.DANGEROUS -> 1f
}

/** Legend of the zones present: icon + word, never colour alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZoneLegend(zones: Collection<RiskZone>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RiskZone.entries.filter { it in zones }.forEach { ZoneBadge(it) }
    }
}

/**
 * Today minute by minute: each run of minutes drawn in its zone's colour AND height (unmeasured
 * minutes are a low grey line), with a "now" marker.
 */
@Composable
fun ZoneStrip(segments: List<ZoneSegment>, nowMinute: Int, description: String, modifier: Modifier = Modifier) {
    val c = Vox.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(40.dp).clearAndSetSemantics { contentDescription = description }
        ) {
            val w = size.width
            val h = size.height
            fun x(m: Int) = w * m / 1440f
            drawRoundRect(c.surface2, Offset(0f, h - 3.dp.toPx()), Size(w, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
            for (s in segments) {
                val z = s.zone ?: continue
                val bh = h * zoneHeight(z)
                drawRect(c.zoneFill(z), Offset(x(s.fromMinute), h - bh), Size(maxOf(1f, x(s.toMinute) - x(s.fromMinute)), bh))
            }
            for (hour in listOf(6, 12, 18)) {
                drawLine(c.outline, Offset(x(hour * 60), 0f), Offset(x(hour * 60), h), 1.dp.toPx())
            }
            val nx = x(nowMinute.coerceIn(0, 1440))
            drawLine(c.text, Offset(nx, 0f), Offset(nx, h), 2.dp.toPx(), cap = StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = c.text3)
            }
        }
        ZoneLegend(segments.mapNotNull { it.zone }.toSet())
    }
}

/** 24 hourly levels (0 = not measured): bar height is the level, colour and legend the zone. */
@Composable
fun HourlyChart(hourly: List<Float>, currentHour: Int?, description: String, modifier: Modifier = Modifier) {
    val c = Vox.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(160.dp).clearAndSetSemantics { contentDescription = description }) {
            val n = 24
            val gap = 3.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            val floor = 30f
            val top = 110f
            for (h in 0 until n) {
                val v = hourly.getOrElse(h) { 0f }
                val x = h * (bw + gap)
                if (v <= 0f) {
                    drawRoundRect(c.surface2, Offset(x, size.height - 4.dp.toPx()), Size(bw, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                    continue
                }
                val frac = ((v - floor) / (top - floor)).coerceIn(0.04f, 1f)
                val bh = size.height * frac
                drawRoundRect(
                    c.zoneFill(RiskZone.of(v.toDouble())), Offset(x, size.height - bh), Size(bw, bh),
                    CornerRadius(minOf(bw / 2, 6.dp.toPx())),
                )
                if (h == currentHour) {
                    drawCircle(c.text, 2.5.dp.toPx(), Offset(x + bw / 2, size.height - bh - 6.dp.toPx()))
                }
            }
            // 85 dBA guide: the level the daily limit is built on.
            val y85 = size.height * (1f - (85f - floor) / (top - floor))
            drawLine(c.text3, Offset(0f, y85), Offset(size.width, y85), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = c.text3) }
        }
        ZoneLegend(hourly.filter { it > 0f }.map { RiskZone.of(it.toDouble()) }.toSet())
    }
}

/**
 * Daily dose bars (oldest first) with the 100% line dashed. Height is the dose; colour and the
 * legend give the zone.
 */
@Composable
fun DoseBars(values: List<Double>, labels: List<String>, description: String, modifier: Modifier = Modifier) {
    val c = Vox.colors
    val maxV = maxOf(120.0, values.maxOrNull() ?: 0.0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(170.dp).clearAndSetSemantics { contentDescription = description }) {
            val n = values.size.coerceAtLeast(1)
            val gap = if (n > 14) 2.dp.toPx() else 6.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            values.forEachIndexed { i, v ->
                val x = i * (bw + gap)
                val bh = (size.height * (v / maxV)).toFloat().coerceAtLeast(if (v > 0) 3.dp.toPx() else 2.dp.toPx())
                val color = if (v > 0) c.zoneFill(RiskZone.ofPercent(v)) else c.surface2
                drawRoundRect(color, Offset(x, size.height - bh), Size(bw, bh), CornerRadius(minOf(bw / 2, 6.dp.toPx())))
            }
            val y100 = (size.height * (1 - 100 / maxV)).toFloat()
            drawLine(c.text2, Offset(0f, y100), Offset(size.width, y100), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = c.text3) }
            }
        }
        Text(stringResource(R.string.trends_limit_line), style = MaterialTheme.typography.labelSmall, color = c.text2)
        ZoneLegend(values.filter { it > 0 }.map { RiskZone.ofPercent(it) }.toSet())
    }
}

/** The rolling budget as a line, 100% dashed. */
@Composable
fun BudgetLine(values: List<Double>, description: String, modifier: Modifier = Modifier) {
    val c = Vox.colors
    val maxV = maxOf(120.0, values.maxOrNull() ?: 0.0)
    Canvas(modifier.fillMaxWidth().height(140.dp).clearAndSetSemantics { contentDescription = description }) {
        if (values.size < 2) return@Canvas
        val step = size.width / (values.size - 1)
        fun y(v: Double) = (size.height * (1 - v / maxV)).toFloat()
        val line = Path().apply {
            moveTo(0f, y(values[0]))
            values.drop(1).forEachIndexed { i, v -> lineTo((i + 1) * step, y(v)) }
        }
        val area = Path().apply {
            addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(area, c.brand.copy(alpha = 0.12f))
        drawPath(line, c.brand, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val y100 = y(100.0)
        drawLine(c.text2, Offset(0f, y100), Offset(size.width, y100), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        val last = values.last()
        drawCircle(c.zoneFill(RiskZone.ofPercent(last)), 5.dp.toPx(), Offset(size.width, y(last)))
    }
}

/** Two-part bar: surroundings vs headphones, each labelled with its share. */
@Composable
fun SplitBar(headphoneShare: Double, modifier: Modifier = Modifier) {
    val c = Vox.colors
    val hp = (headphoneShare * 100).roundToInt().coerceIn(0, 100)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
            Canvas(Modifier.fillMaxWidth().height(14.dp).clearAndSetSemantics { }) {
                val split = size.width * (1 - hp / 100f)
                drawRect(c.brand, Offset.Zero, Size(split, size.height))
                drawRect(c.zoneFill(RiskZone.MODERATE), Offset(split, 0f), Size(size.width - split, size.height))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(VoxIcons.Waves, null, tint = c.brand, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.split_ambient, 100 - hp), style = MaterialTheme.typography.labelMedium, color = c.text)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(VoxIcons.Headphones, null, tint = c.zoneInk(RiskZone.MODERATE), modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.split_headphones, hp), style = MaterialTheme.typography.labelMedium, color = c.text)
            }
        }
    }
}

/** Zone word for the TalkBack summaries. */
@Composable
fun zoneWordText(z: RiskZone?): String =
    if (z == null) stringResource(R.string.zone_not_measured) else stringResource(z.wordRes())
