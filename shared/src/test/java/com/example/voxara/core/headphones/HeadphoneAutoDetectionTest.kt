package com.example.voxara.core.headphones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUTOMATIC DETECTION — which connected output counts as headphones, with and without the Bluetooth
 * permission, and what a speaker or car kit does. The Android layer only fills [RouteCandidate]s; the
 * decisions are here, on the JVM.
 */
class HeadphoneAutoDetectionTest {

    private fun c(kind: OutputKind, hint: BtClassHint = BtClassHint.UNKNOWN, type: Int = 0) = RouteCandidate(kind, hint, type)

    @Test
    fun `wired and usb headsets are headphones, never a possible speaker`() {
        for (k in listOf(OutputKind.WIRED_HEADPHONES, OutputKind.USB_HEADSET)) {
            val r = chooseRoute(listOf(c(k)), null)!!
            assertEquals(HeadphoneCategory.WIRED, r.category)
            assertFalse(r.possibleSpeaker)
        }
    }

    @Test
    fun `a Bluetooth headset class is headphones and not a possible speaker`() {
        val r = chooseRoute(listOf(c(OutputKind.BLUETOOTH_A2DP, BtClassHint.HEADPHONES_OR_HEADSET)), HeadphoneCategory.EARBUDS)!!
        assertEquals(HeadphoneCategory.EARBUDS, r.category)
        assertFalse(r.possibleSpeaker)
    }

    @Test
    fun `a speaker or a car kit is never headphones`() {
        assertNull(chooseRoute(listOf(c(OutputKind.BLUETOOTH_A2DP, BtClassHint.SPEAKER_OR_CAR)), null))
        assertNull(chooseRoute(listOf(c(OutputKind.BLE_HEADSET, BtClassHint.SPEAKER_OR_CAR)), null))
        assertNull(chooseRoute(listOf(c(OutputKind.OTHER)), null))
        assertNull(chooseRoute(emptyList(), null))
    }

    @Test
    fun `BLUETOOTH_CONNECT denied - A2DP is treated as headphones (warn too much, not too little) and shown as a possible speaker`() {
        // Without the permission the class cannot be read: the hint is UNKNOWN.
        val a2dp = chooseRoute(listOf(c(OutputKind.BLUETOOTH_A2DP, BtClassHint.UNKNOWN)), null)!!
        assertEquals(HeadphoneCategory.UNKNOWN, a2dp.category)
        assertTrue(a2dp.possibleSpeaker)
        // A BLE headset says it is a headset by its type: no doubt about it.
        val ble = chooseRoute(listOf(c(OutputKind.BLE_HEADSET, BtClassHint.UNKNOWN)), null)!!
        assertEquals(HeadphoneCategory.UNKNOWN, ble.category)
        assertFalse(ble.possibleSpeaker)
    }

    @Test
    fun `the route is the one media takes - wired first, then BLE, then A2DP`() {
        val route = chooseRoute(
            listOf(
                c(OutputKind.BLUETOOTH_A2DP, BtClassHint.HEADPHONES_OR_HEADSET, type = 8),
                c(OutputKind.BLE_HEADSET, BtClassHint.UNKNOWN, type = 26),
                c(OutputKind.WIRED_HEADPHONES, type = 3),
            ),
            null,
        )!!
        assertEquals(OutputKind.WIRED_HEADPHONES, route.kind)
        assertEquals(3, route.androidType)
        assertEquals(OutputKind.BLE_HEADSET, chooseRoute(listOf(c(OutputKind.BLUETOOTH_A2DP), c(OutputKind.BLE_HEADSET)), null)!!.kind)
    }

    @Test
    fun `a speaker next to real headphones does not hide them`() {
        val r = chooseRoute(
            listOf(c(OutputKind.BLUETOOTH_A2DP, BtClassHint.SPEAKER_OR_CAR), c(OutputKind.WIRED_HEADPHONES)), null,
        )
        assertNotNull(r)
        assertEquals(OutputKind.WIRED_HEADPHONES, r!!.kind)
    }

    @Test
    fun `the tiers escalate at 80, 90 and 100 dBA`() {
        assertEquals(0, headphoneTierOf(80.0))
        assertEquals(0, headphoneTierOf(Double.NaN))
        assertEquals(1, headphoneTierOf(80.1))
        assertEquals(1, headphoneTierOf(89.9))
        assertEquals(2, headphoneTierOf(90.0))
        assertEquals(3, headphoneTierOf(100.0))
    }

    @Test
    fun `80 dBA for 40 hours is exactly the WHO weekly reference`() {
        // The claim in HeadphoneProfile.SAFE_DBA: 4e-10 Pa2 * 10^(80/10) * 40 h = 1.6 Pa2h.
        assertEquals(1.6, weeklyPa2hAt(HeadphoneProfile.SAFE_DBA, 40.0), 1e-9)
    }

    private fun weeklyPa2hAt(dba: Double, hours: Double) = 4.0e-10 * Math.pow(10.0, dba / 10.0) * hours
}
