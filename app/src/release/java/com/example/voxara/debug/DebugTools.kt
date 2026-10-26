package com.example.voxara.debug

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions

/** Release builds: the debug tools do not exist. Same API, no behaviour, no strings. */
object DebugTools {
    const val available = false
    const val HAPTICS_ROUTE = ""

    @Suppress("UNUSED_PARAMETER")
    fun settingsSection(
        list: TransformingLazyColumnScope,
        env: UiEnv,
        actions: VoxaraActions,
        navigate: (String) -> Unit,
        open: Boolean,
        onToggle: () -> Unit,
    ) = Unit

    @Composable
    fun HapticsScreen() = Unit
}
