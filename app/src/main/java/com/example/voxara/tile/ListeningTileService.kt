package com.example.voxara.tile

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders.ARC_ANCHOR_START
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcLine
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_MEDIUM
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.voxara.R
import com.example.voxara.core.design.Palette
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_ADULT
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_SENSITIVE
import com.example.voxara.data.LocaleStore
import com.example.voxara.data.VoxaraStore
import com.example.voxara.presentation.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val RES_VERSION = "1"

/**
 * LISTENING TILE — the rolling 7-day headphone allowance as one thick ring and one number, marked
 * "estimated". Renders from the cached ledger; it never touches the microphone or the audio route.
 */
class ListeningTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                runCatching { VoxaraStore(this@ListeningTileService).read() }
                    .onSuccess { completer.set(buildListeningTile(this@ListeningTileService, it)) }
                    .onFailure { completer.setException(it) }
            }
            "voxara-listening-tile"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<Resources> =
        Futures.immediateFuture(Resources.Builder().setVersion(RES_VERSION).build())

    companion object {
        fun requestRefresh(context: Context) {
            runCatching { getUpdater(context).requestUpdate(ListeningTileService::class.java) }
        }
    }
}

internal fun listeningPercent(p: VoxaraStore.Persisted): Int {
    val budget = if (p.sensitiveListener) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT
    return (100.0 * p.headphoneWeeklyPa2h / budget).roundToInt()
}

/** The shared zone palette, by share of the weekly allowance. */
internal fun listeningTint(percent: Int): Int = Palette.zone(RiskZone.ofPercent(percent.toDouble())).toInt()

@androidx.annotation.OptIn(androidx.wear.protolayout.expression.ProtoLayoutExperimental::class)
internal fun buildListeningTile(context: Context, p: VoxaraStore.Persisted): TileBuilders.Tile {
    val res = LocaleStore.localized(context)
    val percent = listeningPercent(p)
    val tint = listeningTint(percent)
    val sweep = (percent / 100f).coerceIn(0f, 1f) * 360f

    val launch = ModifiersBuilders.Modifiers.Builder()
        .setClickable(
            ModifiersBuilders.Clickable.Builder()
                .setId("voxara_open_listening")
                .setOnClick(
                    ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(
                            ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(context.packageName)
                                .setClassName(MainActivity::class.java.name)
                                .build()
                        )
                        .build()
                )
                .build()
        )
        .build()

    fun text(s: String, size: Float, weight: Int, color: Int) = Text.Builder()
        .setText(s)
        .setFontStyle(FontStyle.Builder().setSize(sp(size)).setWeight(weight).setColor(argb(color)).build())
        .build()

    val ring = Box.Builder()
        .setWidth(expand()).setHeight(expand())
        .addContent(
            Arc.Builder().setAnchorAngle(degrees(0f)).setAnchorType(ARC_ANCHOR_START)
                .addContent(ArcLine.Builder().setLength(degrees(360f)).setThickness(dp(12f)).setColor(argb(Palette.SURFACE_3.toInt())).build())
                .build()
        )
        .apply {
            if (sweep > 1f) addContent(
                Arc.Builder().setAnchorAngle(degrees(0f)).setAnchorType(ARC_ANCHOR_START)
                    .addContent(ArcLine.Builder().setLength(degrees(sweep)).setThickness(dp(12f)).setColor(argb(tint)).build())
                    .build()
            )
        }
        .addContent(
            Column.Builder()
                .addContent(text(res.getString(R.string.tile_listening_title), 12f, FONT_WEIGHT_MEDIUM, Palette.TEXT_SECONDARY.toInt()))
                .addContent(Spacer.Builder().setHeight(dp(2f)).build())
                .addContent(text(res.getString(R.string.tile_dose_value, percent), 36f, FONT_WEIGHT_BOLD, Palette.TEXT_PRIMARY.toInt()))
                .addContent(text(res.getString(zoneWordRes(RiskZone.ofPercent(percent.toDouble()))), 13f, FONT_WEIGHT_MEDIUM, tint))
                .addContent(Spacer.Builder().setHeight(dp(2f)).build())
                .addContent(text(res.getString(R.string.tile_listening_sub), 12f, FONT_WEIGHT_MEDIUM, Palette.TEXT_SECONDARY.toInt()))
                .build()
        )
        .setModifiers(launch)
        .build()

    val root = Box.Builder().setWidth(expand()).setHeight(expand())
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(0xFF000000.toInt())).build())
                .build()
        )
        .addContent(ring)
        .build()

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RES_VERSION)
        .setFreshnessIntervalMillis(15 * 60 * 1000)
        .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
        .build()
}
