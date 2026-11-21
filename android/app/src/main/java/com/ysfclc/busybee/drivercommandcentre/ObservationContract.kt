package com.ysfclc.busybee.drivercommandcentre

import org.json.JSONArray
import org.json.JSONObject

/**
 * Android-side DTOs for the versioned BusyBee observation contract.
 *
 * Capture adapters create ObservationEnvelope values. They do not classify
 * provider text directly into trusted driver events. Classification and event
 * reconciliation belong to the provider-neutral domain pipeline.
 */
internal object ObservationContract {
    const val SCHEMA_VERSION = 1
    const val EVENT_SCHEMA_VERSION = 1
}

internal enum class ObservationSourceKind {
    NOTIFICATION,
    PROVIDER_API,
    ANDROID_SYSTEM,
    OWNED_APP_INSTRUMENTATION,
    MANUAL,
    IMPORT
}

internal data class ObservationSource(
    val sourceId: String,
    val provider: String,
    val kind: ObservationSourceKind,
    val adapterVersion: String
)

internal data class EvidenceReference(
    val evidenceId: String,
    val evidenceType: String,
    val localRef: String? = null
)

internal data class ObservedFact(
    /** Null means unknown; adapters must not infer or invent a value. */
    val value: String?,
    val evidence: List<EvidenceReference> = emptyList()
)

internal data class ObservationEnvelope(
    val schemaVersion: Int = ObservationContract.SCHEMA_VERSION,
    val observationId: String,
    val source: ObservationSource,
    val observedAtUnixMs: Long,
    val receivedAtUnixMs: Long,
    val subjectId: String? = null,
    val facts: Map<String, ObservedFact> = emptyMap(),
    val evidence: List<EvidenceReference> = emptyList()
) {
    fun validationError(): String? = when {
        schemaVersion != ObservationContract.SCHEMA_VERSION -> "unsupported_schema_version"
        observationId.isBlank() || source.sourceId.isBlank() ||
            source.provider.isBlank() || source.adapterVersion.isBlank() -> "missing_identity"
        observedAtUnixMs <= 0L || receivedAtUnixMs <= 0L -> "invalid_timestamp"
        receivedAtUnixMs < observedAtUnixMs -> "received_before_observed"
        facts.keys.any { it.isBlank() } -> "empty_fact_key"
        else -> null
    }
}

internal enum class DriverActivityKind {
    OFFER_OBSERVED,
    OFFER_UPDATED,
    OFFER_EXPIRED,
    OFFER_DECISION_OBSERVED,
    TRIP_STARTED,
    TRIP_UPDATED,
    TRIP_COMPLETED,
    TRIP_CANCELLED,
    EARNING_OBSERVED,
    ADJUSTMENT_OBSERVED
}

internal enum class DriverEventStatus { CANDIDATE, CONFIRMED, REJECTED, CONFLICT }
internal enum class FieldCertainty { DIRECTLY_OBSERVED, DERIVED, UNKNOWN, CONFLICTING }

internal data class FieldProvenance(
    val observationId: String,
    val sourceId: String,
    val observedAtUnixMs: Long,
    val evidenceIds: List<String>
)

internal data class CanonicalField(
    val value: String?,
    val certainty: FieldCertainty,
    val provenance: List<FieldProvenance>
)

internal data class DriverActivityEvent(
    val schemaVersion: Int = ObservationContract.EVENT_SCHEMA_VERSION,
    val eventId: String,
    val lifecycleId: String,
    val kind: DriverActivityKind,
    val status: DriverEventStatus,
    val occurredAtUnixMs: Long?,
    val firstObservedAtUnixMs: Long,
    val lastObservedAtUnixMs: Long,
    val fields: Map<String, CanonicalField>,
    val contributingObservationIds: List<String>
) {
    fun validationError(): String? {
        if (schemaVersion != ObservationContract.EVENT_SCHEMA_VERSION) return "unsupported_schema_version"
        if (eventId.isBlank() || lifecycleId.isBlank()) return "missing_identity"
        if (firstObservedAtUnixMs <= 0L || lastObservedAtUnixMs < firstObservedAtUnixMs ||
            (occurredAtUnixMs != null && occurredAtUnixMs <= 0L)) return "invalid_timestamp"
        if (contributingObservationIds.isEmpty()) return "missing_provenance"
        fields.values.forEach { field ->
            if ((field.certainty == FieldCertainty.UNKNOWN || field.certainty == FieldCertainty.CONFLICTING) &&
                field.value != null) return "unresolved_field_has_value"
            if ((field.certainty == FieldCertainty.DIRECTLY_OBSERVED || field.certainty == FieldCertainty.DERIVED) &&
                field.value == null) return "known_field_missing_value"
            if (field.provenance.isEmpty()) return "missing_provenance"
        }
        return null
    }
}

/** JSON wire format consumed by Rust serde; field names intentionally match snake_case Rust fields. */
internal fun ObservationEnvelope.toWireJson(): String {
    val sourceJson = JSONObject()
        .put("source_id", source.sourceId)
        .put("provider", source.provider)
        .put("kind", source.kind.name.lowercase())
        .put("adapter_version", source.adapterVersion)
    val factsJson = JSONObject()
    facts.forEach { (key, fact) ->
        val factEvidence = JSONArray()
        fact.evidence.forEach { ref ->
            factEvidence.put(JSONObject()
                .put("evidence_id", ref.evidenceId)
                .put("evidence_type", ref.evidenceType)
                .put("local_ref", ref.localRef ?: JSONObject.NULL))
        }
        factsJson.put(key, JSONObject()
            .put("value", fact.value ?: JSONObject.NULL)
            .put("evidence", factEvidence))
    }
    val evidenceJson = JSONArray()
    evidence.forEach { ref ->
        evidenceJson.put(JSONObject()
            .put("evidence_id", ref.evidenceId)
            .put("evidence_type", ref.evidenceType)
            .put("local_ref", ref.localRef ?: JSONObject.NULL))
    }
    return JSONObject()
        .put("schema_version", schemaVersion)
        .put("observation_id", observationId)
        .put("source", sourceJson)
        .put("observed_at_unix_ms", observedAtUnixMs)
        .put("received_at_unix_ms", receivedAtUnixMs)
        .put("subject_id", subjectId ?: JSONObject.NULL)
        .put("facts", factsJson)
        .put("evidence", evidenceJson)
        .toString()
}
