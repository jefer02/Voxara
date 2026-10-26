package com.example.voxara.core.headphones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "NOT HEADPHONES": an output the wearer marked is left out of headphones mode only until it disconnects; a
 * Bluetooth device remembered as a speaker stays out on later connections; nothing identifying is kept.
 */
class OutputIgnoreTest {

    private val salt = "test-salt"
    private val speaker = OutputId(OutputKind.BLUETOOTH_A2DP, 8, "AA:BB:CC:11:22:33")
    private val buds = OutputId(OutputKind.BLUETOOTH_A2DP, 8, "DD:EE:FF:44:55:66")
    private val wired = OutputId(OutputKind.WIRED_HEADPHONES, 4, null)
    private fun candidate(id: OutputId) = RouteCandidate(id.kind, BtClassHint.UNKNOWN, id.androidType, id)

    @Test fun ignoredOnlyUntilItDisconnects() {
        val list = OutputIgnoreList()
        list.ignoreUntilDisconnect(wired)
        assertTrue(list.isIgnored(wired, salt))
        list.prune(listOf(wired))                  // still plugged in: still ignored
        assertTrue(list.isIgnored(wired, salt))
        list.prune(emptyList())                    // unplugged: forgotten
        assertFalse(list.isIgnored(wired, salt))   // the next connection is detected again
    }

    @Test fun aRememberedSpeakerStaysOutOnLaterConnections() {
        val list = OutputIgnoreList()
        assertTrue(list.rememberSpeaker(speaker, salt))
        list.prune(emptyList())                    // it disconnects...
        assertTrue(list.isIgnored(speaker, salt))  // ...and is still a speaker when it comes back
        assertFalse(list.isIgnored(buds, salt))    // another device is not affected
        // Survives a restart through the digests the app stores.
        val reloaded = OutputIgnoreList(list.speakers)
        assertTrue(reloaded.isIgnored(speaker, salt))
        reloaded.forgetSpeakers()
        assertFalse(reloaded.isIgnored(speaker, salt))
    }

    @Test fun onlyADigestIsKeptNeverTheAddress() {
        val key = speakerKeyOf(speaker, salt)
        assertNotNull(key)
        assertEquals(32, key!!.length)
        assertFalse(key.contains("AA", ignoreCase = true) && key.contains("BB", ignoreCase = true) && key.contains("CC", ignoreCase = true))
        assertTrue(speakerKeyOf(speaker, "another-install") != key) // salted per install
    }

    @Test fun aMaskedOrMissingAddressIsNeverRememberedOnlyIgnored() {
        val masked = OutputId(OutputKind.BLUETOOTH_A2DP, 8, "XX:XX:XX:XX:22:33")
        assertNull(speakerKeyOf(masked, salt))
        assertNull(speakerKeyOf(wired, salt))
        val list = OutputIgnoreList()
        assertFalse(list.rememberSpeaker(masked, salt)) // ignored for now, not remembered
        assertTrue(list.isIgnored(masked, salt))
        assertTrue(list.speakers.isEmpty())
    }

    @Test fun theRouteSkipsAnIgnoredOutputAndFallsBackToTheNextOne() {
        val list = OutputIgnoreList()
        list.ignoreUntilDisconnect(wired)
        val route = chooseRoute(listOf(candidate(wired), candidate(buds)), null) { c -> c.id?.let { list.isIgnored(it, salt) } == true }
        assertEquals(buds, route?.id)
        list.ignoreUntilDisconnect(buds)
        assertNull(chooseRoute(listOf(candidate(wired), candidate(buds)), null) { c -> c.id?.let { list.isIgnored(it, salt) } == true })
    }
}
