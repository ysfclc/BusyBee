package com.ysfclc.busybee.drivercommandcentre

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
class DriverMirrorStoreCorrectionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val databaseName = "busybee_driver_mirror.db"

    @Before
    fun clean() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun correctionsUpdateDerivedRecordButPreserveOriginalEvidenceAndAuditHistory() {
        val store = DriverMirrorStore(context)
        val sourceRow = DriverCsv.parse(
            "platform,record_type,occurred_at,pickup,dropoff,distance_km,gross,fees,bonus,currency,status\n" +
                "Uber,trip,2026-11-25T12:00:00,Old Town,Podgorze,10,40,8,0,PLN,completed"
        ).single()
        assertEquals(1, store.importRows("uber-export.csv", listOf(sourceRow)).second)
        val original = store.recent(1).single()

        assertTrue(store.correctRecord(
            original.id,
            DriverRecordCorrection(
                occurredAt = original.occurredAt,
                pickup = original.pickup,
                dropoff = original.dropoff,
                distanceKm = 12.0,
                gross = 44.0,
                fees = 8.0,
                bonus = 2.0,
                currency = "PLN",
                status = original.status
            )
        ))

        val corrected = store.recent(1).single()
        assertEquals(12.0, corrected.distanceKm!!, 0.0001)
        assertEquals(44.0, corrected.gross!!, 0.0001)
        assertEquals(original.raw, corrected.raw)
        assertEquals(original.sourceName, corrected.sourceName)
        val history = store.correctionHistory(original.id)
        assertEquals(1, history.size)
        assertTrue(history.single().contains("\"gross\":40.0"))
        assertTrue(history.single().contains("\"gross\":44.0"))
        store.close()
    }

    @Test
    fun missingRecordCannotBeCorrected() {
        val store = DriverMirrorStore(context)
        assertFalse(store.correctRecord(
            999L,
            DriverRecordCorrection(null, null, null, null, null, null, null, "", null)
        ))
        store.close()
    }

    @Test
    fun versionOneDatabaseReceivesAdditiveCorrectionHistoryMigration() {
        context.openOrCreateDatabase(databaseName, 0, null).use { db ->
            db.execSQL("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT)")
            db.version = 1
        }
        val store = DriverMirrorStore(context)
        store.writableDatabase
        store.readableDatabase.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='record_corrections'",
            null
        ).use { cursor -> assertTrue(cursor.moveToFirst()) }
        store.close()
    }
}
