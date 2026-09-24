package com.example.voxara.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.MinuteStore

/**
 * MINUTE LEDGER STORAGE — one row per minute and exposure kind.
 *
 * Framework SQLite (no extra dependency, no annotation processor): the schema is tiny and every
 * rule that matters lives in the pure `core.ledger` code. Rows hold numbers only — no audio, no
 * device names, no media titles or app names. Kept for [RETENTION_DAYS], then pruned.
 */
class ExposureDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION), MinuteStore {

    companion object {
        private const val NAME = "voxara_exposure.db"
        private const val VERSION = 1
        const val RETENTION_DAYS = 35L
        private const val T = "minutes"

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
        writableDatabase.delete(T, "minute < ?", arrayOf((nowMinute - RETENTION_DAYS * 1440).toString()))
    }

    /** Clears one kind's minutes in a range (manual "reset today's dose"). */
    override fun clear(fromMinute: Long, toMinute: Long, kind: ExposureKind) {
        writableDatabase.delete(
            T, "minute >= ? AND minute < ? AND kind = ?",
            arrayOf(fromMinute.toString(), toMinute.toString(), kind.code.toString()),
        )
    }
}
