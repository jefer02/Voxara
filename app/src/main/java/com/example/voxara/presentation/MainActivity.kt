package com.example.voxara.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.AppScaffold
import com.example.voxara.data.AppMode
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.Scenario
import com.example.voxara.service.DosimeterService
import com.example.voxara.service.MidnightRollupWorker
import com.example.voxara.ui.VoxaraApp
import com.example.voxara.ui.theme.VoxaraTheme
import com.example.voxara.voice.VoiceTurn
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val ambient = mutableStateOf(false)
    private lateinit var voice: VoiceTurn

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            ambient.value = true
        }

        override fun onExitAmbient() {
            ambient.value = false
        }

        override fun onUpdateAmbient() = Unit
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { DosimeterService.start(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        ExposureRepository.init(this)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))
        MidnightRollupWorker.schedule(this)

        voice = VoiceTurn(this).also { turn ->
            turn.prepare()
            turn.onModeRequest = { mode ->
                // The mode change is already applied when the sentence starts.
                DosimeterService.send(
                    this,
                    if (mode == "CONCERT") DosimeterService.ACTION_CONCERT
                    else DosimeterService.ACTION_URBAN,
                )
            }
        }

        lifecycleScope.launch {
            ExposureRepository.hydrate(ExposureRepository.store.read())
            requestWhatWeNeed()
        }

        setContent { VoxaraRoot(voice) }
    }

    override fun onDestroy() {
        voice.release()
        super.onDestroy()
    }

    private fun requestWhatWeNeed() {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.BODY_SENSORS)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isEmpty()) DosimeterService.start(this)
        else permissionLauncher.launch(wanted.toTypedArray())
    }

    @Composable
    private fun VoxaraRoot(voice: VoiceTurn) {
        val context = LocalContext.current
        val state by ExposureRepository.state.collectAsState()
        val turn by voice.turn.collectAsState()
        val isAmbient by ambient

        VoxaraTheme(ambient = isAmbient) {
            AppScaffold {
                VoxaraApp(
                    state = state,
                    turn = turn,
                    ambient = isAmbient,
                    onMode = { mode ->
                        ExposureRepository.setMode(mode)
                        lifecycleScope.launch { ExposureRepository.store.setMode(mode.name) }
                        when (mode) {
                            AppMode.CONCERT ->
                                DosimeterService.send(context, DosimeterService.ACTION_CONCERT)
                            else ->
                                DosimeterService.send(context, DosimeterService.ACTION_URBAN)
                        }
                    },
                    onMonitoring = { on ->
                        if (on) DosimeterService.start(context) else DosimeterService.stop(context)
                        lifecycleScope.launch { ExposureRepository.store.setMonitoring(on) }
                    },
                    onCalibration = { offset ->
                        val clamped = offset.coerceIn(-24.0, 24.0)
                        ExposureRepository.update { it.copy(calibrationOffsetDb = clamped) }
                        lifecycleScope.launch { ExposureRepository.store.setCalibration(clamped) }
                    },
                    onScenario = { s: Scenario ->
                        DosimeterService.send(
                            context, DosimeterService.ACTION_SET_SIM, simDba = s.dba,
                        )
                    },
                    onResetDose = {
                        ExposureRepository.engine.resetDay()
                        ExposureRepository.update {
                            it.copy(dosePercent = 0.0, twaDba = null, peakDbc = 0.0)
                        }
                        lifecycleScope.launch { ExposureRepository.store.resetDose() }
                    },
                    onConcertToggle = {
                        val next = if (state.mode == AppMode.CONCERT) AppMode.URBAN
                        else AppMode.CONCERT
                        ExposureRepository.setMode(next)
                        DosimeterService.send(
                            context,
                            if (next == AppMode.CONCERT) DosimeterService.ACTION_CONCERT
                            else DosimeterService.ACTION_URBAN,
                        )
                    },
                    onVoiceArm = {
                        if (turn.phase == VoiceTurn.Phase.LISTENING) voice.stop()
                        else voice.start(state)
                    },
                )
            }
        }
    }
}
