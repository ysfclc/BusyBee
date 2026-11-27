package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationFingerprintTest {
    @Test
    fun identicalRepostsOfSameAndroidNotificationAreDeduplicated() {
        val first = NotificationFingerprint.create("Uber", "New trip", "Pickup nearby", 1_800_000_000_000L)
        val repost = NotificationFingerprint.create("Uber", "New trip", "Pickup nearby", 1_800_000_000_000L)

        assertEquals(first, repost)
    }

    @Test
    fun identicalOfferTextAtDifferentPostTimesIsPreservedAsANewOffer() {
        val first = NotificationFingerprint.create("Uber", "New trip", "Pickup nearby", 1_800_000_000_000L)
        val laterOffer = NotificationFingerprint.create("Uber", "New trip", "Pickup nearby", 1_800_000_000_001L)

        assertNotEquals(first, laterOffer)
    }

    @Test
    fun changedNotificationContentHasADifferentIdentity() {
        val first = NotificationFingerprint.create("Bolt", "New ride", "Pickup: Main Street", 1_800_000_000_000L)
        val updated = NotificationFingerprint.create("Bolt", "New ride", "Pickup: Other Street", 1_800_000_000_000L)

        assertNotEquals(first, updated)
    }

    @Test
    fun platformIsPartOfNotificationIdentity() {
        val uber = NotificationFingerprint.create("Uber", "New trip", "Pickup nearby", 1_800_000_000_000L)
        val bolt = NotificationFingerprint.create("Bolt", "New trip", "Pickup nearby", 1_800_000_000_000L)

        assertNotEquals(uber, bolt)
    }
}
