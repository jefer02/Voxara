package com.example.voxara.phone.ui

import androidx.compose.runtime.Composable
import com.example.voxara.ai.CoachUi
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.core.design.Accent
import com.example.voxara.phone.data.ThemeMode
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertRecord
import com.example.voxara.core.ledger.DaySummary
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.energyOf
import com.example.voxara.core.ledger.nioshDoseOf
import com.example.voxara.audio.HeadphoneWatcher
import com.example.voxara.core.headphones.HeadphoneAlert
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.HeadphoneModeChoice
import com.example.voxara.core.headphones.HeadphoneRoute
import com.example.voxara.core.headphones.LowerUi
import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.link.LinkHealth
import com.example.voxara.phone.listening.PhoneListeningRuntime
import com.example.voxara.sync.LowerOutcome
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.link.LinkState
import com.example.voxara.link.SyncProblem
import com.example.voxara.link.WatchNode
import com.example.voxara.phone.model.History
import com.example.voxara.phone.ui.screens.AlertsScreen
import com.example.voxara.phone.ui.screens.HeadphonesScreen
import com.example.voxara.phone.ui.screens.HomeScreen
import com.example.voxara.phone.ui.screens.HowItWorksScreen
import com.example.voxara.phone.ui.screens.SettingsScreen
import com.example.voxara.phone.ui.screens.SetupScreen
import com.example.voxara.phone.ui.screens.TodayScreen
import com.example.voxara.phone.ui.screens.TonoScreen
import com.example.voxara.phone.ui.screens.TrendsScreen
import com.example.voxara.phone.ui.screens.WatchScreen
import com.example.voxara.phone.ui.theme.VoxaraPhoneTheme
import com.example.voxara.settings.SettingField
import com.example.voxara.sync.WatchStatus
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The key phone screens at phone / foldable / tablet widths and font scales 1.0 and 1.3 (2.0 for
 * Home and Settings), light and dark, every accent on Home, rendered on the JVM into phone/build/screenshots/ for
 * review. Fake data only; nothing touches the network or the Data Layer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class PhoneScreenshotTest {

    /** Qualifiers in resource order: size, orientation, then (night inserted here) density. */
    private data class Size(val name: String, val screen: String, val density: String)

    private val phone = Size("phone", "w360dp-h800dp", "xxhdpi")
    private val foldable = Size("foldable", "w673dp-h841dp", "xhdpi")
    private val tablet = Size("tablet", "w1280dp-h800dp-land", "xhdpi")
    private val sizes = listOf(phone, foldable, tablet)
    private val tall = Size("tall", "w360dp-h3200dp", "xhdpi")

    private val outDir by lazy { File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() } }

    private fun capture(
        name: String, size: Size, scale: Float, dark: Boolean = false, accent: Accent? = null, locale: String = "",
        content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.setQualifiers("${if (locale.isEmpty()) "" else "$locale-"}${size.screen}-${if (dark) "night" else "notnight"}-${size.density}")
        RuntimeEnvironment.setFontScale(scale)
        val tag = accent?.let { "_" + it.name.lowercase() } ?: ""
        val file = File(outDir, "${if (locale.isEmpty()) "" else "${locale}_"}${name}_${size.name}_x$scale$tag${if (dark) "_dark" else ""}.png")
        captureRoboImage(file.path) {
            VoxaraPhoneTheme(darkTheme = dark, accent = accent ?: Accent.DEFAULT, reduceMotion = true) { content() }
        }
    }

    private fun all(name: String, content: @Composable () -> Unit) {
        for (size in sizes) for (scale in listOf(1.0f, 1.3f)) capture(name, size, scale, content = content)
    }

    // ------------------------------------------------------------------ sample data

    private val now = 1_750_000_000_000L
    private val watch = WatchNode("w1", "Galaxy Watch7", nearby = true)
    private val todayStart = Math.floorDiv(now, 60_000L) / 1440 * 1440

    private val minutes = (0 until 900).filter { it % 7 != 0 }.map { m ->
        val dba = when (m) { in 480..560 -> 78.0; in 720..800 -> 88.0; in 840..860 -> 97.0; else -> 58.0 }
        MinuteRecord(todayStart + m, ExposureKind.AMBIENT, 60.0, energyOf(dba, 60.0), nioshDoseOf(dba, 60.0))
    }
    private val history = History.build(minutes, emptyList(), emptyList(), now).let { h ->
        h.copy(
            ambient = h.ambient.mapIndexed { i, d ->
                if (i == 0) d else DaySummary(d.dayEpoch, List(24) { hr -> if (hr in 8..19) 60f + (hr * 3 + i) % 30 else 0f },
                    listOf(35.0, 62.0, 18.0, 91.0, 44.0, 120.0, 27.0, 55.0)[i % 8], 420.0, 0.05 + 0.02 * (i % 5))
            },
            headphone = h.headphone.mapIndexed { i, d ->
                if (i > 20) d else DaySummary(d.dayEpoch, List(24) { hr -> if (hr in 17..19) 82f else 0f }, 0.0, 60.0, 0.03 + 0.01 * (i % 3))
            },
            alerts = listOf(
                AlertRecord(4, now - 20 * 60_000L, AlertKind.DAILY_80, 81.0, 0, acknowledged = false, snoozed = false),
                AlertRecord(3, now - 140 * 60_000L, AlertKind.AMBIENT_LOUD, 91.0, 0, acknowledged = true, snoozed = false),
                AlertRecord(2, now - 26 * 3_600_000L, AlertKind.DAILY_100, 102.0, 100, acknowledged = true, snoozed = false),
                AlertRecord(1, now - 50 * 3_600_000L, AlertKind.HEADPHONE_LOUD_NOW, 101.0, 0, acknowledged = false, snoozed = true),
            ),
        )
    }
    private val status = WatchStatus(
        atMs = now - 4 * 60_000L, appVersion = "0.9", monitoring = MonitoringStatus.RUNNING, micGranted = true,
        notificationsGranted = true, calibrated = false, calibratedAtMs = null, dosePercent = 83.0, weeklyPercent = 64.0,
        headphoneWeeklyPercent = 27.0, lastDba = 71.0, lastReadingAtMs = now - 4 * 60_000L, sensitiveListener = false,
        powerSaveMode = false, ignoringBatteryOptimizations = false, tonoEnabled = true, retentionDays = 35,
    )
    private val reply = CoachReply(
        status = "A busy afternoon for your ears",
        insight = "You are at 83% of today's limit and 64% of this week's.",
        why = "Most of it came from two loud hours after lunch.",
        suggestion = "A quieter evening keeps the week on track.",
        chips = listOf("My week?", "Why estimated?", "Tips"),
    )
    private val state = PhoneUiState(
        link = LinkState.Linked(watch, emptyList(), now - 4 * 60_000L),
        status = status, history = history, lastSyncMs = now - 4 * 60_000L, nowMs = now,
        coach = CoachUi(reply = reply, source = ReplySource.CLOUD_AI),
        chat = listOf(ChatTurn("How loud was lunch?", reply, ReplySource.CLOUD_AI, cloudFailed = false)),
        cloudAvailable = true, pending = setOf(SettingField.ALERT_THRESHOLD), phoneSampling = true,
        lastListeningSentMs = now - 30 * 60_000L, appVersion = "0.9.2-phone",
    )
    private val actions = PhoneActions()

    // ------------------------------------------------------------------ screens

    @Test fun home() {
        all("home") { HomeScreen(state, actions) {} }
        capture("home", phone, 2.0f) { HomeScreen(state, actions) {} }
        capture("home", phone, 1.0f, dark = true) { HomeScreen(state, actions) {} }
        capture("home", phone, 2.0f, dark = true) { HomeScreen(state, actions) {} }
    }

    /** Settings → Appearance: Home in every accent, light and dark (a calm day, so the hero shows the accent). */
    @Test fun homeAccents() {
        val calm = state.copy(status = status.copy(dosePercent = 34.0))
        for (accent in Accent.entries) for (dark in listOf(false, true)) {
            capture("home_accent", phone, 1.0f, dark = dark, accent = accent) { HomeScreen(calm, actions) {} }
        }
    }

    /** The watch in "Transport + Headphones" (detected by the watch), on Home, Watch and Settings; also with large text. */
    @Test fun headphonesMode() {
        val hp = state.copy(
            status = status.copy(situationMode = SituationMode.TRANSPORT, headphonesMode = true, headphonesSource = LayerSource.WATCH),
        )
        for (scale in listOf(1.0f, 1.3f, 2.0f)) {
            capture("home_hp", phone, scale) { HomeScreen(hp, actions) {} }
            capture("watch_hp", phone, scale) { WatchScreen(hp, actions) }
        }
        capture("home_hp", phone, 1.0f, dark = true) { HomeScreen(hp, actions) {} }
        capture("settings_hp", tall, 1.0f) { SettingsScreen(hp, actions) {} }
        capture("settings_hp", tall, 1.0f, dark = true) { SettingsScreen(hp, actions) {} }
        // Headphones mode is automatic: whatever an older app left in the synced field, there is nothing to switch here.
        capture("settings_hp", tall, 2.0f) { SettingsScreen(hp.copy(settings = hp.settings.copy(headphoneMode = HeadphoneModeChoice.ON)), actions) {} }
    }

    @Test fun homeStates() {
        capture("home_loading", phone, 1.0f) { HomeScreen(state.copy(loaded = false), actions) {} }
        capture("home_no_watch", phone, 1.0f) {
            HomeScreen(PhoneUiState(link = LinkState.NoWatch, nowMs = now), actions) {}
        }
        capture("home_not_installed", phone, 1.0f) {
            HomeScreen(PhoneUiState(link = LinkState.NotInstalled(listOf(watch)), nowMs = now), actions) {}
        }
        capture("home_stale", phone, 1.0f) {
            HomeScreen(state.copy(status = status.copy(atMs = now - 26 * 3_600_000L), lastSyncMs = now - 26 * 3_600_000L), actions) {}
        }
    }

    @Test fun navigationAdaptsToWidth() {
        for (size in sizes) capture("app", size, 1.0f) { PhoneApp(state, actions) }
    }

    @Test fun today() = all("today") { TodayScreen(state, {}, {}) }
    @Test fun trends() {
        all("trends") { TrendsScreen(state, {}) }
        capture("trends", phone, 1.0f, dark = true) { TrendsScreen(state, {}) }
    }
    @Test fun alerts() = all("alerts") { AlertsScreen(state, {}, {}) }
    @Test fun headphones() = all("headphones") { HeadphonesScreen(state, actions, {}) }

    // ------------------------------------------------------------------ headphones on the phone, the link, battery

    private val hpSnapshot = HeadphoneWatcher.Snapshot(
        route = HeadphoneRoute(HeadphoneCategory.UNKNOWN, OutputKind.BLUETOOTH_A2DP, 0, possibleSpeaker = true),
        playing = true, volumeDb = -14.0, volumeIndex = 11, volumeMax = 15, fixedVolume = false, btDetailsAvailable = false,
    )
    private val liveOver = PhoneListeningRuntime.Live(
        running = true, snapshot = hpSnapshot,
        alert = HeadphoneAlert(1, 86.0, now, LayerSource.PHONE),
    )

    /** The phone's own headphones: playing, a possible speaker, the alert and each answer to "lower the volume". */
    @Test fun phoneHeadphonesLive() {
        val hp = state.copy(phoneSampling = true, bluetoothGranted = false)
        for (locale in listOf("", "es")) for (scale in listOf(1.0f, 1.3f)) {
            capture("hp_live", phone, scale, locale = locale) { HeadphonesScreen(hp.copy(phoneLive = liveOver), actions, {}) }
            capture("hp_live_working", phone, scale, locale = locale) {
                HeadphonesScreen(hp.copy(phoneLive = liveOver.copy(lower = LowerUi.Working)), actions, {})
            }
            capture("hp_live_lowered", phone, scale, locale = locale) {
                HeadphonesScreen(hp.copy(phoneLive = liveOver.copy(lower = LowerUi.Done(LowerOutcome.LOWERED, 79.0))), actions, {})
            }
            capture("hp_live_fixed", phone, scale, locale = locale) {
                HeadphonesScreen(hp.copy(phoneLive = liveOver.copy(lower = LowerUi.Done(LowerOutcome.FIXED_VOLUME, null))), actions, {})
            }
            capture("hp_live_calm", phone, scale, locale = locale) {
                val calm = hpSnapshot.copy(volumeIndex = 6, volumeDb = -30.0)
                HeadphonesScreen(hp.copy(phoneLive = PhoneListeningRuntime.Live(running = true, snapshot = calm)), actions, {})
            }
            capture("hp_live_none", phone, scale, locale = locale) {
                HeadphonesScreen(hp.copy(phoneLive = PhoneListeningRuntime.Live(running = true)), actions, {})
            }
        }
        capture("hp_live", phone, 1.0f, dark = true) { HeadphonesScreen(hp.copy(phoneLive = liveOver), actions, {}) }
    }

    /** Reconnecting, disconnected for how long, and the battery guidance (Samsung's background limits). */
    @Test fun linkHealthAndBattery() {
        for (locale in listOf("", "es")) for (scale in listOf(1.0f, 1.3f)) {
            val reconnecting = state.copy(linkHealth = LinkHealth.Reconnecting(now - 20_000), pendingSpans = 3)
            val gone = state.copy(linkHealth = LinkHealth.Disconnected(now - 47 * 60_000L), link = LinkState.NoWatch, pendingSpans = 3, batteryRestricted = true)
            capture("link_reconnecting_watch", phone, scale, locale = locale) { WatchScreen(reconnecting, actions) }
            capture("link_disconnected_watch", phone, scale, locale = locale) { WatchScreen(gone, actions) }
            capture("link_disconnected_home", phone, scale, locale = locale) { HomeScreen(gone, actions) {} }
            capture("battery_guide_home", phone, scale, locale = locale) { HomeScreen(state.copy(batteryRestricted = true), actions) {} }
        }
    }

    @Test fun tono() {
        all("tono") { TonoScreen(state, actions) }
        capture("tono", phone, 1.0f, dark = true) { TonoScreen(state, actions) }
        capture("tono_cloud_off", phone, 1.0f) { TonoScreen(state.copy(chat = emptyList(), coach = CoachUi()), actions) }
    }

    @Test fun watch() {
        all("watch") { WatchScreen(state, actions) }
        capture("watch_problem", phone, 1.0f) {
            WatchScreen(state.copy(link = LinkState.Problem(watch, emptyList(), SyncProblem.UNREACHABLE, now - 5 * 3_600_000L)), actions)
        }
        capture("watch_not_installed", phone, 1.0f) {
            WatchScreen(state.copy(link = LinkState.NotInstalled(listOf(watch)), status = null, debugBuild = true), actions)
        }
    }

    @Test fun settings() {
        all("settings") { SettingsScreen(state, actions) {} }
        capture("settings", phone, 2.0f) { SettingsScreen(state, actions) {} }
        capture("settings", phone, 1.0f, dark = true) { SettingsScreen(state, actions) {} }
        // A tall canvas, so the whole list (Appearance included) is in one image.
        capture("settings_appearance", tall, 1.0f, accent = Accent.TURQUOISE) {
            SettingsScreen(state.copy(accent = Accent.TURQUOISE, themeMode = ThemeMode.SYSTEM), actions) {}
        }
        capture("settings_appearance", tall, 1.0f, dark = true) { SettingsScreen(state.copy(themeMode = ThemeMode.DARK), actions) {} }
    }

    @Test fun setup() {
        capture("setup_welcome", phone, 1.0f) { SetupScreen(state.copy(setupDone = false), actions, initialStep = 0) }
        capture("setup_link", phone, 1.3f) { SetupScreen(state.copy(link = LinkState.NoWatch), actions, initialStep = 2) }
        capture("setup_headphones", tablet, 1.0f) { SetupScreen(state, actions, initialStep = 4) }
    }

    @Test fun howItWorks() = all("how") { HowItWorksScreen {} }
}
