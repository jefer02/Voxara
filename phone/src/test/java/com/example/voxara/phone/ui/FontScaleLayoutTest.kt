package com.example.voxara.phone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.example.voxara.link.LinkState
import com.example.voxara.phone.ui.PhoneFixtures.calmState
import com.example.voxara.phone.ui.PhoneFixtures.liveOver
import com.example.voxara.phone.ui.PhoneFixtures.now
import com.example.voxara.phone.ui.PhoneFixtures.state
import com.example.voxara.phone.ui.PhoneFixtures.watch
import com.example.voxara.phone.ui.screens.AlertsScreen
import com.example.voxara.phone.ui.screens.HeadphonesScreen
import com.example.voxara.phone.ui.screens.HomeScreen
import com.example.voxara.phone.ui.screens.HowItWorksScreen
import com.example.voxara.phone.ui.screens.SettingsScreen
import com.example.voxara.phone.ui.screens.SetupScreen
import com.example.voxara.phone.ui.screens.TodayScreen
import com.example.voxara.phone.ui.screens.TonoScreen
import com.example.voxara.phone.ui.screens.TrendRange
import com.example.voxara.phone.ui.screens.TrendsScreen
import com.example.voxara.phone.ui.screens.WatchScreen
import com.example.voxara.phone.ui.theme.VoxaraPhoneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * LARGE TEXT. Every screen at font scale 1.3 and 2.0 on a 360 dp phone: no word is ever broken across two lines, no text
 * is cut off (ellipsis or clipped lines), and no two pieces of text sit on top of each other. A failure names the screen,
 * the scale and the text, so the render of that screen shows what to fix.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class FontScaleLayoutTest {

    @get:Rule val rule = createComposeRule()

    private val actions = PhoneActions()

    private data class Probe(val text: String, val bounds: Rect, val layout: TextLayoutResult?)

    private fun SemanticsNode.collect(out: MutableList<Probe>) {
        val texts = config.getOrNull(SemanticsProperties.Text)
        val layouts = ArrayList<TextLayoutResult>()
        config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        if (texts != null) out += Probe(texts.joinToString(" ") { it.text }, boundsInRoot, layouts.firstOrNull())
        children.forEach { it.collect(out) }
    }

    /** True when a line of [layout] ends inside a word: the character before and the one after the break are both letters or digits. */
    private fun brokenWord(layout: TextLayoutResult): String? {
        val t = layout.layoutInput.text.text
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line, visibleEnd = false)
            if (end in 1 until t.length && t[end - 1].isLetterOrDigit() && t[end].isLetterOrDigit()) return t.substring(maxOf(0, end - 6), minOf(t.length, end + 6))
        }
        return null
    }

    private fun check(name: String, scale: Float): List<String> {
        rule.waitForIdle()
        val probes = ArrayList<Probe>()
        rule.onRoot(useUnmergedTree = true).fetchSemanticsNode().collect(probes)
        val problems = ArrayList<String>()
        for (p in probes) {
            val l = p.layout ?: continue
            brokenWord(l)?.let { problems += "$name x$scale: word broken across lines near \"$it\" in \"${p.text.take(60)}\"" }
            if (l.didOverflowHeight || (0 until l.lineCount).any { l.isLineEllipsized(it) }) problems += "$name x$scale: text cut off: \"${p.text.take(60)}\""
        }
        // two texts drawn on top of each other (more than a few pixels in both directions)
        val visible = probes.filter { it.text.isNotBlank() && it.bounds.width > 0f && it.bounds.height > 0f }
        for (i in visible.indices) for (j in i + 1 until visible.size) {
            val a = visible[i]; val b = visible[j]
            val w = minOf(a.bounds.right, b.bounds.right) - maxOf(a.bounds.left, b.bounds.left)
            val h = minOf(a.bounds.bottom, b.bounds.bottom) - maxOf(a.bounds.top, b.bounds.top)
            if (w > 6f && h > 6f) problems += "$name x$scale: \"${a.text.take(40)}\" overlaps \"${b.text.take(40)}\""
        }
        return problems
    }

    private val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
        "home" to { HomeScreen(state, actions) {} },
        "home calm" to { HomeScreen(calmState, actions) {} },
        "home headphones alert" to { HomeScreen(state.copy(phoneSampling = true, phoneLive = liveOver), actions) {} },
        "home no watch" to { HomeScreen(PhoneUiState(link = LinkState.NoWatch, nowMs = now), actions) {} },
        "today" to { TodayScreen(state, {}, {}) },
        "alerts" to { AlertsScreen(state, {}, {}) },
        "trends day" to { TrendsScreen(state, {}, initialRange = TrendRange.DAY) },
        "trends week" to { TrendsScreen(state, {}, initialRange = TrendRange.WEEK) },
        "trends month" to { TrendsScreen(state, {}, initialRange = TrendRange.MONTH) },
        "tono" to { TonoScreen(state, actions) },
        "watch" to { WatchScreen(state, actions) },
        "headphones" to { HeadphonesScreen(state.copy(phoneSampling = true, phoneLive = liveOver), actions, {}) },
        "settings" to { SettingsScreen(state, actions) {} },
        "how" to { HowItWorksScreen {} },
        "setup 0" to { SetupScreen(state.copy(setupDone = false), actions, initialStep = 0) },
        "setup 2" to { SetupScreen(state.copy(link = LinkState.NotInstalled(listOf(watch)), setupDone = false), actions, initialStep = 2) },
    )

    private fun run(scale: Float) {
        RuntimeEnvironment.setQualifiers("w360dp-h6000dp-notnight-xxhdpi")
        RuntimeEnvironment.setFontScale(scale)
        var index by mutableIntStateOf(0)
        // one composition, the screens swapped in turn (a test may set its content once)
        rule.setContent { VoxaraPhoneTheme(darkTheme = false, reduceMotion = true) { key(index) { screens[index].second() } } }
        val all = ArrayList<String>()
        for (i in screens.indices) {
            index = i
            all += check(screens[i].first, scale)
        }
        assertTrue("Large text problems at x$scale:" + System.lineSeparator() + all.joinToString(System.lineSeparator()), all.isEmpty())
    }

    @Test fun `no broken words, cut text or overlaps at font scale 1_3`() = run(1.3f)

    @Test fun `no broken words, cut text or overlaps at font scale 2_0`() = run(2.0f)
}
