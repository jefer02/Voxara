package com.example.voxara.phone

import android.content.Context

/** The companion's only state: the opt-in and two timestamps. */
object PhonePrefs {
    private const val FILE = "voxara_phone"
    private const val ENABLED = "enabled"
    private const val LAST_SAMPLE = "last_sample_ms"
    private const val LAST_SYNC = "last_sync_ms"

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** OFF until the wearer opts in on the consent screen. */
    fun enabled(c: Context) = p(c).getBoolean(ENABLED, false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean(ENABLED, v).apply()
    fun lastSampleMs(c: Context) = p(c).getLong(LAST_SAMPLE, 0L)
    fun setLastSampleMs(c: Context, v: Long) = p(c).edit().putLong(LAST_SAMPLE, v).apply()
    fun lastSyncMs(c: Context) = p(c).getLong(LAST_SYNC, 0L)
    fun setLastSyncMs(c: Context, v: Long) = p(c).edit().putLong(LAST_SYNC, v).apply()
}
