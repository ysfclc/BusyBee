package com.ysfclc.busybee.drivercommandcentre

/**
 * Provider-neutral domain boundary. Android APIs, provider SDKs and acquisition
 * mechanisms must remain outside this file.
 */
enum class ProviderCapability {
    LIVE_OFFERS, OFFER_LIFECYCLE, ACTIVE_TRIP, COMPLETED_TRIPS,
    TRIP_ROUTE, TRIP_DISTANCE, TRIP_DURATION, FARE_BREAKDOWN,
    BONUSES, ADJUSTMENTS, PAYOUTS, DRIVER_SESSION, HISTORICAL_SYNC
}

enum class ProviderConnectionState {
    NOT_CONFIGURED, PERMISSION_REQUIRED, CONNECTED_PARTIAL,
    CONNECTED_VERIFIED, STALE, DEGRADED, BLOCKED, UNSUPPORTED, UNKNOWN, DISCONNECTED
}

enum class ProviderSourceMethod {
    OFFICIAL_API, USER_EXPORT, ANDROID_NOTIFICATION, USER_CAPTURE,
    MANUAL_ENTRY, AUTHORIZED_TEST_INSTRUMENTATION
}

enum class ProviderEventType {
    OFFER_OBSERVED, OFFER_UPDATED, OFFER_EXPIRED, OFFER_DECISION_OBSERVED,
    TRIP_STARTED, TRIP_UPDATED, TRIP_COMPLETED, TRIP_CANCELLED,
    EARNING_OBSERVED, ADJUSTMENT_OBSERVED
}

enum class ProviderEvidenceClass {
    AUTHORITATIVE, USER_SUPPLIED, USER_VISIBLE, INFERRED, SYNTHETIC
}

enum class OfferExpiryEvidence {
    NONE, PROVIDER_EXPLICIT_STATE, PROVIDER_EXPLICIT_DEADLINE, USER_CONFIRMED
}

sealed class ProviderEventPayload {
    data class Offer(
        val providerOfferId: String? = null,
        val pickup: String? = null,
        val dropoff: String? = null,
        val fare: Double? = null,
        val currency: String? = null,
        val pickupDistanceKm: Double? = null,
        val tripDistanceKm: Double? = null,
        val estimatedDurationMinutes: Double? = null,
        val providerExpiryAtMillis: Long? = null,
        val decision: String? = null
    ) : ProviderEventPayload()

    data class Trip(
        val providerTripId: String? = null,
        val pickup: String? = null,
        val dropoff: String? = null,
        val distanceKm: Double? = null,
        val durationMinutes: Double? = null,
        val gross: Double? = null,
        val fees: Double? = null,
        val bonus: Double? = null,
        val currency: String? = null,
        val status: String? = null
    ) : ProviderEventPayload()

    data class Earning(
        val category: String,
        val amount: Double?,
        val currency: String?,
        val providerReference: String? = null
    ) : ProviderEventPayload()

    data class Adjustment(
        val category: String,
        val amount: Double?,
        val currency: String?,
        val providerReference: String? = null
    ) : ProviderEventPayload()
}

/**
 * One normalized event backed by an observation. Null source time means unknown;
 * receivedAtMillis is the local ingestion time and must never be substituted for it.
 */
data class ProviderEventEnvelope(
    val eventId: String,
    val idempotencyKey: String,
    val providerId: String,
    val adapterId: String,
    val adapterVersion: String,
    val eventType: ProviderEventType,
    val lifecycleId: String?,
    val sourceMethod: ProviderSourceMethod,
    val evidenceClass: ProviderEvidenceClass,
    val sourceEventId: String?,
    val occurredAtMillis: Long?,
    val receivedAtMillis: Long,
    val expiresAtMillis: Long? = null,
    val evidenceReference: String? = null,
    val evidenceDigest: String? = null,
    val expiryEvidence: OfferExpiryEvidence = OfferExpiryEvidence.NONE,
    val payload: ProviderEventPayload
)

data class ProviderAdapterHealth(
    val state: ProviderConnectionState,
    val lastObservedAtMillis: Long?,
    val lastSuccessfulSyncAtMillis: Long?,
    val detail: String? = null
)

interface DriverProviderAdapter {
    val providerId: String
    val adapterId: String
    val adapterVersion: String
    fun capabilities(): Set<ProviderCapability>
    fun health(): ProviderAdapterHealth
}

/** Multiple acquisition adapters may exist for one provider; adapter IDs are unique. */
class ProviderAdapterRegistry {
    private val adapters = linkedMapOf<String, DriverProviderAdapter>()

