package com.ysfclc.busybee.drivercommandcentre

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Exact package allowlist for ride apps. Notification capture is a fallback signal;
 * the primary app-data path is the driver's explicit native screen-capture bridge
 * or an approved provider API.
 */
internal object RidePlatformPackages {
    fun platformFor(packageName: String): String? = when (packageName.lowercase()) {
        "com.ubercab.driver" -> "Uber"
        "ee.mtakso.driver" -> "Bolt"
        else -> null
    }
}

internal object WhatsAppNotificationParser {
    data class GroupUpdate(
        val groupTitle: String,
        val messages: List<Triple<String, String, Long>>
    )

    /**
     * Uses only Android's public notification extras. WhatsApp does not expose
     * its private chat database to this listener. A message is available only
     * when WhatsApp places its group conversation title and message preview in
     * an Android notification.
     */
    fun parse(notification: Notification, postedAt: Long): GroupUpdate? {
        val extras = notification.extras ?: return null
        val isGroup = extras.getBoolean("android.isGroupConversation", false)
        if (!isGroup) return null

        val title = extras.getCharSequence("android.conversationTitle")
            ?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val body = (
            extras.getCharSequence("android.bigText")
                ?: extras.getCharSequence("android.text")
            )?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: return GroupUpdate(title.take(160), emptyList())

        val sender = extras.getCharSequence("android.title")
            ?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: "WhatsApp participant"
        return GroupUpdate(
            groupTitle = title.take(160),
            messages = listOf(Triple(sender.take(120), body.take(4000), postedAt))
        )
    }
}

class PlatformNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        when {
            RidePlatformPackages.platformFor(sbn.packageName) != null -> captureRidePlatformNotice(sbn)
            sbn.packageName == "com.whatsapp" || sbn.packageName == "com.whatsapp.w4b" ->
                captureSelectedWhatsAppGroup(sbn)
        }
    }

    private fun captureRidePlatformNotice(sbn: StatusBarNotification) {
        val platform = RidePlatformPackages.platformFor(sbn.packageName) ?: return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = listOf(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        ).filterNotNull().filter { it.isNotBlank() }.distinct().joinToString("\n")
        if (title.isBlank() && body.isBlank()) return
        DriverCommandStore(applicationContext).captureNotice(platform, title, body, sbn.postTime)
    }

    private fun captureSelectedWhatsAppGroup(sbn: StatusBarNotification) {
        val update = WhatsAppNotificationParser.parse(sbn.notification, sbn.postTime) ?: return
        val store = DriverCommandStore(applicationContext)
        val groupId = store.observeWhatsAppGroup(sbn.packageName, update.groupTitle, sbn.postTime) ?: return

        // Group discovery works while tracking is off, but message text is not
        // persisted until the driver enables this exact group.
        if (!store.isWhatsAppGroupTrackingEnabled(groupId)) return
        update.messages.forEach { (sender, body, timestamp) ->
            store.captureTrackedWhatsAppMessage(groupId, sender, body, timestamp)
        }
    }
}
