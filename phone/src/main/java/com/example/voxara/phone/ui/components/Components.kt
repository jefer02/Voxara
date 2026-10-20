package com.example.voxara.phone.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.R
import com.example.voxara.phone.model.Freshness
import com.example.voxara.phone.ui.freshnessText
import com.example.voxara.phone.ui.theme.EyebrowStyle
import com.example.voxara.phone.ui.theme.LocalReduceMotion
import com.example.voxara.phone.ui.theme.VoxNum
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.animateTone
import com.example.voxara.ui.design.wordRes

/** Readable line length on tablets and unfolded foldables. */
val ContentMaxWidth = 720.dp
val ScreenGutter = 16.dp
val TouchTarget = 48.dp

// ------------------------------------------------------------------------------------ screen

/**
 * A screen with an editorial large title (uppercase [eyebrow] with a pulsing dot over an
 * extra-bold title) that collapses as the list scrolls, pull to refresh, and content centered to a
 * readable width on large screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoxScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    eyebrow: String? = stringResource(R.string.home_title),
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val c = Vox.colors
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = c.background,
        bottomBar = bottomBar,
        topBar = {
            LargeTopAppBar(
                title = {
                    // The template's editorial header: a small uppercase label with a pulsing dot
                    // above the extra-bold title. The label goes away once the bar collapses.
                    Column {
                        if (eyebrow != null && scroll.state.collapsedFraction < 0.5f) EditorialLabel(eyebrow)
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(VoxIcons.Back, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.background,
                    scrolledContainerColor = c.surface,
                    titleContentColor = c.text,
                    navigationIconContentColor = c.text,
                    actionIconContentColor = c.text,
                ),
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        val list = @Composable {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                    start = ScreenGutter, end = ScreenGutter,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
        if (onRefresh != null) {
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { list() }
        } else {
            list()
        }
    }
}

/** Width-limited, full-width item for [VoxScreen] lists. */
fun Modifier.contentWidth(): Modifier = this.widthIn(max = ContentMaxWidth).fillMaxWidth()

// ------------------------------------------------------------------------------------ cards

/** The template card: 22 dp radius, 1 dp border, a soft shadow (light theme). */
@Composable
fun VoxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    shape: Shape = MaterialTheme.shapes.large,
    elevation: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Vox.colors
    val body = @Composable {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    val border = BorderStroke(1.dp, c.outline)
    val shadow = if (c.dark) 0.dp else elevation
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = c.surface, contentColor = c.text, border = border, shadowElevation = shadow) { body() }
    } else {
        Surface(modifier = modifier, shape = shape, color = c.surface, contentColor = c.text, border = border, shadowElevation = shadow) { body() }
    }
}

/** Small uppercase label above a group, like an inset grouped list. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = EyebrowStyle,
        color = Vox.colors.text3,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 0.dp).semantics { heading() },
    )
}

/** A rounded inset group of rows separated by hairlines. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Vox.colors
    Surface(
        modifier = modifier, shape = MaterialTheme.shapes.medium, color = c.surface, contentColor = c.text,
        border = BorderStroke(1.dp, c.outline), shadowElevation = if (c.dark) 0.dp else 2.dp,
    ) {
        Column(content = content)
    }
}

@Composable
fun GroupDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 1.dp, color = Vox.colors.outline)

/** A footnote under a group. */
@Composable
fun GroupNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Vox.colors.text2, modifier = modifier.padding(horizontal = 16.dp))
}

@Composable
fun PendingTag() {
    Text(
        stringResource(R.string.pending_sync),
        style = MaterialTheme.typography.labelSmall,
        color = Vox.colors.caution,
        modifier = Modifier
            .clip(CircleShape)
            .background(Vox.colors.surface2)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun RowLabels(title: String, subtitle: String?, pending: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Vox.colors.text)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Vox.colors.text2)
        if (pending) PendingTag()
    }
}

@Composable
private fun RowIcon(icon: ImageVector?, tint: Color?) {
    if (icon != null) {
        IconTile(icon, tint ?: Vox.colors.brand)
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    pending: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val row = @Composable {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(icon, iconTint)
            RowLabels(title, subtitle, pending, Modifier.weight(1f))
            if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
            else if (onClick != null) Icon(VoxIcons.Forward, contentDescription = null, tint = Vox.colors.text3, modifier = Modifier.size(18.dp))
        }
    }
    if (onClick != null) Surface(onClick = onClick, color = Color.Transparent, contentColor = Vox.colors.text) { row() } else row()
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    pending: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch) {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onChange(it)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, null)
        RowLabels(title, subtitle, pending, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        VoxSwitch(checked = checked)
    }
}

/** A value with − and + (48 dp targets each), for bounded numbers like the alert level. */
@Composable
fun StepperRow(
    title: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    canMinus: Boolean,
    canPlus: Boolean,
    subtitle: String? = null,
    pending: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    // Label above, control below: nothing gets squeezed at large font scales.
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        RowLabels(title, subtitle, pending, Modifier.padding(end = 8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            val buttons = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Vox.colors.surface2, contentColor = Vox.colors.brand,
            )
            FilledTonalIconButton(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onMinus() },
                enabled = canMinus, modifier = Modifier.size(TouchTarget), colors = buttons,
            ) { Icon(VoxIcons.Minus, contentDescription = stringResource(R.string.action_decrease, title)) }
            Text(
                value, style = VoxNum.medium, color = Vox.colors.text, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 80.dp).semantics { stateDescription = value },
                maxLines = 1,
            )
            FilledTonalIconButton(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onPlus() },
                enabled = canPlus, modifier = Modifier.size(TouchTarget), colors = buttons,
            ) { Icon(VoxIcons.Plus, contentDescription = stringResource(R.string.action_increase, title)) }
        }
    }
}

