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
    private const val LANGUAGE = "language"

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Phone-side headphone sampling: OFF until the wearer opts in. */
    fun enabled(c: Context) = p(c).getBoolean(ENABLED, false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean(ENABLED, v).apply()
    fun lastSampleMs(c: Context) = p(c).getLong(LAST_SAMPLE, 0L)
    fun setLastSampleMs(c: Context, v: Long) = p(c).edit().putLong(LAST_SAMPLE, v).apply()
    /** Last listening span handed to the watch. */
    fun lastSyncMs(c: Context) = p(c).getLong(LAST_SYNC, 0L)
    fun setLastSyncMs(c: Context, v: Long) = p(c).edit().putLong(LAST_SYNC, v).apply()

    fun language(c: Context): LanguageChoice =
        runCatching { LanguageChoice.valueOf(p(c).getString(LANGUAGE, null) ?: "SYSTEM") }.getOrDefault(LanguageChoice.SYSTEM)
    fun setLanguage(c: Context, v: LanguageChoice) = p(c).edit().putString(LANGUAGE, v.name).apply()
}
