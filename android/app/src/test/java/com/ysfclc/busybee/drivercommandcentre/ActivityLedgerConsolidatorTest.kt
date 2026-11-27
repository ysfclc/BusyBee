package com.ysfclc.busybee.drivercommandcentre

import android.content.ContentValues
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
class ActivityLedgerConsolidatorTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sourceName = UnifiedActivityLedgerSchema.DATABASE_NAME
    private val targetName = "busybee_driver_mirror.db"

    @Before
    fun cleanBefore() {
        clean()
    }

    @After
    fun cleanAfter() {
        clean()
    }

    @Test
    fun consolidatesAllTablesPreservingIdsAndHistoricalMarkers() {
        createSourceFixture()

        val report = ActivityLedgerConsolidator(context) { 50_000L }.use { it.consolidateIfNeeded() }

        assertTrue(report.complete)
        assertEquals(5, report.tables.size)
        assertEquals(5, report.copiedRows)
        val db = DriverMirrorStore(context).readableDatabase
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM activity_evidence WHERE id=7 AND origin_key='evidence-7'"))
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM activity_observations WHERE id=9 AND evidence_id=7"))
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM provider_event_envelopes WHERE event_id='event-1' AND payload_json LIKE '%offer%'"))
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM migration_sources WHERE source_database='legacy.db' AND source_table='records' AND state='complete'"))
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM migration_rows WHERE source_database='legacy.db' AND source_table='records' AND source_row_id='42'"))
        assertEquals(5, scalar(db, "SELECT COUNT(*) FROM migration_rows WHERE source_database='__consolidation__:busybee_driver_activity.db'"))
        assertEquals(0, scalar(db, "SELECT COUNT(*) FROM provider_events"))

        // Source remains present and unchanged for rollback.
        val source = SQLiteDatabase.openDatabase(
            context.getDatabasePath(sourceName).absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        source.use {
            assertEquals(1, scalar(it, "SELECT COUNT(*) FROM activity_evidence WHERE id=7"))
            assertEquals(1, scalar(it, "SELECT COUNT(*) FROM provider_events WHERE event_id='event-1'"))
        }
    }

    @Test
    fun secondRunIsIdempotentAndVerifiesExistingPayloads() {
        createSourceFixture()
        val (first, second) = ActivityLedgerConsolidator(context) { 60_000L }.use { consolidator ->
            consolidator.consolidateIfNeeded() to consolidator.consolidateIfNeeded()
        }

        assertTrue(first.complete)
        assertTrue(second.complete)
        assertEquals(5, first.copiedRows)
        assertEquals(0, second.copiedRows)
        assertEquals(5, second.tables.sumOf { it.alreadyPresentRows })
        val db = DriverMirrorStore(context).readableDatabase
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM activity_evidence WHERE id=7"))
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM activity_observations WHERE id=9"))
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM provider_event_envelopes WHERE event_id='event-1'"))
    }

    @Test
    fun detectsSourceChangesUntilTheNextConsolidationRun() {
        createSourceFixture()
        val results = ActivityLedgerConsolidator(context).use { consolidator ->
            assertTrue(consolidator.consolidateIfNeeded().complete)
            SQLiteDatabase.openDatabase(
                context.getDatabasePath(sourceName).absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { source ->
                source.execSQL(
                    """INSERT INTO activity_evidence(
                        id,origin_key,source_database,source_table,source_row_id,provider,captured_at_ms,
                        received_at_ms,evidence_kind,content_digest,content,content_ref
                    ) VALUES(8,'evidence-8','legacy.db','raw','8','Bolt',190,200,'notification','digest-8','second payload','ref-8')"""
                )
            }
            val staleParity = consolidator.verifyParity()
            val refreshed = consolidator.consolidateIfNeeded()
            val currentParity = consolidator.verifyParity()
            Triple(staleParity, refreshed, currentParity)
        }

        assertFalse(results.first.complete)
        assertTrue(results.second.complete)
        assertTrue(results.third.complete)
        assertEquals(2, results.third.tables.first { it.sourceTable == "activity_evidence" }.alreadyPresentRows)
    }

    @Test
    fun conflictingExistingTargetPayloadFailsWithoutWritingCompletionMarker() {
        createSourceFixture()
        val store = DriverMirrorStore(context)
        store.writableDatabase.insertOrThrow(
            "activity_evidence",
            null,
            ContentValues().apply {
                put("id", 7L)
                put("origin_key", "evidence-7")
                put("source_database", "legacy.db")
                put("source_table", "raw")
                put("source_row_id", "7")
                put("provider", "Uber")
                put("received_at_ms", 100L)
                put("evidence_kind", "notification")
                put("content_digest", "wrong-digest")
                put("content", "tampered")
            }
        )
        store.close()

        val parity = ActivityLedgerConsolidator(context).use { it.verifyParity() }
        assertFalse(parity.complete)
        val error = runCatching { ActivityLedgerConsolidator(context).use { it.consolidateIfNeeded() } }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        val db = DriverMirrorStore(context).readableDatabase
        assertEquals(0, scalar(db, "SELECT COUNT(*) FROM migration_rows WHERE source_database='__consolidation__:busybee_driver_activity.db' AND source_table='activity_evidence'"))
        assertEquals(0, scalar(db, "SELECT COUNT(*) FROM migration_sources WHERE source_database='__consolidation__:busybee_driver_activity.db' AND source_table='activity_evidence' AND state='complete'"))
    }

    @Test
    fun missingSourceIsNotCreatedOrReportedComplete() {
        val report = ActivityLedgerConsolidator(context).use { it.consolidateIfNeeded() }

        assertFalse(report.complete)
        assertTrue(report.missingSource)
        assertFalse(context.getDatabasePath(sourceName).exists())
        assertFalse(context.getDatabasePath(targetName).exists())
    }

    private fun createSourceFixture() {
        val file = context.getDatabasePath(sourceName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            UnifiedActivityLedgerSchema.create(db)
            db.execSQL(
                """INSERT INTO activity_evidence(
                    id,origin_key,source_database,source_table,source_row_id,provider,captured_at_ms,
                    received_at_ms,evidence_kind,content_digest,content,content_ref
                ) VALUES(7,'evidence-7','legacy.db','raw','7','Uber',90,100,'notification','digest-7','raw payload','ref-7')"""
            )
            db.execSQL(
                """INSERT INTO activity_observations(
                    id,origin_key,provider,observation_kind,source_method,source_occurred_at_ms,
                    received_at_ms,classification,lifecycle_id,status,amount,currency,pickup,
                    destination,distance_km,duration_min,evidence_id
                ) VALUES(9,'observation-9','Uber','offer','USER_CAPTURE',95,110,'unclassified',
                    'trip-9','pending',42.5,'PLN','Centre','Airport',12.0,25.0,7)"""
            )
            db.execSQL(
                """INSERT INTO provider_events(
                    event_id,idempotency_key,provider,adapter_id,adapter_version,event_type,
                    lifecycle_id,source_method,evidence_class,source_event_id,source_occurred_at_ms,
                    received_at_ms,expires_at_ms,evidence_ref,evidence_digest,expiry_evidence,payload_json
                ) VALUES('event-1','idem-1','Uber','adapter-1','1','offer',NULL,'ACCESSIBILITY',
                    'LOCAL_CAPTURE',NULL,120,130,500,'ref','digest',NULL,'{"kind":"offer"}')"""
            )
            db.execSQL(
                """INSERT INTO migration_sources(
                    source_database,source_table,source_schema_version,started_at_ms,completed_at_ms,
                    source_row_count,migrated_row_count,verification_digest,state
                ) VALUES('legacy.db','records',3,10,20,1,1,'history-digest','complete')"""
            )
            db.execSQL(
                """INSERT INTO migration_rows(
                    source_database,source_table,source_row_id,target_table,target_row_id,migrated_at_ms
                ) VALUES('legacy.db','records','42','activity_observations',9,20)"""
            )
            db.version = UnifiedActivityLedgerSchema.VERSION
        }
    }

    private fun scalar(db: SQLiteDatabase, sql: String): Int =
        db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun clean() {
        listOf(sourceName, targetName).forEach { context.deleteDatabase(it) }
    }
}
