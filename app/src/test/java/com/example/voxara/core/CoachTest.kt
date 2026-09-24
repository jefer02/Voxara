package com.example.voxara.core

import com.example.voxara.ai.CoachResult
import com.example.voxara.ai.CoachSnapshots
import com.example.voxara.ai.HttpTransport
import com.example.voxara.ai.OpenAiCompatibleCoachClient
import com.example.voxara.core.ai.AggregateSnapshot
import com.example.voxara.core.ai.CoachBudget
import com.example.voxara.core.ai.CoachRequest
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.ai.DayPart
import com.example.voxara.core.ai.HearingCareScore
import com.example.voxara.core.ai.MiniJson
import com.example.voxara.core.ai.ReplyValidator
import com.example.voxara.core.ai.SymptomGuard
import com.example.voxara.core.ai.Validation
import com.example.voxara.core.ai.cacheKey
import com.example.voxara.core.ai.daysToWeeklyLimit
import com.example.voxara.core.ai.streakDays
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.ledger.DaySummary
import com.example.voxara.data.ExposureState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Tono: privacy gate, deterministic figures, guardrails, validation and the HTTP contract. */
class CoachTest {

    private val snapshot = AggregateSnapshot(
        locale = "en", dailyDosePercent = 42, weeklyAmbientPercent = 35, weeklyHeadphonePercent = 61,
        currentDba = 72, averageDbaToday = 68, loudestHourDbaToday = 88, measuredMinutesToday = 310,
        listeningMinutesToday = 95, loudHoursByDayPart = mapOf(DayPart.EVENING to 2),
        headphoneCategory = "earbuds", ambientCategory = "conversation", calibrated = false,
        hearingCareScore = 93, streakDays = 4, daysToWeeklyLimit = 3,
    )

    private fun reply(status: String, insight: String, suggestion: String, why: String = "", chips: String = "[]") =
        """{"status":"$status","insight":"$insight","why":"$why","suggestion":"$suggestion","chips":$chips}"""

    // ------------------------------------------------------------------ privacy gate

    @Test
    fun `only whitelisted aggregate keys ever leave the watch`() {
        val wire = snapshot.toWire()
        assertEquals(AggregateSnapshot.WIRE_KEYS, wire.keys)
        val json = MiniJson.write(wire)
        listOf("title", "app", "package", "address", "name", "lat", "lon", "audio", "device").forEach {
            assertFalse("no '$it' in $json", json.contains("\"$it"))
        }
    }

    @Test
    fun `the snapshot built from app state is aggregates only`() {
        val state = ExposureState(
            dba = 71.6, dosePercent = 41.7, weeklyFraction = 0.35, headphoneWeeklyFraction = 0.61,
            headphoneCategory = HeadphoneCategory.EARBUDS,
            days = listOf(DaySummary(10L, List(24) { h -> if (h == 19) 85f else if (h == 9) 60f else 0f }, 41.7, 300.0, 0.3)),
            headphoneDays = listOf(DaySummary(10L, List(24) { 0f }, 0.0, 95.0, 0.5)),
        )
        val s = CoachSnapshots.build(state, "es")
        assertEquals("es", s.locale)
        assertEquals(42, s.dailyDosePercent)
        assertEquals(72, s.currentDba)
        assertEquals(85, s.loudestHourDbaToday)
        assertEquals(1, s.loudHoursByDayPart[DayPart.EVENING])
        assertEquals("earbuds", s.headphoneCategory)
        assertEquals(95, s.listeningMinutesToday)
        assertEquals(AggregateSnapshot.WIRE_KEYS, s.toWire().keys)
    }

    // ------------------------------------------------------------------ deterministic figures

    @Test
    fun `hearing care score is a habits score with explainable penalties`() {
        assertEquals(100, HearingCareScore.of(30.0, 45.0, 0))
        assertEquals(70, HearingCareScore.of(100.0, 20.0, 0))           // -30 at 100% weekly
        assertEquals(40, HearingCareScore.of(150.0, 0.0, 0))            // capped at -60
        assertEquals(84, HearingCareScore.of(20.0, 20.0, 2))            // -8 per day over the limit
        assertEquals(0, HearingCareScore.of(200.0, 0.0, 7))
    }

