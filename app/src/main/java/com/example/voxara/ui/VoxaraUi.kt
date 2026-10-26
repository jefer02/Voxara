package com.example.voxara.ui

import com.example.voxara.ai.CoachUi
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.calibration.CalibrationRecord
import com.example.voxara.core.calibration.MicSource
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.data.AppLanguage
import com.example.voxara.data.ExposureState
import com.example.voxara.data.Scenario
import com.example.voxara.voice.VoiceTurn

/**
 * Navigation routes. Home (the pager of three glance pages: Now, Dose, More) is the root. The routes
 * [NOW], [TODAY], [WEEK], [HEADPHONES] and [MENU] open that pager on a page and view: Now (also [HOME]),
 * Dose on Today, Dose on Week (also [HEADPHONES], whose weekly share lives there) and More.
 */
object Routes {
    const val HOME = "home"
    const val NOW = "now"
    const val HEADPHONES = "headphones"
    const val TODAY = "today"
    const val WEEK = "week"
    const val MENU = "menu"
    const val ASK = "ask"
    const val SETTINGS = "settings"
    const val CALIBRATION = "calibration"
    const val MODE = "mode"
    /** What the Dose ring means (a tap on its number). */
    const val DOSE_HELP = "dose_help"
    /** "Not headphones": the way out of headphones mode (its chip on Now, or the row under it). */
    const val HP_EXIT = "hp_exit"
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
    /** The wearer picked Auto or a fixed situation mode on the watch. */
    val onSituationChoice: (SituationChoice) -> Unit = {},
    /** "Dismiss" on the headphone-volume alert: closes it for this tier, never switches alerts off. */
    val onDismissHeadphoneAlert: () -> Unit = {},
    /** "Lower to safe volume": brings the music volume to the recommended level (watch or phone headphones). */
    val onLowerHeadphones: () -> Unit = {},
    /**
     * "Not headphones": headphones mode leaves and ignores the current output until it disconnects; with
     * `rememberSpeaker` a Bluetooth device is remembered as a speaker. The room's alerts are never affected.
     */
    val onNotHeadphones: (rememberSpeaker: Boolean) -> Unit = {},
    /** Forgets every output remembered as a speaker. */
    val onForgetSpeakers: () -> Unit = {},
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
    /** Tono on the watch on/off. Off hides Tono; voice commands keep working. */
    val onTonoEnabled: (Boolean) -> Unit = {},
    val onAsk: (CoachTask, String?) -> Unit = { _, _ -> },
    val onVoiceArm: () -> Unit = {},
    val onLanguage: (AppLanguage) -> Unit = {},
    val onOpenBatterySettings: () -> Unit = {},
    val onFinishOnboarding: () -> Unit = {},
)
