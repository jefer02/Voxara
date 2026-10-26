package com.example.voxara.sync

import com.example.voxara.core.alerts.AlertKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirroredAlertTest {

    private val now = 1_750_000_000_000L
    private val limit = MirroredAlert(AlertKind.DAILY_100, 101.5, 100, now - 30_000)

    @Test
    fun `round-trips and rejects garbage`() {
        assertEquals(limit, MirroredAlert.decode(limit.encode()))
        assertNull(MirroredAlert.decode("nope".toByteArray()))
    }

    @Test
    fun `off by default, limits only, once, and never stale`() {
        assertFalse(MirroredAlert.shouldShow(limit, enabled = false, lastShownAtMs = 0, nowMs = now))
        assertTrue(MirroredAlert.shouldShow(limit, enabled = true, lastShownAtMs = 0, nowMs = now))
        // Already shown (the same message delivered twice).
        assertFalse(MirroredAlert.shouldShow(limit, enabled = true, lastShownAtMs = limit.atMs, nowMs = now))
        // Warnings and advisories are never mirrored.
        assertFalse(MirroredAlert.shouldShow(limit.copy(kind = AlertKind.DAILY_80), true, 0, now))
        // Delivered long after it fired (the phone was away): not news any more.
        assertFalse(MirroredAlert.shouldShow(limit.copy(atMs = now - MirroredAlert.MAX_AGE_MS - 1), true, 0, now))
        // Each 100% kind has its own notification, levels replace each other per kind.
        assertEquals("DAILY_100:100", limit.notificationKey)
    }

    @Test
    fun `a watch whose clock is a couple of minutes ahead of the phone is still shown`() {
        // The alert says it fired 2 minutes in the phone's future: clock difference, not time travel.
        assertTrue(MirroredAlert.shouldShow(limit.copy(atMs = now + 120_000), true, 0, now))
        // Hours ahead is nonsense and is not shown.
        assertFalse(MirroredAlert.shouldShow(limit.copy(atMs = now + 3_600_000), true, 0, now))
    }
}
