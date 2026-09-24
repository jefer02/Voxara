package com.example.voxara.ui

import com.example.voxara.ai.CoachUi
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.calibration.CalibrationRecord
import com.example.voxara.core.calibration.MicSource
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.data.AppLanguage
import com.example.voxara.data.ExposureState
import com.example.voxara.data.Scenario
import com.example.voxara.voice.VoiceTurn

/** Navigation routes. Home (Tono's status + cards) is the root; everything else is one tap away. */
object Routes {
    const val HOME = "home"
    const val NOW = "now"
    const val HEADPHONES = "headphones"
    const val TODAY = "today"
    const val WEEK = "week"
    const val ASK = "ask"
    const val SETTINGS = "settings"
    const val CALIBRATION = "calibration"
}

/** Everything a screen may read. */
data class UiEnv(
    val state: ExposureState,
    val coach: CoachUi = CoachUi(),
    val turn: VoiceTurn.Turn = VoiceTurn.Turn(),
    val language: AppLanguage = AppLanguage.SYSTEM,
    val cloudAvailable: Boolean = false,
    val micGranted: Boolean = true,
    val notificationsGranted: Boolean = true,
    /** BLUETOOTH_CONNECT still needed (API 31+ and not granted). */
    val bluetoothNeeded: Boolean = false,
    val onboarded: Boolean = true,
    val debug: Boolean = false,
)

/** Everything a screen may do. Screens never talk to services or stores directly. */
data class VoxaraActions(
    val onMonitoring: (Boolean) -> Unit = {},
    val onResume: () -> Unit = {},
    val onConcertToggle: () -> Unit = {},
    val onCalibrationTrim: (Double) -> Unit = {},
    val onSaveCalibration: (CalibrationRecord) -> Unit = {},
    val onEnsureMonitoring: () -> Unit = {},
    val onProbe: (MicSource?) -> Unit = {},
    val onSaveSourceOffset: (MicSource, Double, Double) -> Boolean = { _, _, _ -> false },
    val onScenario: (Scenario) -> Unit = {},
    val onResetDose: () -> Unit = {},
    val onAlertSettings: (AlertSettings) -> Unit = {},
    val onSensitive: (Boolean) -> Unit = {},
    val onUsualHeadphones: (HeadphoneCategory?) -> Unit = {},
    val onRequestMic: () -> Unit = {},
    val onRequestNotifications: () -> Unit = {},
    val onRequestBluetooth: () -> Unit = {},
    val onAiConsent: (Boolean) -> Unit = {},
    val onAsk: (CoachTask, String?) -> Unit = { _, _ -> },
    val onVoiceArm: () -> Unit = {},
    val onLanguage: (AppLanguage) -> Unit = {},
    val onOpenBatterySettings: () -> Unit = {},
    val onFinishOnboarding: () -> Unit = {},
)
