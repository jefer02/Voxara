package com.example.voxara.phone.ui

import com.example.voxara.ai.CoachUi
import com.example.voxara.ai.ReplySource
import com.example.voxara.audio.HeadphoneWatcher
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertRecord
import com.example.voxara.core.headphones.HeadphoneAlert
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.HeadphoneRoute
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.dayStartMinute
import com.example.voxara.core.ledger.energyOf
import com.example.voxara.core.ledger.localEpochDay
import com.example.voxara.core.ledger.nioshDoseOf
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.link.LinkState
import com.example.voxara.link.WatchNode
import com.example.voxara.phone.listening.PhoneListeningRuntime
import com.example.voxara.phone.model.Budget
import com.example.voxara.phone.model.History
import com.example.voxara.settings.SettingField
import com.example.voxara.sync.WatchStatus
import java.util.TimeZone

/**
 * Fake data for the screenshot and consistency tests. Nothing here comes from a device, and nothing touches the
 * network or the Data Layer.
 *
 * Everything is DERIVED: the days are built from one-minute ledger records, the watch's status takes its figures from
 * that ledger, and the alerts sit at the minute the dose crossed their level. So the screens can disagree only if the
 * app does. The clock is fixed: Tuesday 17 June 2025, 15:06 UTC (the default time zone is set to UTC so the renders
 * do not depend on the machine).
 */
object PhoneFixtures {
    init { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }

    /** Tuesday 17 June 2025, 15:06:40 UTC. */
    const val now = 1_750_172_800_000L
    val watch = WatchNode("w1", "Galaxy Watch7", nearby = true)

    private val zone = TimeZone.getTimeZone("UTC")
    private val todayEpoch = localEpochDay(now, zone)
    private val todayStart = dayStartMinute(todayEpoch, zone)

    /** Minutes of 88 dBA that add one point of the daily dose (permitted time at 88 dBA is 4 h). */
    private const val POINT_PER_MINUTE_88 = 100.0 / 240.0

    private fun record(minute: Long, kind: ExposureKind, dba: Double): MinuteRecord =
        MinuteRecord(minute, kind, 60.0, energyOf(dba, 60.0), if (kind == ExposureKind.AMBIENT) nioshDoseOf(dba, 60.0) else 0.0)

    /** A past day: a quiet background from 07:00 to 19:00 and as many minutes at 88 dBA as reach [dosePercent]. */
    private fun pastDay(back: Int, dosePercent: Double): List<MinuteRecord> {
        if (dosePercent <= 0.0) return emptyList()
        val start = todayStart - back * 1440L
        val loud = (dosePercent / POINT_PER_MINUTE_88).toInt().coerceAtMost(600)
        val background = (7 * 60 until 19 * 60).filter { it !in 12 * 60 until 12 * 60 + loud }
        return background.map { record(start + it, ExposureKind.AMBIENT, 58.0) } +
            (12 * 60 until 12 * 60 + loud).map { record(start + it, ExposureKind.AMBIENT, 88.0) }
    }

    /** The last 6 days are quiet (their sum keeps the week under its allowance); older days vary, two over the daily limit. */
    private val pastDoses = listOf(
        12.0, 4.0, 16.0, 0.0, 6.0, 3.0,
        22.0, 18.0, 30.0, 0.0, 12.0, 8.0, 20.0,
        104.0, 28.0, 64.0, 41.0, 55.0, 12.0, 87.0, 33.0, 0.0, 71.0, 46.0, 19.0, 92.0, 25.0, 58.0, 7.0, 120.0, 38.0,
    )

    /** Headphones: a few minutes at 88 dBA on four of the last six days. */
    private val headphoneMinutes = mapOf(1 to 15, 3 to 12, 5 to 15)

    private fun headphoneDay(back: Int, minutes: Int): List<MinuteRecord> =
        (0 until minutes).map { record(todayStart - back * 1440L + 17 * 60 + it, ExposureKind.HEADPHONE, 88.0) }

    /**
     * Today as a list of (from minute, to minute, dBA) in minutes after midnight. The LOUD scenario holds a 20-minute
     * stretch at 97 dBA at 14:00 (3.3 points a minute) after 40 minutes at 88 dBA: 83 % of the day by 14:20.
     */
    private val loudToday = listOf(
        Triple(6 * 60 + 30, 7 * 60 + 30, 70.0), Triple(8 * 60, 9 * 60, 74.0), Triple(9 * 60, 12 * 60, 58.0),
        Triple(12 * 60 + 20, 13 * 60, 88.0), Triple(13 * 60, 14 * 60, 58.0), Triple(14 * 60, 14 * 60 + 20, 97.0),
        Triple(14 * 60 + 20, 15 * 60, 60.0),
    )
    private val calmToday = listOf(
        Triple(6 * 60 + 30, 7 * 60 + 30, 70.0), Triple(8 * 60, 9 * 60, 74.0), Triple(9 * 60, 12 * 60, 58.0),
        Triple(12 * 60 + 20, 13 * 60 + 22, 88.0), Triple(13 * 60 + 22, 15 * 60, 58.0),
    )

