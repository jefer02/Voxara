package com.example.voxara.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.voxara.data.AppLanguage
import com.example.voxara.data.AppMode
import com.example.voxara.data.ExposureState
import com.example.voxara.data.Scenario
import com.example.voxara.ui.screens.BreachScreen
import com.example.voxara.ui.screens.ConcertScreen
import com.example.voxara.ui.screens.DayScreen
import com.example.voxara.ui.screens.LiveScreen
import com.example.voxara.ui.screens.ModesScreen
import com.example.voxara.ui.screens.VoiceScreen
import com.example.voxara.ui.theme.Vox
import com.example.voxara.voice.VoiceTurn
import kotlinx.coroutines.launch

/** The five surfaces, left to right. LIVE owns the wrist-raise. */
private const val PAGE_LIVE = 0
private const val PAGE_DAY = 1
private const val PAGE_CONCERT = 2
private const val PAGE_VOICE = 3
private const val PAGE_MODES = 4
private const val PAGE_COUNT = 5

@Composable
fun VoxaraApp(
    state: ExposureState,
    turn: VoiceTurn.Turn,
    ambient: Boolean,
    onMode: (AppMode) -> Unit,
    onMonitoring: (Boolean) -> Unit,
    onCalibration: (Double) -> Unit,
    onScenario: (Scenario) -> Unit,
    onResetDose: () -> Unit,
    onConcertToggle: () -> Unit,
    onVoiceArm: () -> Unit,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
) {
    val pager = rememberPagerState(initialPage = PAGE_LIVE) { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    var breachDismissedAt by remember { mutableStateOf(0.0) }

    // Ambient always returns to the one screen that matters from a lowered wrist.
    LaunchedEffect(ambient) { if (ambient) pager.scrollToPage(PAGE_LIVE) }

    // A voice turn pulls its own page forward; the answer should never arrive off-screen.
    LaunchedEffect(turn.phase) {
        if (turn.phase == VoiceTurn.Phase.LISTENING) pager.animateScrollToPage(PAGE_VOICE)
    }

    val breach = state.dosePercent >= 100.0 && state.dosePercent > breachDismissedAt && !ambient

    Box(Modifier.fillMaxSize().background(Vox.Void)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !ambient,
        ) { page ->
            when (page) {
                PAGE_LIVE -> LiveScreen(state, ambient)
                PAGE_DAY -> DayScreen(state, ambient)
                PAGE_CONCERT -> ConcertScreen(state, onConcertToggle)
                PAGE_VOICE -> VoiceScreen(turn, onVoiceArm)
                PAGE_MODES -> ModesScreen(
                    state = state,
                    onMode = { mode ->
                        onMode(mode)
                        scope.launch {
                            when (mode) {
                                AppMode.CONCERT -> pager.animateScrollToPage(PAGE_CONCERT)
                                AppMode.VOICE -> pager.animateScrollToPage(PAGE_VOICE)
                                AppMode.URBAN -> pager.animateScrollToPage(PAGE_LIVE)
                            }
                        }
                    },
                    onMonitoring = onMonitoring,
                    onCalibration = onCalibration,
                    onScenario = onScenario,
                    onResetDose = {
                        onResetDose()
                        breachDismissedAt = 0.0
                    },
                    language = language,
                    onLanguage = onLanguage,
                )
            }
        }

        if (!ambient) {
            PageDots(
                count = PAGE_COUNT,
                current = pager.currentPage,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            )
        }

        // BREACH ALERT — haptic first (fired by the service), screen second.
        AnimatedVisibility(visible = breach, enter = fadeIn(), exit = fadeOut()) {
            BreachScreen(
                state = state,
                onDismiss = { breachDismissedAt = state.dosePercent },
            )
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 5.dp else 3.dp)
                    .clip(CircleShape)
                    .background(if (i == current) Vox.Ink2 else Vox.Ink3.copy(alpha = 0.5f))
            )
        }
    }
}
