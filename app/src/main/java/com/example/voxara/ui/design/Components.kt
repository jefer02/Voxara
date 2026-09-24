package com.example.voxara.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.theme.LocalAmbient
import com.example.voxara.ui.theme.LocalReduceMotion
import kotlin.math.cos
import kotlin.math.sin

/*
 * VOXARA COMPONENTS — the building blocks every screen is made of. They read only design tokens
 * (VColor, VType, VSpace, VRadius, VSize, VMotion), respect ambient mode and reduced motion, keep
 * touch targets >= 48 dp, and never let colour carry meaning alone.
 */

// ------------------------------------------------------------------------------------ zones

/** The zone's word, localised. */
@Composable
fun zoneWord(z: RiskZone): String = stringResource(
    when (z) {
        RiskZone.OK -> R.string.zone_ok
        RiskZone.MODERATE -> R.string.zone_moderate
        RiskZone.LOUD -> R.string.zone_loud
        RiskZone.DANGEROUS -> R.string.zone_dangerous
    }
)

/** Icon + word in a pill: the zone, readable without colour. */
@Composable
fun ZoneBadge(zone: RiskZone, modifier: Modifier = Modifier, label: String = zoneWord(zone)) {
    val c = if (LocalAmbient.current) VColor.TextSecondary else VColor.zone(zone)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = VRadius.pill))
            .background(c.copy(alpha = 0.16f))
            .border(1.dp, c.copy(alpha = 0.6f), RoundedCornerShape(percent = VRadius.pill))
            .padding(horizontal = VSpace.m, vertical = VSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VSpace.xs),
    ) {
        Icon(VoxIcons.zone(zone), contentDescription = null, tint = c, modifier = Modifier.size(16.dp))
        Text(label, style = VType.Label, color = VColor.Text, maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------ gauges

private const val GAUGE_MIN = 30f
private const val GAUGE_MAX = 110f
private const val GAUGE_START = 135f
private const val GAUGE_SWEEP = 270f

private fun angleOf(dba: Float): Float =
    GAUGE_START + GAUGE_SWEEP * ((dba - GAUGE_MIN) / (GAUGE_MAX - GAUGE_MIN)).coerceIn(0f, 1f)

/**
 * ZONE GAUGE — a thick 270-degree ring from 30 to 110 dBA with the four zones as bands, a filled
 * arc to the current level and a pointer. Position on the ring carries the level, so it reads the
 * same in any colour vision. Ambient: hairline outline only, no fills.
 */
@Composable
fun ZoneGauge(dba: Float?, modifier: Modifier = Modifier, stroke: Dp = VSize.ringStroke) {
    val ambient = LocalAmbient.current
    val animated by animateFloatAsState(
        targetValue = dba ?: GAUGE_MIN,
        animationSpec = if (LocalReduceMotion.current) androidx.compose.animation.core.snap() else VMotion.value(),
        label = "gauge",
    )
    Canvas(modifier) {
        val w = if (ambient) 2.dp.toPx() else stroke.toPx()
        val inset = w / 2 + 2.dp.toPx()
        val arcSize = Size(size.minDimension - 2 * inset, size.minDimension - 2 * inset)
        val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
        // Zone bands as the track.
        RiskZone.entries.forEachIndexed { i, z ->
            val from = maxOf(z.fromDba.toFloat(), GAUGE_MIN)
            val to = RiskZone.entries.getOrNull(i + 1)?.fromDba?.toFloat() ?: GAUGE_MAX
            val a0 = angleOf(from)
            val sweep = angleOf(to) - a0
            drawArc(
                color = if (ambient) VColor.TextTertiary.copy(alpha = 0.5f) else VColor.zone(z).copy(alpha = 0.22f),
                startAngle = a0 + 0.8f, sweepAngle = sweep - 1.6f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(w, cap = StrokeCap.Butt),
            )
        }
        if (dba != null) {
            if (!ambient) {
                // Filled per zone: the path up to the level keeps each zone's colour.
                RiskZone.entries.forEachIndexed { i, z ->
                    val from = maxOf(z.fromDba.toFloat(), GAUGE_MIN)
                    val to = minOf(RiskZone.entries.getOrNull(i + 1)?.fromDba?.toFloat() ?: GAUGE_MAX, animated)
                    if (to <= from) return@forEachIndexed
                    val first = i == 0
                    drawArc(
                        color = VColor.zone(z),
                        startAngle = angleOf(from), sweepAngle = angleOf(to) - angleOf(from), useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(w, cap = if (first) StrokeCap.Round else StrokeCap.Butt),
                    )
                }
            }
            pointer(angleOf(animated), topLeft, arcSize, w, ambient)
        }
    }
}

private fun DrawScope.pointer(angle: Float, topLeft: Offset, arcSize: Size, w: Float, ambient: Boolean) {
    val r = arcSize.width / 2
    val c = Offset(topLeft.x + r, topLeft.y + r)
    val rad = Math.toRadians(angle.toDouble())
    val p = Offset(c.x + r * cos(rad).toFloat(), c.y + r * sin(rad).toFloat())
    val dot = if (ambient) 4.dp.toPx() else w * 0.62f
    drawCircle(Color.Black, dot + 2.dp.toPx(), p)
    drawCircle(Color.White, dot, p)
}

/**
 * RING — a thick progress ring (dose, weekly budget, score). [fraction] 0..1+; the colour follows
 * the zone of [percentForZone] and the centre content always states the number in words.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = VColor.BrandStart,
    stroke: Dp = VSize.ringStroke,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val ambient = LocalAmbient.current
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (LocalReduceMotion.current) androidx.compose.animation.core.snap() else VMotion.value(),
        label = "ring",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = if (ambient) 2.dp.toPx() else stroke.toPx()
            val inset = w / 2
            val arcSize = Size(size.minDimension - 2 * inset, size.minDimension - 2 * inset)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            drawArc(
                if (ambient) VColor.TextTertiary.copy(alpha = 0.5f) else VColor.Surface3,
                -90f, 360f, false, topLeft, arcSize, style = Stroke(w),
            )
            if (animated > 0.002f) {
                drawArc(
                    if (ambient) VColor.TextSecondary else color,
                    -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

// ------------------------------------------------------------------------------------ text

/** One-line-ish hero status: the plain-language sentence that owns the screen. */
@Composable
fun HeroStatus(text: String, modifier: Modifier = Modifier, maxLines: Int = 3) {
    Text(
        text = text, style = VType.Title, color = VColor.Text, textAlign = TextAlign.Center,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = VColor.TextSecondary) {
    Text(text, style = VType.Caption, color = color, textAlign = TextAlign.Center, modifier = modifier, maxLines = 2)
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = VColor.TextSecondary, maxLines: Int = 6) {
    Text(
        text, style = VType.Body, color = color, textAlign = TextAlign.Center, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}

// ------------------------------------------------------------------------------------ actions

enum class PillStyle { PRIMARY, TONAL, OUTLINED }

/** Full-width pill button, >= 48 dp tall, optional icon. */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: PillStyle = PillStyle.PRIMARY,
    enabled: Boolean = true,
) {
    val m = modifier.fillMaxWidth().defaultMinSize(minHeight = VSize.touch)
    val iconSlot: (@Composable BoxScope.() -> Unit)? = icon?.let {
        { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
    }
    val labelSlot: @Composable RowScope.() -> Unit = {
        Text(label, style = VType.TitleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    when (style) {
        PillStyle.PRIMARY -> Button(onClick = onClick, modifier = m, enabled = enabled, icon = iconSlot, label = labelSlot)
        PillStyle.TONAL -> FilledTonalButton(onClick = onClick, modifier = m, enabled = enabled, icon = iconSlot, label = labelSlot)
        PillStyle.OUTLINED -> OutlinedButton(onClick = onClick, modifier = m, enabled = enabled, icon = iconSlot, label = labelSlot)
    }
}

/** A quick-reply chip for Ask (still a 48 dp target). */
@Composable
fun ReplyChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = VSize.touch)
            .clip(RoundedCornerShape(percent = VRadius.pill))
            .background(VColor.Surface2)
            .border(1.dp, VColor.Outline, RoundedCornerShape(percent = VRadius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = VSpace.l, vertical = VSpace.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = VType.Label, color = VColor.Text, maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------ cards

/**
 * METRIC CARD — large rounded card: icon, label, value, optional detail. The whole card is the
 * touch target.
 */
@Composable
fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    accent: Color = VColor.BrandStart,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = VSize.touch)
            .clip(RoundedCornerShape(VRadius.card))
            .background(VColor.Surface1)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = VSpace.l, vertical = VSpace.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VSpace.m),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            // Labels wrap rather than clip: large font sizes on a 192 dp screen.
            Text(label, style = VType.Caption.copy(textAlign = TextAlign.Start), color = VColor.TextSecondary, maxLines = 2)
            Text(value, style = VType.TitleSmall.copy(textAlign = TextAlign.Start), color = VColor.Text, maxLines = 2)
            detail?.let {
                Text(it, style = VType.Caption.copy(textAlign = TextAlign.Start), color = VColor.TextSecondary, maxLines = 2)
            }
        }
        trailing?.invoke()
    }
}

/** A plain rounded panel for grouped content. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VRadius.card))
            .background(VColor.Surface1)
            .padding(VSpace.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VSpace.s),
    ) { content() }
}

// ------------------------------------------------------------------------------------ charts

/**
 * WEEK BARS — seven rounded bars (oldest -> today). Values are fractions 0..1 of the bar height;
 * the numbers are also exposed to TalkBack through [description].
 */
@Composable
fun WeekBars(
    values: List<Float>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = VColor.BrandStart,
) {
    Column(modifier.semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VSpace.s),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(64.dp),
        ) {
            values.forEach { v ->
                Box(Modifier.width(14.dp).fillMaxHeight(v.coerceIn(0.04f, 1f))) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = if (v > 0f) color else VColor.Surface3,
                            cornerRadius = CornerRadius(VRadius.bar.toPx()),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VSpace.s), modifier = Modifier.padding(top = VSpace.xs)) {
            labels.forEach { Text(it, style = VType.Caption, color = VColor.TextSecondary, modifier = Modifier.width(14.dp), maxLines = 1) }
        }
    }
}

// ------------------------------------------------------------------------------------ sheets

/**
 * ALERT SHEET — full-screen alert content: zone icon, title, body, actions. Haptics come first
 * (from the service); this is what the wearer sees when they look.
 */
@Composable
fun AlertSheet(
    zone: RiskZone,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VSpace.s),
    ) {
        Icon(VoxIcons.zone(zone), contentDescription = zoneWord(zone), tint = VColor.zone(zone), modifier = Modifier.size(32.dp))
        Text(title, style = VType.Title, color = VColor.Text, textAlign = TextAlign.Center, maxLines = 3)
        BodyText(body, color = VColor.TextSecondary)
        actions()
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VSpace.s)) {
        Icon(icon, contentDescription = null, tint = VColor.TextTertiary, modifier = Modifier.size(28.dp))
        BodyText(text, color = VColor.TextSecondary)
    }
}
