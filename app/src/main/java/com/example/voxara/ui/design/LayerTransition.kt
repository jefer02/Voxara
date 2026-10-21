package com.example.voxara.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.example.voxara.ui.theme.LocalAmbient
import com.example.voxara.ui.theme.LocalReduceMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * HEADPHONES MODE — THE TRANSITION. The signature moment of the app: the listening layer turning on
 * or off on the Overview hero. One object holds the stages as separate values so each part of the
 * picture reads only its own, in the draw phase, with no recomposition per frame:
 *
 *   ring    the 270° ring splits into two ear-cup arcs with a spring, accent -> indigo
 *   waves   the organic wave rings morph into the stereo (left-right) shape, accent -> indigo
 *   figure  the centre swaps from "Tono + outside level" to "listening level" (cross-fade)
 *   moon    the outside level flies from the centre to a small moon in the ring's top gap
 *   label   "Headphones mode" appears around the top, then settles into the chip
 *
 * Staged ring -> waves -> figure -> moon, about 900 ms in, about 700 ms out. Reduced motion: only the
 * cross-fade. Ambient, or the screen not resumed: no animation at all. If an alert is delivered
 * during it (or just before), the alert wins and the picture jumps to its end state.
 */

/** An alert this recent (ms) means a transition is not played at all: the alert has the wrist and the eye. */
internal const val ALERT_SKIP_MS = 60_000L

@Stable
internal class LayerProgress(initial: Boolean) {
    /** 0 = one 270° ring, 1 = two arcs (overshoots on the way: the spring). */
    val ring = Animatable(if (initial) 1f else 0f)

    /** 0 = organic waves, 1 = stereo waves. */
    val waves = Animatable(if (initial) 1f else 0f)

    /** 0 = the normal centre (Tono's line), 1 = the listening centre. */
    val figure = Animatable(if (initial) 1f else 0f)

    /** 0 = the outside level is the figure, 1 = it is the moon at the top (overshoots slightly). */
    val moon = Animatable(if (initial) 1f else 0f)

    /** 0 hidden, 0..1 appearing around the top, 1..2 settling into the chip. */
    val label = Animatable(0f)

    /** The centre figure shows the listening value (it has rolled): set partway through the transition. */
    var swapped by mutableStateOf(initial)
        private set

    /** The last state this progress was driven to. */
    var target by mutableStateOf(initial)
        private set

    suspend fun snapTo(active: Boolean) {
        target = active
        swapped = active
        val v = if (active) 1f else 0f
        ring.snapTo(v); waves.snapTo(v); figure.snapTo(v); moon.snapTo(v); label.snapTo(0f)
    }

    /** Reduced motion: everything is already where it ends except the centre, which cross-fades. */
    suspend fun crossfade(active: Boolean) {
        val v = if (active) 1f else 0f
        target = active
        ring.snapTo(v); waves.snapTo(v); moon.snapTo(v); label.snapTo(0f)
        swapped = active
        figure.animateTo(v, tween(250))
    }

    suspend fun enter() = coroutineScope {
        target = true
        swapped = false
        // 1. ring: the arcs part with a spring and turn indigo
        launch { ring.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
        // 2. waves: morph to the stereo shape
        launch { delay(120); waves.animateTo(1f, tween(480, easing = FastOutSlowInEasing)) }
        // 3. figure: the centre swaps and the digits roll from the outside level to the listening one
        launch { delay(240); figure.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
        launch { delay(330); swapped = true }
        // 4. moon: the outside level leaves the centre and settles in the top gap
        launch { delay(300); moon.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 250f)) }
        // The label appears, holds, and settles into the chip.
        launch {
            delay(280)
            label.animateTo(1f, tween(180))
            delay(140)
            label.animateTo(2f, tween(280, easing = FastOutSlowInEasing))
        }
    }

    suspend fun leave() = coroutineScope {
        target = false
        launch { label.snapTo(0f) }
        // The moon returns to the centre and is the main figure again.
        launch { moon.animateTo(0f, tween(420, easing = FastOutSlowInEasing)) }
        launch { delay(120); swapped = false }
        launch { delay(60); figure.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
        // The arcs rejoin.
        launch { delay(160); ring.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
        launch { delay(200); waves.animateTo(0f, tween(380, easing = FastOutSlowInEasing)) }
    }
}

/**
 * The transition's state for the Overview hero, driven by [active]. The first composition sits at
 * the end state (no replay when a page is composed again); a later change animates only while the
 * screen is interactive and resumed (not ambient, lifecycle RESUMED; reduced motion cross-fades), is skipped when an alert was
 * delivered within [ALERT_SKIP_MS] and cancelled, jumping to its end, if one is delivered during it.
 */
@Composable
internal fun rememberLayerProgress(active: Boolean, lastAlertAtMs: Long): LayerProgress {
    val progress = remember { LayerProgress(active) }
    val still = LocalReduceMotion.current
    // Something may play (a full transition, or the cross-fade under reduced motion) only while the screen is
    // interactive (not ambient) and resumed.
    val canAnimate = animationsActive(!LocalAmbient.current)
    val alertAt = rememberUpdatedState(lastAlertAtMs)
    val animate = rememberUpdatedState(canAnimate)
    val reduce = rememberUpdatedState(still)
    LaunchedEffect(active) {
        if (progress.target == active) return@LaunchedEffect
        val startAlert = alertAt.value
        val alertJustNow = startAlert > 0L && System.currentTimeMillis() - startAlert < ALERT_SKIP_MS
        when {
            !animate.value || alertJustNow -> progress.snapTo(active)
            reduce.value -> progress.crossfade(active)
            else -> coroutineScope {
                val run = launch { if (active) progress.enter() else progress.leave() }
                // An alert delivered meanwhile wins: stop and show where this was going.
                val watch = launch { snapshotFlow { alertAt.value }.first { it != startAlert }; run.cancel() }
                run.join()
                watch.cancel()
                progress.snapTo(active)
            }
        }
    }
    return progress
}
