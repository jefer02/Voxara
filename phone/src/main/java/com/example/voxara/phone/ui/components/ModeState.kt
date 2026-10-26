package com.example.voxara.phone.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.phone.R
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.labelRes

/**
 * The watch's situation with its listening layer, as of its last status: "Street", or
 * "Street + Headphones" while headphones mode is on, with "Auto · " in front when the wearer left the
 * situation on Auto. Null before the watch has reported one (or from an older watch).
 */
@Composable
fun watchModeName(s: PhoneUiState): String? {
    val st = s.status ?: return null
    val mode = st.situationMode ?: return null
    val name = stringResource(mode.labelRes())
    val named = if (st.headphonesMode) stringResource(R.string.mode_plus_headphones, name) else name
    return if (s.settings.situationChoice == SituationChoice.AUTO) stringResource(R.string.watch_mode_auto, named) else named
}

/** Why headphones mode is on, in words; null when it is off. */
@Composable
fun headphonesSourceText(source: LayerSource): String? = when (source) {
    LayerSource.WATCH -> stringResource(R.string.hp_source_watch)
    LayerSource.PHONE -> stringResource(R.string.hp_source_phone)
    LayerSource.MANUAL -> stringResource(R.string.hp_source_manual)
    LayerSource.NONE -> null
}

/**
 * The watch's mode as one quiet line: a headphones icon and the indigo ink while headphones mode is on (the icon and the
 * colour both say it), a watch icon and neutral ink otherwise. Nothing before the watch has reported a mode.
 */
@Composable
fun WatchModePill(s: PhoneUiState, modifier: Modifier = Modifier) {
    val name = watchModeName(s) ?: return
    val on = s.status?.headphonesMode == true
    val c = Vox.colors
    val ink = if (on) c.secondary else c.text2
    val cd = stringResource(R.string.home_mode_cd, name)
    Row(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = cd }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(if (on) VoxIcons.Headphones else VoxIcons.Watch, contentDescription = null, tint = ink, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, color = ink)
    }
}
