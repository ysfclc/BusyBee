package com.ysfclc.busybee.drivercommandcentre

import android.app.Notification
import android.os.Build
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

    fun parse(notification: Notification): GroupUpdate? {
        val extras = notification.extras
        val style = Notification.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val isGroup = if (Build.VERSION.SDK_INT >= 28) {
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, style?.isGroupConversation == true)
        } else {
            style?.isGroupConversation == true
        }
        if (!isGroup) return null

        val title = style?.conversationTitle?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() }
            ?: return null

        val messages = style?.messages.orEmpty().mapNotNull { message ->
            val body = message.text?.toString()?.trim().orEmpty()
            if (body.isBlank()) null
            else Triple(
                message.sender?.toString().orEmpty().ifBlank { "Unknown sender" },
                body,
                message.timestamp
            )
        }
        return GroupUpdate(title.take(160), messages)
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
        val update = WhatsAppNotificationParser.parse(sbn.notification) ?: return
        val store = DriverCommandStore(applicationContext)
        val groupId = store.observeWhatsAppGroup(sbn.packageName, update.groupTitle, sbn.postTime) ?: return

        // Discover the group even while tracking is off, but do not persist the
        // notification's message text until the driver enables this exact group.
        if (!store.isWhatsAppGroupTrackingEnabled(groupId)) return
        update.messages.forEach { (sender, body, timestamp) ->
            store.captureTrackedWhatsAppMessage(groupId, sender, body, timestamp)
        }
    }
}
