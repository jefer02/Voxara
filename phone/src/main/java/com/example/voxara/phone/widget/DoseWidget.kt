package com.example.voxara.phone.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.voxara.core.design.Accent
import com.example.voxara.core.design.Palette
import com.example.voxara.core.ledger.localEpochDay
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.MainActivity
import com.example.voxara.phone.PhoneGraph
import com.example.voxara.phone.PhoneLocale
import com.example.voxara.phone.R
import com.example.voxara.phone.data.ThemeMode
import com.example.voxara.ui.design.wordRes
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * HOME-SCREEN WIDGET — the watch's ring with today's dose from the watch, the zone as a word and the time it was true.
 * Shown only when the watch's status is from today; otherwise an empty ring and "No data today". Never live: refreshed after
 * each sync. The ring and the figure are the app's own (see [WidgetArt]); the zone is a word, never colour alone. Colours
 * follow the app's accent and theme (Settings → Appearance); the widget is re-rendered when they change.
 */
class DoseWidget : GlanceAppWidget() {

    private class Art(val track: Bitmap, val arc: Bitmap, val figure: Bitmap)

    private data class Data(
        val percent: Int?, val zoneWord: String?, val zone: RiskZone?, val line: String, val description: String,
        val accent: Accent, val themeMode: ThemeMode, val art: Art,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { Content(data) }
    }

    private suspend fun load(context: Context): Data {
        PhoneGraph.init(context)
        val res = PhoneLocale.localized(context)
        val s = PhoneGraph.store.read()
        val now = System.currentTimeMillis()
        val st = s.status
        val time = DateFormat.getTimeInstance(DateFormat.SHORT)
        val px = 360
        val today = st != null && localEpochDay(st.atMs) == localEpochDay(now)
        val value = if (today) st!!.dosePercent.roundToInt() else null
        val art = Art(
            WidgetArt.track(px),
            WidgetArt.arc(px, if (today) (st!!.dosePercent / 100.0).toFloat() else 0f),
            WidgetArt.figure(context, px, value),
        )
        return if (today) {
            val zone = RiskZone.ofPercent(st!!.dosePercent)
            val word = res.getString(zone.wordRes())
            Data(
                percent = value, zoneWord = word, zone = zone,
                line = res.getString(R.string.widget_as_of, time.format(Date(st.atMs))),
                description = res.getString(R.string.home_ring_cd, value!!, word),
                accent = s.accent, themeMode = s.themeMode, art = art,
            )
        } else {
            Data(null, null, null, res.getString(R.string.widget_no_data), res.getString(R.string.widget_no_data), s.accent, s.themeMode, art)
        }
    }

    @Composable
    private fun Content(d: Data) {
        // The app's appearance: Light / Dark pin both slots; Follow system gives Glance a day and a night colour.
        fun pick(light: Long, dark: Long) = when (d.themeMode) {
            ThemeMode.LIGHT -> ColorProvider(day = Color(light), night = Color(light))
            ThemeMode.DARK -> ColorProvider(day = Color(dark), night = Color(dark))
            ThemeMode.SYSTEM -> ColorProvider(day = Color(light), night = Color(dark))
        }
        val bg = pick(Palette.LIGHT_CARD, Palette.DARK_CARD)
        val text2 = pick(Palette.LIGHT_TEXT_2, Palette.DARK_TEXT_2)
        val figure = pick(Palette.LIGHT_TEXT, Palette.DARK_TEXT)
        val track = pick(Palette.LIGHT_TRACK.toLong(), Palette.DARK_TRACK.toLong())
        // The arc and the zone word in the tier's colour (accent, amber, crimson), always with the word too.
        val zone = d.zone ?: RiskZone.OK
        val fill = pick(Palette.level(zone, d.accent, false).fill, Palette.level(zone, d.accent, true).fill)
        val ink = d.zone?.let { pick(Palette.level(it, d.accent, false).ink, Palette.level(it, d.accent, true).ink) } ?: text2
        Column(
            modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(22.dp).padding(10.dp)
                .clickable(actionStartActivity<MainActivity>())
                .semantics { contentDescription = d.description + ". " + d.line },
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(GlanceModifier.size(88.dp), contentAlignment = Alignment.Center) {
                Image(ImageProvider(d.art.track), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), colorFilter = ColorFilter.tint(track))
                if (d.percent != null) Image(ImageProvider(d.art.arc), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), colorFilter = ColorFilter.tint(fill))
                Image(ImageProvider(d.art.figure), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), colorFilter = ColorFilter.tint(figure))
            }
            Spacer(GlanceModifier.height(2.dp))
            if (d.zoneWord != null) Text(d.zoneWord, style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text(d.line, style = TextStyle(color = text2, fontSize = 12.sp))
        }
    }

    companion object {
        /** Re-renders every placed widget from the stored status (after a sync). */
        suspend fun refresh(context: Context) = DoseWidget().updateAll(context)
    }
}

class DoseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DoseWidget()
}