/** A labelled pill segmented control (2–4 options). */
@Composable
fun <T> ChoiceRow(
    title: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
    pending: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null) RowLabels(title, subtitle, pending)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            options.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    colors = segmentColors(),
                    icon = {},
                    modifier = Modifier.heightIn(min = TouchTarget).fillMaxHeight(),
                    // Four or more segments get the smaller label, so "120 min" keeps its unit at 360 dp;
                    // a long label ("Follow system", large fonts) wraps to a second line instead of cutting.
                    label = {
                        Text(
                            label, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            style = if (options.size >= 4) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }
    }
}

/** The template's preset pills: the active one in the accent fill with dark text. */
@Composable
private fun segmentColors(): SegmentedButtonColors {
    val c = Vox.colors
    return SegmentedButtonDefaults.colors(
        activeContainerColor = c.accent.fill, activeContentColor = c.accent.onFill, activeBorderColor = c.accent.fill,
        inactiveContainerColor = c.surface2, inactiveContentColor = c.text, inactiveBorderColor = c.outline,
    )
}

// ------------------------------------------------------------------------------------ buttons

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tonal: Boolean = false,
    enabled: Boolean = true,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    val m = modifier.heightIn(min = 52.dp)
    val c = Vox.colors
    if (tonal) {
        FilledTonalButton(
            onClick = onClick, modifier = m, enabled = enabled, shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.surface2, contentColor = c.brand),
            content = content,
        )
    } else {
        Button(
            onClick = onClick, modifier = m, enabled = enabled, shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = c.accent.fill, contentColor = c.accent.onFill),
            content = content,
        )
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = TouchTarget)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Vox.colors.brand)
    }
}

// ------------------------------------------------------------------------------------ zones

/** The template status badge: icon + word in the tier ink on its soft tint. A zone is readable without colour. */
@Composable
fun ZoneBadge(zone: RiskZone, modifier: Modifier = Modifier) {
    val tone = animateTone(Vox.colors.level(zone), instant = LocalReduceMotion.current)
    val ink = tone.ink
    Row(
        modifier
            .clip(CircleShape)
            .background(tone.soft)
            .border(1.dp, ink.copy(alpha = 0.7f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(VoxIcons.zone(zone), contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Text(stringResource(zone.wordRes()), style = MaterialTheme.typography.labelMedium, color = ink)
    }
}

// ------------------------------------------------------------------------------------ ring

/** A thick ring gauge with a spring (instant when motion is reduced). */
@Composable
fun RingGauge(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val target = fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(
        target,
        animationSpec = if (reduce) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "ring",
    )
    val track = Vox.colors.track
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (shown > 0f) drawArc(color, -90f, 360f * shown, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

// ------------------------------------------------------------------------------------ freshness

/** "Updated 4 min ago" / "As of 14:05" / "Last known · …" — stale figures say so. */
@Composable
fun FreshnessLine(atMs: Long?, nowMs: Long, modifier: Modifier = Modifier) {
    val f = Freshness.of(atMs, nowMs)
    val c = Vox.colors
    val tint = if (f == Freshness.STALE) c.caution else c.text2
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(VoxIcons.Clock, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(freshnessText(atMs, nowMs), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

// ------------------------------------------------------------------------------------ states

/** A calm placeholder while data loads (a gentle pulse; still when motion is reduced). */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 20.dp, shape: RoundedCornerShape = RoundedCornerShape(10.dp)) {
    val alpha = if (LocalReduceMotion.current) 0.6f else {
        val t = rememberInfiniteTransition(label = "skeleton")
        val a by t.animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
        a
    }
    Box(modifier.height(height).clip(shape).background(Vox.colors.surface2.copy(alpha = alpha)).clearAndSetSemantics { })
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier, lines: Int = 3) {
    VoxCard(modifier) {
        Skeleton(Modifier.fillMaxWidth(0.4f), height = 14.dp)
        repeat(lines) { Skeleton(Modifier.fillMaxWidth(if (it == lines - 1) 0.6f else 1f)) }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    VoxCard(modifier) {
        IconTile(icon, Vox.colors.brand)
        Text(title, style = MaterialTheme.typography.titleMedium, color = Vox.colors.text)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Vox.colors.text2)
        if (action != null && onAction != null) PillButton(action, onAction, tonal = true)
    }
}

/** A banner with an icon, used for problems (with help) and gentle prompts. */
@Composable
fun Banner(
    icon: ImageVector,
    title: String,
    body: String,
    tint: Color,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    VoxCard(modifier, padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(icon, tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Vox.colors.text)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Vox.colors.text2)
                if (action != null && onAction != null) LinkButton(action, onAction, Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Stat with its own ring, zone (icon + word) and the time it was true. */
@Composable
fun StatCard(
    icon: ImageVector,
    label: String,
    percent: Int,
    sub: String,
    asOfMs: Long?,
    nowMs: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val zone = RiskZone.ofPercent(percent.toDouble())
    val c = Vox.colors
    val stale = Freshness.of(asOfMs, nowMs) == Freshness.STALE
    val cd = stringResource(R.string.stat_cd, label, percent, stringResource(zone.wordRes()), freshnessText(asOfMs, nowMs))
    VoxCard(modifier.semantics(mergeDescendants = true) { contentDescription = cd }, onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, c.brand, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(label.uppercase(), style = EyebrowStyle, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(percent / 100f, c.zoneFill(zone), size = 52.dp, stroke = 7.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.value_percent, percent),
                style = MaterialTheme.typography.displaySmall,
                color = if (stale) c.text2 else c.text,
                maxLines = 1,
            )
        }
        ZoneBadge(zone)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = c.text2)
        FreshnessLine(asOfMs, nowMs)
    }
}
