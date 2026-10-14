package com.example.voxara.settings

import com.example.voxara.settings.SettingsMerge.SKEW_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMergeTest {

    private val now = 1_750_000_000_000L
    private fun doc(vararg f: Triple<SettingField, String, Long>) =
        f.fold(SettingsDoc()) { d, (field, v, at) -> d.with(field, v, at) }

    // ------------------------------------------------------------------ validation (the watch clamps)

    @Test
    fun `every value is validated and clamped before it is used`() {
        assertEquals("95", normalize(SettingField.ALERT_THRESHOLD, "120"))
        assertEquals("80", normalize(SettingField.ALERT_THRESHOLD, "40"))
        assertEquals("88", normalize(SettingField.ALERT_THRESHOLD, "87.6"))
        assertEquals("120", normalize(SettingField.SNOOZE_MINUTES, "600"))     // never more than 2 h
        assertEquals("15", normalize(SettingField.SNOOZE_MINUTES, "0"))
        assertEquals("105", normalize(SettingField.HEADPHONE_LOUD, "200"))
        assertEquals("1439", normalize(SettingField.QUIET_START, "5000"))
        assertEquals("10", normalize(SettingField.DAILY_CAP, "99"))
        assertNull(normalize(SettingField.ALERT_THRESHOLD, "loud"))
        assertNull(normalize(SettingField.ALERT_THRESHOLD, "NaN"))
        assertNull(normalize(SettingField.TONO_ENABLED, "yes"))
        assertNull(normalize(SettingField.LANGUAGE, "FRENCH"))
        assertEquals("SPANISH", normalize(SettingField.LANGUAGE, "SPANISH"))
    }

    @Test
    fun `the typed view falls back to the defaults`() {
        val s = VoxaraSettings.from(doc(Triple(SettingField.ALERT_THRESHOLD, "90", 1L)))
        assertEquals(90, s.alertThresholdDba)
        assertEquals(VoxaraSettings.DEFAULTS.copy(alertThresholdDba = 90), s)
        assertEquals(85, VoxaraSettings.from(SettingsDoc()).alertThresholdDba)
        assertEquals(false, VoxaraSettings.DEFAULTS.cloudAi)
    }

    @Test
    fun `json round-trips and drops what it cannot read`() {
        val d = doc(
            Triple(SettingField.ALERT_THRESHOLD, "90", now),
            Triple(SettingField.LANGUAGE, "ENGLISH", now - 5),
            Triple(SettingField.TONO_ENABLED, "false", 3L),
        )
        assertEquals(d, SettingsDoc.fromJson(d.toJson()))
        val tampered = """{"v":1,"fields":{"alert_threshold_dba":{"value":"500","at":10},"unknown":{"value":"1","at":1},"tono_enabled":{"value":"maybe","at":1}}}"""
        val parsed = SettingsDoc.fromJson(tampered)
        assertEquals("95", parsed[SettingField.ALERT_THRESHOLD]?.value)
        assertEquals(1, parsed.fields.size)
        assertEquals(SettingsDoc(), SettingsDoc.fromJson("not json"))
    }

    // ------------------------------------------------------------------ the conflict rule

    @Test
    fun `the newer change wins per field`() {
        val watch = doc(
            Triple(SettingField.ALERT_THRESHOLD, "85", now - 60_000),
            Triple(SettingField.QUIET_ENABLED, "true", now - 1_000),     // changed on the watch just now
        )
        val phone = doc(
            Triple(SettingField.ALERT_THRESHOLD, "90", now - 10_000),    // changed on the phone later than the watch
            Triple(SettingField.QUIET_ENABLED, "false", now - 120_000),  // older than the watch's change
        )
        val r = SettingsMerge.watchReceives(watch, phone, now)
        assertEquals("90", r.merged[SettingField.ALERT_THRESHOLD]?.value)
        assertEquals("true", r.merged[SettingField.QUIET_ENABLED]?.value)
        assertEquals(setOf(SettingField.ALERT_THRESHOLD), r.accepted)
        // The accepted value keeps the phone's timestamp, so the phone sees it applied.
        assertEquals(now - 10_000, r.merged[SettingField.ALERT_THRESHOLD]?.atMs)
    }

    @Test
    fun `changes within the clock skew are ties and ties go to the watch`() {
        val watch = doc(Triple(SettingField.ALERT_THRESHOLD, "85", now - 10_000))
        val phone = doc(Triple(SettingField.ALERT_THRESHOLD, "92", now - 10_000 + SKEW_MS))    // exactly at the skew edge
        assertEquals("85", SettingsMerge.watchReceives(watch, phone, now).merged[SettingField.ALERT_THRESHOLD]?.value)
        val phoneLater = doc(Triple(SettingField.ALERT_THRESHOLD, "92", now - 10_000 + SKEW_MS + 1))
        assertEquals("92", SettingsMerge.watchReceives(watch, phoneLater, now).merged[SettingField.ALERT_THRESHOLD]?.value)

        // The phone receiving the watch's settings: a tie still goes to the watch.
        val phoneOwn = doc(Triple(SettingField.ALERT_THRESHOLD, "92", now - 9_000))
        val fromWatch = doc(Triple(SettingField.ALERT_THRESHOLD, "85", now - 12_000))
        assertEquals("85", SettingsMerge.phoneReceives(phoneOwn, fromWatch, now).merged[SettingField.ALERT_THRESHOLD]?.value)
    }

    @Test
    fun `a phone timestamp in the watch's future never wins`() {
        val watch = doc(Triple(SettingField.ALERT_THRESHOLD, "85", now - 60_000))
        val phone = doc(Triple(SettingField.ALERT_THRESHOLD, "95", now + SKEW_MS + 1))
        val r = SettingsMerge.watchReceives(watch, phone, now)
        assertEquals("85", r.merged[SettingField.ALERT_THRESHOLD]?.value)
        assertEquals(setOf(SettingField.ALERT_THRESHOLD), r.refusedFuture)
        // Inside the skew it is not "future", just a normal (newer) change.
        assertEquals("95", SettingsMerge.watchReceives(watch, doc(Triple(SettingField.ALERT_THRESHOLD, "95", now + SKEW_MS)), now)
            .merged[SettingField.ALERT_THRESHOLD]?.value)
    }

    @Test
    fun `a field the watch never changed takes the phone's value`() {
        val watch = doc(Triple(SettingField.ALERT_THRESHOLD, "85", 0L))       // default, published with time 0
        val phone = doc(Triple(SettingField.ALERT_THRESHOLD, "88", now - 1_000))
        assertEquals("88", SettingsMerge.watchReceives(watch, phone, now).merged[SettingField.ALERT_THRESHOLD]?.value)
        assertEquals("88", SettingsMerge.watchReceives(SettingsDoc(), phone, now).merged[SettingField.ALERT_THRESHOLD]?.value)
    }

    @Test
    fun `out-of-range values from the phone are clamped by the receiver`() {
        val phone = SettingsDoc(mapOf(SettingField.SNOOZE_MINUTES to Stamped("480", now - 1_000)))
        val r = SettingsMerge.watchReceives(SettingsDoc(), phone, now)
        assertEquals("120", r.merged[SettingField.SNOOZE_MINUTES]?.value)
    }

    @Test
    fun `pending lists phone changes the watch has not applied`() {
        val phone = doc(
            Triple(SettingField.ALERT_THRESHOLD, "90", now),
            Triple(SettingField.TONO_ENABLED, "false", now - 60_000),
        )
        assertEquals(phone.fields.keys, SettingsMerge.pending(phone, null))
        val watch = doc(
            Triple(SettingField.ALERT_THRESHOLD, "85", now - 30_000),
            Triple(SettingField.TONO_ENABLED, "false", now - 60_000),
        )
        assertEquals(setOf(SettingField.ALERT_THRESHOLD), SettingsMerge.pending(phone, watch))
        // Once the watch echoes the phone's change (same timestamp), nothing is pending.
        val applied = SettingsMerge.watchReceives(watch, phone, now).merged
        assertTrue(SettingsMerge.pending(phone, applied).isEmpty())
    }
}
