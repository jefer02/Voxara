package com.example.voxara.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertLog
import com.example.voxara.core.alerts.AlertRecord
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.MinuteStore

/**
 * MINUTE LEDGER STORAGE — one row per minute and exposure kind.
 *
 * Framework SQLite (no extra dependency, no annotation processor): the schema is tiny and every
 * rule that matters lives in the pure `core.ledger` code. Rows hold numbers only — no audio, no
 * device names, no media titles or app names. Kept for [RETENTION_DAYS], then pruned.
 *
 * Version 2 adds the alert history ([alerts]): when, which alert, its value and what the wearer
 * did with it. The upgrade only creates the new table; the minute rows are untouched.
 */
class ExposureDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION), MinuteStore {

    companion object {
        private const val NAME = "voxara_exposure.db"
        private const val VERSION = 2
        const val RETENTION_DAYS = 35L
        private const val T = "minutes"
        private const val A = "alerts"

        @Volatile private var instance: ExposureDb? = null

        fun get(context: Context): ExposureDb =
            instance ?: synchronized(this) {
                instance ?: ExposureDb(context).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T (
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
        createAlerts(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createAlerts(db)
    }

    private fun createAlerts(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $A (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                at_ms INTEGER NOT NULL,
                kind TEXT NOT NULL,
                value REAL NOT NULL,
                level INTEGER NOT NULL,
                ack INTEGER NOT NULL DEFAULT 0,
                snoozed INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    /** Inserts or replaces the row keyed by (minute, kind, origin). */
    override fun put(r: MinuteRecord) {
        writableDatabase.insertWithOnConflict(T, null, values(r), SQLiteDatabase.CONFLICT_REPLACE)
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

    /** Records with fromMinute <= minute < toMinute for one kind (all origins). */
    override fun range(fromMinute: Long, toMinute: Long, kind: ExposureKind): List<MinuteRecord> {
        val where = StringBuilder("minute >= ? AND minute < ? AND kind = ?")
        val args = arrayListOf(fromMinute.toString(), toMinute.toString(), kind.code.toString())
        val out = ArrayList<MinuteRecord>()
        readableDatabase.query(
            T, null, where.toString(), args.toTypedArray(), null, null, "minute ASC",
        ).use { c ->
            val iMin = c.getColumnIndexOrThrow("minute")
            val iKind = c.getColumnIndexOrThrow("kind")
            val iS = c.getColumnIndexOrThrow("measured_s")
            val iE = c.getColumnIndexOrThrow("energy_pa2s")
            val iD = c.getColumnIndexOrThrow("niosh_dose")
            val iCat = c.getColumnIndexOrThrow("category")
            val iConf = c.getColumnIndexOrThrow("confidence")
            val iOrg = c.getColumnIndexOrThrow("origin")
            while (c.moveToNext()) {
                out += MinuteRecord(
                    minute = c.getLong(iMin),
                    kind = ExposureKind.of(c.getInt(iKind)),
                    measuredSeconds = c.getDouble(iS),
                    energyPa2s = c.getDouble(iE),
                    nioshDosePercent = c.getDouble(iD),
                    category = c.getInt(iCat),
                    confidence = c.getInt(iConf),
                    origin = c.getInt(iOrg),
                )
            }
        }
        return out
    }

    override fun prune(nowMinute: Long) {
        val cutoff = nowMinute - RETENTION_DAYS * 1440
        writableDatabase.delete(T, "minute < ?", arrayOf(cutoff.toString()))
        alerts.prune(cutoff * 60_000L)
    }

    /** "Delete all data" (asked by the phone): every minute and every alert. */
    fun clearAll() {
        writableDatabase.delete(T, null, null)
        writableDatabase.delete(A, null, null)
    }

    /** The alert history, in the same database and with the same retention as the minutes. */
    val alerts: AlertLog = object : AlertLog {
        override fun add(kind: AlertKind, value: Double, level: Int, atMs: Long): Long =
            writableDatabase.insert(A, null, ContentValues().apply {
                put("at_ms", atMs); put("kind", kind.name); put("value", value); put("level", level)
            })

        override fun resolveLatest(kind: AlertKind, acknowledged: Boolean, snoozed: Boolean) {
            writableDatabase.execSQL(
                "UPDATE $A SET ack = ?, snoozed = ? WHERE id = " +
                    "(SELECT id FROM $A WHERE kind = ? AND ack = 0 AND snoozed = 0 ORDER BY id DESC LIMIT 1)",
                arrayOf<Any>(if (acknowledged) 1 else 0, if (snoozed) 1 else 0, kind.name),
            )
        }

        override fun page(fromMs: Long, afterId: Long, limit: Int): List<AlertRecord> = query(
            "at_ms >= ? AND id > ?", arrayOf(fromMs.toString(), afterId.toString()), "id ASC", limit,
        )

        override fun latest(): AlertRecord? = query(null, null, "at_ms DESC, id DESC", 1).firstOrNull()

        override fun prune(beforeMs: Long) {
            writableDatabase.delete(A, "at_ms < ?", arrayOf(beforeMs.toString()))
        }

        override fun clear() {
            writableDatabase.delete(A, null, null)
        }

        private fun query(where: String?, args: Array<String>?, order: String, limit: Int): List<AlertRecord> {
            val out = ArrayList<AlertRecord>()
            readableDatabase.query(A, null, where, args, null, null, order, limit.toString()).use { c ->
                while (c.moveToNext()) {
                    val kind = runCatching { AlertKind.valueOf(c.getString(c.getColumnIndexOrThrow("kind"))) }.getOrNull()
                        ?: continue
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
    }

    /** Clears one kind's minutes in a range (manual "reset today's dose"). */
    override fun clear(fromMinute: Long, toMinute: Long, kind: ExposureKind) {
        writableDatabase.delete(
            T, "minute >= ? AND minute < ? AND kind = ?",
            arrayOf(fromMinute.toString(), toMinute.toString(), kind.code.toString()),
        )
    }
}
