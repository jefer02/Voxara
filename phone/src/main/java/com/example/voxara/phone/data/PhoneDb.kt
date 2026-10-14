package com.example.voxara.phone.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertRecord
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.sync.LedgerCodec
import com.example.voxara.sync.PhoneHistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The phone's copy of the watch's history — the same framework-SQLite approach as the watch
 * (no annotation processor). Numbers only: minute energy/dose/seconds with their category,
 * confidence and origin codes, and the alert history. No audio, no titles, no app or device
 * names, no identifiers. Kept for the retention the wearer chose (90 days by default).
 *
 * Excluded from backup and device transfer (see res/xml/data_extraction_rules.xml).
 */
class PhoneDb(context: Context, name: String? = NAME) :
    SQLiteOpenHelper(context.applicationContext, name, null, VERSION), PhoneHistoryStore {

    companion object {
        const val NAME = "voxara_phone.db"
        private const val VERSION = 1
        private const val M = "minutes"
        private const val A = "alerts"
    }

    private val _changes = MutableStateFlow(0)
    /** Bumped after every write, so screens re-read. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $M (
                minute INTEGER NOT NULL,
                kind INTEGER NOT NULL,
                measured_s REAL NOT NULL,
                energy_pa2s REAL NOT NULL,
                niosh_dose REAL NOT NULL,
                category INTEGER NOT NULL DEFAULT 0,
                confidence INTEGER NOT NULL DEFAULT 0,
                origin INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (minute, kind, origin)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $A (
                id INTEGER PRIMARY KEY,
                at_ms INTEGER NOT NULL,
                kind TEXT NOT NULL,
                value REAL NOT NULL,
                level INTEGER NOT NULL,
                ack INTEGER NOT NULL,
                snoozed INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX alerts_at ON $A(at_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun bump() { _changes.value++ }

    // ------------------------------------------------------------------ PhoneHistoryStore (sync)

    override fun digests(fromDay: Long, toDay: Long): Map<Pair<Long, ExposureKind>, String> {
        val out = HashMap<Pair<Long, ExposureKind>, String>()
        for (kind in ExposureKind.entries) {
            minutes(LedgerCodec.dayStartMinute(fromDay), LedgerCodec.dayStartMinute(toDay + 1), kind)
                .groupBy { LedgerCodec.utcDay(it.minute) }
                .forEach { (day, rows) -> out[day to kind] = LedgerCodec.digest(rows) }
        }
        return out
    }

    override fun replaceDay(day: Long, kind: ExposureKind, records: List<MinuteRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                M, "minute >= ? AND minute < ? AND kind = ?",
                arrayOf(LedgerCodec.dayStartMinute(day).toString(), LedgerCodec.dayStartMinute(day + 1).toString(), kind.code.toString()),
            )
            for (r in records) {
                if (r.kind != kind || LedgerCodec.utcDay(r.minute) != day) continue   // never trust a stray row
                db.insertWithOnConflict(M, null, values(r), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    override fun replaceAlerts(fromMs: Long, alerts: List<AlertRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(A, "at_ms >= ?", arrayOf(fromMs.toString()))
            for (a in alerts) {
                db.insertWithOnConflict(A, null, ContentValues().apply {
                    put("id", a.id); put("at_ms", a.atMs); put("kind", a.kind.name); put("value", a.value)
                    put("level", a.level); put("ack", if (a.acknowledged) 1 else 0); put("snoozed", if (a.snoozed) 1 else 0)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    // ------------------------------------------------------------------ reads

    /** Minutes with fromMinute <= minute < toMinute of one ledger, all origins, in minute order. */
    fun minutes(fromMinute: Long, toMinute: Long, kind: ExposureKind): List<MinuteRecord> {
        val out = ArrayList<MinuteRecord>()
        readableDatabase.query(
            M, null, "minute >= ? AND minute < ? AND kind = ?",
            arrayOf(fromMinute.toString(), toMinute.toString(), kind.code.toString()), null, null, "minute ASC, origin ASC",
        ).use { c ->
            val iMin = c.getColumnIndexOrThrow("minute")
            val iS = c.getColumnIndexOrThrow("measured_s")
            val iE = c.getColumnIndexOrThrow("energy_pa2s")
            val iD = c.getColumnIndexOrThrow("niosh_dose")
            val iCat = c.getColumnIndexOrThrow("category")
            val iConf = c.getColumnIndexOrThrow("confidence")
            val iOrg = c.getColumnIndexOrThrow("origin")
            while (c.moveToNext()) {
                out += MinuteRecord(
                    minute = c.getLong(iMin), kind = kind, measuredSeconds = c.getDouble(iS),
                    energyPa2s = c.getDouble(iE), nioshDosePercent = c.getDouble(iD),
                    category = c.getInt(iCat), confidence = c.getInt(iConf), origin = c.getInt(iOrg),
                )
            }
        }
        return out
    }

    /** Alerts at or after [fromMs], newest first. */
    fun alerts(fromMs: Long = 0L): List<AlertRecord> {
        val out = ArrayList<AlertRecord>()
        readableDatabase.query(A, null, "at_ms >= ?", arrayOf(fromMs.toString()), null, null, "at_ms DESC, id DESC").use { c ->
            while (c.moveToNext()) {
                val kind = runCatching { AlertKind.valueOf(c.getString(c.getColumnIndexOrThrow("kind"))) }.getOrNull() ?: continue
                out += AlertRecord(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    atMs = c.getLong(c.getColumnIndexOrThrow("at_ms")),
                    kind = kind,
                    value = c.getDouble(c.getColumnIndexOrThrow("value")),
                    level = c.getInt(c.getColumnIndexOrThrow("level")),
                    acknowledged = c.getInt(c.getColumnIndexOrThrow("ack")) != 0,
                    snoozed = c.getInt(c.getColumnIndexOrThrow("snoozed")) != 0,
                )
            }
        }
        return out
    }

    fun isEmpty(): Boolean = readableDatabase.rawQuery("SELECT 1 FROM $M LIMIT 1", null).use { !it.moveToFirst() }

    // ------------------------------------------------------------------ retention and deletion

    /** Drops minutes and alerts older than [retentionDays] before [nowMs]. */
    fun prune(retentionDays: Int, nowMs: Long) {
        val cutoffMs = nowMs - retentionDays * 86_400_000L
        writableDatabase.delete(M, "minute < ?", arrayOf((cutoffMs / 60_000L).toString()))
        writableDatabase.delete(A, "at_ms < ?", arrayOf(cutoffMs.toString()))
        bump()
    }

    fun clearAll() {
        writableDatabase.delete(M, null, null)
        writableDatabase.delete(A, null, null)
        bump()
    }

    private fun values(r: MinuteRecord) = ContentValues().apply {
        put("minute", r.minute)
        put("kind", r.kind.code)
        put("measured_s", r.measuredSeconds)
        put("energy_pa2s", r.energyPa2s)
        put("niosh_dose", r.nioshDosePercent)
        put("category", r.category)
        put("confidence", r.confidence)
        put("origin", r.origin)
    }
}
