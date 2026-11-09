package com.example.voxara.ui

import com.example.voxara.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Each mode's advice speaks about that mode's place: Street about a street (never a platform or a carriage), Transport
 * about the platform and the vehicle. The words Street once got wrong are named here so they cannot come back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdviceCopyTest {

    private fun text(lang: String, id: Int): String {
        RuntimeEnvironment.setQualifiers(lang)
        return RuntimeEnvironment.getApplication().getString(id, "1h").lowercase()
    }

    private val transit = Regex("platform|carriage|car forward|commute|andén|vagón|trayecto")

    @Test fun streetAdviceIsAboutAStreet() {
        for (lang in listOf("en", "es")) for (id in listOf(R.string.advice_accruing_traffic, R.string.advice_hazard_traffic)) {
            val s = text(lang, id)
            assertFalse("$lang: $s", transit.containsMatchIn(s))
        }
        assertTrue(text("en", R.string.advice_accruing_traffic).contains("street"))
        assertTrue(text("es", R.string.advice_accruing_traffic).contains("calle"))
    }

    @Test fun transportHasPlatformAndVehicleAdviceOfItsOwn() {
        for (lang in listOf("en", "es")) {
            assertTrue(transit.containsMatchIn(text(lang, R.string.advice_accruing_transit)))
            assertTrue(transit.containsMatchIn(text(lang, R.string.advice_hazard_transit)))
        }
    }
}
