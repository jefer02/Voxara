package com.example.voxara.core.situation

import com.example.voxara.core.ai.AggregateSnapshot
import com.example.voxara.core.ai.SnapshotBuilder
import com.example.voxara.core.ai.SnapshotInputs
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.settings.SettingField
import com.example.voxara.settings.SettingsDoc
import com.example.voxara.settings.SettingsMerge
import com.example.voxara.settings.VoxaraSettings
import com.example.voxara.settings.normalize
import com.example.voxara.sync.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Situation modes: migration, Auto, the synced choice, the status field and Tono's data. */
class SituationTest {

    @Test
    fun `the pre-modes value migrates URBAN and VOICE to Street, CONCERT to Concert`() {
        assertEquals(SituationChoice.STREET, Situation.migrateLegacyMode("URBAN"))
        assertEquals(SituationChoice.STREET, Situation.migrateLegacyMode("VOICE"))
        assertEquals(SituationChoice.CONCERT, Situation.migrateLegacyMode("CONCERT"))
        // Nothing stored: a fresh install keeps the Auto default.
        assertNull(Situation.migrateLegacyMode(null))
        assertNull(Situation.migrateLegacyMode(""))
        // Garbage was never a valid mode: the old code read it as Urban, so it becomes Street.
        assertEquals(SituationChoice.STREET, Situation.migrateLegacyMode("???"))
    }

    @Test
    fun `Auto is the default and, for now, resolves to Street`() {
        assertEquals(SituationChoice.AUTO, SituationChoice.DEFAULT)
        assertEquals(SituationChoice.AUTO, VoxaraSettings.DEFAULTS.situationChoice)
        assertEquals(SituationMode.STREET, Situation.resolve(SituationChoice.AUTO))
        SituationMode.entries.forEach { m -> assertEquals(m, Situation.resolve(SituationChoice.of(m))) }
        // A fixed choice always wins over anything detected.
        assertEquals(SituationMode.WORK, Situation.resolve(SituationChoice.WORK, detected = SituationMode.CONCERT))
    }

    @Test
    fun `situation_mode is validated like every other synced field`() {
        assertEquals("CONCERT", normalize(SettingField.SITUATION_MODE, " CONCERT "))
        assertNull(normalize(SettingField.SITUATION_MODE, "PARTY"))
        val doc = SettingsDoc().with(SettingField.SITUATION_MODE, "TRANSPORT", 10L)
        assertEquals(SituationChoice.TRANSPORT, VoxaraSettings.from(SettingsDoc.fromJson(doc.toJson())).situationChoice)
        // An unreadable value on the wire is dropped, never guessed.
        val tampered = SettingsDoc.fromJson(doc.toJson().replace("TRANSPORT", "PARTY"))
        assertNull(tampered[SettingField.SITUATION_MODE])
    }

    @Test
    fun `the newest situation_mode wins, ties go to the watch`() {
        val now = 1_750_000_000_000L
        val watch = SettingsDoc().with(SettingField.SITUATION_MODE, "WORK", now - 60_000)
        val newerPhone = SettingsDoc().with(SettingField.SITUATION_MODE, "QUIET", now - 1_000)
        val watchApplies = SettingsMerge.watchReceives(watch, newerPhone, now)
        assertEquals("QUIET", watchApplies.merged[SettingField.SITUATION_MODE]?.value)
        assertEquals(setOf(SettingField.SITUATION_MODE), watchApplies.accepted)

        val tiePhone = SettingsDoc().with(SettingField.SITUATION_MODE, "QUIET", now - 58_000)
        assertEquals("WORK", SettingsMerge.watchReceives(watch, tiePhone, now).merged[SettingField.SITUATION_MODE]?.value)

        val newerWatch = SettingsDoc().with(SettingField.SITUATION_MODE, "CONCERT", now)
        val phoneTakes = SettingsMerge.phoneReceives(newerPhone, newerWatch, now)
        assertEquals("CONCERT", phoneTakes.merged[SettingField.SITUATION_MODE]?.value)
    }

