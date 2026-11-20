package com.ysfclc.appobserver

import org.json.JSONArray
import org.json.JSONObject

/**
 * Data-minimisation boundary for App Observer.
 * Only allowlisted network metadata is retained; raw addresses, identifiers and payloads are discarded.
 */
object ObservationPrivacy {
    private val networkFields = setOf(
        "timestampEpochMs",
        "appName",
        "frameBytes",
        "ipVersion",
        "protocol",
        "sourcePort",
        "destinationPort",
        "tcpFlags",
        "payloadBytes"
    )

    private val blockedKeys = setOf(
        "text", "title", "contentdescription", "textlines", "payload", "rawpayload",
        "authorization", "cookie", "cookies", "headers", "sourceip", "destinationip",
        "tcpsequence", "url", "uri", "domain", "email", "phone", "token", "password",
        "secret", "body", "message", "clipboard", "screenshot", "rawpacket", "packetdata"
    )

    fun safeNetworkMetadata(input: JSONObject): JSONObject {
        val safe = JSONObject().put("schema", "network-observation/v1")
        for (field in networkFields) {
            if (input.has(field) && !input.isNull(field)) {
                safe.put(field, input.get(field))
            }
        }
        return safe
    }

    /** Recursively strips sensitive fields from both new and legacy evidence during export. */
    fun sanitizeForExport(input: JSONObject): JSONObject = sanitizeObject(input)

    private fun sanitizeObject(input: JSONObject): JSONObject {
        val output = JSONObject()
        val keys = input.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val normalised = key.lowercase().replace("_", "").replace("-", "")
            if (normalised in blockedKeys) continue
            val value = input.opt(key)
            when (value) {
                is JSONObject -> output.put(key, sanitizeObject(value))
                is JSONArray -> output.put(key, sanitizeArray(value))
                JSONObject.NULL -> output.put(key, JSONObject.NULL)
                else -> output.put(key, value)
            }
        }
        return output
    }

    private fun sanitizeArray(input: JSONArray): JSONArray {
        val output = JSONArray()
        for (index in 0 until input.length()) {
            when (val value = input.opt(index)) {
                is JSONObject -> output.put(sanitizeObject(value))
                is JSONArray -> output.put(sanitizeArray(value))
                else -> output.put(value)
            }
        }
        return output
    }
}
