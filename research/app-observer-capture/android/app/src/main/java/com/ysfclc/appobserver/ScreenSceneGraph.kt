package com.ysfclc.appobserver

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class SceneNode(
    val id: String,
    val kind: String,
    val source: String,
    val region: NormalisedRegion?,
    val attributes: Map<String, String>
)

data class SceneRelation(
    val from: String,
    val to: String,
    val kind: String,
    val evidence: String,
    val strength: Float
)

data class ScreenSceneGraph(
    val nodes: List<SceneNode>,
    val relations: List<SceneRelation>
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", "app-observer-scene-graph/v1")
        .put("nodes", JSONArray().apply {
            nodes.forEach { node ->
                put(JSONObject()
                    .put("id", node.id)
                    .put("kind", node.kind)
                    .put("source", node.source)
                    .put("region", node.region?.let {
                        JSONObject().put("left", it.left.toDouble())
                            .put("top", it.top.toDouble())
                            .put("right", it.right.toDouble())
                            .put("bottom", it.bottom.toDouble())
                    } ?: JSONObject.NULL)
                    .put("attributes", JSONObject(node.attributes)))
            }
        })
        .put("relations", JSONArray().apply {
            relations.forEach { relation ->
                put(JSONObject()
                    .put("from", relation.from)
                    .put("to", relation.to)
                    .put("kind", relation.kind)
                    .put("evidence", relation.evidence)
                    .put("strength", relation.strength.toDouble()))
            }
        })
}

/**
 * Builds a privacy-safe structural scene graph for one screenshot.
 *
 * OCR strings are inspected only in memory and reduced to coarse text-shape categories.
 * No recognised text is copied into graph nodes or relations.
 */
object ScreenSceneGraphBuilder {
    private const val MAX_NODES = 220
    private const val MAX_RELATIONS = 2_400

    fun build(evidence: LearningEvidence): ScreenSceneGraph {
        val nodes = ArrayList<SceneNode>()
        val relations = ArrayList<SceneRelation>()

        evidence.accessibilityElements.take(MAX_NODES / 2).forEachIndexed { index, element ->
            val kind = when {
                element.editable -> "input_control"
                element.clickable -> "interactive_control"
                element.scrollable -> "scroll_container"
                element.className.contains("map", true) ||
                    element.resourceId.contains("map", true) -> "map_surface"
                element.className.contains("image", true) -> "image_element"
                element.className.contains("text", true) -> "text_element"
                element.className.contains("recycler", true) ||
                    element.className.contains("list", true) -> "repeating_container"
                else -> "ui_element"
            }
            nodes += SceneNode(
                id = "ui-$index",
                kind = kind,
                source = "accessibility",
                region = element.region,
                attributes = mapOf(
                    "class" to element.className.take(120),
                    "resourceId" to element.resourceId.take(160),
                    "clickable" to element.clickable.toString(),
                    "scrollable" to element.scrollable.toString(),
                    "editable" to element.editable.toString()
                )
            )
        }

        evidence.lines.take(MAX_NODES / 2).forEachIndexed { index, line ->
            val region = lineRegion(line, evidence.width, evidence.height)
            nodes += SceneNode(
                id = "text-$index",
                kind = "text_region",
                source = "ocr_ephemeral",
                region = region,
                attributes = mapOf(
                    "shape" to textShape(line.text),
                    "hasGeometry" to (region != null).toString()
                )
            )
        }

        evidence.visualLabels.take(8).forEachIndexed { index, label ->
            nodes += SceneNode(
                id = "vision-$index",
                kind = "screen_visual_hint",
                source = "on_device_image_labelling",
                region = null,
                attributes = mapOf(
                    "label" to label.label.take(48),
                    "modelScore" to label.confidence.coerceIn(0f, 1f).toString()
                )
            )
        }

        if (evidence.interactionType.isNotBlank()) {
            nodes += SceneNode(
                id = "interaction-0",
                kind = "interaction_observation",
                source = "accessibility_event",
                region = null,
                attributes = mapOf("event" to evidence.interactionType.take(120))
            )
        }
        if (evidence.notificationCount > 0) {
            nodes += SceneNode(
                id = "notification-0",
                kind = "notification_signal",
                source = "notification_listener",
                region = null,
                attributes = mapOf(
                    "count" to evidence.notificationCount.toString(),
                    "categories" to evidence.notificationCategories.distinct().take(8).joinToString(",")
                )
            )
        }
        if (evidence.networkPacketCount > 0) {
            nodes += SceneNode(
                id = "network-0",
                kind = "network_activity_signal",
                source = "packet_header_metadata",
                region = null,
                attributes = mapOf(
                    "packetCount" to evidence.networkPacketCount.toString(),
                    "byteCount" to evidence.networkBytes.coerceAtLeast(0L).toString(),
                    "protocols" to evidence.networkProtocols.distinct().take(8).joinToString(","),
                    "portCount" to evidence.networkPorts.distinct().size.toString()
                )
            )
        }

        val geometric = nodes.filter { it.region != null && it.kind != "screen_visual_hint" }
        for (i in geometric.indices) {
            for (j in i + 1 until geometric.size) {
                if (relations.size >= MAX_RELATIONS) break
                val a = geometric[i]
                val b = geometric[j]
                val ar = a.region ?: continue
                val br = b.region ?: continue
                val relation = geometricRelation(a, ar, b, br) ?: continue
                relations += relation
            }
            if (relations.size >= MAX_RELATIONS) break
        }

        val interaction = nodes.firstOrNull { it.kind == "interaction_observation" }
        val network = nodes.firstOrNull { it.kind == "network_activity_signal" }
        val notification = nodes.firstOrNull { it.kind == "notification_signal" }
        val lastUi = nodes.lastOrNull { it.source == "accessibility" }
        if (interaction != null && lastUi != null) {
            relations += SceneRelation(interaction.id, lastUi.id, "temporally_associated_with",
                "same_screenshot_observation", 0.5f)
        }
        if (interaction != null && network != null) {
            relations += SceneRelation(interaction.id, network.id, "temporally_near",
                "bounded_observation_window", 0.25f)
        }
        if (interaction != null && notification != null) {
            relations += SceneRelation(interaction.id, notification.id, "temporally_near",
                "bounded_observation_window", 0.25f)
        }

        return ScreenSceneGraph(nodes.take(MAX_NODES), relations.take(MAX_RELATIONS))
    }

