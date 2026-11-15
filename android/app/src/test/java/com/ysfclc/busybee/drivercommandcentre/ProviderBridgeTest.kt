package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.*
import org.junit.Test

class ProviderBridgeTest {
    private val adapter = AdapterIdentity("fixture", "1")

    private fun event(
        key: String,
        occurred: Long? = 100L,
        received: Long = 110L,
        expires: Long? = null,
        present: Set<String> = setOf("fare")
    ) = ProviderEvent(
        eventId = key,
        provider = ProviderId.UBER,
        adapter = adapter,
        type = ProviderEventType.OFFER,
        sourceMethod = SourceMethod.OWNED_TEST_FIXTURE,
        occurredAtEpochMs = occurred,
        receivedAtEpochMs = received,
        expiresAtEpochMs = expires,
        payload = ProviderEventPayload.Offer(fare = Money(12.5, "PLN")),
        completeness = FieldCompleteness(setOf("fare", "pickup"), present, 0.8),
        evidence = EvidenceReference("fp-$key", retention = EvidenceRetention.EPHEMERAL),
        dedupeKey = key
    )

    @Test fun ingestionIsIdempotentAcrossSources() {
        val repo = InMemoryProviderEventRepository()
        val ingestor = ProviderEventIngestor(repo)
        val first = ingestor.ingest(listOf(event("same")))
        val second = ingestor.ingest(listOf(event("same")))
        assertEquals(1, first.accepted)
        assertEquals(1, second.duplicates)
        assertEquals(1, repo.events().size)
    }

    @Test fun timelineOrdersByEventTimeAndThenReceiveTime() {
        val repo = InMemoryProviderEventRepository()
        repo.insertIfNew(event("older", occurred = 50, received = 200))
        repo.insertIfNew(event("newer", occurred = 150, received = 160))
        assertEquals(listOf("newer", "older"), repo.events().map { it.eventId })
    }

    @Test fun missingFieldsRemainExplicitlyMissing() {
        val completeness = event("partial").completeness
        assertFalse(completeness.isComplete)
        assertEquals(setOf("pickup"), completeness.missingFields)
    }

    @Test fun staleAndExpiredEventsAreDetected() {
        assertTrue(event("old", occurred = 10, received = 10).isStale(1000, 100))
        assertTrue(event("expired", occurred = 950, received = 990, expires = 980).isStale(1000, 100))
        assertFalse(event("fresh", occurred = 950, received = 950).isStale(1000, 100))
    }

    @Test fun registryAllowsMultipleSourcesForOneProviderButRejectsDuplicateIds() {
        val registry = ProviderAdapterRegistry()
        val first = SyntheticProviderAdapter(ProviderId.UBER)
        val second = object : DriverProviderAdapter by first {
            override val identity = AdapterIdentity("notification.uber", "1")
        }
        registry.register(first)
        registry.register(second)
        assertEquals(2, registry.forProvider(ProviderId.UBER).size)
        try {
            registry.register(first)
            fail("Expected duplicate adapter id to fail")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun syntheticAdapterNeverClaimsVerifiedConnection() {
        val adapter = SyntheticProviderAdapter()
        assertEquals(ProviderConnectionState.PARTIAL, adapter.connect().state)
        assertTrue(adapter.connect().detail!!.contains("no provider account"))
    }
}
