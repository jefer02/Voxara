package com.example.voxara.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.voxara.R
import com.example.voxara.core.risk.RiskZone
import kotlinx.coroutines.delay
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
 * ZONE TAG — the zone as icon + word in its tier colour: a small filled tag, not an outlined pill, so
 * it does not look like a button (it is not one). A dark scrim under the tint keeps the word readable
 * over the wave rings. When the zone changes, the colour crossfades (400 ms) and the icon and word
 * swap with a short pop (150 ms); both are instant with reduced motion. Ambient: grey text with a
 * hairline outline.
 */
@Composable
fun ZoneTag(zone: RiskZone, modifier: Modifier = Modifier) {
    val ambient = LocalAmbient.current
    val still = LocalReduceMotion.current || ambient
    val tone = animateTone(VColor.tone(zone), instant = still)
    val ink = if (ambient) VColor.TextSecondary else tone.ink
    val shape = RoundedCornerShape(8.dp)
    AnimatedContent(
        targetState = zone,
        modifier = modifier
            .clip(shape)
            .let {
                if (ambient) it.border(VSize.border, VColor.TextTertiary, shape)
                else it.background(Color.Black.copy(alpha = 0.62f)).background(tone.soft)
            },
        transitionSpec = {
            if (still) fadeIn(snap()) togetherWith fadeOut(snap())
            else (fadeIn(tween(VMotion.TAG_MS)) + scaleIn(tween(VMotion.TAG_MS), initialScale = 0.9f)) togetherWith
                fadeOut(tween(VMotion.TAG_MS / 2))
        },
        label = "zoneTag",
    ) { z ->
        Row(
            Modifier.padding(horizontal = VSpace.s, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VSpace.xs),
        ) {
            Icon(VoxIcons.zone(z), contentDescription = null, tint = ink, modifier = Modifier.size(16.dp * LocalTextScale.current))
            Text(zoneWord(z), style = VType.Label.heroScaled(), color = ink, maxLines = 1)
        }
    }
}

/**
 * PRESSABLE — a tap target with press feedback: while held it shrinks to 96 % for [VMotion.PRESS_MS]
 * and springs back (the system ripple stays). No scale with reduced motion; the ripple still confirms.
 */
@Composable
fun Modifier.pressable(onClick: () -> Unit, onClickLabel: String? = null, role: Role = Role.Button): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val still = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !still) 0.96f else 1f,
        animationSpec = tween(VMotion.PRESS_MS),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = LocalIndication.current, role = role, onClickLabel = onClickLabel, onClick = onClick)
}

/**
 * RESUME PILL — shown in place of the zone tag while monitoring is paused: one tap resumes. A 48 dp
 * target in the caution tone, with the state in words ("Monitoring paused") for the screen reader.
 */
@Composable
fun ResumePill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.action_resume)
    val state = stringResource(R.string.paused_label)
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = VSize.touch)
            .clip(pillShape)
            .background(VColor.Caution.copy(alpha = 0.16f))
            .border(VSize.border, VColor.Caution.copy(alpha = 0.7f), pillShape)
            .pressable(onClick = onClick, onClickLabel = label)
            .semantics { contentDescription = "$state. $label" }
            .padding(horizontal = VSpace.m, vertical = VSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VSpace.xs),
    ) {
        Icon(VoxIcons.Play, contentDescription = null, tint = VColor.Caution, modifier = Modifier.size(16.dp))
        Text(label, style = VType.Label, color = VColor.Caution, maxLines = 1)
    }
}

/** Level gauge scale (dBA): the template's bezel maps 30–110 onto its arc. */
const val GAUGE_MIN_DBA = 30f
const val GAUGE_MAX_DBA = 110f

/** 0..1 position of a level on the bezel. */
fun gaugeFraction(dba: Float): Float = ((dba - GAUGE_MIN_DBA) / (GAUGE_MAX_DBA - GAUGE_MIN_DBA)).coerceIn(0f, 1f)

