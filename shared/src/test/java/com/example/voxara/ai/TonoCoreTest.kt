package com.example.voxara.ai

import com.example.voxara.core.ai.AggregateSnapshot
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.core.ai.CoachRequest
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.ai.DayPart
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared Tono decisions, with a fake cloud client: no test ever reaches a real endpoint. */
class TonoCoreTest {

    private val snapshot = AggregateSnapshot(
        locale = "en", dailyDosePercent = 42, weeklyAmbientPercent = 30, weeklyHeadphonePercent = 10,
        currentDba = 70, averageDbaToday = 65, loudestHourDbaToday = 78, measuredMinutesToday = 300,
        listeningMinutesToday = 20, loudHoursByDayPart = DayPart.entries.associateWith { 0 },
        headphoneCategory = null, ambientCategory = "office", calibrated = false, hearingCareScore = 90,
        streakDays = 3, daysToWeeklyLimit = null,
    )

    private fun reply(tag: String) = CoachReply(tag, "insight $tag", "", "suggestion", emptyList())

    private object BuiltIn : BuiltInCoach {
        override fun reply(req: CoachRequest) = CoachReply("built-in", "i", "", "s", emptyList())
        override fun symptomReply() = CoachReply("see a hearing professional", "i", "", "s", emptyList())
    }

    private class FakeClient(var result: CoachResult) : CoachClient {
        val seen = ArrayList<CoachRequest>()
        override suspend fun reply(request: CoachRequest): CoachResult { seen += request; return result }
    }

    private suspend fun TonoCore.askNow(
        task: CoachTask, client: CoachClient?, consent: Boolean = true, automatic: Boolean = false,
        question: String? = null, nowMs: Long = 1_000_000L,
    ) = ask(task, snapshot, question, null, BuiltIn, consent, client, automatic, nowMs, today = 1L)

    @Test
    fun `a symptom question gets the fixed answer and is never sent`() = runBlocking {
        val client = FakeClient(CoachResult.Success(reply("cloud")))
        val ui = TonoCore().askNow(CoachTask.ASK, client, question = "my ears have a ringing sound")
        assertEquals(ReplySource.SAFETY, ui.source)
        assertEquals("see a hearing professional", ui.reply?.status)
        assertTrue(client.seen.isEmpty())
    }

    @Test
    fun `without consent the cloud is never called`() = runBlocking {
        val client = FakeClient(CoachResult.Success(reply("cloud")))
        val ui = TonoCore().askNow(CoachTask.DAILY_INSIGHT, client, consent = false)
        assertEquals(ReplySource.OFFLINE, ui.source)
        assertFalse(ui.cloudFailed)
        assertTrue(client.seen.isEmpty())
    }

    @Test
    fun `a failed cloud reply falls back to the built-in answer and says so`() = runBlocking {
        val failures = ArrayList<String>()
        val core = TonoCore(onCloudFailure = { failures += it })
        val ui = core.askNow(CoachTask.DAILY_INSIGHT, FakeClient(CoachResult.Failure("malformed response")))
        assertEquals(ReplySource.OFFLINE, ui.source)
        assertTrue(ui.cloudFailed)
        assertEquals(listOf("malformed response"), failures)
    }

    @Test
    fun `a rate-limited automatic refresh keeps the last cloud reply`() = runBlocking {
        val core = TonoCore()
        val client = FakeClient(CoachResult.Success(reply("first")))
        assertEquals("first", core.askNow(CoachTask.STATUS, client, automatic = true).reply?.status)
        // Within the hour, with different data (no cache hit): no call, the cloud reply stays.
        client.result = CoachResult.Success(reply("second"))
        val again = core.ask(
            CoachTask.STATUS, snapshot.copy(dailyDosePercent = 90), null, null, BuiltIn, true, client,
            automatic = true, nowMs = 1_060_000L, today = 1L,
        )
        assertEquals("first", again.reply?.status)
        assertEquals(ReplySource.CLOUD_AI, again.source)
        assertEquals(1, client.seen.size)
    }

    @Test
    fun `a cached reply is served without a second call`() = runBlocking {
        val core = TonoCore()
        val client = FakeClient(CoachResult.Success(reply("cloud")))
        core.askNow(CoachTask.DAILY_INSIGHT, client)
        val second = core.askNow(CoachTask.DAILY_INSIGHT, client)
        assertEquals(ReplySource.CLOUD_AI, second.source)
        assertEquals(1, client.seen.size)
    }

    @Test
    fun `the question sent to the cloud is capped at 200 characters`() = runBlocking {
        val client = FakeClient(CoachResult.Success(reply("cloud")))
        TonoCore().askNow(CoachTask.ASK, client, question = "x".repeat(500))
        assertEquals(200, client.seen.single().question?.length)
    }
}
