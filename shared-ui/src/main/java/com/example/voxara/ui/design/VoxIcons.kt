package com.example.voxara.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.example.voxara.core.risk.RiskZone

/**
 * VOXARA ICONS — one consistent rounded stroke set (2 px on a 24 grid, round caps and joins),
 * drawn here so no third-party or platform icon assets are used. Tinted by the caller.
 */
object VoxIcons {

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    /** Circle helper: two arcs. */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        close()
    }

    val Headphones = icon("headphones") {
        moveTo(4f, 17f); lineTo(4f, 12f)
        arcTo(8f, 8f, 0f, false, true, 20f, 12f); lineTo(20f, 17f)
        moveTo(4f, 14f); lineTo(7f, 14f); lineTo(7f, 20f); lineTo(4f, 20f); close()
        moveTo(20f, 14f); lineTo(17f, 14f); lineTo(17f, 20f); lineTo(20f, 20f); close()
    }

    /** Sound waves: the room / ambient noise. */
    val Waves = icon("waves") {
        moveTo(4f, 10f); lineTo(4f, 14f)
        moveTo(8f, 7f); lineTo(8f, 17f)
        moveTo(12f, 4f); lineTo(12f, 20f)
        moveTo(16f, 7f); lineTo(16f, 17f)
        moveTo(20f, 10f); lineTo(20f, 14f)
    }

    val Calendar = icon("calendar") {
        moveTo(5f, 6f); lineTo(19f, 6f); lineTo(19f, 20f); lineTo(5f, 20f); close()
        moveTo(5f, 10f); lineTo(19f, 10f)
        moveTo(9f, 4f); lineTo(9f, 7f)
        moveTo(15f, 4f); lineTo(15f, 7f)
    }

    val Chart = icon("chart") {
        moveTo(5f, 20f); lineTo(5f, 13f)
        moveTo(10f, 20f); lineTo(10f, 8f)
        moveTo(15f, 20f); lineTo(15f, 11f)
        moveTo(20f, 20f); lineTo(20f, 5f)
    }

    val Settings = icon("settings") {
        // Sliders: simple, legible at 20 dp.
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(4f, 17f); lineTo(20f, 17f)
        circle(9f, 7f, 2.5f)
        circle(15f, 17f, 2.5f)
    }

    val Ask = icon("ask") {
        moveTo(5f, 5f); lineTo(19f, 5f); lineTo(19f, 16f); lineTo(11f, 16f); lineTo(7f, 20f); lineTo(7f, 16f)
        lineTo(5f, 16f); close()
        moveTo(9f, 10.5f); lineTo(9.01f, 10.5f)
        moveTo(12f, 10.5f); lineTo(12.01f, 10.5f)
        moveTo(15f, 10.5f); lineTo(15.01f, 10.5f)
    }

    val Mic = icon("mic") {
        moveTo(9f, 6f); arcTo(3f, 3f, 0f, false, true, 15f, 6f); lineTo(15f, 11f)
        arcTo(3f, 3f, 0f, false, true, 9f, 11f); close()
        moveTo(6f, 11f); arcTo(6f, 6f, 0f, false, false, 18f, 11f)
        moveTo(12f, 17f); lineTo(12f, 21f)
    }

    /** Calibration: a dial with a needle. */
    val Tune = icon("tune") {
        moveTo(4f, 16f); arcTo(8f, 8f, 0f, false, true, 20f, 16f)
        moveTo(12f, 16f); lineTo(16f, 10f)
        moveTo(4f, 19f); lineTo(20f, 19f)
    }

    val Check = icon("check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }

    val Info = icon("info") {
        circle(12f, 12f, 9f)
        moveTo(12f, 11f); lineTo(12f, 16.5f)
        moveTo(12f, 7.5f); lineTo(12.01f, 7.5f)
    }

    val Warning = icon("warning") {
        moveTo(12f, 3.5f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close()
        moveTo(12f, 9.5f); lineTo(12f, 14f)
        moveTo(12f, 17f); lineTo(12.01f, 17f)
    }

    val Stop = icon("stop") {
        // Octagon with an exclamation: the most severe zone.
        moveTo(8f, 3f); lineTo(16f, 3f); lineTo(21f, 8f); lineTo(21f, 16f); lineTo(16f, 21f)
        lineTo(8f, 21f); lineTo(3f, 16f); lineTo(3f, 8f); close()
        moveTo(12f, 7.5f); lineTo(12f, 13f)
        moveTo(12f, 16.5f); lineTo(12.01f, 16.5f)
    }

    val Close = icon("close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }

    val Play = icon("play") { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() }

    val Pause = icon("pause") { moveTo(8f, 5f); lineTo(8f, 19f); moveTo(16f, 5f); lineTo(16f, 19f) }

    val Shield = icon("shield") {
        moveTo(12f, 3f); lineTo(19f, 6f); lineTo(19f, 11f)
        arcTo(9f, 10f, 0f, false, true, 12f, 21f)
        arcTo(9f, 10f, 0f, false, true, 5f, 11f); lineTo(5f, 6f); close()
    }

    val Sparkle = icon("sparkle") {
        moveTo(12f, 3f); lineTo(13.8f, 10.2f); lineTo(21f, 12f); lineTo(13.8f, 13.8f)
        lineTo(12f, 21f); lineTo(10.2f, 13.8f); lineTo(3f, 12f); lineTo(10.2f, 10.2f); close()
    }

    val Back = icon("back") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }

    // ------------------------------------------------------------------ added for the phone app

    val Forward = icon("forward") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }

    val Home = icon("home") {
        moveTo(4f, 11f); lineTo(12f, 4f); lineTo(20f, 11f)
        moveTo(6f, 9.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 9.5f)
        moveTo(10f, 20f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 20f)
    }

    /** A round watch face with its strap. */
    val Watch = icon("watch") {
        circle(12f, 12f, 6f)
        moveTo(9f, 6.8f); lineTo(9.6f, 3f); lineTo(14.4f, 3f); lineTo(15f, 6.8f)
        moveTo(9f, 17.2f); lineTo(9.6f, 21f); lineTo(14.4f, 21f); lineTo(15f, 17.2f)
        moveTo(12f, 9.5f); lineTo(12f, 12f); lineTo(13.8f, 13.2f)
    }

    val Phone = icon("phone") {
        moveTo(7f, 3f); lineTo(17f, 3f); lineTo(17f, 21f); lineTo(7f, 21f); close()
        moveTo(11f, 18f); lineTo(13f, 18f)
    }

    val Refresh = icon("refresh") {
        moveTo(19f, 12f); arcTo(7f, 7f, 0f, true, true, 16.9f, 7f)
        moveTo(17f, 3f); lineTo(17f, 7f); lineTo(13f, 7f)
    }

    val Share = icon("share") {
        moveTo(12f, 15f); lineTo(12f, 3f)
        moveTo(8f, 7f); lineTo(12f, 3f); lineTo(16f, 7f)
        moveTo(6f, 11f); lineTo(5f, 11f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 11f); lineTo(18f, 11f)
    }

    val Trash = icon("trash") {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f)
        moveTo(6f, 7f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 7f)
        moveTo(10f, 11f); lineTo(10f, 16f); moveTo(14f, 11f); lineTo(14f, 16f)
    }

    val Bell = icon("bell") {
        moveTo(6f, 16f); lineTo(6f, 11f); arcTo(6f, 6f, 0f, false, true, 18f, 11f); lineTo(18f, 16f)
        lineTo(20f, 18f); lineTo(4f, 18f); close()
        moveTo(10f, 21f); lineTo(14f, 21f)
    }

    val Link = icon("link") {
        moveTo(10f, 14f); lineTo(14f, 10f)
        moveTo(11f, 7f); lineTo(13f, 5f); arcTo(4f, 4f, 0f, false, true, 19f, 11f); lineTo(17f, 13f)
        moveTo(13f, 17f); lineTo(11f, 19f); arcTo(4f, 4f, 0f, false, true, 5f, 13f); lineTo(7f, 11f)
    }

    val Globe = icon("globe") {
        circle(12f, 12f, 9f)
        moveTo(3f, 12f); lineTo(21f, 12f)
        moveTo(12f, 3f); arcTo(14f, 14f, 0f, false, true, 12f, 21f)
        moveTo(12f, 3f); arcTo(14f, 14f, 0f, false, false, 12f, 21f)
    }

    val Lock = icon("lock") {
        moveTo(6f, 11f); lineTo(18f, 11f); lineTo(18f, 20f); lineTo(6f, 20f); close()
        moveTo(8.5f, 11f); lineTo(8.5f, 8f); arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 8f); lineTo(15.5f, 11f)
    }

    val Battery = icon("battery") {
        moveTo(3f, 8f); lineTo(18f, 8f); lineTo(18f, 16f); lineTo(3f, 16f); close()
        moveTo(21f, 11f); lineTo(21f, 13f)
        moveTo(6f, 11f); lineTo(6f, 13f); moveTo(9f, 11f); lineTo(9f, 13f)
    }

    val Moon = icon("moon") {
        moveTo(20f, 14.5f); arcTo(8f, 8f, 0f, true, true, 9.5f, 4f)
        arcTo(6.5f, 6.5f, 0f, false, false, 20f, 14.5f); close()
    }

    val Plus = icon("plus") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }

    val Minus = icon("minus") { moveTo(5f, 12f); lineTo(19f, 12f) }

    val Clock = icon("clock") {
        circle(12f, 12f, 9f)
        moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
    }

    val Vibrate = icon("vibrate") {
        moveTo(8f, 4f); lineTo(16f, 4f); lineTo(16f, 20f); lineTo(8f, 20f); close()
        moveTo(4f, 9f); lineTo(4f, 15f); moveTo(20f, 9f); lineTo(20f, 15f)
    }

    /** Every zone has its own shape, so it never depends on colour. */
    fun zone(z: RiskZone): ImageVector = when (z) {
        RiskZone.OK -> Check
        RiskZone.MODERATE -> Info
        RiskZone.LOUD -> Warning
        RiskZone.DANGEROUS -> Stop
    }
}