/**
 * BEZEL ARC — the template's 270° progress arc (gap at the bottom), rounded caps, white-7 % track
 * and a glow under the progress (a wide translucent stroke: no blur on the watch). Position carries
 * the value, so it reads the same in any colour vision. Ambient: hairline track and progress in
 * grey, no glow. It is drawn on the same rim as every page's ring ([Face]): give it the whole display.
 * [gapDeg] above 0 draws the track as two arcs with that gap at the top (headphones mode, ambient: a
 * still outline, no progress).
 */
@Composable
fun BezelArc(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = Dp.Unspecified,
    gapDeg: Float = 0f,
) {
    val ambient = LocalAmbient.current
    val still = LocalReduceMotion.current || ambient
    // The first draw sweeps from empty to the value (a screen opening); later changes follow the
    // data with a spring. No motion at all with reduced motion or in ambient.
    var armed by remember { mutableStateOf(still) }
    var sweeping by remember { mutableStateOf(!still) }
    LaunchedEffect(Unit) {
        armed = true
        if (sweeping) { delay(VMotion.SWEEP_MS + 100L); sweeping = false }
    }
    val shown by animateFloatAsState(
        targetValue = if (armed) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = if (still) snap() else if (sweeping) VMotion.sweep() else VMotion.value(),
        label = "bezel",
    )
    Canvas(modifier) {
        val w = if (ambient) 1.5.dp.toPx() else if (stroke != Dp.Unspecified) stroke.toPx() else size.minDimension * Face.BEZEL
        val glow = w * 3f
        // Outer edge on the shared rim, like every page's ring. In ambient the system clock is shown (the pages hide
        // it), so the hairline sits further in, clear of the curved time text.
        val inset = Face.RING_INSET + (if (ambient) 0.07f else 0f)
        val radius = size.minDimension * (0.5f - inset) - (if (ambient) w else w * 1.1f) / 2
        val arcSize = Size(2 * radius, 2 * radius)
        val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
        val trackColor = if (ambient) VColor.TextTertiary.copy(alpha = 0.5f) else VColor.BezelTrack
        if (gapDeg > 0f) {
            val len = Face.SWEEP / 2 - gapDeg / 2
            drawArc(trackColor, Face.START, len, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(trackColor, 270f + gapDeg / 2, len, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            return@Canvas
        }
        drawArc(trackColor, Face.START, Face.SWEEP, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        if (shown > 0.003f) {
            val sweep = Face.SWEEP * shown
            if (!ambient) {
                drawArc(color.copy(alpha = 0.18f), Face.START, sweep, false, topLeft, arcSize, style = Stroke(glow, cap = StrokeCap.Round))
            }
            drawArc(
                if (ambient) VColor.TextSecondary else color,
                Face.START, sweep, false, topLeft, arcSize, style = Stroke(w * 1.1f, cap = StrokeCap.Round),
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

// ------------------------------------------------------------------------------------ text

/** The plain-language sentence that owns the screen: extra-bold, tight. Always whole (lists scroll; no ellipsis). */
@Composable
fun HeroStatus(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = VType.Title, color = VColor.Text, textAlign = TextAlign.Center, modifier = modifier)
}

/** A short caption-sized line (notes, sources, coverage). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = VColor.TextSecondary) {
    Text(text, style = VType.Caption, color = color, textAlign = TextAlign.Center, modifier = modifier)
}

/** A group heading inside a long list (Settings): sentence case, quiet, a heading for TalkBack. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, color: Color = VColor.TextSecondary) {
    Text(
        text, style = VType.Label, color = color, textAlign = TextAlign.Center,
        modifier = modifier.padding(top = VSpace.s).semantics { heading() },
    )
}

/** A screen's title: extra-bold, tight, centred, one heading (no label above it). */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, color: Color = VColor.Text) {
    Text(
        text, style = VType.Title, color = color, textAlign = TextAlign.Center,
        modifier = modifier.semantics { heading() },
    )
}

/** A sentence in a list: always whole (the list scrolls), never cut with an ellipsis. */
@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = VColor.TextSecondary) {
    Text(text, style = VType.Body, color = color, textAlign = TextAlign.Center, modifier = modifier)
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
    /** A longer label: the Label size and room for three lines, so it is never cut with an ellipsis. */
    wide: Boolean = false,
    /** The largest the wearer's font size makes the label (1.5 times by default). */
    maxFontScale: Float = 1.5f,
) {
    val m = modifier.fillMaxWidth().defaultMinSize(minHeight = VSize.touch)
    // On a small face or at large text the icon gives its width to the words, so no word breaks mid-way
    // ("Allow microphon/e" was seen at 192 dp).
    val roomy = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 200 &&
        androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.15f
    val iconSlot: (@Composable BoxScope.() -> Unit)? = icon?.takeIf { roomy }?.let {
        { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
    }
    val labelSlot: @Composable RowScope.() -> Unit = {
        // Whole, on as many lines as it needs: a button never hides part of what it does.
        // (The Wear button's label slot would otherwise cap it at three lines with an ellipsis.)
        Text(label, style = (if (wide) VType.Label else VType.TitleSmall).capFont(maxFontScale), maxLines = Int.MAX_VALUE)
    }
    when (style) {
        PillStyle.PRIMARY -> Button(onClick = onClick, modifier = m, enabled = enabled, icon = iconSlot, label = labelSlot)
        PillStyle.TONAL -> FilledTonalButton(onClick = onClick, modifier = m, enabled = enabled, icon = iconSlot, label = labelSlot)
        PillStyle.OUTLINED -> OutlinedButton(onClick = onClick, modifier = m, enabled = enabled, icon = iconSlot, label = labelSlot)
    }
}

/**
 * SWITCH ROW — a setting that is on or off: Wear Material 3 `SwitchButton`, the label (what it is) and a real switch
 * (its state), plus an optional [secondary] line (the hours of quiet time). Spoken as a switch with its state, so
 * the state is never only a word inside the label ("Tono: Sí").
 */
@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, secondary: String? = null) {
    androidx.wear.compose.material3.SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = VSize.touch),
        secondaryLabel = secondary?.let { { Text(it, maxLines = Int.MAX_VALUE) } },
        label = { Text(label, style = VType.TitleSmall.capFont(1.5f), maxLines = Int.MAX_VALUE) },
    )
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
            .pressable(onClick = onClick)
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
 * METRIC ROW — tinted icon, muted label, value, optional detail.
 *
 * WHAT LOOKS TAPPABLE IS TAPPABLE. With [onClick] it is an action: a card, fully rounded like the Wear buttons it
 * sits among, a chevron at the end, press feedback, the whole row the 48 dp target. Without it, it is information
 * and looks like it: no card, no fill, no chevron, nothing that invites a tap that would do nothing.
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
    val shape = RoundedCornerShape(VRadius.card)
    val action = onClick != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let {
                if (action) it.defaultMinSize(minHeight = VSize.touch).clip(shape).background(VColor.Card).pressable(onClick = onClick!!)
                else it.semantics(mergeDescendants = true) { }
            }
            .padding(horizontal = if (action) VSpace.l else VSpace.s, vertical = if (action) VSpace.s + 2.dp else VSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VSpace.s + 2.dp),
    ) {
        // On a small face or at large text the icon gives its width to the words, so no word breaks mid-way.
        val roomy = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 200 &&
            androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.15f
        if (roomy) Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = VType.Caption.copy(textAlign = TextAlign.Start), color = VColor.TextSecondary)
            Text(
                value, style = (if (action) VType.TitleSmall else VType.Label).copy(textAlign = TextAlign.Start),
                color = VColor.Text,
            )
            detail?.let { Text(it, style = VType.Caption.copy(textAlign = TextAlign.Start), color = VColor.TextSecondary) }
        }
        when {
            trailing != null -> trailing()
            action -> Icon(VoxIcons.Forward, contentDescription = null, tint = VColor.TextSecondary, modifier = Modifier.size(18.dp))
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
