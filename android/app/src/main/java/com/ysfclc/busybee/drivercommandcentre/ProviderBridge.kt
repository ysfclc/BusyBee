package com.ysfclc.busybee.drivercommandcentre

/**
 * BusyBee's internal provider contract. Acquisition mechanisms live behind adapters;
 * UI and analytics consume only these provider-neutral records.
 */
enum class ProviderId { UBER, BOLT, OTHER }

data class AdapterIdentity(val id: String, val version: String) {
    init { require(id.isNotBlank()); require(version.isNotBlank()) }
}

enum class ProviderCapability {
    LIVE_OFFERS, OFFER_LIFECYCLE, ACTIVE_TRIPS, COMPLETED_TRIPS, TRIP_ROUTE,
    TRIP_DISTANCE, TRIP_DURATION, FARE_BREAKDOWN, PAYOUTS, DRIVER_SESSION, HISTORY
}

enum class CapabilityState {
    AVAILABLE, PARTIAL, PERMISSION_REQUIRED, APPROVAL_REQUIRED, UNSUPPORTED, BLOCKED, UNKNOWN
}

data class CapabilityReport(
    val capability: ProviderCapability,
    val state: CapabilityState,
    val detail: String? = null
)

enum class ProviderConnectionState {
    NOT_CONFIGURED, PERMISSION_REQUIRED, PARTIAL, VERIFIED, STALE_OR_DEGRADED,
    BLOCKED_OR_UNSUPPORTED, DISCONNECTED
}

enum class SourceMethod {
    OFFICIAL_API, USER_EXPORT, ANDROID_NOTIFICATION, USER_SELECTED_IMPORT,
    OWNED_TEST_FIXTURE, MANUAL, OTHER
}

enum class ProviderEventType {
    OFFER, OFFER_ACCEPTED, OFFER_REJECTED, OFFER_EXPIRED, OFFER_CANCELLED,
    TRIP_STARTED, TRIP_COMPLETED, TRIP_CANCELLED, EARNINGS_ADJUSTMENT, PAYOUT
}

sealed class ProviderEventPayload {
    data class Offer(
        val pickup: String? = null,
        val destination: String? = null,
        val fare: Money? = null,
        val bonus: Money? = null,
        val pickupDistanceKm: Double? = null,
        val tripDistanceKm: Double? = null,
        val durationMinutes: Double? = null
    ) : ProviderEventPayload()

    data class Trip(
        val pickup: String? = null,
        val destination: String? = null,
        val distanceKm: Double? = null,
        val durationMinutes: Double? = null,
        val gross: Money? = null,
        val fees: Money? = null,
        val bonus: Money? = null
    ) : ProviderEventPayload()

    data class Earnings(
        val amount: Money,
        val category: String? = null,
        val description: String? = null
    ) : ProviderEventPayload()
}

data class Money(val amount: Double, val currency: String) {
    init {
        require(amount.isFinite())
        require(currency.matches(Regex("[A-Z]{3}"))) { "Currency must be an ISO-style 3-letter code" }
    }
}

data class FieldCompleteness(
    val expectedFields: Set<String>,
    val presentFields: Set<String>,
    val confidence: Double? = null
) {
    init {
        require(presentFields.all { it in expectedFields }) { "Present fields must be expected fields" }
        require(confidence == null || confidence in 0.0..1.0)
    }

    val isComplete: Boolean get() = expectedFields.isNotEmpty() && presentFields.containsAll(expectedFields)
    val missingFields: Set<String> get() = expectedFields - presentFields
}

enum class EvidenceRetention { KEEP_SOURCE_REFERENCE, LIMITED, EPHEMERAL }
data class EvidenceReference(
    val fingerprint: String,
    val reference: String? = null,
    val retention: EvidenceRetention = EvidenceRetention.LIMITED,
    val redacted: Boolean = true
) {
    init { require(fingerprint.isNotBlank()) }
}

