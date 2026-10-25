package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.voxara.core.headphones.LowerUi
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.link.LinkHealth
import com.example.voxara.core.format.formatClock
import com.example.voxara.phone.R
import com.example.voxara.phone.listening.PhoneAlerts
import com.example.voxara.phone.ui.PhoneActions
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.Banner
import com.example.voxara.phone.ui.components.PillButton
import com.example.voxara.phone.ui.components.VoxCard
import com.example.voxara.phone.ui.components.ZoneWord
import com.example.voxara.phone.ui.theme.figureStyle
import com.example.voxara.ui.design.RollingDigits
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.sync.LowerOutcome
import com.example.voxara.ui.design.VoxIcons
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The phone's own headphones, live: what is connected, whether something plays, the estimated level (an
 * estimate from the volume setting, and it says so), "possible speaker" when a Bluetooth output could not be
 * told from headphones, and — above the recommended 80 dBA — the alert with its two actions, the same two the
 * notification and the watch have: "Lower to safe volume" and "Dismiss" (which closes it for this tier and
 * mutes nothing). Shown only while the wearer shares headphone listening with the watch.
 */
fun LazyListScope.headphoneLiveItems(s: PhoneUiState, actions: PhoneActions, onlyWhenActive: Boolean = false) {
    if (!showsHeadphoneLive(s, onlyWhenActive)) return
    item { HeadphoneLiveCards(s, actions, onlyWhenActive, Modifier.contentWidth()) }
}

/** On Home the live card shows only while the phone's headphones are playing (or an alert is open); the Headphones screen always has it. */
private fun showsHeadphoneLive(s: PhoneUiState, onlyWhenActive: Boolean): Boolean {
    if (!s.phoneSampling) return false
    val live = s.phoneLive
    val alert = live.alert?.takeIf { !it.dismissed }
    return !onlyWhenActive || alert != null || (live.running && live.snapshot.connected && live.snapshot.playing)
}

/** The live card and, above the recommended level, the alert with its two actions: one block, for any container. */
@Composable
fun HeadphoneLiveCards(s: PhoneUiState, actions: PhoneActions, onlyWhenActive: Boolean, modifier: Modifier = Modifier) {
    if (!showsHeadphoneLive(s, onlyWhenActive)) return
    val alert = s.phoneLive.alert?.takeIf { !it.dismissed }
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeadphoneLiveCard(s, Modifier.fillMaxWidth())
        if (alert != null) HeadphoneAlertCard(s, actions, Modifier.fillMaxWidth())
    }
}

@Composable
private fun HeadphoneLiveCard(s: PhoneUiState, modifier: Modifier) {
    val c = Vox.colors
    val snap = s.phoneLive.snapshot
    val estimate = snap.estimate
    VoxCard(modifier) {
        Text(stringResource(R.string.hp_live_title), style = MaterialTheme.typography.titleMedium, color = c.text)
        when {
            !s.phoneLive.running -> Text(stringResource(R.string.hp_live_not_watching), style = MaterialTheme.typography.bodyMedium, color = c.text2)
            !snap.connected -> Text(stringResource(R.string.hp_live_none), style = MaterialTheme.typography.bodyMedium, color = c.text2)
            estimate == null -> Text(
                stringResource(if (snap.playing) R.string.hp_live_playing_unknown else R.string.hp_live_idle),
                style = MaterialTheme.typography.bodyMedium, color = c.text2,
            )
            else -> {
                RollingDigits(estimate.dba.roundToInt(), figureStyle(36), c.text, prefix = "≈", suffix = "dBA", still = true, cellScale = 0.94f)
                ZoneWord(RiskZone.of(estimate.dba), sizeSp = 15)
                Text(stringResource(R.string.hp_estimate_label), style = MaterialTheme.typography.labelMedium, color = c.text2)
            }
        }
        if (snap.connected && snap.possibleSpeaker) {
            Text(stringResource(R.string.hp_possible_speaker), style = MaterialTheme.typography.labelLarge, color = c.caution)
            Text(stringResource(R.string.hp_possible_speaker_note), style = MaterialTheme.typography.bodySmall, color = c.text2)
        }
    }
}

@Composable
private fun HeadphoneAlertCard(s: PhoneUiState, actions: PhoneActions, modifier: Modifier) {
    val c = Vox.colors
    val live = s.phoneLive
    val alert = live.alert ?: return
    val lower = live.lower
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    val ok = lower is LowerUi.Done && (lower.outcome == LowerOutcome.LOWERED || lower.outcome == LowerOutcome.ALREADY_SAFE)
    // Once it worked the card says so for a moment, then leaves by itself.
    LaunchedEffect(ok) { if (ok) { delay(3_000); actions.onDismissHeadphoneAlert() } }
    VoxCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(VoxIcons.Warning, contentDescription = null, tint = c.zoneInk(if (alert.tier >= 3) RiskZone.DANGEROUS else RiskZone.LOUD))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.hp_alert_title), style = MaterialTheme.typography.titleMedium, color = c.text)
        }
        Text(stringResource(R.string.hp_alert_text, alert.dba.roundToInt()), style = MaterialTheme.typography.bodyMedium, color = c.text2)
        (lower as? LowerUi.Done)?.let {
            Text(
                PhoneAlerts.lowerText(res, it.outcome, it.dba),
                style = MaterialTheme.typography.bodyMedium, color = if (ok) c.brand else c.text,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (!ok) {
            // side by side while the words fit; one under the other at large text
            val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f
            val lowerButton = @Composable { m: Modifier ->
                PillButton(
                    stringResource(if (lower is LowerUi.Working) R.string.hp_lower_working else R.string.hp_lower_action),
                    onClick = actions.onLowerHeadphones, icon = VoxIcons.Headphones, enabled = lower !is LowerUi.Working, modifier = m,
                )
            }
            val dismissButton = @Composable { m: Modifier ->
                PillButton(stringResource(R.string.hp_dismiss_action), onClick = actions.onDismissHeadphoneAlert, tonal = true, modifier = m)
            }
            if (stacked) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lowerButton(Modifier.fillMaxWidth()); dismissButton(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lowerButton(Modifier.weight(1f)); dismissButton(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * KEEP VOXARA RUNNING — shown when the system may stop the app in the background (battery optimisation on).
 * On Samsung phones this is what makes the watch link and the headphone detection drop when the app is left.
 * It guides; it never changes the setting for the wearer.
 */
@Composable
fun BatteryGuide(s: PhoneUiState, actions: PhoneActions, modifier: Modifier = Modifier) {
    if (!s.batteryRestricted || s.batteryHintDismissed) return
    Banner(
        VoxIcons.Battery, stringResource(R.string.battery_guide_title), stringResource(R.string.battery_guide_body),
        tint = Vox.colors.caution, modifier = modifier,
        action = stringResource(R.string.battery_guide_action), onAction = actions.onOpenBatterySettings,
    )
}

/** The link in words, honestly: reconnecting, or disconnected for how long. Null while connected or unknown. */
@Composable
fun linkHealthText(health: LinkHealth, nowMs: Long): String? = when (health) {
    LinkHealth.Unknown, is LinkHealth.Connected -> null
    is LinkHealth.Reconnecting -> stringResource(R.string.link_reconnecting)
    is LinkHealth.Disconnected -> stringResource(R.string.link_disconnected_for, formatClock(((nowMs - health.sinceMs) / 60_000.0).coerceAtLeast(1.0)))
}