    @Test
    fun `the mode the watch is in travels in WatchStatus, and older watches send none`() {
        val status = WatchStatus(
            atMs = 1L, appVersion = "1", monitoring = MonitoringStatus.RUNNING, micGranted = true,
            notificationsGranted = true, calibrated = false, calibratedAtMs = null, dosePercent = 1.0,
            weeklyPercent = 2.0, headphoneWeeklyPercent = 3.0, lastDba = 60.0, lastReadingAtMs = 1L,
            sensitiveListener = false, powerSaveMode = false, ignoringBatteryOptimizations = false,
            tonoEnabled = true, retentionDays = 35, situationMode = SituationMode.TRANSPORT,
        )
        assertEquals(SituationMode.TRANSPORT, WatchStatus.fromJson(status.toJson())?.situationMode)
        val older = status.toJson().replace(",\"situation_mode\":\"TRANSPORT\"", "")
        assertTrue(!older.contains("situation_mode"))
        assertNull(WatchStatus.fromJson(older)?.situationMode)
        // It is a status, never a setting: no settings field carries the mode IN force.
        assertTrue(SettingField.entries.none { it.key == "situation_mode_active" })
    }

    @Test
    fun `headphones mode travels in WatchStatus with its source, and older watches mean off`() {
        val status = WatchStatus(
            atMs = 1L, appVersion = "1", monitoring = MonitoringStatus.RUNNING, micGranted = true,
            notificationsGranted = true, calibrated = false, calibratedAtMs = null, dosePercent = 1.0,
            weeklyPercent = 2.0, headphoneWeeklyPercent = 3.0, lastDba = 60.0, lastReadingAtMs = 1L,
            sensitiveListener = false, powerSaveMode = false, ignoringBatteryOptimizations = false,
            tonoEnabled = true, retentionDays = 35, situationMode = SituationMode.TRANSPORT,
            headphonesMode = true, headphonesSource = LayerSource.PHONE,
        )
        val back = WatchStatus.fromJson(status.toJson())
        assertEquals(status, back)
        val older = status.toJson().replace(",\"hp_mode\":true", "").replace(",\"hp_source\":\"PHONE\"", "")
        assertTrue(!older.contains("hp_mode") && !older.contains("hp_source"))
        val old = WatchStatus.fromJson(older)
        assertEquals(false, old?.headphonesMode)
        assertEquals(LayerSource.NONE, old?.headphonesSource)
        // An unknown source from a newer watch falls back safely.
        assertEquals(LayerSource.NONE, WatchStatus.fromJson(status.toJson().replace("PHONE", "SOMETHING_NEW"))?.headphonesSource)
    }

    @Test
    fun `Tono sees the mode as one coarse word, and nothing else changes`() {
        val inputs = SnapshotInputs(
            dosePercent = 12.0, weeklyFraction = 0.2, headphoneWeeklyFraction = 0.1, dba = 70.0,
            simulated = false, sensitiveListener = false, days = emptyList(), headphoneDays = emptyList(),
            headphoneCategory = null, calibrated = false,
        )
        val none = SnapshotBuilder.build(inputs, "en")
        val concert = SnapshotBuilder.build(inputs.copy(situation = SituationMode.CONCERT), "en")
        assertNull(none.situationMode)
        assertEquals("concert", concert.situationMode)
        assertEquals(AggregateSnapshot.WIRE_KEYS, concert.toWire().keys)
        // Same numbers either way: the mode only sets the tone.
        assertEquals(none.copy(situationMode = "concert"), concert)
        assertEquals(none.allowedNumbers(), concert.allowedNumbers())
    }

    @Test
    fun `each mode puts its own card first`() {
        assertEquals(FeaturedCard.WEEK, Situation.featuredCard(SituationMode.QUIET))
        assertEquals(FeaturedCard.NOW, Situation.featuredCard(SituationMode.STREET))
        assertEquals(FeaturedCard.HEADPHONES, Situation.featuredCard(SituationMode.TRANSPORT))
        assertEquals(FeaturedCard.NOW, Situation.featuredCard(SituationMode.CONCERT))
        assertEquals(FeaturedCard.TODAY, Situation.featuredCard(SituationMode.WORK))
    }

    @Test
    fun `Concert keeps its limits from before modes existed`() {
        assertEquals(6L * 60 * 60 * 1000, Situation.CONCERT_CAP_MS)
        assertEquals(20L * 60 * 1000, Situation.CONCERT_EXIT_QUIET_MS)
        assertEquals(80.0, Situation.CONCERT_EXIT_BELOW_DBA, 0.0)
    }
}
