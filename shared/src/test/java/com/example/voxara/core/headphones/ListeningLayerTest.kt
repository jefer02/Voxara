package com.example.voxara.core.headphones

import com.example.voxara.settings.SettingField
import com.example.voxara.settings.SettingsDoc
import com.example.voxara.settings.SettingsMerge
import com.example.voxara.settings.VoxaraSettings
import com.example.voxara.settings.normalize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Headphones mode: the choice, the hysteresis, the quick switch and the phone report's freshness. */
class ListeningLayerTest {

    private fun layer() = ListeningLayer()
    private val auto = HeadphoneModeChoice.AUTO

    @Test fun choiceParsesNamesOnly() {
        assertEquals(HeadphoneModeChoice.ON, HeadphoneModeChoice.parse(" ON "))
        assertNull(HeadphoneModeChoice.parse("MAYBE"))
        assertNull(HeadphoneModeChoice.parse(null))
        assertEquals(HeadphoneModeChoice.AUTO, HeadphoneModeChoice.DEFAULT)
    }

    @Test fun startsOffWithNothingDetected() {
        val s = layer().update(0, auto, watchHeadphones = false, phoneHeadphones = false)
        assertEquals(ListeningLayerState.OFF, s)
    }

    @Test fun entersOnlyAfterThreeSeconds() {
        val l = layer()
        assertFalse(l.update(0, auto, true, false).active)
        assertTrue(l.pending)
        assertFalse(l.update(2_900, auto, true, false).active)
        val on = l.update(3_000, auto, true, false)
        assertTrue(on.active)
        assertEquals(LayerSource.WATCH, on.source)
        assertFalse(l.pending)
    }

    @Test fun leavesOnlyAfterTenSeconds() {
        val l = layer()
        l.update(0, auto, true, false); l.update(3_000, auto, true, false)
        assertTrue(l.update(20_000, auto, false, false).active)         // gone, still holding
        assertTrue(l.update(29_900, auto, false, false).active)
        assertFalse(l.update(30_000, auto, false, false).active)         // gone for 10 s
    }

    @Test fun aFlappingLinkNeverTurnsItOnOrOff() {
        val l = layer()
        // Connected for 2 s, gone for 1 s, connected for 2 s...: never three seconds in a row.
        var t = 0L
        repeat(10) {
            assertFalse(l.update(t, auto, true, false).active); t += 2_000
            assertFalse(l.update(t, auto, false, false).active); t += 1_000
        }
        // Once on, a drop shorter than ten seconds and a return changes nothing.
        l.update(t, auto, true, false); t += 3_000
        assertTrue(l.update(t, auto, true, false).active)
        repeat(10) {
            assertTrue(l.update(t, auto, false, false).active); t += 8_000
            assertTrue(l.update(t, auto, true, false).active); t += 1_000
        }
    }

    @Test fun phoneDetectionIsLabelledAsFromThePhone() {
        val l = layer()
        l.update(0, auto, false, true)
        val s = l.update(3_000, auto, false, true)
        assertTrue(s.active)
        assertEquals(LayerSource.PHONE, s.source)
        assertEquals(LayerSource.PHONE, s.detectedBy)
    }

    @Test fun theWatchWinsTheLabelWhenBothSeeHeadphones() {
        val l = layer()
        l.update(0, auto, true, true)
        assertEquals(LayerSource.WATCH, l.update(3_000, auto, true, true).source)
    }

    @Test fun manualOnAndOffAreImmediateAndKeepTrackingDetection() {
        val l = layer()
        val on = l.update(0, HeadphoneModeChoice.ON, false, false)
        assertTrue(on.active); assertEquals(LayerSource.MANUAL, on.source); assertFalse(on.detected)
        // Headphones really arrive while it is manually Off: detected, but the layer stays off.
        l.update(1_000, HeadphoneModeChoice.OFF, true, false)
        val off = l.update(4_000, HeadphoneModeChoice.OFF, true, false)
        assertFalse(off.active); assertEquals(LayerSource.MANUAL, off.source); assertTrue(off.detected)
        // Back to Auto continues from what is really connected: on at once, no new hold.
        val back = l.update(4_100, auto, true, false)
        assertTrue(back.active); assertEquals(LayerSource.WATCH, back.source)
    }

