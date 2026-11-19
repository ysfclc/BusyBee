package com.ysfclc.appobserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcapdroidPacketParserTest {
    @Test
    fun parsesUdpExporterPacketAndStripsPcapdroidTrailer() {
        val frame = udpIpv4Frame()
        val datagram = ByteArray(16 + frame.size + 32)
        val record = ByteBuffer.wrap(datagram).order(ByteOrder.LITTLE_ENDIAN)
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
        datagram[datagram.size - 4] = 0
        record.position(0)

        val parsed = PcapdroidPacketParser.parse(datagram, datagram.size)

        assertNotNull(parsed)
        assertEquals(12345, parsed!!.uid)
        assertEquals("com.ubercab.driver", parsed.appName)
        assertEquals(frame.size, parsed.ethernetFrame.size)
        assertEquals("udp", parsed.summary.getString("protocol"))
        assertEquals(1234, parsed.summary.getInt("sourcePort"))
        assertEquals(53, parsed.summary.getInt("destinationPort"))
        assertEquals(4, parsed.summary.getInt("payloadBytes"))
        assertEquals(frame.size, ByteBuffer.wrap(parsed.recordHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(8))
        assertEquals(frame.size, ByteBuffer.wrap(parsed.recordHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(12))
    }

    @Test
    fun detectsClassicPcapGlobalHeader() {
        val header = byteArrayOf(
            0xd4.toByte(), 0xc3.toByte(), 0xb2.toByte(), 0xa1.toByte(),
            0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0xff.toByte(), 0xff.toByte(),
            0x00, 0x00, 0x01, 0x00, 0x00, 0x00
        )
        assertTrue(PcapdroidPacketParser.isGlobalHeader(header, header.size))
    }

    private fun udpIpv4Frame(): ByteArray {
        val bytes = ByteArray(14 + 20 + 8 + 4)
        bytes[12] = 0x08
        bytes[13] = 0x00
        bytes[14] = 0x45
        bytes[16] = 0x00
        bytes[17] = 0x20
        bytes[23] = 17
        bytes[26] = 1
        bytes[29] = 1
        bytes[30] = 8
        bytes[33] = 8
        bytes[34] = 0x04
        bytes[35] = 0xd2.toByte()
        bytes[36] = 0x00
        bytes[37] = 0x35
        bytes[38] = 0x00
        bytes[39] = 0x0c
        bytes[42] = 1
        bytes[43] = 2
        bytes[44] = 3
        bytes[45] = 4
        return bytes
    }
}
