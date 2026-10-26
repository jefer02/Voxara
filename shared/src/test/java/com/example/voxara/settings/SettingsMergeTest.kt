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
    fun `a phone timestamp in the watch's future is accepted at the watch's now, not lost and not eternal`() {
        val watch = doc(Triple(SettingField.ALERT_THRESHOLD, "85", now - 60_000))
        val phone = doc(Triple(SettingField.ALERT_THRESHOLD, "95", now + 20_000))      // the phone's clock runs 20 s ahead
        val r = SettingsMerge.watchReceives(watch, phone, now)
        // The change is NOT refused (the Data Layer would never deliver it again) ...
        assertEquals("95", r.merged[SettingField.ALERT_THRESHOLD]?.value)
        assertEquals(setOf(SettingField.ALERT_THRESHOLD), r.clampedFuture)
        // ... but it is stamped with the watch's own now, so it cannot win every later comparison.
        assertEquals(now, r.merged[SettingField.ALERT_THRESHOLD]?.atMs)
        val laterOnWatch = r.merged.with(SettingField.ALERT_THRESHOLD, "88", now + 30_000)
        val againFromPhone = SettingsMerge.watchReceives(laterOnWatch, phone, now + 31_000)
        assertEquals("88", againFromPhone.merged[SettingField.ALERT_THRESHOLD]?.value)
        // Inside the skew it is not "future", just a normal (newer) change.
        val inside = SettingsMerge.watchReceives(watch, doc(Triple(SettingField.ALERT_THRESHOLD, "95", now + SKEW_MS)), now)
        assertEquals("95", inside.merged[SettingField.ALERT_THRESHOLD]?.value)
        assertTrue(inside.clampedFuture.isEmpty())
    }

    @Test
    fun `a future-dated phone value is accepted once, and its redelivery cannot undo a later change on the watch`() {
        val f = SettingField.ALERT_THRESHOLD
        val watch = doc(Triple(f, "85", now - 60_000))
        val phone = doc(Triple(f, "95", now + 600_000))                              // the phone's clock is 10 minutes ahead
        val first = SettingsMerge.watchReceives(watch, phone, now)
        assertEquals("95", first.merged[f]?.value)
        assertEquals(mapOf(f to now + 600_000), first.futureSeen)

        // The wearer sets 88 on the watch a minute later; then the phone's document comes again (the pull on reconnect).
        val laterOnWatch = first.merged.with(f, "88", now + 60_000)
        val redelivered = SettingsMerge.watchReceives(laterOnWatch, phone, now + 120_000, first.futureSeen)
        assertEquals("the wearer's later change stays", "88", redelivered.merged[f]?.value)
        assertTrue(redelivered.clampedFuture.isEmpty())

        // Without the memory the stale value would beat it (this is what the memory is for).
        val forgetful = SettingsMerge.watchReceives(laterOnWatch, phone, now + 120_000)
        assertEquals("95", forgetful.merged[f]?.value)

        // A NEW change on the phone has a new stamp and is accepted again.
        val newer = doc(Triple(f, "92", now + 700_000))
        val second = SettingsMerge.watchReceives(laterOnWatch, newer, now + 130_000, first.futureSeen)
        assertEquals("92", second.merged[f]?.value)
    }

    @Test
    fun `a change from a phone with a fast clock is not pending for ever`() {
        val watch = doc(Triple(SettingField.ALERT_THRESHOLD, "85", now - 60_000))
        val phone = doc(Triple(SettingField.ALERT_THRESHOLD, "95", now + 20_000))
        val applied = SettingsMerge.watchReceives(watch, phone, now).merged
        // The watch holds the phone's value, stamped 20 s earlier than the phone's own stamp: nothing to apply.
        assertTrue(SettingsMerge.pending(phone, applied).isEmpty())
        // A different value is still pending.
        assertEquals(setOf(SettingField.ALERT_THRESHOLD), SettingsMerge.pending(phone, watch))
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
