package com.example.voxara.sync

import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.core.headphones.ORIGIN_PHONE
import com.example.voxara.core.headphones.PhoneListeningSample
import com.example.voxara.core.headphones.PhoneSpanReceiver
import com.example.voxara.core.headphones.applyPhoneSample
import com.example.voxara.core.headphones.mergePhoneMinute
import com.example.voxara.core.headphones.phoneSampleFromJson
import com.example.voxara.core.headphones.sanitizePhoneSample
import com.example.voxara.core.headphones.toJson
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.energyOf
import com.example.voxara.core.ledger.minuteOf
import com.example.voxara.link.SeenKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone's listening spans reaching the watch's ledger over a fake Data Layer: the phone cuts a span at every
 * play, pause and volume change, so a minute arrives in pieces, possibly twice, possibly out of order, possibly
 * hours late. Whatever happens, the ledger must hold exactly what the ears got.
 */
class PhoneSpanSyncTest {

    private val t0 = 1_750_000_000_000L - (1_750_000_000_000L % 60_000L)      // a minute boundary
    private val now = t0 + 6 * 3_600_000L

    private val layer = FakeDataLayer()
    private val ledger = InMemoryMinuteStore()
    private val seen = SeenKeys()
    private val receiver = PhoneSpanReceiver(ledger, seen)
    private var applied = 0

    init {
        // The watch's CompanionService: apply once, then acknowledge by deleting the item.
        layer.onItem { item ->
            val sample = phoneSampleFromJson(item.bytes.toString(Charsets.UTF_8)) ?: return@onItem
            if (receiver.receive(item.path, sample, null, now) != null) applied++
            layer.delete(item.path)
        }
    }

    private fun span(endMs: Long, seconds: Double, volumeDb: Double = -21.0) =
        PhoneListeningSample(endMs, seconds, volumeDb, 8, 15, OutputKind.BLUETOOTH_A2DP, coarse = false)

    /** The phone's WatchBridge.flushSpans: the item path carries the end time, so the same span is the same item. */
    private fun send(s: PhoneListeningSample) = layer.put("/voxara/listening/${s.endMs}", s.toJson().toByteArray())

    private fun headphoneSeconds() = ledger.all(ExposureKind.HEADPHONE).sumOf { it.measuredSeconds }
    private fun headphoneEnergy() = ledger.all(ExposureKind.HEADPHONE).sumOf { it.energyPa2s }

    @Test
    fun `two spans in the same minute add up - the second does not erase the first`() {
        // 10:00:00-10:00:40 at one volume, then the wearer turns it up: 10:00:40-10:01:00 at another.
        send(span(t0 + 40_000, 40.0, volumeDb = -25.0))
        send(span(t0 + 60_000, 20.0, volumeDb = -15.0))
        val minute = ledger.all(ExposureKind.HEADPHONE).single { it.minute == minuteOf(t0) }
        assertEquals(60.0, minute.measuredSeconds, 1e-6)
        assertEquals(energyOf(75.0, 40.0) + energyOf(85.0, 20.0), minute.energyPa2s, 1e-12)
        assertEquals(ORIGIN_PHONE, minute.origin)
    }

    @Test
    fun `many short spans in one minute are all counted`() {
        // Ten volume presses in a minute: ten spans of six seconds.
        for (i in 1..10) send(span(t0 + i * 6_000L, 6.0, volumeDb = -20.0 - i))
        assertEquals(60.0, headphoneSeconds(), 1e-6)
        assertEquals(10, applied)
    }

    @Test
    fun `the same span delivered twice counts once - even after it was consumed and deleted`() {
        val s = span(t0 + 30_000, 30.0)
        assertTrue(send(s))
        assertEquals(1, applied)
        assertEquals("consumed: nothing left in the Data Layer", emptyList<String>(), layer.paths())
        // The phone re-puts it (its outbox flush was retried after a crash): the path is free again, so it IS delivered.
        assertTrue(send(s))
        assertEquals("but it is not counted again", 1, applied)
        assertEquals(30.0, headphoneSeconds(), 1e-6)
    }

    @Test
    fun `a restarted listener that is handed the batch twice counts it once`() {
        layer.disconnect()
        for (i in 1..5) send(span(t0 + i * 10_000L, 10.0, volumeDb = -20.0))
        layer.reconnect(redeliver = 5)
        assertEquals(5, applied)
        assertEquals(50.0, headphoneSeconds(), 1e-6)
    }

    @Test
    fun `order does not matter - reversed and shuffled deliveries give the same ledger`() {
        val spans = (1..30).map { span(t0 + it * 15_000L, 15.0, volumeDb = -30.0 + (it % 7)) }

        fun run(order: FakeDataLayer.Order): List<MinuteRecord> {
            val l = FakeDataLayer()
            val store = InMemoryMinuteStore()
            val rx = PhoneSpanReceiver(store, SeenKeys())
            l.onItem { item -> phoneSampleFromJson(item.bytes.toString(Charsets.UTF_8))?.let { rx.receive(item.path, it, null, now) } }
            l.disconnect()
            spans.forEach { l.put("/voxara/listening/${it.endMs}", it.toJson().toByteArray()) }
            l.reconnect(order, seed = 7)
            return store.all(ExposureKind.HEADPHONE)
        }

        val inOrder = run(FakeDataLayer.Order.AS_SENT)
        for (order in listOf(FakeDataLayer.Order.REVERSED, FakeDataLayer.Order.SHUFFLED)) {
            val other = run(order)
            assertEquals(inOrder.size, other.size)
            inOrder.zip(other).forEach { (a, b) ->
                assertEquals(a.minute, b.minute)
                assertEquals(a.measuredSeconds, b.measuredSeconds, 1e-9)
                assertEquals(a.energyPa2s, b.energyPa2s, a.energyPa2s * 1e-12)
                assertEquals(a.nioshDosePercent, b.nioshDosePercent, 1e-12)
            }
        }
    }

