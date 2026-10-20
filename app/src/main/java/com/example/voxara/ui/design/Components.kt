package com.example.voxara.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.theme.LocalAmbient
import com.example.voxara.ui.theme.LocalReduceMotion

/*
 * VOXARA COMPONENTS — the building blocks every screen is made of, in the template style
 * (docs/design/STYLE.md): dark cards with 1 dp borders on black, accent-soft icon tiles, pill
 * badges, a 270° bezel arc. They read only design tokens (VColor, VType, VSpace, VRadius, VSize,
 * VMotion), respect ambient mode and reduced motion, keep touch targets >= 48 dp, and never let
 * colour carry meaning alone.
 */

private val pillShape = RoundedCornerShape(percent = VRadius.pill)

// ------------------------------------------------------------------------------------ zones

/** The zone's word, localised. */
@Composable
fun zoneWord(z: RiskZone): String = stringResource(z.wordRes())

/**
 * Icon + word in a pill: the zone, readable without colour. Accent-soft fill, tier ink and a tier
 * border, as the template's status badge. Ambient: grey outline only.
 */
@Composable
fun ZoneBadge(zone: RiskZone, modifier: Modifier = Modifier, label: String = zoneWord(zone)) {
    val ambient = LocalAmbient.current
    val tone = animateTone(VColor.tone(zone), instant = LocalReduceMotion.current || ambient)
    val ink = if (ambient) VColor.TextSecondary else tone.ink
    Row(
        modifier = modifier
            .clip(pillShape)
            .background(if (ambient) Color.Transparent else tone.soft)
            .border(VSize.border, if (ambient) VColor.TextTertiary else tone.ink.copy(alpha = 0.7f), pillShape)
            .padding(horizontal = VSpace.m, vertical = VSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VSpace.xs),
    ) {
        Icon(VoxIcons.zone(zone), contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Text(label, style = VType.Label, color = if (ambient) VColor.TextSecondary else ink, maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------ arcs and rings

/** Level gauge scale (dBA): the template's bezel maps 30–110 onto its arc. */
const val GAUGE_MIN_DBA = 30f
const val GAUGE_MAX_DBA = 110f

/** 0..1 position of a level on the bezel. */
fun gaugeFraction(dba: Float): Float = ((dba - GAUGE_MIN_DBA) / (GAUGE_MAX_DBA - GAUGE_MIN_DBA)).coerceIn(0f, 1f)

/**
 * BEZEL ARC — the template's 270° progress arc (gap at the bottom), rounded caps, white-7 % track
 * and a glow under the progress (a wide translucent stroke: no blur on the watch). Position carries
 * the value, so it reads the same in any colour vision. Ambient: hairline track and progress in
 * grey, no glow.
 */
@Composable
fun BezelArc(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = VSize.bezel,
) {
    val ambient = LocalAmbient.current
    val shown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (LocalReduceMotion.current || ambient) snap() else VMotion.value(),
        label = "bezel",
    )
    Canvas(modifier) {
        val w = if (ambient) 1.5.dp.toPx() else stroke.toPx()
        val glow = w * 3.2f
        val inset = (if (ambient) w else glow) / 2 + 1.dp.toPx()
        val arcSize = Size(size.minDimension - 2 * inset, size.minDimension - 2 * inset)
        val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
        drawArc(
            if (ambient) VColor.TextTertiary.copy(alpha = 0.5f) else VColor.BezelTrack,
            135f, 270f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round),
        )
        if (shown > 0.003f) {
            val sweep = 270f * shown
            if (!ambient) {
                drawArc(color.copy(alpha = 0.18f), 135f, sweep, false, topLeft, arcSize, style = Stroke(glow, cap = StrokeCap.Round))
            }
            drawArc(
                if (ambient) VColor.TextSecondary else color,
                135f, sweep, false, topLeft, arcSize, style = Stroke(w * 1.1f, cap = StrokeCap.Round),
            )
        }
    }
}

/** The bezel showing a live level ([dba] null = nothing measured yet), in the level's tier colour. */
@Composable
fun LevelBezel(dba: Float?, modifier: Modifier = Modifier) {
    val tone = animateTone(VColor.tone(RiskZone.of((dba ?: 0f).toDouble())), instant = LocalReduceMotion.current)
    BezelArc(fraction = dba?.let(::gaugeFraction) ?: 0f, color = tone.fill, modifier = modifier)
}

/**
 * RING — a full progress ring (dose, weekly budget, score), rounded cap, translucent track. The
 * centre content always states the number in words.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = VColor.AccentInk,
    stroke: Dp = VSize.ringStroke,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val ambient = LocalAmbient.current
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (LocalReduceMotion.current) snap() else VMotion.value(),
        label = "ring",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = if (ambient) 2.dp.toPx() else stroke.toPx()
            val inset = w / 2
            val arcSize = Size(size.minDimension - 2 * inset, size.minDimension - 2 * inset)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            drawArc(
                if (ambient) VColor.TextTertiary.copy(alpha = 0.5f) else VColor.Track,
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

/** The plain-language sentence that owns the screen: extra-bold, tight. */
@Composable
fun HeroStatus(text: String, modifier: Modifier = Modifier, maxLines: Int = 3) {
    Text(
        text = text, style = VType.Title, color = VColor.Text, textAlign = TextAlign.Center,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}

/** A short caption-sized line (notes, sources, coverage). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = VColor.TextSecondary) {
    Text(text, style = VType.Caption, color = color, textAlign = TextAlign.Center, modifier = modifier, maxLines = 2)
}

/** The small uppercase, letter-spaced label above a group: a heading for TalkBack. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = VColor.TextTertiary) {
    Text(
        text.uppercase(), style = VType.Eyebrow, color = color, textAlign = TextAlign.Center, maxLines = 2,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * EDITORIAL HEADER — the template's small uppercase accent label with a pulsing dot, and (when
 * given) a large extra-bold title under it. Both are headings.
 */
@Composable
fun EditorialHeader(label: String, modifier: Modifier = Modifier, title: String? = null, color: Color = VColor.AccentInk) {
    val ambient = LocalAmbient.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.xs)) {
            PulseDot(color, animate = LocalAnimate.current && !ambient)
            Text(label.uppercase(), style = VType.Eyebrow, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() })
        }
        if (title != null) {
            Text(title, style = VType.Title, color = VColor.Text, textAlign = TextAlign.Center, maxLines = 3,
                modifier = Modifier.semantics { heading() })
        }
    }
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

