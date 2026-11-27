package com.ysfclc.busybee.drivercommandcentre

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/**
 * Versioned, deterministic local representation for ProviderEvent.
 * This is an internal storage format, not an interchange/API format.
 */
object ProviderEventCodec {
    private const val FORMAT_VERSION = 1

    fun encode(event: ProviderEvent): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeString(event.eventId)
            out.writeString(event.provider.name)
            out.writeString(event.adapter.id)
            out.writeString(event.adapter.version)
            out.writeString(event.type.name)
            out.writeString(event.sourceMethod.name)
            out.writeNullableLong(event.occurredAtEpochMs)
            out.writeLong(event.receivedAtEpochMs)
            out.writeNullableLong(event.expiresAtEpochMs)
            out.writeNullableString(event.correlationId)
            out.writeNullableString(event.externalEventId)
            out.writeNullableString(event.externalEntityId)
            when (val payload = event.payload) {
                is ProviderEventPayload.Offer -> {
                    out.writeByte(1)
                    out.writeNullableString(payload.pickup)
                    out.writeNullableString(payload.destination)
                    out.writeMoney(payload.fare)
                    out.writeMoney(payload.bonus)
                    out.writeNullableDouble(payload.pickupDistanceKm)
                    out.writeNullableDouble(payload.tripDistanceKm)
                    out.writeNullableDouble(payload.durationMinutes)
                }
                is ProviderEventPayload.Trip -> {
                    out.writeByte(2)
                    out.writeNullableString(payload.pickup)
                    out.writeNullableString(payload.destination)
                    out.writeNullableDouble(payload.distanceKm)
                    out.writeNullableDouble(payload.durationMinutes)
                    out.writeMoney(payload.gross)
                    out.writeMoney(payload.fees)
                    out.writeMoney(payload.bonus)
                }
                is ProviderEventPayload.Earnings -> {
                    out.writeByte(3)
                    out.writeMoney(payload.amount)
                    out.writeNullableString(payload.category)
                    out.writeNullableString(payload.description)
                }
                is ProviderEventPayload.Observation -> {
                    out.writeByte(4)
                    out.writeString(payload.title)
                    out.writeNullableString(payload.detail)
                }
            }
            out.writeStringSet(event.completeness.expectedFields)
            out.writeStringSet(event.completeness.presentFields)
            out.writeNullableDouble(event.completeness.confidence)
            out.writeString(event.evidence.fingerprint)
            out.writeNullableString(event.evidence.reference)
            out.writeString(event.evidence.retention.name)
            out.writeBoolean(event.evidence.redacted)
            out.writeString(event.dedupeKey)
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun decode(encoded: String): ProviderEvent {
        val bytes = Base64.getDecoder().decode(encoded)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == FORMAT_VERSION) { "Unsupported ProviderEvent storage version" }
            val eventId = input.readString()
            val provider = input.readEnum<ProviderId>()
            val adapter = AdapterIdentity(input.readString(), input.readString())
            val type = input.readEnum<ProviderEventType>()
            val source = input.readEnum<SourceMethod>()
            val occurred = input.readNullableLong()
            val received = input.readLong()
            val expires = input.readNullableLong()
            val correlation = input.readNullableString()
            val externalEvent = input.readNullableString()
            val externalEntity = input.readNullableString()
            val payload = when (input.readUnsignedByte()) {
                1 -> ProviderEventPayload.Offer(
                    input.readNullableString(), input.readNullableString(),
                    input.readMoney(), input.readMoney(),
                    input.readNullableDouble(), input.readNullableDouble(), input.readNullableDouble()
                )
                2 -> ProviderEventPayload.Trip(
                    input.readNullableString(), input.readNullableString(),
                    input.readNullableDouble(), input.readNullableDouble(),
                    input.readMoney(), input.readMoney(), input.readMoney()
                )
                3 -> ProviderEventPayload.Earnings(
                    input.readMoney() ?: error("Earnings amount missing"),
                    input.readNullableString(), input.readNullableString()
                )
                4 -> ProviderEventPayload.Observation(
                    input.readString(), input.readNullableString()
                )
                else -> error("Unknown ProviderEvent payload type")
            }
            val expected = input.readStringSet()
            val present = input.readStringSet()
            val confidence = input.readNullableDouble()
            val evidence = EvidenceReference(
                input.readString(), input.readNullableString(),
                input.readEnum<EvidenceRetention>(), input.readBoolean()
            )
            val dedupeKey = input.readString()
            require(input.available() == 0) { "Unexpected trailing ProviderEvent data" }
            return ProviderEvent(
                eventId, provider, adapter, type, source, occurred, received, expires,
                correlation, externalEvent, externalEntity, payload,
                FieldCompleteness(expected, present, confidence), evidence, dedupeKey
            )
        }
    }

    private fun DataOutputStream.writeString(value: String) {
        val data = value.toByteArray(Charsets.UTF_8)
        writeInt(data.size)
        write(data)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size in 0..1_048_576) { "Invalid ProviderEvent string length" }
        val data = ByteArray(size)
        readFully(data)
        return String(data, Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeString(value)
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readString() else null

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    private fun DataInputStream.readNullableLong(): Long? =
        if (readBoolean()) readLong() else null

    private fun DataOutputStream.writeNullableDouble(value: Double?) {
        writeBoolean(value != null)
        if (value != null) writeDouble(value)
    }

    private fun DataInputStream.readNullableDouble(): Double? =
        if (readBoolean()) readDouble() else null

    private fun DataOutputStream.writeMoney(value: Money?) {
        writeBoolean(value != null)
        if (value != null) {
            writeDouble(value.amount)
            writeString(value.currency)
        }
    }

    private fun DataInputStream.readMoney(): Money? =
        if (readBoolean()) Money(readDouble(), readString()) else null

    private fun DataOutputStream.writeStringSet(values: Set<String>) {
        val sorted = values.sorted()
        writeInt(sorted.size)
        sorted.forEach { writeString(it) }
    }

    private fun DataInputStream.readStringSet(): Set<String> {
        val count = readInt()
        require(count in 0..10_000) { "Invalid ProviderEvent set size" }
        return buildSet { repeat(count) { add(readString()) } }
    }

    private inline fun <reified T : Enum<T>> DataInputStream.readEnum(): T =
        enumValueOf<T>(readString())
}
