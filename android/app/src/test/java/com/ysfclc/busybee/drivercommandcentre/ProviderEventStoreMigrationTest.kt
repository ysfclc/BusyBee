package com.ysfclc.busybee.drivercommandcentre

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProviderEventStoreMigrationTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    private fun createLegacyDatabase(version: Int) {
        context.deleteDatabase("busybee_driver_mirror.db")
        val db = context.openOrCreateDatabase("busybee_driver_mirror.db", 0, null)
        db.execSQL("""
            CREATE TABLE imports (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_name TEXT NOT NULL,
                imported_at INTEGER NOT NULL,
                row_count INTEGER NOT NULL,
                inserted_count INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE raw_evidence (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                import_id INTEGER NOT NULL,
                row_number INTEGER NOT NULL,
                fingerprint TEXT NOT NULL,
                raw TEXT NOT NULL,
                source_name TEXT NOT NULL,
                FOREIGN KEY(import_id) REFERENCES imports(id)
            )
        """.trimIndent())
        val durationColumn = if (version >= 2) "duration_minutes REAL," else ""
        val confidenceColumn = if (version >= 3) "parse_confidence REAL," else ""
        db.execSQL("""
            CREATE TABLE records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fingerprint TEXT NOT NULL UNIQUE,
                import_id INTEGER NOT NULL,
                platform TEXT NOT NULL,
                record_type TEXT NOT NULL,
                external_id TEXT,
                occurred_at TEXT,
                pickup TEXT,
                dropoff TEXT,
                distance_km REAL,
                $durationColumn
                $confidenceColumn
                gross REAL,
                fees REAL,
                bonus REAL,
                currency TEXT NOT NULL,
                status TEXT,
                raw TEXT NOT NULL,
                source_name TEXT NOT NULL,
                FOREIGN KEY(import_id) REFERENCES imports(id)
            )
        """.trimIndent())
        db.execSQL("INSERT INTO imports(source_name, imported_at, row_count, inserted_count) VALUES('legacy.csv', 1, 1, 1)")
        db.execSQL("INSERT INTO raw_evidence(import_id, row_number, fingerprint, raw, source_name) VALUES(1, 1, 'legacy-fp', 'original-row', 'legacy.csv')")
        db.execSQL("""
            INSERT INTO records(fingerprint, import_id, platform, record_type, currency, raw, source_name)
            VALUES('legacy-fp', 1, 'Uber', 'trip', 'PLN', 'original-row', 'legacy.csv')
        """.trimIndent())
        db.version = version
        db.close()
    }

    private fun count(dbName: String, table: String): Int {
        val db = context.openOrCreateDatabase(dbName, 0, null)
        return try {
            db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun freshInstallCreatesProviderEventLedger() {
        context.deleteDatabase("busybee_driver_mirror.db")
        val store = DriverMirrorStore(context)
        try {
            val db = store.writableDatabase
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='provider_events'", null).use {
                assertTrue("provider_events table must exist on a fresh install", it.moveToFirst())
            }
        } finally {
            store.close()
            context.deleteDatabase("busybee_driver_mirror.db")
        }
    }

    @Test
    fun populatedVersionOneDatabaseUpgradesWithoutLosingEvidence() {
        createLegacyDatabase(1)
        val store = DriverMirrorStore(context)
        try {
            val db = store.writableDatabase
            assertEquals(1, count("busybee_driver_mirror.db", "imports"))
            assertEquals(1, count("busybee_driver_mirror.db", "records"))
            assertEquals(1, count("busybee_driver_mirror.db", "raw_evidence"))
            db.rawQuery("PRAGMA table_info(records)", null).use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns.add(cursor.getString(1))
                assertTrue(columns.contains("duration_minutes"))
                assertTrue(columns.contains("parse_confidence"))
            }
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='provider_events'", null).use {
                assertTrue("provider_events table must be added during upgrade", it.moveToFirst())
            }
        } finally {
            store.close()
            context.deleteDatabase("busybee_driver_mirror.db")
        }
    }

    @Test
    fun populatedVersionThreeDatabaseAddsOnlyProviderEventLedger() {
        createLegacyDatabase(3)
        val store = DriverMirrorStore(context)
        try {
            val db = store.writableDatabase
            assertEquals(1, count("busybee_driver_mirror.db", "imports"))
            assertEquals(1, count("busybee_driver_mirror.db", "records"))
            assertEquals(1, count("busybee_driver_mirror.db", "raw_evidence"))
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='provider_events'", null).use {
                assertTrue("provider_events table must be added to version 3", it.moveToFirst())
            }
        } finally {
            store.close()
            context.deleteDatabase("busybee_driver_mirror.db")
        }
    }
}
