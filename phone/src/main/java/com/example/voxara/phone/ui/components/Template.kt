package com.example.voxara.phone.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.voxara.core.design.Accent
import com.example.voxara.core.design.Palette
import com.example.voxara.phone.ui.theme.LocalReduceMotion
import com.example.voxara.phone.ui.theme.Vox

/*
 * SMALL PIECES: the icon tile, the switch and the accent swatches (docs/design/STYLE.md §4). Colours come from
 * Vox.colors; the data always comes from the caller.
 */

/** An icon tile: a rounded square in [tint]'s soft tint with the icon in [tint]. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 3)).background(tint.copy(alpha = Palette.SOFT_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size / 2))
    }
}

/**
 * The Material 3 switch in the accent: an accent track with the dark on-fill thumb when on; when off a quiet track with a
 * visible outline (muted text, 4.5:1) and thumb. Visual only: the row around it is the toggleable target and speaks the state.
 */
@Composable
fun VoxSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = Vox.colors
    Switch(
        checked = checked, onCheckedChange = null, modifier = modifier.clearAndSetSemantics { },
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.accent.onFill, checkedTrackColor = c.accent.fill, checkedBorderColor = c.accent.fill,
            uncheckedThumbColor = c.text3, uncheckedTrackColor = c.surface2, uncheckedBorderColor = c.text3,
        ),
    )
}

/**
 * ACCENT SWATCHES — four round swatches (48 dp targets); the selected one is scaled up with a ring. Each swatch is a
 * radio button named for TalkBack.
 */
@Composable
fun AccentSwatches(selected: Accent, onSelect: (Accent) -> Unit, names: Map<Accent, String>, modifier: Modifier = Modifier) {
    val c = Vox.colors
    val reduce = LocalReduceMotion.current
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Accent.entries.forEach { a ->
            val on = a == selected
            val swatch = Color(Palette.accent(a, c.dark).fill)
            val scale by animateFloatAsState(if (on) 1.22f else 1f, if (reduce) snap() else tween(200), label = "swatch")
            Box(
                Modifier
                    .size(48.dp)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(a) })
                    .semantics { contentDescription = names[a].orEmpty() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(28.dp).scale(scale).clip(CircleShape).background(swatch)
                        .border(2.dp, if (on) c.text else Color.White.copy(alpha = 0.25f), CircleShape),
                )
            }
        }
    }
}