    private fun geometricRelation(
        a: SceneNode,
        ar: NormalisedRegion,
        b: SceneNode,
        br: NormalisedRegion
    ): SceneRelation? {
        val intersectionWidth = max(0f, min(ar.right, br.right) - max(ar.left, br.left))
        val intersectionHeight = max(0f, min(ar.bottom, br.bottom) - max(ar.top, br.top))
        val intersection = intersectionWidth * intersectionHeight
        val areaA = max(0f, ar.right - ar.left) * max(0f, ar.bottom - ar.top)
        val areaB = max(0f, br.right - br.left) * max(0f, br.bottom - br.top)
        val minArea = min(areaA, areaB).coerceAtLeast(0.0001f)
        val overlap = intersection / minArea

        if (contains(ar, br) && areaA > areaB * 1.08f) {
            return SceneRelation(a.id, b.id,
                if (a.source == "accessibility" && b.source == "ocr_ephemeral") "contains_text_region" else "contains",
                "normalised_geometry", overlap.coerceIn(0f, 1f))
        }
        if (contains(br, ar) && areaB > areaA * 1.08f) {
            return SceneRelation(b.id, a.id,
                if (b.source == "accessibility" && a.source == "ocr_ephemeral") "contains_text_region" else "contains",
                "normalised_geometry", overlap.coerceIn(0f, 1f))
        }
        if (overlap >= 0.22f) {
            return SceneRelation(a.id, b.id, "overlaps", "normalised_geometry", overlap.coerceIn(0f, 1f))
        }

        val centreAx = (ar.left + ar.right) / 2f
        val centreAy = (ar.top + ar.bottom) / 2f
        val centreBx = (br.left + br.right) / 2f
        val centreBy = (br.top + br.bottom) / 2f
        if (kotlin.math.abs(centreAy - centreBy) <= 0.014f) {
            return SceneRelation(a.id, b.id, "aligned_horizontally", "normalised_geometry", 0.65f)
        }
        if (kotlin.math.abs(centreAx - centreBx) <= 0.014f) {
            return SceneRelation(a.id, b.id, "aligned_vertically", "normalised_geometry", 0.65f)
        }
        val dx = max(0f, max(ar.left - br.right, br.left - ar.right))
        val dy = max(0f, max(ar.top - br.bottom, br.top - ar.bottom))
        if (sqrt(dx * dx + dy * dy) <= 0.025f) {
            return SceneRelation(a.id, b.id, "adjacent", "normalised_geometry", 0.4f)
        }
        return null
    }

    private fun contains(outer: NormalisedRegion, inner: NormalisedRegion): Boolean =
        inner.left >= outer.left && inner.top >= outer.top &&
            inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun lineRegion(
        line: OcrLineEvidence,
        width: Int,
        height: Int
    ): NormalisedRegion? {
        if (width <= 0 || height <= 0) return null
        val left = line.left ?: return null
        val top = line.top ?: return null
        val right = line.right ?: return null
        val bottom = line.bottom ?: return null
        val region = NormalisedRegion(
            (left / width.toFloat()).coerceIn(0f, 1f),
            (top / height.toFloat()).coerceIn(0f, 1f),
            (right / width.toFloat()).coerceIn(0f, 1f),
            (bottom / height.toFloat()).coerceIn(0f, 1f)
        )
        return region.takeIf { it.left < it.right && it.top < it.bottom }
    }

    private fun textShape(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return "empty"
        if (Regex("""(?i).*(zł|pln|eur|€|usd|\$|gbp|£).*""").matches(value)) return "currency"
        if (Regex("""\d{1,2}[:.]\d{2}""").containsMatchIn(value)) return "time"
        if (Regex("""\d{4}[-/.]\d{1,2}[-/.]\d{1,2}""").containsMatchIn(value)) return "date"
        if (Regex("""\d+([,.]\d+)?""").matches(value)) return "numeric"
        return when {
            value.length <= 24 -> "short_text"
            value.length <= 80 -> "medium_text"
            else -> "long_text"
        }
    }
}
