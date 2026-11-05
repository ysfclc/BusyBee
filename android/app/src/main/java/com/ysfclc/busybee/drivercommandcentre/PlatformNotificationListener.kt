package com.ysfclc.busybee.drivercommandcentre

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PlatformNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName.lowercase()
        val platform = when {
            pkg.contains("uber") -> "Uber"
            pkg.contains("bolt") || pkg.contains("taxify") -> "Bolt"
            else -> return
        }
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val body = listOf(
            extras.getCharSequence("android.text")?.toString(),
            extras.getCharSequence("android.bigText")?.toString(),
            extras.getCharSequence("android.subText")?.toString()
        ).filterNotNull().filter { it.isNotBlank() }.distinct().joinToString("\n")
        if (title.isBlank() && body.isBlank()) return
        DriverCommandStore(applicationContext).captureNotice(platform, title, body, sbn.postTime)
    }
}
