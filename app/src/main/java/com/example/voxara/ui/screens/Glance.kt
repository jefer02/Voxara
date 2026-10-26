package com.example.voxara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import com.example.voxara.ui.design.wholeSentencesToFit
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.design.Face
import com.example.voxara.ui.design.LevelNumber
import com.example.voxara.ui.design.LocalFigureScale
import com.example.voxara.ui.design.LocalTextScale
import com.example.voxara.ui.design.heroScaled
import com.example.voxara.ui.design.scaled
import com.example.voxara.ui.design.OrbitStage
import com.example.voxara.ui.design.pressable
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VoxTone
import com.example.voxara.ui.design.animateTone
import com.example.voxara.ui.design.gaugeFraction
import com.example.voxara.ui.design.row
import com.example.voxara.ui.theme.LocalReduceMotion

/** Font scale from which glance pages switch to their compact arrangement (smaller figure, no extra label). */
private const val COMPACT_FONT_SCALE = 1.15f

/** Screens narrower than this (dp) always use the compact hero: the arc's gap is too small for the foot. */
private const val COMPACT_SCREEN_DP = 200

/**
 * True at large system font sizes or on the smallest round screens, where a page drops its optional
 * lines (caption, foot) to stay inside the ring; pages repeat the foot as their first detail.
 */
@Composable
internal fun isCompactFont(): Boolean =
    LocalDensity.current.fontScale >= COMPACT_FONT_SCALE || LocalConfiguration.current.screenWidthDp < COMPACT_SCREEN_DP

/** Largest font scale Tono's line on the hero follows. */
private const val STATUS_MAX_FONT_SCALE = 1.3f

/** Largest font scale the big figure follows: past it the figure stays put and the words around it keep growing. */
private const val FIGURE_MAX_SCALE = 1.3f

/** [style] with its size capped so the figure never exceeds [FIGURE_MAX_SCALE] times its design size. */
@Composable
private fun capFigure(style: TextStyle): TextStyle {
    val scale = LocalDensity.current.fontScale
    return if (scale <= FIGURE_MAX_SCALE) style else style.copy(
        fontSize = style.fontSize * (FIGURE_MAX_SCALE / scale),
        lineHeight = style.lineHeight * (FIGURE_MAX_SCALE / scale),
    )
}

/** Where a page sits in the pager (index, count), for the "Page 3 of 6, Today" description. Null outside it. */
internal val LocalPageInfo = compositionLocalOf<Pair<Int, Int>?> { null }

/** The tier tone for [zone], crossfading between zones (instant with reduced motion). */
@Composable
internal fun zoneTone(zone: RiskZone): VoxTone = animateTone(VColor.tone(zone), instant = LocalReduceMotion.current)

/** The 0..1 level on the wave scale; 0 when nothing is measured. */
internal fun waveOf(dba: Double): Float = if (dba > 0.0) gaugeFraction(dba.toFloat()) else 0f

/** A page's identity: the name TalkBack says ("Page 1 of 3, Now"), its icon and the accent its details carry. */
internal class PageIdentity(val name: String, val icon: ImageVector, val accent: Color)

/** The identity of [page]: the name is spoken, not drawn (the pages say what they are: the unit, the switch, the title). */
@Composable
internal fun pageIdentity(page: GlancePage, accent: Color = VColor.AccentInk): PageIdentity = when (page) {
    GlancePage.NOW -> PageIdentity(stringResource(R.string.page_now), VoxIcons.Waves, accent)
    GlancePage.DOSE -> PageIdentity(stringResource(R.string.page_dose), VoxIcons.Calendar, accent)
    GlancePage.MENU -> PageIdentity(stringResource(R.string.page_more), VoxIcons.Tune, accent)
}

/**
 * PAGE STAGE — what every glance page is built on: the wave rings filling the ring's inside, a scrim
 * for legibility, the page's own [picture] on the ring, then two groups of text. At the top of the
 * ring, the [center] content (figure, caption, zone tag). In the ring's gap at the bottom, just above
 * the page indicator, the [foot] (a few short lines) and the [nameSlot] (the Dose page's Today/Week
 * switch, the headphones chip; nothing on Now: its figure and unit say what it is). The groups flow in
 * one column, so nothing overlaps; if very large text needs more than the screen the page grows and
 * scrolls. At large font sizes [center] is told to go compact and the [foot] is dropped (pages show it
 * as their first detail).
 *
 * The [foot] is told how much [room] the gap leaves under the centre, so it can show less (one line, or
 * nothing) rather than crowd the figure on a small display; whatever it leaves out is the page's first
 * detail. The whole page is spoken as [description], prefixed with its place in the pager.
 */
