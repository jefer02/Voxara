package com.example.voxara.core.headphones

import com.example.voxara.sync.PhoneListeningState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffectiveListeningTest {

    private val now = 1_750_000_000_000L
    private val none = WatchListening(false, false, null, false, null)

    private fun phone(playing: Boolean = true, index: Int = 8, atMs: Long = now, connected: Boolean = true, speaker: Boolean = false) =
        PhoneListeningState(atMs, 1, connected, playing, OutputKind.BLUETOOTH_A2DP, speaker, -21.0, index, 15)

    @Test
    fun `nothing connected anywhere is nothing`() {
        assertEquals(EffectiveListening.NONE, effectiveListening(none, null, now))
        assertEquals(EffectiveListening.NONE, effectiveListening(none, phone(connected = false), now))
    }

    @Test
    fun `the watch alone, playing or just connected`() {
        val playing = effectiveListening(WatchListening(true, true, 72.0, false, HeadphoneCategory.WIRED), null, now)
        assertEquals(LayerSource.WATCH, playing.source); assertEquals(72.0, playing.dba!!, 0.0)
        val idle = effectiveListening(WatchListening(true, false, null, false, HeadphoneCategory.WIRED), null, now)
        assertEquals(LayerSource.WATCH, idle.source); assertNull(idle.dba)
    }

    @Test
    fun `the phone alone, only while its state is fresh`() {
        val live = effectiveListening(none, phone(), now)
        assertEquals(LayerSource.PHONE, live.source); assertEquals(79.0, live.dba!!, 1e-9)
        // Older than three minutes it is only "last known": it never counts as headphones right now.
        assertEquals(EffectiveListening.NONE, effectiveListening(none, phone(atMs = now - 4 * 60_000), now))
    }

    @Test
    fun `when both play the louder estimate counts, the cautious choice`() {
        val watchLouder = effectiveListening(WatchListening(true, true, 90.0, false, HeadphoneCategory.WIRED), phone(), now)
        assertEquals(LayerSource.WATCH, watchLouder.source)
        val phoneLouder = effectiveListening(WatchListening(true, true, 60.0, false, HeadphoneCategory.WIRED), phone(), now)
        assertEquals(LayerSource.PHONE, phoneLouder.source)
        assertEquals(79.0, phoneLouder.dba!!, 1e-9)
    }

    @Test
    fun `a possible speaker travels with its source`() {
        assertEquals(true, effectiveListening(none, phone(speaker = true), now).possibleSpeaker)
        assertEquals(true, effectiveListening(WatchListening(true, true, 70.0, true, HeadphoneCategory.UNKNOWN), null, now).possibleSpeaker)
    }
}
