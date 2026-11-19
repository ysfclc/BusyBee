package com.ysfclc.appobserver

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

/**
 * Captures a bounded, read-only UI timeline only during an explicitly started session.
 * It never clicks controls, performs gestures, or reads editable/password fields.
 */
class ObserverAccessibilityService : AccessibilityService() {
    private val lastSampleAt = mutableMapOf<String, Long>()
    private val lastFingerprint = mutableMapOf<String, Int>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        ObserverSignals.setServiceState(this, "accessibility", true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (!ObservationStore.hasActiveSession(this) || ObservationStore.activePackage(this) != pkg) return
        if (!AppCatalog.isObservedPackage(pkg)) return

        val now = System.currentTimeMillis()
        val previous = lastSampleAt[pkg] ?: 0L
        if (now - previous < SAMPLE_INTERVAL_MS) return
        lastSampleAt[pkg] = now

        val root = runCatching { rootInActiveWindow }.getOrNull()
        if (root == null || root.packageName?.toString() != pkg) {
            ObservationStore.appendUiEvent(this, JSONObject()
                .put("timestampEpochMs", now)
                .put("packageName", pkg)
                .put("eventType", AppCatalog.eventName(event.eventType))
                .put("windowAvailable", false))
            ObserverSignals.recordAccessibility(this, pkg, event.eventType, null)
            return
        }

        val nodes = JSONArray()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val rootShape = WindowShape(0, 0, 0, 0, 0, 0)
        var count = 0
        var clickable = 0
        var editable = 0
        var scrollable = 0
        var visible = 0
        var passwords = 0
        val textParts = ArrayList<String>()
        queue.add(root)

        while (queue.isNotEmpty() && count < MAX_NODES) {
            val node = queue.removeFirst()
            count++
            val isVisible = runCatching { node.isVisibleToUser }.getOrDefault(false)
            if (isVisible) visible++
            if (node.isClickable) clickable++
            if (node.isEditable) editable++
            if (node.isScrollable) scrollable++
            if (node.isPassword) passwords++

            if (isVisible && !node.isPassword && !node.isEditable) {
                val item = JSONObject()
                    .put("className", node.className?.toString()?.take(120) ?: JSONObject.NULL)
                    .put("viewId", node.viewIdResourceName?.take(180) ?: JSONObject.NULL)
                    .put("clickable", node.isClickable)
                    .put("scrollable", node.isScrollable)
                    .put("enabled", node.isEnabled)
                    .put("selected", node.isSelected)
                    .put("checked", node.isChecked)
                if (ObservationStore.includeUiText(this)) {
                    val text = ObservationStore.sanitizeVisibleText(node.text)
                    val description = ObservationStore.sanitizeVisibleText(node.contentDescription)
                    if (text.isNotBlank()) item.put("text", text)
                    if (description.isNotBlank()) item.put("contentDescription", description)
                    if (text.isNotBlank()) textParts.add(text)
                    if (description.isNotBlank()) textParts.add(description)
                }
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                item.put("bounds", JSONObject()
                    .put("left", bounds.left).put("top", bounds.top)
                    .put("right", bounds.right).put("bottom", bounds.bottom))
                nodes.put(item)
            }

            for (index in 0 until node.childCount) {
                runCatching { node.getChild(index) }.getOrNull()?.let(queue::add)
            }
        }

        val shape = WindowShape(count, clickable, editable, scrollable, visible, passwords)
        val fingerprint = (AppCatalog.eventName(event.eventType) + "|" + textParts.joinToString("|") +
            "|" + nodes.length()).hashCode()
        if (lastFingerprint[pkg] != fingerprint || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastFingerprint[pkg] = fingerprint
            ObservationStore.appendUiEvent(this, JSONObject()
                .put("timestampEpochMs", now)
                .put("packageName", pkg)
                .put("eventType", AppCatalog.eventName(event.eventType))
                .put("windowClassName", event.className?.toString()?.take(180) ?: JSONObject.NULL)
                .put("windowAvailable", true)
                .put("nodeCount", count)
                .put("nodes", nodes)
                .put("visibleTextCaptured", ObservationStore.includeUiText(this)))
        }

        ObserverSignals.recordAccessibility(this, pkg, event.eventType, shape)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        ObserverSignals.setServiceState(this, "accessibility", false)
        super.onDestroy()
    }

    companion object {
        private const val SAMPLE_INTERVAL_MS = 250L
        private const val MAX_NODES = 180
    }
}