@Composable
internal fun PageStage(
    identity: PageIdentity,
    wave: Float,
    waveTone: VoxTone,
    description: String,
    modifier: Modifier = Modifier,
    listening: Boolean = false,
    stereo: () -> Float = { 0f },
    stereoColor: Color = Color.Unspecified,
    waves: Boolean = true,
    nameSlot: (@Composable () -> Unit)? = null,
    picture: @Composable () -> Unit,
    foot: (@Composable ColumnScope.(room: Dp) -> Unit)? = null,
    center: @Composable ColumnScope.(compact: Boolean) -> Unit,
) {
    val compact = isCompactFont()
    val info = LocalPageInfo.current
    val spoken = (info?.let { stringResource(R.string.page_cd, it.first + 1, it.second, identity.name) + ". " } ?: "") + description
    OrbitStage(
        wave = wave, waveColor = waveTone.fill,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        listening = listening, stereo = stereo, stereoColor = stereoColor, waves = waves, picture = picture,
    ) {
        val d = minOf(maxWidth, maxHeight)
        // With a foot, the page is exactly one screen and the foot takes what the centre leaves; without one (large
        // text, or a name slot only) it is at least one screen and grows.
        val flex = !compact && foot != null
        Column(
            Modifier.fillMaxWidth()
                .then(if (flex) Modifier.height(d) else Modifier.heightIn(min = d))
                .padding(top = d * Face.CENTER_TOP, bottom = d * Face.FOOT_BOTTOM),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                Modifier.widthIn(max = d * Face.CENTER_WIDTH),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) { center(compact) }
            if (flex) {
                BoxWithConstraints(Modifier.weight(1f).widthIn(max = d * Face.FOOT_WIDTH), contentAlignment = Alignment.BottomCenter) {
                    val room = maxHeight
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        foot(room)
                        nameSlot?.invoke()
                    }
                }
            } else {
                Column(Modifier.widthIn(max = d * Face.FOOT_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
                    nameSlot?.invoke()
                }
            }
        }
    }
}

/**
 * The page's figure: tabular rolling digits over a soft glow, with a smaller companion [suffix]
 * ("dBA", "%") on the same baseline and an optional [prefix] ("≈"). Capped at 1.3× at large fonts.
 */
@Composable
internal fun HeroFigure(value: Int?, glow: Color, compact: Boolean, suffix: String, prefix: String = "") {
    LevelNumber(
        value, glow = glow, style = capFigure(if (compact) VType.Display else VType.Hero).scaled(LocalFigureScale.current),
        suffix = suffix, prefix = prefix,
    )
}

/** A caption under a figure: what the figure is a share of. */
@Composable
internal fun FigureCaption(text: String) {
    Text(text, style = VType.Caption.heroScaled(), color = VColor.TextSecondary, textAlign = TextAlign.Center)
}

/** Tono's line as it will be drawn on the hero: the words and the type step they fit at. */
internal data class FittedLine(val text: String, val style: TextStyle)

/**
 * TONO'S LINE ON THE HERO — a whole sentence or nothing, never an ellipsis. Tries the text in at most [maxLines]
 * lines of [maxWidth] at the [style] size (Title small), then one and two steps down (never under 12 sp); if it
 * still does not fit, the longest run of whole sentences from its start ([wholeSentencesToFit]); otherwise null,
 * and the caller puts the full line under the hero instead. Held at 1.3 times the wearer's font size, like the figure.
 */
