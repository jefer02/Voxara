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
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.ReplyChip
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VSpace
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.WeekBars
import com.example.voxara.ui.design.ZoneTag
import com.example.voxara.ui.design.GroupLabel
import com.example.voxara.ui.design.LevelNumber
import com.example.voxara.ui.design.LevelScale
import com.example.voxara.ui.design.OrbitStage
import com.example.voxara.ui.design.PageDots
import com.example.voxara.ui.design.ResumePill
import com.example.voxara.ui.design.ScreenTitle
import com.example.voxara.ui.design.TabularNumber
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
            OrbitStage(
                wave = 0.73f, waveColor = VColor.tone(RiskZone.LOUD).fill, modifier = Modifier.size(180.dp),
                picture = { LevelScale(88f) },
            ) {
                LevelNumber(88, VColor.tone(RiskZone.LOUD).fill, VType.Display, Modifier.align(Alignment.Center), suffix = "dBA")
            }
        }
    }

    /** Plain zeros, equal-width digits: "30", "100", "0" and "88" side by side. */
    @Test
    fun numerals() = ScreenshotHarness.captureAll("ds_numerals") {
        Column(
            Modifier.fillMaxSize().background(VColor.Black).padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            listOf("30", "100", "0%", "88", "11").forEach { TabularNumber(it, VType.Display, VColor.Text) }
        }
    }

    @Test
    fun badgesAndText() = ScreenshotHarness.captureAll("ds_badges") {
        Column(
            Modifier.fillMaxSize().background(VColor.Black).padding(horizontal = VSpace.roundInset, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VSpace.xs),
        ) {
            ScreenTitle("Loud café")
            GroupLabel("Alerts")
            PageDots(7, 2)
            ResumePill(onClick = {})
            RiskZone.entries.forEach { ZoneTag(it) }
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
}
