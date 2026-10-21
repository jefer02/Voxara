package com.example.voxara.core.headphones

import com.example.voxara.core.alerts.Alert
import com.example.voxara.core.alerts.AlertEngine
import com.example.voxara.core.alerts.AlertInputs
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertMemory
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteAccumulator
import com.example.voxara.core.ledger.MinuteRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THE GUARANTEE: headphones mode can only ADD protection. With the layer on, off, manual or by
 * detection, the room is still measured and written to the ledger, its alerts fire exactly as before,
 * and the thresholds are the wearer's own. A manual Off with headphones connected still records the
 * listening estimate. The double-count rule between the two ledgers is unchanged.
 */
class ListeningLayerSafetyTest {

    private val layers = listOf(
        ListeningLayerState.OFF,
        ListeningLayerState(active = true, source = LayerSource.WATCH, detected = true, detectedBy = LayerSource.WATCH),
        ListeningLayerState(active = true, source = LayerSource.PHONE, detected = true, detectedBy = LayerSource.PHONE),
        ListeningLayerState(active = true, source = LayerSource.MANUAL),
        ListeningLayerState(active = false, source = LayerSource.MANUAL, detected = true, detectedBy = LayerSource.WATCH),
    )

    // ------------------------------------------------------------------- alerts

    private val dayStart = 1_750_000_000_000L

    /** A loud day, one step every 5 minutes: the room swings from 70 to 95 dBA and the dose climbs past the limits. */
    private val day: List<AlertInputs> = (0 until 18 * 12).map { i ->
        AlertInputs(
            nowMs = dayStart + i * 5 * 60_000L,
            localDay = 1L,
            localMinuteOfDay = (6 * 60 + i * 5) % 1440,
            sustainedAmbientDba = when ((i / 6) % 4) { 0 -> 70.0; 1 -> 88.0 + (i % 3); 2 -> 95.0; else -> 82.0 },
            dailyDosePercent = i * 1.6,
            weeklyAmbientPercent = 40.0 + i * 0.5,
            weeklyHeadphonePercent = 20.0 + i * 0.6,
            sustainedHeadphoneDba = if ((i / 10) % 3 == 1) 101.0 else 85.0,
        )
    }

    private fun run(layer: ListeningLayerState, base: AlertSettings): List<List<Alert>> {
        var memory = AlertMemory()
        return day.map { inputs ->
            val (alerts, next) = AlertEngine.evaluate(inputs, ListeningLayerPolicy.alertSettings(layer, base), memory)
            memory = next
            alerts
        }
    }

    @Test fun theLayerNeverChangesTheAlertSettingsTheEngineRunsWith() {
        val variants = listOf(
            AlertSettings(),
            AlertSettings(thresholdDba = 80, dailyCap = 1),
            AlertSettings(thresholdDba = 95, headphoneLoudDba = 95, quietHoursEnabled = true),
        )
        for (base in variants) for (layer in layers) assertEquals(base, ListeningLayerPolicy.alertSettings(layer, base))
    }

    @Test fun environmentalAlertsFireExactlyAsBeforeWhileTheLayerIsOn() {
        for (base in listOf(AlertSettings(), AlertSettings(dailyCap = 1), AlertSettings(thresholdDba = 80))) {
            val without = run(ListeningLayerState.OFF, base)
            for (layer in layers) assertEquals("$layer", without, run(layer, base))
        }
    }

    @Test fun aLoudRoomAlertsWithTheLayerOn() {
        val on = layers[1]
        val fired = run(on, AlertSettings()).flatten().map { it.kind }
        assertTrue("sustained room level", AlertKind.AMBIENT_LOUD in fired)
        assertTrue("daily limit", AlertKind.DAILY_100 in fired)
        assertTrue("weekly room limit", AlertKind.WEEK_100 in fired)
    }

    // ------------------------------------------------------------------- the ledgers

    /** What one service burst does, as [com.example.voxara.service.DosimeterService] runs it. */
    private class Ledgers {
        val ambient = MinuteAccumulator(ExposureKind.AMBIENT)
        val listening = MinuteAccumulator(ExposureKind.HEADPHONE)
        val ambientMinutes = mutableListOf<MinuteRecord>()
        val listeningMinutes = mutableListOf<MinuteRecord>()
        var sustainedSeconds = 0.0

