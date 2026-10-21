package com.example.voxara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.basicCurvedText
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.headphones.ListeningLayerState
import com.example.voxara.ui.design.JakartaFamily
import com.example.voxara.ui.design.LayerProgress
import com.example.voxara.ui.design.LocalTextScale
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.PageName
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VSize
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.heroScaled
import com.example.voxara.ui.design.pressable
import com.example.voxara.ui.theme.LocalAmbient
import kotlin.math.PI
import kotlin.math.sin

/*
 * HEADPHONES MODE on the Now page: the chip that takes the bottom slot, the curved label that plays while
 * the mode comes in, and the quick switch below the fold. The picture itself (arcs, moon) is `NowRing`.
 */

/** What "why" a layer is on or off says, as the chip's second line and the card's value. */
@Composable
internal fun layerSourceText(layer: ListeningLayerState): String = stringResource(
    when (layer.source) {
        LayerSource.MANUAL -> R.string.hp_mode_src_manual
        LayerSource.PHONE -> R.string.hp_mode_src_auto_phone
        else -> R.string.hp_mode_src_auto
    }
)

/**
 * THE CHIP — headphones mode's place in the hero's bottom slot: "Headphones" over "Auto" or "Manual"
 * (and "from phone" when the phone said so), a 48 dp indigo pill. A tap switches the mode off (Manual).
 * On small screens or large text it is a plain label (no 48 dp room); the Now page's headphones card below
 * does the switching then. It pops once as the label settles into it at the end of the transition.
 */
@Composable
internal fun HeadphonesChip(layer: ListeningLayerState, progress: LayerProgress, interactive: Boolean, onClick: () -> Unit) {
    val ambient = LocalAmbient.current
    val title = stringResource(R.string.hp_mode_chip_title)
    val why = layerSourceText(layer)
    val label = stringResource(R.string.hp_mode_turn_off)
    val shape = RoundedCornerShape(percent = 50)
    val body: @Composable () -> Unit = {
        Icon(
            VoxIcons.Headphones, contentDescription = null,
            tint = if (ambient) VColor.TextSecondary else VColor.Secondary,
            modifier = Modifier.size(16.dp * LocalTextScale.current),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = VType.Label.heroScaled(), color = if (ambient) VColor.TextSecondary else VColor.Secondary, maxLines = 1)
            Text(why, style = VType.Caption.heroScaled(), color = VColor.TextSecondary, maxLines = 1)
        }
    }
    val pop = Modifier.graphicsLayer {
        val l = progress.label.value
        val p = if (l in 1.5f..2f) sin((l - 1.5f) * 2f * PI.toFloat()) else 0f
        scaleX = 1f + 0.08f * p; scaleY = scaleX
    }
    if (!interactive) {
        // No room for a 48 dp pill: it is the page's name slot, like any other page's name.
        PageName(title, VoxIcons.Headphones, VColor.Secondary, Modifier.then(pop).semantics { contentDescription = "$title, $why" })
        return
    }
    run {
        Row(
            Modifier
                .then(pop)
                .defaultMinSize(minHeight = VSize.touch)
                .clip(shape)
                .background(VColor.SecondaryFill.copy(alpha = 0.18f))
                .border(1.dp, VColor.SecondaryFill.copy(alpha = 0.7f), shape)
                .pressable(onClick = onClick, onClickLabel = label)
                .semantics { contentDescription = "$title, $why. $label" }
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { body() }
    }
}

/**
 * THE LABEL — "Headphones mode" curved round the bottom of the ring while the transition plays, just
 * where the chip will be: it appears, holds a beat, then shrinks and fades as the chip takes its place.
 * Only during the transition; never in ambient.
 */
@Composable
internal fun HeadphonesLabel(progress: LayerProgress) {
    if (LocalAmbient.current) return
    val visible by remember { derivedStateOf { progress.label.value > 0.001f } }
    if (!visible) return
    val text = stringResource(R.string.hp_mode_label)
    val scale = LocalTextScale.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = minOf(maxWidth, maxHeight)
        CurvedLayout(
            Modifier
                .fillMaxSize()
                .padding(d * 0.095f)
                .graphicsLayer {
                    val l = progress.label.value
                    // Gone by the middle of its settle: the chip appears into the space it leaves, never over it.
                    alpha = if (l <= 1f) l.coerceIn(0f, 1f) else (1f - 2f * (l - 1f)).coerceIn(0f, 1f)
                    val sc = if (l <= 1f) 0.92f + 0.08f * l.coerceIn(0f, 1f) else 1f - 0.25f * (l - 1f).coerceIn(0f, 1f)
                    scaleX = sc; scaleY = sc
                }
                .semantics { },
            anchor = 90f,
            angularDirection = CurvedDirection.Angular.CounterClockwise,
        ) {
            basicCurvedText(
                text,
                style = { CurvedTextStyle(fontSize = (13f * scale).sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFamily, color = VColor.Secondary) },
            )
        }
    }
}

/**
 * HEADPHONES MODE CARD — the quick action on Overview: what the mode is now ("Off", "On · Auto", "On ·
 * Manual", "On · from phone") and one tap to flip it. Going back to what Auto would do puts it back on
 * Auto, so a manual override never lingers longer than needed. The full choice is on the Mode screen.
 */
@Composable
internal fun HeadphonesModeCard(layer: ListeningLayerState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val why = layerSourceText(layer)
    val value = when {
        layer.active -> stringResource(R.string.hp_mode_on_fmt, why)
        layer.source == LayerSource.MANUAL -> stringResource(R.string.hp_mode_off_fmt, why)
        else -> stringResource(R.string.hp_mode_off)
    }
    MetricCard(
        icon = VoxIcons.Headphones,
        label = stringResource(R.string.hp_mode_title),
        value = value,
        accent = VColor.Secondary,
        onClick = onClick,
        modifier = modifier,
    )
}
