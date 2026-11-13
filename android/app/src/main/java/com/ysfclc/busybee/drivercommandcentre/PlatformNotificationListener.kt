package com.ysfclc.busybee.drivercommandcentre

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Exact package allowlist for driver applications whose launch package is already
 * verified in this app. Passenger apps and similarly named packages must not be
 * treated as driver offer sources.
 */
internal object RidePlatformPackages {
    fun platformFor(packageName: String): String? = when (packageName.lowercase()) {
        "com.ubercab.driver" -> "Uber"
        "ee.mtakso.driver" -> "Bolt"
        else -> null
    }
}

class PlatformNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val platform = RidePlatformPackages.platformFor(sbn.packageName) ?: return
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
