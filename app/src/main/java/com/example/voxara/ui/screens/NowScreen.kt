package com.example.voxara.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.dose.DOSE_THRESHOLD_DBA
import com.example.voxara.core.format.formatHeadroom
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.text.sceneOrBandLabelRes
import com.example.voxara.ui.Routes
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.Face
import com.example.voxara.ui.design.HeroList
import com.example.voxara.ui.design.LocalDisplay
import com.example.voxara.ui.design.LocalTextScale
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.NowRing
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.ResumePill
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VMotion
import com.example.voxara.ui.design.VRadius
import com.example.voxara.ui.design.VSize
import com.example.voxara.ui.design.VSpace
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.ZoneTag
import com.example.voxara.ui.design.gaugeFraction
import com.example.voxara.ui.design.heroScaled
import com.example.voxara.ui.design.pressable
import com.example.voxara.ui.design.rememberLayerProgress
import com.example.voxara.ui.design.row
import com.example.voxara.ui.design.zoneWord
import com.example.voxara.ui.theme.LocalReduceMotion
import kotlin.math.roundToInt

/** What the hero's foot shows: Tono's tag in [tagLines] lines (0 = it is the first detail instead) and whether the dose line fits. */
internal data class FootPlan(val tagLines: Int, val dose: Boolean) {
    companion object { val Full = FootPlan(2, true) }
}

/**
 * What fits in the [room] the ring's gap leaves under the centre: the tag in two lines, in one, or not at
 * all (it is the first detail row then), with the dose line ([doseHeight] tall) under it. A small display
 * or an uncalibrated note takes room from the foot; the page degrades by what it can show, never by
 * crowding the figure.
 */
internal fun footPlan(room: Dp, doseHeight: Dp, textScale: Float): FootPlan {
    val gap = 4.dp
    val oneLine = 16.dp * textScale + 12.dp
    val twoLines = 32.dp * textScale + 12.dp
    return when {
        room < doseHeight -> FootPlan(0, false)
        room >= doseHeight + gap + twoLines -> FootPlan(2, true)
        room >= doseHeight + gap + oneLine -> FootPlan(1, true)
        else -> FootPlan(0, true)
    }
}

/**
 * NOW — the room around you, and how today is going: the live level as a big rolling number over its dBA
 * scale (the Now design), the zone in words, and, in the gap under it, Tono's one line as a small tag (tap:
 * Ask) over today's dose. The scale's four zones (accent, accent, amber, crimson) light up to the level and a
 * marker rides at it; the rings behind swell and calm with the sound. Until calibrated, a neutral note under
 * the zone says this is an estimate. Paused monitoring replaces the zone tag with a resume pill.
 *
 * HEADPHONES MODE. When the listening layer is on, this page is the one that changes: the scale opens at the
 * top into two ear-cup arcs that rise with the estimated listening level (indigo), the waves go stereo, the
 * centre leads with that level (≈, or the week's share of the listening allowance when nothing plays) and the
 * outside level becomes a small moon in the ring's top gap. The bottom slot becomes a chip that says it is
 * on and whether that is Auto or Manual (a tap switches it off). Tono's line and the outside level move to
 * the first details. The transition is [rememberLayerProgress]'s.
 *
 * Below the fold: the surroundings, the situation mode chip, the Concert note and the headphones quick switch;
 * in headphones mode also what the listening estimate rests on (status, range, time left).
 */
