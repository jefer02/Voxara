package com.example.voxara.phone.listening

import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.core.headphones.PhoneListeningSample
import com.example.voxara.core.headphones.phoneSampleFromJson
import com.example.voxara.core.headphones.toJson
import com.example.voxara.link.Outbox
import com.example.voxara.phone.PhonePrefs
import com.example.voxara.sync.LowerOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What the phone has not been able to hand to the watch waits, persisted and keyed: sent once, however often it is retried. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WatchBridgeOutboxTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val t0 = 1_750_000_000_000L

    private fun span(end: Long, seconds: Double = 120.0) =
        PhoneListeningSample(end, seconds, -21.0, 8, 15, OutputKind.BLUETOOTH_A2DP, coarse = false)

    @Test
    fun `the same span queued twice is one item, a different one is another`() {
        val before = WatchBridge.pendingSpans(app, t0 + 20_000_000)
        WatchBridge.enqueueSpan(app, span(t0), t0)
        WatchBridge.enqueueSpan(app, span(t0), t0 + 1_000)         // a retry or a re-delivery
        assertEquals(before + 1, WatchBridge.pendingSpans(app, t0 + 20_000_000))
        WatchBridge.enqueueSpan(app, span(t0 + 600_000), t0 + 600_000)
        assertEquals(before + 2, WatchBridge.pendingSpans(app, t0 + 20_000_000))
    }

    @Test
    fun `the queue survives a restart - it is stored as text`() {
        WatchBridge.enqueueSpan(app, span(t0 + 5_000_000), t0 + 5_000_000)
        val stored = Outbox.decode(PhonePrefs.outbox(app), maxItems = 500, maxAgeMs = Long.MAX_VALUE)
        val item = stored.pending().first { it.key == "span/${t0 + 5_000_000}" }
        assertEquals(span(t0 + 5_000_000), phoneSampleFromJson(item.payload))
    }

    @Test
    fun `delete all empties the queue so deleted listening does not come back on the watch`() {
        WatchBridge.enqueueSpan(app, span(t0 + 9_000_000), t0 + 9_000_000)
        WatchBridge.enqueueSpan(app, span(t0 + 9_600_000), t0 + 9_600_000)
        val held = WatchBridge.pendingSpans(app, t0 + 20_000_000)
        assertTrue(held >= 2)
        assertEquals(held, WatchBridge.clearQueued(app))
        assertEquals(0, WatchBridge.pendingSpans(app, t0 + 20_000_000))
        // New listening after the deletion is new data, and queues again.
        WatchBridge.enqueueSpan(app, span(t0 + 10_200_000), t0 + 10_200_000)
        assertEquals(1, WatchBridge.pendingSpans(app, t0 + 20_000_000))
    }

    @Test
    fun `after delete all, the span that was running keeps only its time after the delete and older ones are never queued`() {
        val deletedAt = t0 + 20_000_000
        // What discardPending records before it touches the Data Layer (which is not there under Robolectric).
        PhonePrefs.setWipedAtMs(app, deletedAt)
        WatchBridge.clearQueued(app)

        // A span that ended before the delete (late hand-over) is dropped; one that straddles it keeps its later part.
        WatchBridge.enqueueSpan(app, span(deletedAt - 1_000, 120.0), deletedAt)
        assertEquals(0, WatchBridge.pendingSpans(app, t0 + 20_000_000))
        WatchBridge.enqueueSpan(app, span(deletedAt + 30_000, 120.0), deletedAt + 30_000)
        val queued = Outbox.decode(PhonePrefs.outbox(app), maxItems = 500, maxAgeMs = Long.MAX_VALUE).pending().single()
        assertEquals(30.0, phoneSampleFromJson(queued.payload)!!.seconds, 1e-9)

        // And one wholly after it is untouched.
        WatchBridge.enqueueSpan(app, span(deletedAt + 400_000, 60.0), deletedAt + 400_000)
        assertEquals(2, WatchBridge.pendingSpans(app, t0 + 20_000_000))
    }

    @Test
    fun `a span carries numbers and an output type, nothing else`() {
        val json = span(t0).toJson()
        for (forbidden in listOf("name", "address", "title", "app")) assertFalse(json, json.contains(forbidden, ignoreCase = true))
        assertTrue(json.contains("BLUETOOTH_A2DP"))
    }

    @Test
    fun `every outcome of lowering has words on the phone too`() {
        val res = app.resources
        val texts = LowerOutcome.entries.map { PhoneAlerts.lowerText(res, it, 79.0) }
        assertTrue(texts.all { it.isNotBlank() })
        assertTrue(PhoneAlerts.lowerText(res, LowerOutcome.LOWERED, 79.0).contains("79"))
        assertTrue(PhoneAlerts.lowerText(res, LowerOutcome.FIXED_VOLUME, null).contains("fixed", ignoreCase = true))
    }
}
