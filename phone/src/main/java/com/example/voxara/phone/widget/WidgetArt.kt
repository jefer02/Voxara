package com.example.voxara.phone.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.voxara.designsystem.R as DsR

/**
 * THE WIDGET'S PICTURES. A home-screen widget cannot use the app's font or draw an arc, so its ring and its figure are
 * drawn here as three white-on-transparent masks (the track, the arc, the figure) that Glance tints with the theme's day and
 * night colours: the same 250° ring and the same Plus Jakarta Sans digits in fixed-width cells as the app and the watch,
 * in either theme, without drawing again when the system theme changes.
 */
internal object WidgetArt {
    private const val START = 145f
    private const val SWEEP = 250f

    /** Plus Jakarta Sans at extra-bold (the variable font's weight axis), falling back to the system bold. */
    private fun typeface(context: Context): Typeface {
        val base = runCatching { ResourcesCompat.getFont(context, DsR.font.plus_jakarta_sans) }.getOrNull() ?: Typeface.DEFAULT
        return Typeface.create(base, 800, false)
    }

    private fun mask(sizePx: Int) = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)

    private fun stroke(sizePx: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = sizePx * 0.095f
    }

    private fun oval(sizePx: Int, paint: Paint): RectF {
        val inset = paint.strokeWidth / 2f + sizePx * 0.02f
        return RectF(inset, inset, sizePx - inset, sizePx - inset)
    }

    /** The empty ring: the 250° track. */
    fun track(sizePx: Int): Bitmap = mask(sizePx).also { bmp ->
        val p = stroke(sizePx)
        Canvas(bmp).drawArc(oval(sizePx, p), START, SWEEP, false, p)
    }

    /** The share of the allowance: [fraction] 0..1 of the arc, from its start. */
    fun arc(sizePx: Int, fraction: Float): Bitmap = mask(sizePx).also { bmp ->
        val f = fraction.coerceIn(0f, 1f)
        if (f <= 0f) return@also
        val p = stroke(sizePx)
        Canvas(bmp).drawArc(oval(sizePx, p), START, SWEEP * f, false, p)
    }

    /**
     * The figure in the middle of the ring: each digit centred in a cell as wide as the widest digit of the value (tightened
     * a little), then a smaller "%" on the same baseline; "—" when there is nothing today.
     */
    fun figure(context: Context, sizePx: Int, value: Int?): Bitmap = mask(sizePx).also { bmp ->
        val tf = typeface(context)
        val text = value?.toString() ?: "—"
        // a variable font's weight axis is not reachable from a Paint on every device, so the extra-bold is a thin outline on the regular cut
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); typeface = tf; textAlign = Paint.Align.CENTER; style = Paint.Style.FILL_AND_STROKE }
        // the biggest size whose digits and "%" fit inside the ring
        val room = sizePx * 0.62f
        var size = sizePx * 0.40f
        fun widthAt(s: Float): Float {
            paint.textSize = s
            val cell = text.maxOf { paint.measureText(it.toString()) } * 0.92f
            paint.textSize = s * 0.42f
            val unit = if (value == null) 0f else paint.measureText("%") + s * 0.06f
            return cell * text.length + unit
        }
        while (size > 8f && widthAt(size) > room) size -= 1f
        paint.textSize = size
        paint.strokeWidth = size * 0.03f
        val cell = text.maxOf { paint.measureText(it.toString()) } * 0.92f
        paint.textSize = size * 0.42f
        val unit = if (value == null) 0f else paint.measureText("%")
        val gap = if (value == null) 0f else size * 0.06f
        val total = cell * text.length + gap + unit
        val metrics = Paint(paint).apply { textSize = size }.fontMetrics
        val baseline = sizePx * 0.5f - (metrics.ascent + metrics.descent) / 2f - sizePx * 0.03f
        val canvas = Canvas(bmp)
        var x = (sizePx - total) / 2f
        paint.textSize = size
        for (ch in text) {
            canvas.drawText(ch.toString(), x + cell / 2f, baseline, paint)
            x += cell
        }
        if (value != null) {
            paint.textSize = size * 0.42f
            paint.strokeWidth = size * 0.42f * 0.03f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("%", x + gap, baseline, paint)
        }
    }
}