@Composable
fun NowScreen(env: UiEnv, actions: VoxaraActions, navigate: (String) -> Unit = {}) {
    val s = env.state
    val tono = s.tonoEnabled
    val name = stringResource(R.string.ai_name)
    // Refresh Tono's status line when the page appears (automatic calls are rate-limited to hourly).
    // (Also when cloud consent flips: turning Tono's cloud on must not wait for the next visit.)
    LaunchedEffect(tono, s.aiConsent) { if (tono) actions.onAsk(CoachTask.STATUS, null) }

    val zone = RiskZone.of(s.dba)
    val liveTone = zoneTone(zone)
    val level = s.dba.toFloat().takeIf { s.dba > 0.0 }
    val outside = s.dba.roundToInt()
    val unit = stringResource(R.string.unit_dba)
    val status = env.coach.reply?.status?.takeIf { tono } ?: stringResource(R.string.home_status_fallback)
    val cloud = tono && env.coach.source == ReplySource.CLOUD_AI
    val askLabel = if (tono) stringResource(R.string.home_ask, name) else stringResource(R.string.ask_speak)
    val paused = s.monitoringStatus == MonitoringStatus.PAUSED
    val uncalibrated = !s.calibrated && !s.simulated
    val concert = s.mode == SituationMode.CONCERT
    val scene = stringResource(sceneOrBandLabelRes(s.scene, s.dba))
    val compact = isCompactFont()

    val layerOn = s.listening.active
    val est = s.headphoneEstimate?.takeIf { s.headphoneListening }
    val estZone = est?.let { RiskZone.of(it.dba) }
    val weeklyPercent = (s.headphoneWeeklyFraction * 100).roundToInt()
    val weekZone = RiskZone.ofPercent(weeklyPercent.toDouble())
    val listenTone = zoneTone(estZone ?: weekZone)
    // Indigo for the listening meter; the tier colour once the listening itself is loud (the zone tag says it too).
    val loudListening = (estZone ?: RiskZone.OK) >= RiskZone.LOUD
    val listenColor = if (loudListening) VColor.tone(estZone!!).fill else VColor.SecondaryFill
    val listenFraction = est?.let { gaugeFraction(it.dba.toFloat()) } ?: s.headphoneWeeklyFraction.toFloat()
    val progress = rememberLayerProgress(layerOn, s.lastAlertAtMs)
    // The chip (and the foot it replaces) takes over once the label has faded (halfway through its settle); at rest it is simply there.
    val showChip by remember { derivedStateOf { progress.target && (progress.label.value >= 1.5f || (progress.label.value == 0f && progress.moon.value >= 0.999f)) } }

    val doseLine = if (s.dba < DOSE_THRESHOLD_DBA) stringResource(R.string.now_not_accruing, s.dosePercent.roundToInt())
    else stringResource(R.string.now_accruing, formatHeadroom(s.headroomMinutes), s.dosePercent.roundToInt())
    // On the hero the dose line is short (the ring's gap is narrow at the bottom); the full sentence is the first detail.
    val shortDoseLine = if (s.dba < DOSE_THRESHOLD_DBA) stringResource(R.string.now_foot_idle, s.dosePercent.roundToInt())
    else stringResource(R.string.home_today_caption, s.dosePercent.roundToInt())
    val outsideLine = stringResource(R.string.hp_mode_outside_line, outside, s.dosePercent.roundToInt())
    val uncalibratedLine = stringResource(R.string.uncalibrated_short)
    val spoken = if (layerOn) {
        if (est != null) stringResource(R.string.cd_home_headphones, est.dba.roundToInt(), outside, zoneWord(zone), s.dosePercent.roundToInt())
        else stringResource(R.string.cd_home_headphones_idle, weeklyPercent, outside, zoneWord(zone), s.dosePercent.roundToInt())
    } else stringResource(R.string.cd_now, outside, zoneWord(zone), s.dosePercent.roundToInt()) + " " + status +
        (if (uncalibrated) ". $uncalibratedLine" else "")
    val identity = pageIdentity(GlancePage.NOW)

    // What the hero's foot has room for (set from inside the stage, where the room is known); the rest is the first detail.
    var plan by remember { mutableStateOf(FootPlan.Full) }
    val tagOnHero = !compact && !layerOn && plan.tagLines > 0
    val doseOnHero = !compact && !layerOn && plan.dose
    val detailFoot: @Composable ColumnScope.() -> Unit = {
        FootLine(doseLine, color = VColor.Text)
    }

    Box {
    HeroList(
        hero = { m ->
            PageStage(
                identity = identity,
                wave = if (layerOn && est != null) waveOf(est.dba) else waveOf(s.dba),
                waveTone = if (layerOn && est != null) listenTone else liveTone,
                description = spoken,
                modifier = m, listening = s.headphoneListening,
                stereo = { progress.waves.value }, stereoColor = VColor.SecondaryFill,
                // The bottom slot: Tono's tag over today's dose; in headphones mode, the chip.
                nameSlot = if (showChip) {
                    { HeadphonesChip(s.listening, s.headphonePossibleSpeaker, progress, interactive = !compact, onClick = { navigate(Routes.HP_EXIT) }) }
                } else null,
                picture = {
                    Box(Modifier.fillMaxSize()) {
                        NowRing(
                            level = level, layer = progress,
                            listenFraction = listenFraction, listenColor = listenColor,
                            outsideDba = outside.takeIf { s.dba > 0.0 }, outsideTone = VColor.tone(zone),
                        )
                        HeadphonesLabel(progress)
                    }
                },
                foot = if (showChip) null else { room ->
                    val d = LocalDisplay.current
                    val width = d * Face.FOOT_WIDTH
                    val measurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val doseStyle = VType.Caption.heroScaled()
                    val doseHeight = remember(shortDoseLine, width, density, doseStyle) {
                        val px = measurer.measure(shortDoseLine, doseStyle, maxLines = 4, constraints = Constraints(maxWidth = with(density) { width.roundToPx() })).size.height
                        with(density) { px.toDp() }
                    }
                    val planned = footPlan(room, doseHeight, LocalTextScale.current)
                    // Tono's line on the hero is a whole sentence or nothing: when not even one fits it goes under the hero.
                    val fittedTag = rememberFittedStatus(status, tonoTextWidth(d * 0.76f), VType.Label, planned.tagLines)
                    val fit = if (fittedTag == null) planned.copy(tagLines = 0) else planned
                    SideEffect { if (plan != fit) plan = fit }
                    // While headphones mode comes in, the foot fades under the label that curves round the bottom.
                    Column(
                        Modifier.graphicsLayer { alpha = if (progress.target) (1f - 2f * progress.label.value).coerceIn(0f, 1f) else 1f },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // The tag sits higher than the dose line, where the ring's gap is wider: it may take more of the width.
                        if (fittedTag != null) TonoTag(status, cloud, askLabel, fittedTag, onClick = { navigate(Routes.ASK) }, modifier = Modifier.requiredWidthIn(max = d * 0.76f))
                        if (fit.dose) FootLine(shortDoseLine, color = VColor.Text)
                    }
                },
            ) { isCompact ->
                // One figure throughout: it is the outside level, and when headphones mode has taken over it rolls to the
                // listening level (≈) or, with nothing playing, the week's share of the listening allowance.
                val swapped = progress.swapped
                when {
                    !swapped -> HeroFigure(outside.takeIf { s.dba > 0.0 }, liveTone.fill, isCompact, suffix = unit)
                    est != null -> HeroFigure(est.dba.roundToInt(), listenTone.fill, isCompact, suffix = unit, prefix = "≈")
                    else -> HeroFigure(weeklyPercent, listenTone.fill, isCompact, suffix = "%")
                }
                // Under it the two centres never show together: the old words fade out in the first half of the swap,
                // the new ones fade in during the second.
                val normalVisible by remember { derivedStateOf { progress.figure.value < 0.5f } }
                val layerVisible by remember { derivedStateOf { progress.figure.value > 0.5f } }
                Box(contentAlignment = Alignment.TopCenter) {
                    if (normalVisible) {
                        Column(
                            Modifier.graphicsLayer { alpha = (1f - 2f * progress.figure.value).coerceIn(0f, 1f) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (paused) ResumePill(actions.onResume) else ZoneTag(zone)
                            // A required notice: on the hero at every size and font scale (it wraps; the page grows if it must).
                            if (uncalibrated) FootNote(uncalibratedLine, Modifier.requiredWidthIn(max = LocalDisplay.current * 0.82f))
                        }
                    }
                    if (layerVisible) {
                        Column(
                            Modifier.graphicsLayer { alpha = (2f * progress.figure.value - 1f).coerceIn(0f, 1f) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (est != null) {
                                // "estimated" is part of the number's meaning: kept at every size.
                                FigureCaption(stringResource(R.string.estimated_word))
                                ZoneTag(estZone!!)
                            } else if (!isCompact) FigureCaption(stringResource(R.string.hp_week_share_caption))
                        }
                    }
                }
            }
        },
        details = {
            if (layerOn) {
                // The way out, first: detection stays automatic, but the wearer can say this is not headphones.
                row {
                    PillButton(
                        stringResource(R.string.hp_not_headphones), onClick = { navigate(Routes.HP_EXIT) },
                        icon = VoxIcons.Headphones, style = PillStyle.TONAL,
                    )
                }
                row { FootLine(outsideLine, color = VColor.Text) }
                headphonesLiveRows(env)
            }
            // Tono's line, when the hero had no room for the tag (or headphones mode has the chip).
            if (!tagOnHero) {
                row {
                    TonoTag(status, cloud, askLabel, fitted = null, onClick = { navigate(Routes.ASK) }, minHeight = VSize.touch)
                }
            }
            footDetail(!layerOn && !doseOnHero, detailFoot)
            // Off: one line of information (nothing to switch: it turns on by itself).
            if (!layerOn) row { HeadphonesStatusCard(s.listening, s.headphonePossibleSpeaker) }
            row { MetricCard(VoxIcons.Waves, stringResource(R.string.scene_label), scene, accent = VColor.AccentInk) }
            // One tap to the mode picker. Auto's conclusions morph the chip.
            row { ModeChip(modeValue(s.situationChoice, s.mode, s.listening), concert) { navigate(Routes.MODE) } }
            if (s.simulated) row { BodyText(stringResource(R.string.bench_source), color = VColor.AccentInk) }
            if (concert) {
                // A chosen Concert measures continuously; an Auto-detected one does not.
                val note = if (s.situationChoice == SituationChoice.CONCERT) R.string.concert_on_note else R.string.mode_auto_concert_note
                row { BodyText(stringResource(note), color = VColor.TextSecondary) }
            }
        },
    )
    // The headphone-volume alert, inside the headphones view (the same two actions as its notification).
    HeadphoneAlertPanel(env, actions, navigate)
    }
}

/**
 * MODE CHIP — "Mode: Auto · Street". When Auto concludes a different mode the label rolls to the new
 * one with a short pop (200 ms) and the chip fills with the accent while Concert is on. Static with
 * reduced motion.
 */
@Composable
private fun ModeChip(label: String, concert: Boolean, onClick: () -> Unit) {
    val still = LocalReduceMotion.current
    val container by animateColorAsState(
        if (concert) VColor.accent.fill else VColor.CardSubtle,
        if (still) snap() else tween(VMotion.DIGIT_MS),
        label = "modeChip",
    )
    val content = if (concert) VColor.accent.onFill else VColor.Text
    val text = stringResource(R.string.mode_chip, label)
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = VSize.touch)
            .clip(RoundedCornerShape(VRadius.pill))
            .background(container)
            .pressable(onClick = onClick)
            .padding(horizontal = VSpace.l, vertical = VSpace.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VSpace.s, Alignment.CenterHorizontally),
    ) {
        Icon(VoxIcons.Tune, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                if (still) fadeIn(snap()) togetherWith fadeOut(snap())
                else (fadeIn(tween(VMotion.DIGIT_MS)) + scaleIn(tween(VMotion.DIGIT_MS), initialScale = 0.85f)) togetherWith
                    fadeOut(tween(VMotion.DIGIT_MS / 2))
            },
            label = "modeLabel",
        ) { t ->
            Text(t, style = VType.TitleSmall, color = content)
        }
    }
}
