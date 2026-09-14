package com.example.voxara.complication

import android.content.ComponentName
import android.content.Context
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
import com.example.voxara.core.format.formatHeadroom
import com.example.voxara.data.VoxaraStore
import com.example.voxara.tile.headroomMinutes
import com.example.voxara.tile.tintFor
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
        val description = PlainComplicationText.Builder(
            "$pct percent of today's noise dose used"
        ).build()

        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = dose.coerceIn(0.0, 100.0).toFloat(),
                min = 0f,
                max = 100f,
                contentDescription = description,
            )
                .setText(PlainComplicationText.Builder("$pct%").build())
                .setTitle(PlainComplicationText.Builder("DOSE").build())
                .setMonochromaticImage(
                    MonochromaticImage.Builder(
                        Icon.createWithResource(this, R.drawable.ic_voxara_status)
                    ).build()
                )
                .setColorRamp(ColorRamp(intArrayOf(0xFF2BFF88.toInt(), tint), true))
                .build()

            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("$pct%").build(),
                contentDescription = description,
            )
                .setTitle(
                    PlainComplicationText.Builder(
                        if (dba < 80.0) "SAFE" else formatHeadroom(headroomMinutes(dba, dose))
                    ).build()
                )
                .build()

            else -> null
        }
    }

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
