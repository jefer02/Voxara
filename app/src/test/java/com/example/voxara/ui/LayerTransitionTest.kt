package com.example.voxara.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.voxara.ui.design.LayerProgress
import com.example.voxara.ui.design.rememberLayerProgress
import com.example.voxara.ui.theme.VoxaraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The headphones-mode transition's rules, on a manual clock: it plays only when it may, takes about
 * 900 ms in and 700 ms out, is staged ring -> waves -> figure -> moon, and gives way to an alert.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LayerTransitionTest {

    @get:Rule val rule = createComposeRule()

    private var active by mutableStateOf(false)
    private var alertAt by mutableStateOf(0L)
    private lateinit var progress: LayerProgress

    private fun start(initial: Boolean, ambient: Boolean = false, reduceMotion: Boolean = false) {
        active = initial
        alertAt = 0L
        rule.mainClock.autoAdvance = false
        rule.setContent {
            VoxaraTheme(ambient = ambient, reduceMotion = reduceMotion) {
                progress = rememberLayerProgress(active, alertAt)
            }
        }
        rule.mainClock.advanceTimeBy(50)
    }

    /** State written from the test thread reaches the composition on the next frame, as in the app. */
    private fun advance(ms: Long) {
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(ms)
    }

    private fun atEnd(on: Boolean) {
        val v = if (on) 1f else 0f
        assertEquals(v, progress.ring.value, 0.001f)
        assertEquals(v, progress.waves.value, 0.001f)
        assertEquals(v, progress.figure.value, 0.001f)
        assertEquals(v, progress.moon.value, 0.001f)
        assertEquals(0f, progress.label.value, 0.001f)
        assertEquals(on, progress.swapped)
        assertEquals(on, progress.target)
    }

    @Test fun aScreenComposedWhileOnShowsItAtOnceAndDoesNotReplay() {
        start(initial = true)
        atEnd(true)
    }

    @Test fun enteringIsStagedAndLandsWithinAboutNineHundredMilliseconds() {
        start(initial = false)
        atEnd(false)
        active = true
        advance(100)
        // 100 ms in: the ring has started, the moon and the figure have not.
        assertTrue("ring started", progress.ring.value > 0.05f)
        assertEquals("moon waits", 0f, progress.moon.value, 0.001f)
        assertEquals("figure waits", 0f, progress.figure.value, 0.001f)
        assertFalse("digits have not rolled yet", progress.swapped)
        advance(260)
        // 360 ms: ring far along, waves moving, figure cross-fading, digits rolling, moon leaving, label up.
        assertTrue(progress.ring.value > 0.5f)
        assertTrue(progress.waves.value > 0.2f)
        assertTrue(progress.figure.value > 0.2f)
        assertTrue(progress.swapped)
        assertTrue(progress.moon.value > 0.01f)
        assertTrue(progress.label.value > 0.1f)
        advance(640)
        // 1 s: done.
        atEnd(true)
    }

    @Test fun theRingOvershootsLikeASpringAndSettles() {
        start(initial = false)
        active = true
        var peak = 0f
        repeat(60) { advance(16); peak = maxOf(peak, progress.ring.value) }
        assertTrue("spring overshoot ($peak)", peak > 1.02f)
        advance(500)
        assertEquals(1f, progress.ring.value, 0.001f)
    }

    @Test fun leavingReversesWithinAboutSevenHundredMilliseconds() {
        start(initial = true)
        active = false
        advance(150)
        assertTrue("moon heading home", progress.moon.value < 1f)
        assertFalse("digits rolled back", progress.swapped)
        advance(1_000)
        atEnd(false)
    }

    @Test fun anAlertJustBeforeSkipsTheTransition() {
        start(initial = false)
        alertAt = System.currentTimeMillis() - 5_000
        advance(20)
        active = true
        advance(40)
        // No animation at all: straight to where it was going.
        atEnd(true)
    }

    @Test fun anAlertDuringTheTransitionWins() {
        start(initial = false)
        active = true
        advance(250)
        assertTrue("mid-way", progress.ring.value in 0.05f..1.3f && progress.moon.value < 1f)
        alertAt = System.currentTimeMillis()
        advance(40)
        atEnd(true)
    }

    @Test fun anOldAlertDoesNotStopIt() {
        start(initial = false)
        alertAt = System.currentTimeMillis() - 10 * 60_000
        advance(20)
        active = true
        advance(100)
        assertTrue("it plays", progress.ring.value in 0.05f..1.3f && progress.moon.value < 1f)
    }

    @Test fun inAmbientNothingAnimates() {
        start(initial = false, ambient = true)
        active = true
        advance(40)
        atEnd(true)
    }

    @Test fun withReducedMotionOnlyTheCentreCrossFades() {
        start(initial = false, reduceMotion = true)
        active = true
        advance(100)
        // The ring, waves and moon are already there; the centre is cross-fading (250 ms).
        assertEquals(1f, progress.ring.value, 0.001f)
        assertEquals(1f, progress.waves.value, 0.001f)
        assertEquals(1f, progress.moon.value, 0.001f)
        assertEquals(0f, progress.label.value, 0.001f)
        assertTrue(progress.figure.value in 0.01f..0.99f)
        advance(400)
        atEnd(true)
    }

    @Test fun togglingBackMidwayReturnsFromWhereItIs() {
        start(initial = false)
        active = true
        advance(300)
        active = false
        advance(1_200)
        atEnd(false)
    }
}
