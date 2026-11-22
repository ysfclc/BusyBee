package com.ysfclc.busybee.drivercommandcentre

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SqliteProviderEventStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clean() {
        context.deleteDatabase(UnifiedActivityLedgerSchema.DATABASE_NAME)
    }

    @After
    fun cleanAfter() = clean()

    @Test
    fun persistsEventAndEnforcesIdempotencyAcrossStoreInstances() {
        val event = sampleEvent()
        SqliteProviderEventStore(context).use { first ->
            assertFalse(first.containsIdempotencyKey(event.idempotencyKey))
            assertTrue(first.append(event))
            assertTrue(first.containsIdempotencyKey(event.idempotencyKey))
        }
        SqliteProviderEventStore(context).use { reopened ->
            assertTrue(reopened.containsIdempotencyKey(event.idempotencyKey))
            assertFalse(reopened.append(event))
        }

        val helper = UnifiedActivityLedger(context)
        try {
            val db = helper.readableDatabase
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM provider_events"))
            db.rawQuery("SELECT evidence_digest,payload_json FROM provider_events WHERE event_id='event-1'", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("digest-1", it.getString(0))
                assertTrue(it.getString(1).contains("\"fare\":12.5"))
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun repositoryMergesObservationsAndCanonicalEventsWithoutInventingSourceTime() {
        val ledger = UnifiedActivityLedger(context)
        val db = ledger.writableDatabase
        db.execSQL(
            """INSERT INTO activity_observations
               (origin_key,provider,observation_kind,source_method,source_occurred_at_ms,
                received_at_ms,classification)
               VALUES('notice:1','Uber','notification','ANDROID_NOTIFICATION',NULL,1000,'unclassified')"""
        )
        SqliteProviderEventStore(context).use { it.append(sampleEvent()) }

        UnifiedActivityRepository(context).use { repository ->
            val items = repository.timeline()
            assertEquals(2, items.size)
            assertEquals("provider_event", items[0].sourceKind)
            assertNull(items[0].sourceOccurredAtMillis)
            assertEquals(2000L, items[0].timelineTimestampMillis)
            assertEquals("observation", items[1].sourceKind)
            assertNull(items[1].sourceOccurredAtMillis)
            assertEquals(1000L, items[1].timelineTimestampMillis)
            assertEquals("new", items[1].status)
            assertEquals(12.5, items[1].amount!!, 0.001)
            assertEquals("Airport", items[1].destination)
        }
        ledger.close()
    }

    @Test
    fun upgradesVersionOneLedgerAdditively() {
        val file = context.getDatabasePath(UnifiedActivityLedgerSchema.DATABASE_NAME)
        file.parentFile?.mkdirs()
        val old = object : SQLiteOpenHelper(context, UnifiedActivityLedgerSchema.DATABASE_NAME, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                UnifiedActivityLedgerSchema.createStatements.forEach { statement ->
                    db.execSQL(statement.replace("            evidence_digest TEXT,\n", ""))
                }
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        old.writableDatabase
        old.close()

        val helper = UnifiedActivityLedger(context)
        try {
            val db = helper.writableDatabase
            var found = false
            db.rawQuery("PRAGMA table_info(provider_events)", null).use { c ->
                while (c.moveToNext()) if (c.getString(1) == "evidence_digest") found = true
            }
            assertTrue(found)
            assertEquals(2, db.version)
        } finally {
            helper.close()
        }
    }

    private fun sampleEvent() = ProviderEventEnvelope(
        eventId = "event-1",
        idempotencyKey = "uber:offer:one",
        providerId = "uber",
        adapterId = "uber-notification",
        adapterVersion = "1",
        eventType = ProviderEventType.OFFER_OBSERVED,
        lifecycleId = "offer-one",
        sourceMethod = ProviderSourceMethod.ANDROID_NOTIFICATION,
        evidenceClass = ProviderEvidenceClass.USER_VISIBLE,
        sourceEventId = null,
        occurredAtMillis = null,
        receivedAtMillis = 2000L,
        evidenceReference = "notification://one",
        evidenceDigest = "digest-1",
        payload = ProviderEventPayload.Offer(fare = 12.5, currency = "PLN")
    )

    private fun scalar(db: SQLiteDatabase, sql: String): Int =
        db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
