package com.example.voxara.phone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.R
import com.example.voxara.phone.model.DayFigure
import com.example.voxara.phone.model.ZoneSegment
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.theme.wordsStyle
import com.example.voxara.ui.design.CappedFontScale
import com.example.voxara.ui.design.wordRes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * THE CHARTS (docs/design/STYLE.md §6, the dataviz rules on Compose Canvas):
 *  - thin, capped marks: a column is at most 14–28 dp wide, rounded only at its data end, flat on the baseline;
 *  - guides are solid hairlines, never dashed, and their label sits BESIDE the line (in the gutter), never on it;
 *  - direct labels only where they help (the loudest hour, every value of a 7-column chart, the end of a line);
 *  - one series needs no legend; the zones are icon + word, never colour alone;
 *  - the future is quiet (a hairline, no marks) and the past without data is a short grey stub;
 *  - every chart has a spoken twin (the description the caller passes) and most have the table next to them;
 *  - they draw in once when the screen opens and stand still when motion is reduced.
 * Text drawn on a canvas is held at 1.3 times the font scale; the same facts are always also real text beside the chart.
 */

// ------------------------------------------------------------------------------------ helpers

/** Measures text for a canvas at the capped font scale. */
@Stable
class ChartInk(private val measurer: TextMeasurer, private val density: Density) {
    fun text(s: String, style: androidx.compose.ui.text.TextStyle = AnnotationStyle): TextLayoutResult =
        measurer.measure(s, style, maxLines = 1, softWrap = false, density = density)
}

@Composable
fun rememberChartInk(): ChartInk {
    val measurer = rememberTextMeasurer()
    val density = cappedDensity()
    return remember(measurer, density.density, density.fontScale) { ChartInk(measurer, density) }
}

/** The fill a zone takes in a chart: the accent for OK and MODERATE, amber for LOUD, crimson for DANGEROUS. */
@Composable
fun zoneChartFill(zone: RiskZone): Color = Vox.colors.zoneFill(zone)

/** A column rounded at its data end only, flat on the baseline (a 4 dp cap, or half the width if narrower). */
private fun DrawScope.capped(color: Color, left: Float, baseline: Float, width: Float, height: Float, radius: Float) {
    if (height <= 0f) return
    val r = min(radius, min(width / 2f, height))
    val p = Path().apply {
        addRoundRect(
            RoundRect(
                left, baseline - height, left + width, baseline,
                CornerRadius(r), CornerRadius(r), CornerRadius.Zero, CornerRadius.Zero,
            ),
        )
    }
    drawPath(p, color)
}

private fun DrawScope.hairline(color: Color, x0: Float, x1: Float, y: Float) = drawLine(color, Offset(x0, y), Offset(x1, y), 1.dp.toPx())

private fun ease(t: Float): Float = 1f - (1f - t.coerceIn(0f, 1f)).let { it * it * it }

/** The grow-in of column [i] of [n]: each starts a little after the one before. */
private fun stagger(progress: Float, i: Int, n: Int): Float = ease((progress * 1.6f - i.toFloat() / max(1, n) * 0.6f))

/** Legend of the zones present: icon + word, never colour alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZoneLegend(zones: Collection<RiskZone>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RiskZone.entries.filter { it in zones }.forEach { ZoneWord(it, sizeSp = 13) }
    }
}

// ------------------------------------------------------------------------------------ hours

/**
 * THE DAY HOUR BY HOUR — 24 thin columns, height = the hour's level, colour = its zone. The 85 dBA guide (the level the
 * daily limit is built on) is one solid hairline with its label above the line at the start. Hours still to come are a
 * quiet tick, past hours without data a short grey stub, the loudest hour wears a dot, the current hour a notch.
 */
