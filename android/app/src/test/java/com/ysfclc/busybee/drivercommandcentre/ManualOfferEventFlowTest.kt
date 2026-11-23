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
    fun savedOfferOutboxSurvivesProcessRestartAndDeliversExactlyOnce() {
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

        // Process 1: persist the offer and its event outbox row atomically.
        val firstProcessStore = DriverCommandStore(context)
        val savedOffer = firstProcessStore.saveOfferWithPendingEvent(offer)
        assertTrue(savedOffer.id > 0L)
        assertEquals(1, firstProcessStore.pendingProviderOfferEventCount())
        firstProcessStore.close()

        // Process 2: after a restart, the offer is still available and the decision
        // is written atomically with its own outbox row.
        val restartedStore = DriverCommandStore(context)
        val reopenedOffer = restartedStore.offers().single()
        assertEquals(savedOffer.id, reopenedOffer.id)
        assertEquals("pending", reopenedOffer.status)
        val decidedAt = capturedAt + 5_000L
        val skippedOffer = restartedStore.recordOfferDecision(
            offerId = savedOffer.id,
            status = "skipped",
            decision = "skipped_by_driver_in_busybee",
            decidedAtMillis = decidedAt
        )
        assertEquals("skipped", skippedOffer?.status)
        // Repeating the same in-app decision is idempotent and does not
        // replace the original decision timestamp or create another outbox row.
        restartedStore.recordOfferDecision(
            offerId = savedOffer.id,
            status = "skipped",
            decision = "skipped_by_driver_in_busybee",
            decidedAtMillis = decidedAt + 250L
        )
        assertEquals(2, restartedStore.pendingProviderOfferEventCount())

        val upperBound = restartedStore.latestProviderOfferOutboxId()
        val pending = restartedStore.pendingProviderOfferEvents(upperBound)
        assertEquals(2, pending.size)
        val observed = pending.single { it.event?.eventType == ProviderEventType.OFFER_OBSERVED }.event
            ?: error("Offer-observed outbox event could not be reconstructed")
        val skipped = pending.single { it.event?.eventType == ProviderEventType.OFFER_DECISION_OBSERVED }.event
            ?: error("Offer-decision outbox event could not be reconstructed")
        assertNull(observed.occurredAtMillis)
        assertEquals(capturedAt, observed.receivedAtMillis)
        assertEquals(decidedAt, skipped.occurredAtMillis)
        assertEquals("skipped_by_driver_in_busybee", (skipped.payload as ProviderEventPayload.Offer).decision)

        // Persist both canonical events, then simulate process death before marking
        // the source outbox rows delivered. Idempotency makes this safe to replay.
        SqliteProviderEventStore(context).use { eventStore ->
            val ingestor = ProviderEventIngestor(eventStore)
            pending.forEach { item ->
                val event = item.event ?: error(item.failureReason ?: "Outbox event could not be reconstructed")
                assertTrue(ingestor.ingest(event) is ProviderIngestResult.Accepted)
            }
        }
        restartedStore.close()

        // Process 3: rows reappear in the pending outbox; target inserts resolve as
        // duplicates, after which the source rows can safely be acknowledged.
        val thirdProcessStore = DriverCommandStore(context)
        val afterRestart = thirdProcessStore.pendingProviderOfferEvents(
            thirdProcessStore.latestProviderOfferOutboxId()
        )
        assertEquals(2, afterRestart.size)
        SqliteProviderEventStore(context).use { eventStore ->
            val ingestor = ProviderEventIngestor(eventStore)
            afterRestart.forEach { item ->
                val event = item.event ?: error(item.failureReason ?: "Outbox event could not be reconstructed")
                assertTrue(ingestor.ingest(event) is ProviderIngestResult.Duplicate)
                thirdProcessStore.markProviderOfferEventDelivered(item.outboxId, decidedAt + 1L)
            }
        }
        assertEquals(0, thirdProcessStore.pendingProviderOfferEventCount())

        // The legacy source offer and its raw evidence remain untouched by the
        // delivery and migration paths.
        val stillPresent = thirdProcessStore.offers().single()
        assertEquals(savedOffer.id, stillPresent.id)
        assertEquals("skipped", stillPresent.status)
        assertEquals("Driver-entered offer details", stillPresent.raw)
        thirdProcessStore.close()

        val migration = UnifiedActivityLedgerMigrator(context) { capturedAt + 10_000L }
            .migrateIfNeeded()
        val offerTable = migration.tables.single {
            it.database == "busybee_command_centre.db" && it.table == "offers"
        }
        assertTrue(offerTable.complete)
        assertEquals(1, offerTable.sourceRows)
        assertEquals(1, offerTable.copiedRows)

        UnifiedActivityRepository(context).use { repository ->
            val items = repository.timeline()
            val eventItems = items.filter { it.sourceKind == "provider_event" }
            val observations = items.filter { it.sourceKind == "observation" }
            assertEquals(2, eventItems.size)
            assertEquals(1, observations.size)

            val offerEvent = eventItems.single {
                it.itemType == ProviderEventType.OFFER_OBSERVED.name
            }
            assertNull(offerEvent.sourceOccurredAtMillis)
            assertEquals(capturedAt, offerEvent.receivedAtMillis)
            assertTrue(offerEvent.payloadJson.orEmpty().contains("\"fare\":42.5"))
            assertTrue(offerEvent.payloadJson.orEmpty().contains("\"bonus\":3.25"))
            assertTrue(offerEvent.payloadJson.orEmpty().contains("\"passengerRating\":4.96"))

            val skipEvent = eventItems.single {
                it.itemType == ProviderEventType.OFFER_DECISION_OBSERVED.name
            }
            assertEquals(decidedAt, skipEvent.sourceOccurredAtMillis)
            assertTrue(skipEvent.payloadJson.orEmpty().contains("skipped_by_driver_in_busybee"))

            val observation = observations.single()
            assertEquals("offer", observation.itemType)
            assertEquals("skipped", observation.status)
            assertEquals("manual_entry", observation.sourceMethod.lowercase())
            assertTrue(observation.evidenceDigest?.matches(Regex("[a-f0-9]{64}")) == true)
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
