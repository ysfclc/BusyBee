package com.ysfclc.appobserver

import org.json.JSONArray
import java.security.MessageDigest

/**
 * Stable, privacy-safe signature for a captured UI tree.
 *
 * It intentionally excludes text, descriptions, editable values and screen coordinates.
 * The signature is structural: it groups trees with the same hierarchy, control classes,
 * resource IDs and boolean control states. It does not claim two screens have the same meaning.
 */
object UiStructureMatcher {
    const val VERSION = 1

    fun fingerprint(nodes: JSONArray): String {
        val canonical = buildString {
            append("app-observer-ui-structure-v").append(VERSION).append('\n')
            for (index in 0 until nodes.length()) {
                val node = nodes.optJSONObject(index) ?: continue
                append(node.optInt("index", index)).append('|')
                append(node.optInt("parentIndex", -1)).append('|')
                append(node.optInt("depth", 0)).append('|')
                append(node.optInt("childIndex", 0)).append('|')
                append(node.optString("className", "")).append('|')
                append(node.optString("viewId", "")).append('|')
                append(node.optBoolean("visible")).append('|')
                append(node.optBoolean("clickable")).append('|')
                append(node.optBoolean("scrollable")).append('|')
                append(node.optBoolean("editable")).append('|')
                append(node.optBoolean("password")).append('|')
                append(node.optBoolean("enabled")).append('|')
                append(node.optBoolean("selected")).append('|')
                append(node.optBoolean("checked")).append('|')
                append(node.optInt("childCount", 0)).append('\n')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