    @Test
    fun `weekly limit projection from the recent daily pace`() {
        assertEquals(3, daysToWeeklyLimit(70.0, listOf(10.0, 10.0, 10.0)))
        assertNull("nothing accruing", daysToWeeklyLimit(20.0, listOf(0.0, 0.1)))
        assertNull("already over", daysToWeeklyLimit(100.0, listOf(20.0)))
        assertNull("too far to be useful", daysToWeeklyLimit(0.0, listOf(1.0)))
    }

    @Test
    fun `streaks are gentle`() {
        assertEquals(3, streakDays(listOf(true, true, true, false, false, true)))
        assertEquals("one slip forgiven", 5, streakDays(listOf(true, true, false, true, true, true)))
        assertEquals("two slips in a week are not", 2, streakDays(listOf(true, true, false, false, true)))
        assertEquals(0, streakDays(listOf(false, true, true)))
    }

    // ------------------------------------------------------------------ guardrails

    @Test
    fun `symptoms are caught in both languages`() {
        assertTrue(SymptomGuard.detect("I have ringing in my ears"))
        assertTrue(SymptomGuard.detect("tengo un zumbido en el oído"))
        assertTrue(SymptomGuard.detect("everything sounds muffled"))
        assertFalse(SymptomGuard.detect("how loud is it?"))
        assertFalse(SymptomGuard.detect(null))
    }

    @Test
    fun `a good reply passes validation`() {
        val v = ReplyValidator.validate(
            reply("Calm evening", "You are at 42% of today's allowance.", "Keep the volume where it is.", chips = """["My week?"]"""),
            snapshot,
        )
        assertTrue(v is Validation.Valid)
        assertEquals(listOf("My week?"), (v as Validation.Valid).reply.chips)
    }

    @Test
    fun `invented numbers, medical claims and oversized text are rejected`() {
        assertTrue(ReplyValidator.validate(reply("Hi", "You are at 57% today.", "Rest."), snapshot) is Validation.Invalid)
        assertTrue(ReplyValidator.validate(reply("Hi", "This may cause permanent damage.", "Rest."), snapshot) is Validation.Invalid)
        assertTrue(ReplyValidator.validate(reply("Hi", "I can diagnose this.", "Rest."), snapshot) is Validation.Invalid)
        assertTrue(ReplyValidator.validate(reply("x".repeat(61), "ok", "ok"), snapshot) is Validation.Invalid)
        assertTrue(ReplyValidator.validate("not json", snapshot) is Validation.Invalid)
        assertTrue(ReplyValidator.validate("", snapshot) is Validation.Invalid)
        // Word boundaries: "secure" is not "cure".
        assertTrue(ReplyValidator.validate(reply("Hi", "Your data stays secure.", "Rest."), snapshot) is Validation.Valid)
    }

    // ------------------------------------------------------------------ budget and cache

    @Test
    fun `automatic insights are hourly and calls are capped per day`() {
        var b = CoachBudget()
        assertTrue(b.canCall(1L, 1_000L, automatic = true))
        b = b.spend(1L, 1_000L, automatic = true)
        assertFalse(b.canCall(1L, 1_000L + 30 * 60_000L, automatic = true))
        assertTrue("asking is not throttled hourly", b.canCall(1L, 2_000L, automatic = false))
        repeat(CoachBudget.MAX_CALLS_PER_DAY) { b = b.spend(1L, 3_000L, automatic = false) }
        assertFalse(b.canCall(1L, 4_000L, automatic = false))
        assertTrue("a new day resets the cap", b.canCall(2L, 5_000L, automatic = false))
    }

    @Test
    fun `the cache key ignores small changes but not the question`() {
        val a = CoachRequest(CoachTask.DAILY_INSIGHT, snapshot)
        val b = CoachRequest(CoachTask.DAILY_INSIGHT, snapshot.copy(dailyDosePercent = 44))
        assertEquals(cacheKey(a, 1L), cacheKey(b, 1L))
        assertFalse(cacheKey(a, 1L) == cacheKey(a.copy(task = CoachTask.ASK, question = "why?"), 1L))
    }

