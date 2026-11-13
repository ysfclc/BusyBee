package com.ysfclc.busybee.drivercommandcentre

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import java.security.MessageDigest
import java.util.ArrayDeque

/**
 * Read-only observer for explicitly selected ride-driver packages.
 *
 * It reads visible accessibility text only. It does not click, gesture, type,
 * accept/decline trips, record audio, capture pixels, or inspect other apps.
 * Android's Accessibility Settings screen is the user's explicit enable/disable
 * control. The in-app disclosure is shown before opening those settings.
 */
class DriverAppAccessibilityService : AccessibilityService() {
    private val lastReadAtByPackage = mutableMapOf<String,Long>()
    private val lastSnapshotFingerprintByPackage = mutableMapOf<String,String>()
    private val lastSnapshotAtByPackage = mutableMapOf<String,Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType !in OBSERVED_EVENT_TYPES) return

        val packageName = event.packageName?.toString() ?: return
        val platform = RidePlatformPackages.platformFor(packageName) ?: return

        val now = System.currentTimeMillis()
        if (now - (lastReadAtByPackage[packageName] ?: 0L) < MIN_EVENT_INTERVAL_MS) return
        lastReadAtByPackage[packageName] = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != packageName) return
        val visibleText = collectVisibleText(root)
        if (visibleText.length < MIN_TEXT_LENGTH) return

        val parsed = DriverScreenTextParser.parse(
            platform = platform,
            text = visibleText,
            capturedAt = now,
            sourceType = "ACCESSIBILITY_UI"
        ) ?: return
        val fingerprint = sha256(platform + "\u0000" + parsed.screenType + "\u0000" + parsed.visibleText)
        if (fingerprint == lastSnapshotFingerprintByPackage[packageName] &&
            now - (lastSnapshotAtByPackage[packageName] ?: 0L) < DUPLICATE_WINDOW_MS
        ) return
        lastSnapshotFingerprintByPackage[packageName] = fingerprint
        lastSnapshotAtByPackage[packageName] = now

        val rowId = runCatching {
            DriverAppCaptureStore(applicationContext).save(parsed)
        }.getOrElse {
            Log.w(TAG, "Could not persist a driver-app observation", it)
            -1L
        }
        if (rowId > 0L) {
            DriverAlertManager.notifyDriverAppUpdate(
                applicationContext,
                platform,
                parsed.screenType,
                "Android live UI listener"
            )
        }
    }

    override fun onInterrupt() {
        // There are no gestures or pending actions to cancel.
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo): String {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val visited = HashSet<AccessibilityNodeInfo>()
        val lines = LinkedHashSet<String>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < MAX_NODES && lines.sumOf { it.length } < MAX_CAPTURE_CHARS) {
            val node = queue.removeFirst()
            if (!visited.add(node)) continue
            count++

            if (node.isVisibleToUser && !node.isPassword) {
                // Avoid reading user-entered search/address text from editable fields.
                val className = node.className?.toString().orEmpty()
                if (!className.endsWith("EditText", ignoreCase = true)) {
                    node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(lines::add)
                    node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(lines::add)
                }
            }

            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                queue.add(child)
            }
        }
        return lines.joinToString("\n").take(MAX_CAPTURE_CHARS)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "BusyBeeAppListener"
        private const val MIN_EVENT_INTERVAL_MS = 700L
        private const val DUPLICATE_WINDOW_MS = 8_000L
        private const val MIN_TEXT_LENGTH = 12
        private const val MAX_NODES = 260
        private const val MAX_CAPTURE_CHARS = 4_000
        private val OBSERVED_EVENT_TYPES = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED
        )
    }
}
