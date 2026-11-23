package com.ysfclc.busybee.drivercommandcentre

import java.security.MessageDigest
import java.util.Locale

/**
 * Maps explicit driver-entered or driver-reviewed offer actions into the
 * provider-neutral event ledger. It does not claim a provider API connection.
 */
object ManualOfferEventFactory {
    private const val ADAPTER_ID = "busybee.user-offer"
    private const val ADAPTER_VERSION = "1"

    fun observed(offer: DriverOffer): ProviderEventEnvelope {
        require(offer.id > 0L) { "Offer must be saved before it can be emitted" }
        require(offer.capturedAt > 0L) { "Offer capture time must be positive" }

        val providerId = normalizeProviderId(offer.platform)
        val idempotencyKey = "busybee-offer:$providerId:${offer.id}:observed"
        val reviewedFromNotification = offer.source.equals("notification-review", ignoreCase = true)
        val sourceMethod = if (reviewedFromNotification) {
            ProviderSourceMethod.USER_CAPTURE
        } else {
            ProviderSourceMethod.MANUAL_ENTRY
        }
        val evidenceClass = if (reviewedFromNotification) {
            ProviderEvidenceClass.USER_VISIBLE
        } else {
            ProviderEvidenceClass.USER_SUPPLIED
        }

        return ProviderEventEnvelope(
            eventId = idempotencyKey,
            idempotencyKey = idempotencyKey,
            providerId = providerId,
            adapterId = ADAPTER_ID,
            adapterVersion = ADAPTER_VERSION,
            eventType = ProviderEventType.OFFER_OBSERVED,
            lifecycleId = "offer:$providerId:${offer.id}",
            sourceMethod = sourceMethod,
            evidenceClass = evidenceClass,
            sourceEventId = "command-offer:${offer.id}",
            // capturedAt is our local receipt time, not a provider event timestamp.
            occurredAtMillis = null,
            receivedAtMillis = offer.capturedAt,
            evidenceReference = "busybee_command_centre.db:offers:${offer.id}",
            evidenceDigest = offerDigest(offer, providerId),
            payload = offerPayload(offer)
        )
    }

    fun decisionObserved(
        offer: DriverOffer,
        decision: String,
        decidedAtMillis: Long
    ): ProviderEventEnvelope {
        require(offer.id > 0L) { "Offer must be saved before a decision can be emitted" }
        require(decision.isNotBlank()) { "Decision is required" }
        require(decidedAtMillis > 0L) { "Decision time must be positive" }

        val providerId = normalizeProviderId(offer.platform)
        val normalizedDecision = decision.trim().lowercase(Locale.ROOT)
        val idempotencyKey = "busybee-offer:$providerId:${offer.id}:decision:$normalizedDecision"

        return ProviderEventEnvelope(
            eventId = idempotencyKey,
            idempotencyKey = idempotencyKey,
            providerId = providerId,
            adapterId = "busybee.driver-action",
            adapterVersion = ADAPTER_VERSION,
            eventType = ProviderEventType.OFFER_DECISION_OBSERVED,
            lifecycleId = "offer:$providerId:${offer.id}",
            sourceMethod = ProviderSourceMethod.MANUAL_ENTRY,
            evidenceClass = ProviderEvidenceClass.USER_SUPPLIED,
            sourceEventId = idempotencyKey,
            // The driver action happens inside BusyBee, so its time is known.
            occurredAtMillis = decidedAtMillis,
            receivedAtMillis = decidedAtMillis,
            evidenceDigest = sha256("$idempotencyKey|$decidedAtMillis"),
            payload = offerPayload(offer, normalizedDecision)
        )
    }

    private fun offerPayload(offer: DriverOffer, decision: String? = null) =
        ProviderEventPayload.Offer(
            pickup = offer.pickup.takeIf { it.isNotBlank() },
            dropoff = offer.destination.takeIf { it.isNotBlank() },
            fare = offer.fare,
            currency = "PLN",
            pickupDistanceKm = offer.pickupKm,
            tripDistanceKm = offer.tripKm,
            estimatedDurationMinutes = offer.durationMin,
            decision = decision
        )

    private fun normalizeProviderId(value: String): String =
        value.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "unknown" }

    private fun offerDigest(offer: DriverOffer, providerId: String): String = sha256(
        listOf(
            offer.id.toString(),
            providerId,
            offer.pickup,
            offer.destination,
            offer.fare.toString(),
            offer.bonus.toString(),
            offer.pickupKm.toString(),
            offer.tripKm.toString(),
            offer.durationMin.toString(),
            offer.passengerRating?.toString().orEmpty(),
            offer.capturedAt.toString(),
            offer.source,
            offer.raw
        ).joinToString("\u001f")
    )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
