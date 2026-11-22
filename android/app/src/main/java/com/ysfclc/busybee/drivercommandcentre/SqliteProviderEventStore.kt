package com.ysfclc.busybee.drivercommandcentre

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/**
 * Persistent event store backed by the ledger's UNIQUE idempotency constraint.
 * The insert result, rather than a prior read alone, is authoritative under races.
 */
class SqliteProviderEventStore(context: Context) : ProviderEventStore, AutoCloseable {
    private val ledger = UnifiedActivityLedger(context.applicationContext)

    override fun containsIdempotencyKey(key: String): Boolean =
        ledger.readableDatabase.rawQuery(
            "SELECT 1 FROM provider_events WHERE idempotency_key=? LIMIT 1",
            arrayOf(key)
        ).use { it.moveToFirst() }

    override fun append(event: ProviderEventEnvelope): Boolean {
        val values = ContentValues().apply {
            put("event_id", event.eventId)
            put("idempotency_key", event.idempotencyKey)
            put("provider", event.providerId)
            put("adapter_id", event.adapterId)
            put("adapter_version", event.adapterVersion)
            put("event_type", event.eventType.name)
            put("lifecycle_id", event.lifecycleId)
            put("source_method", event.sourceMethod.name)
            put("evidence_class", event.evidenceClass.name)
            put("source_event_id", event.sourceEventId)
            if (event.occurredAtMillis == null) putNull("source_occurred_at_ms")
            else put("source_occurred_at_ms", event.occurredAtMillis)
            put("received_at_ms", event.receivedAtMillis)
            if (event.expiresAtMillis == null) putNull("expires_at_ms")
            else put("expires_at_ms", event.expiresAtMillis)
            put("evidence_ref", event.evidenceReference)
            put("evidence_digest", event.evidenceDigest)
            put("expiry_evidence", event.expiryEvidence.name)
            put("payload_json", payloadJson(event.payload))
        }
        return ledger.writableDatabase.insertWithOnConflict(
            "provider_events", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    private fun payloadJson(payload: ProviderEventPayload): String = JSONObject().apply {
        when (payload) {
            is ProviderEventPayload.Offer -> {
                put("type", "offer")
                put("providerOfferId", payload.providerOfferId ?: JSONObject.NULL)
                put("pickup", payload.pickup ?: JSONObject.NULL)
                put("dropoff", payload.dropoff ?: JSONObject.NULL)
                put("fare", payload.fare ?: JSONObject.NULL)
                put("currency", payload.currency ?: JSONObject.NULL)
                put("pickupDistanceKm", payload.pickupDistanceKm ?: JSONObject.NULL)
                put("tripDistanceKm", payload.tripDistanceKm ?: JSONObject.NULL)
                put("estimatedDurationMinutes", payload.estimatedDurationMinutes ?: JSONObject.NULL)
                put("providerExpiryAtMillis", payload.providerExpiryAtMillis ?: JSONObject.NULL)
                put("decision", payload.decision ?: JSONObject.NULL)
            }
            is ProviderEventPayload.Trip -> {
                put("type", "trip")
                put("providerTripId", payload.providerTripId ?: JSONObject.NULL)
                put("pickup", payload.pickup ?: JSONObject.NULL)
                put("dropoff", payload.dropoff ?: JSONObject.NULL)
                put("distanceKm", payload.distanceKm ?: JSONObject.NULL)
                put("durationMinutes", payload.durationMinutes ?: JSONObject.NULL)
                put("gross", payload.gross ?: JSONObject.NULL)
                put("fees", payload.fees ?: JSONObject.NULL)
                put("bonus", payload.bonus ?: JSONObject.NULL)
                put("currency", payload.currency ?: JSONObject.NULL)
                put("status", payload.status ?: JSONObject.NULL)
            }
            is ProviderEventPayload.Earning -> {
                put("type", "earning")
                put("category", payload.category)
                put("amount", payload.amount ?: JSONObject.NULL)
                put("currency", payload.currency ?: JSONObject.NULL)
                put("providerReference", payload.providerReference ?: JSONObject.NULL)
            }
            is ProviderEventPayload.Adjustment -> {
                put("type", "adjustment")
                put("category", payload.category)
                put("amount", payload.amount ?: JSONObject.NULL)
                put("currency", payload.currency ?: JSONObject.NULL)
                put("providerReference", payload.providerReference ?: JSONObject.NULL)
            }
        }
    }.toString()

    override fun close() = ledger.close()
}
