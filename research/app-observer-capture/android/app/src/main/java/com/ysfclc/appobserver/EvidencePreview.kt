package com.ysfclc.appobserver

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns the local JSONL evidence into a readable, bounded on-device preview.
 * Only structural fields and the network metadata allowlist are displayed.
 */
object EvidencePreview {
    fun ui(file: File?, totalEvents: Long): String {
        val records = readRecent(file, 60)
        if (records.isEmpty()) {
            return if (totalEvents == 0L) {
                "No UI evidence yet. Start a session, switch to the selected app, then use it normally. " +
                    "Return here to see its captured structure. UI observation does not depend on PCAPdroid."
            } else {
                "UI records exist, but the recent JSONL tail could not be read. Export the evidence ZIP for inspection."
            }
        }

        val snapshots = records.filter { it.optJSONArray("nodes") != null }
        val fingerprints = snapshots.mapNotNull {
            it.optString("structureFingerprint").takeIf(String::isNotBlank)
        }
        val groups = fingerprints.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })

        return buildString {
            append(totalEvents).append(" UI events · ")
                .append(snapshots.size).append(" screen snapshots in the recent sample window\n")
            if (groups.isEmpty()) {
                append("No structural fingerprints yet. Older records may not include this field.\n")
            } else {
                append("REPEATED STRUCTURES (recent window)\n")
                groups.take(4).forEach { (fingerprint, count) ->
                    append("• ").append(count).append(" matching captures · ")
                        .append(fingerprint.take(16)).append("…\n")
                }
            }

            val latest = snapshots.lastOrNull()
            if (latest != null) {
                val timestamp = latest.optLong("timestampEpochMs")
                val fingerprint = latest.optString("structureFingerprint")
                val matchCount = if (fingerprint.isNotBlank()) groups.firstOrNull { it.key == fingerprint }?.value ?: 1 else 1
                append("\nLATEST SCREEN · ").append(formatTime(timestamp)).append('\n')
                append("Structure match: ").append(matchCount)
                    .append(" capture(s) in this recent window")
                if (fingerprint.isNotBlank()) append(" · ").append(fingerprint.take(16)).append("…")
                append('\n')
                append("Window: ").append(latest.optString("windowClassName", "unknown"))
                    .append(" · ").append(latest.optInt("visibleNodeCount"))
                    .append('/').append(latest.optInt("nodeCount")).append(" visible nodes\n")
                if (latest.optBoolean("treeTruncated")) {
                    append("WARNING: tree reached the recorder's node limit; this snapshot is partial.\n")
                }
                append("VISIBLE CONTROL TREE (text intentionally omitted)\n")
                val nodes = latest.optJSONArray("nodes")
                var shown = 0
                if (nodes != null) {
                    for (i in 0 until nodes.length()) {
                        val node = nodes.optJSONObject(i) ?: continue
                        if (!node.optBoolean("visible")) continue
                        val depth = node.optInt("depth").coerceIn(0, 8)
                        val className = node.optString("className").takeUnless { it == "null" }
                            ?.substringAfterLast('.') ?: "Unknown control"
                        val viewId = node.optString("viewId").takeUnless { it == "null" || it.isBlank() }
                            ?.substringAfterLast('/') ?: ""
                        val flags = buildList {
                            if (node.optBoolean("clickable")) add("click")
                            if (node.optBoolean("scrollable")) add("scroll")
                            if (node.optBoolean("editable")) add("editable")
                            if (node.optBoolean("password")) add("password")
                            if (node.optBoolean("selected")) add("selected")
                            if (node.optBoolean("checked")) add("checked")
                            if (!node.optBoolean("enabled", true)) add("disabled")
                        }.joinToString(",")
                        append("  ".repeat(depth)).append("› ").append(className)
                        if (viewId.isNotBlank()) append(" · ").append(viewId)
                        if (flags.isNotBlank()) append("  [").append(flags).append(']')
                        val bounds = node.optJSONObject("bounds")
                        if (bounds != null) {
                            append("  @").append(bounds.optInt("left")).append(',')
                                .append(bounds.optInt("top")).append(" ")
                                .append(bounds.optInt("right") - bounds.optInt("left")).append('×')
                                .append(bounds.optInt("bottom") - bounds.optInt("top"))
                        }
                        append('\n')
                        shown++
                        if (shown >= 22) break
                    }
                }
                if (shown == 0) append("No visible nodes were exposed by this window.\n")
                else if ((nodes?.length() ?: 0) > shown) append("…more nodes available in the exported JSONL.\n")
                val source = latest.optJSONObject("sourceNode")
                if (source != null) {
                    append("Interaction source: ")
                        .append(source.optString("className", "unknown"))
                        .append(" · ").append(source.optString("viewId", "no resource ID"))
                        .append('\n')
                }
            }

            append("\nRECENT EVENTS\n")
            records.takeLast(6).asReversed().forEach { record ->
                append("• ").append(formatTime(record.optLong("timestampEpochMs")))
                    .append(" · ").append(record.optString("eventType", "event"))
                if (record.has("snapshotChanged")) {
                    append(if (record.optBoolean("snapshotChanged")) " · snapshot" else " · structure unchanged")
                }
                append('\n')
            }
        }.trimEnd()
    }

    fun network(file: File?, totalEvents: Long): String {
        val records = readRecent(file, 12)
        if (records.isEmpty()) {
            return "Network metadata events: $totalEvents. No packet headers are visible yet. " +
                "Network evidence requires PCAPdroid and received packets; UI structure capture works independently."
        }
        return buildString {
            append(totalEvents).append(" network metadata events · latest packet headers\n")
            records.takeLast(10).asReversed().forEach { record ->
                append("• ").append(formatTime(record.optLong("timestampEpochMs"))).append(" · ")
                    .append(record.optString("protocol", "unknown").uppercase(Locale.ROOT)).append(' ')
                    .append(record.optInt("sourcePort")).append(" → ")
                    .append(record.optInt("destinationPort"))
                    .append(" · ").append(record.optInt("frameBytes")).append(" frame bytes")
                    .append(" · ").append(record.optInt("payloadBytes")).append(" payload bytes")
                if (record.has("tcpFlags")) append(" · flags ").append(record.optInt("tcpFlags"))
                append('\n')
            }
            append("No IP addresses, payload contents, URLs or headers are displayed or retained.")
        }.trimEnd()
    }

    private fun readRecent(file: File?, limit: Int): List<JSONObject> {
        if (file?.isFile != true || limit <= 0) return emptyList()
        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                val length = input.length()
                val start = (length - MAX_TAIL_BYTES).coerceAtLeast(0L)
                input.seek(start)
                if (start > 0L) input.readLine() // discard a possibly partial first line
                val remaining = (length - input.filePointer).coerceAtMost(MAX_TAIL_BYTES).toInt()
                if (remaining <= 0) return@use emptyList()
                val bytes = ByteArray(remaining)
                input.readFully(bytes)
                String(bytes, Charsets.UTF_8).lineSequence()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
                    .toList()
                    .takeLast(limit)
            }
        }.getOrDefault(emptyList())
    }

    private fun formatTime(timestamp: Long): String =
        if (timestamp > 0L) SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        else "--:--:--"

    private const val MAX_TAIL_BYTES = 512L * 1024L
}
