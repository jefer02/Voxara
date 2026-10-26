package com.example.voxara.phone.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.example.voxara.phone.ui.theme.LocalReduceMotion
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.theme.VoxNum
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.WaveLines
import com.example.voxara.ui.design.animateTone
import com.example.voxara.ui.design.rememberWavePhase
import com.example.voxara.ui.design.wordRes

/** Readable line length on tablets and unfolded foldables. */
val ContentMaxWidth = 720.dp
val ScreenGutter = 16.dp
val TouchTarget = 48.dp

// ------------------------------------------------------------------------------------ screen

/**
 * A screen: a large title that collapses as the list scrolls (Material 3), pull to refresh with the wave indicator,
 * and content centred to a readable width on large screens. With [largeTitle] off there is no bar at all and the
 * screen draws its own header (Home: the link line and the sync button).
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
    largeTitle: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val c = Vox.colors
    Scaffold(
        modifier = if (largeTitle) Modifier.nestedScroll(scroll.nestedScrollConnection) else Modifier,
        containerColor = c.background,
        bottomBar = bottomBar,
        topBar = {
            if (largeTitle) {
                LargeTopAppBar(
                    title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack, modifier = Modifier.size(TouchTarget)) {
                                Icon(VoxIcons.Back, contentDescription = stringResource(R.string.action_back))
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = c.background,
                        scrolledContainerColor = c.background,
                        titleContentColor = c.text,
                        navigationIconContentColor = c.text,
                        actionIconContentColor = c.text,
                    ),
                    scrollBehavior = scroll,
                )
            }
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
        if (onRefresh != null) {
            val state = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = refreshing, onRefresh = onRefresh, state = state, modifier = Modifier.fillMaxSize(),
                indicator = { WaveRefreshIndicator(state, refreshing, Modifier.align(Alignment.TopCenter)) },
            ) { list() }
        } else {
            list()
        }
    }
}

/**
 * The pull-to-refresh indicator: three sine lines in the accent that rise with the pull and keep moving while the sync
 * runs (the same waves as the watch's rings). Still when motion is reduced; the amplitude still follows the pull.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaveRefreshIndicator(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier = Modifier) {
    val c = Vox.colors
    val reduce = LocalReduceMotion.current
    val waves = remember { WaveLines() }
    val visible = refreshing || state.distanceFraction > 0f
    val phase = rememberWavePhase(running = visible && !reduce, speed = 5.5f)
    val height = 72.dp
    Canvas(
        modifier.fillMaxWidth().height(height).clearAndSetSemantics { }.graphicsLayer {
            val f = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
            translationY = (f - 1f) * height.toPx() * 0.6f
            alpha = f
        },
    ) {
        val f = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
        waves.draw(this, phase.value, 0.25f + 0.75f * f, c.accent.fill)
    }
}

/** Width-limited, full-width item for [VoxScreen] lists. */
fun Modifier.contentWidth(): Modifier = this.widthIn(max = ContentMaxWidth).fillMaxWidth()

// ------------------------------------------------------------------------------------ cards

/**
 * Press feedback: the target gives a little (97 %) while it is pressed and springs back; nothing moves when motion is
 * reduced. Pass the same [source] to the clickable.
 */
