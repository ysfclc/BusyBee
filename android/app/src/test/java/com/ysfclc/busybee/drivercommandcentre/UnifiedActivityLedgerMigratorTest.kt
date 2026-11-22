package com.ysfclc.busybee.drivercommandcentre

import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UnifiedActivityLedgerMigratorTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun cleanDatabases() {
        listOf(
            UnifiedActivityLedgerSchema.DATABASE_NAME,
            "busybee_driver_mirror.db",
            "busybee_command_centre.db"
        ).forEach { context.deleteDatabase(it) }
    }

    @After
    fun cleanAfterTest() = cleanDatabases()

    @Test
    fun absentLegacyDatabasesAreNotCreatedOrMarkedComplete() {
        val report = UnifiedActivityLedgerMigrator(context).migrateIfNeeded()
        assertFalse(report.complete)
        assertEquals(
            setOf("busybee_driver_mirror.db", "busybee_command_centre.db"),
            report.missingDatabases.toSet()
        )
        assertFalse(context.getDatabasePath("busybee_driver_mirror.db").exists())
        assertFalse(context.getDatabasePath("busybee_command_centre.db").exists())
    }

    @Test
    fun migratesEvidenceAndObservationsWithoutClassifyingThemAsCanonicalEvents() {
        createMirrorFixture()
        createCommandFixture()

        val report = UnifiedActivityLedgerMigrator(context) { 9_999L }.migrateIfNeeded()
        assertTrue(report.complete)
        assertEquals(7, report.copiedRows)

        val db = UnifiedActivityLedger(context).readableDatabase
        assertEquals(5, scalar(db, "SELECT COUNT(*) FROM activity_evidence"))
        assertEquals(4, scalar(db, "SELECT COUNT(*) FROM activity_observations"))
        assertEquals(7, scalar(db, "SELECT COUNT(*) FROM migration_rows"))
        assertEquals(5, scalar(db, "SELECT COUNT(*) FROM migration_sources WHERE state='complete'"))
        assertEquals(0, scalar(db, "SELECT COUNT(*) FROM provider_events"))
        assertEquals(2, scalar(db, "SELECT COUNT(*) FROM activity_evidence WHERE source_table='raw_evidence'"))
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM activity_observations WHERE evidence_id IS NOT NULL AND origin_key LIKE '%:records:%'"))
        db.rawQuery(
            "SELECT verification_digest FROM migration_sources WHERE source_table='records'",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(64, cursor.getString(0).length)
        }
        db.rawQuery(
            "SELECT source_occurred_at_ms,received_at_ms,classification FROM activity_observations WHERE origin_key LIKE '%:records:1'",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(java.time.Instant.parse("2026-11-21T12:00:00Z").toEpochMilli(), cursor.getLong(0))
            assertEquals(1_500L, cursor.getLong(1))
            assertEquals("unclassified", cursor.getString(2))
        }
        db.rawQuery(
            "SELECT source_occurred_at_ms FROM activity_observations WHERE origin_key LIKE '%:records:2'",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        db.rawQuery(
            "SELECT source_method,source_occurred_at_ms FROM activity_observations WHERE observation_kind='notification'",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(ProviderSourceMethod.ANDROID_NOTIFICATION.name, cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
    }

    @Test
    fun rerunningMigrationDoesNotDuplicateCopiedRows() {
        createMirrorFixture()
        createCommandFixture()
        val migrator = UnifiedActivityLedgerMigrator(context) { 10_000L }

        val first = migrator.migrateIfNeeded()
        val second = migrator.migrateIfNeeded()

        assertTrue(first.complete)
        assertTrue(second.complete)
        assertEquals(7, first.copiedRows)
        assertEquals(0, second.copiedRows)
        assertEquals(7, second.tables.sumOf { it.alreadyCopiedRows })
        val db = UnifiedActivityLedger(context).readableDatabase
        assertEquals(5, scalar(db, "SELECT COUNT(*) FROM activity_evidence"))
        assertEquals(4, scalar(db, "SELECT COUNT(*) FROM activity_observations"))
    }

    @Test
    fun missingSourceTableIsNotReportedAsSuccessfullyMigrated() {
        val file = context.getDatabasePath("busybee_driver_mirror.db")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE imports(id INTEGER PRIMARY KEY,source_name TEXT,imported_at INTEGER,row_count INTEGER,inserted_count INTEGER)")
            db.version = 1
        }
        createCommandFixture()
        val report = UnifiedActivityLedgerMigrator(context).migrateIfNeeded()
        assertFalse(report.complete)
        assertFalse(report.tables.single { it.table == "raw_evidence" }.complete)
        assertFalse(report.tables.single { it.table == "records" }.complete)
    }

    private fun createMirrorFixture() {
        val file = context.getDatabasePath("busybee_driver_mirror.db")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE imports(id INTEGER PRIMARY KEY,source_name TEXT,imported_at INTEGER,row_count INTEGER,inserted_count INTEGER)")
            db.execSQL("CREATE TABLE raw_evidence(id INTEGER PRIMARY KEY,import_id INTEGER,row_number INTEGER,fingerprint TEXT,raw TEXT,source_name TEXT)")
            db.execSQL("""CREATE TABLE records(
                id INTEGER PRIMARY KEY,fingerprint TEXT,import_id INTEGER,platform TEXT,record_type TEXT,
                external_id TEXT,occurred_at TEXT,pickup TEXT,dropoff TEXT,distance_km REAL,gross REAL,
                fees REAL,bonus REAL,currency TEXT,status TEXT,raw TEXT,source_name TEXT)""")
            db.execSQL("INSERT INTO imports VALUES(1,'driver.csv',1500,1,1)")
            db.execSQL("INSERT INTO raw_evidence VALUES(1,1,1,'fingerprint-1','platform=Uber&record_type=trip','driver.csv')")
            db.execSQL("INSERT INTO raw_evidence VALUES(2,1,2,'fingerprint-1','duplicate raw row','driver.csv')")
            db.execSQL("""INSERT INTO records VALUES(
                1,'fingerprint-1',1,'Uber','trip','trip-42','2026-11-21T12:00:00Z','Old Town','Airport',
                12.5,50.0,5.0,0.0,'PLN','completed','raw-trip','driver.csv')""")
            db.execSQL("""INSERT INTO records VALUES(
                2,'fingerprint-2',1,'Uber','trip','trip-43','not-an-epoch','Old Town','Airport',
                NULL,NULL,NULL,NULL,'PLN',NULL,'raw-trip-2','driver.csv')""")
            db.version = 1
        }
    }

    private fun createCommandFixture() {
        val file = context.getDatabasePath("busybee_command_centre.db")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("""CREATE TABLE offers(
                id INTEGER PRIMARY KEY,platform TEXT,pickup TEXT,destination TEXT,fare REAL,bonus REAL,
                pickup_km REAL,trip_km REAL,duration_min REAL,passenger_rating REAL,captured_at INTEGER,
                source TEXT,raw TEXT,status TEXT)""")
            db.execSQL("""CREATE TABLE notices(
                id INTEGER PRIMARY KEY,fingerprint TEXT,platform TEXT,title TEXT,body TEXT,captured_at INTEGER)""")
            db.execSQL("INSERT INTO offers VALUES(1,'Bolt','Centre','Airport',40,2,1.5,10,25,NULL,2000,'manual','captured offer','pending')")
            db.execSQL("INSERT INTO notices VALUES(1,'notice-fp','Uber','New trip','Pickup nearby',2500)")
            db.version = 2
        }
    }

    private fun scalar(db: SQLiteDatabase, sql: String): Int =
        db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
