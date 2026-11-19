package com.ysfclc.appobserver

import org.json.JSONObject
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PcapdroidPacket(
    val recordHeader: ByteArray,
    val packetData: ByteArray,
    val linkType: Int,
    val uid: Int?,
    val appName: String?,
    val timestampSeconds: Long,
    val timestampMicros: Long,
    val summary: JSONObject
)

/**
 * Decodes PCAPdroid UDP-exporter datagrams with or without the optional
 * PCAPdroid metadata extension. The default mode uses unadorned raw IP packets
 * so capture does not depend on PCAPdroid's paid dump_extensions feature.
 */
object PcapdroidPacketParser {
    private const val RECORD_HEADER_SIZE = 16
    private const val EXTENSION_AND_FCS_SIZE = 32
    private const val MAGIC = 0x01072021
    const val DLT_RAW = 101
    const val DLT_EN10MB = 1

    fun isGlobalHeader(bytes: ByteArray, length: Int): Boolean =
        length == 24 && bytes.size >= 4 &&
            bytes[0] == 0xd4.toByte() && bytes[1] == 0xc3.toByte() &&
            bytes[2] == 0xb2.toByte() && bytes[3] == 0xa1.toByte()

    fun parse(bytes: ByteArray, length: Int, selectedPackage: String): PcapdroidPacket? {
        if (length <= RECORD_HEADER_SIZE) return null
        val header = bytes.copyOfRange(0, RECORD_HEADER_SIZE)
        val little = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val seconds = little.getInt(0).toLong() and 0xffffffffL
        val micros = little.getInt(4).toLong() and 0xffffffffL

        val extensionStart = length - EXTENSION_AND_FCS_SIZE
        if (length >= RECORD_HEADER_SIZE + 14 + EXTENSION_AND_FCS_SIZE) {
            val trailer = ByteBuffer.wrap(bytes, extensionStart, 28).order(ByteOrder.BIG_ENDIAN)
            if (trailer.int == MAGIC) {
                val uid = trailer.int
                val appBytes = ByteArray(20)
                trailer.get(appBytes)
                val appName = String(appBytes, Charsets.UTF_8).trim('\u0000', ' ')
                val frameLength = extensionStart - RECORD_HEADER_SIZE
                if (frameLength < 14 || appName.isBlank()) return null
                val frame = bytes.copyOfRange(RECORD_HEADER_SIZE, extensionStart)
                normalizeRecordLengths(header, frameLength)
                return PcapdroidPacket(
                    header, frame, DLT_EN10MB, uid, appName, seconds, micros,
                    summarizeEthernet(frame, uid, appName, seconds, micros)
                )
            }
        }

        // Without the optional extension, PCAPdroid exports raw IPv4/IPv6 packets.
        val packetLength = length - RECORD_HEADER_SIZE
        if (packetLength < 20) return null
        val packetData = bytes.copyOfRange(RECORD_HEADER_SIZE, length)
        normalizeRecordLengths(header, packetLength)
        return PcapdroidPacket(
            header, packetData, DLT_RAW, null, selectedPackage, seconds, micros,
            summarizeRawIp(packetData, selectedPackage, seconds, micros)
        )
    }

    private fun normalizeRecordLengths(header: ByteArray, packetLength: Int) {
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(8, packetLength)
            .putInt(12, packetLength)
    }

    private fun summarizeEthernet(
        frame: ByteArray,
        uid: Int,
        appName: String,
        seconds: Long,
        micros: Long
    ): JSONObject {
        val result = baseSummary(seconds, micros, appName, uid, frame.size)
        if (frame.size < 14) return result.put("protocol", "unknown")
        var offset = 14
        var etherType = u16(frame, 12)
        while (etherType == 0x8100 || etherType == 0x88a8 || etherType == 0x9100) {
            if (frame.size < offset + 4) return result.put("protocol", "truncated_vlan")
            etherType = u16(frame, offset + 2)
            offset += 4
        }
        return when (etherType) {
            0x0800 -> summarizeIp(result, frame, offset, "ethernet")
            0x86dd -> summarizeIp(result, frame, offset, "ethernet")
            0x0806 -> result.put("protocol", "arp")
            else -> result.put("protocol", "non_ip")
        }
    }

