package com.ysfclc.appobserver

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * Records metadata for observed apps and, during an explicitly opted-in session,
 * redacted title/text for notifications from the selected app only.
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
        val pkg = sbn.packageName ?: return
        if (!AppCatalog.isObservedPackage(pkg)) return

        val notification = sbn.notification
        val isGroup = if (android.os.Build.VERSION.SDK_INT >= 24) {
            notification.extras?.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ?: false
        } else false

        ObserverSignals.recordNotification(
            this, pkg, notification.category, notification.actions?.size ?: 0,
            (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0, isGroup
        )

        if (!ObservationStore.hasActiveSession(this) || ObservationStore.activePackage(this) != pkg) return
        val event = JSONObject()
            .put("timestampEpochMs", sbn.postTime)
            .put("packageName", pkg)
            .put("eventType", "notification_posted")
            .put("category", notification.category ?: JSONObject.NULL)
            .put("ongoing", (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
            .put("groupConversation", isGroup)
            .put("actionCount", notification.actions?.size ?: 0)

        if (ObservationStore.includeUiText(this)) {
            val extras = notification.extras
            val title = ObservationStore.sanitizeVisibleText(extras?.getCharSequence(Notification.EXTRA_TITLE))
            val text = ObservationStore.sanitizeVisibleText(extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras?.getCharSequence(Notification.EXTRA_TEXT))
            if (title.isNotBlank()) event.put("title", title)
            if (text.isNotBlank()) event.put("text", text)
            val lines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (lines != null && lines.isNotEmpty()) {
                val safeLines = JSONArray()
                lines.take(5).forEach { line ->
                    ObservationStore.sanitizeVisibleText(line).takeIf { it.isNotBlank() }?.let(safeLines::put)
                }
                if (safeLines.length() > 0) event.put("textLines", safeLines)
            }
        }
        ObservationStore.appendUiEvent(this, event)
    }
}
