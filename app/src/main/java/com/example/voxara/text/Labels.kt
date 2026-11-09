package com.example.voxara.text

import android.content.Context
import androidx.annotation.StringRes
import com.example.voxara.R
import com.example.voxara.core.advice.Advice
import com.example.voxara.core.advice.AdviceKind
import com.example.voxara.core.risk.RiskState
import com.example.voxara.data.AppLanguage
import com.example.voxara.data.Scenario

/**
 * The bridge between the language-agnostic core and the words the user hears or reads. The core
 * emits typed outcomes; every display string is resolved here, so Spanish and English differ in
 * resources alone and never in logic.
 */

@StringRes
fun RiskState.labelRes(): Int = when (this) {
    RiskState.CALM -> R.string.risk_calm
    RiskState.ACCRUING -> R.string.risk_accruing
    RiskState.HAZARD -> R.string.risk_hazard
    RiskState.CRITICAL -> R.string.risk_critical
    RiskState.RECOVER -> R.string.risk_recover
}

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
