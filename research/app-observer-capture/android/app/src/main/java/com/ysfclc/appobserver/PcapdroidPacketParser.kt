package com.ysfclc.appobserver

import org.json.JSONObject
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder


data class PcapFileHeader(
    val byteOrder: ByteOrder,
    val linkType: Int,
    val nanoTimestamps: Boolean
)

data class PcapRecordHeader(
    val timestampSeconds: Long,
    val timestampFraction: Long,
    val capturedLength: Int,
    val originalLength: Int
)

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
    const val DLT_LINUX_SLL = 113
    const val DLT_LINUX_SLL2 = 276
    private const val MAX_PACKET_BYTES = 16 * 1024 * 1024

    /** Build a classic-PCAP global header whose link type matches packetData. */
    fun globalHeader(linkType: Int): ByteArray {
        require(linkType == DLT_RAW || linkType == DLT_EN10MB) { "Unsupported PCAP link type: $linkType" }
        return byteArrayOf(
            0xd4.toByte(), 0xc3.toByte(), 0xb2.toByte(), 0xa1.toByte(),
            0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0xff.toByte(), 0xff.toByte(), 0x00, 0x00,
            linkType.toByte(), (linkType ushr 8).toByte(), 0x00, 0x00
        )
    }

    fun isGlobalHeader(bytes: ByteArray, length: Int): Boolean =
        length == 24 && bytes.size >= 4 &&
            bytes[0] == 0xd4.toByte() && bytes[1] == 0xc3.toByte() &&
            bytes[2] == 0xb2.toByte() && bytes[3] == 0xa1.toByte()


    /** Parse a standard 24-byte classic-PCAP global header from TCP Exporter. */
    fun parseGlobalHeader(bytes: ByteArray): PcapFileHeader? {
        if (bytes.size < 24) return null
        val orderAndResolution = when {
            bytes[0] == 0xd4.toByte() && bytes[1] == 0xc3.toByte() &&
                bytes[2] == 0xb2.toByte() && bytes[3] == 0xa1.toByte() ->
                ByteOrder.LITTLE_ENDIAN to false
            bytes[0] == 0xa1.toByte() && bytes[1] == 0xb2.toByte() &&
                bytes[2] == 0xc3.toByte() && bytes[3] == 0xd4.toByte() ->
                ByteOrder.BIG_ENDIAN to false
            bytes[0] == 0x4d.toByte() && bytes[1] == 0x3c.toByte() &&
                bytes[2] == 0xb2.toByte() && bytes[3] == 0xa1.toByte() ->
                ByteOrder.LITTLE_ENDIAN to true
            bytes[0] == 0xa1.toByte() && bytes[1] == 0xb2.toByte() &&
                bytes[2] == 0x3c.toByte() && bytes[3] == 0x4d.toByte() ->
                ByteOrder.BIG_ENDIAN to true
            else -> return null
        }
        val linkType = ByteBuffer.wrap(bytes).order(orderAndResolution.first).getInt(20) and 0xffff
        return PcapFileHeader(orderAndResolution.first, linkType, orderAndResolution.second)
    }

    /** Validate and decode one standard 16-byte classic-PCAP packet record header. */
    fun parseRecordHeader(bytes: ByteArray, order: ByteOrder): PcapRecordHeader? {
        if (bytes.size != RECORD_HEADER_SIZE) return null
        val record = ByteBuffer.wrap(bytes).order(order)
        val seconds = record.int.toLong() and 0xffffffffL
        val fraction = record.int.toLong() and 0xffffffffL
        val captured = record.int
        val original = record.int
        if (captured < 0 || captured > MAX_PACKET_BYTES || original < captured || original < 0) return null
        return PcapRecordHeader(seconds, fraction, captured, original)
    }

    /** Build a safe metadata index entry without retaining or decoding application payload bytes. */
    fun summarizePcapPacket(
        packetData: ByteArray,
        fileHeader: PcapFileHeader,
        record: PcapRecordHeader,
        selectedPackage: String
    ): JSONObject {
        val fractionMicros = if (fileHeader.nanoTimestamps) record.timestampFraction / 1_000L
            else record.timestampFraction
        val result = baseSummary(
            record.timestampSeconds, fractionMicros, selectedPackage, null, record.capturedLength
        )
        // TCP-exported packet bytes are represented only by a short in-memory prefix.
        // Do not materialise IP addresses or TCP sequence identifiers for this metadata path.
        return when (fileHeader.linkType) {
            DLT_RAW -> summarizeRawIp(result, packetData, includeAddresses = false)
            DLT_EN10MB -> summarizeEthernet(result, packetData, includeAddresses = false)
            DLT_LINUX_SLL -> summarizeLinuxCooked(result, packetData, 16, 14, includeAddresses = false)
            DLT_LINUX_SLL2 -> summarizeLinuxCooked(result, packetData, 20, 0, includeAddresses = false)
            else -> result.put("protocol", "linktype-${fileHeader.linkType}")
        }
    }

    private fun summarizeRawIp(
        result: JSONObject,
        packet: ByteArray,
        includeAddresses: Boolean = true
    ): JSONObject {
        if (packet.isEmpty()) return result.put("protocol", "unknown")
        return summarizeIp(result, packet, 0, "raw", includeAddresses)
    }

    private fun summarizeEthernet(
        result: JSONObject,
        frame: ByteArray,
        includeAddresses: Boolean = true
    ): JSONObject {
        if (frame.size < 14) return result.put("protocol", "truncated_ethernet")
        var offset = 14
        var etherType = u16(frame, 12)
        while (etherType == 0x8100 || etherType == 0x88a8 || etherType == 0x9100) {
            if (frame.size < offset + 4) return result.put("protocol", "truncated_vlan")
            etherType = u16(frame, offset + 2)
            offset += 4
        }
        return when (etherType) {
            0x0800, 0x86dd -> summarizeIp(result, frame, offset, "ethernet", includeAddresses)
            0x0806 -> result.put("protocol", "arp")
            else -> result.put("protocol", "non_ip")
        }
    }

    private fun summarizeLinuxCooked(
        result: JSONObject,
        frame: ByteArray,
        headerLength: Int,
        protocolOffset: Int,
        includeAddresses: Boolean = true
    ): JSONObject {
        if (frame.size < headerLength || frame.size < protocolOffset + 2) {
            return result.put("protocol", "truncated_linux_cooked")
        }
        return when (u16(frame, protocolOffset)) {
            0x0800, 0x86dd -> summarizeIp(result, frame, headerLength, "linux_cooked", includeAddresses)
            0x0806 -> result.put("protocol", "arp")
            else -> result.put("protocol", "non_ip")
        }
    }

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
        val ipVersion = (bytes[RECORD_HEADER_SIZE].toInt() ushr 4) and 0x0f
        if (ipVersion != 4 && ipVersion != 6) return null
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
    ): JSONObject {
        val result = JSONObject()
            .put("timestampEpochMs", seconds * 1000L + micros / 1000L)
            .put("appName", appName)
            .put("frameBytes", packetBytes)
        // Omit UID entirely when the metadata-only path has no UID rather than storing null.
        uid?.let { result.put("uid", it) }
        return result
    }

    private fun summarizeIp(
        result: JSONObject,
        bytes: ByteArray,
        offset: Int,
        source: String,
        includeAddresses: Boolean = true
    ): JSONObject {
        if (bytes.size <= offset) return result.put("protocol", "truncated_ip")
        return when ((bytes[offset].toInt() ushr 4) and 0x0f) {
            4 -> summarizeIpv4(result, bytes, offset, source, includeAddresses)
            6 -> summarizeIpv6(result, bytes, offset, includeAddresses)
            else -> result.put("protocol", "unknown_ip_version")
        }
    }

    private fun summarizeIpv4(
        result: JSONObject,
        bytes: ByteArray,
        offset: Int,
        source: String,
        includeAddresses: Boolean
    ): JSONObject {
        if (bytes.size < offset + 20) return result.put("protocol", "truncated_ipv4")
        val versionAndLength = bytes[offset].toInt() and 0xff
        val ihl = (versionAndLength and 0x0f) * 4
        if ((versionAndLength ushr 4) != 4 || ihl < 20 || bytes.size < offset + ihl) {
            return result.put("protocol", "malformed_ipv4")
        }
        val totalLength = u16(bytes, offset + 2)
        if (totalLength < ihl) return result.put("protocol", "malformed_ipv4")
        val protocol = bytes[offset + 9].toInt() and 0xff
        result.put("ipVersion", 4)
        if (includeAddresses) {
            result.put("sourceIp", address(bytes, offset + 12, 4))
                .put("destinationIp", address(bytes, offset + 16, 4))
        }
        return summarizeTransport(result, bytes, offset + ihl, protocol, totalLength - ihl, includeAddresses)
    }

    private fun summarizeIpv6(
        result: JSONObject,
        bytes: ByteArray,
        offset: Int,
        includeAddresses: Boolean
    ): JSONObject {
        if (bytes.size < offset + 40) return result.put("protocol", "truncated_ipv6")
        val nextHeader = bytes[offset + 6].toInt() and 0xff
        val payloadLength = u16(bytes, offset + 4)
        result.put("ipVersion", 6)
        if (includeAddresses) {
            result.put("sourceIp", address(bytes, offset + 8, 16))
                .put("destinationIp", address(bytes, offset + 24, 16))
        }
        // Extension headers are not traversed on this metadata-only path.
        return summarizeTransport(result, bytes, offset + 40, nextHeader, payloadLength, includeAddresses)
    }

    private fun summarizeTransport(
        result: JSONObject,
        bytes: ByteArray,
        offset: Int,
        protocolNumber: Int,
        payloadLength: Int,
        includeSequence: Boolean = true
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
                    .put("tcpFlags", bytes[offset + 13].toInt() and 0xff)
                    .put("payloadBytes", (payloadLength - headerLength).coerceAtLeast(0))
                if (includeSequence) result.put("tcpSequence", u32(bytes, offset + 4))
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
