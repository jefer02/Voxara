package com.example.voxara.debug

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.haptics.HapticConductor
import com.example.voxara.haptics.Pattern
import com.example.voxara.ui.Routes
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.SectionLabel
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VoxList
import kotlinx.coroutines.delay

/**
 * DEBUG BUILD ONLY (src/debug). Tools for checking the watch by hand; release builds get a no-op
 * with the same API (src/release), so neither this code nor its strings ship.
 */
object DebugTools {
    const val available = true

    /** The entries this build adds to Settings → Debug tools. */
    @Composable
    fun SettingsEntries(navigate: (String) -> Unit) {
        PillButton(
            stringResource(R.string.debug_haptics_title),
            onClick = { navigate(Routes.DEBUG_HAPTICS) },
            icon = VoxIcons.Vibrate,
            style = PillStyle.OUTLINED,
        )
    }

    /**
     * Plays every haptic pattern on demand, so each one can be felt on the Galaxy Watch 7 —
     * with Do Not Disturb on and off — and compared with the others.
     */
    @Composable
    fun HapticsScreen() {
        val context = LocalContext.current
        val conductor = remember { HapticConductor(context) }
        var strong by remember { mutableStateOf(false) }
        var quiet by remember { mutableStateOf(QuietState.read(context)) }
        // Do Not Disturb is changed from quick settings while this screen is open: keep it current.
        LaunchedEffect(Unit) {
            while (true) {
                quiet = QuietState.read(context)
                delay(1_000)
            }
        }
        VoxList {
            item { Text(stringResource(R.string.debug_haptics_title), style = VType.Title, color = VColor.Text, modifier = Modifier.semantics { heading() }) }
            item {
                BodyText(
                    stringResource(
                        R.string.debug_haptics_state,
                        stringResource(if (quiet.dnd) R.string.debug_on else R.string.debug_off),
                        quiet.ringer,
                    ),
                    color = VColor.Text,
                )
            }
            item { BodyText(stringResource(R.string.debug_haptics_note)) }
            item {
                PillButton(
                    stringResource(if (strong) R.string.debug_haptics_strong_on else R.string.debug_haptics_strong_off),
                    onClick = { strong = !strong },
                    style = if (strong) PillStyle.PRIMARY else PillStyle.TONAL,
                )
            }
            Pattern.entries.forEach { p ->
                item {
                    PillButton(
                        "${p.name} · ${stringResource(roleOf(p))}",
                        onClick = { conductor.fire(p, if (strong) 2 else 1) },
                        style = PillStyle.TONAL,
                    )
                }
                item {
                    SectionLabel(
                        stringResource(if (conductor.isComposed(p)) R.string.debug_haptics_composed else R.string.debug_haptics_fallback),
                        color = VColor.TextTertiary,
                    )
                }
            }
        }
    }

    private fun roleOf(p: Pattern): Int = when (p) {
        Pattern.THRESHOLD, Pattern.SOFT_TICK -> R.string.debug_role_advisory
        Pattern.TWO_FIRM, Pattern.QUICK_TRIPLE -> R.string.debug_role_warning
        Pattern.DOSE_FULL, Pattern.LONG_SHORT_LONG -> R.string.debug_role_limit
        Pattern.IMPULSE -> R.string.debug_role_impulse
        Pattern.CALM -> R.string.debug_role_calm
        Pattern.MODE_CHANGE -> R.string.debug_role_mode
        Pattern.CONFIRM -> R.string.debug_role_confirm
    }

    private data class QuietState(val dnd: Boolean, val ringer: String) {
        companion object {
            fun read(context: Context): QuietState {
                val nm = context.getSystemService(NotificationManager::class.java)
                val filter = runCatching { nm?.currentInterruptionFilter }.getOrNull()
                val am = context.getSystemService(AudioManager::class.java)
                val ringer = when (runCatching { am?.ringerMode }.getOrNull()) {
                    AudioManager.RINGER_MODE_SILENT -> "SILENT"
                    AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                    AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
                    else -> "?"
                }
                return QuietState(
                    dnd = filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                        filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
                    ringer = ringer,
                )
            }
        }
    }
}