data class ProviderEvent(
    val eventId: String,
    val provider: ProviderId,
    val adapter: AdapterIdentity,
    val type: ProviderEventType,
    val sourceMethod: SourceMethod,
    val occurredAtEpochMs: Long?,
    val receivedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val correlationId: String? = null,
    val externalEventId: String? = null,
    val externalEntityId: String? = null,
    val payload: ProviderEventPayload,
    val completeness: FieldCompleteness,
    val evidence: EvidenceReference,
    val dedupeKey: String
) {
    init {
        require(eventId.isNotBlank())
        require(dedupeKey.isNotBlank())
        require(expiresAtEpochMs == null || expiresAtEpochMs >= receivedAtEpochMs)
    }

    fun isStale(nowEpochMs: Long, maxAgeMs: Long): Boolean {
        val eventTime = occurredAtEpochMs ?: receivedAtEpochMs
        return (expiresAtEpochMs != null && expiresAtEpochMs < nowEpochMs) ||
            nowEpochMs - eventTime > maxAgeMs
    }
}

data class ProviderSyncResult(
    val received: Int,
    val accepted: Int,
    val duplicates: Int,
    val rejected: Int
)

data class ProviderConnectResult(val state: ProviderConnectionState, val detail: String? = null)

/** Adapter implementations must disclose their real acquisition method and capability coverage. */
interface DriverProviderAdapter {
    val provider: ProviderId
    val identity: AdapterIdentity
    fun capabilities(): List<CapabilityReport>
    fun connectionState(): ProviderConnectionState
    fun connect(): ProviderConnectResult
    fun readEvents(sinceEpochMs: Long? = null): List<ProviderEvent>
    fun disconnect()
}

interface ProviderEventRepository {
    /** Returns false when this dedupe key has already been ingested. */
    fun insertIfNew(event: ProviderEvent): Boolean
    fun events(limit: Int = 100): List<ProviderEvent>
}

class InMemoryProviderEventRepository : ProviderEventRepository {
    private val byDedupeKey = linkedMapOf<String, ProviderEvent>()

    @Synchronized
    override fun insertIfNew(event: ProviderEvent): Boolean {
        val storageKey = "${event.provider.name}:${event.dedupeKey}"
        if (byDedupeKey.containsKey(storageKey)) return false
        byDedupeKey[storageKey] = event
        return true
    }

    @Synchronized
    override fun events(limit: Int): List<ProviderEvent> =
        byDedupeKey.values.sortedWith(
            compareByDescending<ProviderEvent> { it.occurredAtEpochMs ?: it.receivedAtEpochMs }
                .thenByDescending { it.receivedAtEpochMs }
        ).take(limit.coerceAtLeast(0))
}

class ProviderAdapterRegistry {
    private val adapters = linkedMapOf<String, DriverProviderAdapter>()

    @Synchronized
    fun register(adapter: DriverProviderAdapter) {
        require(!adapters.containsKey(adapter.identity.id)) {
            "An adapter is already registered with id ${adapter.identity.id}"
        }
        adapters[adapter.identity.id] = adapter
    }

    @Synchronized
    fun get(adapterId: String): DriverProviderAdapter? = adapters[adapterId]

    @Synchronized
    fun forProvider(provider: ProviderId): List<DriverProviderAdapter> = adapters.values.filter { it.provider == provider }

    @Synchronized
    fun all(): List<DriverProviderAdapter> = adapters.values.toList()
}

class ProviderEventIngestor(private val repository: ProviderEventRepository) {
    fun ingest(events: List<ProviderEvent>): ProviderSyncResult {
        var accepted = 0
        var duplicates = 0
        var rejected = 0
        events.forEach { event ->
            try {
                if (repository.insertIfNew(event)) accepted++ else duplicates++
            } catch (_: IllegalArgumentException) {
                rejected++
            }
        }
        return ProviderSyncResult(events.size, accepted, duplicates, rejected)
    }
}

/** Deterministic fixture adapter. It is never evidence of a real Uber/Bolt connection. */
class SyntheticProviderAdapter(
    override val provider: ProviderId = ProviderId.OTHER,
    private val fixtureEvents: List<ProviderEvent> = emptyList()
) : DriverProviderAdapter {
    override val identity = AdapterIdentity("busybee.synthetic", "1")
    override fun capabilities() = listOf(
        CapabilityReport(ProviderCapability.LIVE_OFFERS, CapabilityState.AVAILABLE, "Synthetic fixture only")
    )
    override fun connectionState() = ProviderConnectionState.PARTIAL
    override fun connect() = ProviderConnectResult(ProviderConnectionState.PARTIAL, "Synthetic fixture; no provider account connected")
    override fun readEvents(sinceEpochMs: Long?) =
        fixtureEvents.filter { sinceEpochMs == null || (it.occurredAtEpochMs ?: it.receivedAtEpochMs) >= sinceEpochMs }
    override fun disconnect() = Unit
}
