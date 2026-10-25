package com.example.voxara.sync

import com.example.voxara.core.headphones.OutputKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadphoneWireTest {

    private val t0 = 1_750_000_000_000L

    private fun state(playing: Boolean = true, db: Double? = -21.0, index: Int = 8, max: Int = 15) = PhoneListeningState(
        atMs = t0, sequence = 7, connected = true, playing = playing, output = OutputKind.BLUETOOTH_A2DP,
        possibleSpeaker = true, volumeDb = db, volumeIndex = index, volumeMax = max,
    )

    @Test
    fun `the phone's state round-trips, and carries only numbers and a type`() {
        val s = state()
        assertEquals(s, PhoneListeningState.decode(s.encode()))
        val json = s.encode().toString(Charsets.UTF_8)
        for (forbidden in listOf("name", "address", "title", "app")) assertFalse(json, json.contains(forbidden, ignoreCase = true))
        assertEquals(s.copy(volumeDb = null), PhoneListeningState.decode(s.copy(volumeDb = null).encode()))
        assertNull(PhoneListeningState.decode("not json".toByteArray()))
    }

    @Test
    fun `the estimate exists only while connected and playing`() {
        assertEquals(79.0, state().estimate!!.dba, 1e-9)
        assertNull(state(playing = false).estimate)
        assertNull(PhoneListeningState.none(t0, 8).estimate)
        assertNull(state(index = 0).estimate)                         // muted
    }

    @Test
    fun `a state is fresh for three minutes, after that it is only the last known`() {
        val s = state()
        assertTrue(s.isFresh(t0 + 60_000))
        assertFalse(s.isFresh(t0 + HeadphoneWire.FRESH_MS + 1))
    }

    @Test
    fun `a state from a phone whose clock is ahead of the watch's is still fresh, a far-future one is not`() {
        val s = state()
        assertTrue(s.isFresh(t0 - 2 * 60_000))                                       // watch 2 min behind the phone
        assertFalse(s.isFresh(t0 - HeadphoneWire.CLOCK_TOLERANCE_MS - 1))
    }

    @Test
    fun `when the arrival time is known, freshness is the receiver's own clock, so a phone clock that is ahead cannot stretch it`() {
        val ahead = state().copy(atMs = t0 + 4 * 60_000)                              // stamped 4 minutes in the watch's future
        // By the phone's stamp it would stay "live" for about 7 real minutes ...
        assertTrue(ahead.isFresh(t0 + 5 * 60_000))
        // ... but received at t0, it is "last known" after three, as for any state.
        val received = ahead.copy(receivedAtMs = t0)
        assertTrue(received.isFresh(t0 + 60_000))
        assertFalse(received.isFresh(t0 + HeadphoneWire.FRESH_MS + 1))
        assertEquals("arrival time is not on the wire", state().encode().toList(), state().copy(receivedAtMs = 5).encode().toList())
    }

    @Test
    fun `the lower-volume command and its answer round-trip with the same id`() {
        val req = LowerVolumeRequest("abc", t0, 80.0)
        assertEquals(req, LowerVolumeRequest.decode(req.encode()))
        val res = LowerVolumeResult(req.id, LowerOutcome.LOWERED, 8, 79.0)
        assertEquals(res, LowerVolumeResult.decode(res.encode()))
        val failed = LowerVolumeResult(req.id, LowerOutcome.FIXED_VOLUME, 12, null)
        assertEquals(failed, LowerVolumeResult.decode(failed.encode()))
        assertNotNull(LowerOutcome.valueOf("UNREACHABLE"))
        assertNull(LowerVolumeResult.decode(byteArrayOf(1, 2, 3)))
    }
}
