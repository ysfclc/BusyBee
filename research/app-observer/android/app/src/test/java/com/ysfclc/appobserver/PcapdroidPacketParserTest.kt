package com.ysfclc.appobserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcapdroidPacketParserTest {
    @Test
    fun parsesRawIpUdpExporterPacketWithoutPaidMetadataExtension() {
        val packet = udpIpv4Packet()
        val datagram = ByteArray(16 + packet.size)
        ByteBuffer.wrap(datagram).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0, 1_700_000_000)
            .putInt(4, 250_000)
            .putInt(8, packet.size)
            .putInt(12, packet.size)
        packet.copyInto(datagram, 16)

        val parsed = PcapdroidPacketParser.parse(datagram, datagram.size, "ee.mtakso.driver")

        assertNotNull(parsed)
        assertEquals(PcapdroidPacketParser.DLT_RAW, parsed!!.linkType)
        assertEquals(null, parsed.uid)
        assertEquals("ee.mtakso.driver", parsed.appName)
        assertEquals(packet.size, parsed.packetData.size)
        assertEquals("udp", parsed.summary.getString("protocol"))
        assertEquals("1.1.1.1", parsed.summary.getString("sourceIp"))
        assertEquals("8.8.8.8", parsed.summary.getString("destinationIp"))
        assertEquals(1234, parsed.summary.getInt("sourcePort"))
        assertEquals(53, parsed.summary.getInt("destinationPort"))
        assertEquals(4, parsed.summary.getInt("payloadBytes"))
        assertEquals(packet.size, ByteBuffer.wrap(parsed.recordHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(8))
        assertEquals(packet.size, ByteBuffer.wrap(parsed.recordHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(12))
    }

    @Test
    fun parsesOptionalPcapdroidMetadataExtensionWhenPresent() {
        val frame = ethernetUdpIpv4Frame()
        val datagram = ByteArray(16 + frame.size + 32)
        ByteBuffer.wrap(datagram).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0, 1_700_000_000)
            .putInt(4, 250_000)
            .putInt(8, frame.size + 32)
            .putInt(12, frame.size + 32)
        frame.copyInto(datagram, 16)

        val trailer = ByteBuffer.wrap(datagram, 16 + frame.size, 28).order(ByteOrder.BIG_ENDIAN)
        trailer.putInt(0x01072021)
        trailer.putInt(12345)
        val appName = "com.ubercab.driver".toByteArray(Charsets.UTF_8)
        trailer.put(appName)
        trailer.put(ByteArray(20 - appName.size))

        val parsed = PcapdroidPacketParser.parse(datagram, datagram.size, "selected-app")

        assertNotNull(parsed)
        assertEquals(PcapdroidPacketParser.DLT_EN10MB, parsed!!.linkType)
        assertEquals(12345, parsed.uid)
        assertEquals("com.ubercab.driver", parsed.appName)
        assertEquals(frame.size, parsed.packetData.size)
        assertEquals("udp", parsed.summary.getString("protocol"))
        assertEquals(1234, parsed.summary.getInt("sourcePort"))
        assertEquals(53, parsed.summary.getInt("destinationPort"))
        assertEquals(4, parsed.summary.getInt("payloadBytes"))
    }

    @Test
    fun ignoresMalformedOrUnrelatedDatagrams() {
        assertNull(PcapdroidPacketParser.parse(ByteArray(40), 40, "selected-app"))
    }

    @Test
    fun detectsClassicPcapGlobalHeader() {
        val header = byteArrayOf(
            0xd4.toByte(), 0xc3.toByte(), 0xb2.toByte(), 0xa1.toByte(),
            0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0xff.toByte(), 0xff.toByte(),
            0x00, 0x00, 0x65, 0x00, 0x00, 0x00
        )
        assertTrue(PcapdroidPacketParser.isGlobalHeader(header, header.size))
    }

    private fun ethernetUdpIpv4Frame(): ByteArray {
        val ip = udpIpv4Packet()
        return ByteArray(14).apply {
            this[12] = 0x08
            this[13] = 0x00
        } + ip
    }

    private fun udpIpv4Packet(): ByteArray {
        val bytes = ByteArray(20 + 8 + 4)
        bytes[0] = 0x45
        bytes[2] = 0
        bytes[3] = bytes.size.toByte()
        bytes[9] = 17
        bytes[12] = 1
        bytes[15] = 1
        bytes[16] = 8
        bytes[19] = 8
        bytes[20] = 0x04
        bytes[21] = 0xd2.toByte()
        bytes[22] = 0x00
        bytes[23] = 0x35
        bytes[24] = 0x00
        bytes[25] = 0x0c
        bytes[28] = 1
        bytes[29] = 2
        bytes[30] = 3
        bytes[31] = 4
        return bytes
    }
}
