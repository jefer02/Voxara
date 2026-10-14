package com.example.voxara.data

import android.database.sqlite.SQLiteDatabase
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.ledger.ExposureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A watch updated from the previous version keeps every minute it recorded: the v1 → v2 upgrade
 * only adds the alert-history table.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ExposureDbUpgradeTest {

    @Test
    fun `upgrading from v1 keeps the minutes and adds the alert history`() {
        val context = RuntimeEnvironment.getApplication()
        val file = context.getDatabasePath("voxara_exposure.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
            v1.execSQL(
                """
                CREATE TABLE minutes (
                    minute INTEGER NOT NULL, kind INTEGER NOT NULL, measured_s REAL NOT NULL,
                    energy_pa2s REAL NOT NULL, niosh_dose REAL NOT NULL, category INTEGER NOT NULL DEFAULT 0,
                    confidence INTEGER NOT NULL DEFAULT 0, origin INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (minute, kind, origin)
                )
                """.trimIndent()
            )
            for (m in 1_000L until 1_060L) {
                v1.execSQL("INSERT INTO minutes VALUES ($m, 0, 60.0, 0.012, 0.25, 0, 0, 0)")
            }
            v1.version = 1
        }

        val db = ExposureDb(context)
        assertEquals(60, db.range(0, Long.MAX_VALUE, ExposureKind.AMBIENT).size)
        assertEquals(2, db.readableDatabase.version)
        val id = db.alerts.add(AlertKind.DAILY_100, 101.0, 100, 5_000L)
        db.alerts.resolveLatest(AlertKind.DAILY_100, acknowledged = true, snoozed = false)
        val stored = db.alerts.page(0L, 0L, 10).single()
        assertEquals(id, stored.id)
        assertTrue(stored.acknowledged)
        assertFalse(stored.snoozed)
        assertEquals(100, stored.level)

        db.clearAll()
        assertTrue(db.range(0, Long.MAX_VALUE, ExposureKind.AMBIENT).isEmpty())
        assertTrue(db.alerts.page(0L, 0L, 10).isEmpty())
        db.close()
    }
}