@Composable
internal fun rememberFittedStatus(text: String, maxWidth: Dp, style: TextStyle = VType.TitleSmall, maxLines: Int = 2): FittedLine? {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val capped = if (density.fontScale > STATUS_MAX_FONT_SCALE) STATUS_MAX_FONT_SCALE / density.fontScale else 1f
    val base = style.heroScaled().scaled(capped)
    return remember(text, maxWidth, density.density, density.fontScale, base, maxLines) {
        if (maxLines <= 0) return@remember null
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        fun styled(step: Float): TextStyle {
            val size = maxOf(base.fontSize.value * step, 12f)
            return base.copy(fontSize = size.sp, lineHeight = (base.lineHeight.value * size / base.fontSize.value).sp)
        }
        fun fits(style: TextStyle, t: String): Boolean =
            !measurer.measure(t, style, maxLines = maxLines, constraints = Constraints(maxWidth = widthPx)).hasVisualOverflow
        val steps = listOf(1f, 0.92f, 0.85f).map(::styled)
        steps.firstOrNull { fits(it, text) }?.let { return@remember FittedLine(text, it) }
        val smallest = steps.last()
        wholeSentencesToFit(text) { fits(smallest, it) }?.let { FittedLine(it, smallest) }
    }
}

/**
 * TONO'S TAG — Tono's one line as a small tag (a sparkle marks wording from the cloud AI): a tap opens Ask.
 * On the hero it shows a [fitted] line (whole sentences only, [rememberFittedStatus]); under the hero ([fitted]
 * null) the whole [status] wraps on as many lines as it needs. [minHeight] makes it a full 48 dp target where
 * there is room (on the hero the platform widens the touch area of a smaller tag to 48 dp).
 */
@Composable
internal fun TonoTag(
    status: String,
    cloud: Boolean,
    askLabel: String,
    fitted: FittedLine?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 0.dp,
) {
    val shape = RoundedCornerShape(14.dp)
    val scale = LocalTextScale.current
    val icon = 14.dp * scale
    val padH = 10.dp
    val cloudNote = stringResource(R.string.home_source_ai)
    Row(
        modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .pressable(onClick = onClick, onClickLabel = askLabel)
            .semantics { contentDescription = (if (cloud) "$cloudNote. " else "") + status }
            .padding(horizontal = padH, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        Icon(if (cloud) VoxIcons.Sparkle else VoxIcons.Ask, contentDescription = null, tint = VColor.AccentInk, modifier = Modifier.size(icon))
        if (fitted != null) Text(fitted.text, style = fitted.style, color = VColor.Text, textAlign = TextAlign.Center)
        else Text(status, style = VType.Label, color = VColor.Text, textAlign = TextAlign.Center)
    }
}

/** The width Tono's words get inside a tag [tagWidth] wide (its padding and icon taken off). */
@Composable
internal fun tonoTextWidth(tagWidth: Dp): Dp = tagWidth - 10.dp * 2 - 14.dp * LocalTextScale.current - 4.dp

/** A foot line: one short sentence under the figure. */
@Composable
internal fun FootLine(text: String, modifier: Modifier = Modifier, color: Color = VColor.TextSecondary) {
    Text(
        text, style = VType.Caption.heroScaled(), color = color, textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A neutral note with an info icon ("Uncalibrated estimate"): informative, never a warning, so it
 * is muted: amber and crimson are kept for real zone warnings.
 */
@Composable
internal fun FootNote(text: String, modifier: Modifier = Modifier) {
    // The icon is part of the text (inline, before the first word), so when the line wraps it stays beside the
    // words instead of drifting to the edge of a wide box.
    val style = VType.Caption.heroScaled()
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        appendInlineContent("info", "ⓘ")
        append(" ")
        append(text)
    }
    val inline = mapOf(
        "info" to androidx.compose.foundation.text.InlineTextContent(
            androidx.compose.ui.text.Placeholder(style.fontSize, style.fontSize, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter),
        ) { Icon(VoxIcons.Info, contentDescription = null, tint = VColor.TextSecondary, modifier = Modifier.fillMaxSize()) },
    )
    Text(
        annotated, style = style, color = VColor.TextSecondary, textAlign = TextAlign.Center, inlineContent = inline,
        modifier = modifier.semantics { contentDescription = text },
    )
}

/** The foot as the first detail row, shown only at large font sizes where the hero drops it. */
internal fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope.footDetail(
    compact: Boolean,
    foot: @Composable ColumnScope.() -> Unit,
) {
    if (compact) row { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = foot) }
}
