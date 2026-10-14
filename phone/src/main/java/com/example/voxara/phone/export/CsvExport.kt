package com.example.voxara.phone.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.voxara.core.alerts.AlertRecord
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.phone.data.PhoneDb
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * CSV export through the share sheet. Numbers only: local date and time, which ledger, measured
 * seconds, level, dose, and for headphones the category and whether the watch or the phone
 * reported it. No device names, identifiers, titles or app names exist to export.
 */
object CsvExport {

    const val MINUTES_HEADER = "date,time,ledger,measured_s,laeq_dba,niosh_dose_pct,source,headphone_type"
    const val ALERTS_HEADER = "date,time,alert,tier,value,limit_level_pct,acknowledged,snoozed"

    private fun fmt(pattern: String, zone: TimeZone) = SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = zone }

    /** Dot decimal separator whatever the phone's locale, so every spreadsheet reads it. */
    private fun num(v: Double, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", v)

    fun minutesCsv(records: List<MinuteRecord>, zone: TimeZone = TimeZone.getDefault()): String {
        val date = fmt("yyyy-MM-dd", zone)
        val time = fmt("HH:mm", zone)
        return buildString {
            appendLine(MINUTES_HEADER)
            for (r in records.sortedWith(compareBy({ it.minute }, { it.kind.code }, { it.origin }))) {
                val at = Date(r.minute * 60_000L)
                append(date.format(at)).append(',')
                append(time.format(at)).append(',')
                append(if (r.kind == ExposureKind.AMBIENT) "ambient" else "headphones").append(',')
                append(num(r.measuredSeconds, 1)).append(',')
                append(r.laeq?.let { num(it, 1) } ?: "").append(',')
                append(num(r.nioshDosePercent, 4)).append(',')
                append(if (r.origin == 0) "watch" else "phone").append(',')
                append(if (r.kind == ExposureKind.HEADPHONE) HeadphoneCategory.of(r.category).name.lowercase() else "")
                appendLine()
            }
        }
    }

    fun alertsCsv(alerts: List<AlertRecord>, zone: TimeZone = TimeZone.getDefault()): String {
        val date = fmt("yyyy-MM-dd", zone)
        val time = fmt("HH:mm", zone)
        return buildString {
            appendLine(ALERTS_HEADER)
            for (a in alerts.sortedBy { it.atMs }) {
                val at = Date(a.atMs)
                append(date.format(at)).append(',')
                append(time.format(at)).append(',')
                append(a.kind.name.lowercase()).append(',')
                append(a.tier.name.lowercase()).append(',')
                append(num(a.value, 1)).append(',')
                append(a.level).append(',')
                append(a.acknowledged).append(',')
                append(a.snoozed)
                appendLine()
            }
        }
    }

    /** Writes both files to the app's cache and returns a share-sheet intent for them. */
    fun shareIntent(context: Context, db: PhoneDb): Intent {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val minutes = File(dir, "voxara_minutes.csv").apply {
            writeText(minutesCsv(db.minutes(0, Long.MAX_VALUE, ExposureKind.AMBIENT) + db.minutes(0, Long.MAX_VALUE, ExposureKind.HEADPHONE)))
        }
        val alerts = File(dir, "voxara_alerts.csv").apply { writeText(alertsCsv(db.alerts())) }
        val authority = "${context.packageName}.export"
        val uris = arrayListOf(
            FileProvider.getUriForFile(context, authority, minutes),
            FileProvider.getUriForFile(context, authority, alerts),
        )
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newRawUri(null, uris[0]).apply { addItem(ClipData.Item(uris[1])) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }
}
