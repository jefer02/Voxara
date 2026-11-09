package com.example.voxara.core.advice

import com.example.voxara.core.risk.RiskState
import com.example.voxara.core.situation.SituationMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The advice is keyed by the mode the wearer is known to be in (the same one the mode chip and Tono use),
 * and falls back to level-only sentences when Auto is not sure: Street as a fallback is not a street.
 */
class AdviceGrammarTest {

    private fun advise(mode: SituationMode?, risk: RiskState, dose: Double = 50.0) =
        AdviceGrammar.advise(AdviceGrammar.State(dba = 88.0, dosePercent = dose, headroomMinutes = 90.0, mode = mode, risk = risk)).kind

    @Test fun anUnknownModeGetsLevelOnlyAdvice() {
        assertEquals(AdviceKind.ACCRUING_GENERIC, advise(null, RiskState.ACCRUING))
        assertEquals(AdviceKind.HAZARD_GENERIC, advise(null, RiskState.HAZARD))
        assertEquals(AdviceKind.CRITICAL_GENERIC, advise(null, RiskState.CRITICAL))
        assertEquals(AdviceKind.FINE, advise(null, RiskState.CALM, dose = 10.0))
    }

    @Test fun aKnownModeGetsItsOwnSentence() {
        assertEquals(AdviceKind.HAZARD_TRAFFIC, advise(SituationMode.STREET, RiskState.HAZARD))
        assertEquals(AdviceKind.ACCRUING_TRANSIT, advise(SituationMode.TRANSPORT, RiskState.ACCRUING))
        assertEquals(AdviceKind.CRITICAL_MUSIC, advise(SituationMode.CONCERT, RiskState.CRITICAL))
        assertEquals(AdviceKind.HAZARD_MACHINERY, advise(SituationMode.WORK, RiskState.HAZARD))
        assertEquals(AdviceKind.FINE, advise(SituationMode.QUIET, RiskState.CALM, dose = 10.0))
        assertEquals(AdviceKind.COMFORTABLE, advise(SituationMode.STREET, RiskState.CALM, dose = 10.0))
    }

    @Test fun streetAndTransportEachKeepTheirOwnAdvice() {
        // Street: the street sentences (crossing, a side street), never Transport's platform or carriage.
        assertEquals(AdviceKind.ACCRUING_TRAFFIC, advise(SituationMode.STREET, RiskState.ACCRUING))
        assertEquals(AdviceKind.HAZARD_TRAFFIC, advise(SituationMode.STREET, RiskState.HAZARD))
        // Transport: its own vehicle (accruing) and platform (hazard) sentences.
        assertEquals(AdviceKind.ACCRUING_TRANSIT, advise(SituationMode.TRANSPORT, RiskState.ACCRUING))
        assertEquals(AdviceKind.HAZARD_TRANSIT, advise(SituationMode.TRANSPORT, RiskState.HAZARD))
    }
}
