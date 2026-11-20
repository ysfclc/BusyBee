package com.ysfclc.appobserver

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Read-only selected-app observer. Never reads node text, content descriptions,
 * view IDs or event text. Never performs actions or gestures.
 */
class ObserverAccessibilityService : AccessibilityService() {
    private val lastSampleAt = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        ObserverSignals.setServiceState(this, "accessibility", true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (!AppCatalog.isObservedPackage(pkg)) return
        val now = System.currentTimeMillis()
        if (now - (lastSampleAt[pkg] ?: 0L) < 500L) return
        lastSampleAt[pkg] = now

        val root = runCatching { rootInActiveWindow }.getOrNull()
        val shape = if (root != null && root.packageName?.toString() == pkg) inspectShape(root) else null
        ObserverSignals.recordAccessibility(this, pkg, event.eventType, shape)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        ObserverSignals.setServiceState(this, "accessibility", false)
        super.onDestroy()
    }

    private fun inspectShape(root: AccessibilityNodeInfo): WindowShape {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val visited = HashSet<AccessibilityNodeInfo>()
        queue.add(root)
        var nodes = 0
        var clickable = 0
        var editable = 0
        var scrollable = 0
        var visible = 0
        var passwords = 0
        while (queue.isNotEmpty() && nodes < 180) {
            val node = queue.removeFirst()
            if (!visited.add(node)) continue
            nodes++
            if (node.isVisibleToUser) visible++
            if (node.isClickable) clickable++
            if (node.isEditable) editable++
            if (node.isScrollable) scrollable++
            if (node.isPassword) passwords++
            for (index in 0 until node.childCount) {
                runCatching { node.getChild(index) }.getOrNull()?.let(queue::add)
            }
        }
        return WindowShape(nodes, clickable, editable, scrollable, visible, passwords)
    }
}
