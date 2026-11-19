package com.ysfclc.appobserver

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

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

        // Only scalar metadata is read. Notification extras containing text,
        // sender, title, MessagingStyle messages and conversation names are not accessed.
        val notification = sbn.notification
        val isGroup = if (android.os.Build.VERSION.SDK_INT >= 24) {
            notification.extras?.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ?: false
        } else false

        ObserverSignals.recordNotification(
            this, pkg, notification.category, notification.actions?.size ?: 0,
            (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0, isGroup
        )
    }
}
