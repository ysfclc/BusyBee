package com.ysfclc.appobserver

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

/**
 * Read-only, privacy-minimised UI recorder.
 *
 * Every stage is counted without retaining identifiers for apps other than the selected target.
 * Node text, descriptions, typed values, screenshots and gestures are never read or stored.
 */
class ObserverAccessibilityService : AccessibilityService() {
    private val lastContentSampleAt = mutableMapOf<String, Long>()
    private val lastFingerprint = mutableMapOf<String, String>()
    private var observedSessionId: String? = null
    private var lastScreenshotRequestedAt = 0L

    private data class PendingNode(
        val node: AccessibilityNodeInfo,
        val parentIndex: Int,
        val depth: Int,
        val childIndex: Int
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        ObserverSignals.setServiceState(this, "accessibility", true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!ObservationStore.hasActiveSession(this)) return

        ObserverSignals.recordCaptureDiagnostic(this, "events_received")
        val packageName = runCatching { event.packageName?.toString() }
            .getOrNull()?.takeIf { it.isNotBlank() }
        if (packageName == null) {
            ObserverSignals.recordCaptureDiagnostic(this, "events_without_package")
            return
        }

        val sessionId = ObservationStore.activeSessionId(this)
        if (!AppCatalog.isSessionTarget(packageName, ObservationStore.activePackage(this))) {
            ObserverSignals.recordCaptureDiagnostic(this, "events_target_mismatch")
            if (sessionId != null && SemanticRuleStore.hasSystemSurfaceRules(this) &&
                isSystemPackage(packageName) && shouldCaptureScreenshot(event.eventType)) {
                // Only system/updated-system packages are eligible here. Other installed apps
                // remain outside the selected session and are never screenshotted.
                requestScreenshot(
                    sessionId, SemanticRuleStore.SYSTEM_SURFACE_PACKAGE,
                    event.eventType, System.currentTimeMillis()
                )
            }
            return
        }
        ObserverSignals.recordCaptureDiagnostic(this, "events_target_match")

        if (sessionId != observedSessionId) {
            // A new session must always receive its own initial full snapshot, even when
            // the selected app's structure matches the preceding session.
            lastContentSampleAt.clear()
            lastFingerprint.clear()
            observedSessionId = sessionId
        }

        val eventType = runCatching { event.eventType }.getOrDefault(0)
        val now = System.currentTimeMillis()
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val previous = lastContentSampleAt[packageName] ?: 0L
            if (now - previous < CONTENT_SAMPLE_INTERVAL_MS) {
                ObserverSignals.recordCaptureDiagnostic(this, "content_events_throttled")
                return
            }
            lastContentSampleAt[packageName] = now
        }

