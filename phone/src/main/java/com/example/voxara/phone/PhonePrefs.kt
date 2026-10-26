package com.example.voxara.phone

import android.content.Context
import com.example.voxara.settings.LanguageChoice

/**
 * Small state that must be readable synchronously: the headphone-sampling opt-in (read by the
 * sampler and the Bluetooth receiver), its two timestamps, and the app language (read in
 * attachBaseContext, before any coroutine could finish).
 */
object PhonePrefs {
    private const val FILE = "voxara_phone"
    private const val ENABLED = "enabled"
    private const val LAST_SAMPLE = "last_sample_ms"
    private const val LAST_SYNC = "last_sync_ms"
    private const val WIPED_AT = "wiped_at_ms"
    private const val LANGUAGE = "language"
    private const val BT_ASKED = "bt_asked"
    private const val ALERT_MEMORY = "alert_memory"
    private const val OUTBOX = "outbox"
    private const val SEQ = "hp_seq"
    private const val BATTERY_HINT_DISMISSED = "battery_hint_dismissed"
    private const val SPEAKER_KEYS = "hp_speaker_keys"
    private const val SPEAKER_SALT = "hp_speaker_salt"

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Phone-side headphone sampling: OFF until the wearer opts in. */
    fun enabled(c: Context) = p(c).getBoolean(ENABLED, false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean(ENABLED, v).apply()
    fun lastSampleMs(c: Context) = p(c).getLong(LAST_SAMPLE, 0L)
    fun setLastSampleMs(c: Context, v: Long) = p(c).edit().putLong(LAST_SAMPLE, v).apply()
    /** When "delete all" last ran here (0 = never): listening before it is never queued for the watch. Committed, not applied: it must hold if the process dies next. */
    fun wipedAtMs(c: Context) = p(c).getLong(WIPED_AT, 0L)
    fun setWipedAtMs(c: Context, v: Long) = p(c).edit().putLong(WIPED_AT, v).commit()
    /** Last listening span handed to the watch. */
    fun lastSyncMs(c: Context) = p(c).getLong(LAST_SYNC, 0L)
    fun setLastSyncMs(c: Context, v: Long) = p(c).edit().putLong(LAST_SYNC, v).apply()

    fun language(c: Context): LanguageChoice =
        runCatching { LanguageChoice.valueOf(p(c).getString(LANGUAGE, null) ?: "SYSTEM") }.getOrDefault(LanguageChoice.SYSTEM)
    fun setLanguage(c: Context, v: LanguageChoice) = p(c).edit().putString(LANGUAGE, v.name).apply()

    /** BLUETOOTH_CONNECT was asked for in context already (the first time something connected over Bluetooth). */
    fun btAsked(c: Context) = p(c).getBoolean(BT_ASKED, false)
    fun setBtAsked(c: Context, v: Boolean) = p(c).edit().putBoolean(BT_ASKED, v).apply()

    /** The headphone alert's memory on the phone (tier already alerted, cooldown), as [com.example.voxara.core.alerts.AlertMemoryCodec] text. */
    fun alertMemory(c: Context): String? = p(c).getString(ALERT_MEMORY, null)
    fun setAlertMemory(c: Context, v: String) = p(c).edit().putString(ALERT_MEMORY, v).apply()

    /** Listening spans not yet handed to the Data Layer ([com.example.voxara.link.Outbox] text). */
    fun outbox(c: Context): String? = p(c).getString(OUTBOX, null)
    fun setOutbox(c: Context, v: String) = p(c).edit().putString(OUTBOX, v).apply()

    /** The next sequence number for the live headphone state (it only grows, so a receiver can drop old news). */
    @Synchronized
    fun nextSequence(c: Context, nowMs: Long): Long {
        val next = maxOf(p(c).getLong(SEQ, 0L) + 1, nowMs)
        p(c).edit().putLong(SEQ, next).apply()
        return next
    }

    /**
     * "Not headphones" memory: the outputs remembered as speakers (salted digests, never an address or a name) and
     * this install's salt, made on first use. Local to the phone.
     */
    @Synchronized
    fun speakerSalt(c: Context): String = p(c).getString(SPEAKER_SALT, null)
        ?: java.util.UUID.randomUUID().toString().also { p(c).edit().putString(SPEAKER_SALT, it).apply() }
    fun speakerKeys(c: Context): Set<String> = p(c).getStringSet(SPEAKER_KEYS, null)?.toSet().orEmpty()
    fun setSpeakerKeys(c: Context, v: Set<String>) = p(c).edit().putStringSet(SPEAKER_KEYS, v.toSet()).apply()

    /** Loads the "Not headphones" memory into the process-wide list every headphone watcher reads (once per process). */
    @Synchronized
    fun loadSpeakerMemory(c: Context) {
        if (speakerMemoryLoaded) return
        val app = c.applicationContext
        com.example.voxara.audio.HeadphoneWatcher.salt = speakerSalt(app)
        com.example.voxara.audio.HeadphoneWatcher.ignored.loadSpeakers(speakerKeys(app))
        com.example.voxara.audio.HeadphoneWatcher.onSpeakersChanged = { setSpeakerKeys(app, it) }
        speakerMemoryLoaded = true
    }
    @Volatile private var speakerMemoryLoaded = false

    /** The battery-optimisation hint was dismissed (it is shown again if the app is still restricted after a while). */
    fun batteryHintDismissedAt(c: Context) = p(c).getLong(BATTERY_HINT_DISMISSED, 0L)
    fun setBatteryHintDismissedAt(c: Context, v: Long) = p(c).edit().putLong(BATTERY_HINT_DISMISSED, v).apply()
}
