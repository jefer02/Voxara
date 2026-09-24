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
import com.example.voxara.data.LocaleStore
import com.example.voxara.data.VoxaraStore
import com.example.voxara.tile.listeningPercent
import com.example.voxara.tile.listeningTint
import com.example.voxara.core.design.Palette

/**
 * COMPLICATION — the rolling 7-day headphone allowance (estimated). RANGED_VALUE + SHORT_TEXT.
 * Reads the cached ledger; never touches the microphone or the audio route.
 */
class ListeningComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, 34)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, listeningPercent(VoxaraStore(this).read()))

    private fun build(type: ComplicationType, percent: Int): ComplicationData? {
        val res = LocaleStore.localized(this)
        val description = PlainComplicationText.Builder(res.getString(R.string.complication_listening_cd, percent)).build()
        val value = PlainComplicationText.Builder(res.getString(R.string.tile_dose_value, percent)).build()
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = percent.coerceIn(0, 100).toFloat(), min = 0f, max = 100f, contentDescription = description,
            )
                .setText(value)
                .setTitle(PlainComplicationText.Builder(res.getString(R.string.complication_listening_title)).build())
                .setMonochromaticImage(
                    MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_voxara_status)).build()
                )
                .setColorRamp(ColorRamp(intArrayOf(Palette.ZONE_OK.toInt(), listeningTint(percent)), true))
                .build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text = value, contentDescription = description)
                .setTitle(PlainComplicationText.Builder(res.getString(R.string.complication_listening_title)).build())
                .build()
            else -> null
        }
    }

    companion object {
        fun requestRefresh(context: Context) {
            runCatching {
                ComplicationDataSourceUpdateRequester
                    .create(context, ComponentName(context, ListeningComplicationService::class.java))
                    .requestUpdateAll()
            }
        }
    }
}
