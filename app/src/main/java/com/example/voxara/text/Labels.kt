package com.example.voxara.text

import android.content.Context
import androidx.annotation.StringRes
import com.example.voxara.R
import com.example.voxara.core.advice.Advice
import com.example.voxara.core.advice.AdviceKind
import com.example.voxara.core.risk.RiskState
import com.example.voxara.core.scene.EnvBand
import com.example.voxara.core.scene.Scene
import com.example.voxara.core.scene.environmentBand
import com.example.voxara.data.AppLanguage
import com.example.voxara.data.AppMode
import com.example.voxara.data.Scenario

/**
 * The bridge between the language-agnostic core and the words the user hears or reads. The core
 * emits typed outcomes; every display string is resolved here, so Spanish and English differ in
 * resources alone and never in logic.
 */

@StringRes
fun Scene.labelRes(): Int = when (this) {
    Scene.TRAFFIC -> R.string.scene_traffic
    Scene.TRANSIT -> R.string.scene_transit
    Scene.LIVE_MUSIC -> R.string.scene_live_music
    Scene.MACHINERY -> R.string.scene_machinery
    Scene.CONVERSATION -> R.string.scene_conversation
    Scene.QUIET_INDOOR -> R.string.scene_quiet_indoor
    Scene.UNKNOWN -> R.string.scene_unknown
}

@StringRes
fun EnvBand.labelRes(): Int = when (this) {
    EnvBand.QUIET_ROOM -> R.string.env_quiet_room
    EnvBand.OFFICE -> R.string.env_office
    EnvBand.CONVERSATION -> R.string.env_conversation
    EnvBand.CITY_TRAFFIC -> R.string.env_city_traffic
    EnvBand.LIVE_MUSIC -> R.string.env_live_music
    EnvBand.NIGHTCLUB -> R.string.env_nightclub
    EnvBand.POWER_TOOL -> R.string.env_power_tool
}

@StringRes
fun RiskState.labelRes(): Int = when (this) {
    RiskState.CALM -> R.string.risk_calm
    RiskState.ACCRUING -> R.string.risk_accruing
    RiskState.HAZARD -> R.string.risk_hazard
    RiskState.CRITICAL -> R.string.risk_critical
    RiskState.RECOVER -> R.string.risk_recover
}

/** The level-only fallback band for a reading, as a string resource. */
@StringRes
fun environmentLabelRes(dba: Double): Int = environmentBand(dba).labelRes()

fun Context.environmentLabel(dba: Double): String = getString(environmentLabelRes(dba))

/**
 * What the user is told they are in: the classifier's scene when it is confident, otherwise the
 * level-only band. An unlabelled reading is better than a wrong label.
 */
@StringRes
fun sceneOrBandLabelRes(scene: Scene, dba: Double): Int =
    if (scene == Scene.UNKNOWN) environmentLabelRes(dba) else scene.labelRes()

@StringRes
fun AdviceKind.textRes(): Int = when (this) {
    AdviceKind.FINE -> R.string.advice_fine
    AdviceKind.COMFORTABLE -> R.string.advice_comfortable
    AdviceKind.CRITICAL_MUSIC -> R.string.advice_critical_music
    AdviceKind.CRITICAL_MACHINERY -> R.string.advice_critical_machinery
    AdviceKind.CRITICAL_GENERIC -> R.string.advice_critical_generic
    AdviceKind.HAZARD_MUSIC -> R.string.advice_hazard_music
    AdviceKind.HAZARD_MACHINERY -> R.string.advice_hazard_machinery
    AdviceKind.HAZARD_TRANSIT -> R.string.advice_hazard_transit
    AdviceKind.HAZARD_TRAFFIC -> R.string.advice_hazard_traffic
    AdviceKind.HAZARD_GENERIC -> R.string.advice_hazard_generic
    AdviceKind.ACCRUING_TRAFFIC -> R.string.advice_accruing_traffic
    AdviceKind.ACCRUING_TRANSIT -> R.string.advice_accruing_transit
    AdviceKind.ACCRUING_MUSIC -> R.string.advice_accruing_music
    AdviceKind.ACCRUING_MACHINERY -> R.string.advice_accruing_machinery
    AdviceKind.ACCRUING_GENERIC -> R.string.advice_accruing_generic
    AdviceKind.RECOVER -> R.string.advice_recover
    AdviceKind.CALM -> R.string.advice_calm
}

/** The advice sentence, in the language currently in force on this context. */
fun Context.render(advice: Advice): String =
    getString(advice.kind.textRes(), *advice.args.toTypedArray())

@StringRes
fun AppMode.titleRes(): Int = when (this) {
    AppMode.CONCERT -> R.string.mode_concert_title
    AppMode.URBAN -> R.string.mode_urban_title
    AppMode.VOICE -> R.string.mode_voice_title
}

@StringRes
fun AppMode.indexRes(): Int = when (this) {
    AppMode.CONCERT -> R.string.mode_concert_index
    AppMode.URBAN -> R.string.mode_urban_index
    AppMode.VOICE -> R.string.mode_voice_index
}

@StringRes
fun AppMode.blurbRes(): Int = when (this) {
    AppMode.CONCERT -> R.string.mode_concert_blurb
    AppMode.URBAN -> R.string.mode_urban_blurb
    AppMode.VOICE -> R.string.mode_voice_blurb
}

@StringRes
fun Scenario.labelRes(): Int = when (this) {
    Scenario.OFFICE -> R.string.scenario_office
    Scenario.CAFE -> R.string.scenario_cafe
    Scenario.TRAFFIC -> R.string.scenario_traffic
    Scenario.CLUB -> R.string.scenario_club
    Scenario.JACKHAMMER -> R.string.scenario_jackhammer
}

@StringRes
fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.SPANISH -> R.string.language_spanish
}
