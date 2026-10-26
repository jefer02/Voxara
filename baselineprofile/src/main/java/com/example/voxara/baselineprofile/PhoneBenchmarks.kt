package com.example.voxara.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the profile buys: cold start-up and the frame timing of the journeys (Home scroll, Trends scroll, opening Today),
 * each without any compilation and with the Baseline Profile.
 * `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` with a device connected.
 */
@RunWith(AndroidJUnit4::class)
class PhoneBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test fun startupNoCompilation() = startup(CompilationMode.None())
    @Test fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test fun journeysNoCompilation() = journeys(CompilationMode.None())
    @Test fun journeysBaselineProfile() = journeys(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private fun journeys(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            passSetupIfShown()
        },
    ) {
        scrollHome()
        scrollTrends()
        openToday()
    }
}
