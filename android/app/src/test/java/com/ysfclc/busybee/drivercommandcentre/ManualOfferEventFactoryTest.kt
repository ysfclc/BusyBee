package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualOfferEventFactoryTest {
    private fun offer(
        id: Long = 7L,
        source: String = "manual",
        platform: String = " Uber ",
        capturedAt: Long = 1_700_000_000_000L
    ) = DriverOffer(
        id = id,
        platform = platform,
        pickup = "Kazimierz",
        destination = "Airport",
        fare = 42.5,
        bonus = 3.0,
        pickupKm = 2.5,
        tripKm = 14.0,
        durationMin = 28.0,
        passengerRating = 4.9,
        capturedAt = capturedAt,
        source = source,
        raw = "Driver-visible source evidence"
    )

    @Test
    fun manualOfferBecomesTypedEventWithoutInventingProviderEventTime() {
        val event = ManualOfferEventFactory.observed(offer())

        assertEquals("uber", event.providerId)
        assertEquals(ProviderEventType.OFFER_OBSERVED, event.eventType)
        assertEquals(ProviderSourceMethod.MANUAL_ENTRY, event.sourceMethod)
        assertEquals(ProviderEvidenceClass.USER_SUPPLIED, event.evidenceClass)
        assertNull(event.occurredAtMillis)
        assertEquals(1_700_000_000_000L, event.receivedAtMillis)
        assertEquals("busybee_command_centre.db:offers:7", event.evidenceReference)

        val payload = event.payload as ProviderEventPayload.Offer
        assertEquals("PLN", payload.currency)
        assertEquals(42.5, payload.fare!!, 0.001)
        assertEquals(2.5, payload.pickupDistanceKm!!, 0.001)
        assertEquals(14.0, payload.tripDistanceKm!!, 0.001)
        assertEquals(28.0, payload.estimatedDurationMinutes!!, 0.001)
        assertEquals(3.0, payload.bonus!!, 0.001)
        assertEquals(4.9, payload.passengerRating!!, 0.001)
        assertNull(payload.decision)
        assertTrue(event.evidenceDigest?.matches(Regex("[a-f0-9]{64}")) == true)
    }

    @Test
    fun notificationReviewedOfferKeepsUserVisibleProvenance() {
        val event = ManualOfferEventFactory.observed(offer(source = "notification-review"))

        assertEquals(ProviderSourceMethod.USER_CAPTURE, event.sourceMethod)
        assertEquals(ProviderEvidenceClass.USER_VISIBLE, event.evidenceClass)
        assertEquals(ProviderEventType.OFFER_OBSERVED, event.eventType)
    }

    @Test
    fun skipActionCreatesExplicitBusyBeeDecisionWithKnownActionTime() {
        val event = ManualOfferEventFactory.decisionObserved(
            offer(),
            decision = "skipped_by_driver_in_busybee",
            decidedAtMillis = 1_700_000_000_999L
        )

        assertEquals(ProviderEventType.OFFER_DECISION_OBSERVED, event.eventType)
        assertEquals(ProviderSourceMethod.MANUAL_ENTRY, event.sourceMethod)
        assertEquals(ProviderEvidenceClass.USER_SUPPLIED, event.evidenceClass)
        assertEquals(1_700_000_000_999L, event.occurredAtMillis)
        assertEquals(1_700_000_000_999L, event.receivedAtMillis)
        assertEquals(
            "busybee-offer:uber:7:decision:skipped_by_driver_in_busybee",
            event.idempotencyKey
        )
        assertEquals(
            "skipped_by_driver_in_busybee",
            (event.payload as ProviderEventPayload.Offer).decision
        )
    }

    @Test
    fun repeatedObservationProducesSameIdempotencyKeyAndDigest() {
        val offer = offer()
        val first = ManualOfferEventFactory.observed(offer)
        val second = ManualOfferEventFactory.observed(offer)

        assertEquals(first.eventId, second.eventId)
        assertEquals(first.idempotencyKey, second.idempotencyKey)
        assertEquals(first.evidenceDigest, second.evidenceDigest)
    }

    @Test
    fun differentDriverDecisionsHaveDifferentIdempotencyKeys() {
        val offer = offer()
        val skipped = ManualOfferEventFactory.decisionObserved(offer, "skipped", 1000L)
        val rejected = ManualOfferEventFactory.decisionObserved(offer, "rejected", 1001L)

        assertFalse(skipped.idempotencyKey == rejected.idempotencyKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsavedOfferCannotProduceCanonicalEvent() {
        ManualOfferEventFactory.observed(offer(id = 0L))
    }
}
