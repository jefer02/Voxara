package com.example.voxara.core.headphones

import java.security.MessageDigest

/**
 * One audio output as far as "Not headphones" needs to know it: its kind, its platform type and, for Bluetooth,
 * its address (read to recognise the same device, never shown, never sent, and only kept as [speakerKeyOf]'s digest).
 */
data class OutputId(val kind: OutputKind, val androidType: Int, val address: String?) {

    val bluetooth: Boolean
        get() = kind == OutputKind.BLUETOOTH_A2DP || kind == OutputKind.BLE_HEADSET || kind == OutputKind.BLE_BROADCAST

    /** The same physical output for as long as it stays connected (a Bluetooth device by address, a socket by type). */
    val sessionKey: String get() = if (!address.isNullOrEmpty()) "a:${address.uppercase()}" else "t:$androidType"

    /**
     * Whether the device can be remembered as a speaker: a Bluetooth output whose full address the platform gave
     * (without the Bluetooth permission it may hand out a partly masked one, which two devices could share; such an
     * output is only ignored until it disconnects, so a real pair of headphones is never silenced by a lookalike).
     */
    val rememberable: Boolean get() = bluetooth && address != null && FULL_ADDRESS.matches(address) && address.any { it != '0' && it != ':' }

    private companion object {
        val FULL_ADDRESS = Regex("""^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$""")
    }
}

/**
 * The salted one-way digest a remembered speaker is kept as (16 bytes of SHA-256, hex): enough to recognise the
 * same device on this watch or phone, useless for finding the address again. Null when [id] cannot be remembered.
 */
fun speakerKeyOf(id: OutputId, salt: String): String? {
    if (!id.rememberable) return null
    val digest = MessageDigest.getInstance("SHA-256").digest("$salt|${id.address!!.uppercase()}".toByteArray(Charsets.UTF_8))
    return digest.take(16).joinToString("") { "%02x".format(it) }
}

/** What "Not headphones" did. */
enum class NotHeadphonesOutcome {
    /** Left out of headphones mode until it disconnects; the next connection is detected again. */
    IGNORED,
    /** Ignored now and remembered as a speaker: left out on every later connection too. */
    REMEMBERED,
    /** Nothing was connected (it had already gone). */
    NOTHING,
}

/**
 * "NOT HEADPHONES" — the outputs the wearer said are not headphones, so headphones mode stops counting them.
 *
 *  - IGNORED UNTIL IT DISCONNECTS: the output is left out of [chooseRoute] while it stays connected. Once it is gone
 *    (unplugged, Bluetooth off, out of range) [prune] forgets it, so the next connection is detected again.
 *  - REMEMBERED AS A SPEAKER: a Bluetooth output the wearer marked as a speaker stays out on every later connection,
 *    by its salted digest ([speakerKeyOf]); the caller persists [speakers]. [forgetSpeakers] undoes it.
 *
 * Only the headphone estimate reads this: the room is still measured, saved and alerted on exactly as before, and
 * headphone alerts stop only for the output that was left out. Thread-safe (the platform's callbacks and the UI).
 */
class OutputIgnoreList(speakers: Set<String> = emptySet()) {

    private val untilDisconnect = HashSet<String>()
    private val speakerKeys = HashSet(speakers)

    /** The remembered speakers' digests, to persist. */
    val speakers: Set<String> @Synchronized get() = speakerKeys.toSet()

    /** Some output is being ignored until it disconnects. */
    val ignoringNow: Boolean @Synchronized get() = untilDisconnect.isNotEmpty()

    @Synchronized
    fun isIgnored(id: OutputId, salt: String): Boolean =
        id.sessionKey in untilDisconnect || speakerKeyOf(id, salt)?.let { it in speakerKeys } == true

    /** Leaves [id] out until it disconnects. */
    @Synchronized
    fun ignoreUntilDisconnect(id: OutputId) { untilDisconnect += id.sessionKey }

    /** Leaves [id] out now and on every later connection; false (and only ignored now) when it cannot be remembered. */
    @Synchronized
    fun rememberSpeaker(id: OutputId, salt: String): Boolean {
        untilDisconnect += id.sessionKey
        val key = speakerKeyOf(id, salt) ?: return false
        speakerKeys += key
        return true
    }

    /** Replaces the remembered speakers (loaded from storage). */
    @Synchronized
    fun loadSpeakers(keys: Set<String>) { speakerKeys.clear(); speakerKeys += keys }

    /** Forgets every remembered speaker (they are detected as headphones again on their next connection). */
    @Synchronized
    fun forgetSpeakers() { speakerKeys.clear() }

    /**
     * Forgets the "until it disconnects" outputs that are no longer [connected]. Call it with every output the
     * platform reports, at every change. True when something was forgotten.
     */
    @Synchronized
    fun prune(connected: Collection<OutputId>): Boolean {
        val present = connected.mapTo(HashSet()) { it.sessionKey }
        return untilDisconnect.retainAll(present)
    }
}
