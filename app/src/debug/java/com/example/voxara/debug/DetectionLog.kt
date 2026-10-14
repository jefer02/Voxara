package com.example.voxara.debug

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.voxara.R
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VSpace
import com.example.voxara.ui.design.VoxIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * DEBUG BUILD ONLY (src/debug). Auto-mode DETECTION LOG for validating the detector on the watch:
 * one CSV row per classification — time, dBA, feature and inference time, top-3 scene classes,
 * the vote after vetoes, the vetoes, the decision and the mode in force. Numbers and labels only:
 * no audio and no feature vectors ever reach it.
 *
 * Private storage (filesDir/detection_log), two rotating files of ~1 MB each (~2 MB in all),
 * written on its own thread. "Export detection log" merges them into one CSV in the app's external
 * files dir, which `adb pull` can read without root (path shown on screen). Release builds get a
 * no-op with the same API (src/release).
 */
object DetectionLog {
    const val available = true

    private const val TAG = "VoxaraDetectionLog"
    private const val MAX_FILE_BYTES = 1_000_000L
    private const val DIR = "detection_log"
    private const val CURRENT = "detection.csv"
    private const val PREVIOUS = "detection.1.csv"
    const val EXPORT_NAME = "voxara_detection_log.csv"

    const val HEADER = "epoch_ms,dba,feature_ms,vectors,inference_ms," +
        "top1_class,top1_p,top2_class,top2_p,top3_class,top3_p," +
        "vote_quiet,vote_street,vote_transport,vote_work,music_like,tonality_db," +
        "vetoes,detected,mode,confidence,headphones,scene_model"

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "voxara-detection-log").apply { isDaemon = true } }

    /** Queues one row; never blocks the dosimeter loop, never throws. */
    fun record(context: Context, trace: DetectionTrace) {
        val app = context.applicationContext
        val line = csv(trace)
        io.execute { runCatching { append(app, line) }.onFailure { Log.w(TAG, "append failed", it) } }
    }

    fun csv(t: DetectionTrace): String {
        fun n(v: Double, digits: Int = 3) = if (v.isNaN()) "" else String.format(Locale.ROOT, "%.${digits}f", v)
        val top = (0 until 3).joinToString(",") { i ->
            t.topClasses.getOrNull(i)?.let { (c, p) -> "${c.replace(',', ';')},${n(p, 4)}" } ?: ","
        }
        val vote = t.vote?.joinToString(",") { n(it, 4) } ?: ",,,"
        return listOf(
            t.atMs.toString(), n(t.dba, 1), n(t.featureMs), t.vectors.toString(), n(t.inferenceMs),
            top, vote, if (t.musicLike) "1" else "0", n(t.tonalityDb, 2),
            t.vetoes.joinToString("|"), t.detected?.name ?: "", t.mode.name, n(t.confidence, 3),
            if (t.headphonesPlaying) "1" else "0", if (t.sceneModelEnabled) "1" else "0",
        ).joinToString(",")
    }

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun append(context: Context, line: String) {
        val d = dir(context)
        val current = File(d, CURRENT)
        if (current.exists() && current.length() + line.length + 1 > MAX_FILE_BYTES) {
            val previous = File(d, PREVIOUS)
            previous.delete()
            current.renameTo(previous)
        }
        if (!current.exists()) current.writeText(HEADER + "\n")
        current.appendText(line + "\n")
    }

    data class Stats(val rows: Int, val bytes: Long)

    /** Rows and bytes kept now (both files). Reads files: call off the main thread. */
    fun stats(context: Context): Stats {
        val files = listOf(PREVIOUS, CURRENT).map { File(dir(context), it) }.filter { it.exists() }
        val rows = files.sumOf { f -> f.useLines { lines -> lines.count() - 1 }.coerceAtLeast(0) }
        return Stats(rows, files.sumOf { it.length() })
    }

    /** Merges both files (oldest first) into one CSV for adb pull. Null when there is nothing. */
    fun export(context: Context): File? {
        val parts = listOf(PREVIOUS, CURRENT).map { File(dir(context), it) }.filter { it.exists() }
        if (parts.isEmpty()) return null
        val out = File(context.getExternalFilesDir(null) ?: context.filesDir, EXPORT_NAME)
        out.bufferedWriter().use { w ->
            w.write(HEADER); w.newLine()
            parts.forEach { f -> f.useLines { lines -> lines.drop(1).forEach { w.write(it); w.newLine() } } }
        }
        return out
    }

    fun clear(context: Context) {
        listOf(PREVIOUS, CURRENT).forEach { File(dir(context), it).delete() }
    }

    /** Debug tools entries: row count, Export and Clear. */
    @Composable
    fun SettingsEntries() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var stats by remember { mutableStateOf<Stats?>(null) }
        var exported by remember { mutableStateOf<String?>(null) }
        var refresh by remember { mutableIntStateOf(0) }
        LaunchedEffect(refresh) { stats = withContext(Dispatchers.IO) { runCatching { stats(context) }.getOrNull() } }

        Column(verticalArrangement = Arrangement.spacedBy(VSpace.xs)) {
            BodyText(
                stats?.let { stringResource(R.string.debug_detection_stats, it.rows, (it.bytes + 512) / 1024) }
                    ?: stringResource(R.string.debug_detection_title),
            )
            PillButton(
                stringResource(R.string.debug_detection_export),
                onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { runCatching { export(context) }.getOrNull() }
                        exported = file?.absolutePath
                        refresh++
                    }
                },
                icon = VoxIcons.Share,
                style = PillStyle.OUTLINED,
            )
            exported?.let { path ->
                BodyText(stringResource(R.string.debug_detection_exported, path), color = VColor.Text)
            }
            PillButton(
                stringResource(R.string.debug_detection_clear),
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { clear(context) }
                        exported = null
                        refresh++
                    }
                },
                icon = VoxIcons.Trash,
                style = PillStyle.OUTLINED,
            )
        }
    }
}