    @Test
    fun `hours offline - everything queued arrives at once and nothing is lost, doubled or put past 60 seconds a minute`() {
        layer.disconnect()
        // Four hours of listening cut into 30-second spans: 480, about what the phone's outbox (500) can hold.
        val spans = (1..480).map { span(t0 + it * 30_000L, 30.0, volumeDb = -22.0) }
        spans.forEach { send(it) }
        assertEquals("nothing reached the watch yet", 0, applied)
        layer.reconnect(FakeDataLayer.Order.SHUFFLED, seed = 3, redeliver = 100)
        assertEquals(480, applied)
        assertEquals(480 * 30.0, headphoneSeconds(), 1e-6)
        assertTrue(ledger.all(ExposureKind.HEADPHONE).all { it.measuredSeconds <= 60.0 + 1e-9 })
        assertEquals(480 * energyOf(78.0, 30.0), headphoneEnergy(), headphoneEnergy() * 1e-9)
    }

    @Test
    fun `a minute the room owns is not also counted for the headphones, and a louder headphone minute takes the room's`() {
        // The watch measured 90 dBA in the room for the whole minute: the earbuds at 79 dBA lose (10 dB passive attenuation).
        ledger.put(MinuteRecord(minuteOf(t0), ExposureKind.AMBIENT, 60.0, energyOf(90.0, 60.0), 5.0))
        send(span(t0 + 60_000, 60.0, volumeDb = -21.0))
        assertTrue(ledger.all(ExposureKind.HEADPHONE).isEmpty())
        assertEquals(1, ledger.all(ExposureKind.AMBIENT).size)
        // A quiet room (60 dBA) and loud headphones: the headphones own the minute, the room's is dropped.
        ledger.put(MinuteRecord(minuteOf(t0) + 5, ExposureKind.AMBIENT, 60.0, energyOf(60.0, 60.0), 0.0))
        send(span(t0 + 5 * 60_000L + 60_000, 60.0, volumeDb = -21.0))
        assertEquals(1, ledger.all(ExposureKind.HEADPHONE).size)
        assertTrue(ledger.all(ExposureKind.AMBIENT).none { it.minute == minuteOf(t0) + 5 })
    }

    @Test
    fun `a span from a phone whose clock is ahead is credited up to now, not to the future`() {
        val s = span(now + 3 * 3_600_000L, 60.0)
        val clean = sanitizePhoneSample(s, now)!!
        assertEquals(now, clean.endMs)
        // A little ahead is just clock difference: kept as it is.
        val slightly = span(now + 30_000, 30.0)
        assertEquals(slightly, sanitizePhoneSample(slightly, now))
        assertTrue(applyPhoneSample(ledger, s, null, now).changed)
        assertTrue(ledger.all(ExposureKind.HEADPHONE).all { it.minute <= minuteOf(now) })
    }

    @Test
    fun `a span older than the ledger keeps, or with nonsense seconds, is dropped`() {
        assertNull(sanitizePhoneSample(span(now - 40L * 86_400_000L, 60.0), now))
        assertNull(sanitizePhoneSample(span(now, Double.NaN), now))
        assertNull(sanitizePhoneSample(span(now, -5.0), now))
        assertFalse(applyPhoneSample(ledger, span(now, Double.NaN), null, now).changed)
        assertTrue(ledger.all(ExposureKind.HEADPHONE).isEmpty())
    }

    @Test
    fun `merging keeps the louder category, the weaker confidence and never more than a minute`() {
        val a = MinuteRecord(100, ExposureKind.HEADPHONE, 45.0, energyOf(70.0, 45.0), 0.1, category = 1, confidence = 1, origin = ORIGIN_PHONE)
        val b = MinuteRecord(100, ExposureKind.HEADPHONE, 30.0, energyOf(88.0, 30.0), 0.4, category = 2, confidence = 2, origin = ORIGIN_PHONE)
        val m = mergePhoneMinute(a, b)
        assertEquals(60.0, m.measuredSeconds, 1e-9)                       // 75 s of spans in one minute: scaled to 60
        assertEquals(2, m.category)                                       // the louder part's
        assertEquals(2, m.confidence)                                     // the weaker confidence (higher code)
        assertEquals((a.energyPa2s + b.energyPa2s) * 60.0 / 75.0, m.energyPa2s, 1e-18)
        assertEquals(b, mergePhoneMinute(null, b))
    }

    @Test
    fun `the seen log survives a restart as text and forgets what is old`() {
        val keys = SeenKeys(maxItems = 3, maxAgeMs = 1_000)
        assertTrue(keys.firstTime("a", 0)); assertTrue(keys.firstTime("b", 10)); assertTrue(keys.firstTime("c", 20))
        assertFalse(keys.firstTime("a", 30))
        val back = SeenKeys.decode(keys.encode(), maxItems = 3, maxAgeMs = 1_000)
        assertFalse(back.firstTime("b", 40))
        // The fourth key pushes the oldest out (bounded) ...
        assertTrue(back.firstTime("d", 50)); assertEquals(3, back.size)
        // ... and anything older than the age limit is forgotten.
        assertTrue(back.firstTime("b", 5_000))
        // Tabs and newlines in a key cannot break the text form.
        val odd = SeenKeys(); odd.firstTime("x\ty\nz", 1)
        assertFalse(SeenKeys.decode(odd.encode()).firstTime("x y z", 2))
    }
}
