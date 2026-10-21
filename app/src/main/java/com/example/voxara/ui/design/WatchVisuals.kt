package com.example.voxara.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.wear.compose.material3.Text
import com.example.voxara.ui.theme.LocalAmbient
import com.example.voxara.ui.theme.LocalReduceMotion

/*
 * WATCH-ONLY TEMPLATE VISUALS (docs/design/STYLE.md §4–5): harmonic wave rings with a radial halo
 * behind the main number, the soft glow under the number, the top source pill, page dots and the
 * round overlay of the limit alert. No blur anywhere (minSdk 30): glows are translucent fills.
 */

/**
 * True when free-running animation may run: interactive (not ambient) and "remove animations" off.
 * [animationsActive] adds "the screen is resumed" on top, so nothing animates while paused.
 */
val LocalAnimate = staticCompositionLocalOf { false }

/** Rings drift slowly when it is quiet and pick up pace with the level (they settle, never stop dead). */
private fun settleSpeed(intensity: Float): Float = 0.9f + 4.3f * intensity.coerceIn(0f, 1f)

/**
 * WAVE HALO — the breathing harmonic rings and halo, sized to the box. [intensity] 0..1 comes from
 * the live level and is followed with a spring, so the rings swell and calm smoothly instead of
 * snapping; [listening] draws 4 finer rings for headphones. [strength] scales their opacity (the pages
 * keep them low behind text). Static when animation is off; nothing at all in ambient.
 */
@Composable
fun WaveHalo(
    intensity: Float, color: Color, modifier: Modifier = Modifier, listening: Boolean = false, strength: Float = 1f,
    stereo: () -> Float = { 0f }, stereoColor: Color = Color.Unspecified,
) {
    if (LocalAmbient.current) return
    val rings = remember { HarmonicRings() }
    val follow by animateFloatAsState(
        targetValue = intensity,
        animationSpec = if (LocalReduceMotion.current) snap() else VMotion.value(),
        label = "waveIntensity",
    )
    val phase = rememberWavePhase(running = LocalAnimate.current, speed = settleSpeed(follow))
    Canvas(modifier) {
        // Headphones mode: the rings morph to the stereo shape and shift to indigo, driven by the
        // transition (read here, in the draw phase, so a frame does not recompose anything).
        val m = stereo().coerceIn(0f, 1f)
        val tint = if (m > 0f && stereoColor != Color.Unspecified) lerp(color, stereoColor, m) else color
        rings.draw(
            this, phase.value, follow, color, rings = if (listening && m == 0f) 4 else 3, fine = listening && m == 0f,
            strength = strength, morph = m, ringColor = tint,
        )
    }
}

/**
 * ORBIT STAGE — a page's round canvas: the wave rings and halo fill the ring's inside, a dark scrim
 * under the middle keeps text legible over them, then [picture] (the page's own visualisation on the
 * ring) and [content] on top. The box is the display; the ring hugs its edge ([Face]) and the waves
 * fill the ring's inside. Everything is a fraction of the display, which is also where the hero's
 * text and figure scales come from ([LocalTextScale], [LocalFigureScale]). [stereo] and
 * [stereoColor] morph the waves for headphones mode.
 */
@Composable
fun OrbitStage(
    wave: Float,
    waveColor: Color,
    modifier: Modifier = Modifier,
    listening: Boolean = false,
    stereo: () -> Float = { 0f },
    stereoColor: Color = Color.Unspecified,
    picture: @Composable () -> Unit,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val d = minOf(maxWidth, maxHeight)
        CompositionLocalProvider(
            LocalTextScale provides textScaleFor(d),
            LocalFigureScale provides figureScaleFor(d),
            LocalDisplay provides d,
        ) {
            WaveHalo(wave, waveColor, Modifier.size(d).padding(d * Face.WAVES_PAD), listening, strength = 0.9f, stereo = stereo, stereoColor = stereoColor)
            if (!LocalAmbient.current) {
                Canvas(Modifier.size(d)) {
                    val scrim = size.minDimension * 0.38f
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color.Black.copy(alpha = 0.5f), 0.7f to Color.Black.copy(alpha = 0.28f), 1f to Color.Transparent,
                            center = center, radius = scrim,
                        ),
                        radius = scrim, center = center,
                    )
                }
            }
            Box(Modifier.size(d)) { picture() }
            content()
        }
    }
}

/**
 * LEVEL NUMBER — a page's figure: tabular rolling digits over a soft glow in the tier colour (a
 * translucent radial fill, no blur), with a smaller companion [suffix] after the digits and an optional
 * [prefix] before them. Ambient: lighter weight, grey, no glow, no rolling.
 */
@Composable
fun LevelNumber(
    value: Int?, glow: Color, style: TextStyle, modifier: Modifier = Modifier, suffix: String = "", prefix: String = "",
) {
    val ambient = LocalAmbient.current
    val glowScale = LocalFigureScale.current
    // The glow is drawn behind the digits without taking any room: the figure is as tall as its line.
    Box(
        modifier.then(
            if (ambient) Modifier else Modifier.drawBehind {
                val gw = 96.dp.toPx() * glowScale
                val gh = 64.dp.toPx() * glowScale
                translate(center.x - gw / 2, center.y - gh / 2) {
                    drawOval(Brush.radialGradient(listOf(glow.copy(alpha = 0.30f), Color.Transparent), center = Offset(gw / 2, gh / 2), radius = gh / 2), size = Size(gw, gh))
                }
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        val s = if (ambient) style.copy(fontWeight = FontWeight.Medium) else style
        val c = if (ambient) VColor.TextSecondary else VColor.Text
        if (value == null) Text("—", style = s, color = c, maxLines = 1)
        else RollingNumber(value, s, c, suffix = suffix, prefix = prefix)
    }
}

/** PAGE DOTS — progress through steps: the current one is a 16 dp accent pill, the rest small dots. */
@Composable
fun PageDots(count: Int, index: Int, modifier: Modifier = Modifier, color: Color = VColor.AccentInk) {
    Row(
        modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier.height(6.dp).width(if (i == index) 16.dp else 6.dp).clip(CircleShape)
                    .background(if (i == index) color else Color.White.copy(alpha = 0.30f))
            )
        }
    }
}

/**
 * CIRCULAR OVERLAY — a near-black round panel inset 6 dp with a 1.5 dp border in the tier colour,
 * behind [content] (the limit alert). [pulse] (0..1, read while drawing) swells the border and draws
 * a soft translucent ring outside it: the alert's entrance, one pulse per haptic beat.
 */
@Composable
fun CircularOverlay(
    border: Color,
    modifier: Modifier = Modifier,
    pulse: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit,
) {
    val ambient = LocalAmbient.current
    Box(modifier.fillMaxSize().background(VColor.Black)) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            drawCircle(Color(0xEB070910))
            if (!ambient) {
                val p = pulse()
                if (p > 0f) drawCircle(border.copy(alpha = 0.35f * p), radius = size.minDimension / 2 - 3.dp.toPx() * p, style = Stroke(8.dp.toPx() * p))
                drawCircle(border, style = Stroke((1.5f + 2.5f * p).dp.toPx()))
            }
        }
        content()
    }
}
