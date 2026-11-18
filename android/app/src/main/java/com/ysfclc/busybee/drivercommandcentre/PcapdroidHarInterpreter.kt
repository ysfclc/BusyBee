package com.ysfclc.busybee.drivercommandcentre

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.Instant
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

data class PcapdroidCall(
    val index: Int,
    val host: String,
    val method: String,
    val path: String,
    val status: Int?,
    val startedAt: String?,
    val requestMimeType: String?,
    val responseMimeType: String?,
    val classification: String,
    val requestKind: String,
    val responseKind: String,
    val requestBytes: Int,
    val responseBytes: Int,
    val requestPreview: String,
    val responsePreview: String
)

data class PcapdroidCaptureAnalysis(
    val provider: String,
    val calls: List<PcapdroidCall>,
    val normalizedRows: List<Map<String, String>>,
    val jsonBodies: Int,
    val textBodies: Int,
    val binaryBodies: Int,
    val protobufBodies: Int
)

object PcapdroidHarInterpreter {
    private const val MAX_HAR_CHARS = 40 * 1024 * 1024
    private const val MAX_BODY_BYTES = 2 * 1024 * 1024
    private const val MAX_PREVIEW_CHARS = 3_500
    private const val MAX_ENTRIES = 20_000

    private data class DecodedBody(
        val kind: String,
        val byteCount: Int,
        val preview: String,
        val json: Any?
    )

    fun interpret(harText: String, provider: String): PcapdroidCaptureAnalysis {
        require(harText.isNotBlank()) { "The HAR file is empty." }
        require(harText.length <= MAX_HAR_CHARS) { "HAR file is too large (40 MB limit)." }
        val root = try { JSONObject(harText) } catch (error: JSONException) {
            throw IllegalArgumentException("This is not a valid HAR JSON file.", error)
        }
        val entries = root.optJSONObject("log")?.optJSONArray("entries")
            ?: throw IllegalArgumentException("HAR is missing log.entries.")
        require(entries.length() <= MAX_ENTRIES) { "HAR contains too many entries." }

        val safeProvider = provider.trim().ifBlank { "Unknown" }
        val calls = ArrayList<PcapdroidCall>(entries.length())
        val records = LinkedHashMap<String, Map<String, String>>()
        var jsonBodies = 0
        var textBodies = 0
        var binaryBodies = 0
        var protobufBodies = 0

        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            val request = entry.optJSONObject("request") ?: JSONObject()
            val response = entry.optJSONObject("response") ?: JSONObject()
            val url = request.optString("url", "")
            val uri = runCatching { URI(url) }.getOrNull()
            val host = (uri?.host ?: hostFromUrl(url)).lowercase()
            val path = (uri?.rawPath ?: "/").ifBlank { "/" }.take(512)
            val method = request.optString("method", "UNKNOWN").uppercase().take(16)
            val requestHeaders = request.optJSONArray("headers") ?: JSONArray()
            val responseHeaders = response.optJSONArray("headers") ?: JSONArray()

            val requestPost = request.optJSONObject("postData") ?: JSONObject()
            val requestMime = firstNonBlank(requestPost.optString("mimeType", ""), header(requestHeaders, "content-type"))
            val responseContent = response.optJSONObject("content") ?: JSONObject()
            val responseMime = firstNonBlank(responseContent.optString("mimeType", ""), header(responseHeaders, "content-type"))

            val requestBody = decodeBody(
                requestBodyText(requestPost), requestPost.optString("encoding", ""),
                requestMime, header(requestHeaders, "content-encoding")
            )
            val responseBody = decodeBody(
                responseContent.optString("text", "").takeIf { responseContent.has("text") && !responseContent.isNull("text") },
                responseContent.optString("encoding", ""), responseMime, header(responseHeaders, "content-encoding")
            )

            listOf(requestBody, responseBody).forEach { body ->
                when (body.kind) {
                    "json" -> jsonBodies++
                    "text", "form" -> textBodies++
                    "binary", "protobuf-like", "compressed", "oversize" -> binaryBodies++
                }
                if (body.kind == "protobuf-like") protobufBodies++
            }

            val status = if (response.has("status")) response.optInt("status").takeIf { it > 0 } else null
            val startedAt = entry.optString("startedDateTime", "").takeIf { it.isNotBlank() }
            val classification = classify(host, path, requestHeaders)
            val call = PcapdroidCall(
                index + 1, host.ifBlank { "unknown-host" }, method, path, status, startedAt,
                requestMime, responseMime, classification, requestBody.kind, responseBody.kind,
                requestBody.byteCount, responseBody.byteCount, requestBody.preview, responseBody.preview
            )
            calls.add(call)

            if (classification == "provider_api_candidate") {
                listOf("request" to requestBody.json, "response" to responseBody.json).forEach { (direction, payload) ->
                    if (payload != null) {
                        extractBusinessRows(payload, safeProvider, path.lowercase(), call).forEach { row ->
                            val key = listOf(
                                row["platform"], row["record_type"], row["external_id"], row["occurred_at"],
                                row["gross"], row["fees"], row["bonus"], row["status"],
                                row["pickup"], row["dropoff"], row["distance_km"]
                            ).joinToString("|")
                            if (!records.containsKey(key)) records[key] = row + mapOf("_capture_direction" to direction)
                        }
                    }
                }
            }
        }

