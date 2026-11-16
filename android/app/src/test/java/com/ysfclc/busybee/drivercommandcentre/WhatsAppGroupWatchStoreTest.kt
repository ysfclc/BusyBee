package com.ysfclc.busybee.drivercommandcentre

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WhatsAppGroupWatchStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var store: DriverCommandStore

    @Before
    fun setUp() {
        context.deleteDatabase("busybee_command_centre.db")
        store = DriverCommandStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase("busybee_command_centre.db")
    }

    @Test
    fun discoveredGroupStoresNoMessageUntilDriverEnablesIt() {
        val groupId = store.observeWhatsAppGroup("com.whatsapp", "Krakow Drivers", 1000L)
        assertNotNull(groupId)
        assertFalse(store.isWhatsAppGroupTrackingEnabled(groupId!!))

        assertFalse(store.captureTrackedWhatsAppMessage(groupId, "Alex", "Road closure on A4", 1100L))
        assertTrue(store.whatsappGroupMessages(groupId).isEmpty())
        assertEquals(0, store.whatsappGroups().single().capturedMessages)
    }

    @Test
    fun onlyEnabledGroupCapturesMessagesAndDuplicateNotificationsAreIgnored() {
        val trackedId = store.observeWhatsAppGroup("com.whatsapp", "Night Shift", 1000L)!!
        val ignoredId = store.observeWhatsAppGroup("com.whatsapp", "Weekend Drivers", 1000L)!!
        store.setWhatsAppGroupTracking(trackedId, true)

        assertTrue(store.captureTrackedWhatsAppMessage(trackedId, "Mina", "Airport queue is long", 2000L))
        assertFalse(store.captureTrackedWhatsAppMessage(trackedId, "Mina", "Airport queue is long", 2000L))
        assertFalse(store.captureTrackedWhatsAppMessage(ignoredId, "Sam", "Police on the road", 2001L))
        assertEquals(1, store.whatsappGroupMessages(trackedId).size)
        assertTrue(store.whatsappGroupMessages(ignoredId).isEmpty())
    }

    @Test
    fun rediscoveringGroupPreservesItsTrackingChoiceAndDeleteRemovesItsData() {
        val groupId = store.observeWhatsAppGroup("com.whatsapp.w4b", "Fleet Dispatch", 1000L)!!
        store.setWhatsAppGroupTracking(groupId, true)
        store.captureTrackedWhatsAppMessage(groupId, "Dispatcher", "Vehicle ready", 2000L)

        val rediscovered = store.observeWhatsAppGroup("com.whatsapp.w4b", "Fleet Dispatch", 3000L)
        assertEquals(groupId, rediscovered)
        assertTrue(store.isWhatsAppGroupTrackingEnabled(groupId))
        assertEquals(3000L, store.whatsappGroups().single().lastSeenAt)

        store.deleteWhatsAppGroup(groupId)
        assertTrue(store.whatsappGroups().isEmpty())
        assertTrue(store.whatsappGroupMessages(groupId).isEmpty())
    }

    @Test
    fun rejectsNonWhatsAppPackages() {
        assertEquals(null, store.observeWhatsAppGroup("com.example.fake", "Drivers", 1000L))
        assertTrue(store.whatsappGroups().isEmpty())
    }
}
