package com.example.voxara.ui

import android.content.res.Resources
import com.example.voxara.core.situation.AutoSettleWindow
import com.example.voxara.core.situation.AutoStatus
import com.example.voxara.core.situation.Situation
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.data.ExposureState
import com.example.voxara.ui.design.situationLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * THE MODE CHIP is the one answer to "where am I", so it only says what Auto actually knows: a detected mode,
 * "Detecting…" in Auto's first settling window only, plain "Auto" when it is still unsure after that or the build has
 * no scene model. In every state the mode applied stays the one Situation.resolve gives (Street while unsure): this
 * is wording only. The phone's pill uses the same [situationLabel].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModeLabelTest {

    private fun res(lang: String): Resources {
        RuntimeEnvironment.setQualifiers(lang)
        return RuntimeEnvironment.getApplication().resources
    }

    private fun chip(
        lang: String,
        auto: AutoStatus?,
        mode: SituationMode = SituationMode.STREET,
        choice: SituationChoice = SituationChoice.AUTO,
        layer: Boolean = false,
        spoken: Boolean = false,
    ) = situationLabel(res(lang), choice, mode, auto, layer, spoken)

    @Test fun detectedNamesTheMode() {
        assertEquals("Auto · Street", chip("en", AutoStatus.DETECTED))
        assertEquals("Auto · Calle", chip("es", AutoStatus.DETECTED))
    }

    @Test fun settlingSaysDetectingNotStreet() {
        assertEquals("Auto · Detecting…", chip("en", AutoStatus.DETECTING))
        assertEquals("Auto · Detectando…", chip("es", AutoStatus.DETECTING))
    }

    @Test fun unsureAfterTheWindowIsJustAuto() {
        assertEquals("Auto", chip("en", AutoStatus.UNSURE))
        assertEquals("Auto", chip("es", AutoStatus.UNSURE))
    }

    @Test fun withoutTheModelItIsJustAuto() {
        assertEquals("Auto", chip("en", AutoStatus.NO_MODEL))
        assertEquals("Auto", chip("es", AutoStatus.NO_MODEL))
    }

    @Test fun concertDetectedByItsRuleStillShowsWithoutTheModel() {
        assertEquals("Auto · Concert", chip("en", AutoStatus.DETECTED, SituationMode.CONCERT))
        assertEquals("Auto · Concierto", chip("es", AutoStatus.DETECTED, SituationMode.CONCERT))
    }

    @Test fun aFixedChoiceIsJustTheMode() {
        assertEquals("Street", chip("en", null, choice = SituationChoice.STREET))
        assertEquals("Transporte", chip("es", null, SituationMode.TRANSPORT, SituationChoice.TRANSPORT))
    }

    @Test fun anOlderWatchThatNeverSaidKeepsTheOlderLabel() {
        // The phone's pill with a status from a watch too old to send "auto_status".
        assertEquals("Auto · Street", chip("en", null))
        assertEquals("Auto · Calle", chip("es", null))
    }

    @Test fun headphonesModeIsAppended() {
        assertEquals("Auto · Street + Headphones", chip("en", AutoStatus.DETECTED, layer = true))
        assertEquals("Auto + Audífonos", chip("es", AutoStatus.UNSURE, layer = true))
    }

    @Test fun talkBackNeverHearsTheEllipsis() {
        assertEquals("Auto · Detecting", chip("en", AutoStatus.DETECTING, spoken = true))
        assertEquals("Auto · Detectando", chip("es", AutoStatus.DETECTING, spoken = true))
        for (lang in listOf("en", "es")) for (status in AutoStatus.entries) {
            assertFalse(chip(lang, status, spoken = true).contains('…'))
        }
    }

    /** The chip over time, the way the dosimeter drives it: the window, then the status, then the words. */
    private class Timeline(private val res: Resources) {
        private val window = AutoSettleWindow()

        fun chipAt(ms: Long, choice: SituationChoice = SituationChoice.AUTO, detected: SituationMode? = null): String {
            val settling = window.settling(choice == SituationChoice.AUTO, ms)
            val auto = Situation.autoStatus(choice, detected, sceneModel = true, settling = settling)
            // Whatever the words say, the mode applied is unchanged: Street while unsure.
            val mode = Situation.resolve(choice, detected)
            assertEquals(choice.fixed ?: detected ?: SituationMode.STREET, mode)
            return situationLabel(res, choice, mode, auto, false)
        }
    }

    @Test fun detectingLastsOnlyTheFirstSettlingWindow() {
        val t = Timeline(res("es"))
        val w = Situation.AUTO_SETTLE_MS
        // Fresh start: Detecting.
        assertEquals("Auto · Detectando…", t.chipAt(0))
        assertEquals("Auto · Detectando…", t.chipAt(w - 1))
        // Still unsure past the window: plain Auto, for as long as it stays unsure.
        assertEquals("Auto", t.chipAt(w))
        assertEquals("Auto", t.chipAt(45 * 60_000L))
        // A real detection after that names the mode.
        assertEquals("Auto · Transporte", t.chipAt(46 * 60_000L, detected = SituationMode.TRANSPORT))
        // Detection lost again later: back to plain Auto, not Detecting (the window is long gone).
        assertEquals("Auto", t.chipAt(60 * 60_000L))
    }

    @Test fun switchingAwayFromAutoAndBackRestartsTheWindow() {
        val t = Timeline(res("en"))
        val w = Situation.AUTO_SETTLE_MS
        assertEquals("Auto · Detecting…", t.chipAt(0))
        assertEquals("Auto", t.chipAt(w + 1))
        assertEquals("Street", t.chipAt(w + 2, SituationChoice.STREET))
        val back = w + 60_000L
        assertEquals("Auto · Detecting…", t.chipAt(back))
        assertEquals("Auto · Detecting…", t.chipAt(back + w - 1))
        assertEquals("Auto", t.chipAt(back + w))
    }

    @Test fun theWatchStateSaysWhichStateAutoIsIn() {
        val auto = ExposureState(situationChoice = SituationChoice.AUTO, mode = SituationMode.STREET)
        assertEquals(AutoStatus.DETECTING, auto.copy(sceneModel = true, autoSettling = true).autoStatus)
        assertEquals(AutoStatus.UNSURE, auto.copy(sceneModel = true, autoSettling = false).autoStatus)
        assertEquals(AutoStatus.NO_MODEL, auto.copy(sceneModel = false, autoSettling = true).autoStatus)
        val concert = auto.copy(sceneModel = false, mode = SituationMode.CONCERT, detectedMode = SituationMode.CONCERT)
        assertEquals(AutoStatus.DETECTED, concert.autoStatus)
        assertNull(ExposureState(situationChoice = SituationChoice.STREET).autoStatus)
    }

    @Test fun theFallbackIsAppliedButNotKnown() {
        val unsure = ExposureState(situationChoice = SituationChoice.AUTO, mode = SituationMode.STREET, detectedMode = null)
        // Street still applies (alerts and everything else are unchanged)...
        assertEquals(SituationMode.STREET, unsure.mode)
        // ...but advice and Tono are not told the wearer is on a street.
        assertNull(unsure.knownMode)
        assertEquals(SituationMode.STREET, ExposureState(situationChoice = SituationChoice.STREET).knownMode)
        assertEquals(SituationMode.TRANSPORT, unsure.copy(detectedMode = SituationMode.TRANSPORT).knownMode)
    }
}