    @Synchronized
    fun register(adapter: DriverProviderAdapter) {
        require(adapter.providerId.isNotBlank()) { "providerId is required" }
        require(adapter.adapterId.isNotBlank()) { "adapterId is required" }
        require(adapter.adapterVersion.isNotBlank()) { "adapterVersion is required" }
        require(!adapters.containsKey(adapter.adapterId)) { "adapterId already registered: ${adapter.adapterId}" }
        adapters[adapter.adapterId] = adapter
    }

    @Synchronized
    fun forProvider(providerId: String): List<DriverProviderAdapter> =
        adapters.values.filter { it.providerId.equals(providerId, ignoreCase = true) }

    @Synchronized
    fun all(): List<DriverProviderAdapter> = adapters.values.toList()
}

interface ProviderEventStore {
    /** Must be backed by a UNIQUE constraint in a persistent implementation. */
    fun containsIdempotencyKey(key: String): Boolean
    /** Append without discarding delayed or out-of-order evidence. */
    fun append(event: ProviderEventEnvelope): Boolean
}

sealed class ProviderIngestResult {
    data class Accepted(val eventId: String) : ProviderIngestResult()
    data class Duplicate(val idempotencyKey: String) : ProviderIngestResult()
    data class Rejected(val reason: String) : ProviderIngestResult()
}

/**
 * Validates the contract and delegates durable idempotency to the store.
 * It deliberately does not infer expiry from disappearance or reorder evidence.
 */
class ProviderEventIngestor(private val store: ProviderEventStore) {
    @Synchronized
    fun ingest(event: ProviderEventEnvelope): ProviderIngestResult {
        val invalid = validate(event)
        if (invalid != null) return ProviderIngestResult.Rejected(invalid)
        if (store.containsIdempotencyKey(event.idempotencyKey)) {
            return ProviderIngestResult.Duplicate(event.idempotencyKey)
        }
        if (!store.append(event)) return ProviderIngestResult.Duplicate(event.idempotencyKey)
        return ProviderIngestResult.Accepted(event.eventId)
    }

    private fun validate(event: ProviderEventEnvelope): String? {
        if (event.eventId.isBlank()) return "eventId is required"
        if (event.idempotencyKey.isBlank()) return "idempotencyKey is required"
        if (event.providerId.isBlank()) return "providerId is required"
        if (event.adapterId.isBlank() || event.adapterVersion.isBlank()) return "adapter identity and version are required"
        if (event.receivedAtMillis <= 0L) return "receivedAtMillis must be positive"
        if (event.occurredAtMillis != null && event.occurredAtMillis < 0L) return "occurredAtMillis cannot be negative"
        if (event.expiresAtMillis != null && event.expiresAtMillis < 0L) return "expiresAtMillis cannot be negative"
        if (event.eventType in OFFER_TYPES && event.payload !is ProviderEventPayload.Offer) return "offer event requires Offer payload"
        if (event.eventType in TRIP_TYPES && event.payload !is ProviderEventPayload.Trip) return "trip event requires Trip payload"
        if (event.eventType == ProviderEventType.EARNING_OBSERVED && event.payload !is ProviderEventPayload.Earning) return "earning event requires Earning payload"
        if (event.eventType == ProviderEventType.ADJUSTMENT_OBSERVED && event.payload !is ProviderEventPayload.Adjustment) return "adjustment event requires Adjustment payload"
        if (event.eventType == ProviderEventType.OFFER_EXPIRED && event.expiryEvidence == OfferExpiryEvidence.NONE) {
            return "offer expiry requires explicit deadline, provider state, or user confirmation evidence"
        }
        if (event.payload is ProviderEventPayload.Earning && event.payload.category.isBlank()) return "earning category is required"
        if (event.payload is ProviderEventPayload.Adjustment && event.payload.category.isBlank()) return "adjustment category is required"
        return null
    }

    private companion object {
        val OFFER_TYPES = setOf(
            ProviderEventType.OFFER_OBSERVED, ProviderEventType.OFFER_UPDATED,
            ProviderEventType.OFFER_EXPIRED, ProviderEventType.OFFER_DECISION_OBSERVED
        )
        val TRIP_TYPES = setOf(
            ProviderEventType.TRIP_STARTED, ProviderEventType.TRIP_UPDATED,
            ProviderEventType.TRIP_COMPLETED, ProviderEventType.TRIP_CANCELLED
        )
    }
}
