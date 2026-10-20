package com.example.voxara.phone.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voxara.ai.CoachUi
import com.example.voxara.link.LinkState
import com.example.voxara.phone.data.PhoneStoreState
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.ai.SymptomGuard
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.phone.BuildConfig
import com.example.voxara.phone.ListeningSampler
import com.example.voxara.phone.PhoneGraph
import com.example.voxara.phone.PhoneLocale
import com.example.voxara.phone.PhonePrefs
import com.example.voxara.phone.R
import com.example.voxara.phone.model.History
import com.example.voxara.phone.model.phoneSnapshotInputs
import com.example.voxara.phone.sync.SyncCoordinator
import com.example.voxara.phone.widget.DoseWidget
import com.example.voxara.settings.LanguageChoice
import com.example.voxara.settings.SettingField
import com.example.voxara.settings.SettingsMerge
import com.example.voxara.settings.VoxaraSettings
import com.example.voxara.sync.WearContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-off things to tell the wearer (snackbars) or to start (share sheet, other apps). */
sealed interface PhoneEvent {
    data class Message(val text: Int) : PhoneEvent
    data object Export : PhoneEvent
    data object OpenWearableApp : PhoneEvent
    data object RequestNotifications : PhoneEvent
    data object RequestBluetooth : PhoneEvent
    data class LanguageChanged(val language: LanguageChoice) : PhoneEvent
}

/**
 * Turns the stores, the database, the link and Tono into one [PhoneUiState], and the wearer's
 * taps into coordinator calls. Nothing here decides an alert: the watch does that on its own.
 */
class PhoneViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val graph = PhoneGraph.also { it.init(app) }
    private val store = graph.store
    private val db = graph.db
    private val sync = graph.sync
    private val tono = graph.tono

    private val _state = MutableStateFlow(
        PhoneUiState(
            loaded = false,
            setupDone = true,
            cloudAvailable = tono.cloudAvailable,
            appVersion = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty(),
            debugBuild = BuildConfig.DEBUG,
        )
    )
    val state: StateFlow<PhoneUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PhoneEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PhoneEvent> = _events.asSharedFlow()

    private val chat = MutableStateFlow<List<ChatTurn>>(emptyList())
    private val tick = MutableStateFlow(System.currentTimeMillis())
    private val history = MutableStateFlow(History.EMPTY)

    init {
        // Stores, link, Tono, chat and the clock → state.
        viewModelScope.launch {
            combine(store.flow, sync.link, sync.syncing, tono.ui, chat) { st, link, syncing, coach, turns ->
                Sources(st, link, syncing, coach, turns)
            }.combine(tick) { src, now -> src to now }
                .combine(history) { (src, now), h -> Triple(src, now, h) }
                .collect { (src, now, h) ->
                    val st = src.store
                    _state.update {
                        it.copy(
                            loaded = true,
                            setupDone = st.setupDone,
                            link = src.link,
                            syncing = src.syncing,
                            coach = src.coach,
                            chat = src.chat,
                            status = st.status,
                            history = h,
                            lastSyncMs = st.sync.lastSuccessMs,
                            nowMs = now,
                            settings = VoxaraSettings.from(st.phoneSettings),
                            pending = SettingsMerge.pending(st.phoneSettings, st.watchSettings),
                            retentionDays = st.retentionDays,
                            mirrorAlerts = st.mirrorAlerts,
                            deletePending = st.deletePending,
                            accent = st.accent,
                            themeMode = st.themeMode,
                        )
                    }
                }
        }
        // History: re-read after every database write.
        viewModelScope.launch {
            db.changes.collectLatest { reloadHistory() }
        }
        // Freshness labels move on with the clock.
        viewModelScope.launch {
            while (true) { delay(30_000); tick.value = System.currentTimeMillis() }
        }
        // A synced language change on the phone's own UI.
        viewModelScope.launch {
            store.flow.collect { st ->
                val lang = VoxaraSettings.from(st.phoneSettings).language
                if (lang != PhonePrefs.language(ctx)) {
                    PhonePrefs.setLanguage(ctx, lang)
                    _events.tryEmit(PhoneEvent.LanguageChanged(lang))
                }
            }
        }
        refreshLocal()
        viewModelScope.launch { sync.sync(); askStatus() }
    }

    private suspend fun reloadHistory() {
        val now = System.currentTimeMillis()
        val from = (now / 60_000L) - (History.DAYS + 1) * 1440L
        history.value = withContext(Dispatchers.IO) {
            History.build(
                db.minutes(from, Long.MAX_VALUE, ExposureKind.AMBIENT),
                db.minutes(from, Long.MAX_VALUE, ExposureKind.HEADPHONE),
                db.alerts(),
                now,
            )
        }
    }

    /** Permissions and the phone-only sampling switch (changed outside the app too). */
    fun refreshLocal() {
        fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        _state.update {
            it.copy(
                phoneSampling = PhonePrefs.enabled(ctx),
                lastListeningSentMs = PhonePrefs.lastSyncMs(ctx),
                notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS),
                bluetoothGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(Manifest.permission.BLUETOOTH_CONNECT),
            )
        }
    }

    /** Back in the foreground: refresh local state, and sync when the last one is not recent. */
    fun onResume() {
        refreshLocal()
        tick.value = System.currentTimeMillis()
        val last = _state.value.lastSyncMs ?: 0L
        if (System.currentTimeMillis() - last > 10 * 60_000L) refresh(quiet = true)
    }

    fun refresh(quiet: Boolean = false) {
        viewModelScope.launch {
            val ok = sync.sync()
            runCatching { DoseWidget.refresh(ctx) }
            if (!quiet) _events.tryEmit(PhoneEvent.Message(if (ok) R.string.sync_done_toast else R.string.sync_failed_toast))
            askStatus()
        }
    }

    // ------------------------------------------------------------------ Tono

    private fun localizedRes() = PhoneLocale.localized(ctx, PhonePrefs.language(ctx)).resources

    private suspend fun askStatus() {
        if (!_state.value.settings.tonoEnabled || _state.value.noData) return
        ask(CoachTask.STATUS, null, automatic = true, record = false)
    }

    private suspend fun ask(task: CoachTask, question: String?, automatic: Boolean, record: Boolean) {
        val s = _state.value
        // Tono off: only a symptom question is still answered (with the fixed safe answer).
        if (!s.settings.tonoEnabled && !(task == CoachTask.ASK && SymptomGuard.detect(question))) return
        val locale = PhoneLocale.resolve(ctx, PhonePrefs.language(ctx)).language
        val ui = tono.ask(
            res = localizedRes(), locale = locale, task = task, question = question,
            inputs = phoneSnapshotInputs(s.status, s.history, s.head, s.settings.sensitiveListener, System.currentTimeMillis()),
            consent = s.settings.cloudAi, automatic = automatic,
        )
        if (record) ui.reply?.let { r -> chat.update { (it + ChatTurn(question, r, ui.source, ui.cloudFailed)).takeLast(30) } }
    }

    // ------------------------------------------------------------------ actions

    val actions = PhoneActions(
        onRefresh = { refresh() },
        onSetting = { field, value ->
            viewModelScope.launch {
                sync.changeSetting(field, value)
                if (field == SettingField.TONO_ENABLED && value == "true") askStatus()
            }
        },
        onPhoneSampling = { on ->
            PhonePrefs.setEnabled(ctx, on)
            if (on) {
                ListeningSampler.schedule(ctx)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !_state.value.bluetoothGranted) _events.tryEmit(PhoneEvent.RequestBluetooth)
            } else {
                ListeningSampler.cancel(ctx)
            }
            refreshLocal()
        },
        onAsk = { task, question -> viewModelScope.launch { ask(task, question, automatic = false, record = true) } },
        onOpenOnWatch = { open(WearContract.LINK_OPEN) },
        onOpenCalibration = { open(WearContract.LINK_CALIBRATION) },
        onInstallOnWatch = { nodeId ->
            viewModelScope.launch {
                val ok = sync.openOnWatch(WearContract.STORE_URI, nodeId)
                _events.tryEmit(PhoneEvent.Message(if (ok) R.string.link_opened else R.string.link_store_failed))
            }
        },
        onChooseWatch = { id -> viewModelScope.launch { sync.chooseWatch(id); refresh(quiet = true) } },
        onOpenWearableApp = { _events.tryEmit(PhoneEvent.OpenWearableApp) },
        onRequestNotifications = { _events.tryEmit(PhoneEvent.RequestNotifications) },
        onRequestBluetooth = { _events.tryEmit(PhoneEvent.RequestBluetooth) },
        onExport = { _events.tryEmit(PhoneEvent.Export) },
        onDeleteAll = {
            viewModelScope.launch {
                val r = sync.deleteAll()
                chat.value = emptyList()
                _events.tryEmit(
                    PhoneEvent.Message(
                        if (r == SyncCoordinator.DeleteResult.DELETED_EVERYWHERE) R.string.deleted_everywhere else R.string.deleted_phone_only
                    )
                )
            }
        },
        onRetention = { days -> viewModelScope.launch { store.setRetentionDays(days); withContext(Dispatchers.IO) { db.prune(days, System.currentTimeMillis()) } } },
        onMirrorAlerts = { on ->
            viewModelScope.launch { store.setMirrorAlerts(on) }
            if (on && !_state.value.notificationsGranted) _events.tryEmit(PhoneEvent.RequestNotifications)
        },
        onFinishSetup = { viewModelScope.launch { store.setSetupDone(true); refresh(quiet = true) } },
        onRunSetupAgain = { viewModelScope.launch { store.setSetupDone(false) } },
        // Appearance: stored on the phone only; the widget is re-rendered in the new colours.
        onAccent = { a -> viewModelScope.launch { store.setAccent(a); runCatching { DoseWidget.refresh(ctx) } } },
        onThemeMode = { m -> viewModelScope.launch { store.setThemeMode(m); runCatching { DoseWidget.refresh(ctx) } } },
    )

    private fun open(uri: String) {
        viewModelScope.launch {
            val ok = sync.openOnWatch(uri)
            _events.tryEmit(PhoneEvent.Message(if (ok) R.string.link_opened else R.string.link_open_failed))
        }
    }

    /** Used by the share sheet. */
    fun exportIntent() = com.example.voxara.phone.export.CsvExport.shareIntent(ctx, db)

    private data class Sources(
        val store: PhoneStoreState,
        val link: LinkState,
        val syncing: Boolean,
        val coach: CoachUi,
        val chat: List<ChatTurn>,
    )
}
