package com.ysfclc.busybee.drivercommandcentre

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

class PcapdroidHarInterpreterTest {

    @Test
    fun reconstructsCallsAndNormalizesIdentifiedOfferWithoutLeakingSecrets() {
        val offer = JSONObject()
            .put("offer_id", "offer-17")
            .put("fare", 24.5)
            .put("currency", "PLN")
            .put("pickup_address", "Krakow Main Station")
            .put("trip_distance_km", 6.2)
            .put("token", "body-secret-value")
        val requestBody = JSONObject()
            .put("event_type", "offer_updated")
            .put("offer", offer)

        val request = JSONObject()
            .put("method", "POST")
            .put("url", "https://europe-company.taxify.eu/v1/driver/offers?access_token=query-secret")
            .put("headers", JSONArray().put(JSONObject()
                .put("name", "Authorization")
                .put("value", "Bearer header-secret-value")))
            .put("postData", JSONObject()
                .put("mimeType", "application/json")
                .put("text", requestBody.toString()))
        val responseOffer = JSONObject()
            .put("offer_id", "offer-17")
            .put("fare", 24.5)
            .put("currency", "PLN")
            .put("pickup_address", "Krakow Main Station")
            .put("trip_distance_km", 6.2)
        val response = JSONObject()
            .put("status", 200)
            .put("headers", JSONArray().put(JSONObject()
                .put("name", "Content-Type")
                .put("value", "application/json")))
            .put("content", JSONObject()
                .put("mimeType", "application/json")
                .put("text", JSONObject().put("offer", responseOffer).toString()))
        val entry = JSONObject()
            .put("startedDateTime", "2026-11-18T19:21:00.000Z")
            .put("request", request)
            .put("response", response)

        val analysis = PcapdroidHarInterpreter.interpret(har(entry), "Bolt")

        assertEquals(1, analysis.calls.size)
        assertEquals("europe-company.taxify.eu", analysis.calls[0].host)
        assertEquals("/v1/driver/offers", analysis.calls[0].path)
        assertEquals(200, analysis.calls[0].status)
        assertEquals("provider_api_candidate", analysis.calls[0].classification)
        assertEquals("json", analysis.calls[0].requestKind)
        assertTrue(analysis.calls[0].requestPreview.contains("[REDACTED]"))
        assertFalse(analysis.calls[0].requestPreview.contains("body-secret-value"))
        assertFalse(analysis.calls[0].requestPreview.contains("header-secret-value"))
        assertFalse(analysis.calls[0].path.contains("query-secret"))

        assertEquals(1, analysis.normalizedRows.size)
        val normalized = analysis.normalizedRows.single()
        assertEquals("Bolt", normalized["platform"])
        assertEquals("offer", normalized["record_type"])
        assertEquals("offer-17", normalized["external_id"])
        assertEquals("24.5", normalized["gross"])
        assertEquals("6.2", normalized["distance_km"])
        assertEquals("PLN", normalized["currency"])
        assertEquals("Krakow Main Station", normalized["pickup"])
    }

    @Test
    fun keepsAnalyticsAndCrashReportingOutOfDriverRecords() {
        val decoy = JSONObject()
            .put("offer", JSONObject().put("offer_id", "not-a-driver-offer").put("fare", 99))
        val crash = simpleEntry("https://crashlyticsreports-pa.googleapis.com/v1/reports", decoy.toString())
        val analytics = simpleEntry("https://api.mixpanel.com/track", decoy.toString())

        val analysis = PcapdroidHarInterpreter.interpret(har(crash, analytics), "Bolt")

        assertEquals("diagnostics", analysis.calls[0].classification)
        assertEquals("analytics", analysis.calls[1].classification)
        assertTrue(analysis.normalizedRows.isEmpty())
    }

    @Test
    fun identifiesProtobufLikeBinaryWithoutInventingItsSchema() {
        val bytes = byteArrayOf(8, 0x96.toByte(), 1, 18, 3, 66, 111, 98)
        val entry = JSONObject()
            .put("request", JSONObject()
                .put("method", "GET")
                .put("url", "https://europe-company.taxify.eu/v1/ride/data"))
            .put("response", JSONObject()
                .put("status", 200)
                .put("content", JSONObject()
                    .put("mimeType", "application/octet-stream")
                    .put("encoding", "base64")
                    .put("text", Base64.getEncoder().encodeToString(bytes))))

        val analysis = PcapdroidHarInterpreter.interpret(har(entry), "Bolt")

        assertEquals("protobuf-like", analysis.calls.single().responseKind)
        assertEquals(1, analysis.protobufBodies)
        assertTrue(analysis.calls.single().responsePreview.contains("schema is required"))
        assertTrue(analysis.normalizedRows.isEmpty())
    }

    @Test
    fun expandsGzipBeforeParsingJson() {
        val json = """{"state":"online","heartbeat":true}"""
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val entry = JSONObject()
            .put("request", JSONObject()
                .put("method", "GET")
                .put("url", "https://europe-company.taxify.eu/api/driver/state"))
            .put("response", JSONObject()
                .put("status", 200)
                .put("headers", JSONArray().put(JSONObject()
                    .put("name", "Content-Encoding")
                    .put("value", "gzip")))
                .put("content", JSONObject()
                    .put("mimeType", "application/json")
                    .put("encoding", "base64")
                    .put("text", Base64.getEncoder().encodeToString(output.toByteArray()))))

        val analysis = PcapdroidHarInterpreter.interpret(har(entry), "Bolt")

        assertEquals("json", analysis.calls.single().responseKind)
        assertTrue(analysis.calls.single().responsePreview.contains("online"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHarJson() {
        PcapdroidHarInterpreter.interpret("""{"log":{}}""", "Bolt")
    }

    private fun simpleEntry(url: String, responseBody: String): JSONObject =
        JSONObject()
            .put("request", JSONObject().put("method", "POST").put("url", url))
            .put("response", JSONObject()
                .put("status", 200)
                .put("content", JSONObject().put("mimeType", "application/json").put("text", responseBody)))

    private fun har(vararg entries: JSONObject): String {
        val list = JSONArray()
        entries.forEach { list.put(it) }
        return JSONObject().put("log", JSONObject().put("entries", list)).toString()
    }
}
