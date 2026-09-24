package com.example.voxara.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.AppScaffold
import com.example.voxara.BuildConfig
import com.example.voxara.ai.Tono
import com.example.voxara.audio.DeviceAudioInfo
import com.example.voxara.audio.PowerPolicy
import com.example.voxara.bench.Bench
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.ai.SymptomGuard
import com.example.voxara.core.calibration.CalibrationRecord
import com.example.voxara.core.calibration.ReferenceType
import com.example.voxara.core.calibration.SOURCE_OFFSET_RANGE
import com.example.voxara.core.calibration.TRIM_RANGE
import com.example.voxara.core.calibration.offsetFromReference
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.LedgerBook
import com.example.voxara.core.ledger.localEpochDay
import com.example.voxara.core.monitoring.monitoringStatus
import com.example.voxara.data.AppLanguage
import com.example.voxara.data.AppMode
import com.example.voxara.data.ExposureDb
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.LocaleStore
import com.example.voxara.service.DosimeterService
import com.example.voxara.service.MidnightRollupWorker
import com.example.voxara.service.MonitoringWatchdogWorker
import com.example.voxara.service.Notifications
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.VoxaraApp
import com.example.voxara.ui.theme.VoxaraTheme
import com.example.voxara.voice.VoiceTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val ambient = mutableStateOf(false)
    private lateinit var voice: VoiceTurn

    /** The language the resources below were inflated with; a change to it re-creates us. */
    private var language = AppLanguage.SYSTEM

    /** First run: onboarding asks for each permission in context; nothing is asked at launch. */
    private val onboarded = mutableStateOf(true)

    /** Bumped after any permission result so the UI re-reads the grants. */
    private val permissionTick = mutableStateOf(0)

    /**
     * Every resource this activity resolves - and so every word the wearer reads - comes from
     * the locale chosen here, before the first view exists. Wear has no AppCompat delegate to
     * do it for us and minSdk 30 predates the per-app locale API, so we wrap the base context.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleStore.localized(newBase))
    }

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            ambient.value = true
        }

        override fun onExitAmbient() {
            ambient.value = false
        }

        override fun onUpdateAmbient() = Unit
    }

    /** One launcher per permission, each asked in context with its reason on screen. */
    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionTick.value++
        if (it) resumeIfEnabled()
    }
    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionTick.value++
    }

    /** BLUETOOTH_CONNECT: only to tell a Bluetooth speaker or car kit from headphones. */
    private val bluetoothLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionTick.value++
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun needsBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !granted(Manifest.permission.BLUETOOTH_CONNECT)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        ExposureRepository.init(this)
        language = LocaleStore.read(this)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))
        MidnightRollupWorker.schedule(this)
        MonitoringWatchdogWorker.schedule(this)

        voice = VoiceTurn(this).also { turn ->
            turn.prepare()
            turn.onModeRequest = { mode ->
                // The mode change is already applied when the sentence starts.
                DosimeterService.send(
                    this,
                    if (mode == "CONCERT") DosimeterService.ACTION_CONCERT else DosimeterService.ACTION_URBAN,
                )
            }
            // Symptoms always get the fixed safe answer; with cloud consent Tono answers the rest;
            // otherwise the deterministic on-device intents reply.
            turn.onHeard = { heard ->
                val state = ExposureRepository.state.value
                val toTono = SymptomGuard.detect(heard) || (state.aiConsent && Tono.cloudAvailable)
                if (toTono) {
                    lifecycleScope.launch {
                        val ui = Tono.ask(this@MainActivity, ExposureRepository.state.value, CoachTask.ASK, question = heard)
                        ui.reply?.let { voice.speak("${it.insight} ${it.suggestion}") }
                    }
                }
                toTono
            }
        }

        lifecycleScope.launch {
            val p = ExposureRepository.store.read()
            ExposureRepository.hydrate(p)
            onboarded.value = p.onboarded
            // After onboarding, the app just resumes monitoring; permissions were asked in context.
            if (p.onboarded) resumeIfEnabled()
        }
        // Debug builds: display-only bench driver (src/debug). Release: a no-op (src/release).
        lifecycleScope.launch { Bench.run(this@MainActivity) }

        setContent { VoxaraRoot() }
    }

    override fun onResume() {
        super.onResume()
        permissionTick.value++
        refreshHealth()
        // Changed from the settings panel - or, in principle, from another surface.
        if (LocaleStore.read(this) != language) recreate()
    }

    override fun onDestroy() {
        voice.release()
        super.onDestroy()
    }

    /**
     * The guided calibration's result becomes the offset for that capture path. The fine trim is
     * reset, because the calibration already makes the watch agree with the reference.
     */
    private fun saveCalibration(record: CalibrationRecord) {
        ExposureRepository.update {
            it.copy(calibrations = it.calibrations + (record.source to record), calibrationOffsetDb = 0.0)
        }
        lifecycleScope.launch {
            ExposureRepository.store.saveCalibration(record)
            ExposureRepository.store.setCalibration(0.0)
        }
    }

    /**
     * Opens the system battery-optimisation list when this watch provides one. OEM builds may
     * not (not verified on One UI Watch), so the UI only shows the button when it resolves.
     */
    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (intent.resolveActivity(packageManager) != null) runCatching { startActivity(intent) }
    }

    /** Status the UI shows: running, paused (enabled but not running) or off. */
    private fun refreshHealth() {
        lifecycleScope.launch {
            val p = ExposureRepository.store.read()
            val status = monitoringStatus(
                enabled = p.monitoringEnabled,
                serviceActive = ExposureRepository.serviceActive,
                lastHeartbeatMs = p.heartbeatMs,
                nowMs = System.currentTimeMillis(),
            )
            val batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            ExposureRepository.update {
                it.copy(
                    monitoringStatus = status,
                    device = DeviceAudioInfo.read(this@MainActivity),
                    power = PowerPolicy.read(this@MainActivity),
                    batterySettingsAvailable = batteryIntent.resolveActivity(packageManager) != null,
                )
            }
        }
    }

    /** The system "remove animations" setting (animator duration scale 0). */
    private fun reduceMotion(): Boolean = runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    private fun setLanguage(choice: AppLanguage) {
        if (choice == language) return
        LocaleStore.set(this, choice)
        voice.release()
        recreate()
    }

    /**
     * Starting from the visible activity is what Android 14+ requires for a microphone
     * foreground service. Honours the wearer's last on/off choice.
     */
    private fun resumeIfEnabled() {
        lifecycleScope.launch {
            if (ExposureRepository.store.read().monitoringEnabled && DosimeterService.hasMic(this@MainActivity)) {
                Notifications.cancelResumePrompt(this@MainActivity)
                DosimeterService.start(this@MainActivity)
            }
        }
    }

    private fun ask(task: CoachTask, question: String?) {
        // Home's automatic status refresh must not wipe a voice answer the wearer is reading.
        if (task != CoachTask.STATUS) voice.clear()
        lifecycleScope.launch {
            Tono.ask(
                this@MainActivity, ExposureRepository.state.value, task,
                question = question, automatic = task == CoachTask.STATUS,
            )
        }
    }

    private fun actions() = VoxaraActions(
        onMonitoring = { on ->
            if (on) DosimeterService.start(this) else DosimeterService.stop(this)
            lifecycleScope.launch { ExposureRepository.store.setMonitoring(on) }
        },
        onResume = {
            lifecycleScope.launch { ExposureRepository.store.setMonitoring(true) }
            if (DosimeterService.hasMic(this)) DosimeterService.start(this)
            else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        onConcertToggle = {
            val next = if (ExposureRepository.state.value.mode == AppMode.CONCERT) AppMode.URBAN else AppMode.CONCERT
            ExposureRepository.setMode(next)
            lifecycleScope.launch { ExposureRepository.store.setMode(next.name) }
            DosimeterService.send(
                this,
                if (next == AppMode.CONCERT) DosimeterService.ACTION_CONCERT else DosimeterService.ACTION_URBAN,
            )
        },
        onCalibrationTrim = { offset ->
            val clamped = offset.coerceIn(TRIM_RANGE.start, TRIM_RANGE.endInclusive)
            ExposureRepository.update { it.copy(calibrationOffsetDb = clamped) }
            lifecycleScope.launch { ExposureRepository.store.setCalibration(clamped) }
        },
        onSaveCalibration = ::saveCalibration,
        onEnsureMonitoring = {
            lifecycleScope.launch { ExposureRepository.store.setMonitoring(true) }
            DosimeterService.start(this)
        },
        onProbe = { source -> ExposureRepository.calibrationProbe.value = source },
        onSaveSourceOffset = save@{ source, referenceDba, dbfsA ->
            val offset = offsetFromReference(referenceDba, dbfsA)
            if (offset !in SOURCE_OFFSET_RANGE) return@save false
            saveCalibration(
                CalibrationRecord(
                    source = source,
                    offsetDb = offset,
                    calibratedAtMs = System.currentTimeMillis(),
                    deviceModel = ExposureRepository.state.value.device?.deviceKey.orEmpty(),
                    referenceType = ReferenceType.SOUND_LEVEL_METER,
                )
            )
            true
        },
        onScenario = { s -> Bench.target = s.dba },
        onResetDose = {
            ExposureRepository.resetDay()
            lifecycleScope.launch {
                ExposureRepository.store.resetDose()
                // With the dosimeter running it clears today's minutes itself.
                if (!ExposureRepository.serviceActive) {
                    withContext(Dispatchers.IO) {
                        val now = System.currentTimeMillis()
                        LedgerBook(ExposureKind.AMBIENT, ExposureDb.get(this@MainActivity)).clearDay(localEpochDay(now), now)
                    }
                }
            }
        },
        onAlertSettings = { s ->
            ExposureRepository.update { it.copy(alertSettings = s) }
            lifecycleScope.launch { ExposureRepository.store.saveAlertSettings(s) }
        },
        onSensitive = { on ->
            ExposureRepository.update { it.copy(sensitiveListener = on) }
            lifecycleScope.launch { ExposureRepository.store.setSensitive(on) }
        },
        onUsualHeadphones = { c ->
            ExposureRepository.update { it.copy(usualHeadphones = c) }
            lifecycleScope.launch { ExposureRepository.store.setUsualHeadphones(c) }
        },
        onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onRequestNotifications = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onRequestBluetooth = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        },
        onAiConsent = { on ->
            ExposureRepository.update { it.copy(aiConsent = on) }
            lifecycleScope.launch { ExposureRepository.store.setAiConsent(on) }
        },
        onAsk = ::ask,
        onVoiceArm = {
            if (voice.turn.value.phase == VoiceTurn.Phase.LISTENING) voice.stop()
            else voice.start(ExposureRepository.state.value)
        },
        onLanguage = ::setLanguage,
        onOpenBatterySettings = ::openBatterySettings,
        onFinishOnboarding = {
            onboarded.value = true
            lifecycleScope.launch {
                ExposureRepository.store.setOnboarded(true)
                ExposureRepository.store.setMonitoring(true)
            }
            resumeIfEnabled()
        },
    )

    @Composable
    private fun VoxaraRoot() {
        val state by ExposureRepository.state.collectAsState()
        val turn by voice.turn.collectAsState()
        val coach by Tono.ui.collectAsState()
        val isAmbient by ambient
        val isOnboarded by onboarded
        // Read so a permission result recomposes with the new grants.
        @Suppress("UNUSED_VARIABLE") val tick by permissionTick

        val env = UiEnv(
            state = state,
            coach = coach,
            turn = turn,
            language = language,
            cloudAvailable = Tono.cloudAvailable,
            micGranted = granted(Manifest.permission.RECORD_AUDIO),
            notificationsGranted = granted(Manifest.permission.POST_NOTIFICATIONS),
            bluetoothNeeded = needsBluetoothPermission(),
            onboarded = isOnboarded,
            debug = BuildConfig.DEBUG,
        )
        VoxaraTheme(ambient = isAmbient, reduceMotion = reduceMotion()) {
            AppScaffold {
                VoxaraApp(env = env, actions = actions(), ambient = isAmbient)
            }
        }
    }
}
