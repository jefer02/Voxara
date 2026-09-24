package com.example.voxara.tile

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
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
import com.example.voxara.R
import com.example.voxara.core.design.Palette
import com.example.voxara.core.format.formatHeadroom
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.monitoring.monitoringStatus
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.LocaleStore
import com.example.voxara.data.VoxaraStore
import com.example.voxara.presentation.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.pow
import kotlin.math.roundToInt

private const val RESOURCES_VERSION = "2"

/**
 * TILE — today's dose as one thick ring, one number, the zone in WORDS and how fresh the data
 * is. Renders from the cached ledger; it never wakes the microphone. Tap opens the app (which is
 * also how a paused monitor resumes: Android 14+ needs a visible surface).
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

// ---------------------------------------------------------------- shared tile helpers

/** The zone that matters for a cached reading: the worse of the level and the daily dose. */
internal fun zoneFor(dba: Double, dosePercent: Double): RiskZone =
    maxOf(RiskZone.of(dba), RiskZone.ofPercent(dosePercent))

/** Zone colour as an ARGB int (the shared palette). */
internal fun tintFor(dba: Double, dosePercent: Double): Int = Palette.zone(zoneFor(dba, dosePercent)).toInt()

internal fun zoneWordRes(z: RiskZone): Int = when (z) {
    RiskZone.OK -> R.string.zone_ok
    RiskZone.MODERATE -> R.string.zone_moderate
    RiskZone.LOUD -> R.string.zone_loud
    RiskZone.DANGEROUS -> R.string.zone_dangerous
}

internal fun openApp(context: Context, id: String) = ModifiersBuilders.Modifiers.Builder()
    .setClickable(
        ModifiersBuilders.Clickable.Builder()
            .setId(id)
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

/** Text in the tile type scale: never below 12 sp. */
internal fun tileText(s: String, size: Float, weight: Int, color: Int): LayoutElementBuilders.LayoutElement =
    Text.Builder()
        .setText(s)
        .setMaxLines(2)
        .setFontStyle(
            FontStyle.Builder().setSize(sp(size.coerceAtLeast(12f))).setWeight(weight).setColor(argb(color)).build()
        )
        .build()

/** A thick full ring with [fraction] filled in [tint]. */
internal fun ringBox(fraction: Float, tint: Int, center: LayoutElementBuilders.LayoutElement, modifiers: ModifiersBuilders.Modifiers): Box {
    val sweep = fraction.coerceIn(0f, 1f) * 360f
    return Box.Builder()
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
        .addContent(center)
        .setModifiers(modifiers)
        .build()
}

internal fun blackRoot(content: LayoutElementBuilders.LayoutElement): LayoutElementBuilders.LayoutElement =
    Box.Builder().setWidth(expand()).setHeight(expand())
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(Palette.BLACK.toInt())).build())
                .build()
        )
        .addContent(content)
        .build()

@androidx.annotation.OptIn(androidx.wear.protolayout.expression.ProtoLayoutExperimental::class)
internal fun buildTile(context: Context, p: VoxaraStore.Persisted): TileBuilders.Tile {
    val dose = p.dosePercent
    val dba = p.lastDba
    val zone = zoneFor(dba, dose)
    val tint = Palette.zone(zone).toInt()
    // Built by the tile host, which hands us its own context: resolve the language ourselves.
    val res = LocaleStore.localized(context)
    // Left on but no heartbeat: the watch stopped monitoring. Tapping the tile opens the app.
    val paused = monitoringStatus(
        enabled = p.monitoringEnabled,
        serviceActive = ExposureRepository.serviceActive,
        lastHeartbeatMs = p.heartbeatMs,
        nowMs = System.currentTimeMillis(),
    ) == MonitoringStatus.PAUSED
    val secondary = Palette.TEXT_SECONDARY.toInt()
    val primary = Palette.TEXT_PRIMARY.toInt()

    val bottom = when {
        paused -> res.getString(R.string.paused_resume)
        p.heartbeatMs > 0 -> res.getString(
            R.string.tile_updated,
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(p.heartbeatMs)),
        )
        else -> res.getString(R.string.card_now_none)
    }
    val detail = if (dba < 80.0) res.getString(R.string.zone_with_level, res.getString(zoneWordRes(zone)), dba.roundToInt())
    else res.getString(R.string.headroom_suffix, formatHeadroom(headroomMinutes(dba, dose)))

    val center = Column.Builder()
        .addContent(tileText(res.getString(R.string.tile_todays_dose), 12f, FONT_WEIGHT_MEDIUM, secondary))
        .addContent(Spacer.Builder().setHeight(dp(2f)).build())
        .addContent(tileText(res.getString(R.string.tile_dose_value, dose.roundToInt()), 36f, FONT_WEIGHT_BOLD, primary))
        .addContent(tileText(detail, 13f, FONT_WEIGHT_MEDIUM, tint))
        .addContent(Spacer.Builder().setHeight(dp(2f)).build())
        .addContent(tileText(bottom, 12f, FONT_WEIGHT_MEDIUM, if (paused) Palette.ZONE_MODERATE.toInt() else secondary))
        .build()

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setFreshnessIntervalMillis(5 * 60 * 1000)
        .setTileTimeline(
            TimelineBuilders.Timeline.fromLayoutElement(
                blackRoot(ringBox((dose / 100.0).toFloat(), tint, center, openApp(context, "voxara_open")))
            )
        )
        .build()
}

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
        VoxaraStore.Persisted(dosePercent = 46.0, lastDba = 88.0, heartbeatMs = System.currentTimeMillis()),
    )
}
