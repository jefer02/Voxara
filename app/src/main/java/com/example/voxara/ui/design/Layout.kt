package com.example.voxara.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/** The list item shown first. Always 0 in the app; the review renders set it to show a page below its hero. */
val LocalInitialItem = androidx.compose.runtime.staticCompositionLocalOf { 0 }

/** Side inset [row] adds itself (0 in [VoxList], which pads the whole column; the round inset in [HeroList]). */
val LocalRowInset = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

/** Room at the bottom of a pager page that the page indicator may cover (0 outside the pager). */
val LocalPageInset = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

/** Height of the black fade that hides content scrolling under the time text (solid where the clock's digits are). */
private val TopScrim = 44.dp

/**
 * EDGE SCRIMS — black fades over the top and bottom edge of a scrolling page, under the time text and
 * the page indicator but over the content, so nothing is ever read through the clock or the dots.
 * The top one shows once the page has scrolled; the bottom one while there is more below ([bottomAtRest]
 * false: only once scrolled, so a hero page keeps its foot clear at rest). Both are inert on a black ground.
 */
@Composable
private fun BoxScope.EdgeScrims(state: TransformingLazyColumnState, bottomHeight: androidx.compose.ui.unit.Dp, bottomAtRest: Boolean) {
    val topAlpha by androidx.compose.animation.core.animateFloatAsState(if (state.canScrollBackward) 1f else 0f, label = "topScrim")
    val bottomOn = state.canScrollForward && (bottomAtRest || state.canScrollBackward)
    val bottomAlpha by androidx.compose.animation.core.animateFloatAsState(if (bottomOn) 1f else 0f, label = "bottomScrim")
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(TopScrim)
            .graphicsLayer { alpha = topAlpha }
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to VColor.Black, 0.6f to VColor.Black, 1f to androidx.compose.ui.graphics.Color.Transparent,
                )
            )
    )
    if (bottomHeight > 0.dp) {
        // Black right where the page dots sit (the lowest half), fading in above them: content never shows through the dots.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomHeight + 6.dp)
                .graphicsLayer { alpha = bottomAlpha }
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent, 0.5f to VColor.Black, 1f to VColor.Black,
                    )
                )
        )
    }
}

/** The transformation spec of the enclosing [VoxList]: items scale and fade as they leave the screen. */
val LocalTransformSpec = androidx.compose.runtime.staticCompositionLocalOf<TransformationSpec?> { null }

/**
 * One list item that follows the round screen: it keeps its own height, and scales/fades toward the
 * top and bottom edge with the list's transformation spec (so content slides under the clock instead
 * of colliding with it). Use instead of `item` inside [VoxList] / [HeroList].
 */
