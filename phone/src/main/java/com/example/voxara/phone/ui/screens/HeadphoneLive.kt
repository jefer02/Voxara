package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.voxara.core.format.formatClock
import com.example.voxara.core.headphones.LowerUi
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.link.LinkHealth
import com.example.voxara.phone.R
import com.example.voxara.phone.listening.PhoneAlerts
import com.example.voxara.phone.ui.PhoneActions
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.HeroCard
import com.example.voxara.phone.ui.components.IconTile
import com.example.voxara.phone.ui.components.LinkButton
import com.example.voxara.phone.ui.components.PillButton
import com.example.voxara.phone.ui.components.VoxCard
import com.example.voxara.phone.ui.components.ZoneWord
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.theme.VoxSpace
import com.example.voxara.phone.ui.theme.figureStyle
import com.example.voxara.sync.LowerOutcome
import com.example.voxara.ui.design.CappedFontScale
import com.example.voxara.ui.design.RollingDigits
import com.example.voxara.ui.design.VoxIcons
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The phone's own headphones, live: what is connected, whether something plays, the estimated level (an estimate from the
 * volume setting, and it says so), "possible speaker" when a Bluetooth output could not be told from headphones, and —
 * above the recommended 80 dBA — the alert with its three actions: "Lower volume", "Got it" (closes it for this tier and
 * mutes nothing) and "Not headphones" (this output is left out until it disconnects). Shown only while the wearer shares
 * headphone listening with the watch.
 */
fun LazyListScope.headphoneLiveItems(s: PhoneUiState, actions: PhoneActions, onlyWhenActive: Boolean = false) {
    if (!showsHeadphoneLive(s, onlyWhenActive)) return
    item(key = "hp-live") { HeadphoneLiveCards(s, actions, onlyWhenActive, Modifier.contentWidth()) }
}

/** On Home the live card shows only while the phone's headphones are playing (or an alert is open); the Headphones screen always has it. */
private fun showsHeadphoneLive(s: PhoneUiState, onlyWhenActive: Boolean): Boolean {
    if (!s.phoneSampling) return false
    val live = s.phoneLive
    val alert = live.alert?.takeIf { !it.dismissed }
    return !onlyWhenActive || alert != null || (live.running && live.snapshot.connected && live.snapshot.playing)
}

/** Home's live card and, above the recommended level, the alert with its actions: one block, for any container. */
@Composable
fun HeadphoneLiveCards(s: PhoneUiState, actions: PhoneActions, onlyWhenActive: Boolean, modifier: Modifier = Modifier) {
    if (!showsHeadphoneLive(s, onlyWhenActive)) return
    val alert = s.phoneLive.alert?.takeIf { !it.dismissed }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VoxSpace.m)) {
        if (alert != null) HeadphoneAlertCard(s, actions, Modifier.fillMaxWidth())
        VoxCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hp_live_title), style = MaterialTheme.typography.titleSmall, color = Vox.colors.text)
            LiveState(s, compact = true)
        }
    }
}

/**
 * THE HEADPHONES HERO — what this phone's headphones are doing now. While something plays at a known volume: the estimated
 * level as the figure ("≈ 86 dBA"), its zone as a word with its icon, and "estimated ±10 dB" under it. Otherwise the state
 * in words. Home's headphones card grows into it ([sharedKey]).
 */
@Composable
fun HeadphoneHero(s: PhoneUiState, modifier: Modifier = Modifier, sharedKey: String? = null) {
    val c = Vox.colors
    HeroCard(modifier, sharedKey = sharedKey) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxSpace.xs)) {
            Icon(VoxIcons.Headphones, null, tint = c.secondary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.hp_live_title), style = MaterialTheme.typography.labelLarge, color = c.text2, modifier = Modifier.semantics { heading() })
        }
        if (!s.phoneSampling) {
            Text(stringResource(R.string.phone_status_off), style = MaterialTheme.typography.bodyLarge, color = c.text2, textAlign = TextAlign.Center)
        } else {
            LiveState(s, compact = false)
        }
    }
}

