package com.ysfclc.appobserver

import android.content.Context
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** User-authored meaning rules stored locally and preserved with app data across signed updates. */
data class SemanticRule(
    val id: String,
    val packageName: String,
    val label: String,
    val payloadSource: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val screenshotId: String,
    val createdAtEpochMs: Long
) {
    val capturesAccessibilityText: Boolean get() = payloadSource == SOURCE_ACCESSIBILITY_TEXT
    val capturesNotificationText: Boolean get() = payloadSource == SOURCE_NOTIFICATION_TEXT

    fun containsScreenPoint(x: Float, y: Float, screenWidth: Int, screenHeight: Int): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0) return false
        val nx = x / screenWidth.toFloat()
        val ny = y / screenHeight.toFloat()
        return nx >= left && nx <= right && ny >= top && ny <= bottom
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("packageName", packageName).put("label", label)
        .put("payloadSource", payloadSource)
        .put("left", left.toDouble()).put("top", top.toDouble())
        .put("right", right.toDouble()).put("bottom", bottom.toDouble())
        .put("screenshotId", screenshotId).put("createdAtEpochMs", createdAtEpochMs)

    companion object {
        const val SOURCE_VISUAL_ONLY = "visual_only"
        const val SOURCE_ACCESSIBILITY_TEXT = "accessibility_text"
        const val SOURCE_NOTIFICATION_TEXT = "notification_text"

        fun fromJson(value: JSONObject): SemanticRule? = runCatching {
            SemanticRule(
                id = value.getString("id"),
                packageName = value.getString("packageName"),
                label = value.getString("label"),
                payloadSource = value.getString("payloadSource"),
                left = value.getDouble("left").toFloat().coerceIn(0f, 1f),
                top = value.getDouble("top").toFloat().coerceIn(0f, 1f),
                right = value.getDouble("right").toFloat().coerceIn(0f, 1f),
                bottom = value.getDouble("bottom").toFloat().coerceIn(0f, 1f),
                screenshotId = value.optString("screenshotId"),
                createdAtEpochMs = value.optLong("createdAtEpochMs")
            ).takeIf {
                it.packageName.isNotBlank() && it.label.isNotBlank() &&
                    it.left < it.right && it.top < it.bottom &&
                    it.payloadSource in setOf(SOURCE_VISUAL_ONLY, SOURCE_ACCESSIBILITY_TEXT, SOURCE_NOTIFICATION_TEXT)
            } ?: throw IllegalArgumentException("Invalid semantic rule")
        }.getOrNull()
    }
}

object SemanticRuleStore {
    const val SYSTEM_SURFACE_PACKAGE = "__android_system_surface__"
    private const val PREFS = "app_observer_semantic_rules"
    private const val RULES_KEY = "rules_json"
    private const val MAX_RULES = 500
    private const val PERMANENT_SELF_TEST_PACKAGE = "com.ysfclc.appobserver.capture"
    private val LEGACY_SELF_TEST_PACKAGES = setOf(
        "com.ysfclc.appobserver.capture.generic.debug",
        "com.ysfclc.appobserver.capture.generic.viewer.debug",
        "com.ysfclc.appobserver.capture.generic.viewer.diagnostics.debug"
    )