    private fun summarizeRawIp(
        packet: ByteArray,
        selectedPackage: String,
        seconds: Long,
        micros: Long
    ): JSONObject {
        val result = baseSummary(seconds, micros, selectedPackage, null, packet.size)
        if (packet.isEmpty()) return result.put("protocol", "unknown")
        return when ((packet[0].toInt() ushr 4) and 0x0f) {
            4 -> summarizeIp(result, packet, 0, "raw")
            6 -> summarizeIp(result, packet, 0, "raw")
            else -> result.put("protocol", "unknown_ip_version")
        }
    }

    private fun baseSummary(
        seconds: Long,
        micros: Long,
        appName: String,
        uid: Int?,
        packetBytes: Int
    ) = JSONObject()
        .put("timestampEpochMs", seconds * 1000L + micros / 1000L)
        .put("appName", appName)
        .put("uid", uid ?: JSONObject.NULL)
        .put("frameBytes", packetBytes)

    private fun summarizeIp(result: JSONObject, bytes: ByteArray, offset: Int, source: String): JSONObject {
        if (bytes.size <= offset) return result.put("protocol", "truncated_ip")
        return when ((bytes[offset].toInt() ushr 4) and 0x0f) {
            4 -> summarizeIpv4(result, bytes, offset, source)
            6 -> summarizeIpv6(result, bytes, offset)
            else -> result.put("protocol", "unknown_ip_version")
        }
    }

    private fun summarizeIpv4(result: JSONObject, bytes: ByteArray, offset: Int, source: String): JSONObject {
        if (bytes.size < offset + 20) return result.put("protocol", "truncated_ipv4")
        val versionAndLength = bytes[offset].toInt() and 0xff
        val ihl = (versionAndLength and 0x0f) * 4
        if ((versionAndLength ushr 4) != 4 || ihl < 20 || bytes.size < offset + ihl) {
            return result.put("protocol", "malformed_ipv4")
        }
        val totalLength = u16(bytes, offset + 2)
        if (totalLength < ihl) return result.put("protocol", "malformed_ipv4")
        val protocol = bytes[offset + 9].toInt() and 0xff
        val sourceIp = address(bytes, offset + 12, 4)
        val destinationIp = address(bytes, offset + 16, 4)
        result.put("ipVersion", 4).put("sourceIp", sourceIp).put("destinationIp", destinationIp)
        return summarizeTransport(result, bytes, offset + ihl, protocol, totalLength - ihl)
    }

    private fun summarizeIpv6(result: JSONObject, bytes: ByteArray, offset: Int): JSONObject {
        if (bytes.size < offset + 40) return result.put("protocol", "truncated_ipv6")
        val nextHeader = bytes[offset + 6].toInt() and 0xff
        val payloadLength = u16(bytes, offset + 4)
        val sourceIp = address(bytes, offset + 8, 16)
        val destinationIp = address(bytes, offset + 24, 16)
        result.put("ipVersion", 6).put("sourceIp", sourceIp).put("destinationIp", destinationIp)
        // Extension headers are retained in the PCAP; summary stops at the next-header value.
        return summarizeTransport(result, bytes, offset + 40, nextHeader, payloadLength)
    }

    private fun summarizeTransport(
        result: JSONObject,
        bytes: ByteArray,
        offset: Int,
        protocolNumber: Int,
        payloadLength: Int
    ): JSONObject {
        val protocol = when (protocolNumber) {
            6 -> "tcp"
            17 -> "udp"
            1 -> "icmp"
            58 -> "icmpv6"
            else -> "ip-$protocolNumber"
        }
        result.put("protocol", protocol)
        if (protocolNumber == 6 && bytes.size >= offset + 20) {
            val headerLength = ((bytes[offset + 12].toInt() ushr 4) and 0x0f) * 4
            if (headerLength >= 20 && bytes.size >= offset + headerLength) {
                result.put("sourcePort", u16(bytes, offset))
                    .put("destinationPort", u16(bytes, offset + 2))
                    .put("tcpSequence", u32(bytes, offset + 4))
                    .put("tcpFlags", bytes[offset + 13].toInt() and 0xff)
                    .put("payloadBytes", (payloadLength - headerLength).coerceAtLeast(0))
            }
        } else if (protocolNumber == 17 && bytes.size >= offset + 8) {
            result.put("sourcePort", u16(bytes, offset))
                .put("destinationPort", u16(bytes, offset + 2))
                .put("payloadBytes", (u16(bytes, offset + 4) - 8).coerceAtLeast(0))
        }
        return result
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private fun address(bytes: ByteArray, offset: Int, length: Int): String =
        runCatching { InetAddress.getByAddress(bytes.copyOfRange(offset, offset + length)).hostAddress ?: "unknown" }
            .getOrDefault("unknown")
}
