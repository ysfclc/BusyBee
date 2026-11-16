package com.ysfclc.busybee.drivercommandcentre

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/**
 * Projects the two pre-bridge stores into the canonical event ledger.
 * Source stores are read-only here; only the separate ProviderEventRepository is written.
 */
internal object LegacyProviderEventPolicy {
    fun sourceMethodForRecord(sourceName: String): SourceMethod = when {
        sourceName.contains("live UI listener", ignoreCase = true) -> SourceMethod.ANDROID_ACCESSIBILITY_UI
        sourceName.contains("screen capture", ignoreCase = true) ||
            sourceName.contains("MediaProjection", ignoreCase = true) -> SourceMethod.ANDROID_MEDIA_PROJECTION_OCR
        sourceName.contains("manual", ignoreCase = true) -> SourceMethod.MANUAL
        else -> SourceMethod.USER_SELECTED_IMPORT
    }

    fun offerDedupeKey(id: Long, status: String): String =
        "offer:$id:${status.trim().lowercase(Locale.ROOT)}"

    fun notificationDedupeKey(id: Long): String = "notification:$id"
}
class LegacyProviderActivityProjector(
    private val commands: DriverCommandStore,
    private val mirror: DriverMirrorStore,
    private val repository: ProviderEventRepository = SqliteProviderEventRepository(mirror)
) {
    fun project(limitPerSource: Int = 200): List<ProviderEvent> {
        val ingestor = ProviderEventIngestor(repository)
        val now = System.currentTimeMillis()
        val events = buildList {
            commands.offers(limitPerSource).forEach { add(it.toProviderEvent(now)) }
            commands.notices(limitPerSource).forEach { add(it.toProviderEvent()) }
            mirror.recent(limitPerSource).forEach { add(it.toProviderEvent(now)) }
        }
        ingestor.ingest(events)
        return repository.events(limitPerSource * 3)
    }

    private fun DriverOffer.toProviderEvent(now: Long): ProviderEvent {
        val provider = providerId(platform)
        val sourceMethod = when (source.lowercase()) {
            "manual" -> SourceMethod.MANUAL
            "notification", "notification-review" -> SourceMethod.ANDROID_NOTIFICATION
            else -> SourceMethod.OTHER
        }
        val type = when (status.lowercase()) {
            "accepted" -> ProviderEventType.OFFER_ACCEPTED
            "rejected" -> ProviderEventType.OFFER_REJECTED
            "expired" -> ProviderEventType.OFFER_EXPIRED
            "cancelled", "canceled" -> ProviderEventType.OFFER_CANCELLED
            else -> ProviderEventType.OFFER
        }
        val fareMoney = fare.takeIf { it.isFinite() }?.let { Money(it, "PLN") }
        val bonusMoney = bonus.takeIf { it.isFinite() }?.let { Money(it, "PLN") }
        val pickupDistance = pickupKm.takeIf { it.isFinite() }
        val tripDistance = tripKm.takeIf { it.isFinite() }
        val duration = durationMin.takeIf { it.isFinite() }
        val expected = setOf("pickup", "destination", "fare", "bonus", "pickupDistanceKm", "tripDistanceKm", "durationMinutes")
        val present = buildSet {
            if (pickup.isNotBlank()) add("pickup")
            if (destination.isNotBlank()) add("destination")
            if (fareMoney != null) add("fare")
            if (bonusMoney != null) add("bonus")
            if (pickupDistance != null) add("pickupDistanceKm")
            if (tripDistance != null) add("tripDistanceKm")
            if (duration != null) add("durationMinutes")
        }
        val fingerprint = sha256(raw.ifBlank { "$platform|$pickup|$destination|$fare|$bonus|$capturedAt" })
        val key = LegacyProviderEventPolicy.offerDedupeKey(id, status)
        return ProviderEvent(
            eventId = "command-offer:$id:${status.lowercase()}",
            provider = provider,
            adapter = AdapterIdentity("busybee.legacy.offer-store", "1"),
            type = type,
            sourceMethod = sourceMethod,
            occurredAtEpochMs = capturedAt,
            receivedAtEpochMs = now,
            payload = ProviderEventPayload.Offer(
                pickup, destination, fareMoney, bonusMoney,
                pickupDistance, tripDistance, duration
            ),
            completeness = FieldCompleteness(expected, present, if (present.containsAll(expected)) 1.0 else null),
            evidence = EvidenceReference(fingerprint, "command-offer:$id", EvidenceRetention.LIMITED, redacted = raw.isBlank()),
            dedupeKey = key
        )
    }

    private fun CapturedNotice.toProviderEvent(): ProviderEvent {
        val fingerprint = sha256("$platform|$title|$body")
        return ProviderEvent(
            eventId = "command-notice:$id",
            provider = providerId(platform),
            adapter = AdapterIdentity("busybee.android-notification", "1"),
            type = ProviderEventType.SOURCE_OBSERVATION,
            sourceMethod = SourceMethod.ANDROID_NOTIFICATION,
            occurredAtEpochMs = capturedAt,
            receivedAtEpochMs = capturedAt,
            payload = ProviderEventPayload.Observation(title, body),
            completeness = FieldCompleteness(setOf("title", "body"), setOf("title", "body"), 1.0),
            evidence = EvidenceReference(fingerprint, "command-notice:$id", EvidenceRetention.LIMITED, redacted = false),
            dedupeKey = LegacyProviderEventPolicy.notificationDedupeKey(id)
        )
    }

    private fun DriverRecord.toProviderEvent(now: Long): ProviderEvent {
        val provider = providerId(platform)
        val normalizedType = type.lowercase()
        val isTrip = normalizedType in setOf("trip", "ride", "completed_trip")
        val isOffer = normalizedType == "offer"
        val currencyCode = currency.uppercase().takeIf { it.matches(Regex("[A-Z]{3}")) }
        val duration = durationMinutes?.takeIf { it.isFinite() }
        val grossMoney = gross?.takeIf { currencyCode != null }?.let { Money(it, currencyCode!!) }
        val feesMoney = fees?.takeIf { currencyCode != null }?.let { Money(it, currencyCode!!) }
        val bonusMoney = bonus?.takeIf { currencyCode != null }?.let { Money(it, currencyCode!!) }
        val amountMoney = grossMoney ?: feesMoney ?: bonusMoney
        val expected = when {
            isTrip -> setOf("pickup", "dropoff", "distanceKm", "durationMinutes", "gross", "fees", "bonus")
            isOffer -> setOf("pickup", "dropoff", "gross")
            amountMoney != null -> setOf("amount")
            else -> setOf("sourceRecord")
        }
        val present = buildSet {
            if (pickup != null) add("pickup")
            if (dropoff != null) add("dropoff")
            if (distanceKm != null) add("distanceKm")
            if (duration != null) add("durationMinutes")
            if (grossMoney != null) add("gross")
            if (feesMoney != null) add("fees")
            if (bonusMoney != null) add("bonus")
            if (amountMoney != null && !isTrip && !isOffer) add("amount")
            if (expected == setOf("sourceRecord")) add("sourceRecord")
        }
        val eventType = when {
            isTrip -> if (status.equals("cancelled", true)) ProviderEventType.TRIP_CANCELLED else ProviderEventType.TRIP_COMPLETED
            isOffer -> ProviderEventType.OFFER
            normalizedType == "payout" -> ProviderEventType.PAYOUT
            normalizedType in setOf("adjustment", "earnings_adjustment") -> ProviderEventType.EARNINGS_ADJUSTMENT
            else -> ProviderEventType.SOURCE_OBSERVATION
        }
        val payload = when {
            isTrip -> ProviderEventPayload.Trip(
                pickup, dropoff, distanceKm, duration,
                grossMoney, feesMoney, bonusMoney
            )
            isOffer -> ProviderEventPayload.Offer(
                pickup, dropoff, grossMoney,
                bonusMoney, null, distanceKm, null
            )
            amountMoney != null -> ProviderEventPayload.Earnings(
                amountMoney, normalizedType, status
            )
            else -> ProviderEventPayload.Observation("$platform · $type", status)
        }
        val fingerprint = sha256(raw)
        val occurred = parseTimestamp(occurredAt)
        return ProviderEvent(
            eventId = "mirror-record:$id",
            provider = provider,
            adapter = AdapterIdentity("busybee.user-import", "1"),
            type = eventType,
            sourceMethod = LegacyProviderEventPolicy.sourceMethodForRecord(sourceName),
            occurredAtEpochMs = occurred,
            receivedAtEpochMs = now,
            externalEntityId = externalId,
            payload = payload,
            completeness = FieldCompleteness(expected, present),
            evidence = EvidenceReference(fingerprint, "mirror-record:$id", EvidenceRetention.KEEP_SOURCE_REFERENCE, redacted = false),
            dedupeKey = externalId?.takeIf { it.isNotBlank() }?.let { "external:$it" } ?: "import:$fingerprint"
        )
    }

    private fun providerId(platform: String): ProviderId = when {
        platform.contains("uber", ignoreCase = true) -> ProviderId.UBER
        platform.contains("bolt", ignoreCase = true) -> ProviderId.BOLT
        else -> ProviderId.OTHER
    }

    private fun parseTimestamp(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try { Instant.parse(value).toEpochMilli() } catch (_: Exception) {
            try { OffsetDateTime.parse(value).toInstant().toEpochMilli() } catch (_: Exception) {
                try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)?.time } catch (_: Exception) { null }
            }
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
