package com.example.voxara.audio

import android.os.Looper
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.HeadphoneRoute
import com.example.voxara.core.headphones.OutputKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * THE LATENCY MECHANISM. A volume change that no event announces is still seen within the guard tick
 * ([HeadphoneWatcher.GUARD_MS], 2 s) while headphones play, and when nothing plays there is no tick at all
 * (no polling to find headphones). What the platform itself delivers (device and playback callbacks) is an
 * event, not a timer. The numbers themselves need a real device: see PROGRESS.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeadphoneWatcherLatencyTest {

    private fun snap(playing: Boolean, index: Int, connected: Boolean = true) = HeadphoneWatcher.Snapshot(
        route = if (connected) HeadphoneRoute(HeadphoneCategory.EARBUDS, OutputKind.BLE_HEADSET, 0, false) else null,
        playing = playing && connected, volumeDb = -3.0 * (15 - index), volumeIndex = index, volumeMax = 15,
        fixedVolume = false, btDetailsAvailable = true,
    )

    private fun watcher(read: () -> HeadphoneWatcher.Snapshot, seen: MutableList<HeadphoneWatcher.Snapshot>): HeadphoneWatcher =
        HeadphoneWatcher(RuntimeEnvironment.getApplication()).also {
            it.reader = read
            it.onChanged = { s -> seen += s }
        }

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    @Test
    fun `a volume change nobody announced is seen within the 2 s guard while playing`() {
        var current = snap(playing = true, index = 8)
        val seen = ArrayList<HeadphoneWatcher.Snapshot>()
        val w = watcher({ current }, seen)
        w.start()
        assertEquals(1, seen.size)                                   // the state at start
        current = snap(playing = true, index = 12)                   // the wearer raised it; no broadcast arrived
        advance(HeadphoneWatcher.GUARD_MS + 100)
        assertEquals(2, seen.size)
        assertEquals(12, seen.last().volumeIndex)
        assertTrue("seen within the 3 s target", HeadphoneWatcher.GUARD_MS < 3_000)
        w.stop()
    }

    @Test
    fun `nothing playing means no tick at all`() {
        var reads = 0
        val seen = ArrayList<HeadphoneWatcher.Snapshot>()
        val w = watcher({ reads++; snap(playing = false, index = 8) }, seen)
        w.start()
        val afterStart = reads
        advance(10 * 60_000L)                                         // ten quiet minutes
        assertEquals("no polling while nothing plays", afterStart, reads)
        w.stop()
    }

    @Test
    fun `no headphones means no tick either, and playback starting wakes it`() {
        var playing = false
        var reads = 0
        val seen = ArrayList<HeadphoneWatcher.Snapshot>()
        val w = watcher({ reads++; snap(playing = playing, index = 9) }, seen)
        w.start()
        val afterStart = reads
        advance(60_000L)
        assertEquals(afterStart, reads)
        playing = true
        w.refresh()                                                   // what the playback callback does
        assertEquals(true, seen.last().playing)
        val afterEvent = reads
        advance(HeadphoneWatcher.GUARD_MS * 3 + 100)
        assertTrue("it ticks now that something plays", reads >= afterEvent + 3)
        // And stops ticking once playback stops.
        playing = false
        advance(HeadphoneWatcher.GUARD_MS + 100)
        val atStop = reads
        advance(60_000L)
        assertEquals(atStop, reads)
        w.stop()
    }

    @Test
    fun `an unchanged state publishes nothing, a change publishes once`() {
        var index = 5
        val seen = ArrayList<HeadphoneWatcher.Snapshot>()
        val w = watcher({ snap(playing = true, index = index) }, seen)
        w.start()
        repeat(5) { w.refresh() }
        assertEquals(1, seen.size)
        index = 6
        w.refresh(); w.refresh()
        assertEquals(2, seen.size)
        w.stop()
    }
}
