package com.example.voxara.core.ai

import com.example.voxara.core.situation.SituationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tono (offline or cloud) is never told a place it was not given: the level goes out as its zone (the ring's word),
 * and only a known situation (a fixed choice or a real Auto detection) says where the wearer is.
 */
class SnapshotPlaceTest {

    private fun snapshot(dba: Double, situation: SituationMode? = null, simulated: Boolean = false) = SnapshotBuilder.build(
        SnapshotInputs(
            dosePercent = 40.0, weeklyFraction = 0.3, headphoneWeeklyFraction = 0.0, dba = dba, simulated = simulated,
            sensitiveListener = false, days = emptyList(), headphoneDays = emptyList(), headphoneCategory = null,
            calibrated = false, situation = situation,
        ),
        "es",
    )

    @Test fun theLevelGoesOutAsItsZoneNeverAPlace() {
        assertEquals("ok", snapshot(55.0).ambientZone)
        assertEquals("moderate", snapshot(72.0).ambientZone)
        assertEquals("loud", snapshot(88.0).ambientZone)
        assertEquals("dangerous", snapshot(101.0).ambientZone)
        // No current level, no zone (a bench value is never quoted as current either).
        assertNull(snapshot(0.0).ambientZone)
        assertNull(snapshot(88.0, simulated = true).ambientZone)
        val wire = snapshot(88.0).toWire()
        assertFalse(wire.containsKey("ambient_category"))
        assertEquals(AggregateSnapshot.WIRE_KEYS, wire.keys)
    }

    @Test fun onlyAKnownSituationSaysWhere() {
        assertNull(snapshot(88.0, situation = null).toWire()["situation_mode"])
        assertEquals("street", snapshot(88.0, situation = SituationMode.STREET).toWire()["situation_mode"])
    }

    @Test fun theCloudPromptForbidsGuessingAPlace() {
        for (lang in listOf("en", "es")) {
            val system = CoachPrompt.system(lang)
            assertTrue(system.contains("Never name or guess where the wearer is"))
            assertTrue(system.contains("not a place"))
        }
    }
}