    // ------------------------------------------------------------------ HTTP contract (fake transport)

    private class FakeTransport(private val code: Int, private val body: String, private val fail: Boolean = false) : HttpTransport {
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var sent: String? = null
        override fun postJson(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
            this.url = url; this.headers = headers; this.sent = body
            if (fail) throw IOException("offline")
            return code to this.body
        }
    }

    private fun completion(content: String) =
        MiniJson.write(mapOf("choices" to listOf(mapOf("message" to mapOf("role" to "assistant", "content" to content)))))

    @Test
    fun `the request is OpenAI-compatible JSON mode with only aggregates`() = runBlocking {
        val fake = FakeTransport(200, completion(reply("Calm", "You are at 42% today.", "Keep it up.")))
        val client = OpenAiCompatibleCoachClient("https://example.invalid/", "test-model", "test-key", fake)
        val r = client.reply(CoachRequest(CoachTask.DAILY_INSIGHT, snapshot))
        assertTrue(r is CoachResult.Success)
        assertEquals("https://example.invalid/chat/completions", fake.url)
        assertEquals("Bearer test-key", fake.headers["Authorization"])
        val body = MiniJson.parse(fake.sent!!) as Map<*, *>
        assertEquals("test-model", body["model"])
        assertEquals(mapOf("type" to "json_object"), body["response_format"])
        assertEquals(250.0, body["max_tokens"])
        val messages = body["messages"] as List<*>
        assertTrue(((messages[0] as Map<*, *>)["content"] as String).contains("json"))
        val user = MiniJson.parse((messages[1] as Map<*, *>)["content"] as String) as Map<*, *>
        assertEquals(AggregateSnapshot.WIRE_KEYS, (user["data"] as Map<*, *>).keys)
    }

    @Test
    fun `invalid replies, HTTP errors and no network all fail softly`() = runBlocking {
        val req = CoachRequest(CoachTask.DAILY_INSIGHT, snapshot)
        suspend fun result(t: FakeTransport) = OpenAiCompatibleCoachClient("https://x.invalid", "m", "k", t).reply(req)
        assertTrue(result(FakeTransport(200, completion(reply("Hi", "You are at 57% today.", "Rest.")))) is CoachResult.Failure)
        assertTrue(result(FakeTransport(401, "{}")) is CoachResult.Failure)
        assertTrue(result(FakeTransport(200, "garbage")) is CoachResult.Failure)
        assertTrue(result(FakeTransport(200, "", fail = true)) is CoachResult.Failure)
        assertTrue(OpenAiCompatibleCoachClient("https://x.invalid", "m", "", FakeTransport(200, "")).reply(req) is CoachResult.Failure)
    }

    @Test
    fun `a non-IO transport error fails softly instead of crashing`() = runBlocking {
        val throwing = object : HttpTransport {
            override fun postJson(url: String, headers: Map<String, String>, body: String): Pair<Int, String> =
                throw SecurityException("no INTERNET")
        }
        val r = OpenAiCompatibleCoachClient("https://x.invalid", "m", "k", throwing)
            .reply(CoachRequest(CoachTask.STATUS, snapshot))
        assertTrue(r is CoachResult.Failure)
    }

    @Test
    fun `mini json round-trips what the coach sends`() {
        val v = mapOf("a" to "quote \" and \\ and \n", "n" to 3, "d" to 2.5, "l" to listOf(true, null), "o" to mapOf("x" to 1))
        val back = MiniJson.parse(MiniJson.write(v)) as Map<*, *>
        assertEquals("quote \" and \\ and \n", back["a"])
        assertEquals(3.0, back["n"])
        assertEquals(2.5, back["d"])
        assertEquals(listOf(true, null), back["l"])
        assertEquals(mapOf("x" to 1.0), back["o"])
    }
}
