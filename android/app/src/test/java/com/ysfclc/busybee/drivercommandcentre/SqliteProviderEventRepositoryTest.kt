package com.ysfclc.busybee.drivercommandcentre

import android.app.Application
import android.content.ContentValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SqliteProviderEventRepositoryTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun malformedEventBlobIsSkippedAndReportedWithoutDeletingIt() {
        context.deleteDatabase("busybee_driver_mirror.db")
        val store = DriverMirrorStore(context)
        try {
            val db = store.writableDatabase
            val corrupt = ContentValues().apply {
                put("provider", ProviderId.UBER.name)
                put("dedupe_key", "corrupt-event")
                put("event_time", 100L)
                put("received_at", 100L)
                put("event_blob", "not-a-valid-event-blob")
            }
            assertTrue(db.insert("provider_events", null, corrupt) != -1L)

            val repository = SqliteProviderEventRepository(store)
            val valid = ProviderEvent(
                eventId = "valid-event",
                provider = ProviderId.UBER,
                adapter = AdapterIdentity("fixture", "1"),
                type = ProviderEventType.SOURCE_OBSERVATION,
                sourceMethod = SourceMethod.MANUAL,
                occurredAtEpochMs = 110L,
                receivedAtEpochMs = 110L,
                payload = ProviderEventPayload.Observation("Driver note", "Test evidence"),
                completeness = FieldCompleteness(setOf("title", "detail"), setOf("title", "detail"), 1.0),
                evidence = EvidenceReference("valid-fingerprint", retention = EvidenceRetention.LIMITED),
                dedupeKey = "valid-event"
            )
            assertTrue(repository.insertIfNew(valid))

            val events = repository.events(20)
            assertEquals(listOf("valid-event"), events.map { it.eventId })
            assertEquals(1, repository.lastReadFailures)
            assertEquals(1, db.rawQuery("SELECT COUNT(*) FROM provider_events WHERE dedupe_key='corrupt-event'", null).use {
                assertTrue(it.moveToFirst())
                it.getInt(0)
            })
        } finally {
            store.close()
            context.deleteDatabase("busybee_driver_mirror.db")
        }
    }
}