@Composable
fun Modifier.pressFeedback(source: InteractionSource, amount: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(
        if (pressed && !reduce) amount else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "press",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A card: flat (no shadow, depth is colour), 20 dp radius, a hairline border; a tap gives press feedback. */
@Composable
fun VoxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Vox.colors
    val body = @Composable {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    val border = BorderStroke(1.dp, c.outline)
    if (onClick != null) {
        val source = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick, modifier = modifier.pressFeedback(source), shape = shape, color = c.surface, contentColor = c.text,
            border = border, interactionSource = source,
        ) { body() }
    } else {
        Surface(modifier = modifier, shape = shape, color = c.surface, contentColor = c.text, border = border) { body() }
    }
}

/** The title of a group of rows: sentence case, bold, quiet. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = Vox.colors.text2,
        modifier = modifier.padding(start = 4.dp, top = 14.dp).semantics { heading() },
    )
}

/** A rounded group of rows separated by hairlines. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Vox.colors
    Surface(
        modifier = modifier, shape = MaterialTheme.shapes.medium, color = c.surface, contentColor = c.text,
        border = BorderStroke(1.dp, c.outline),
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
    if (onClick != null) {
        val source = remember { MutableInteractionSource() }
        Surface(onClick = onClick, color = Color.Transparent, contentColor = Vox.colors.text, interactionSource = source) { row() }
    } else row()
}

/**
 * A row that opens in place: the title and a chevron, the [body] under it once tapped. Used to fold long how-tos
 * (battery tips, calibration, troubleshooting) into the row they belong to.
 */
@Composable
fun ExpandableRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val reduce = LocalReduceMotion.current
    // The forward chevron turns down when closed and up when open.
    val turn by animateFloatAsState(if (open) -90f else 90f, if (reduce) snap() else tween(200), label = "chevron")
    val stateText = stringResource(if (open) R.string.state_expanded else R.string.state_collapsed)
    Column(modifier.animateContentSize(if (reduce) snap() else spring(stiffness = Spring.StiffnessMediumLow))) {
        Surface(
            onClick = { open = !open }, color = Color.Transparent, contentColor = Vox.colors.text,
            modifier = Modifier.semantics { stateDescription = stateText },
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RowIcon(icon, iconTint)
                RowLabels(title, subtitle, false, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Icon(VoxIcons.Forward, contentDescription = null, tint = Vox.colors.text3, modifier = Modifier.size(18.dp).rotate(turn))
            }
        }
        if (open) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), content = body,
            )
        }
    }
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

/** A labelled segmented control (2–4 options): a filter, not navigation. */
@Composable
fun <T> ChoiceRow(
    title: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
    pending: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null) RowLabels(title, subtitle, pending)
        SegmentedChoice(options, selected, onSelect)
    }
}

/**
 * The Material 3 single-choice segmented button row, in the accent. Four or more segments get the smaller label so
 * "120 min" keeps its unit at 360 dp. When the longest WORD of a label would not fit its segment (large text, narrow
 * screen) the choice becomes a list of radio rows instead: a word is never broken, a label is never cut.
 */
@Composable
fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = if (options.size >= 4) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val segment = maxWidth / options.size
        val longestWord = options.maxOf { (_, label) -> label.split(' ').maxOf { measurer.measure(it, labelStyle, maxLines = 1, softWrap = false, density = density).size.width } }
        val fits = with(density) { longestWord.toDp() + 24.dp <= segment }
        if (!fits) {
            Column(Modifier.selectableGroup()) {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = TouchTarget)
                            .selectable(selected = value == selected, role = Role.RadioButton) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(value) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected, onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Vox.colors.accent.fill, unselectedColor = Vox.colors.text2),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = Vox.colors.text)
                    }
                }
            }
        } else {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = value == selected,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        colors = segmentColors(),
                        icon = {},
                        modifier = Modifier.heightIn(min = TouchTarget).fillMaxHeight(),
                        label = { Text(label, maxLines = 2, textAlign = TextAlign.Center, style = labelStyle) },
                    )
                }
            }
        }
    }
}

/** The active segment in the accent fill with its own text colour; the others quiet. */
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
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
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

/** A zone badge: icon + word in the tier ink on its soft tint. A zone is readable without colour. */
@Composable
fun ZoneBadge(zone: RiskZone, modifier: Modifier = Modifier) {
    val tone = animateTone(Vox.colors.level(zone), instant = LocalReduceMotion.current)
    val ink = tone.ink
    Row(
        modifier
            .clip(CircleShape)
            .background(tone.soft)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(VoxIcons.zone(zone), contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Text(stringResource(zone.wordRes()), style = MaterialTheme.typography.labelMedium, color = ink)
    }
}

// ------------------------------------------------------------------------------------ freshness

/** "Updated 4 min ago" / "As of 14:05" / "Last known · …" — stale figures say so. */
@Composable
fun FreshnessLine(atMs: Long?, nowMs: Long, modifier: Modifier = Modifier) {
    val f = Freshness.of(atMs, nowMs)
    val c = Vox.colors
    val tint = if (f == Freshness.STALE) c.caution else c.text2
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(VoxIcons.Clock, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(14.dp))
        Text(freshnessText(atMs, nowMs), style = MaterialTheme.typography.labelMedium, color = tint)
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
