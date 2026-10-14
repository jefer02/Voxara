package com.example.voxara.phone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.voxara.core.design.Palette
import com.example.voxara.core.ledger.localEpochDay
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.MainActivity
import com.example.voxara.phone.PhoneGraph
import com.example.voxara.phone.PhoneLocale
import com.example.voxara.phone.R
import com.example.voxara.ui.design.wordRes
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * HOME-SCREEN WIDGET — today's dose from the watch, with the time it was true. Shown only when
 * the watch's status is from today; otherwise "No data today". Never live: refreshed after each
 * sync. The zone is a word, never colour alone.
 */
class DoseWidget : GlanceAppWidget() {

    private data class Data(val percent: Int?, val zoneWord: String?, val zone: RiskZone?, val line: String, val title: String)

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
        return if (st != null && localEpochDay(st.atMs) == localEpochDay(now)) {
            val zone = RiskZone.ofPercent(st.dosePercent)
            Data(
                percent = st.dosePercent.roundToInt(),
                zoneWord = res.getString(zone.wordRes()),
                zone = zone,
                line = res.getString(R.string.widget_as_of, time.format(Date(st.atMs))),
                title = res.getString(R.string.widget_name),
            )
        } else {
            Data(null, null, null, res.getString(R.string.widget_no_data), res.getString(R.string.widget_name))
        }
    }

    @Composable
    private fun Content(d: Data) {
        val bg = ColorProvider(day = Color(Palette.LIGHT_SURFACE_1), night = Color(Palette.SURFACE_1))
        val text = ColorProvider(day = Color(Palette.LIGHT_TEXT_PRIMARY), night = Color(Palette.TEXT_PRIMARY))
        val text2 = ColorProvider(day = Color(Palette.LIGHT_TEXT_SECONDARY), night = Color(Palette.TEXT_SECONDARY))
        val ink = d.zone?.let { ColorProvider(day = Color(Palette.zoneInk(it)), night = Color(Palette.zone(it))) } ?: text2
        Column(
            modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(24.dp).padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(d.title, style = TextStyle(color = text2, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(4.dp))
            Text(
                d.percent?.let { "$it%" } ?: "—",
                style = TextStyle(color = text, fontSize = 36.sp, fontWeight = FontWeight.Bold),
            )
            if (d.zoneWord != null) Text(d.zoneWord, style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(4.dp))
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
