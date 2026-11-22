package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.*
import org.junit.Test

class ProviderBridgeTest {
    private class FakeStore : ProviderEventStore {
        val events = mutableListOf<ProviderEventEnvelope>()
        override fun containsIdempotencyKey(key: String) = events.any { it.idempotencyKey == key }
        override fun append(event: ProviderEventEnvelope) { events.add(event) }
    }

    private fun offerEvent(
        key: String = "uber:offer:1:observed",
        type: ProviderEventType = ProviderEventType.OFFER_OBSERVED,
        received: Long = 200L,
        occurred: Long? = 100L,
        expiryEvidence: OfferExpiryEvidence = OfferExpiryEvidence.NONE
    ) = ProviderEventEnvelope(
        eventId = key,
        idempotencyKey = key,
        providerId = "uber",
        adapterId = "uber-notification-v1",
        adapterVersion = "1.0",
        eventType = type,
        lifecycleId = "offer-1",
        sourceMethod = ProviderSourceMethod.ANDROID_NOTIFICATION,
        evidenceClass = ProviderEvidenceClass.USER_VISIBLE,
        sourceEventId = null,
        occurredAtMillis = occurred,
        receivedAtMillis = received,
        expiryEvidence = expiryEvidence,
        payload = ProviderEventPayload.Offer(fare = null, currency = null)
    )

    @Test fun acceptsPartialOfferWithoutInventingMissingFields() {
        val store = FakeStore()
        val result = ProviderEventIngestor(store).ingest(offerEvent())
        assertTrue(result is ProviderIngestResult.Accepted)
        val payload = store.events.single().payload as ProviderEventPayload.Offer
        assertNull(payload.fare)
        assertNull(payload.currency)
    }

    @Test fun duplicateIdempotencyKeyIsNotAppendedTwice() {
        val store = FakeStore()
        val ingestor = ProviderEventIngestor(store)
        assertTrue(ingestor.ingest(offerEvent()).isAccepted())
        assertTrue(ingestor.ingest(offerEvent()).isDuplicate())
        assertEquals(1, store.events.size)
    }

    @Test fun delayedAndOutOfOrderEventsArePreservedRatherThanDiscarded() {
        val store = FakeStore()
        val ingestor = ProviderEventIngestor(store)
        val later = offerEvent(key = "offer:update", received = 300L, occurred = 250L, type = ProviderEventType.OFFER_UPDATED)
        val earlier = offerEvent(key = "offer:initial", received = 400L, occurred = 150L)
        assertTrue(ingestor.ingest(later).isAccepted())
        assertTrue(ingestor.ingest(earlier).isAccepted())
        assertEquals(listOf(250L, 150L), store.events.map { it.occurredAtMillis })
    }

    @Test fun unknownSourceTimeStaysUnknown() {
        val event = offerEvent(occurred = null)
        assertNull(event.occurredAtMillis)
        assertEquals(200L, event.receivedAtMillis)
    }

    @Test fun offerCannotBeMarkedExpiredFromDisappearanceAlone() {
        val store = FakeStore()
        val expired = offerEvent(type = ProviderEventType.OFFER_EXPIRED)
        val result = ProviderEventIngestor(store).ingest(expired)
        assertTrue(result is ProviderIngestResult.Rejected)
        assertTrue(store.events.isEmpty())
    }

    @Test fun explicitExpiryEvidenceAllowsExpiryEvent() {
        val store = FakeStore()
        val expired = offerEvent(
            type = ProviderEventType.OFFER_EXPIRED,
            expiryEvidence = OfferExpiryEvidence.PROVIDER_EXPLICIT_DEADLINE
        )
        assertTrue(ProviderEventIngestor(store).ingest(expired).isAccepted())
    }

    @Test fun rejectsMismatchedPayloadType() {
        val store = FakeStore()
        val event = offerEvent().copy(
            eventType = ProviderEventType.TRIP_STARTED,
            payload = ProviderEventPayload.Offer()
        )
        assertTrue(ProviderEventIngestor(store).ingest(event) is ProviderIngestResult.Rejected)
    }

    @Test fun registryAllowsMultipleAdaptersPerProviderButUniqueAdapterIds() {
        val registry = ProviderAdapterRegistry()
        val notification = FakeAdapter("uber", "uber-notice")
        val export = FakeAdapter("uber", "uber-export")
        registry.register(notification)
        registry.register(export)
        assertEquals(2, registry.forProvider("UBER").size)
        try {
            registry.register(notification)
            fail("duplicate adapter id should be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    private data class FakeAdapter(
        override val providerId: String,
        override val adapterId: String
    ) : DriverProviderAdapter {
        override val adapterVersion = "1"
        override fun capabilities() = setOf(ProviderCapability.LIVE_OFFERS)
        override fun health() = ProviderAdapterHealth(ProviderConnectionState.CONNECTED_PARTIAL, null, null)
    }

    private fun ProviderIngestResult.isAccepted() = this is ProviderIngestResult.Accepted
    private fun ProviderIngestResult.isDuplicate() = this is ProviderIngestResult.Duplicate
}