@Composable
fun HourColumns(
    hourly: List<Float>,
    nowHour: Int?,
    description: String,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 140.dp,
) {
    val c = Vox.colors
    val ink = rememberChartInk()
    val density = LocalDensity.current
    val axisHeight = with(density) { ink.text("0").size.height.toDp() } + 6.dp
    val grow by rememberDrawIn()
    val loudest = hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0f }?.index
    val axis = listOf(0 to "0", 6 to "6", 12 to "12", 18 to "18")
    val guideLabel = stringResource(R.string.chart_guide_85)
    Canvas(modifier.fillMaxWidth().height(plotHeight + axisHeight).clearAndSetSemantics { contentDescription = description }) {
        val n = 24
        val slot = size.width / n
        val bw = min(14.dp.toPx(), slot * 0.62f)
        val base = plotHeight.toPx()
        val floor = 30f
        val top = 110f
        fun y(v: Float) = base * (1f - ((v - floor) / (top - floor)).coerceIn(0f, 1f))
        // the guide, then the marks over it
        val y85 = y(85f)
        hairline(c.outline, 0f, size.width, y85)
        val gl = ink.text(guideLabel)
        drawText(gl, c.text2, Offset(0f, y85 - gl.size.height - 2.dp.toPx()))
        hairline(c.outline, 0f, size.width, base)
        for (h in 0 until n) {
            val x = h * slot + (slot - bw) / 2
            val v = hourly.getOrElse(h) { 0f }
            val future = nowHour != null && h > nowHour
            when {
                future -> drawRoundRect(c.outline, Offset(x, base - 2.dp.toPx()), Size(bw, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
                v <= 0f -> drawRoundRect(c.track, Offset(x, base - 3.dp.toPx()), Size(bw, 3.dp.toPx()), CornerRadius(1.5.dp.toPx()))
                else -> {
                    val full = max(base - y(v), 4.dp.toPx())
                    capped(c.zoneFill(RiskZone.of(v.toDouble())), x, base, bw, full * stagger(grow, h, n), 4.dp.toPx())
                }
            }
        }
        if (loudest != null) {
            val v = hourly[loudest]
            drawCircle(c.surface, 5.dp.toPx(), Offset(loudest * slot + slot / 2, y(v) - 9.dp.toPx()))
            drawCircle(c.text, 3.dp.toPx(), Offset(loudest * slot + slot / 2, y(v) - 9.dp.toPx()))
        }
        if (nowHour != null && nowHour in 0 until n) {
            drawRoundRect(c.text, Offset(nowHour * slot + slot / 2 - 1.dp.toPx(), base + 2.dp.toPx()), Size(2.dp.toPx(), 6.dp.toPx()), CornerRadius(1.dp.toPx()))
        }
        // the hours under the baseline: 0, 6, 12, 18, and 24 at the end
        val labelY = base + 8.dp.toPx()
        axis.forEach { (hour, text) ->
            val t = ink.text(text)
            val cx = hour * slot
            drawText(t, c.text2, Offset((cx).coerceAtLeast(0f), labelY))
        }
        val end = ink.text("24")
        drawText(end, c.text2, Offset(size.width - end.size.width, labelY))
    }
}

// ------------------------------------------------------------------------------------ days

/**
 * SEVEN DAYS — capped columns with the value of each on its cap (seven are few enough to label every one), the day under
 * it, and the 100 % hairline with its label in the gutter beside it. [days] is oldest first; the last is today. A day
 * without data has no column. [labels] has one entry per day.
 */
@Composable
fun DayColumns(
    days: List<DayFigure>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 170.dp,
) {
    val c = Vox.colors
    val ink = rememberChartInk()
    val density = LocalDensity.current
    val grow by rememberDrawIn()
    val valueStyle = wordsStyle(13, FontWeight.ExtraBold, 1.2f)
    val labelStyle = wordsStyle(12, FontWeight.SemiBold, 1.2f)
    val textHeight = with(density) { ink.text("0", valueStyle).size.height.toDp() }
    val axisHeight = with(density) { ink.text("0", labelStyle).size.height.toDp() } + 8.dp
    val topMargin = textHeight + 6.dp
    val limitLabel = stringResource(R.string.chart_limit_100)
    val maxV = max(120.0, days.maxOfOrNull { it.percent } ?: 0.0)
    Canvas(modifier.fillMaxWidth().height(plotHeight + axisHeight).clearAndSetSemantics { contentDescription = description }) {
        val n = max(1, days.size)
        val limit = ink.text(limitLabel)
        val gutter = limit.size.width + 8.dp.toPx()
        val plotW = size.width - gutter
        val slot = plotW / n
        val bw = min(28.dp.toPx(), slot * 0.6f)
        val base = plotHeight.toPx()
        val usable = base - topMargin.toPx()
        fun y(v: Double) = base - (usable * (v / maxV)).toFloat()
        hairline(c.outline, gutter, size.width, base)
        val y100 = y(100.0)
        hairline(c.text3, gutter, size.width, y100)
        drawText(limit, c.text2, Offset(0f, y100 - limit.size.height / 2f))
        days.forEachIndexed { i, d ->
            val x = gutter + i * slot + (slot - bw) / 2
            val cx = gutter + i * slot + slot / 2
            if (d.measured) {
                val h = max(base - y(d.percent), 4.dp.toPx()) * stagger(grow, i, n)
                capped(c.zoneFill(RiskZone.ofPercent(d.percent)), x, base, bw, h, 4.dp.toPx())
                val v = ink.text("${d.percent.roundToInt()}", valueStyle)
                drawText(v, c.text, Offset(cx - v.size.width / 2f, base - h - v.size.height - 3.dp.toPx()))
            } else {
                drawRoundRect(c.track, Offset(x, base - 3.dp.toPx()), Size(bw, 3.dp.toPx()), CornerRadius(1.5.dp.toPx()))
            }
            val today = i == days.lastIndex
            val l = ink.text(labels.getOrElse(i) { "" }, if (today) wordsStyle(12, FontWeight.ExtraBold, 1.2f) else labelStyle)
            drawText(l, if (today) c.text else c.text2, Offset(cx - l.size.width / 2f, base + 8.dp.toPx()))
        }
    }
}

// ------------------------------------------------------------------------------------ lines

/** Draws [values] (oldest first) as a 2 dp line with a faint area, a gutter label on the 100 % hairline and a ringed end dot. */
@Composable
private fun LineChart(
    values: List<Double>,
    lineColor: Color,
    description: String,
    modifier: Modifier,
    plotHeight: Dp,
    xAxis: List<Pair<Float, String>> = emptyList(),
    domainEnd: Float = 1f,
    minMax: Double = 120.0,
) {
    val c = Vox.colors
    val ink = rememberChartInk()
    val density = LocalDensity.current
    val grow by rememberDrawIn(900)
    val axisHeight = if (xAxis.isEmpty()) 8.dp else with(density) { ink.text("0").size.height.toDp() } + 10.dp
    val limitLabel = stringResource(R.string.chart_limit_100)
    val maxV = max(minMax, values.maxOrNull() ?: 0.0)
    Canvas(modifier.fillMaxWidth().height(plotHeight + axisHeight).clearAndSetSemantics { contentDescription = description }) {
        val limit = ink.text(limitLabel)
        val gutter = limit.size.width + 8.dp.toPx()
        val plotW = size.width - gutter
        val base = plotHeight.toPx()
        val pad = 8.dp.toPx()
        fun y(v: Double) = pad + ((base - 2 * pad) * (1.0 - v / maxV)).toFloat()
        fun x(i: Int) = gutter + plotW * domainEnd * (if (values.size <= 1) 0f else i / (values.size - 1f))
        hairline(c.outline, gutter, size.width, base)
        val y100 = y(100.0)
        hairline(c.text3, gutter, size.width, y100)
        drawText(limit, c.text2, Offset(0f, y100 - limit.size.height / 2f))
        if (values.size >= 2) {
            val line = Path().apply {
                moveTo(x(0), y(values[0]))
                for (i in 1 until values.size) lineTo(x(i), y(values[i]))
            }
            val shown = Path()
            val measure = PathMeasure().apply { setPath(line, false) }
            measure.getSegment(0f, measure.length * grow, shown, true)
            val area = Path().apply {
                addPath(shown)
                val endX = x(((values.size - 1) * grow).toInt().coerceIn(0, values.size - 1)).let { if (grow >= 1f) x(values.size - 1) else it }
                lineTo(endX, base); lineTo(x(0), base); close()
            }
            drawPath(area, lineColor.copy(alpha = 0.10f))
            drawPath(shown, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (grow >= 1f) {
                val end = Offset(x(values.size - 1), y(values.last()))
                drawCircle(c.surface, 6.dp.toPx(), end)
                drawCircle(c.zoneFill(RiskZone.ofPercent(values.last())), 4.dp.toPx(), end)
            }
        }
        xAxis.forEach { (frac, text) ->
            val t = ink.text(text)
            val cx = gutter + plotW * frac
            drawText(t, c.text2, Offset((cx - t.size.width / 2f).coerceIn(gutter, size.width - t.size.width), base + 8.dp.toPx()))
        }
    }
}

/** THE ROLLING WEEK — the share of the weekly allowance used, as of the end of each day (oldest first); the end dot is today. */
@Composable
fun RollingLine(values: List<Double>, description: String, modifier: Modifier = Modifier, plotHeight: Dp = 110.dp) {
    LineChart(values, Vox.colors.accent.fill, description, modifier, plotHeight)
}

/**
 * WHERE THE DAY WENT — how today's allowance filled up: the cumulative dose by minute (oldest first, [fractions] of the
 * day on x), ending at [nowFraction] of the day. The hours still to come stay an empty baseline. The line is the zone of the total.
 */
@Composable
fun CumulativeLine(
    cumulative: List<Double>,
    nowFraction: Float,
    description: String,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 96.dp,
) {
    val total = cumulative.lastOrNull() ?: 0.0
    LineChart(
        cumulative, Vox.colors.zoneFill(RiskZone.ofPercent(total)), description, modifier, plotHeight,
        xAxis = listOf(0f to "0", 0.25f to "6", 0.5f to "12", 0.75f to "18", 1f to "24"),
        domainEnd = nowFraction.coerceIn(0.02f, 1f),
        minMax = max(110.0, total * 1.15),
    )
}

// ------------------------------------------------------------------------------------ month

/**
 * THE MONTH AS A CALENDAR — each day a cell in the zone it reached (OK is the accent outlined and softly filled, MODERATE
 * the solid accent, LOUD amber, DANGEROUS crimson; no data is the grey track), the day number on it, Monday or Sunday
 * first as the locale has it. The first day of a month carries its month name, today wears a ring. [days] is oldest first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeatCalendar(days: List<DayFigure>, zoneWordFor: (RiskZone) -> String, noData: String, todayLabel: String, modifier: Modifier = Modifier) {
    if (days.isEmpty()) return
    val c = Vox.colors
    val locale = Locale.getDefault()
    val sundayFirst = Calendar.getInstance(locale).firstDayOfWeek == Calendar.SUNDAY
    val lead = calendarLead(days.first().dayEpoch, sundayFirst)
    val cells: List<DayFigure?> = List(lead) { null } + days
    val todayEpoch = days.last().dayEpoch
    val headEpoch = days.first().dayEpoch - lead
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CappedFontScale(1.3f) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { i ->
                    Text(
                        dayText(headEpoch + i, "EEE", locale).take(1).uppercase(locale), style = wordsStyle(12, FontWeight.SemiBold, 1.2f), color = c.text2,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val d = week.getOrNull(i)
                    if (d == null) {
                        Box(Modifier.weight(1f).heightIn(min = 44.dp))
                    } else {
                        CalendarCell(d, d.dayEpoch == todayEpoch, locale, zoneWordFor, noData, todayLabel, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(
    d: DayFigure,
    today: Boolean,
    locale: Locale,
    zoneWordFor: (RiskZone) -> String,
    noData: String,
    todayLabel: String,
    modifier: Modifier,
) {
    val c = Vox.colors
    val zone = RiskZone.ofPercent(d.percent)
    val fill = zoneChartFill(zone)
    val shape = RoundedCornerShape(10.dp)
    val dayOfMonth = Calendar.getInstance(locale).apply { timeInMillis = d.dayEpoch * 86_400_000L + 43_200_000L }.get(Calendar.DAY_OF_MONTH)
    val first = dayOfMonth == 1
    val date = dayText(d.dayEpoch, "d MMMM", locale)
    val spoken = buildString {
        append(date)
        append(": ")
        append(if (d.measured) "${d.percent.roundToInt()} %, ${zoneWordFor(zone)}" else noData)
        if (today) append(", $todayLabel")
    }
    // OK: soft fill with an outline in the accent (still 3:1 against the surface); MODERATE and above: solid.
    val soft = d.measured && zone == RiskZone.OK
    val bg = when {
        !d.measured -> c.track
        soft -> fill.copy(alpha = if (c.dark) 0.28f else 0.20f)
        else -> fill
    }
    val onBg = if (d.measured && !soft) Vox.colors.level(zone).onFill else c.text
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(bg)
            .semantics { contentDescription = spoken }
            .let { m -> if (soft) m.border(1.dp, fill, shape) else m }
            .let { m -> if (today) m.border(2.dp, c.text, shape) else m },
        contentAlignment = Alignment.Center,
    ) {
        CappedFontScale(1.3f) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (first) Text(dayText(d.dayEpoch, "MMM", locale), style = wordsStyle(10, FontWeight.Bold, 1.1f), color = onBg, maxLines = 1)
                Text("$dayOfMonth", style = wordsStyle(13, FontWeight.ExtraBold, 1.2f), color = onBg, maxLines = 1)
            }
        }
    }
}

/**
 * How many empty cells come before [firstDayEpoch] in a calendar whose weeks start on Sunday or Monday, so every column
 * is always the same weekday. 1 Jan 1970 (day 0) was a Thursday: column 3 counting from Monday, 4 from Sunday.
 */
fun calendarLead(firstDayEpoch: Long, sundayFirst: Boolean): Int = (((firstDayEpoch + if (sundayFirst) 4 else 3) % 7 + 7) % 7).toInt()

/** A day of the ledger formatted with [pattern] in [locale] (the ledger's days are local days: noon is safely inside one). */
fun dayText(dayEpoch: Long, pattern: String, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat(pattern, locale).format(Date(dayEpoch * 86_400_000L + 43_200_000L))

// ------------------------------------------------------------------------------------ minutes

/** Bar height per zone, so the strip reads without colour: taller = louder. */
private fun zoneHeight(z: RiskZone): Float = when (z) {
    RiskZone.OK -> 0.4f
    RiskZone.MODERATE -> 0.6f
    RiskZone.LOUD -> 0.8f
    RiskZone.DANGEROUS -> 1f
}

/**
 * TODAY MINUTE BY MINUTE — each run of minutes in its zone's colour AND height (unmeasured minutes are a short grey
 * line), with the current minute marked. Hours 0, 6, 12, 18 and 24 are labelled; no vertical guides.
 */
@Composable
fun ZoneStrip(segments: List<ZoneSegment>, nowMinute: Int, description: String, modifier: Modifier = Modifier) {
    val c = Vox.colors
    val ink = rememberChartInk()
    val density = LocalDensity.current
    val axisHeight = with(density) { ink.text("0").size.height.toDp() } + 10.dp
    val grow by rememberDrawIn()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(44.dp + axisHeight).clearAndSetSemantics { contentDescription = description }) {
            val w = size.width
            val base = 44.dp.toPx()
            fun x(m: Int) = w * m / 1440f
            drawRoundRect(c.track, Offset(0f, base - 3.dp.toPx()), Size(w, 3.dp.toPx()), CornerRadius(1.5.dp.toPx()))
            for (s in segments) {
                val z = s.zone ?: continue
                val bh = base * zoneHeight(z) * ease(grow)
                val sw = max(2f, x(s.toMinute) - x(s.fromMinute))
                capped(c.zoneFill(z), x(s.fromMinute), base, sw, bh, min(3.dp.toPx(), sw / 2))
            }
            val nx = x(nowMinute.coerceIn(0, 1440))
            drawLine(c.text, Offset(nx, 0f), Offset(nx, base + 4.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
            listOf(0f to "0", 0.25f to "6", 0.5f to "12", 0.75f to "18", 1f to "24").forEach { (f, text) ->
                val t = ink.text(text)
                drawText(t, c.text2, Offset((w * f - t.size.width / 2f).coerceIn(0f, w - t.size.width), base + 10.dp.toPx()))
            }
        }
        ZoneLegend(segments.mapNotNull { it.zone }.toSet())
    }
}

/** Zone word for the TalkBack summaries. */
@Composable
fun zoneWordText(z: RiskZone?): String =
    if (z == null) stringResource(R.string.zone_not_measured) else stringResource(z.wordRes())
