package com.example.voxara.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import com.example.voxara.ui.design.HeroList
import com.example.voxara.ui.design.VSpace
import com.example.voxara.ui.design.ZoneTag
import com.example.voxara.ui.design.CircularOverlay
import com.example.voxara.ui.design.LevelNumber
import com.example.voxara.ui.design.VMotion
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.LocalAnimate
import com.example.voxara.ui.design.animationsActive
import com.example.voxara.ui.design.row
import com.example.voxara.ui.design.BezelArc
import com.example.voxara.ui.design.LevelBezel
import com.example.voxara.ui.design.VoxIcons
import androidx.wear.compose.material3.Icon
import com.example.voxara.ui.design.ZoneIconCircle
import com.example.voxara.ui.design.zoneWord
import kotlin.math.roundToInt

/** Which limit the in-app alert screen is about. */
enum class LimitKind { DAILY, WEEK_AMBIENT, WEEK_HEADPHONE }

/**
 * Beats of the limit haptic, in ms from the start, as composed in HapticConductor (DOSE_FULL: three
 * clicks and a thud; LONG_SHORT_LONG: thud, click, thud). The alert's border pulses on each one, a
 * big pulse on the thuds.
 */
private fun LimitKind.beats(): List<Pair<Long, Boolean>> = when (this) {
    LimitKind.DAILY -> listOf(0L to false, 150L to false, 300L to false, 470L to true)
    LimitKind.WEEK_AMBIENT, LimitKind.WEEK_HEADPHONE -> listOf(0L to true, 220L to false, 400L to true)
}

/**
 * LIMIT ALERT — the full-screen view of a 100% alert while the app is open (the haptic and the
 * notification already fired from the service). "Why?" asks Tono to explain; it never decides. On
 * entry the tier border and the icon pulse once per haptic beat (static with reduced motion).
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

    val pulse = remember { Animatable(0f) }
    val animate = animationsActive(LocalAnimate.current)
    LaunchedEffect(kind, animate) {
        if (!animate) return@LaunchedEffect
        var elapsed = 0L
        for ((at, strong) in kind.beats()) {
            delay(at - elapsed)
            elapsed = at
            pulse.snapTo(if (strong) 1f else 0.55f)
            launch { pulse.animateTo(0f, tween(VMotion.PULSE_MS / if (strong) 1 else 2)) }
        }
    }

    // A dark round panel with a border in the danger tier.
    CircularOverlay(border = VColor.tone(RiskZone.DANGEROUS).fill, pulse = { pulse.value }) {
        val compact = isCompactFont()
        HeroList(
            hero = { m ->
                // What must be seen without scrolling: that it happened, and the one way to acknowledge it.
                Column(
                    m.padding(start = VSpace.roundInset + 4.dp, end = VSpace.roundInset + 4.dp, top = 28.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(VSpace.xs, Alignment.CenterVertically),
                ) {
                    if (!compact) ZoneIconCircle(RiskZone.DANGEROUS, Modifier.graphicsLayer { scaleX = 1f + 0.14f * pulse.value; scaleY = scaleX })
                    HeroStatus(stringResource(title, percent), maxLines = 4)
                    PillButton(stringResource(R.string.alert_action_ack), onClick = onDismiss)
                }
            },
            details = {
                row { BodyText(stringResource(body, percent)) }
                if (env.state.tonoEnabled) {
                    row {
                        PillButton(
                            stringResource(R.string.alert_why),
                            onClick = { actions.onAsk(CoachTask.ALERT_WHY, null) },
                            style = PillStyle.TONAL,
                        )
                    }
                    if (reply != null && reply.why.isNotBlank()) row { BodyText(reply.why, color = VColor.Text) }
                }
            },
        )
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
    val layerOn = s.listening.active
    // Headphones mode: the number is the estimated listening level when there is one, as on the hero.
    val est = s.headphoneEstimate?.takeIf { layerOn && s.headphoneListening }
    val cd = (if (layerOn) stringResource(R.string.hp_mode_announce_on) + ". " else "") +
        stringResource(R.string.cd_now, s.dba.roundToInt(), zoneWord(zone), s.dosePercent.roundToInt())
    BoxWithConstraints(
        Modifier.fillMaxSize().background(VColor.Black).semantics(mergeDescendants = true) { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        // LocalAmbient makes these hairline, grey and still: no waves, no glow, no fills.
        // The same rim as the pages; in headphones mode a still outline of two arcs.
        if (layerOn) BezelArc(fraction = 0f, color = VColor.TextTertiary, modifier = Modifier.size(minOf(maxWidth, maxHeight)), gapDeg = 42f)
        else LevelBezel(dba = s.dba.toFloat().takeIf { s.dba > 0.0 }, modifier = Modifier.size(minOf(maxWidth, maxHeight)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (layerOn) Icon(VoxIcons.Headphones, contentDescription = null, tint = VColor.TextSecondary, modifier = Modifier.size(18.dp))
            LevelNumber(
                (est?.dba ?: s.dba).takeIf { it > 0.0 }?.roundToInt(), glow = VColor.TextTertiary, style = VType.Hero,
                prefix = if (est != null) "≈" else "",
            )
            ZoneTag(if (est != null) RiskZone.of(est.dba) else zone, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
