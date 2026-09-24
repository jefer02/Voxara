package com.example.voxara.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/**
 * VOX LIST — every scrolling screen: Material 3 ScreenScaffold (time text, scroll indicator,
 * optional edge button) around a TransformingLazyColumn with rotary scrolling and round-screen
 * side insets.
 */
@Composable
fun VoxList(
    modifier: Modifier = Modifier,
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: TransformingLazyColumnScope.() -> Unit,
) {
    val state = rememberTransformingLazyColumnState()
    val column: @Composable BoxScope.(androidx.compose.foundation.layout.PaddingValues) -> Unit = { padding ->
        // A little air under the time text, so the first line never touches it at large font sizes.
        val top = padding.calculateTopPadding() + VSpace.s
        val bottom = padding.calculateBottomPadding()
        TransformingLazyColumn(
            state = state,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(VSpace.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.fillMaxSize().padding(horizontal = VSpace.roundInset),
            content = content,
        )
    }
    if (edgeButton != null) {
        ScreenScaffold(scrollState = state, edgeButton = edgeButton, content = column)
    } else {
        ScreenScaffold(scrollState = state, content = column)
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
    Panel(modifier) {
        SectionLabel(label)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onDecrease,
                modifier = Modifier.size(VSize.touch).semantics { contentDescription = decreaseDescription },
            ) { Text("−", style = VType.Title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            Text(value, style = VType.Numeral, color = VColor.Text, modifier = Modifier.semantics { contentDescription = "$label $value" })
            FilledTonalButton(
                onClick = onIncrease,
                modifier = Modifier.size(VSize.touch).semantics { contentDescription = increaseDescription },
            ) { Text("+", style = VType.Title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

/** A single-choice list of full-width pills; the selected one is primary, with a check. */
@Composable
fun <T> ChoiceList(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VSpace.xs)) {
        options.forEach { (value, label) ->
            PillButton(
                label = label,
                onClick = { onSelect(value) },
                style = if (value == selected) PillStyle.PRIMARY else PillStyle.TONAL,
                icon = if (value == selected) VoxIcons.Check else null,
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
