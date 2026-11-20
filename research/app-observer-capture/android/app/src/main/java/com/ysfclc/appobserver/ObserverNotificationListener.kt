package com.ysfclc.appobserver

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/**
 * Records notification envelope metadata by default. Notification text is read only when
 * the user created a matching screenshot-region rule with notification text explicitly enabled.
 */
class ObserverNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        ObserverSignals.setServiceState(this, "notifications", true)
    }

    override fun onListenerDisconnected() {
        ObserverSignals.setServiceState(this, "notifications", false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        if (!ObservationStore.hasActiveSession(this)) return
        if (!AppCatalog.isSessionTarget(packageName, ObservationStore.activePackage(this))) return

        val notification = sbn.notification
        val isGroup = if (android.os.Build.VERSION.SDK_INT >= 24) {
            notification.extras?.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ?: false
        } else false

        ObserverSignals.recordNotification(
            this,
            packageName,
            notification.category,
            notification.actions?.size ?: 0,
            (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0,
            isGroup
        )

        // Only fetch notification content after an explicit user-authored rule enables it.
        val sessionId = ObservationStore.activeSessionId(this)
        val notificationRules = SemanticRuleStore.notificationRules(this, packageName)
        if (sessionId != null && notificationRules.isNotEmpty()) {
            val extras = notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(500).orEmpty()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(4_000).orEmpty()
            val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.take(4_000).orEmpty()
            val lines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.take(12)?.joinToString("\n") { it.toString().take(500) }.orEmpty()
            val body = bigText.ifBlank { lines.ifBlank { text } }
            if (title.isNotBlank() || body.isNotBlank()) {
                val linkedScreenshot = ObservationStore.latestScreenshotId(this, sessionId)
                notificationRules.forEach { rule ->
                    ObservationStore.appendSemanticPayload(this, sessionId, JSONObject()
                        .put("timestampEpochMs", sbn.postTime)
                        .put("packageName", packageName)
                        .put("semanticRuleId", rule.id)
                        .put("semanticLabel", rule.label)
                        .put("source", "notification_listener")
                        .put("screenshotId", linkedScreenshot ?: JSONObject.NULL)
                        .put("notificationCategory", notification.category ?: JSONObject.NULL)
                        .put("payloadTitle", title)
                        .put("payloadText", body))
                }
            }
            ObserverAccessibilityService.requestNotificationScreenshot(this, packageName)
        }

        ObservationStore.appendUiEvent(this, JSONObject()
            .put("timestampEpochMs", sbn.postTime)
            .put("packageName", packageName)
            .put("eventType", "notification_posted")
            .put("category", notification.category ?: JSONObject.NULL)
            .put("ongoing", (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
            .put("groupConversation", isGroup)
            .put("actionCount", notification.actions?.size ?: 0)
            .put("hasContentIntent", notification.contentIntent != null))
    }
}
