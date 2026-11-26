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
class DriverCommandStoreSchemaConvergenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val databaseName = "busybee_command_centre.db"

    @Before
    fun cleanBefore() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun mapFirstVersionThreeKeepsWhatsAppDataAndGainsProviderOutbox() {
        val legacy = createVersionThreeDatabase()
        legacy.execSQL("""CREATE TABLE whatsapp_groups(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            package_name TEXT NOT NULL,
            group_title TEXT NOT NULL,
            tracking_enabled INTEGER NOT NULL DEFAULT 0,
            first_seen_at INTEGER NOT NULL,
            last_seen_at INTEGER NOT NULL,
            UNIQUE(package_name, group_title)
        )""")
        legacy.execSQL("""CREATE TABLE whatsapp_group_messages(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            group_id INTEGER NOT NULL,
            fingerprint TEXT NOT NULL UNIQUE,
            sender TEXT NOT NULL,
            body TEXT NOT NULL,
            message_at INTEGER NOT NULL,
            FOREIGN KEY(group_id) REFERENCES whatsapp_groups(id) ON DELETE CASCADE
        )""")
        legacy.execSQL(
            "INSERT INTO whatsapp_groups(package_name,group_title,tracking_enabled,first_seen_at,last_seen_at) VALUES(?,?,?,?,?)",
            arrayOf("com.whatsapp", "Driver loads", 1, 100L, 200L)
        )
        legacy.version = 3
        legacy.close()

        val store = DriverCommandStore(context)
        try {
            val db = store.writableDatabase
            assertEquals(4, db.version)
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM whatsapp_groups WHERE group_title='Driver loads'"))
            assertTrue(tableExists(db, "whatsapp_group_messages"))
            assertTrue(tableExists(db, "provider_event_outbox"))
            assertEquals(0, scalar(db, "SELECT COUNT(*) FROM provider_event_outbox"))
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM offers WHERE id=1"))
        } finally {
            store.close()
        }
    }

    @Test
    fun nativeBridgeVersionThreeKeepsOutboxRowsAndGainsWhatsAppTables() {
        val legacy = createVersionThreeDatabase()
        legacy.execSQL("""CREATE TABLE provider_event_outbox(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            idempotency_key TEXT NOT NULL UNIQUE,
            offer_id INTEGER NOT NULL,
            event_type TEXT NOT NULL,
            decision TEXT,
            occurred_at_ms INTEGER,
            created_at_ms INTEGER NOT NULL,
            delivered_at_ms INTEGER,
            attempt_count INTEGER NOT NULL DEFAULT 0,
            last_error TEXT,
            FOREIGN KEY(offer_id) REFERENCES offers(id)
        )""")
        legacy.execSQL("INSERT INTO provider_event_outbox(idempotency_key,offer_id,event_type,created_at_ms) VALUES('offer:1:observed',1,'OFFER_OBSERVED',300)")
        legacy.version = 3
        legacy.close()

        val store = DriverCommandStore(context)
        try {
            val db = store.writableDatabase
            assertEquals(4, db.version)
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM provider_event_outbox WHERE idempotency_key='offer:1:observed'"))
            assertTrue(tableExists(db, "whatsapp_groups"))
            assertTrue(tableExists(db, "whatsapp_group_messages"))
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM offers WHERE id=1"))
        } finally {
            store.close()
        }
    }

    @Test
    fun freshDatabaseCreatesBothFeatureSets() {
        val store = DriverCommandStore(context)
        try {
            val db = store.writableDatabase
            assertEquals(4, db.version)
            listOf("whatsapp_groups", "whatsapp_group_messages", "provider_event_outbox").forEach {
                assertTrue("Missing converged table: $it", tableExists(db, it))
            }
        } finally {
            store.close()
        }
    }

    private fun createVersionThreeDatabase(): SQLiteDatabase {
        val file: File = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            execSQL("""CREATE TABLE offers(
                id INTEGER PRIMARY KEY AUTOINCREMENT, platform TEXT NOT NULL, pickup TEXT, destination TEXT,
                fare REAL NOT NULL, bonus REAL NOT NULL, pickup_km REAL NOT NULL, trip_km REAL NOT NULL,
                duration_min REAL NOT NULL, passenger_rating REAL, captured_at INTEGER NOT NULL,
                source TEXT NOT NULL, raw TEXT, status TEXT NOT NULL
            )""")
            execSQL("""INSERT INTO offers(
                id,platform,pickup,destination,fare,bonus,pickup_km,trip_km,duration_min,
                passenger_rating,captured_at,source,raw,status
            ) VALUES(1,'Uber','Centre','Airport',20,2,1,10,20,4.9,100,'manual','preserved offer','pending')""")
            version = 3
        }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table)
        ).use { it.moveToFirst() }

    private fun scalar(db: SQLiteDatabase, sql: String): Int =
        db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
