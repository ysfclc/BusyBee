package com.ysfclc.appobserver

import org.json.JSONObject
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PcapdroidPacket(
    val recordHeader: ByteArray,
    val ethernetFrame: ByteArray,
    val uid: Int,
    val appName: String,
    val timestampSeconds: Long,
    val timestampMicros: Long,
    val summary: JSONObject
)

/**
 * Decodes PCAPdroid UDP-exporter datagrams.
 * The 28-byte app/UID extension plus trailing 4-byte FCS are removed from
 * the packet before it is written to a standard Ethernet PCAP file.
 */
object PcapdroidPacketParser {
    private const val RECORD_HEADER_SIZE = 16
    private const val EXTENSION_AND_FCS_SIZE = 32
    private const val MAGIC = 0x01072021

    fun isGlobalHeader(bytes: ByteArray, length: Int): Boolean =
        length == 24 && bytes.size >= 4 &&
            bytes[0] == 0xd4.toByte() && bytes[1] == 0xc3.toByte() &&
            bytes[2] == 0xb2.toByte() && bytes[3] == 0xa1.toByte()

    fun parse(bytes: ByteArray, length: Int): PcapdroidPacket? {
        if (length < RECORD_HEADER_SIZE + 14 + EXTENSION_AND_FCS_SIZE) return null
        val trailerStart = length - EXTENSION_AND_FCS_SIZE
        val trailer = ByteBuffer.wrap(bytes, trailerStart, 28).order(ByteOrder.BIG_ENDIAN)
        if (trailer.int != MAGIC) return null
        val uid = trailer.int
        val appBytes = ByteArray(20)
        trailer.get(appBytes)
        val appName = appBytes.toString(Charsets.UTF_8).trim('\u0000', ' ')
        if (appName.isBlank()) return null

        val frameLength = trailerStart - RECORD_HEADER_SIZE
        if (frameLength < 14) return null
        val recordHeader = bytes.copyOfRange(0, RECORD_HEADER_SIZE)
        ByteBuffer.wrap(recordHeader).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(8, frameLength)
            .putInt(12, frameLength)
        val frame = bytes.copyOfRange(RECORD_HEADER_SIZE, trailerStart)
        val header = ByteBuffer.wrap(recordHeader).order(ByteOrder.LITTLE_ENDIAN)
        val timestampSeconds = header.getInt(0).toLong() and 0xffffffffL
        val timestampMicros = header.getInt(4).toLong() and 0xffffffffL
        return PcapdroidPacket(
            recordHeader, frame, uid, appName, timestampSeconds, timestampMicros,
            summarizeFrame(frame, uid, appName, timestampSeconds, timestampMicros)
        )
    }

    private fun summarizeFrame(
        frame: ByteArray,
        uid: Int,
        appName: String,
        seconds: Long,
        micros: Long
    ): JSONObject {
        val result = JSONObject()
            .put("timestampEpochMs", seconds * 1000L + micros / 1000L)
            .put("appName", appName)
            .put("uid", uid)
            .put("frameBytes", frame.size)
        if (frame.size < 14) return result.put("protocol", "unknown")

        var networkOffset = 14
        var etherType = u16(frame, 12)
        while (etherType == 0x8100 || etherType == 0x88a8 || etherType == 0x9100) {
            if (frame.size < networkOffset + 4) return result.put("protocol", "truncated_vlan")
            etherType = u16(frame, networkOffset + 2)
            networkOffset += 4
        }

        if (etherType == 0x0800 && frame.size >= networkOffset + 20) {
            val versionAndLength = frame[networkOffset].toInt() and 0xff
            val ihl = (versionAndLength and 0x0f) * 4
            if ((versionAndLength ushr 4) != 4 || ihl < 20 || frame.size < networkOffset + ihl) {
                return result.put("protocol", "malformed_ipv4")
            }
            val ipProtocol = frame[networkOffset + 9].toInt() and 0xff
            val source = address(frame, networkOffset + 12, 4)
            val destination = address(frame, networkOffset + 16, 4)
            result.put("ipVersion", 4).put("sourceIp", source).put("destinationIp", destination)
            return transportSummary(result, frame, networkOffset + ihl, ipProtocol,
                u16(frame, networkOffset + 2).coerceAtLeast(ihl + 20) - ihl, source, destination)
        }

        if (etherType == 0x86dd && frame.size >= networkOffset + 40) {
            val nextHeader = frame[networkOffset + 6].toInt() and 0xff
            val source = address(frame, networkOffset + 8, 16)
            val destination = address(frame, networkOffset + 24, 16)
            result.put("ipVersion", 6).put("sourceIp", source).put("destinationIp", destination)
            return transportSummary(result, frame, networkOffset + 40, nextHeader,
                u16(frame, networkOffset + 4), source, destination)
        }
        return result.put("protocol", if (etherType == 0x0806) "arp" else "non_ip")
    }

    private fun transportSummary(
        result: JSONObject,
        frame: ByteArray,
        offset: Int,
        protocolNumber: Int,
        networkPayloadLength: Int,
        source: String,
        destination: String
    ): JSONObject {
        val protocol = when (protocolNumber) {
            6 -> "tcp"
            17 -> "udp"
            1 -> "icmp"
            58 -> "icmpv6"
            else -> "ip-$protocolNumber"
        }
        result.put("protocol", protocol)
        if (protocolNumber == 6 && frame.size >= offset + 20) {
            val headerLength = ((frame[offset + 12].toInt() ushr 4) and 0x0f) * 4
            if (headerLength >= 20 && frame.size >= offset + headerLength) {
                result.put("sourcePort", u16(frame, offset))
                    .put("destinationPort", u16(frame, offset + 2))
                    .put("tcpSequence", u32(frame, offset + 4))
                    .put("tcpFlags", frame[offset + 13].toInt() and 0xff)
                    .put("payloadBytes", (networkPayloadLength - headerLength).coerceAtLeast(0))
            }
        } else if (protocolNumber == 17 && frame.size >= offset + 8) {
            result.put("sourcePort", u16(frame, offset))
                .put("destinationPort", u16(frame, offset + 2))
                .put("payloadBytes", (u16(frame, offset + 4) - 8).coerceAtLeast(0))
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
