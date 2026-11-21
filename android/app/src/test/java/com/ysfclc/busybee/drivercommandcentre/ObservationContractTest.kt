package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test

class ObservationContractTest {
    private fun envelope(
        observedAt: Long = 1_000L,
        receivedAt: Long = 1_000L
    ) = ObservationEnvelope(
        observationId = "observation-1",
        source = ObservationSource(
            sourceId = "synthetic",
            provider = "synthetic-provider",
            kind = ObservationSourceKind.OWNED_APP_INSTRUMENTATION,
            adapterVersion = "test/1"
        ),
        observedAtUnixMs = observedAt,
        receivedAtUnixMs = receivedAt,
        facts = mapOf("fare" to ObservedFact("25.00"))
    )

    @Test fun acceptsValidEnvelope() {
        assertNull(envelope().validationError())
    }

    @Test fun serializesWireFieldsUsingRustSerdeNames() {
        val json = JSONObject(envelope().toWireJson())
        assertTrue(json.has("schema_version"))
        assertTrue(json.has("observation_id"))
        assertTrue(json.has("observed_at_unix_ms"))
        assertTrue(json.getJSONObject("source").has("source_id"))
        assertTrue(json.getJSONObject("source").has("adapter_version"))
        assertTrue(json.getJSONObject("facts").getJSONObject("fare").has("value"))
    }

    @Test fun rejectsReceiptBeforeObservation() {
        assertEquals("received_before_observed", envelope(2_000L, 1_000L).validationError())
    }

    @Test fun rejectsUnknownFieldWithInventedValue() {
        val provenance = FieldProvenance("observation-1", "synthetic", 1_000L, listOf("evidence-1"))
        val event = DriverActivityEvent(
            eventId = "offer:1:observed",
            lifecycleId = "offer:1",
            kind = DriverActivityKind.OFFER_OBSERVED,
            status = DriverEventStatus.CANDIDATE,
            occurredAtUnixMs = 1_000L,
            firstObservedAtUnixMs = 1_000L,
            lastObservedAtUnixMs = 1_000L,
            fields = mapOf(
                "pickup" to CanonicalField("guessed", FieldCertainty.UNKNOWN, listOf(provenance))
            ),
            contributingObservationIds = listOf("observation-1")
        )
        assertEquals("unresolved_field_has_value", event.validationError())
    }

    @Test fun rejectsEventWithoutSourceObservations() {
        val event = DriverActivityEvent(
            eventId = "offer:1:observed",
            lifecycleId = "offer:1",
            kind = DriverActivityKind.OFFER_OBSERVED,
            status = DriverEventStatus.CANDIDATE,
            occurredAtUnixMs = 1_000L,
            firstObservedAtUnixMs = 1_000L,
            lastObservedAtUnixMs = 1_000L,
            fields = emptyMap(),
            contributingObservationIds = emptyList()
        )
        assertEquals("missing_provenance", event.validationError())
    }
}
