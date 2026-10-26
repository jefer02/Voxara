package com.example.voxara.phone.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voxara.phone.ui.theme.LocalGlass
import com.example.voxara.phone.ui.theme.Vox
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/*
 * MATERIALS AND HAPTICS (docs/design/PHONE_PREMIUM.md). The bars are glass: the content scrolls under them and shows
 * through, blurred (Haze, RenderEffect on Android 12+). Below Android 12, on low-RAM phones, in previews and in tests they
 * are a solid surface of the same colour, so nothing depends on the blur to be read.
 */

/** The app's content as seen by the bottom bar (null outside the app's navigation: previews, tests, setup). */
val LocalBarsHaze = compositionLocalOf<HazeState?> { null }

/**
 * The room the bottom bar takes over the content (its height plus the gesture area), so a list can end above it. Zero
 * when there is no bottom bar (the rail, setup).
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** Content a glass bar can blur. */
fun Modifier.glassSource(state: HazeState?): Modifier = if (state == null) this else hazeSource(state)

/**
 * A glass bar over [state]'s content: a light blur under a tint of [tint] ([GLASS_ALPHA]; the background by default, the
 * surface for the floating tab bar), or [tint] itself, solid, when glass is off ([LocalGlass]) or there is nothing to blur.
 */
@Composable
fun Modifier.glassBar(state: HazeState?, tint: Color = Vox.colors.background): Modifier {
    val c = Vox.colors
    val glass = LocalGlass.current && state != null
    if (!glass) return this.background(tint)
    val style = remember(tint, c.background) {
        HazeStyle(
            backgroundColor = c.background,
            tint = HazeTint(tint.copy(alpha = GLASS_ALPHA)),
            blurRadius = GLASS_BLUR,
            noiseFactor = 0f,
            fallbackTint = HazeTint(tint),
        )
    }
    return this.hazeEffect(state, style)
}

/** How much of the background tints the glass: enough for AA text on any content scrolled under it. */
const val GLASS_ALPHA = 0.78f
val GLASS_BLUR: Dp = 24.dp

/**
 * Crisp ticks, never a buzz: a segment tick for choices and chart scrubbing, a toggle tick for switches (Android 14 has
 * the dedicated constants; older versions get the closest short tick). Silent when the system turns touch feedback off.
 */
@Stable
class VoxHaptics(private val view: View) {
    fun tick() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    fun segment() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    fun toggle(on: Boolean) = perform(
        if (Build.VERSION.SDK_INT >= 34) (if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
        else HapticFeedbackConstants.CLOCK_TICK,
    )

    private fun perform(constant: Int) { view.performHapticFeedback(constant) }
}

@Composable
fun rememberVoxHaptics(): VoxHaptics {
    val view = LocalView.current
    return remember(view) { VoxHaptics(view) }
}
