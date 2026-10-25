package com.example.voxara.link

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkHealthAndOutboxTest {

    private val t0 = 1_750_000_000_000L

    // ------------------------------------------------------------------ link health

    @Test
    fun `it never guesses before the first observation`() {
        assertEquals(LinkHealth.Unknown, LinkMonitor().health(t0))
    }

    @Test
    fun `connected, then reconnecting for a minute, then disconnected since the loss`() {
        val m = LinkMonitor()
        assertEquals(LinkHealth.Connected(t0), m.observe(t0, true))
        assertEquals(LinkHealth.Connected(t0), m.observe(t0 + 5_000, true))               // unchanged: still since t0
        assertEquals(LinkHealth.Reconnecting(t0 + 10_000), m.observe(t0 + 10_000, false))
        assertEquals(LinkHealth.Reconnecting(t0 + 10_000), m.health(t0 + 60_000))
        assertEquals(LinkHealth.Disconnected(t0 + 10_000), m.health(t0 + 70_000))
        assertEquals(LinkHealth.Disconnected(t0 + 10_000), m.observe(t0 + 3_600_000, false)) // still the same loss
        assertEquals(LinkHealth.Connected(t0 + 3_700_000), m.observe(t0 + 3_700_000, true))
    }

    @Test
    fun `starting disconnected says reconnecting first, not disconnected for ever`() {
        val m = LinkMonitor()
        assertTrue(m.observe(t0, false) is LinkHealth.Reconnecting)
    }

    // ------------------------------------------------------------------ outbox

    @Test
    fun `the same key twice leaves one copy - the latest payload`() {
        val box = Outbox()
        box.enqueue("span/1", "a", t0)
        box.enqueue("span/1", "b", t0 + 1_000)
        assertEquals(1, box.size)
        assertEquals("b", box.pending().single().payload)
        assertEquals(t0, box.pending().single().createdMs)       // it keeps its place in the queue
    }

    @Test
    fun `a failed send stays queued, a sent one leaves, and a resend never duplicates`() = runBlocking {
        val box = Outbox()
        box.enqueue("1", "one", t0); box.enqueue("2", "two", t0 + 1); box.enqueue("3", "three", t0 + 2)
        val delivered = ArrayList<String>()
        // The link is down for "2" only.
        val sent = box.flush(t0 + 10) { if (it.key == "2") false else { delivered += it.payload; true } }
        assertEquals(2, sent)
        assertEquals(listOf("one", "three"), delivered)
        assertEquals(listOf("2"), box.pending().map { it.key })
        assertEquals(1, box.pending().single().attempts)
        // Reconnected: the rest goes out, once.
        assertEquals(1, box.flush(t0 + 20) { delivered += it.payload; true })
        assertEquals(listOf("one", "three", "two"), delivered)
        assertEquals(0, box.flush(t0 + 30) { delivered += it.payload; true })
        assertEquals(3, delivered.size)
    }

    @Test
    fun `an exception counts as a failed send`() = runBlocking {
        val box = Outbox()
        box.enqueue("1", "x", t0)
        assertEquals(0, box.flush(t0) { error("link down") })
        assertEquals(1, box.size)
    }

    @Test
    fun `old items are dropped and the queue is bounded`() = runBlocking {
        val box = Outbox(maxItems = 3, maxAgeMs = 10_000)
        for (i in 1..5) box.enqueue("k$i", "p$i", t0 + i)
        assertEquals(listOf("k3", "k4", "k5"), box.pending().map { it.key })
        assertEquals(0, box.flush(t0 + 60_000) { true })                  // all older than 10 s: nothing worth sending
        assertFalse(box.contains("k5"))
    }

    @Test
    fun `it round-trips through its text form, tabs and newlines included`() {
        val box = Outbox()
        box.enqueue("a\tb", "line1\nline2\\x", t0)
        box.enqueue("c", "{\"v\":1}", t0 + 5)
        val back = Outbox.decode(box.encode())
        assertEquals(box.pending(), back.pending())
        assertEquals(0, Outbox.decode(null).size)
        assertEquals(0, Outbox.decode("garbage\nmore\tgarbage").size)
    }
}
