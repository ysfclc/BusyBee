package com.ysfclc.busybee.drivercommandcentre

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WhatsAppNotificationParserTest {
    private fun notification(
        conversationTitle: String?,
        body: String?,
        sender: String = "Yusuf",
        isGroup: Boolean = true
    ): Notification {
        val context = RuntimeEnvironment.getApplication()
        return Notification.Builder(context)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
            .apply {
                extras.putBoolean("android.isGroupConversation", isGroup)
                if (conversationTitle != null) extras.putCharSequence("android.conversationTitle", conversationTitle)
                if (body != null) extras.putCharSequence("android.text", body)
                extras.putCharSequence("android.title", sender)
            }
    }

    @Test
    fun parsesOnlyVisibleGroupNotificationContent() {
        val parsed = WhatsAppNotificationParser.parse(
            notification("Krakow Drivers", "Roadworks at Rondo Mogilskie"),
            1234L
        )

        assertNotNull(parsed)
        assertEquals("Krakow Drivers", parsed?.groupTitle)
        assertEquals(1, parsed?.messages?.size)
        assertEquals("Yusuf", parsed?.messages?.single()?.first)
        assertEquals("Roadworks at Rondo Mogilskie", parsed?.messages?.single()?.second)
        assertEquals(1234L, parsed?.messages?.single()?.third)
    }

    @Test
    fun rejectsPrivateOneToOneConversation() {
        assertNull(
            WhatsAppNotificationParser.parse(
                notification("Direct chat", "See you later", isGroup = false),
                1234L
            )
        )
    }

    @Test
    fun discoversGroupEvenWhenAndroidDoesNotExposeMessagePreview() {
        val parsed = WhatsAppNotificationParser.parse(
            notification("Night Shift Drivers", null),
            1234L
        )

        assertNotNull(parsed)
        assertEquals("Night Shift Drivers", parsed?.groupTitle)
        assertEquals(0, parsed?.messages?.size)
    }

    @Test
    fun doesNotInventGroupNameWhenConversationTitleIsMissing() {
        assertNull(
            WhatsAppNotificationParser.parse(
                notification(null, "Message text"),
                1234L
            )
        )
    }
}
