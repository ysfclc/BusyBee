package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LegacyProviderActivityProjectorTest {
    @Test
    fun nativeAcquisitionMethodsRemainVisibleInEventProvenance() {
        assertEquals(SourceMethod.ANDROID_ACCESSIBILITY_UI, LegacyProviderEventPolicy.sourceMethodForRecord("Android live UI listener"))
        assertEquals(SourceMethod.ANDROID_MEDIA_PROJECTION_OCR, LegacyProviderEventPolicy.sourceMethodForRecord("Android screen capture"))
        assertEquals(SourceMethod.ANDROID_MEDIA_PROJECTION_OCR, LegacyProviderEventPolicy.sourceMethodForRecord("Android MediaProjection OCR"))
        assertEquals(SourceMethod.USER_SELECTED_IMPORT, LegacyProviderEventPolicy.sourceMethodForRecord("uber-statement.csv"))
        assertEquals(SourceMethod.MANUAL, LegacyProviderEventPolicy.sourceMethodForRecord("manual entry"))
    }

    @Test
    fun identicalNotificationTextFromDifferentNotificationsDoesNotCollapse() {
        assertEquals(LegacyProviderEventPolicy.notificationDedupeKey(42L), LegacyProviderEventPolicy.notificationDedupeKey(42L))
        assertNotEquals(LegacyProviderEventPolicy.notificationDedupeKey(42L), LegacyProviderEventPolicy.notificationDedupeKey(43L))
    }

    @Test
    fun offerUpdatesAreIdempotentButDifferentStatusesRemainDistinct() {
        assertEquals(LegacyProviderEventPolicy.offerDedupeKey(7L, "accepted"), LegacyProviderEventPolicy.offerDedupeKey(7L, " ACCEPTED "))
        assertNotEquals(LegacyProviderEventPolicy.offerDedupeKey(7L, "pending"), LegacyProviderEventPolicy.offerDedupeKey(7L, "accepted"))
    }
}
