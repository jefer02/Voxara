package com.example.voxara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.HeroStatus
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VoxList
import com.example.voxara.ui.design.ZoneBadge
import com.example.voxara.ui.design.CircularOverlay
import com.example.voxara.ui.design.GlowNumber
import com.example.voxara.ui.design.LevelBezel
import com.example.voxara.ui.design.ZoneIconCircle
import com.example.voxara.ui.design.zoneWord
import kotlin.math.roundToInt

/** Which limit the in-app alert screen is about. */
enum class LimitKind { DAILY, WEEK_AMBIENT, WEEK_HEADPHONE }

/**
 * LIMIT ALERT — the full-screen view of a 100% alert while the app is open (the haptic and the
 * notification already fired from the service). "Why?" asks Tono to explain; it never decides.
 */
@Composable
fun LimitAlertScreen(env: UiEnv, kind: LimitKind, actions: VoxaraActions, onDismiss: () -> Unit) {
    val s = env.state
    val percent = when (kind) {
        LimitKind.DAILY -> s.dosePercent
        LimitKind.WEEK_AMBIENT -> s.weeklyFraction * 100
        LimitKind.WEEK_HEADPHONE -> s.headphoneWeeklyFraction * 100
    }.roundToInt()
    val (title, body) = when (kind) {
        LimitKind.DAILY -> R.string.alert_daily_100_title to R.string.alert_daily_100_body
        LimitKind.WEEK_AMBIENT -> R.string.alert_week_100_title to R.string.alert_week_100_body
        LimitKind.WEEK_HEADPHONE -> R.string.alert_hp_week_100_title to R.string.alert_hp_week_100_body
    }
    val reply = env.coach.reply
    // The template's round toast: a dark round panel with a border in the danger tier.
    CircularOverlay(border = VColor.tone(RiskZone.DANGEROUS).fill) {
        VoxList {
            // One item per element (not one tall sheet), so the round-screen list lays them out.
            item { ZoneIconCircle(RiskZone.DANGEROUS) }
            item { HeroStatus(stringResource(title, percent), maxLines = 4) }
            item { BodyText(stringResource(body, percent)) }
            item { PillButton(stringResource(R.string.alert_action_ack), onClick = onDismiss) }
            if (env.state.tonoEnabled) {
                item {
                    PillButton(
                        stringResource(R.string.alert_why),
                        onClick = { actions.onAsk(CoachTask.ALERT_WHY, null) },
                        style = PillStyle.TONAL,
                    )
                }
                if (reply != null && reply.why.isNotBlank()) item { BodyText(reply.why, color = VColor.Text) }
            }
        }
    }
}

/**
 * AMBIENT — the always-on view, whatever screen was open: level, zone word and a hairline bezel.
 * Under 5% of pixels lit, no animation, no glow, grey outline only.
 */
@Composable
fun AmbientScreen(env: UiEnv) {
    val s = env.state
    val zone = RiskZone.of(s.dba)
    val cd = stringResource(R.string.cd_now, s.dba.roundToInt(), zoneWord(zone), s.dosePercent.roundToInt())
    BoxWithConstraints(
        Modifier.fillMaxSize().background(VColor.Black).semantics(mergeDescendants = true) { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        // LocalAmbient makes these hairline, grey and still: no waves, no glow, no fills.
        LevelBezel(dba = s.dba.toFloat().takeIf { s.dba > 0.0 }, modifier = Modifier.size(maxWidth * 0.9f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlowNumber(if (s.dba > 0.0) s.dba.roundToInt().toString() else "—", glow = VColor.TextTertiary)
            ZoneBadge(zone, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
