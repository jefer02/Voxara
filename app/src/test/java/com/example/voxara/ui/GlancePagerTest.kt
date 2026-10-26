package com.example.voxara.ui

import androidx.wear.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.voxara.core.ledger.DaySummary
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.data.ExposureState
import com.example.voxara.ui.screens.DoseView
import com.example.voxara.ui.screens.GlancePage
import com.example.voxara.ui.screens.GlanceTarget
import com.example.voxara.ui.screens.HomeScreen
import com.example.voxara.ui.theme.VoxaraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The glance pager has three pages (Now, Dose, More); the Dose page flips between Today and Week with the crown
 * or its switch; swiping right on the first page still leaves; links open the right page and view.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w216dp-h216dp-round-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class GlancePagerTest {

    @get:Rule val compose = createComposeRule()

    private val env = UiEnv(
        state = ExposureState(
            dba = 62.0, dosePercent = 12.0, weeklyFraction = 0.2, monitoringStatus = MonitoringStatus.RUNNING, monitoring = true,
            days = (0 until 7).map { i -> DaySummary(20_717L - i, List(24) { 0f }, 10.0, 60.0, 0.05) },
        ),
    )

    private fun show(route: String = Routes.HOME) = compose.setContent {
        VoxaraTheme(reduceMotion = true) { AppScaffold { HomeScreen(env, VoxaraActions(), route) {} } }
    }

    /** The crown goes to whatever has focus inside the page (on a watch the list asks for it itself); here the switch does. */
    private fun turnCrown(by: Float) {
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Today").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.onRoot().performRotaryScrollInput { rotateToScrollVertically(by) }
        compose.waitForIdle()
    }

    private fun page(n: Int, name: String) = hasContentDescription("Page $n of 3, $name", substring = true)

    @Test fun `the pager has three pages`() {
        assertEquals(listOf(GlancePage.NOW, GlancePage.DOSE, GlancePage.MENU), GlancePage.entries)
        show()
        compose.onNode(page(1, "Now")).assertExists()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNode(page(2, "Dose")).assertExists()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Page 3 of 3, More")).assertExists()
        // There is no fourth page: another swipe stays put.
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Page 3 of 3, More")).assertExists()
    }

    @Test fun `the crown flips Today and Week on the Dose page`() {
        show(Routes.TODAY)
        compose.onNodeWithContentDescription("Today").assertIsSelected()
        compose.onNodeWithContentDescription("Week").assertIsNotSelected()
        turnCrown(120f)
        compose.onNodeWithContentDescription("Week").assertIsSelected()
        compose.onNodeWithContentDescription("Today").assertIsNotSelected()
    }

    @Test fun `the crown turned the other way flips back to Today`() {
        show(Routes.WEEK)
        compose.onNodeWithContentDescription("Week").assertIsSelected()
        turnCrown(-120f)
        compose.onNodeWithContentDescription("Today").assertIsSelected()
    }

    @Test fun `tapping the switch flips the view`() {
        show(Routes.TODAY)
        compose.onNodeWithContentDescription("Week").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Week").assertIsSelected()
        compose.onNodeWithContentDescription("Today").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Today").assertIsSelected()
    }

    @Test fun `each view keeps its own spoken description`() {
        show(Routes.TODAY)
        compose.onNode(hasContentDescription("percent of today", substring = true)).assertExists()
        compose.onNodeWithContentDescription("Week").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Last 7 days, oldest first", substring = true)).assertExists()
    }

    @Test fun `the week view shows where the share comes from`() {
        // The breakdown is the first row below the hero.
        compose.setContent {
            VoxaraTheme(reduceMotion = true) {
                AppScaffold {
                    androidx.compose.runtime.CompositionLocalProvider(com.example.voxara.ui.design.LocalInitialItem provides 1) {
                        HomeScreen(env, VoxaraActions(), Routes.WEEK) {}
                    }
                }
            }
        }
        compose.onNodeWithText("Around you 20% · Headphones 0%").assertExists()
    }

    @Test fun `links land on the right page and view`() {
        assertEquals(GlanceTarget(GlancePage.NOW), GlanceTarget.forRoute(Routes.HOME))
        assertEquals(GlanceTarget(GlancePage.NOW), GlanceTarget.forRoute(Routes.NOW))
        assertEquals(GlanceTarget(GlancePage.DOSE, DoseView.TODAY), GlanceTarget.forRoute(Routes.TODAY))
        assertEquals(GlanceTarget(GlancePage.DOSE, DoseView.WEEK), GlanceTarget.forRoute(Routes.WEEK))
        // The Headphones page is gone; its weekly share lives on the week.
        assertEquals(GlanceTarget(GlancePage.DOSE, DoseView.WEEK), GlanceTarget.forRoute(Routes.HEADPHONES))
        assertEquals(GlanceTarget(GlancePage.MENU), GlanceTarget.forRoute(Routes.MENU))
        assertEquals(GlanceTarget(GlancePage.NOW), GlanceTarget.forRoute("something else"))
    }

    @Test fun `a link to the week opens the Dose page on Week`() {
        show(Routes.WEEK)
        compose.onNode(page(2, "Dose")).assertExists()
        compose.onNodeWithContentDescription("Week").assertIsSelected()
    }

    @Test fun `a link to headphones opens the Dose page on Week`() {
        show(Routes.HEADPHONES)
        compose.onNode(page(2, "Dose")).assertExists()
        compose.onNodeWithContentDescription("Week").assertIsSelected()
    }

    @Test fun `a link to today opens the Dose page on Today`() {
        show(Routes.TODAY)
        compose.onNode(page(2, "Dose")).assertExists()
        compose.onNodeWithContentDescription("Today").assertIsSelected()
    }

    @Test fun `the old overview route opens Now`() {
        show(Routes.HOME)
        compose.onNode(page(1, "Now")).assertExists()
    }

    @Test fun `swiping right on the first page leaves it for the screen behind`() {
        compose.setContent {
            VoxaraTheme(reduceMotion = true) {
                AppScaffold {
                    val nav = rememberSwipeDismissableNavController()
                    var went by remember { mutableStateOf(false) }
                    SwipeDismissableNavHost(navController = nav, startDestination = "behind") {
                        composable("behind") {
                            Text("BEHIND")
                            LaunchedEffect(Unit) { if (!went) { went = true; nav.navigate("home") } }
                        }
                        composable("home") { HomeScreen(env, VoxaraActions(), Routes.NOW) {} }
                    }
                }
            }
        }
        compose.onNode(page(1, "Now")).assertExists()
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("BEHIND").assertExists()
    }

    @Test fun `page links resolve names from tiles and the phone`() {
        assertEquals(Routes.NOW, PageLinks.routeFor("now"))
        assertEquals(Routes.NOW, PageLinks.routeFor("overview"))
        assertEquals(Routes.TODAY, PageLinks.routeFor("/today"))
        assertEquals(Routes.WEEK, PageLinks.routeFor("week"))
        assertEquals(Routes.HEADPHONES, PageLinks.routeFor("Headphones"))
        assertEquals(Routes.MENU, PageLinks.routeFor("more"))
        assertEquals(null, PageLinks.routeFor("calibration"))
        assertEquals(null, PageLinks.routeFor(null))
    }
}
