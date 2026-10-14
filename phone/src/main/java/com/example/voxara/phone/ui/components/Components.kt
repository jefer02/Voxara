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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.R
import com.example.voxara.phone.model.Freshness
import com.example.voxara.phone.ui.freshnessText
import com.example.voxara.phone.ui.theme.LocalReduceMotion
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.wordRes

/** Readable line length on tablets and unfolded foldables. */
val ContentMaxWidth = 720.dp
val ScreenGutter = 16.dp
val TouchTarget = 48.dp

// ------------------------------------------------------------------------------------ screen

/**
 * A screen with an iOS-like large title that collapses as the list scrolls, pull to refresh,
 * and content centered to a readable width on large screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoxScreen(
    title: String,
    onBack: (() -> Unit)? = null,
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
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
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

@Composable
fun VoxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Vox.colors
    val body = @Composable {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, color = c.surface, contentColor = c.text) { body() }
    } else {
        Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = c.surface, contentColor = c.text) { body() }
    }
}

/** Small uppercase label above a group, like an inset grouped list. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Vox.colors.text2,
        modifier = modifier.padding(start = 16.dp, top = 12.dp, bottom = 0.dp).semantics { heading() },
    )
}

/** A rounded inset group of rows separated by hairlines. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = Vox.colors.surface, contentColor = Vox.colors.text) {
        Column(content = content)
    }
}

@Composable
fun GroupDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = Vox.colors.outline)

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
        color = Vox.colors.zoneInk(RiskZone.MODERATE),
        modifier = Modifier
            .clip(CircleShape)
            .background(Vox.colors.surface2)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun RowLabels(title: String, subtitle: String?, pending: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Vox.colors.text)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Vox.colors.text2)
        if (pending) PendingTag()
    }
}

@Composable
private fun RowIcon(icon: ImageVector?, tint: Color?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint ?: Vox.colors.brand, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
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
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
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
            IconButton(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onMinus() },
                enabled = canMinus, modifier = Modifier.size(TouchTarget),
            ) { Icon(VoxIcons.Minus, contentDescription = stringResource(R.string.action_decrease, title)) }
            Text(
                value, style = MaterialTheme.typography.titleMedium, color = Vox.colors.text,
                modifier = Modifier.widthIn(min = 64.dp).semantics { stateDescription = value },
                maxLines = 1,
            )
            IconButton(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onPlus() },
                enabled = canPlus, modifier = Modifier.size(TouchTarget),
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
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    modifier = Modifier.heightIn(min = TouchTarget),
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
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
    if (tonal) {
        FilledTonalButton(onClick = onClick, modifier = m, enabled = enabled, shape = CircleShape, content = content)
    } else {
        Button(
            onClick = onClick, modifier = m, enabled = enabled, shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Vox.colors.brand, contentColor = if (Vox.colors.dark) Color.Black else Color.White),
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

/** Icon + word: a zone is readable without colour. */
@Composable
fun ZoneBadge(zone: RiskZone, modifier: Modifier = Modifier) {
    val ink = Vox.colors.zoneInk(zone)
    Row(
        modifier
            .clip(CircleShape)
            .background(Vox.colors.surface2)
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
    val track = Vox.colors.surface2
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
    val tint = if (f == Freshness.STALE) c.zoneInk(RiskZone.MODERATE) else c.text2
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
        Icon(icon, contentDescription = null, tint = Vox.colors.brand, modifier = Modifier.size(32.dp))
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
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
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
            Icon(icon, contentDescription = null, tint = c.brand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
