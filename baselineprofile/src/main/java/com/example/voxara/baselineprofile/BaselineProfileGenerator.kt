package com.example.voxara.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the phone app's Baseline Profile: app start (also the startup profile), Home and its scroll, Trends and its
 * scroll, and opening Today from the dose card. `./gradlew :phone:generateBaselineProfile` with a device connected.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        passSetupIfShown()
        scrollHome()
        scrollTrends()
        openToday()
    }
}
