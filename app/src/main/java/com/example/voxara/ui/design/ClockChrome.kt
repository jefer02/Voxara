package com.example.voxara.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * THE SYSTEM CLOCK vs THE RING. The ring now hugs the display's edge, exactly where the curved time
 * text sits, so on the glance pages (whose first screen is the ring) the clock steps aside; lists
 * and the other screens keep it. The app scaffold reads [hidden]; a hero page sets it with
 * [HideClock] while it is the one on screen and the clock comes back when it leaves.
 */
internal object ClockChrome {
    var hidden by mutableStateOf(false)
}

/** Hides the system clock while this is composed and [hide] is true. */
@Composable
internal fun HideClock(hide: Boolean) {
    DisposableEffect(hide) {
        ClockChrome.hidden = hide
        onDispose { ClockChrome.hidden = false }
    }
}
