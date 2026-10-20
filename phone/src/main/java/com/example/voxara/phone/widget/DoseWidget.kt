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
 * HOME-SCREEN WIDGET — today's dose from the watch, with the time it was true. Shown only when
 * the watch's status is from today; otherwise "No data today". Never live: refreshed after each
 * sync. The zone is a word, never colour alone. Colours follow the app's accent and theme
 * (Settings → Appearance); the widget is re-rendered when they change.
 */
class DoseWidget : GlanceAppWidget() {

    private data class Data(
        val percent: Int?, val zoneWord: String?, val zone: RiskZone?, val line: String, val title: String,
        val accent: Accent, val themeMode: ThemeMode,
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
        return if (st != null && localEpochDay(st.atMs) == localEpochDay(now)) {
            val zone = RiskZone.ofPercent(st.dosePercent)
            Data(
                percent = st.dosePercent.roundToInt(),
                zoneWord = res.getString(zone.wordRes()),
                zone = zone,
                line = res.getString(R.string.widget_as_of, time.format(Date(st.atMs))),
                title = res.getString(R.string.widget_name),
                accent = s.accent, themeMode = s.themeMode,
            )
        } else {
            Data(null, null, null, res.getString(R.string.widget_no_data), res.getString(R.string.widget_name), s.accent, s.themeMode)
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
        val text = pick(Palette.LIGHT_TEXT, Palette.DARK_TEXT)
        val text2 = pick(Palette.LIGHT_TEXT_2, Palette.DARK_TEXT_2)
        val accent = pick(Palette.accent(d.accent, false).ink, Palette.accent(d.accent, true).ink)
        // The zone word in its tier colour (accent, amber, crimson), always as a word too.
        val ink = d.zone?.let { pick(Palette.level(it, d.accent, false).ink, Palette.level(it, d.accent, true).ink) } ?: text2
        Column(
            modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(22.dp).padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(d.title.uppercase(), style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold))
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