    private fun todayRecords(plan: List<Triple<Int, Int, Double>>): List<MinuteRecord> =
        plan.flatMap { (from, to, dba) -> (from until to).map { record(todayStart + it, ExposureKind.AMBIENT, dba) } }

    /** Alerts of the older days, newest first (the calm day keeps only these). */
    private val olderAlerts = listOf(
        AlertRecord(3, now - 3 * 86_400_000L - 10 * 60_000L, AlertKind.HEADPHONE_LOUD_NOW, 101.0, 0, acknowledged = false, snoozed = true),
        AlertRecord(2, todayStart * 60_000L - 14 * 86_400_000L + 16 * 3_600_000L + 5 * 60_000L, AlertKind.DAILY_100, 102.0, 100, acknowledged = true, snoozed = false),
    )

    /** What the loud afternoon delivered, newest first: the dose crossed 50 % at 14:10 and 80 % at 14:19. */
    private val loudAlerts = listOf(
        AlertRecord(6, (todayStart + 14 * 60 + 19) * 60_000L, AlertKind.DAILY_80, 80.3, 0, acknowledged = false, snoozed = false),
        AlertRecord(5, (todayStart + 14 * 60 + 12) * 60_000L, AlertKind.AMBIENT_LOUD, 97.0, 0, acknowledged = true, snoozed = false),
        AlertRecord(4, (todayStart + 14 * 60 + 10) * 60_000L, AlertKind.DAILY_50, 50.0, 0, acknowledged = true, snoozed = false),
    ) + olderAlerts

    private fun historyFor(plan: List<Triple<Int, Int, Double>>, alerts: List<AlertRecord>): History {
        val ambient = todayRecords(plan) + pastDoses.flatMapIndexed { i, dose -> pastDay(i + 1, dose) }
        val headphones = headphoneMinutes.flatMap { (back, minutes) -> headphoneDay(back, minutes) }
        return History.build(ambient, headphones, alerts, now)
    }

    val history: History = historyFor(loudToday, loudAlerts)
    val calmHistory: History = historyFor(calmToday, olderAlerts)

    /** The watch's status, taken from the ledger the way the watch's own engine would report it. */
    private fun statusFor(h: History): WatchStatus {
        val budget = Budget.weeklyPa2h(false)
        return WatchStatus(
            atMs = now - 4 * 60_000L, appVersion = "0.9", monitoring = MonitoringStatus.RUNNING, micGranted = true,
            notificationsGranted = true, calibrated = false, calibratedAtMs = null,
            dosePercent = h.ambient[0].nioshDosePercent,
            weeklyPercent = 100.0 * h.ambient.take(7).sumOf { it.energyPa2h } / budget,
            headphoneWeeklyPercent = 100.0 * h.headphone.take(7).sumOf { it.energyPa2h } / budget,
            lastDba = 60.0, lastReadingAtMs = now - 4 * 60_000L, sensitiveListener = false,
            powerSaveMode = false, ignoringBatteryOptimizations = false, tonoEnabled = true, retentionDays = 35,
        )
    }

    val status: WatchStatus = statusFor(history)
    val calmStatus: WatchStatus = statusFor(calmHistory)

    val reply = CoachReply(
        status = "A busy afternoon for your ears",
        insight = "You are at 83% of today's limit. The loudest stretch was around 14:00.",
        why = "Most of it came from twenty minutes near 14:00.",
        suggestion = "A quieter evening keeps the week on track.",
        chips = listOf("My week?", "Why estimated?", "Tips"),
    )
    val calmReply = reply.copy(status = "Calm day for your ears", insight = "You are at 34% of today's limit.", why = "", suggestion = "")

    val state = PhoneUiState(
        link = LinkState.Linked(watch, emptyList(), now - 4 * 60_000L),
        status = status, history = history, lastSyncMs = now - 4 * 60_000L, nowMs = now,
        coach = CoachUi(reply = reply, source = ReplySource.CLOUD_AI),
        chat = listOf(ChatTurn("How loud was lunch?", reply, ReplySource.CLOUD_AI, cloudFailed = false)),
        cloudAvailable = true, pending = setOf(SettingField.ALERT_THRESHOLD), phoneSampling = true,
        lastListeningSentMs = now - 30 * 60_000L, appVersion = "0.9.2-phone",
    )

    /** The same phone on a calm day: today at 34 %, nothing alerted today. */
    val calmState: PhoneUiState = state.copy(status = calmStatus, history = calmHistory, coach = CoachUi(reply = calmReply, source = ReplySource.CLOUD_AI))

    val hpSnapshot = HeadphoneWatcher.Snapshot(
        route = HeadphoneRoute(HeadphoneCategory.UNKNOWN, OutputKind.BLUETOOTH_A2DP, 0, possibleSpeaker = true),
        playing = true, volumeDb = -14.0, volumeIndex = 11, volumeMax = 15, fixedVolume = false, btDetailsAvailable = false,
    )

    /** The phone's own headphones above the recommended level, with the alert showing. */
    val liveOver = PhoneListeningRuntime.Live(
        running = true, snapshot = hpSnapshot,
        alert = HeadphoneAlert(1, 86.0, now, LayerSource.PHONE),
    )
}
