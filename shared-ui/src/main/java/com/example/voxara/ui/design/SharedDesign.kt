package com.example.voxara.ui.design

import androidx.annotation.StringRes
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.designsystem.R

/** One weight of a bundled variable font, chosen by the weight axis. */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: FontWeight) = Font(
    res,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Plus Jakarta Sans (SIL OFL 1.1, bundled): every piece of UI text. */
val JakartaFamily = FontFamily(
    variable(R.font.plus_jakarta_sans, FontWeight.Normal),
    variable(R.font.plus_jakarta_sans, FontWeight.Medium),
    variable(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    variable(R.font.plus_jakarta_sans, FontWeight.Bold),
    variable(R.font.plus_jakarta_sans, FontWeight.ExtraBold),
)

/**
 * JetBrains Mono (SIL OFL 1.1, bundled): every dB, %, time and counter, always with tabular
 * figures ([TABULAR]) so a changing number never jitters.
 */
val MonoFamily = FontFamily(
    variable(R.font.jetbrains_mono, FontWeight.Medium),
    variable(R.font.jetbrains_mono, FontWeight.SemiBold),
    variable(R.font.jetbrains_mono, FontWeight.Bold),
    variable(R.font.jetbrains_mono, FontWeight.ExtraBold),
)

/** OpenType feature string for tabular numerals. */
const val TABULAR = "tnum"

/** The zone's word. Every surface pairs it with [VoxIcons.zone], so colour never carries a zone alone. */
@StringRes
fun RiskZone.wordRes(): Int = when (this) {
    RiskZone.OK -> R.string.zone_ok
    RiskZone.MODERATE -> R.string.zone_moderate
    RiskZone.LOUD -> R.string.zone_loud
    RiskZone.DANGEROUS -> R.string.zone_dangerous
}

/** The situation mode's name (watch and phone). */
@StringRes
fun SituationMode.labelRes(): Int = when (this) {
    SituationMode.QUIET -> R.string.situation_quiet
    SituationMode.STREET -> R.string.situation_street
    SituationMode.TRANSPORT -> R.string.situation_transport
    SituationMode.CONCERT -> R.string.situation_concert
    SituationMode.WORK -> R.string.situation_work
}

@StringRes
fun SituationChoice.labelRes(): Int = fixed?.labelRes() ?: R.string.situation_auto

/** One line on what the choice changes. */
@StringRes
fun SituationChoice.blurbRes(): Int = when (this) {
    SituationChoice.AUTO -> R.string.situation_auto_blurb
    SituationChoice.QUIET -> R.string.situation_quiet_blurb
    SituationChoice.STREET -> R.string.situation_street_blurb
    SituationChoice.TRANSPORT -> R.string.situation_transport_blurb
    SituationChoice.CONCERT -> R.string.situation_concert_blurb
    SituationChoice.WORK -> R.string.situation_work_blurb
}
