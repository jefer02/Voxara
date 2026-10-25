package com.example.voxara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.core.headphones.LowerUi
import com.example.voxara.sync.LowerOutcome
import com.example.voxara.service.hpLowerText
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.CircularOverlay
import com.example.voxara.ui.design.HeroList
import com.example.voxara.ui.design.HeroStatus
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VSpace
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.ZoneIconCircle
import com.example.voxara.ui.design.row
import com.example.voxara.ui.theme.LocalAmbient
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * THE HEADPHONE-VOLUME ALERT, INSIDE THE HEADPHONES VIEW — a round panel over Now while the estimated
 * listening level is above the recommended 80 dBA. Its haptic and its notification already fired from the
 * AlertEngine; this is only the screen, with the same two actions the notification has:
 *
 *  - **Lower to safe volume**: brings the music volume to the recommended level (this watch's, or the
 *    phone's when the headphones are on the phone; the phone confirms). It says what happened, including that
 *    it could not (fixed volume, phone out of reach).
 *  - **Dismiss**: closes the panel for this tier. It writes nothing and mutes nothing: a higher tier, or the
 *    engine's cooldown, alerts again.
 *
 * The level is an ESTIMATE and says so. Colour is never alone: the title, the icon and the number carry it.
 */
@Composable
fun HeadphoneAlertPanel(env: UiEnv, actions: VoxaraActions, navigate: (String) -> Unit = {}) {
    val s = env.state
    val alert = s.headphoneAlert?.takeIf { !it.dismissed } ?: return
    if (LocalAmbient.current) return
    val lower = s.lowerVolume
    val res = LocalContext.current.resources
    val succeeded = lower is LowerUi.Done && (lower.outcome == LowerOutcome.LOWERED || lower.outcome == LowerOutcome.ALREADY_SAFE)
    val failed = lower is LowerUi.Done && !succeeded

    // Once it worked the panel says so for a moment, then leaves by itself.
    LaunchedEffect(succeeded) {
        if (succeeded) { delay(3_000); actions.onDismissHeadphoneAlert() }
    }

    val zone = if (alert.tier >= 3) RiskZone.DANGEROUS else RiskZone.LOUD
    val compact = isCompactFont()
    val level = alert.dba.roundToInt()
    val where = stringResource(if (alert.source == LayerSource.PHONE) R.string.hp_alert_on_phone else R.string.hp_alert_on_watch)
    val title = stringResource(R.string.hp_alert_panel_title)
    val levelLine = stringResource(R.string.hp_alert_level, level)
    val result = (lower as? LowerUi.Done)?.let { hpLowerText(res, it.outcome, it.dba) }

    // The rows below the first screen keep clear of the ring's edge too.
    val side = Modifier.padding(horizontal = alertInset(0.07f))
    CircularOverlay(border = VColor.tone(zone).fill) {
        HeroList(
            hero = { m ->
                Column(
                    m.padding(start = alertInset(0.14f), end = alertInset(0.14f), top = alertInset(0.12f), bottom = alertInset(0.10f))
                        .semantics(mergeDescendants = true) { contentDescription = "$title. $levelLine. $where" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(VSpace.xs, Alignment.CenterVertically),
                ) {
                    if (!compact && result == null) ZoneIconCircle(zone)
                    HeroStatus(title)
                    // The level (an estimate, with the ≈) while asking; what happened once it was asked.
                    if (result == null) Text(levelLine, style = VType.Label, color = VColor.tone(zone).ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (result != null) {
                        // What happened, in words, announced to TalkBack as it appears.
                        Text(
                            result, style = VType.Caption, color = if (failed) VColor.Text else VColor.AccentInk,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (!succeeded) {
                        PillButton(
                            stringResource(if (lower is LowerUi.Working) R.string.hp_lower_working else R.string.hp_lower_action),
                            onClick = actions.onLowerHeadphones,
                            icon = if (compact) null else VoxIcons.Headphones,
                            enabled = lower !is LowerUi.Working,
                            wide = true,
                        )
                    }
                }
            },
            details = {
                row { PillButton(stringResource(R.string.hp_dismiss_action), onClick = actions.onDismissHeadphoneAlert, style = PillStyle.OUTLINED, wide = true) }
                // A speaker taken for headphones: the way out stops this alert for that output only (room alerts stay).
                row { PillButton(stringResource(R.string.hp_not_headphones), onClick = { navigate(com.example.voxara.ui.Routes.HP_EXIT) }, style = PillStyle.OUTLINED, wide = true) }
                row { BodyText(stringResource(R.string.hp_alert_body, level), modifier = side) }
                row { BodyText(where, color = VColor.TextSecondary, modifier = side) }
            },
        )
    }
}
