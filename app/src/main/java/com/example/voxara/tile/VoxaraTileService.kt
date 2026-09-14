package com.example.voxara.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
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
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.voxara.core.format.formatHeadroom
import com.example.voxara.data.VoxaraStore
import com.example.voxara.presentation.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.concurrent.futures.CallbackToFutureAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

private const val RESOURCES_VERSION = "1"

/**
 * TILE — the segmented dose donut. Renders from cached state; never wakes the mic.
 * One arc, one integer, one colour, tinted by risk band.
 */
class VoxaraTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                runCatching { VoxaraStore(this@VoxaraTileService).read() }
                    .onSuccess { completer.set(buildTile(this@VoxaraTileService, it)) }
                    .onFailure { completer.setException(it) }
            }
            "voxara-tile"
        }

    override fun onTileResourcesRequest(
        requestParams: ResourcesRequest,
    ): ListenableFuture<Resources> =
        Futures.immediateFuture(Resources.Builder().setVersion(RESOURCES_VERSION).build())

    companion object {
        /** Called by the dosimeter whenever the persisted ledger moves. */
        fun requestRefresh(context: Context) {
            runCatching {
                getUpdater(context).requestUpdate(VoxaraTileService::class.java)
            }
        }
    }
}

// ---------------------------------------------------------------- layout

private const val VOID = 0xFF04060A.toInt()
private const val INK1 = 0xFFF2F7FF.toInt()
private const val INK3 = 0xFF4E6178.toInt()
private const val TRACK = 0x14FFFFFF

/** Same ramp as the ring, evaluated on the tile's cached level. */
internal fun tintFor(dba: Double, dosePercent: Double): Int = when {
    dosePercent >= 100.0 || dba >= 106.0 -> 0xFFFF2E6B.toInt()
    dba >= 95.0 -> 0xFFFF8A1F.toInt()
    dba >= 85.0 -> 0xFFFFC531.toInt()
    else -> 0xFF2BFF88.toInt()
}

@androidx.annotation.OptIn(androidx.wear.protolayout.expression.ProtoLayoutExperimental::class)
internal fun buildTile(context: Context, p: VoxaraStore.Persisted): TileBuilders.Tile {
    val dose = p.dosePercent
    val dba = p.lastDba
    val tint = tintFor(dba, dose)
    val headroom = headroomMinutes(dba, dose)

    val launch = ModifiersBuilders.Modifiers.Builder()
        .setClickable(
            ModifiersBuilders.Clickable.Builder()
                .setId("voxara_open")
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

    val sweep = (dose / 100.0).coerceIn(0.0, 1.0) * 360.0

    val donut = Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .addContent(
            Arc.Builder()
                .setAnchorAngle(degrees(0f))
                .setAnchorType(ARC_ANCHOR_START)
                .addContent(
                    ArcLine.Builder()
                        .setLength(degrees(360f))
                        .setThickness(dp(9f))
                        .setColor(argb(TRACK))
                        .build()
                )
                .build()
        )
        .apply {
            if (sweep > 1.0) {
                addContent(
                    Arc.Builder()
                        .setAnchorAngle(degrees(0f))
                        .setAnchorType(ARC_ANCHOR_START)
                        .addContent(
                            ArcLine.Builder()
                                .setLength(degrees(sweep.toFloat()))
                                .setThickness(dp(9f))
                                .setColor(argb(tint))
                                .build()
                        )
                        .build()
                )
            }
        }
        .addContent(
            Column.Builder()
                .addContent(meta("TODAY'S DOSE", INK3))
                .addContent(Spacer.Builder().setHeight(dp(2f)).build())
                .addContent(
                    Text.Builder()
                        .setText("${dose.roundToInt()}%")
                        .setFontStyle(
                            FontStyle.Builder()
                                .setSize(sp(34f))
                                .setWeight(FONT_WEIGHT_BOLD)
                                .setColor(argb(tint))
                                .build()
                        )
                        .build()
                )
                .addContent(Spacer.Builder().setHeight(dp(2f)).build())
                .addContent(
                    meta(
                        if (dba < 80.0) "NO DOSE ACCRUING"
                        else "${formatHeadroom(headroom)} HEADROOM",
                        INK1,
                    )
                )
                .build()
        )
        .setModifiers(launch)
        .build()

    val root = Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder().setColor(argb(VOID)).build()
                )
                .build()
        )
        .addContent(donut)
        .build()

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setFreshnessIntervalMillis(5 * 60 * 1000)
        .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
        .build()
}

@androidx.annotation.OptIn(androidx.wear.protolayout.expression.ProtoLayoutExperimental::class)
private fun meta(s: String, color: Int): LayoutElementBuilders.LayoutElement =
    Text.Builder()
        .setText(s)
        .setFontStyle(
            FontStyle.Builder()
                .setSize(sp(10f))
                .setWeight(FONT_WEIGHT_MEDIUM)
                .setLetterSpacing(androidx.wear.protolayout.DimensionBuilders.em(0.14f))
                .setColor(argb(color))
                .build()
        )
        .build()

/** Headroom re-derived from the cached ledger, never recomputed from a live mic. */
internal fun headroomMinutes(dba: Double, dosePercent: Double): Double {
    if (dba < 80.0) return Double.POSITIVE_INFINITY
    val permitted = 8.0 * 60.0 / 2.0.pow((dba - 85.0) / 3.0)
    return (permitted * (1.0 - dosePercent / 100.0)).coerceAtLeast(0.0)
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData(
    { Resources.Builder().setVersion(RESOURCES_VERSION).build() },
) {
    buildTile(
        context,
        VoxaraStore.Persisted(dosePercent = 46.0, lastDba = 88.0),
    )
}
