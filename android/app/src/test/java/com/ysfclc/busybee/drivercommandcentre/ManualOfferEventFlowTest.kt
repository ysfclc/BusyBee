package com.ysfclc.busybee.drivercommandcentre

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
class ManualOfferEventFlowTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun cleanDatabases() {
        deleteDatabases()
    }

    @After
    fun cleanAfterTest() {
        deleteDatabases()
    }

    @Test
    fun savedOfferProducesDurableEventsAndSurvivesLegacyActivityMigration() {
        val capturedAt = 1_700_000_000_000L
        val offer = DriverOffer(
            platform = "Uber",
            pickup = "Kazimierz",
            destination = "Airport",
            fare = 42.5,
            bonus = 3.25,
            pickupKm = 2.5,
            tripKm = 14.0,
            durationMin = 28.0,
            passengerRating = 4.96,
            capturedAt = capturedAt,
            source = "manual",
            raw = "Driver-entered offer details"
        )

        val sourceStore = DriverCommandStore(context)
        val savedId = try {
            val id = sourceStore.saveOffer(offer)
            sourceStore.updateOfferStatus(id, "skipped")
            id
        } finally {
            sourceStore.close()
        }
        val persistedOffer = offer.copy(id = savedId, status = "skipped")

        val observed = ManualOfferEventFactory.observed(persistedOffer)
        val skipped = ManualOfferEventFactory.decisionObserved(
            persistedOffer,
            decision = "skipped_by_driver_in_busybee",
            decidedAtMillis = capturedAt + 5_000L
        )

        SqliteProviderEventStore(context).use { eventStore ->
            val ingestor = ProviderEventIngestor(eventStore)
            assertTrue(ingestor.ingest(observed) is ProviderIngestResult.Accepted)
            assertTrue(ingestor.ingest(observed) is ProviderIngestResult.Duplicate)
            assertTrue(ingestor.ingest(skipped) is ProviderIngestResult.Accepted)
        }

        val migration = UnifiedActivityLedgerMigrator(context) { capturedAt + 10_000L }
            .migrateIfNeeded()
        val offerTable = migration.tables.single { it.database == "busybee_command_centre.db" && it.table == "offers" }
        assertTrue(offerTable.complete)
        assertEquals(1, offerTable.sourceRows)
        assertEquals(1, offerTable.copiedRows)

        UnifiedActivityRepository(context).use { repository ->
            val items = repository.timeline()
            val eventItems = items.filter { it.sourceKind == "provider_event" }
            val observations = items.filter { it.sourceKind == "observation" }

            assertEquals(2, eventItems.size)
            assertEquals(1, observations.size)

            val offerEvent = eventItems.single { it.itemType == ProviderEventType.OFFER_OBSERVED.name }
            assertNull(offerEvent.sourceOccurredAtMillis)
            assertEquals(capturedAt, offerEvent.receivedAtMillis)
            assertTrue(offerEvent.payloadJson.orEmpty().contains("\"fare\":42.5"))
            assertTrue(offerEvent.payloadJson.orEmpty().contains("\"bonus\":3.25"))
            assertTrue(offerEvent.payloadJson.orEmpty().contains("\"passengerRating\":4.96"))

            val skipEvent = eventItems.single { it.itemType == ProviderEventType.OFFER_DECISION_OBSERVED.name }
            assertEquals(capturedAt + 5_000L, skipEvent.sourceOccurredAtMillis)
            assertTrue(skipEvent.payloadJson.orEmpty().contains("skipped_by_driver_in_busybee"))

            val observation = observations.single()
            assertEquals("offer", observation.itemType)
            assertEquals("skipped", observation.status)
            assertEquals("manual_entry", observation.sourceMethod.lowercase())
            assertTrue(observation.evidenceDigest?.matches(Regex("[a-f0-9]{64}")) == true)
        }

        val reopenedSource = DriverCommandStore(context)
        try {
            val stillPresent = reopenedSource.offers().single()
            assertEquals(savedId, stillPresent.id)
            assertEquals("skipped", stillPresent.status)
            assertEquals("Driver-entered offer details", stillPresent.raw)
        } finally {
            reopenedSource.close()
        }

        // The source remains present and the event insert is idempotent across reopen.
        SqliteProviderEventStore(context).use { reopened ->
            assertTrue(reopened.containsIdempotencyKey(observed.idempotencyKey))
            assertTrue(reopened.containsIdempotencyKey(skipped.idempotencyKey))
            assertFalse(reopened.append(observed))
            assertFalse(reopened.append(skipped))
        }
    }

    private fun deleteDatabases() {
        listOf(
            UnifiedActivityLedgerSchema.DATABASE_NAME,
            "busybee_driver_mirror.db",
            "busybee_command_centre.db"
        ).forEach(context::deleteDatabase)
    }
}
