package com.example.voxara.debug

import androidx.compose.runtime.Composable

/** Release builds: the debug tools do not exist. Same API, no behaviour, no strings. */
object DebugTools {
    const val available = false

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun SettingsEntries(navigate: (String) -> Unit) = Unit

    @Composable
    fun HapticsScreen() = Unit
}