    @Test fun withChoiceAppliesAtOnceFromWhatIsDetected() {
        val detected = ListeningLayerState(active = true, source = LayerSource.PHONE, detected = true, detectedBy = LayerSource.PHONE)
        assertFalse(detected.withChoice(HeadphoneModeChoice.OFF).active)
        assertEquals(detected, detected.withChoice(HeadphoneModeChoice.AUTO))
        assertEquals(LayerSource.MANUAL, detected.withChoice(HeadphoneModeChoice.ON).source)
        assertFalse(ListeningLayerState.OFF.withChoice(HeadphoneModeChoice.AUTO).active)
    }

    @Test fun resetForgetsDetection() {
        val l = layer()
        l.update(0, auto, true, false); l.update(3_000, auto, true, false)
        l.reset()
        assertFalse(l.update(3_100, auto, false, false).active)
    }

    @Test fun quickToggleReturnsToAutoWhenAutoWouldAgree() {
        // Headphones detected, layer on: turning off is manual; turning it back on is just Auto again.
        assertEquals(HeadphoneModeChoice.OFF, quickToggleChoice(wantOn = false, detected = true))
        assertEquals(HeadphoneModeChoice.AUTO, quickToggleChoice(wantOn = true, detected = true))
        // Nothing detected: turning on is manual; turning it off again is Auto.
        assertEquals(HeadphoneModeChoice.ON, quickToggleChoice(wantOn = true, detected = false))
        assertEquals(HeadphoneModeChoice.AUTO, quickToggleChoice(wantOn = false, detected = false))
    }

    @Test fun aPhoneReportCountsForAboutAnIntervalAndNoLonger() {
        val now = 10_000_000L
        assertFalse(ListeningLayer.phoneReports(now, 0L))
        assertTrue(ListeningLayer.phoneReports(now, now - 60_000))
        assertTrue(ListeningLayer.phoneReports(now, now - 15 * 60_000))
        assertFalse(ListeningLayer.phoneReports(now, now - 17 * 60_000))
        // A report from a phone whose clock runs a little ahead still counts; one from far in the future does not.
        assertTrue(ListeningLayer.phoneReports(now, now + 30_000))
        assertFalse(ListeningLayer.phoneReports(now, now + 10 * 60_000))
    }

    // ------------------------------------------------------------------- the synced setting

    @Test fun theSettingNormalizesAndDefaultsToAuto() {
        assertEquals("ON", normalize(SettingField.HEADPHONE_MODE, " ON "))
        assertNull(normalize(SettingField.HEADPHONE_MODE, "SOMETIMES"))
        assertNull(normalize(SettingField.HEADPHONE_MODE, null))
        assertEquals(HeadphoneModeChoice.AUTO, VoxaraSettings.DEFAULTS.headphoneMode)
        assertEquals("AUTO", VoxaraSettings.DEFAULTS.valueOf(SettingField.HEADPHONE_MODE))
        assertEquals("headphone_mode", SettingField.HEADPHONE_MODE.key)
    }

    @Test fun theSettingRoundTripsThroughTheDocument() {
        val doc = SettingsDoc().with(SettingField.HEADPHONE_MODE, "OFF", 5L)
        val back = SettingsDoc.fromJson(doc.toJson())
        assertEquals(HeadphoneModeChoice.OFF, VoxaraSettings.from(back).headphoneMode)
        // A tampered value is dropped, never guessed.
        val bad = SettingsDoc.fromJson(doc.toJson().replace("OFF", "LOUD"))
        assertNull(bad[SettingField.HEADPHONE_MODE])
        assertEquals(HeadphoneModeChoice.AUTO, VoxaraSettings.from(bad).headphoneMode)
    }

    @Test fun theNewerChangeWinsAndTiesGoToTheWatch() {
        val now = 1_000_000L
        val watch = SettingsDoc().with(SettingField.HEADPHONE_MODE, "ON", now - 60_000)
        val newerPhone = SettingsDoc().with(SettingField.HEADPHONE_MODE, "OFF", now - 1_000)
        assertEquals("OFF", SettingsMerge.watchReceives(watch, newerPhone, now).merged[SettingField.HEADPHONE_MODE]?.value)
        val tiePhone = SettingsDoc().with(SettingField.HEADPHONE_MODE, "OFF", now - 58_000)
        assertEquals("ON", SettingsMerge.watchReceives(watch, tiePhone, now).merged[SettingField.HEADPHONE_MODE]?.value)
        val futurePhone = SettingsDoc().with(SettingField.HEADPHONE_MODE, "OFF", now + 60_000)
        assertTrue(SettingField.HEADPHONE_MODE in SettingsMerge.watchReceives(watch, futurePhone, now).clampedFuture)
    }
}
