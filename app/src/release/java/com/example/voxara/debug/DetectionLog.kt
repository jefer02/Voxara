package com.example.voxara.debug

import android.content.Context
import androidx.compose.runtime.Composable

/** Release builds: no detection log. Same API, no behaviour, no file, no strings. */
object DetectionLog {
    const val available = false

    @Suppress("UNUSED_PARAMETER")
    fun record(context: Context, trace: DetectionTrace) = Unit

    @Composable
    fun SettingsEntries() = Unit
}
