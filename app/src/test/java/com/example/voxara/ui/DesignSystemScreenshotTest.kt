package com.example.voxara.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.design.AlertSheet
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.ProgressRing
import com.example.voxara.ui.design.ReplyChip
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VSpace
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.WeekBars
import com.example.voxara.ui.design.ZoneBadge
import com.example.voxara.ui.design.EditorialHeader
import com.example.voxara.ui.design.GlowNumber
import com.example.voxara.ui.design.LevelBezel
import com.example.voxara.ui.design.PageDots
import com.example.voxara.ui.design.SourcePill
import com.example.voxara.ui.design.WaveHalo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The component gallery at every target size and font scale (design system previews). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DesignSystemScreenshotTest {

    @Test
    fun gauges() = ScreenshotHarness.captureAll("ds_gauges") {
        Column(Modifier.fillMaxSize().background(VColor.Black), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(150.dp).padding(top = 20.dp), contentAlignment = Alignment.Center) {
                WaveHalo(0.73f, VColor.tone(RiskZone.LOUD).fill, Modifier.fillMaxSize())
                LevelBezel(dba = 88f, modifier = Modifier.fillMaxSize())
                GlowNumber("88", VColor.tone(RiskZone.LOUD).fill)
            }
            ProgressRing(0.56f, Modifier.size(64.dp)) { Text("56%", style = VType.Label, color = VColor.Text) }
        }
    }

    @Test
    fun badgesAndText() = ScreenshotHarness.captureAll("ds_badges") {
        Column(
            Modifier.fillMaxSize().background(VColor.Black).padding(horizontal = VSpace.roundInset, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VSpace.xs),
        ) {
            EditorialHeader("Voxara", title = "Loud café")
            SourcePill("Auto · Street", VColor.AccentInk)
            PageDots(7, 2)
            RiskZone.entries.forEach { ZoneBadge(it) }
        }
    }

    @Test
    fun cardsAndButtons() = ScreenshotHarness.captureAll("ds_cards") {
        Column(
            Modifier.fillMaxSize().background(VColor.Black).verticalScroll(rememberScrollState())
                .padding(horizontal = VSpace.roundInset, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(VSpace.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MetricCard(VoxIcons.Headphones, "Headphones", "≈ 78 dBA", detail = "Estimated", onClick = {})
            PillButton("Ask Tono", onClick = {}, icon = VoxIcons.Ask)
            PillButton("Not now", onClick = {}, style = PillStyle.TONAL)
            ReplyChip("My week?", onClick = {})
            WeekBars(listOf(0.2f, 0.5f, 0.1f, 0f, 0.8f, 0.4f, 0.6f), listOf("M", "T", "W", "T", "F", "S", "S"), "week")
        }
    }

    @Test
    fun alertSheet() = ScreenshotHarness.captureAll("ds_alert") {
        Column(
            Modifier.fillMaxSize().background(VColor.Black).padding(horizontal = VSpace.roundInset, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            AlertSheet(RiskZone.DANGEROUS, "Today's sound allowance is used up", "Protect your ears or move somewhere quieter.") {
                PillButton("Got it", onClick = {})
            }
        }
    }
}
