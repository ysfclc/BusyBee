package com.ysfclc.appobserver

import android.content.Context
import org.json.JSONObject

data class WindowShape(
    val nodes: Int,
    val clickable: Int,
    val editable: Int,
    val scrollable: Int,
    val visible: Int,
    val passwordFields: Int
)

object ObserverSignals {
    private const val PREFS = "app_observer_signals"
    private const val STARTED = "session_started_at"
    private const val PREFIX = "signal."

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureSession(context: Context) {
        val p = prefs(context)
        if (p.getLong(STARTED, 0L) == 0L) p.edit().putLong(STARTED, System.currentTimeMillis()).apply()
    }

    fun sessionStartedAt(context: Context): Long {
        ensureSession(context)
        return prefs(context).getLong(STARTED, 0L)
    }

    fun recordNotification(context: Context, pkg: String, category: String?, actions: Int, ongoing: Boolean, group: Boolean) {
        val p = prefs(context)
        val safeCategory = category?.take(40)?.lowercase()
            ?.replace(Regex("[^a-z0-9_.-]"), "_")?.takeIf { it.isNotBlank() } ?: "uncategorized"
        val key = "$PREFIX$pkg.notification."
        synchronized(ObserverSignals::class.java) {
            p.edit()
                .putInt(key + "count", p.getInt(key + "count", 0) + 1)
                .putInt(key + "actions", p.getInt(key + "actions", 0) + actions.coerceAtLeast(0))
                .putInt(key + "ongoing", p.getInt(key + "ongoing", 0) + if (ongoing) 1 else 0)
                .putInt(key + "groups", p.getInt(key + "groups", 0) + if (group) 1 else 0)
                .putInt(key + "category." + safeCategory, p.getInt(key + "category." + safeCategory, 0) + 1)
                .putLong(key + "last", System.currentTimeMillis())
                .apply()
        }
    }

    fun recordAccessibility(context: Context, pkg: String, eventType: Int, shape: WindowShape?) {
        val p = prefs(context)
        val key = "$PREFIX$pkg.accessibility."
        val name = AppCatalog.eventName(eventType)
        synchronized(ObserverSignals::class.java) {
            val edit = p.edit()
                .putInt(key + "count", p.getInt(key + "count", 0) + 1)
                .putInt(key + "event." + name, p.getInt(key + "event." + name, 0) + 1)
                .putLong(key + "last", System.currentTimeMillis())
            if (shape != null) {
                edit.putInt(key + "shape.nodes", shape.nodes)
                    .putInt(key + "shape.clickable", shape.clickable)
                    .putInt(key + "shape.editable", shape.editable)
                    .putInt(key + "shape.scrollable", shape.scrollable)
                    .putInt(key + "shape.visible", shape.visible)
                    .putInt(key + "shape.password", shape.passwordFields)
            }
            edit.apply()
        }
    }

    fun setServiceState(context: Context, name: String, connected: Boolean) {
        prefs(context).edit()
            .putBoolean("service.$name.connected", connected)
            .putLong("service.$name.last", System.currentTimeMillis())
            .apply()
    }

    fun serviceConnected(context: Context, name: String) =
        prefs(context).getBoolean("service.$name.connected", false)

    fun appSignals(context: Context, pkg: String): JSONObject {
        val p = prefs(context)
        val n = "$PREFIX$pkg.notification."
        val a = "$PREFIX$pkg.accessibility."
        val categories = JSONObject()
        p.all.filterKeys { it.startsWith(n + "category.") }.forEach { (key, value) ->
            categories.put(key.removePrefix(n + "category."), value as? Int ?: 0)
        }
        val types = JSONObject()
        listOf("window_state_changed", "window_content_changed", "view_scrolled",
            "view_clicked", "view_focused", "view_text_changed", "other").forEach {
            types.put(it, p.getInt(a + "event." + it, 0))
        }
        val shape = JSONObject()
            .put("nodes", p.getInt(a + "shape.nodes", 0))
            .put("clickable", p.getInt(a + "shape.clickable", 0))
            .put("editable", p.getInt(a + "shape.editable", 0))
            .put("scrollable", p.getInt(a + "shape.scrollable", 0))
            .put("visible", p.getInt(a + "shape.visible", 0))
            .put("passwordFields", p.getInt(a + "shape.password", 0))
        return JSONObject()
            .put("notification", JSONObject()
                .put("count", p.getInt(n + "count", 0))
                .put("actionCountTotal", p.getInt(n + "actions", 0))
                .put("ongoingCount", p.getInt(n + "ongoing", 0))
                .put("groupConversationCount", p.getInt(n + "groups", 0))
                .put("lastObservedAt", timeOrNull(p.getLong(n + "last", 0L)))
                .put("categories", categories))
            .put("accessibility", JSONObject()
                .put("eventCount", p.getInt(a + "count", 0))
                .put("lastObservedAt", timeOrNull(p.getLong(a + "last", 0L)))
                .put("eventTypes", types)
                .put("latestWindowShape", shape))
    }

    fun reset(context: Context) {
        val p = prefs(context)
        val edit = p.edit()
        p.all.keys.filter { it.startsWith(PREFIX) || it.startsWith("service.") }.forEach(edit::remove)
        edit.putLong(STARTED, System.currentTimeMillis()).apply()
    }

    private fun timeOrNull(value: Long): Any =
        if (value > 0L) value else JSONObject.NULL
}
