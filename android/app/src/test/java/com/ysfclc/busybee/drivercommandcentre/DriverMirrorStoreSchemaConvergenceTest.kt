package com.ysfclc.busybee.drivercommandcentre

import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DriverMirrorStoreSchemaConvergenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val databaseName = "busybee_driver_mirror.db"

    @Before
    fun cleanBefore() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun mainVersionOneUpgradesThroughAllAdditiveStagesAndPreservesRecords() {
        val db = createLegacyDatabase(1, includeCorrections = false, includeProviderEvents = false, includeDuration = false, includeConfidence = false)
        insertRecord(db, includeDuration = false, includeConfidence = false)
        db.close()

        val store = DriverMirrorStore(context)
        try {
            val upgraded = store.writableDatabase
            assertEquals(5, upgraded.version)
            assertRecordColumns(upgraded)
            assertTrue(tableExists(upgraded, "record_corrections"))
            assertTrue(tableExists(upgraded, "provider_events"))
            assertEquals(1, scalar(upgraded, "SELECT COUNT(*) FROM records WHERE id=1"))
            assertEquals("legacy raw", stringValue(upgraded, "SELECT raw FROM records WHERE id=1"))
        } finally {
            store.close()
        }
    }

    @Test
    fun nativeBridgeVersionTwoPreservesCorrectionsAndAddsMapFirstColumnsAndEvents() {
        val db = createLegacyDatabase(2, includeCorrections = true, includeProviderEvents = false, includeDuration = false, includeConfidence = false)
        insertRecord(db, includeDuration = false, includeConfidence = false)
        db.execSQL("INSERT INTO record_corrections(record_id,corrected_at,before_values,after_values) VALUES(1,100,'before','after')")
        db.close()

        val store = DriverMirrorStore(context)
        try {
            val upgraded = store.writableDatabase
            assertEquals(5, upgraded.version)
            assertRecordColumns(upgraded)
            assertEquals(1, scalar(upgraded, "SELECT COUNT(*) FROM record_corrections WHERE after_values='after'"))
            assertTrue(tableExists(upgraded, "provider_events"))
            assertEquals(1, scalar(upgraded, "SELECT COUNT(*) FROM records WHERE id=1"))
        } finally {
            store.close()
        }
    }

    @Test
    fun mapFirstVersionFourPreservesProviderEventsAndAddsCorrectionHistory() {
        val db = createLegacyDatabase(4, includeCorrections = false, includeProviderEvents = true, includeDuration = true, includeConfidence = true)
        insertRecord(db, includeDuration = true, includeConfidence = true)
        db.execSQL("INSERT INTO provider_events(provider,dedupe_key,event_time,received_at,event_blob) VALUES('UBER','event-1',100,110,'legacy-event')")
        db.close()

        val store = DriverMirrorStore(context)
        try {
            val upgraded = store.writableDatabase
            assertEquals(5, upgraded.version)
            assertRecordColumns(upgraded)
            assertEquals(1, scalar(upgraded, "SELECT COUNT(*) FROM provider_events WHERE event_blob='legacy-event'"))
            assertTrue(tableExists(upgraded, "record_corrections"))
            assertEquals(1, scalar(upgraded, "SELECT COUNT(*) FROM records WHERE id=1"))
        } finally {
            store.close()
        }
    }

    @Test
    fun freshDatabaseCreatesConvergedSchema() {
        val store = DriverMirrorStore(context)
        try {
            val db = store.writableDatabase
            assertEquals(5, db.version)
            assertRecordColumns(db)
            assertTrue(tableExists(db, "record_corrections"))
            assertTrue(tableExists(db, "provider_events"))
        } finally {
            store.close()
        }
    }

    private fun createLegacyDatabase(
        version: Int,
        includeCorrections: Boolean,
        includeProviderEvents: Boolean,
        includeDuration: Boolean,
        includeConfidence: Boolean
    ): SQLiteDatabase {
        val file: File = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            execSQL("""CREATE TABLE imports(
                id INTEGER PRIMARY KEY AUTOINCREMENT, source_name TEXT NOT NULL,
                imported_at INTEGER NOT NULL, row_count INTEGER NOT NULL, inserted_count INTEGER NOT NULL
            )""")
            val duration = if (includeDuration) "duration_minutes REAL," else ""
            val confidence = if (includeConfidence) "parse_confidence REAL," else ""
            execSQL("""CREATE TABLE records(
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
                $duration
                $confidence
                gross REAL,
                fees REAL,
                bonus REAL,
                currency TEXT NOT NULL,
                status TEXT,
                raw TEXT NOT NULL,
                source_name TEXT NOT NULL
            )""")
            if (includeCorrections) {
                execSQL("""CREATE TABLE record_corrections(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    record_id INTEGER NOT NULL,
                    corrected_at INTEGER NOT NULL,
                    before_values TEXT NOT NULL,
                    after_values TEXT NOT NULL
                )""")
            }
            if (includeProviderEvents) {
                execSQL("""CREATE TABLE provider_events(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    provider TEXT NOT NULL,
                    dedupe_key TEXT NOT NULL,
                    event_time INTEGER NOT NULL,
                    received_at INTEGER NOT NULL,
                    event_blob TEXT NOT NULL,
                    UNIQUE(provider,dedupe_key)
                )""")
                execSQL("CREATE INDEX provider_events_time_idx ON provider_events(event_time DESC, received_at DESC)")
            }
            version = version
        }
    }

    private fun insertRecord(db: SQLiteDatabase, includeDuration: Boolean, includeConfidence: Boolean) {
        val columns = buildList {
            add("id"); add("fingerprint"); add("import_id"); add("platform"); add("record_type")
            add("external_id"); add("occurred_at"); add("pickup"); add("dropoff"); add("distance_km")
            if (includeDuration) add("duration_minutes")
            if (includeConfidence) add("parse_confidence")
            addAll(listOf("gross", "fees", "bonus", "currency", "status", "raw", "source_name"))
        }
        val values = columns.joinToString(",") { "?" }
        val args = columns.map { column ->
            when (column) {
                "id" -> 1L
                "fingerprint" -> "fp-1"
                "import_id" -> 1L
                "platform" -> "Uber"
                "record_type" -> "trip"
                "external_id" -> "trip-1"
                "occurred_at" -> "2026-11-25T12:00:00Z"
                "pickup" -> "Centre"
                "dropoff" -> "Airport"
                "distance_km" -> 12.0
                "duration_minutes" -> 28.0
                "parse_confidence" -> 0.95
                "gross" -> 50.0
                "fees" -> 5.0
                "bonus" -> 2.0
                "currency" -> "PLN"
                "status" -> "completed"
                "raw" -> "legacy raw"
                "source_name" -> "legacy.csv"
                else -> error("Unexpected fixture column: $column")
            }
        }.toTypedArray()
        db.execSQL("INSERT INTO records(${columns.joinToString(",")}) VALUES($values)", args)
    }

    private fun assertRecordColumns(db: SQLiteDatabase) {
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(records)", null).use { cursor ->
            while (cursor.moveToNext()) columns.add(cursor.getString(1))
        }
        assertTrue(columns.contains("duration_minutes"))
        assertTrue(columns.contains("parse_confidence"))
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table)
        ).use { it.moveToFirst() }

    private fun scalar(db: SQLiteDatabase, sql: String): Int =
        db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun stringValue(db: SQLiteDatabase, sql: String): String =
        db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getString(0) else "" }
}
