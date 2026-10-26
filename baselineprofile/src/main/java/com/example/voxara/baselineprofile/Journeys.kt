package com.example.voxara.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** The phone app's package (the same applicationId as the watch app: the Data Layer needs it). */
const val PACKAGE = "com.example.voxara"

private const val WAIT_MS = 5_000L

/*
 * THE JOURNEYS the profile covers and the benchmarks time. They find the app's parts by test tag (exposed as resource ids
 * in MainActivity), so they work in any language: "tab_<route>", "list", "dose_card", "setup_continue".
 */

/** First run: walk through the setup (its steps only explain and offer; every opt-in stays off). */
fun MacrobenchmarkScope.passSetupIfShown() {
    repeat(10) {
        val next = device.findObject(By.res("setup_continue")) ?: return
        next.click()
        device.waitForIdle()
    }
}

/** Home: wait for its list, fling down and back up. */
fun MacrobenchmarkScope.scrollHome() {
    openTab("home")
    flingList()
}

/** Trends: open the tab, fling its charts down and back up. */
fun MacrobenchmarkScope.scrollTrends() {
    openTab("trends")
    flingList()
}

/** Today: from Home, the dose card opens it (the shared element); then back. */
fun MacrobenchmarkScope.openToday() {
    openTab("home")
    val card = device.wait(Until.findObject(By.res("dose_card")), WAIT_MS) ?: return
    card.click()
    device.wait(Until.hasObject(By.res("list")), WAIT_MS)
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.openTab(route: String) {
    device.wait(Until.findObject(By.res("tab_$route")), WAIT_MS)?.click()
    device.wait(Until.hasObject(By.res("list")), WAIT_MS)
}

private fun MacrobenchmarkScope.flingList() {
    val list = device.findObject(By.res("list")) ?: return
    // keep clear of the system gesture areas and the tab bar
    list.setGestureMargin(device.displayWidth / 5)
    list.fling(Direction.DOWN)
    device.waitForIdle()
    list.fling(Direction.UP)
    device.waitForIdle()
}
