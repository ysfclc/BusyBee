package com.ysfclc.busybee.drivercommandcentre

import android.content.Context

/**
 * Read-only merged timeline over source observations and normalized provider events.
 * Unknown provider time remains null; receive time is only the fallback display sort.
 */
data class UnifiedActivityItem(
    val id: String,
    val sourceKind: String,
    val provider: String?,
    val itemType: String,
    val sourceMethod: String,
    val sourceOccurredAtMillis: Long?,
    val receivedAtMillis: Long,
    val classification: String,
    val lifecycleId: String?,
    val evidenceId: Long?,
    val evidenceReference: String?,
    val evidenceDigest: String?,
    val payloadJson: String?,
    val status: String?,
    val amount: Double?,
    val currency: String?,
    val pickup: String?,
    val destination: String?,
    val distanceKm: Double?,
    val durationMinutes: Double?
) {
    val timelineTimestampMillis: Long
        get() = sourceOccurredAtMillis ?: receivedAtMillis
}

class UnifiedActivityRepository(context: Context) : AutoCloseable {
    private val ledger = UnifiedActivityLedger(context.applicationContext)

    fun timeline(limit: Int = 100): List<UnifiedActivityItem> {
        require(limit in 1..1000) { "limit must be between 1 and 1000" }
        val db = ledger.readableDatabase
        val items = mutableListOf<UnifiedActivityItem>()

        db.rawQuery(
            """SELECT o.id,o.origin_key,o.provider,o.observation_kind,o.source_method,
                      o.source_occurred_at_ms,o.received_at_ms,o.classification,o.lifecycle_id,
                      o.evidence_id,e.content_ref,e.content_digest,o.status,o.amount,o.currency,
                      o.pickup,o.destination,o.distance_km,o.duration_min
               FROM activity_observations o
               LEFT JOIN activity_evidence e ON e.id=o.evidence_id
               ORDER BY COALESCE(o.source_occurred_at_ms,o.received_at_ms) DESC,
                        o.received_at_ms DESC,o.id DESC
               LIMIT ?""",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                items.add(
                    UnifiedActivityItem(
                        id = c.getString(1),
                        sourceKind = "observation",
                        provider = c.getString(2),
                        itemType = c.getString(3),
                        sourceMethod = c.getString(4),
                        sourceOccurredAtMillis = if (c.isNull(5)) null else c.getLong(5),
                        receivedAtMillis = c.getLong(6),
                        classification = c.getString(7),
                        lifecycleId = c.getString(8),
                        evidenceId = if (c.isNull(9)) null else c.getLong(9),
                        evidenceReference = c.getString(10),
                        evidenceDigest = c.getString(11),
                        payloadJson = null,
                        status = c.getString(12),
                        amount = if (c.isNull(13)) null else c.getDouble(13),
                        currency = c.getString(14),
                        pickup = c.getString(15),
                        destination = c.getString(16),
                        distanceKm = if (c.isNull(17)) null else c.getDouble(17),
                        durationMinutes = if (c.isNull(18)) null else c.getDouble(18)
                    )
                )
            }
        }

        db.rawQuery(
            """SELECT event_id,provider,event_type,source_method,source_occurred_at_ms,
                      received_at_ms,lifecycle_id,evidence_ref,evidence_digest,payload_json
               FROM provider_events
               ORDER BY COALESCE(source_occurred_at_ms,received_at_ms) DESC,
                        received_at_ms DESC,event_id DESC
               LIMIT ?""",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                items.add(
                    UnifiedActivityItem(
                        id = c.getString(0),
                        sourceKind = "provider_event",
                        provider = c.getString(1),
                        itemType = c.getString(2),
                        sourceMethod = c.getString(3),
                        sourceOccurredAtMillis = if (c.isNull(4)) null else c.getLong(4),
                        receivedAtMillis = c.getLong(5),
                        classification = "canonical",
                        lifecycleId = c.getString(6),
                        evidenceId = null,
                        evidenceReference = c.getString(7),
                        evidenceDigest = c.getString(8),
                        payloadJson = c.getString(9),
                        status = null,
                        amount = null,
                        currency = null,
                        pickup = null,
                        destination = null,
                        distanceKm = null,
                        durationMinutes = null
                    )
                )
            }
        }

        return items.sortedWith(
            compareByDescending<UnifiedActivityItem> { it.timelineTimestampMillis }
                .thenByDescending { it.receivedAtMillis }
                .thenByDescending { it.id }
        ).take(limit)
    }

    override fun close() = ledger.close()
}
