package com.ysfclc.appobserver

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

/**
 * Records structure-only evidence for the one package selected in the active user-started session.
 * No node text, content descriptions, editable values, screenshots or gestures are collected.
 */
class ObserverAccessibilityService : AccessibilityService() {
    private val lastContentSampleAt = mutableMapOf<String, Long>()
    private val lastFingerprint = mutableMapOf<String, Int>()

    private data class PendingNode(
        val node: AccessibilityNodeInfo,
        val parentIndex: Int,
        val depth: Int,
        val childIndex: Int
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        ObserverSignals.setServiceState(this, "accessibility", true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (!ObservationStore.hasActiveSession(this)) return
        if (!AppCatalog.isSessionTarget(packageName, ObservationStore.activePackage(this))) return

        val now = System.currentTimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val previous = lastContentSampleAt[packageName] ?: 0L
            if (now - previous < CONTENT_SAMPLE_INTERVAL_MS) return
            lastContentSampleAt[packageName] = now
        }

        val eventName = AppCatalog.eventName(event.eventType)
        val sourceEvidence = sourceEvidence(event.source)
        val root = runCatching { rootInActiveWindow }.getOrNull()
        if (root == null || root.packageName?.toString() != packageName) {
            val record = baseEvent(event, packageName, eventName, now)
                .put("windowAvailable", false)
            sourceEvidence?.let { record.put("sourceNode", it) }
            ObservationStore.appendUiEvent(this, record)
            ObserverSignals.recordAccessibility(this, packageName, event.eventType, null)
            return
        }

        val nodes = JSONArray()
        val queue = ArrayDeque<PendingNode>()
        queue.add(PendingNode(root, -1, 0, 0))
        var count = 0
        var clickable = 0
        var editable = 0
        var scrollable = 0
        var visible = 0
        var passwordFields = 0

        while (queue.isNotEmpty() && count < MAX_NODES) {
            val pending = queue.removeFirst()
            val node = pending.node
            val nodeIndex = count++
            val isVisible = runCatching { node.isVisibleToUser }.getOrDefault(false)
            if (isVisible) visible++
            if (node.isClickable) clickable++
            if (node.isEditable) editable++
            if (node.isScrollable) scrollable++
            if (node.isPassword) passwordFields++

            val bounds = Rect()
            runCatching { node.getBoundsInScreen(bounds) }
            nodes.put(JSONObject()
                .put("index", nodeIndex)
                .put("parentIndex", pending.parentIndex)
                .put("depth", pending.depth)
                .put("childIndex", pending.childIndex)
                .put("visible", isVisible)
                .put("className", node.className?.toString()?.take(120) ?: JSONObject.NULL)
                .put("viewId", node.viewIdResourceName?.take(180) ?: JSONObject.NULL)
                .put("clickable", node.isClickable)
                .put("scrollable", node.isScrollable)
                .put("editable", node.isEditable)
                .put("password", node.isPassword)
                .put("enabled", node.isEnabled)
                .put("selected", node.isSelected)
                .put("checked", node.isChecked)
                .put("childCount", node.childCount)
                .put("bounds", JSONObject()
                    .put("left", bounds.left)
                    .put("top", bounds.top)
                    .put("right", bounds.right)
                    .put("bottom", bounds.bottom)))

            val children = node.childCount
            for (index in 0 until children) {
                runCatching { node.getChild(index) }.getOrNull()?.let {
                    queue.add(PendingNode(it, nodeIndex, pending.depth + 1, index))
                }
            }
            runCatching { node.recycle() }
        }

        val shape = WindowShape(count, clickable, editable, scrollable, visible, passwordFields)
        val fingerprint = ((event.className?.toString() ?: "") + "|" + nodes.toString()).hashCode()
        val shouldWriteSnapshot = lastFingerprint[packageName] != fingerprint ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED
        if (shouldWriteSnapshot) {
            lastFingerprint[packageName] = fingerprint
            val record = baseEvent(event, packageName, eventName, now)
                .put("windowAvailable", true)
                .put("snapshotChanged", true)
                .put("nodeCount", count)
                .put("visibleNodeCount", visible)
                .put("nodes", nodes)
            sourceEvidence?.let { record.put("sourceNode", it) }
            ObservationStore.appendUiEvent(this, record)
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            // Keep the event and timing evidence even when the structure fingerprint is unchanged.
            ObservationStore.appendUiEvent(this, baseEvent(event, packageName, eventName, now)
                .put("windowAvailable", true)
                .put("snapshotChanged", false)
                .put("nodeCount", count)
                .put("visibleNodeCount", visible))
        }

        ObserverSignals.recordAccessibility(this, packageName, event.eventType, shape)
    }

    private fun baseEvent(
        event: AccessibilityEvent,
        packageName: String,
        eventName: String,
        wallTime: Long
    ): JSONObject = JSONObject()
        .put("timestampEpochMs", wallTime)
        .put("elapsedRealtimeNanos", SystemClock.elapsedRealtimeNanos())
        .put("eventTimeUptimeMs", event.eventTime)
        .put("packageName", packageName)
        .put("eventType", eventName)
        .put("windowId", event.windowId)
        .put("windowClassName", event.className?.toString()?.take(180) ?: JSONObject.NULL)
        .put("contentChangeTypes", event.contentChangeTypes)
        .put("scrollX", event.scrollX)
        .put("scrollY", event.scrollY)
        .put("maxScrollX", event.maxScrollX)
        .put("maxScrollY", event.maxScrollY)
        .put("itemCount", event.itemCount)
        .put("fromIndex", event.fromIndex)
        .put("toIndex", event.toIndex)

    /** Deliberately reads structural properties only; never accesses node text or descriptions. */
    private fun sourceEvidence(node: AccessibilityNodeInfo?): JSONObject? {
        node ?: return null
        return runCatching {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            JSONObject()
                .put("className", node.className?.toString()?.take(120) ?: JSONObject.NULL)
                .put("viewId", node.viewIdResourceName?.take(180) ?: JSONObject.NULL)
                .put("clickable", node.isClickable)
                .put("enabled", node.isEnabled)
                .put("selected", node.isSelected)
                .put("checked", node.isChecked)
                .put("bounds", JSONObject()
                    .put("left", bounds.left).put("top", bounds.top)
                    .put("right", bounds.right).put("bottom", bounds.bottom))
        }.getOrNull().also { runCatching { node.recycle() } }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        ObserverSignals.setServiceState(this, "accessibility", false)
        super.onDestroy()
    }

    companion object {
        private const val CONTENT_SAMPLE_INTERVAL_MS = 250L
        private const val MAX_NODES = 250
    }
}
