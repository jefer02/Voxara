package com.example.voxara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.ai.HearingCareScore
import com.example.voxara.core.format.formatClock
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.Routes
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.HeroStatus
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.ProgressRing
import com.example.voxara.ui.design.SectionLabel
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VSpace
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VoxList
import com.example.voxara.ui.design.zoneWord
import kotlin.math.roundToInt

/**
 * HOME — Tono is the hero. One plain-language status line, the Hearing Care Score, then one
 * card per area. Ask is the edge button: one tap from the root.
 */
@Composable
fun HomeScreen(env: UiEnv, actions: VoxaraActions, navigate: (String) -> Unit) {
    val s = env.state
    val name = stringResource(R.string.ai_name)
    // Refresh Tono's status line when Home appears (automatic calls are rate-limited to hourly).
    LaunchedEffect(Unit) { actions.onAsk(CoachTask.STATUS, null) }

    val score = HearingCareScore.of(
        s.weeklyFraction * 100, s.headphoneWeeklyFraction * 100,
        s.days.take(7).count { it.nioshDosePercent >= 100.0 },
    )
    val status = env.coach.reply?.status ?: stringResource(R.string.home_status_fallback)

    VoxList(
        edgeButton = {
            EdgeButton(onClick = { navigate(Routes.ASK) }) {
                Icon(VoxIcons.Ask, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.home_ask, name), style = VType.TitleSmall)
            }
        },
    ) {
        item { SectionLabel(name.uppercase(), color = VColor.BrandStart, modifier = Modifier.semantics { heading() }) }
        item { HeroStatus(status) }
        if (env.coach.source == ReplySource.CLOUD_AI) {
            item { SectionLabel(stringResource(R.string.home_source_ai), color = VColor.TextTertiary) }
        }

        if (s.monitoringStatus == MonitoringStatus.PAUSED) {
            item {
                MetricCard(
                    icon = VoxIcons.Play,
                    label = stringResource(R.string.paused_label),
                    value = stringResource(R.string.action_resume),
                    accent = VColor.zone(RiskZone.MODERATE),
                    onClick = actions.onResume,
                )
            }
        }

        item {
            // Stacked, not side by side: it has to fit a 192 dp screen at large font sizes.
            val cd = stringResource(R.string.home_score_cd, score)
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = cd },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VSpace.xs),
            ) {
                ProgressRing(score / 100f, Modifier.size(64.dp), color = VColor.BrandStart, stroke = 8.dp) {
                    Text("$score", style = VType.Numeral, color = VColor.Text)
                }
                Text(stringResource(R.string.score_title), style = VType.Label, color = VColor.Text, textAlign = TextAlign.Center)
                Text(stringResource(R.string.score_sub), style = VType.Caption, color = VColor.TextSecondary, textAlign = TextAlign.Center)
            }
        }

        item {
            val zone = RiskZone.of(s.dba)
            MetricCard(
                icon = VoxIcons.zone(zone),
                label = stringResource(R.string.card_now),
                value = if (s.dba > 0.0) stringResource(R.string.card_now_value, s.dba.roundToInt(), zoneWord(zone))
                else stringResource(R.string.card_now_none),
                detail = if (!s.calibrated) stringResource(R.string.uncalibrated_short) else null,
                accent = VColor.zone(zone),
                onClick = { navigate(Routes.NOW) },
            )
        }
        item {
            val est = s.headphoneEstimate?.takeIf { s.headphoneListening }
            MetricCard(
                icon = VoxIcons.Headphones,
                label = stringResource(R.string.card_headphones),
                value = when {
                    est != null -> stringResource(R.string.card_headphones_listening, est.dba.roundToInt())
                    s.headphoneCategory != null -> stringResource(R.string.card_headphones_connected)
                    else -> stringResource(R.string.card_headphones_none)
                },
                detail = stringResource(R.string.card_week_share, (s.headphoneWeeklyFraction * 100).roundToInt()),
                onClick = { navigate(Routes.HEADPHONES) },
            )
        }
        item {
            val today = s.days.firstOrNull()
            MetricCard(
                icon = VoxIcons.Calendar,
                label = stringResource(R.string.card_today),
                value = stringResource(R.string.card_today_value, s.dosePercent.roundToInt()),
                detail = today?.let { stringResource(R.string.day_measured_plain, formatClock(it.measuredMinutes)) },
                accent = VColor.zone(RiskZone.ofPercent(s.dosePercent)),
                onClick = { navigate(Routes.TODAY) },
            )
        }
        item {
            MetricCard(
                icon = VoxIcons.Chart,
                label = stringResource(R.string.card_week),
                value = stringResource(R.string.card_week_value, (s.weeklyFraction * 100).roundToInt()),
                detail = stringResource(R.string.card_week_detail, (s.headphoneWeeklyFraction * 100).roundToInt()),
                accent = VColor.zone(RiskZone.ofPercent(maxOf(s.weeklyFraction, s.headphoneWeeklyFraction) * 100)),
                onClick = { navigate(Routes.WEEK) },
            )
        }
        item {
            MetricCard(
                icon = VoxIcons.Settings,
                label = stringResource(R.string.card_settings),
                value = stringResource(R.string.card_settings_value),
                accent = VColor.TextSecondary,
                onClick = { navigate(Routes.SETTINGS) },
            )
        }
        item { BodyText(stringResource(R.string.tono_not_medical), color = VColor.TextTertiary) }
    }
}