        fun burst(layer: ListeningLayerState, hpDba: Double?, roomDba: Double, nowMs: Long) {
            val plan = ListeningLayerPolicy.planBurst(layer, hpDba, roomDba, HeadphoneCategory.EARBUDS)
            assertTrue(plan.evaluateAlerts)
            if (plan.foldAmbient) ambientMinutes += ambient.add(roomDba, 10.0, nowMs)
            if (plan.feedSustained) sustainedSeconds += 10.0
            if (plan.foldHeadphone) listeningMinutes += listening.add(hpDba!!, 10.0, nowMs)
        }

        fun finish() { ambient.flush()?.let { ambientMinutes += it }; listening.flush()?.let { listeningMinutes += it } }
    }

    @Test fun environmentalMinutesKeepBeingWrittenWhileTheLayerIsOn() {
        // Headphones connected but not playing (the common idle case) and a 78 dBA room, ten minutes.
        val results = layers.map { layer ->
            Ledgers().also { l ->
                for (i in 0 until 60) l.burst(layer, hpDba = null, roomDba = 78.0, nowMs = i * 10_000L)
                l.finish()
            }
        }
        val baseline = results.first()
        assertTrue("the room is written", baseline.ambientMinutes.isNotEmpty())
        for (r in results) {
            assertEquals(baseline.ambientMinutes, r.ambientMinutes)
            assertEquals(baseline.sustainedSeconds, r.sustainedSeconds, 0.0)
            assertTrue(r.listeningMinutes.isEmpty())
        }
    }

    @Test fun aLoudRoomIsStillWrittenWhenQuietHeadphonesPlay() {
        // 95 dBA around, headphones at 70: the room is the louder source at the ear, so it owns the span.
        val on = Ledgers().also { l ->
            for (i in 0 until 60) l.burst(layers[1], hpDba = 70.0, roomDba = 95.0, nowMs = i * 10_000L)
            l.finish()
        }
        assertTrue(on.ambientMinutes.isNotEmpty())
        assertTrue(on.listeningMinutes.isEmpty())
    }

    @Test fun theDoubleCountRuleIsUnchangedAndNeverCountsASpanTwice() {
        // Loud headphones in a quieter room: the headphones own the span, the room's dose does not also grow.
        for (layer in layers) {
            val plan = ListeningLayerPolicy.planBurst(layer, 92.0, 80.0, HeadphoneCategory.EARBUDS)
            assertTrue(plan.foldHeadphone); assertFalse(plan.foldAmbient)
            // ...but the room still reaches the alert window and the alerts are still evaluated.
            assertTrue(plan.feedSustained); assertTrue(plan.evaluateAlerts)
            val room = ListeningLayerPolicy.planBurst(layer, 60.0, 95.0, HeadphoneCategory.EARBUDS)
            assertTrue(room.foldAmbient); assertFalse(room.foldHeadphone)
        }
        // And the plan agrees with the rule itself, case by case.
        for (hp in listOf(null, 55.0, 80.0, 100.0)) for (room in listOf(50.0, 85.0, 105.0)) {
            val owner = attribute(hp, room, HeadphoneCategory.OVER_EAR)
            val plan = ListeningLayerPolicy.planBurst(layers[1], hp, room, HeadphoneCategory.OVER_EAR)
            assertEquals(owner == Attribution.AMBIENT, plan.foldAmbient)
            assertEquals(owner == Attribution.HEADPHONE, plan.foldHeadphone)
        }
    }

    @Test fun manualOffWithHeadphonesConnectedStillRecordsTheListeningEstimate() {
        val manualOff = layers.last()
        assertFalse(manualOff.active)
        assertTrue(manualOff.detected)
        val l = Ledgers()
        for (i in 0 until 60) l.burst(manualOff, hpDba = 88.0, roomDba = 60.0, nowMs = i * 10_000L)
        l.finish()
        assertTrue("listening is recorded", l.listeningMinutes.isNotEmpty())
        // Same minutes as with the layer on: the layer does not decide what is recorded.
        val on = Ledgers()
        for (i in 0 until 60) on.burst(layers[1], hpDba = 88.0, roomDba = 60.0, nowMs = i * 10_000L)
        on.finish()
        assertEquals(on.listeningMinutes, l.listeningMinutes)
    }
}