/** The live state in words, or the estimate as a figure; the same facts for Home's small card and the hero. */
@Composable
private fun LiveState(s: PhoneUiState, compact: Boolean) {
    val c = Vox.colors
    val snap = s.phoneLive.snapshot
    val estimate = snap.estimate
    val words = @Composable { res: Int ->
        Text(
            stringResource(res), style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = c.text2, textAlign = if (compact) TextAlign.Start else TextAlign.Center,
        )
    }
    when {
        !s.phoneLive.running -> words(R.string.hp_live_not_watching)
        !snap.connected -> words(R.string.hp_live_none)
        estimate == null -> words(if (snap.playing) R.string.hp_live_playing_unknown else R.string.hp_live_idle)
        else -> {
            val dba = estimate.dba.roundToInt()
            val zone = RiskZone.of(estimate.dba)
            val label = stringResource(R.string.hp_estimate_label)
            Column(
                Modifier.semantics(mergeDescendants = true) { },
                horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VoxSpace.xxs),
            ) {
                CappedFontScale(1.3f) {
                    RollingDigits(dba, figureStyle(if (compact) 36 else 64), c.text, prefix = "≈", suffix = "dBA", still = true, cellScale = 0.94f)
                }
                ZoneWord(zone, sizeSp = if (compact) 15 else 17)
                // an estimate always says so, next to its number
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.text2)
            }
        }
    }
    if (snap.connected && snap.possibleSpeaker) {
        Column(horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally) {
            Text(stringResource(R.string.hp_possible_speaker), style = MaterialTheme.typography.labelLarge, color = c.caution)
            Text(
                stringResource(R.string.hp_possible_speaker_note), style = MaterialTheme.typography.bodySmall, color = c.text2,
                textAlign = if (compact) TextAlign.Start else TextAlign.Center,
            )
        }
    }
}

/**
 * The alert above the recommended level, with three clear buttons one under the other (they never squeeze side by side):
 * "Lower volume" (filled), "Got it" and "Not headphones" (tonal). Once lowering worked it says so for a moment and leaves.
 */
@Composable
fun HeadphoneAlertCard(s: PhoneUiState, actions: PhoneActions, modifier: Modifier) {
    val c = Vox.colors
    val live = s.phoneLive
    val alert = live.alert ?: return
    val lower = live.lower
    val res = LocalContext.current.resources
    val ok = lower is LowerUi.Done && (lower.outcome == LowerOutcome.LOWERED || lower.outcome == LowerOutcome.ALREADY_SAFE)
    // Once it worked the card says so for a moment, then leaves by itself.
    LaunchedEffect(ok) { if (ok) { delay(3_000); actions.onDismissHeadphoneAlert() } }
    val zone = if (alert.tier >= 3) RiskZone.DANGEROUS else RiskZone.LOUD
    VoxCard(modifier, padding = VoxSpace.m) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxSpace.s)) {
            IconTile(VoxIcons.Warning, c.zoneInk(zone), size = 32.dp)
            Text(stringResource(R.string.hp_alert_title), style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f).semantics { heading() })
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
            Column(Modifier.fillMaxWidth().padding(top = VoxSpace.xxs), verticalArrangement = Arrangement.spacedBy(VoxSpace.xs)) {
                PillButton(
                    stringResource(if (lower is LowerUi.Working) R.string.hp_lower_working else R.string.hp_lower_action),
                    onClick = actions.onLowerHeadphones, icon = VoxIcons.Headphones, enabled = lower !is LowerUi.Working,
                    modifier = Modifier.fillMaxWidth(),
                )
                PillButton(stringResource(R.string.hp_dismiss_action), onClick = actions.onDismissHeadphoneAlert, tonal = true, modifier = Modifier.fillMaxWidth())
                PillButton(stringResource(R.string.hp_not_headphones_action), onClick = actions.onNotHeadphones, tonal = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * KEEP VOXARA RUNNING — shown when the system may stop the app in the background (battery optimisation on). On Samsung
 * phones this is what makes the watch link and the headphone detection drop when the app is left. One card: what and why,
 * one button to the setting, and "not now". It guides; it never changes the setting for the wearer.
 */
@Composable
fun BatteryGuide(s: PhoneUiState, actions: PhoneActions, modifier: Modifier = Modifier) {
    if (!s.batteryRestricted || s.batteryHintDismissed) return
    val c = Vox.colors
    VoxCard(modifier, padding = VoxSpace.m) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxSpace.s)) {
            IconTile(VoxIcons.Battery, c.caution, size = 32.dp)
            Text(stringResource(R.string.battery_guide_title), style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f).semantics { heading() })
        }
        Text(stringResource(R.string.battery_guide_body), style = MaterialTheme.typography.bodyMedium, color = c.text2)
        PillButton(stringResource(R.string.battery_guide_action), actions.onOpenBatterySettings, modifier = Modifier.fillMaxWidth().padding(top = VoxSpace.xxs))
        LinkButton(stringResource(R.string.action_not_now), actions.onDismissBatteryHint, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** The link in words, honestly: reconnecting, or disconnected for how long. Null while connected or unknown. */
@Composable
fun linkHealthText(health: LinkHealth, nowMs: Long): String? = when (health) {
    LinkHealth.Unknown, is LinkHealth.Connected -> null
    is LinkHealth.Reconnecting -> stringResource(R.string.link_reconnecting)
    is LinkHealth.Disconnected -> stringResource(R.string.link_disconnected_for, formatClock(((nowMs - health.sinceMs) / 60_000.0).coerceAtLeast(1.0)))
}
