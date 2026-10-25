package com.example.voxara.phone.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.voxara.core.ai.SnapshotBuilder
import com.example.voxara.core.alerts.AlertEngine
import com.example.voxara.core.alerts.AlertInputs
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertMemory
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.ai.DayPart
import com.example.voxara.core.ledger.localEpochDay
import com.example.voxara.phone.ui.PhoneFixtures.calmState
import com.example.voxara.phone.ui.PhoneFixtures.now
import com.example.voxara.phone.ui.PhoneFixtures.state
import com.example.voxara.phone.ui.components.calendarLead
import com.example.voxara.phone.ui.components.dayText
import com.example.voxara.phone.ui.screens.HomeScreen
import com.example.voxara.phone.ui.screens.TodayScreen
import com.example.voxara.phone.ui.screens.TrendRange
import com.example.voxara.phone.ui.screens.TrendsScreen
import com.example.voxara.phone.ui.theme.VoxaraPhoneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * ONE STATE, ONE SET OF NUMBERS. Home, Today and Trends are rendered from the same state and must show the same
 * values; the week's difference must be the two numbers printed next to it; the fixtures' dates, alerts and wording must
 * agree with their data (and the real logic must not be able to contradict them).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class FiguresConsistencyTest {

    @get:Rule val rule = createComposeRule()

    private fun SemanticsNode.walk(into: MutableList<String>) {
        config.getOrNull(SemanticsProperties.Text)?.forEach { into += it.text }
        config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { into += it }
        children.forEach { it.walk(into) }
    }

    /** Every text and spoken description a screen shows (the merged tree, as TalkBack reads it). */
    private fun spoken(content: @Composable () -> Unit): String {
        RuntimeEnvironment.setQualifiers("w360dp-h2600dp-notnight-xxhdpi")
        rule.setContent { VoxaraPhoneTheme(darkTheme = false, reduceMotion = true) { content() } }
        rule.waitForIdle()
        val out = ArrayList<String>()
        rule.onRoot().fetchSemanticsNode().walk(out)
        return out.joinToString("\n")
    }

    private fun Double.p() = roundToInt()

    @Test
    fun `Home Today and Trends show the same figures from the same state`() {
        val f = state.figures
        val home = spoken { HomeScreen(state, PhoneActions()) {} }
        assertTrue("Home ring: ${f.todayPercent.p()}\n$home", home.contains("${f.todayPercent.p()} percent of today's limit"))
        assertTrue("Home week tile\n$home", home.contains("Last 7 days, surroundings: ${f.surroundingsWeekPercent.p()} percent"))
        assertTrue("Home headphones tile\n$home", home.contains("Headphones, last 7 days: ${f.headphonesWeekPercent.p()} percent"))
    }

    @Test
    fun `Today shows the Home figure`() {
        val f = state.figures
        val today = spoken { TodayScreen(state, {}, {}) }
        assertTrue(today, today.contains("${f.todayPercent.p()} percent of today's limit"))
        // the last point of "where the day went" is the same number
        assertTrue(today, today.contains("filled up to ${f.todayPercent.p()} percent"))
    }

    @Test
    fun `Trends show the same figures, and the difference is the two numbers beside it`() {
        val f = state.figures
        val trends = spoken { TrendsScreen(state, {}, initialRange = TrendRange.WEEK) }
        // the hero: all sound = surroundings + headphones, the same two numbers the Home tiles show
        assertTrue(trends, trends.contains("${f.allSoundWeekPercent.p()} % of the weekly limit"))
        assertTrue(trends, trends.contains("Surroundings ${f.surroundingsWeekPercent.p()}% + headphones ${f.headphonesWeekPercent.p()}%"))
        // today's column is the Home figure
        assertTrue(trends, trends.contains("Today: ${f.todayPercent.p()} percent"))
        // "N points more than the 7 days before (P%)": N is exactly the hero minus P
        val m = Regex("""(\d+) points? (more|less) than the 7 days before \((\d+)%\)""").find(trends)
        assertNotNull(trends, m)
        val n = m!!.groupValues[1].toInt()
        val previous = m.groupValues[3].toInt()
        val signed = if (m.groupValues[2] == "more") n else -n
        assertEquals("the sentence adds up", (f.allSoundWeekPercent.p() - previous).toDouble(), signed.toDouble(), 1.0)
        assertEquals(f.previousWeekPercent.p(), previous)
        // the rolling line ends at the same number
        assertEquals(f.allSoundWeekPercent, f.rolling.last(), 1e-9)
        // all sound is the two budgets added
        assertEquals(f.surroundingsWeekPercent + f.headphonesWeekPercent, f.allSoundWeekPercent, 1e-9)
    }

    @Test
    fun `the watch status of the fixture is the ledger it was derived from`() {
        val h = PhoneFixtures.history
        assertEquals(h.ambient[0].nioshDosePercent, PhoneFixtures.status.dosePercent, 1e-9)
        // the headline takes the status; the day column takes the headline: one number
        assertEquals(state.head.dosePercent, state.figures.days[0].percent, 1e-9)
    }

    // ------------------------------------------------------------------ the fixtures and the real logic

    private fun levelOf(kind: AlertKind): Double? = when (kind) {
        AlertKind.DAILY_50 -> 50.0
        AlertKind.DAILY_80 -> 80.0
        AlertKind.DAILY_100 -> 100.0
        else -> null
    }

    @Test
    fun `no daily alert in the fixtures is above what its day's dose allows`() {
        for ((name, s) in listOf("loud" to state, "calm" to calmState)) {
            val h = s.history
            val todayStart = h.todayStartMinute
            for (a in h.alerts) {
                val level = levelOf(a.kind) ?: continue
                val day = localEpochDay(a.atMs)
                val dose = h.ambient.first { it.dayEpoch == day }.nioshDosePercent
                assertTrue("$name: ${a.kind} at ${a.atMs} but that day reached ${dose}%", dose >= level - 0.5)
                if (a.atMs >= todayStart * 60_000L) {
                    // an alert today cannot be above today's figure, and the dose at that minute had reached its level
                    assertTrue("$name: ${a.kind} today but today is at ${s.head.dosePercent}%", s.head.dosePercent >= level - 0.5)
                    val atMinute = a.atMs / 60_000L
                    val so = h.todayMinutes.filter { it.minute <= atMinute }.sumOf { it.nioshDosePercent }
                    assertTrue("$name: ${a.kind} at minute ${atMinute - todayStart}: dose so far $so", so >= level - 0.5)
                }
            }
        }
        // the calm day has no alert today at all
        assertTrue(calmState.history.alerts.none { it.atMs >= calmState.history.todayStartMinute * 60_000L })
    }

    @Test
    fun `the real alert engine never raises a daily alert below its level`() {
        var d = 0.0
        while (d <= 150.0) {
            val inputs = AlertInputs(
                nowMs = now, localDay = 1L, localMinuteOfDay = 15 * 60, sustainedAmbientDba = null,
                dailyDosePercent = d, weeklyAmbientPercent = 0.0, weeklyHeadphonePercent = 0.0,
            )
            val (alerts, _) = AlertEngine.evaluate(inputs, AlertSettings(), AlertMemory(day = 1L))
            for (a in alerts) levelOf(a.kind)?.let { assertTrue("${a.kind} raised at dose $d", d >= it) }
            d += 0.5
        }
    }

    // ------------------------------------------------------------------ dates

    @Test
    fun `the fixture clock is Tuesday 17 June and the week and the calendar agree with it`() {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply { timeInMillis = now }
        assertEquals(Calendar.TUESDAY, c.get(Calendar.DAY_OF_WEEK))
        assertEquals(17, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JUNE, c.get(Calendar.MONTH))
        val f = state.figures
        val week = f.lastDays(7)
        assertEquals("the week ends on today", localEpochDay(now), week.last().dayEpoch)
        assertEquals(listOf("Wed", "Thu", "Fri", "Sat", "Sun", "Mon", "Tue"), week.map { dayText(it.dayEpoch, "EEE", Locale.US) })
        // the month: every day sits in the column of its own weekday, and 17 June is a Tuesday
        val month = f.lastDays(30)
        for (sundayFirst in listOf(false, true)) {
            val lead = calendarLead(month.first().dayEpoch, sundayFirst)
            month.forEachIndexed { i, d ->
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply { timeInMillis = d.dayEpoch * 86_400_000L + 43_200_000L }
                val dow = cal.get(Calendar.DAY_OF_WEEK) // Sunday = 1
                val column = if (sundayFirst) dow - 1 else (dow + 5) % 7
                assertEquals("day ${d.dayEpoch} (sundayFirst=$sundayFirst)", column, (lead + i) % 7)
            }
        }
        // the month changes inside the 30 days (31 May -> 1 June)
        assertTrue(month.any { dayText(it.dayEpoch, "d", Locale.US) == "1" })
    }

    // ------------------------------------------------------------------ wording

    @Test
    fun `Tono's afternoon follows the loudest hour of the data`() {
        val hourly = state.history.ambient[0].hourly
        val loudest = hourly.withIndex().maxByOrNull { it.value }!!
        assertEquals("the loudest hour of the loud fixture", 14, loudest.index)
        assertEquals(DayPart.AFTERNOON, SnapshotBuilder.partOf(loudest.index))
        assertTrue(state.coach.reply!!.status.contains("afternoon"))
        // nothing is measured after 15:00, so the copy cannot talk about the evening
        assertTrue(hourly.drop(16).all { it == 0f })
    }
}