        return PcapdroidCaptureAnalysis(
            safeProvider, calls, records.values.toList(), jsonBodies, textBodies, binaryBodies, protobufBodies
        )
    }

    private fun requestBodyText(postData: JSONObject): String? {
        if (postData.has("text") && !postData.isNull("text")) return postData.optString("text")
        val params = postData.optJSONArray("params") ?: return null
        if (params.length() == 0) return null
        return (0 until params.length()).mapNotNull { index ->
            val item = params.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            "$name=" + item.optString("value", "")
        }.joinToString("&")
    }

    private fun decodeBody(raw: String?, encoding: String, mime: String?, contentEncoding: String?): DecodedBody {
        if (raw.isNullOrEmpty()) return DecodedBody("empty", 0, "No body recorded.", null)
        var bytes = try {
            if (encoding.equals("base64", true)) Base64.getMimeDecoder().decode(raw)
            else raw.toByteArray(Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return DecodedBody("invalid-base64", 0, "Payload was marked base64 but could not be decoded.", null)
        }
        if (bytes.size > MAX_BODY_BYTES) {
            return DecodedBody("oversize", bytes.size, "Payload exceeds the 2 MB inspection limit.", null)
        }

        val coding = contentEncoding.orEmpty().lowercase()
        val gzip = coding.contains("gzip") || (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())
        val deflate = coding.contains("deflate")
        if (gzip || deflate) {
            bytes = try {
                val input = ByteArrayInputStream(bytes)
                if (gzip) GZIPInputStream(input).use { readBounded(it) }
                else InflaterInputStream(input).use { readBounded(it) }
            } catch (_: Exception) {
                return DecodedBody("compressed", bytes.size, "Compressed payload could not be expanded (or exceeded the inspection limit).", null)
            }
        } else if (coding.contains("br") || coding.contains("zstd")) {
            return DecodedBody("compressed", bytes.size, "Payload uses $coding compression; this build does not decode it yet.", null)
        }

        val text = bytes.toString(Charsets.UTF_8)
        val binary = looksBinary(bytes, text)
        val json = if (!binary) parseJson(text) else null
        if (json != null) return DecodedBody("json", bytes.size, prettySanitized(json), json)
        if (binary) {
            val protobuf = protobufSketch(bytes)
            return DecodedBody(
                if (protobuf != null) "protobuf-like" else "binary",
                bytes.size,
                protobuf ?: "Binary payload · \${bytes.size} bytes · " + hexPreview(bytes),
                null
            )
        }
        val form = mime.orEmpty().contains("x-www-form-urlencoded", true)
        val preview = redactText(text).take(MAX_PREVIEW_CHARS)
        return DecodedBody(if (form) "form" else "text", bytes.size, preview.ifBlank { "Empty text body." }, null)
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > MAX_BODY_BYTES) throw IllegalArgumentException("decompressed body too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun parseJson(text: String): Any? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !(trimmed.startsWith("{") || trimmed.startsWith("["))) return null
        return try { JSONTokener(trimmed).nextValue().takeIf { it is JSONObject || it is JSONArray } }
        catch (_: Exception) { null }
    }

    private fun looksBinary(bytes: ByteArray, text: String): Boolean {
        if (bytes.isEmpty()) return false
        if (bytes.any { it == 0.toByte() }) return true
        val sample = bytes.take(512)
        val controls = sample.count { value ->
            val v = value.toInt() and 0xff
            v < 0x09 || (v in 0x0e..0x1f)
        }
        return controls > sample.size / 12 || text.contains('\uFFFD')
    }

    private fun prettySanitized(value: Any): String {
        val sanitized = sanitizeJson(value, 0)
        val rendered = when (sanitized) {
            is JSONObject -> sanitized.toString(2)
            is JSONArray -> sanitized.toString(2)
            else -> sanitized.toString()
        }
        return rendered.take(MAX_PREVIEW_CHARS)
    }

    private fun sanitizeJson(value: Any?, depth: Int): Any? {
        if (depth > 16) return "[DEPTH_LIMIT]"
        return when (value) {
            is JSONObject -> JSONObject().also { result ->
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result.put(key, if (isSensitiveKey(key)) "[REDACTED]" else sanitizeJson(value.opt(key), depth + 1))
                }
            }
            is JSONArray -> JSONArray().also { result ->
                for (i in 0 until minOf(value.length(), 200)) result.put(sanitizeJson(value.opt(i), depth + 1))
                if (value.length() > 200) result.put("[TRUNCATED]")
            }
            is String -> redactText(value)
            else -> value
        }
    }

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase().replace(Regex("[^a-z0-9]"), "")
        return listOf(
            "authorization", "cookie", "setcookie", "token", "secret", "password", "passwd",
            "session", "jwt", "apikey", "accesskey", "refresh", "credential", "privatekey",
            "cardnumber", "cvv", "email", "phone", "passengername", "customername"
        ).any { normalized.contains(it) }
    }

    private fun redactText(value: String): String {
        var output = value
        output = output.replace(Regex("(?i)bearer\\s+[a-z0-9._~+/=-]+"), "Bearer [REDACTED]")
        output = output.replace(Regex("(?i)(authorization|cookie|set-cookie|access_token|refresh_token|client_secret)(\\s*[:=]\\s*)[^\\s,;]+")) {
            it.groupValues[1] + it.groupValues[2] + "[REDACTED]"
        }
        output = output.replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[REDACTED_EMAIL]")
        return output
    }

    private fun classify(host: String, path: String, headers: JSONArray): String {
        val h = host.lowercase()
        val p = path.lowercase()
        if (h.contains("crashlytics") || h.contains("sentry") || h.contains("bugsnag")) return "diagnostics"
        if (listOf("mixpanel", "braze", "amplitude", "segment", "app-measurement", "analytics").any { h.contains(it) }) return "analytics"
        if (header(headers, "upgrade").equals("websocket", true) || h.contains("mqtt") || h.contains("websocket")) return "realtime_handshake"
        val providerDomain = h.contains("taxify.eu") || h.contains("bolt.eu") ||
            h.contains("uber.com") || h.contains("ubercab.com")
        val semanticPath = listOf("offer", "ride", "trip", "driver", "earning", "payout", "fare", "order")
            .any { p.contains(it) }
        return if (providerDomain || semanticPath) "provider_api_candidate" else "other"
    }

    private fun extractBusinessRows(
        payload: Any,
        provider: String,
        pathContext: String,
        call: PcapdroidCall
    ): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        fun walk(node: Any?, context: String, depth: Int) {
            if (depth > 12 || result.size >= 100) return
            when (node) {
                is JSONObject -> {
                    val event = firstText(node, listOf("event_type", "eventType", "resource_type", "resourceType", "action"))
                    val hint = (context + " " + event.orEmpty()).lowercase()
                    val offerId = firstText(node, listOf("offer_id", "offerId", "offer_uuid", "offerUuid"))
                    val tripId = firstText(node, listOf("trip_id", "tripId", "ride_id", "rideId"))
                    val genericId = firstText(node, listOf("id", "uuid"))
                    val category = when {
                        offerId != null -> "offer"
                        tripId != null -> "trip"
                        genericId != null && hint.contains("offer") -> "offer"
                        genericId != null && (hint.contains("trip") || hint.contains("ride")) -> "trip"
                        else -> null
                    }
                    if (category != null) {
                        val externalId = if (category == "offer") offerId ?: genericId else tripId ?: genericId
                        if (!externalId.isNullOrBlank()) {
                            val row = linkedMapOf(
                                "platform" to provider,
                                "record_type" to category,
                                "external_id" to externalId,
                                "currency" to (firstText(node, listOf("currency", "currency_code", "currencyCode")) ?: "UNKNOWN"),
                                "_capture_host" to call.host,
                                "_capture_path" to call.path,
                                "_capture_call" to call.index.toString()
                            )
                            timestampField(node)?.let { row["occurred_at"] = it }
                            firstText(node, listOf("pickup", "pickup_address", "pickupAddress", "origin", "origin_address", "start_address"))
                                ?.let { row["pickup"] = it }
                            firstText(node, listOf("dropoff", "drop_off", "dropoff_address", "dropoffAddress", "destination", "destination_address", "end_address"))
                                ?.let { row["dropoff"] = it }
                            firstNumber(node, listOf("distance_km", "distanceKm", "trip_distance_km", "tripDistanceKm"))
                                ?.let { row["distance_km"] = it.toString() }
                                ?: firstNumber(node, listOf("distance_m", "distance_meters", "distanceMeters"))
                                    ?.let { row["distance_km"] = (it / 1000.0).toString() }
                            firstNumber(node, listOf("gross_amount", "grossAmount", "fare_amount", "fareAmount", "total_fare", "totalFare", "fare"))
                                ?.let { row["gross"] = it.toString() }
                            firstNumber(node, listOf("fees", "fee", "platform_fee", "platformFee", "commission"))
                                ?.let { row["fees"] = it.toString() }
                            firstNumber(node, listOf("bonus", "bonus_amount", "bonusAmount", "incentive"))
                                ?.let { row["bonus"] = it.toString() }
                            firstText(node, listOf("status", "state", "lifecycle_status", "lifecycleStatus"))
                                ?.let { row["status"] = it }
                            result.add(row)
                        }
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = node.opt(key)
                        if (child is JSONObject || child is JSONArray) walk(child, context + " " + key, depth + 1)
                    }
                }
                is JSONArray -> for (i in 0 until minOf(node.length(), 200)) walk(node.opt(i), context, depth + 1)
            }
        }
        walk(payload, pathContext, 0)
        return result
    }

    private fun firstText(obj: JSONObject, aliases: List<String>): String? {
        val wanted = aliases.map { normalizeKey(it) }.toSet()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (normalizeKey(key) in wanted) {
                val value = obj.opt(key)
                val text = when (value) {
                    is String -> value
                    is Number, is Boolean -> value.toString()
                    is JSONObject -> firstText(value, listOf("address", "formatted_address", "label", "name", "title", "value"))
                    else -> null
                }?.trim()
                if (!text.isNullOrBlank()) return text
            }
        }
        return null
    }

    private fun firstNumber(obj: JSONObject, aliases: List<String>): Double? {
        val wanted = aliases.map { normalizeKey(it) }.toSet()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (normalizeKey(key) in wanted) {
                val value = obj.opt(key)
                val number = when (value) {
                    is Number -> value.toDouble()
                    is JSONObject -> firstNumber(value, listOf("amount", "value", "distance"))
                    is String -> value.trim().replace(",", ".").toDoubleOrNull()
                    else -> null
                }
                if (number != null && number.isFinite()) return number
            }
        }
        return null
    }

    private fun timestampField(obj: JSONObject): String? {
        val value = firstText(obj, listOf("occurred_at", "occurredAt", "event_time", "eventTime", "created_at", "createdAt", "timestamp", "updated_at", "updatedAt"))
            ?: return null
        val numeric = value.toLongOrNull()
        if (numeric != null) {
            val millis = if (numeric in 1L..99_999_999_999L) numeric * 1000L else numeric
            return runCatching { Instant.ofEpochMilli(millis).toString() }.getOrNull()
        }
        return value
    }

    private fun normalizeKey(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun header(headers: JSONArray, name: String): String? {
        for (i in 0 until headers.length()) {
            val item = headers.optJSONObject(i) ?: continue
            if (item.optString("name").equals(name, true)) return item.optString("value").takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun firstNonBlank(vararg values: String): String? = values.firstOrNull { it.isNotBlank() }

    private fun hostFromUrl(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        return withoutScheme.substringBefore('/').substringBefore('?').substringBefore(':')
    }

    private fun hexPreview(bytes: ByteArray): String =
        bytes.take(48).joinToString(" ") { "%02x".format(it.toInt() and 0xff) }

    private fun protobufSketch(bytes: ByteArray): String? {
        if (bytes.size < 2) return null
        var offset = 0
        val fields = mutableListOf<String>()
        while (offset < bytes.size && fields.size < 8) {
            val tag = readVarint(bytes, offset) ?: break
            offset = tag.second
            val field = (tag.first ushr 3).toInt()
            val wire = (tag.first and 7L).toInt()
            if (field <= 0 || wire !in setOf(0, 1, 2, 5)) return null
            when (wire) {
                0 -> {
                    val value = readVarint(bytes, offset) ?: return null
                    offset = value.second
                }
                1 -> { if (offset + 8 > bytes.size) return null; offset += 8 }
                2 -> {
                    val length = readVarint(bytes, offset) ?: return null
                    offset = length.second
                    if (length.first > (bytes.size - offset).toLong()) return null
                    offset += length.first.toInt()
                }
                5 -> { if (offset + 4 > bytes.size) return null; offset += 4 }
            }
            fields.add("field $field / wire $wire")
        }
        if (fields.size < 2) return null
        return "Protobuf-like wire structure · " + fields.joinToString(", ") +
            ". A schema is required to assign meaning to these fields."
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var offset = start
        while (offset < bytes.size && shift < 64) {
            val b = bytes[offset].toInt() and 0xff
            result = result or ((b and 0x7f).toLong() shl shift)
            offset++
            if ((b and 0x80) == 0) return result to offset
            shift += 7
        }
        return null
    }
}