/** Full-width pill button, >= 48 dp tall, optional icon. Primary = accent fill (theme). */
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
            .clip(pillShape)
            .background(VColor.CardSubtle)
            .border(VSize.border, VColor.Border, pillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = VSpace.l, vertical = VSpace.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = VType.Label, color = VColor.Text, maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------ cards

/** The template's icon tile: a rounded square in accent-soft with the icon in accent ink. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = VSize.iconTile) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(VRadius.tile)).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** A card: dark card fill, 1 dp border, 22 dp radius. */
private fun Modifier.card(): Modifier = this
    .clip(RoundedCornerShape(VRadius.card))
    .background(VColor.Card)
    .border(VSize.border, VColor.Border, RoundedCornerShape(VRadius.card))

/**
 * METRIC CARD — a settings/list row: icon tile, muted label, value, optional detail. The
 * whole card is the touch target.
 */
@Composable
fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    accent: Color = VColor.AccentInk,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Narrow card for the font scale (192 dp at 1.3×): a smaller tile and tighter padding, so one
        // long word ("MONITORING") still fits on its line.
        val compact = maxWidth < 180.dp * LocalDensity.current.fontScale
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = VSize.touch)
                .card()
                .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
                .padding(horizontal = if (compact) VSpace.s else VSpace.m, vertical = VSpace.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else VSpace.s + 2.dp),
        ) {
            IconTile(icon, accent, size = if (compact) 24.dp else VSize.iconTile)
            Column(Modifier.weight(1f)) {
                // Caption, not the tracked uppercase eyebrow: one long word must still fit a 192 dp card at 1.3×.
                Text(label, style = VType.Caption.copy(textAlign = TextAlign.Start), color = VColor.TextTertiary, maxLines = 2)
                Text(value, style = VType.TitleSmall.copy(textAlign = TextAlign.Start), color = VColor.Text, maxLines = 2)
                detail?.let {
                    Text(it, style = VType.Caption.copy(textAlign = TextAlign.Start), color = VColor.TextSecondary, maxLines = 2)
                }
            }
            trailing?.invoke()
        }
    }
}

/** A plain rounded card for grouped content. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .card()
            .padding(VSpace.m + 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VSpace.s),
    ) { content() }
}

// ------------------------------------------------------------------------------------ charts

/**
 * WEEK BARS — seven rounded bars (oldest -> today), today's bar fully opaque and the others at
 * 75 %, as the template's history bars. Values are fractions 0..1 of the bar height; the numbers
 * are also exposed to TalkBack through [description].
 */
@Composable
fun WeekBars(
    values: List<Float>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = VColor.AccentInk,
) {
    Column(modifier.semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VSpace.s),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(64.dp),
        ) {
            values.forEachIndexed { i, v ->
                val last = i == values.lastIndex
                Box(Modifier.width(14.dp).fillMaxHeight(v.coerceIn(0.06f, 1f))) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = if (v > 0f) color.copy(alpha = if (last) 1f else 0.75f) else VColor.Track,
                            cornerRadius = CornerRadius(VRadius.bar.toPx()),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VSpace.s), modifier = Modifier.padding(top = VSpace.xs)) {
            labels.forEach { Text(it, style = VType.Caption, color = VColor.TextTertiary, modifier = Modifier.width(14.dp), maxLines = 1) }
        }
    }
}

// ------------------------------------------------------------------------------------ sheets

/**
 * ALERT SHEET — alert content: zone icon in a tier-soft circle, title, body, actions. Haptics come
 * first (from the service); this is what the wearer sees when they look.
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
        ZoneIconCircle(zone)
        Text(title, style = VType.Title, color = VColor.Text, textAlign = TextAlign.Center, maxLines = 3)
        BodyText(body, color = VColor.TextSecondary)
        actions()
    }
}

/** The toast's round icon: the zone icon in its tier colour on the tier's soft tint. */
@Composable
fun ZoneIconCircle(zone: RiskZone, modifier: Modifier = Modifier) {
    val tone = VColor.tone(zone)
    Box(modifier.size(44.dp).clip(pillShape).background(tone.soft), contentAlignment = Alignment.Center) {
        Icon(VoxIcons.zone(zone), contentDescription = zoneWord(zone), tint = tone.ink, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VSpace.s)) {
        IconTile(icon, VColor.TextTertiary)
        BodyText(text, color = VColor.TextSecondary)
    }
}
