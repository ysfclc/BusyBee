package com.ysfclc.appobserver

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/**
 * Records notification envelope metadata for the selected app only.
 * Notification titles, bodies, expanded text, replies and action labels are never read.
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
