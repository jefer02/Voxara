package com.example.voxara.phone.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The FILLED tab icons: the selected tab of the capsule bar draws its icon filled, the others keep the outlined VoxIcons
 * (shared-ui) they are drawn after, on the same 24 grid. Solid shapes with even-odd cut-outs (the door, the watch's
 * hands), most softened by a 1.5 px stroke of the same colour so the corners stay round like the outlined set. Tinted by the caller.
 */
object TabIcons {

    private fun filled(name: String, strokeWidth: Float = 1.5f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.EvenOdd,
                stroke = if (strokeWidth > 0f) SolidColor(Color.White) else null,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    /** A circle as two arcs (as in VoxIcons). */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        close()
    }

    private fun PathBuilder.rect(l: Float, t: Float, r: Float, b: Float) {
        moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close()
    }

    /** The house, solid, with its door cut out. */
    val Home = filled("home_filled") {
        moveTo(12f, 3.8f); lineTo(19.2f, 10f); lineTo(19.2f, 20f); lineTo(4.8f, 20f); lineTo(4.8f, 10f); close()
        rect(10f, 14.5f, 14f, 20f)
    }

    /** Four solid bars. */
    val Chart = filled("chart_filled") {
        rect(3.8f, 12.5f, 6.2f, 20f)
        rect(8.8f, 7.5f, 11.2f, 20f)
        rect(13.8f, 10.5f, 16.2f, 20f)
        rect(18.8f, 4.5f, 21.2f, 20f)
    }

    /** The round face, solid, its hands cut out; the strap as two solid tabs. */
    val Watch = filled("watch_filled", strokeWidth = 0f) {
        circle(12f, 12f, 6.8f)
        // the hands: a thin L cut out of the face
        moveTo(10.9f, 8.2f); lineTo(13.1f, 8.2f); lineTo(13.1f, 11.3f); lineTo(15f, 12.6f); lineTo(13.9f, 14.3f); lineTo(10.9f, 12.4f); close()
        moveTo(9f, 5.6f); lineTo(9.6f, 2.6f); lineTo(14.4f, 2.6f); lineTo(15f, 5.6f); close()
        moveTo(9f, 18.4f); lineTo(9.6f, 21.4f); lineTo(14.4f, 21.4f); lineTo(15f, 18.4f); close()
    }

    /** The sliders with solid knobs. */
    val Settings: ImageVector = ImageVector.Builder("settings_filled", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(4f, 17f); lineTo(20f, 17f)
        }
        path(fill = SolidColor(Color.White)) {
            circle(9f, 7f, 3.4f)
            circle(15f, 17f, 3.4f)
        }
    }.build()
}