fun TransformingLazyColumnScope.row(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key) {
        val scope = this
        val spec = LocalTransformSpec.current
        // Items keep their own height (no transformedHeight): with it, a tall paragraph near the edge was laid out
        // shorter than it draws and the next item landed on its last lines (text over text in the review renders).
        // The edge effect stays: content scales and fades as it leaves the screen.
        val modifier = if (spec == null) Modifier else Modifier
            .graphicsLayer {
                with(scope) {
                    val progress = scrollProgress
                    with(spec) { applyContentTransformation(progress) }
                }
            }
        Box(modifier.padding(horizontal = LocalRowInset.current), contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * VOX LIST — every scrolling screen: Material 3 ScreenScaffold (time text, scroll indicator,
 * optional edge button) around a TransformingLazyColumn with rotary scrolling, round-screen side
 * insets and per-item scale/fade ([row]).
 */
@Composable
fun VoxList(
    modifier: Modifier = Modifier,
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: TransformingLazyColumnScope.() -> Unit,
) {
    val state = rememberTransformingLazyColumnState(initialAnchorItemIndex = LocalInitialItem.current)
    val spec = rememberTransformationSpec()
    val column: @Composable BoxScope.(PaddingValues) -> Unit = { padding ->
        // A little air under the time text, so the first line never touches it at large font sizes.
        val top = padding.calculateTopPadding() + VSpace.s
        val inset = LocalPageInset.current
        val bottom = padding.calculateBottomPadding() + inset + (if (inset > 0.dp) VSpace.m else 0.dp)
        CompositionLocalProvider(LocalTransformSpec provides spec) {
            TransformingLazyColumn(
                state = state,
                contentPadding = PaddingValues(top = top, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(VSpace.s),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = modifier.fillMaxSize().padding(horizontal = VSpace.roundInset),
                content = content,
            )
        }
        EdgeScrims(state, bottomHeight = inset, bottomAtRest = true)
    }
    if (edgeButton != null) {
        ScreenScaffold(scrollState = state, edgeButton = edgeButton, content = column)
    } else {
        ScreenScaffold(scrollState = state, content = column)
    }
}

/**
 * HERO LIST — a glance page: the first item is exactly one screen tall, so the hero is fully
 * visible without scrolling, centred on the display and under the time text; details follow below
 * and scale/fade in as they scroll up. [hero] receives a `Modifier` that is at least one screen
 * tall (it grows, and the page scrolls, when text at a very large font size needs more room).
 *
 * [onRotary] lets the page use the crown while it rests at its top: it gets each rotary movement (pixels,
 * positive clockwise) before the list does and returns true to take it. Once the page has scrolled, the crown
 * scrolls the list as always, so the two never fight.
 */
@Composable
fun HeroList(
    modifier: Modifier = Modifier,
    onRotary: ((Float) -> Boolean)? = null,
    hero: @Composable (Modifier) -> Unit,
    details: TransformingLazyColumnScope.() -> Unit = {},
) {
    val state = rememberTransformingLazyColumnState(initialAnchorItemIndex = LocalInitialItem.current)
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state, contentPadding = PaddingValues(0.dp)) { _ ->
        val rotary = if (onRotary == null) Modifier else Modifier.onPreRotaryScrollEvent { e -> !state.canScrollBackward && onRotary(e.verticalScrollPixels) }
        BoxWithConstraints(Modifier.fillMaxSize().then(rotary)) {
            val screenHeight = maxHeight
            CompositionLocalProvider(LocalTransformSpec provides spec, LocalRowInset provides VSpace.roundInset) {
                TransformingLazyColumn(
                    state = state,
                    contentPadding = PaddingValues(top = 0.dp, bottom = VSpace.xl + LocalPageInset.current),
                    verticalArrangement = Arrangement.spacedBy(VSpace.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = modifier.fillMaxSize(),
                ) {
                    item { hero(Modifier.fillMaxWidth().heightIn(min = screenHeight)) }
                    details()
                }
            }
            EdgeScrims(state, bottomHeight = LocalPageInset.current, bottomAtRest = false)
        }
    }
}

/** Label, value and -/+ (each a 48 dp target). The value is announced with the label. */
@Composable
fun ValueStepper(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseDescription: String,
    increaseDescription: String,
    modifier: Modifier = Modifier,
) {
    // The value on its own line, the two 48 dp buttons under it: on a 192 dp face there is no room for all three
    // in a row (the value broke across lines and "+" was pushed off the screen).
    Panel(modifier) {
        SectionLabel(label)
        Text(
            value, style = VType.Numeral.capFont(1.3f), color = VColor.Text, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label $value" },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VSpace.l, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onDecrease,
                modifier = Modifier.size(VSize.touch).semantics { contentDescription = decreaseDescription },
            ) { androidx.wear.compose.material3.Icon(VoxIcons.Minus, contentDescription = null, modifier = Modifier.size(22.dp)) }
            FilledTonalIconButton(
                onClick = onIncrease,
                modifier = Modifier.size(VSize.touch).semantics { contentDescription = increaseDescription },
            ) { androidx.wear.compose.material3.Icon(VoxIcons.Plus, contentDescription = null, modifier = Modifier.size(22.dp)) }
        }
    }
}

/**
 * A single choice as ONE LIST ROW PER OPTION ([choiceRows]): a whole choice list in a single row is a tall item, and
 * the list's edge transformation squeezes a tall item as soon as its top leaves the screen, which drew the next
 * paragraph over its last options. One pill per row scales and fades like any other row. [label] words each option.
 */
fun <T> TransformingLazyColumnScope.choiceRows(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
) {
    for (value in options) {
        row {
            val on = value == selected
            PillButton(
                label = label(value),
                onClick = { onSelect(value) },
                modifier = Modifier.semantics { role = androidx.compose.ui.semantics.Role.RadioButton; this.selected = on },
                style = if (on) PillStyle.TONAL else PillStyle.OUTLINED,
                icon = if (on) VoxIcons.Check else null,
            )
        }
    }
}

/**
 * A single-choice list of full-width pills. The selected one is marked as a choice (a check, the tonal fill) and
 * the others are outlined, so a selection never looks like the screen's primary action (that purple fill is kept
 * for buttons that do something). Each is a radio button for TalkBack.
 */
@Composable
fun <T> ChoiceList(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(VSpace.xs)) {
        options.forEach { (value, label) ->
            val on = value == selected
            PillButton(
                label = label,
                onClick = { onSelect(value) },
                modifier = Modifier.semantics { role = androidx.compose.ui.semantics.Role.RadioButton; this.selected = on },
                style = if (on) PillStyle.TONAL else PillStyle.OUTLINED,
                icon = if (on) VoxIcons.Check else null,
            )
        }
    }
}

/** A label on the left, a value on the right: readouts and details. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label $value" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = VType.Caption, color = VColor.TextSecondary, maxLines = 1)
        Text(value, style = VType.Label, color = VColor.Text, maxLines = 1)
    }
}
