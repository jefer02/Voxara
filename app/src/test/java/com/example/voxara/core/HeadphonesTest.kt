package com.example.voxara.core

import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_ADULT
import com.example.voxara.core.headphones.Attribution
import com.example.voxara.core.headphones.BtClassHint
import com.example.voxara.core.headphones.Confidence
import com.example.voxara.core.headphones.Debouncer
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.HeadphoneProfile
import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.core.headphones.attribute
import com.example.voxara.core.headphones.categoryOf
import com.example.voxara.core.headphones.estimateListening
import com.example.voxara.core.headphones.weeklyTimeLeftHours
import com.example.voxara.core.ledger.energyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Headphone safety: category, estimate, the double-count rule, time left, debouncing. */
class HeadphonesTest {

    @Test
    fun `only headphone outputs count, by category`() {
        assertEquals(HeadphoneCategory.WIRED, categoryOf(OutputKind.WIRED_HEADPHONES, BtClassHint.UNKNOWN, null))
        assertEquals(HeadphoneCategory.WIRED, categoryOf(OutputKind.USB_HEADSET, BtClassHint.UNKNOWN, null))
        assertEquals(HeadphoneCategory.UNKNOWN, categoryOf(OutputKind.BLUETOOTH_A2DP, BtClassHint.UNKNOWN, null))
        assertEquals(
            "the wearer's own setting decides earbuds vs over-ear",
            HeadphoneCategory.EARBUDS,
            categoryOf(OutputKind.BLE_HEADSET, BtClassHint.HEADPHONES_OR_HEADSET, HeadphoneCategory.EARBUDS),
        )
        assertNull("a speaker or car kit is not headphones", categoryOf(OutputKind.BLUETOOTH_A2DP, BtClassHint.SPEAKER_OR_CAR, null))
        assertNull(categoryOf(OutputKind.OTHER, BtClassHint.UNKNOWN, HeadphoneCategory.EARBUDS))
    }

    @Test
    fun `the estimate follows the platform volume curve and says how rough it is`() {
        val full = estimateListening(0.0, 15, 15)!!
        assertEquals(HeadphoneProfile.MAX_OUTPUT_DBA, full.dba, 1e-9)
        assertEquals(Confidence.LOW, full.confidence)
        assertEquals(78.0, estimateListening(-22.0, 8, 15)!!.dba, 1e-9)
        assertEquals(68.0, estimateListening(-22.0, 8, 15)!!.lowDba, 1e-9)
        assertNull("muted", estimateListening(-96.0, 0, 15))
    }

    @Test
    fun `without a volume curve the fallback is flagged very low confidence`() {
        val e = estimateListening(null, 15, 30)!!
        assertEquals(Confidence.VERY_LOW, e.confidence)
        assertEquals(70.0, e.dba, 1e-9)
        assertEquals(Confidence.VERY_LOW, estimateListening(Double.NaN, 15, 30)!!.confidence)
    }

    @Test
    fun `a listening span belongs to exactly one ledger`() {
        // Quiet room, music on: headphones.
        assertEquals(Attribution.HEADPHONE, attribute(75.0, 55.0, HeadphoneCategory.EARBUDS))
        // Very loud room, quiet music on open/unknown headphones: the room dominates.
        assertEquals(Attribution.AMBIENT, attribute(70.0, 95.0, HeadphoneCategory.UNKNOWN))
        // Over-ear passive attenuation (15 dB) makes 95 dB rooms ~80 at the ear: music at 82 wins.
        assertEquals(Attribution.HEADPHONE, attribute(82.0, 95.0, HeadphoneCategory.OVER_EAR))
        // No listening: always ambient. No ambient measurement: headphones.
        assertEquals(Attribution.AMBIENT, attribute(null, 95.0, HeadphoneCategory.EARBUDS))
        assertEquals(Attribution.HEADPHONE, attribute(80.0, null, HeadphoneCategory.EARBUDS))
    }

    @Test
    fun `weekly time left at the current volume`() {
        // 80 dBA spends the adult week in 40 h.
        assertEquals(40.0, weeklyTimeLeftHours(0.0, WHO_WEEKLY_PA2H_ADULT, 80.0)!!, 1e-6)
        // Half the week used at 83 dBA (twice the energy rate): 10 h left.
        assertEquals(10.0, weeklyTimeLeftHours(0.8, WHO_WEEKLY_PA2H_ADULT, 83.0103)!!, 0.01)
        // 100 dBA from nothing: about 24 minutes.
        assertEquals(0.4, weeklyTimeLeftHours(0.0, WHO_WEEKLY_PA2H_ADULT, 100.0)!!, 1e-6)
        assertEquals(0.0, weeklyTimeLeftHours(2.0, WHO_WEEKLY_PA2H_ADULT, 80.0)!!, 0.0)
        assertNull(weeklyTimeLeftHours(0.0, WHO_WEEKLY_PA2H_ADULT, null))
        // Consistent with the ledger's energy: 24 min at 100 dBA is the whole adult week.
        assertEquals(WHO_WEEKLY_PA2H_ADULT, energyOf(100.0, 0.4 * 3600) / 3600, 1e-6)
    }

    @Test
    fun `a flapping connection only counts once it holds`() {
        val d = Debouncer<HeadphoneCategory?>(null, holdMs = 3_000L)
        assertNull(d.offer(HeadphoneCategory.EARBUDS, 0L))
        assertNull(d.offer(null, 1_000L))                       // dropped again: nothing changes
        assertNull(d.offer(HeadphoneCategory.EARBUDS, 2_000L))
        assertNull(d.offer(HeadphoneCategory.EARBUDS, 4_000L))
        assertEquals(HeadphoneCategory.EARBUDS, d.offer(HeadphoneCategory.EARBUDS, 5_000L))
        assertEquals(HeadphoneCategory.EARBUDS, d.offer(null, 5_500L))
        assertNull(d.offer(null, 8_600L))
    }

    @Test
    fun `categories round-trip through their stored code`() {
        HeadphoneCategory.entries.forEach { assertEquals(it, HeadphoneCategory.of(it.code)) }
        assertTrue(HeadphoneCategory.of(99) == HeadphoneCategory.UNKNOWN)
    }
}
