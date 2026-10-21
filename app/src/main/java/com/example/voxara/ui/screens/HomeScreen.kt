package com.example.voxara.ui.screens

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.PagerScaffoldDefaults
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.ui.Routes
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.HideClock
import com.example.voxara.ui.design.LocalPageInset
import com.example.voxara.ui.design.LocalPageReveal
import com.example.voxara.ui.design.LocalPageShift
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.ScreenTitle
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VMotion
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VoxList
import com.example.voxara.ui.design.capFont
import com.example.voxara.ui.design.row
import com.example.voxara.ui.theme.LocalReduceMotion

/**
 * Space at the bottom of a pager page that the page indicator may cover: lists keep it clear so every item can
 * scroll up above the dots, and the bottom scrim is solid black where the dots sit.
 */
private val PAGER_INDICATOR_MIN = 30.dp

/**
 * HOME — the root: a horizontal pager of three round pages. NOW (the live level on its dBA scale, the zone,
 * Tono's one line and today's dose; headphones mode transforms it), DOSE (today's 24 hours and the rolling
 * week, one ring that morphs between them: crown or the Today | Week switch) and MORE (Ask, Mode, Settings).
 * The Wear page indicator hugs the bezel; swiping right on the first page leaves the app as usual.
 * [initialPage] is a route ([Routes.NOW], [Routes.TODAY], [Routes.WEEK], [Routes.HEADPHONES], [Routes.MENU]) and
 * opens the page, and for Dose the view, it stands for.
 *
 * Changing page is more than a slide: the picture on the page becoming current draws itself in (arc
 * sweep, bars growing, segments filling) while the one leaving retracts, and each picture turns a
 * little with the swipe ([LocalPageReveal], [LocalPageShift]). The Dose view (Today or Week) is kept here, so
 * swiping away and back finds the wearer where they were.
 */
@Composable
fun HomeScreen(env: UiEnv, actions: VoxaraActions, initialPage: String = Routes.HOME, navigate: (String) -> Unit) {
    val target = remember { GlanceTarget.forRoute(initialPage) }
    val pages = GlancePage.entries
    val pagerState = rememberPagerState(initialPage = pages.indexOf(target.page), pageCount = { pages.size })
    val still = LocalReduceMotion.current
    var doseView by rememberSaveable { mutableStateOf(target.view) }
    AnnounceHeadphonesMode(env.state.listening.active)
    // The ring owns the top edge on the glance pages: the system clock steps aside there (it stays on More).
    HideClock(hide = pages[pagerState.currentPage] != GlancePage.MENU)
    HorizontalPagerScaffold(pagerState = pagerState) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(pagerState),
            key = { pages[it].ordinal },
        ) { index ->
            // 0 -> 1 when this page becomes the current one (a decelerating draw-in), back to 0 when it leaves.
            val reveal = remember { Animatable(if (still) 1f else 0f) }
            LaunchedEffect(index, still) {
                if (still) {
                    reveal.snapTo(1f)
                    return@LaunchedEffect
                }
                snapshotFlow { pagerState.currentPage == index }.collect { current ->
                    if (current) reveal.animateTo(1f, VMotion.sweep()) else reveal.animateTo(0f, tween(VMotion.EXIT_MS))
                }
            }
            val shift = remember { derivedStateOf { index - (pagerState.currentPage + pagerState.currentPageOffsetFraction) } }
            CompositionLocalProvider(
                LocalPageReveal provides reveal.asState(),
                LocalPageShift provides shift,
                LocalPageInfo provides (index to pages.size),
                LocalPageInset provides PAGER_INDICATOR_MIN,
            ) {
                AnimatedPage(pageIndex = index, pagerState = pagerState) {
                    when (pages[index]) {
                        GlancePage.NOW -> NowScreen(env, actions, navigate)
                        GlancePage.DOSE -> DoseScreen(env, actions, doseView, onView = { doseView = it })
                        GlancePage.MENU -> MenuPage(env, navigate)
                    }
                }
            }
        }
    }
}

/**
 * Says it when headphones mode turns on or off ("Headphones mode on, outside sound still measured"),
 * for TalkBack, on whichever page the wearer is. Not on the first composition: only a change is news.
 */
@Composable
private fun AnnounceHeadphonesMode(on: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    val onText = stringResource(R.string.hp_mode_announce_on)
    val offText = stringResource(R.string.hp_mode_announce_off)
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(on) {
        if (first) { first = false; return@LaunchedEffect }
        val am = context.getSystemService(AccessibilityManager::class.java)
        if (am?.isEnabled == true) view.announceForAccessibility(if (on) onText else offText)
    }
}

/**
 * MORE — the last page: Ask, the mode and Settings, and the one-line disclaimer. A list with room at
 * the bottom for the page indicator; Ask is a normal button here (an edge button would sit under the
 * indicator).
 */
@Composable
fun MenuPage(env: UiEnv, navigate: (String) -> Unit) {
    val s = env.state
    val tono = s.tonoEnabled
    val name = stringResource(R.string.ai_name)
    val info = LocalPageInfo.current
    val title = stringResource(R.string.page_more)
    val titleSpoken = info?.let { stringResource(R.string.page_cd, it.first + 1, it.second, title) } ?: title
    VoxList {
        row { ScreenTitle(title, modifier = Modifier.semantics { contentDescription = titleSpoken }) }
        row {
            Button(
                onClick = { navigate(Routes.ASK) },
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(if (tono) VoxIcons.Ask else VoxIcons.Mic, contentDescription = null, modifier = Modifier.size(20.dp)) },
                label = {
                    Text(
                        if (tono) stringResource(R.string.menu_ask, name) else stringResource(R.string.ask_speak),
                        style = VType.TitleSmall.capFont(1.5f), maxLines = 2,
                    )
                },
            )
        }
        row {
            MetricCard(
                icon = VoxIcons.Tune,
                label = stringResource(R.string.mode_title),
                value = modeValue(s.situationChoice, s.mode, s.listening),
                onClick = { navigate(Routes.MODE) },
            )
        }
        row {
            MetricCard(
                icon = VoxIcons.Settings,
                label = stringResource(R.string.card_settings),
                value = stringResource(R.string.card_settings_value),
                accent = VColor.TextSecondary,
                onClick = { navigate(Routes.SETTINGS) },
            )
        }
        row {
            Text(
                stringResource(R.string.disclaimer), style = VType.Caption, color = VColor.TextSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