        try {
            captureSelectedEvent(event, packageName, eventType, now, sessionId.orEmpty())
            if (shouldCaptureScreenshot(eventType)) requestScreenshot(sessionId.orEmpty(), packageName, eventType, now)
        } catch (failure: Throwable) {
            ObserverSignals.recordCaptureDiagnostic(this, "pipeline_failures")
            persistUiEvent(this, JSONObject()
                .put("timestampEpochMs", now)
                .put("elapsedRealtimeNanos", SystemClock.elapsedRealtimeNanos())
                .put("packageName", packageName)
                .put("eventType", AppCatalog.eventName(eventType))
                .put("captureStage", "event_processing")
                .put("captureErrorClass", failure.javaClass.simpleName.take(100))
                .put("captureFailed", true))
        }
    }

    private fun captureSelectedEvent(
        event: AccessibilityEvent,
        packageName: String,
        eventType: Int,
        now: Long,
        sessionId: String
    ) {
        val eventName = AppCatalog.eventName(eventType)
        val source = runCatching { event.source }.getOrNull()
        val sourceRecord = sourceEvidence(source)
        val root = runCatching { rootInActiveWindow }.getOrNull()

        if (root == null) {
            ObserverSignals.recordCaptureDiagnostic(this, "root_unavailable")
            val record = baseEvent(event, packageName, eventName, now)
                .put("windowAvailable", false)
                .put("rootFailure", "root_unavailable")
            sourceRecord?.let { record.put("sourceNode", it) }
            persistUiEvent(this, record)
            ObserverSignals.recordAccessibility(this, packageName, eventType, null)
            return
        }

        val rootPackage = runCatching { root.packageName?.toString() }.getOrNull()
        if (rootPackage == null) {
            ObserverSignals.recordCaptureDiagnostic(this, "root_package_read_failures")
        }
        if (rootPackage != packageName) {
            ObserverSignals.recordCaptureDiagnostic(this, "root_package_mismatch")
            runCatching { root.recycle() }
            val record = baseEvent(event, packageName, eventName, now)
                .put("windowAvailable", false)
                .put("rootFailure", "root_package_mismatch")
            sourceRecord?.let { record.put("sourceNode", it) }
            persistUiEvent(this, record)
            ObserverSignals.recordAccessibility(this, packageName, eventType, null)
            return
        }
        ObserverSignals.recordCaptureDiagnostic(this, "root_acquired")

        val nodes = JSONArray()
        val queue = ArrayDeque<PendingNode>()
        queue.add(PendingNode(root, -1, 0, 0))
        var count = 0
        var clickable = 0
        var editable = 0
        var scrollable = 0
        var visible = 0
        var passwordFields = 0
        var treeTruncated = false

        try {
            while (queue.isNotEmpty() && count < MAX_NODES) {
                val pending = queue.removeFirst()
                val node = pending.node
                try {
                    val nodeIndex = count++
                    val isVisible = readNode(this, false) { node.isVisibleToUser }
                    val isClickable = readNode(this, false) { node.isClickable }
                    val isEditable = readNode(this, false) { node.isEditable }
                    val isScrollable = readNode(this, false) { node.isScrollable }
                    val isPassword = readNode(this, false) { node.isPassword }
                    val isEnabled = readNode(this, false) { node.isEnabled }
                    val isSelected = readNode(this, false) { node.isSelected }
                    val isChecked = readNode(this, false) { node.isChecked }
                    val className = readNode(this, null as String?) {
                        node.className?.toString()?.take(120)
                    }
                    val viewId = readNode(this, null as String?) {
                        node.viewIdResourceName?.take(180)
                    }
                    val childCount = readNode(this, 0) { node.childCount }.coerceIn(0, 100)
                    val bounds = Rect()
                    readNode(this, false) {
                        node.getBoundsInScreen(bounds)
                        true
                    }

                    // Content is read only when this exact screen region has a user-authored
                    // rule that explicitly permits Accessibility text capture.
                    if (!isPassword && !isEditable && !bounds.isEmpty) {
                        val realMetrics = DisplayMetrics()
                        @Suppress("DEPRECATION")
                        (getSystemService(WINDOW_SERVICE) as WindowManager)
                            .defaultDisplay.getRealMetrics(realMetrics)
                        val allowedRules = SemanticRuleStore.matchingAccessibilityRules(
                            this, packageName, bounds,
                            realMetrics.widthPixels,
                            realMetrics.heightPixels
                        )
                        if (allowedRules.isNotEmpty()) {
                            val nodeText = readNode(this, null as String?) {
                                node.text?.toString()?.take(MAX_SEMANTIC_TEXT_CHARS)
                            }?.trim().orEmpty()
                            if (nodeText.isNotEmpty()) {
                                val screenshotId = ObservationStore.latestScreenshotId(this, sessionId)
                                allowedRules.forEach { rule ->
                                    ObservationStore.appendSemanticPayload(this, sessionId, JSONObject()
                                        .put("timestampEpochMs", now)
                                        .put("packageName", packageName)
                                        .put("semanticRuleId", rule.id)
                                        .put("semanticLabel", rule.label)
                                        .put("source", "accessibility_node_text")
                                        .put("screenshotId", screenshotId ?: JSONObject.NULL)
                                        .put("nodeClassName", className ?: JSONObject.NULL)
                                        .put("nodeViewId", viewId ?: JSONObject.NULL)
                                        .put("payloadText", nodeText))
                                }
                            }
                        }
                    }

                    if (isVisible) visible++
                    if (isClickable) clickable++
                    if (isEditable) editable++
                    if (isScrollable) scrollable++
                    if (isPassword) passwordFields++

                    nodes.put(JSONObject()
                        .put("index", nodeIndex)
                        .put("parentIndex", pending.parentIndex)
                        .put("depth", pending.depth)
                        .put("childIndex", pending.childIndex)
                        .put("visible", isVisible)
                        .put("className", className ?: JSONObject.NULL)
                        .put("viewId", viewId ?: JSONObject.NULL)
                        .put("clickable", isClickable)
                        .put("scrollable", isScrollable)
                        .put("editable", isEditable)
                        .put("password", isPassword)
                        .put("enabled", isEnabled)
                        .put("selected", isSelected)
                        .put("checked", isChecked)
                        .put("childCount", childCount)
                        .put("bounds", JSONObject()
                            .put("left", bounds.left)
                            .put("top", bounds.top)
                            .put("right", bounds.right)
                            .put("bottom", bounds.bottom)))

                    for (index in 0 until childCount) {
                        val child = runCatching { node.getChild(index) }.getOrNull()
                        if (child != null) {
                            queue.add(PendingNode(child, nodeIndex, pending.depth + 1, index))
                        } else {
                            ObserverSignals.recordCaptureDiagnostic(this, "child_access_failures")
                        }
                    }
                } finally {
                    runCatching { node.recycle() }
                }
            }
            treeTruncated = queue.isNotEmpty()
        } finally {
            // Nodes already queued when the traversal cap is reached must also be released.
            while (queue.isNotEmpty()) {
                runCatching { queue.removeFirst().node.recycle() }
            }
        }

        if (count == 0) ObserverSignals.recordCaptureDiagnostic(this, "empty_hierarchies")
        val shape = WindowShape(count, clickable, editable, scrollable, visible, passwordFields)
        val fingerprint = UiStructureMatcher.fingerprint(nodes)
        val shouldWriteSnapshot = lastFingerprint[packageName] != fingerprint ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SELECTED

        if (shouldWriteSnapshot) {
            val record = baseEvent(event, packageName, eventName, now)
                .put("windowAvailable", true)
                .put("snapshotChanged", true)
                .put("structureFingerprint", fingerprint)
                .put("fingerprintVersion", UiStructureMatcher.VERSION)
                .put("nodeCount", count)
                .put("visibleNodeCount", visible)
                .put("treeTruncated", treeTruncated)
                .put("nodes", nodes)
            sourceRecord?.let { record.put("sourceNode", it) }
            ObserverSignals.recordCaptureDiagnostic(this, "hierarchy_snapshots")
            if (persistUiEvent(this, record)) lastFingerprint[packageName] = fingerprint
            else lastFingerprint.remove(packageName)
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            ObserverSignals.recordCaptureDiagnostic(this, "unchanged_structure_events")
            persistUiEvent(this, baseEvent(event, packageName, eventName, now)
                .put("windowAvailable", true)
                .put("snapshotChanged", false)
                .put("structureFingerprint", fingerprint)
                .put("fingerprintVersion", UiStructureMatcher.VERSION)
                .put("nodeCount", count)
                .put("visibleNodeCount", visible))
        }

        ObserverSignals.recordAccessibility(this, packageName, eventType, shape)
    }

    private fun isSystemPackage(packageName: String): Boolean {
        // AOSP and common Android OEMs use this package for the status bar and notification
        // shade. Check it directly before querying package metadata, which may be hidden by
        // Android package-visibility filtering on some builds.
        if (packageName == "com.android.systemui" ||
            packageName.startsWith("com.android.systemui:")) return true
        return runCatching {
        val flags = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                .flags
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0).flags
        }
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.getOrDefault(false)
    }

    private fun shouldCaptureScreenshot(eventType: Int): Boolean =
        eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SELECTED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED

    private fun requestScreenshot(
        sessionId: String,
        packageName: String,
        eventType: Int,
        timestampEpochMs: Long,
        attempt: Int = 0
    ) {
        if (ObservationStore.activeSessionId(this) != sessionId) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ObserverSignals.recordCaptureDiagnostic(this, "screenshots_failed")
            ObservationStore.appendUiEvent(this, JSONObject()
                .put("timestampEpochMs", timestampEpochMs)
                .put("packageName", packageName)
                .put("eventType", "screenshot_failed")
                .put("screenshotFailure", "api_level_below_30"))
            return
        }
        if (SystemClock.elapsedRealtime() - lastScreenshotRequestedAt < SCREENSHOT_INTERVAL_MS) {
            ObserverSignals.recordCaptureDiagnostic(this, "screenshots_rate_limited")
            return
        }
        lastScreenshotRequestedAt = SystemClock.elapsedRealtime()
        ObserverSignals.recordCaptureDiagnostic(this, "screenshots_attempted")
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val hardwareBuffer = result.hardwareBuffer
                        val bitmap = runCatching {
                            Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        }.getOrNull()
                        runCatching { hardwareBuffer.close() }
                        if (bitmap == null) {
                            ObserverSignals.recordCaptureDiagnostic(this@ObserverAccessibilityService, "screenshots_failed")
                            ObservationStore.appendUiEvent(this@ObserverAccessibilityService, JSONObject()
                                .put("timestampEpochMs", timestampEpochMs)
                                .put("packageName", packageName)
                                .put("eventType", "screenshot_failed")
                                .put("screenshotFailure", "bitmap_unavailable"))
                            return
                        }
                        val stored = ObservationStore.storeScreenshot(
                            this@ObserverAccessibilityService, sessionId, packageName,
                            AppCatalog.eventName(eventType), timestampEpochMs, bitmap
                        )
                        bitmap.recycle()
                        if (stored != null) {
                            ObserverSignals.recordCaptureDiagnostic(this@ObserverAccessibilityService, "screenshots_stored")
                            ScreenUnderstandingService.analyse(this@ObserverAccessibilityService, sessionId, stored)
                        } else {
                            ObserverSignals.recordCaptureDiagnostic(this@ObserverAccessibilityService, "screenshots_failed")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        ObserverSignals.recordCaptureDiagnostic(this@ObserverAccessibilityService, "screenshots_failed")
                        val failureReason = when (errorCode) {
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "platform_rate_limited"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "accessibility_access_unavailable"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "invalid_display"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "secure_window_protected"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "platform_internal_error"
                            else -> "platform_capture_failure"
                        }
                        ObservationStore.appendUiEvent(this@ObserverAccessibilityService, JSONObject()
                            .put("timestampEpochMs", timestampEpochMs)
                            .put("packageName", packageName)
                            .put("eventType", "screenshot_failed")
                            .put("screenshotFailure", failureReason)
                            .put("screenshotErrorCode", errorCode))
                        if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT &&
                            attempt < MAX_SCREENSHOT_RETRIES) {
                            ObserverSignals.recordCaptureDiagnostic(this@ObserverAccessibilityService, "screenshots_rate_limited")
                            Handler(Looper.getMainLooper()).postDelayed({
                                requestScreenshot(
                                    sessionId, packageName, eventType, timestampEpochMs, attempt + 1
                                )
                            }, SCREENSHOT_RETRY_DELAY_MS)
                        }
                    }
                }
            )
        }.onFailure {
            ObserverSignals.recordCaptureDiagnostic(this, "screenshots_failed")
        }
    }

    private fun <T> readNode(context: Context, defaultValue: T, read: () -> T): T =
        runCatching(read).getOrElse {
            ObserverSignals.recordCaptureDiagnostic(context, "node_property_failures")
            defaultValue
        }

    private fun persistUiEvent(context: Context, record: JSONObject): Boolean {
        ObserverSignals.recordCaptureDiagnostic(context, "ui_write_attempts")
        val result = runCatching { ObservationStore.appendUiEvent(context, record) }
        return if (result.isSuccess) {
            ObserverSignals.recordCaptureDiagnostic(context, "ui_write_successes")
            true
        } else {
            ObserverSignals.recordCaptureDiagnostic(context, "ui_write_failures")
            false
        }
    }

    private fun baseEvent(
        event: AccessibilityEvent,
        packageName: String,
        eventName: String,
        wallTime: Long
    ): JSONObject = JSONObject()
        .put("timestampEpochMs", wallTime)
        .put("elapsedRealtimeNanos", SystemClock.elapsedRealtimeNanos())
        .put("eventTimeUptimeMs", runCatching { event.eventTime }.getOrDefault(0L))
        .put("packageName", packageName)
        .put("eventType", eventName)
        .put("windowId", runCatching { event.windowId }.getOrDefault(-1))
        .put("windowClassName", runCatching { event.className?.toString()?.take(180) }.getOrNull() ?: JSONObject.NULL)
        .put("contentChangeTypes", runCatching { event.contentChangeTypes }.getOrDefault(0))
        .put("scrollX", runCatching { event.scrollX }.getOrDefault(0))
        .put("scrollY", runCatching { event.scrollY }.getOrDefault(0))
        .put("maxScrollX", runCatching { event.maxScrollX }.getOrDefault(0))
        .put("maxScrollY", runCatching { event.maxScrollY }.getOrDefault(0))
        .put("itemCount", runCatching { event.itemCount }.getOrDefault(0))
        .put("fromIndex", runCatching { event.fromIndex }.getOrDefault(-1))
        .put("toIndex", runCatching { event.toIndex }.getOrDefault(-1))

    /** Reads only structural properties from the event source; the source node is always released. */
    private fun sourceEvidence(node: AccessibilityNodeInfo?): JSONObject? {
        node ?: return null
        return try {
            val bounds = Rect()
            readNode(this, false) {
                node.getBoundsInScreen(bounds)
                true
            }
            JSONObject()
                .put("className", readNode(this, null as String?) {
                    node.className?.toString()?.take(120)
                } ?: JSONObject.NULL)
                .put("viewId", readNode(this, null as String?) {
                    node.viewIdResourceName?.take(180)
                } ?: JSONObject.NULL)
                .put("clickable", readNode(this, false) { node.isClickable })
                .put("enabled", readNode(this, false) { node.isEnabled })
                .put("selected", readNode(this, false) { node.isSelected })
                .put("checked", readNode(this, false) { node.isChecked })
                .put("bounds", JSONObject()
                    .put("left", bounds.left).put("top", bounds.top)
                    .put("right", bounds.right).put("bottom", bounds.bottom))
        } finally {
            runCatching { node.recycle() }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        ObserverSignals.setServiceState(this, "accessibility", false)
        super.onDestroy()
    }

    companion object {
        @Volatile private var activeInstance: ObserverAccessibilityService? = null

        fun requestNotificationScreenshot(context: Context, packageName: String) {
            val service = activeInstance ?: return
            val sessionId = ObservationStore.activeSessionId(context) ?: return
            if (ObservationStore.activePackage(context) != packageName) return
            val screenshotScope = if (SemanticRuleStore.hasSystemSurfaceRules(context)) {
                SemanticRuleStore.SYSTEM_SURFACE_PACKAGE
            } else packageName
            service.requestScreenshot(
                sessionId, screenshotScope,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                System.currentTimeMillis()
            )
        }

        private const val CONTENT_SAMPLE_INTERVAL_MS = 250L
        private const val MAX_NODES = 250
        private const val SCREENSHOT_INTERVAL_MS = 1_200L
        private const val MAX_SCREENSHOT_RETRIES = 1
        private const val SCREENSHOT_RETRY_DELAY_MS = 1_800L
        private const val MAX_SEMANTIC_TEXT_CHARS = 4_000
    }
}