    @Synchronized
    fun all(context: Context): List<SemanticRule> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(RULES_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    SemanticRule.fromJson(array.optJSONObject(index) ?: continue)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(
        context: Context,
        packageName: String,
        label: String,
        payloadSource: String,
        normalizedRegion: Rect,
        screenshotWidth: Int,
        screenshotHeight: Int,
        screenshotId: String
    ): SemanticRule {
        require(packageName.isNotBlank()) { "A target app is required." }
        val safeLabel = label.trim().take(64)
        require(safeLabel.isNotBlank()) { "Give this region a meaning, such as chat or notification bar." }
        require(payloadSource in setOf(SemanticRule.SOURCE_VISUAL_ONLY,
            SemanticRule.SOURCE_ACCESSIBILITY_TEXT, SemanticRule.SOURCE_NOTIFICATION_TEXT))
        require(screenshotWidth > 0 && screenshotHeight > 0)
        val left = normalizedRegion.left.toFloat() / screenshotWidth
        val top = normalizedRegion.top.toFloat() / screenshotHeight
        val right = normalizedRegion.right.toFloat() / screenshotWidth
        val bottom = normalizedRegion.bottom.toFloat() / screenshotHeight
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f && left < right && top < bottom) {
            "Select a non-empty region inside the screenshot."
        }
        val rule = SemanticRule(
            id = UUID.randomUUID().toString(), packageName = packageName, label = safeLabel,
            payloadSource = payloadSource, left = left, top = top, right = right, bottom = bottom,
            screenshotId = screenshotId, createdAtEpochMs = System.currentTimeMillis()
        )
        val updated = (all(context).filterNot {
            it.packageName == rule.packageName && it.label.equals(rule.label, ignoreCase = true) &&
                it.payloadSource == rule.payloadSource &&
                kotlin.math.abs(it.left - rule.left) < 0.02f &&
                kotlin.math.abs(it.top - rule.top) < 0.02f
        } + rule).takeLast(MAX_RULES)
        persist(context, updated)
        return rule
    }

    fun matchingAccessibilityRules(
        context: Context, packageName: String, bounds: Rect, screenWidth: Int, screenHeight: Int
    ): List<SemanticRule> {
        if (bounds.isEmpty) return emptyList()
        val x = (bounds.left + bounds.right) / 2f
        val y = (bounds.top + bounds.bottom) / 2f
        return all(context).filter {
            it.packageName == packageName && it.capturesAccessibilityText &&
                it.containsScreenPoint(x, y, screenWidth, screenHeight)
        }
    }

    fun notificationRules(context: Context, packageName: String): List<SemanticRule> =
        all(context).filter { it.packageName == packageName && it.capturesNotificationText }

    fun hasSystemSurfaceRules(context: Context): Boolean =
        all(context).any { it.packageName == SYSTEM_SURFACE_PACKAGE }

    fun exportJson(context: Context): JSONObject = JSONObject()
        .put("schema", "app-observer-semantic-rules/v1")
        .put("rules", JSONArray().apply { all(context).forEach { put(it.toJson()) } })

    @Synchronized
    fun importJson(context: Context, value: JSONObject): Int {
        val incoming = value.optJSONArray("rules") ?: return 0
        val merged = (all(context) + buildList {
            for (index in 0 until incoming.length()) {
                val imported = SemanticRule.fromJson(incoming.optJSONObject(index) ?: continue) ?: continue
                // Rules authored against one of the earlier App Observer self-test APKs
                // must follow the permanent release package after the one-time migration.
                val migratedPackage = if (imported.packageName in LEGACY_SELF_TEST_PACKAGES) {
                    PERMANENT_SELF_TEST_PACKAGE
                } else imported.packageName
                add(imported.copy(packageName = migratedPackage))
            }
        }).distinctBy { it.id }.takeLast(MAX_RULES)
        persist(context, merged)
        return merged.size
    }

    fun describe(context: Context): String {
        val rules = all(context)
        if (rules.isEmpty()) return "No meaning rules yet. Capture a screen and define its meaning."
        return rules.takeLast(30).asReversed().joinToString("\n") {
            val source = when (it.payloadSource) {
                SemanticRule.SOURCE_ACCESSIBILITY_TEXT -> "UI text allowed"
                SemanticRule.SOURCE_NOTIFICATION_TEXT -> "notification text allowed"
                else -> "visual only"
            }
            "• ${it.label} · ${it.packageName.substringAfterLast('.')} · $source"
        }
    }

    private fun persist(context: Context, rules: List<SemanticRule>) {
        val array = JSONArray()
        rules.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(RULES_KEY, array.toString()).apply()
    }
}
