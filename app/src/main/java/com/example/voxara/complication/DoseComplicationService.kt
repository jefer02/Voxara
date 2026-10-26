package com.example.voxara.complication

import android.content.ComponentName
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.example.voxara.R
import com.example.voxara.presentation.MainActivity
import com.example.voxara.ui.PageLinks
import com.example.voxara.core.format.formatHeadroom
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.LocaleStore
import com.example.voxara.data.VoxaraStore
import com.example.voxara.tile.headroomMinutes
import com.example.voxara.tile.tintFor
import com.example.voxara.tile.zoneFor
import com.example.voxara.tile.zoneWordRes
import com.example.voxara.core.design.Accent
import com.example.voxara.core.design.Palette
import kotlin.math.roundToInt

/**
 * COMPLICATION — RANGED_VALUE + SHORT_TEXT. One arc, one integer, one colour, legible at 38 dp
 * and tinted by risk band. Reads the cached ledger; it never wakes the microphone.
 */
class DoseComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, dose = 46.0, dba = 88.0)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val p = VoxaraStore(this).read()
        return build(request.complicationType, p.dosePercent, p.lastDba)
    }

    private fun build(type: ComplicationType, dose: Double, dba: Double): ComplicationData? {
        val pct = dose.roundToInt()
        val tint = tintFor(dba, dose)
        // Created by the watch face, not by the app: it has to resolve the language itself.
        val res = LocaleStore.localized(this)
        // Headphones mode is part of what the wearer hears from TalkBack; the sound around them is still what this shows.
        val headphonesMode = ExposureRepository.serviceActive && ExposureRepository.state.value.listening.active
        val description = PlainComplicationText.Builder(
            res.getString(R.string.complication_cd, pct) +
                if (headphonesMode) " " + res.getString(R.string.complication_headphones_cd) else ""
        ).build()

        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = dose.coerceIn(0.0, 100.0).toFloat(),
                min = 0f,
                max = 100f,
                contentDescription = description,
            )
                .setText(
                    PlainComplicationText.Builder(
                        res.getString(R.string.tile_dose_value, pct)
                    ).build()
                )
                .setTitle(
                    PlainComplicationText.Builder(
                        res.getString(R.string.label_dose)
                    ).build()
                )
                .setMonochromaticImage(
                    MonochromaticImage.Builder(
                        Icon.createWithResource(this, R.drawable.ic_voxara_status)
                    ).build()
                )
                // From the watch accent to the tier colour; the text says the value in words too.
                .setColorRamp(ColorRamp(intArrayOf(Palette.accent(Accent.PURPLE, dark = true).fill.toInt(), tint), true))
                .setTapAction(tap())
                .build()

            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(
                    res.getString(R.string.tile_dose_value, pct)
                ).build(),
                contentDescription = description,
            )
                .setTitle(
                    PlainComplicationText.Builder(
                        if (dba < 80.0) res.getString(zoneWordRes(zoneFor(dba, dose)))
                        else formatHeadroom(headroomMinutes(dba, dose))
                    ).build()
                )
                .setTapAction(tap())
                .build()

            else -> null
        }
    }

    /** A tap opens the app on the page this complication is about (Dose, Today). */
    private fun tap(): PendingIntent = PendingIntent.getActivity(
        this, 1, Intent(this, MainActivity::class.java).putExtra(PageLinks.EXTRA_PAGE, "today"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        fun requestRefresh(context: Context) {
            runCatching {
                ComplicationDataSourceUpdateRequester
                    .create(
                        context,
                        ComponentName(context, DoseComplicationService::class.java),
                    )
                    .requestUpdateAll()
            }
        }
    }
}
