package com.example.voxara.phone.model

import com.example.voxara.core.ai.HearingCareScore
import com.example.voxara.core.ai.SnapshotBuilder
import com.example.voxara.core.ai.SnapshotInputs
import com.example.voxara.core.ledger.DaySummary
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.energyOf
import com.example.voxara.core.ledger.nioshDoseOf
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.export.CsvExport
import com.example.voxara.sync.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** What the phone derives from the synced ledger: same shared functions, never presented as live. */
class HistoryModelTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val now = 1_750_000_000_000L                   // 2025-06-15 15:06:40 UTC
    private val todayStart = Math.floorDiv(now, 60_000L) / 1440 * 1440

    private fun minute(m: Long, dba: Double, kind: ExposureKind = ExposureKind.AMBIENT, origin: Int = 0) =
        MinuteRecord(m, kind, 60.0, energyOf(dba, 60.0), nioshDoseOf(dba, 60.0), origin = origin)

    private fun status(atMs: Long) = WatchStatus(
        atMs = atMs, appVersion = "0.9", monitoring = MonitoringStatus.RUNNING, micGranted = true,
        notificationsGranted = true, calibrated = false, calibratedAtMs = null, dosePercent = 37.0,
        weeklyPercent = 55.0, headphoneWeeklyPercent = 20.0, lastDba = 88.0, lastReadingAtMs = atMs,
        sensitiveListener = false, powerSaveMode = false, ignoringBatteryOptimizations = true,
        tonoEnabled = true, retentionDays = 35,
    )

    @Test
    fun `freshness never calls old data recent`() {
        assertEquals(Freshness.NONE, Freshness.of(null, now))
        assertEquals(Freshness.RECENT, Freshness.of(now - 60_000, now))
        assertEquals(Freshness.EARLIER, Freshness.of(now - 3_600_000, now))
        assertEquals(Freshness.STALE, Freshness.of(now - 4 * 3_600_000L, now))
    }

    @Test
    fun `days come from the shared ledger functions, one record per minute`() {
        val records = listOf(
            minute(todayStart + 60, 90.0), minute(todayStart + 60, 70.0, origin = 1),   // same minute, two origins
            minute(todayStart + 61, 90.0),
        )
        val h = History.build(records, emptyList(), emptyList(), now, utc)
        assertEquals(2, h.todayMinutes.size)                // louder origin kept, never summed
        assertEquals(2.0, h.ambient.first().measuredMinutes, 1e-9)
        assertEquals(2 * nioshDoseOf(90.0, 60.0), h.ambient.first().nioshDosePercent, 1e-9)
        assertFalse(h.isEmpty)
        assertTrue(History.build(emptyList(), emptyList(), emptyList(), now, utc).isEmpty)
    }

    @Test
    fun `zone segments cover the day and mark unmeasured time`() {
        val records = (0 until 30).map { minute(todayStart + 600 + it, if (it < 10) 60.0 else 96.0) }
        val segs = zoneSegments(records, todayStart)
        assertEquals(0, segs.first().fromMinute)
        assertEquals(1440, segs.last().toMinute)
        assertEquals(listOf(null, RiskZone.OK, RiskZone.DANGEROUS, null), segs.map { it.zone })
        assertEquals(600 to 610, segs[1].fromMinute to segs[1].toMinute)
    }

    @Test
    fun `the headline is the watch's own figure only when its status is from today`() {
        val h = History.build(listOf(minute(todayStart + 10, 90.0)), emptyList(), emptyList(), now, utc)
        val fresh = headline(status(now - 60_000), h, lastSyncMs = now - 60_000, sensitive = false, nowMs = now, zone = utc)
        assertTrue(fresh.fromWatchStatus)
        assertEquals(37.0, fresh.dosePercent, 0.0)
        val yesterday = headline(status(now - 20 * 3_600_000L), h, lastSyncMs = now - 60_000, sensitive = false, nowMs = now, zone = utc)
        assertFalse(yesterday.fromWatchStatus)
        assertEquals(h.ambient.first().nioshDosePercent, yesterday.dosePercent, 1e-9)
        assertEquals(now - 60_000, yesterday.asOfMs)
    }

    @Test
    fun `Tono never gets an old reading as the current level`() {
        val h = History.EMPTY
        val live = phoneSnapshotInputs(status(now - 60_000), h, headline(status(now - 60_000), h, null, false, now, utc), false, now)
        assertEquals(88.0, live.dba, 0.0)
        val old = phoneSnapshotInputs(status(now - 30 * 60_000L), h, headline(status(now - 30 * 60_000L), h, null, false, now, utc), false, now)
        assertEquals(0.0, old.dba, 0.0)
        assertNull(SnapshotBuilder.build(old, "en").currentDba)
    }

    @Test
    fun `the phone's snapshot equals the watch's for the same figures`() {
        val days = (0 until 7).map { DaySummary(20_000L - it, List(24) { h -> if (h == 9) 82f else 0f }, 30.0 + it, 120.0, 0.1) }
        val watchSide = SnapshotInputs(
            dosePercent = 37.0, weeklyFraction = 0.55, headphoneWeeklyFraction = 0.2, dba = 88.0, simulated = false,
            sensitiveListener = false, days = days, headphoneDays = days, headphoneCategory = null, calibrated = false,
        )
        val h = History(days, days, emptyList(), todayStart, emptyList())
        val st = status(now - 60_000)
        val phoneSide = phoneSnapshotInputs(st, h, headline(st, h, null, false, now, utc), false, now)
        assertEquals(SnapshotBuilder.build(watchSide, "es"), SnapshotBuilder.build(phoneSide, "es"))
        assertEquals(HearingCareScore.of(55.0, 20.0, 0), careScore(55.0, 20.0, days))
    }

    @Test
    fun `rolling budget and week comparison`() {
        val days = (0 until 14).map { DaySummary(20_000L - it, List(24) { 0f }, 0.0, 60.0, if (it < 7) 0.16 else 0.08) }
        val rolling = rollingWeeklyPercent(days, sensitive = false, count = 3)
        assertEquals(3, rolling.size)
        assertEquals(100.0 * 0.16 * 7 / 1.6, rolling.last(), 1e-9)       // newest: this week's 7 days
        val cmp = weekCompare(days, emptyList(), sensitive = false)
        assertEquals(70.0, cmp.thisWeek, 1e-9)
        assertEquals(35.0, cmp.previousWeek, 1e-9)
        assertEquals(0.0, cmp.headphoneShare, 0.0)
    }

    @Test
    fun `csv export holds numbers only`() {
        val csv = CsvExport.minutesCsv(listOf(minute(todayStart + 60, 90.0), minute(todayStart + 61, 75.0, ExposureKind.HEADPHONE, origin = 1)), utc)
        val lines = csv.trim().lines()
        assertEquals(CsvExport.MINUTES_HEADER, lines[0])
        assertTrue(lines[1], lines[1].startsWith("2025-06-15,01:00,ambient,60.0,90.0,0.6614,"))
        assertTrue(lines[1].endsWith(",watch,"))
        assertTrue(lines[2].startsWith("2025-06-15,01:01,headphones,60.0,75.0,"))
        assertTrue(lines[2].endsWith(",phone,unknown"))
        // Nothing but numbers, fixed words and dates: no names, addresses or identifiers.
        assertTrue(csv.lines().drop(1).all { it.isEmpty() || Regex("""^[0-9:,.\-a-z_]+$""").matches(it) })
    }
}
