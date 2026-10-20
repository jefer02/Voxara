package com.example.voxara.phone.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.voxara.ai.CoachUi
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.Tier
import com.example.voxara.core.design.Accent
import com.example.voxara.link.LinkState
import com.example.voxara.link.WatchNode
import com.example.voxara.phone.R
import com.example.voxara.phone.data.ThemeMode
import com.example.voxara.phone.model.Freshness
import com.example.voxara.phone.model.Headline
import com.example.voxara.phone.model.History
import com.example.voxara.phone.model.careScore
import com.example.voxara.phone.model.headline
import com.example.voxara.settings.SettingField
import com.example.voxara.settings.VoxaraSettings
import com.example.voxara.sync.WatchStatus
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Navigation routes. The five tabs, then the screens reached from them. */
object PhoneRoutes {
    const val HOME = "home"
    const val TRENDS = "trends"
    const val TONO = "tono"
    const val WATCH = "watch"
    const val SETTINGS = "settings"
    const val TODAY = "today"
    const val ALERTS = "alerts"
    const val HEADPHONES = "headphones"
    const val HOW = "how"
}

/** One exchange in the Tono chat (kept in memory only — never stored). */
data class ChatTurn(val question: String?, val reply: CoachReply, val source: ReplySource?, val cloudFailed: Boolean)

/** Everything a screen may read. Screens never talk to the Data Layer or the database. */
data class PhoneUiState(
    /** The first read of the stores has finished (before that, screens show skeletons). */
    val loaded: Boolean = true,
    val setupDone: Boolean = true,
    val link: LinkState = LinkState.Checking,
    val syncing: Boolean = false,
    val status: WatchStatus? = null,
    val history: History = History.EMPTY,
    val lastSyncMs: Long? = null,
    val nowMs: Long = System.currentTimeMillis(),
    val settings: VoxaraSettings = VoxaraSettings.DEFAULTS,
    val pending: Set<SettingField> = emptySet(),
    val phoneSampling: Boolean = false,
    val lastListeningSentMs: Long = 0L,
    val retentionDays: Int = 90,
    val mirrorAlerts: Boolean = false,
    val deletePending: Boolean = false,
    val coach: CoachUi = CoachUi(),
    val chat: List<ChatTurn> = emptyList(),
    val cloudAvailable: Boolean = false,
    val notificationsGranted: Boolean = true,
    val bluetoothGranted: Boolean = true,
    val appVersion: String = "",
    val debugBuild: Boolean = false,
    /** Appearance: phone only, stored in PhoneStore, never synced. */
    val accent: Accent = Accent.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
) {
    val head: Headline get() = headline(status, history, lastSyncMs, settings.sensitiveListener, nowMs)
    val score: Int get() = careScore(head.weeklyPercent, head.headphoneWeeklyPercent, history.ambient)
    val watch: WatchNode?
        get() = when (val l = link) {
            is LinkState.Linked -> l.watch
            is LinkState.Problem -> l.watch
            else -> null
        }
    /** Nothing synced yet, so no number can be shown. */
    val noData: Boolean get() = status == null && history.isEmpty
}

/** Everything a screen may do. */
data class PhoneActions(
    val onRefresh: () -> Unit = {},
    val onSetting: (SettingField, String) -> Unit = { _, _ -> },
    val onPhoneSampling: (Boolean) -> Unit = {},
    val onAsk: (CoachTask, String?) -> Unit = { _, _ -> },
    val onOpenOnWatch: () -> Unit = {},
    val onOpenCalibration: () -> Unit = {},
    val onInstallOnWatch: (String) -> Unit = {},
    val onChooseWatch: (String) -> Unit = {},
    val onOpenWearableApp: () -> Unit = {},
    val onRequestNotifications: () -> Unit = {},
    val onRequestBluetooth: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onDeleteAll: () -> Unit = {},
    val onRetention: (Int) -> Unit = {},
    val onMirrorAlerts: (Boolean) -> Unit = {},
    val onFinishSetup: () -> Unit = {},
    val onRunSetupAgain: () -> Unit = {},
    val onAccent: (Accent) -> Unit = {},
    val onThemeMode: (ThemeMode) -> Unit = {},
)

// ------------------------------------------------------------------ shared wording

@Composable
fun freshnessText(atMs: Long?, nowMs: Long): String = when (Freshness.of(atMs, nowMs)) {
    Freshness.NONE -> stringResource(R.string.fresh_never)
    Freshness.RECENT -> {
        val min = ((nowMs - atMs!!) / 60_000L).toInt()
        if (min < 1) stringResource(R.string.fresh_now) else stringResource(R.string.fresh_min, min)
    }
    Freshness.EARLIER -> stringResource(R.string.fresh_at, timeText(atMs!!))
    Freshness.STALE -> stringResource(R.string.fresh_stale, dateTimeText(atMs!!, nowMs))
}

fun timeText(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

/** "14:05" today, otherwise "24 Sep 14:05". */
fun dateTimeText(ms: Long, nowMs: Long): String {
    val a = Calendar.getInstance().apply { timeInMillis = ms }
    val b = Calendar.getInstance().apply { timeInMillis = nowMs }
    val sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) timeText(ms)
    else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
}

fun dateText(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

@StringRes
fun AlertKind.titleRes(): Int = when (this) {
    AlertKind.AMBIENT_LOUD -> R.string.alert_kind_ambient_loud
    AlertKind.DAILY_50 -> R.string.alert_kind_daily_50
    AlertKind.DAILY_80 -> R.string.alert_kind_daily_80
    AlertKind.DAILY_100 -> R.string.alert_kind_daily_100
    AlertKind.WEEK_80 -> R.string.alert_kind_week_80
    AlertKind.WEEK_100 -> R.string.alert_kind_week_100
    AlertKind.HEADPHONE_WEEK_80 -> R.string.alert_kind_hp_week_80
    AlertKind.HEADPHONE_WEEK_100 -> R.string.alert_kind_hp_week_100
    AlertKind.HEADPHONE_LOUD_NOW -> R.string.alert_kind_hp_loud
}

@StringRes
fun Tier.labelRes(): Int = when (this) {
    Tier.ADVISORY -> R.string.tier_advisory
    Tier.WARNING -> R.string.tier_warning
    Tier.LIMIT -> R.string.tier_limit
}

/** The level alerts carry dBA; every other alert carries a percent. */
val AlertKind.isLevel: Boolean get() = this == AlertKind.AMBIENT_LOUD || this == AlertKind.HEADPHONE_LOUD_NOW
