package com.example.voxara.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.wear.compose.material3.Text
import com.example.voxara.ui.theme.LocalAmbient

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

/**
 * WAVE HALO — the template's breathing harmonic rings and halo, sized to the box. [intensity] 0..1
 * comes from the live level (or the dose); [listening] draws 4 finer rings, as the template does
 * for headphones. Static when animation is off; nothing at all in ambient.
 */
@Composable
fun WaveHalo(intensity: Float, color: Color, modifier: Modifier = Modifier, listening: Boolean = false) {
    if (LocalAmbient.current) return
    val rings = remember { HarmonicRings() }
    val phase = rememberWavePhase(running = LocalAnimate.current, speed = waveSpeed(intensity))
    Canvas(modifier) {
        rings.draw(this, phase.value, intensity, color, rings = if (listening) 4 else 3, fine = listening)
    }
}

/**
 * GLOW NUMBER — the main number in tabular Mono with a soft glow under it: a translucent radial
 * fill in the accent glow (the template's text shadow, without blur). Ambient: outline weight,
 * grey, no glow.
 */
@Composable
fun GlowNumber(text: String, glow: Color, modifier: Modifier = Modifier) {
    val ambient = LocalAmbient.current
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!ambient) {
            Canvas(Modifier.size(96.dp, 64.dp)) {
                drawOval(Brush.radialGradient(listOf(glow.copy(alpha = 0.30f), Color.Transparent)))
            }
        }
        Text(
            text,
            style = if (ambient) VType.Hero.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) else VType.Hero,
            color = if (ambient) VColor.TextSecondary else VColor.Text,
            maxLines = 1,
        )
    }
}

/**
 * SOURCE PILL — the template's top pill: pulsing dot + uppercase extra-bold label on white 10 %
 * with a white 15 % border. Information only (callers wrap it in a button when it does something).
 */
@Composable
fun SourcePill(text: String, dot: Color, modifier: Modifier = Modifier) {
    val ambient = LocalAmbient.current
    Row(
        modifier
            .clip(CircleShape)
            .background(if (ambient) Color.Transparent else Color.White.copy(alpha = 0.10f))
            .border(VSize.border, Color.White.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = VSpace.m, vertical = VSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PulseDot(if (ambient) VColor.TextTertiary else dot, animate = LocalAnimate.current && !ambient)
        // Tighter tracking than the eyebrow and room for a second line: long scene names (ES, 1.3×) stay whole.
        Text(
            text.uppercase(), style = VType.Eyebrow.copy(letterSpacing = 0.06.em), color = VColor.Text,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
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
 * CIRCULAR OVERLAY — the template's round toast: a near-black round panel inset 12 dp with a
 * 1.5 dp border in the tier colour, behind [content] (the limit alert).
 */
@Composable
fun CircularOverlay(border: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val ambient = LocalAmbient.current
    Box(modifier.fillMaxSize().background(VColor.Black)) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            drawCircle(Color(0xEB070910))
            if (!ambient) drawCircle(border, style = Stroke(1.5.dp.toPx()))
        }
        content()
    }
}

