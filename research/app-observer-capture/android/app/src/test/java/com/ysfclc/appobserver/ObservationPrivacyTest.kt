package com.ysfclc.appobserver

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationPrivacyTest {
    @Test
    fun networkMetadataKeepsOnlyTheAllowlistedFields() {
        val input = JSONObject()
            .put("timestampEpochMs", 1234L)
            .put("appName", "com.example.shop")
            .put("protocol", "tcp")
            .put("ipVersion", 4)
            .put("sourcePort", 443)
            .put("destinationPort", 51822)
            .put("tcpFlags", 24)
            .put("payloadBytes", 420)
            .put("frameBytes", 480)
            .put("sourceIp", "192.0.2.10")
            .put("destinationIp", "203.0.113.8")
            .put("tcpSequence", 987654321L)
            .put("uid", 10234)
            .put("payload", "sensitive response body")

        val safe = ObservationPrivacy.safeNetworkMetadata(input)

        assertEquals("network-observation/v1", safe.getString("schema"))
        assertEquals("tcp", safe.getString("protocol"))
        assertEquals(443, safe.getInt("sourcePort"))
        assertFalse(safe.has("sourceIp"))
        assertFalse(safe.has("destinationIp"))
        assertFalse(safe.has("tcpSequence"))
        assertFalse(safe.has("uid"))
        assertFalse(safe.has("payload"))
    }

    @Test
    fun exportSanitiserRemovesSensitiveFieldsRecursively() {
        val legacy = JSONObject()
            .put("eventType", "view_clicked")
            .put("text", "A private message")
            .put("sourceIp", "192.0.2.2")
            .put("node", JSONObject()
                .put("className", "android.widget.Button")
                .put("contentDescription", "Pay Yusuf")
                .put("value", "1234 5678")
                .put("viewId", "com.example:id/confirm"))
        val safe = ObservationPrivacy.sanitizeForExport(legacy)
        val node = safe.getJSONObject("node")

        assertEquals("view_clicked", safe.getString("eventType"))
        assertFalse(safe.has("text"))
        assertFalse(safe.has("sourceIp"))
        assertFalse(node.has("contentDescription"))
        assertFalse(node.has("value"))
        assertEquals("android.widget.Button", node.getString("className"))
        assertEquals("com.example:id/confirm", node.getString("viewId"))
        assertTrue(safe.length() > 0)
    }
}
