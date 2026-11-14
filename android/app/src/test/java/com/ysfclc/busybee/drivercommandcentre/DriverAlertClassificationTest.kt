package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriverAlertClassificationTest {
    @Test
    fun identifiesEnglishOfferNotifications() {
        assertEquals("OFFER", DriverAlertManager.classifyNotification("New trip request", "Pickup in 2 min"))
    }

    @Test
    fun identifiesPolishOfferNotifications() {
        assertEquals("OFFER", DriverAlertManager.classifyNotification("Nowa oferta", "Nowy kurs · odbiór za 3 min"))
    }

    @Test
    fun identifiesEarningsAndPayoutNotifications() {
        assertEquals("EARNINGS", DriverAlertManager.classifyNotification("Weekly earnings", "Payout is ready"))
        assertEquals("EARNINGS", DriverAlertManager.classifyNotification("Wypłata", "Rozliczenie tygodniowe"))
    }

    @Test
    fun ignoresUnclassifiedUpdatesInsteadOfCreatingNoisyAlerts() {
        assertEquals("UPDATE", DriverAlertManager.classifyNotification("Driver app", "Settings were updated"))
    }

    @Test
    fun mapsOnlyKnownDriverPackages() {
        assertEquals("Uber", RidePlatformPackages.platformFor("com.ubercab.driver"))
        assertEquals("Bolt", RidePlatformPackages.platformFor("ee.mtakso.driver"))
        assertNull(RidePlatformPackages.platformFor("com.ubercab.eats"))
        assertNull(RidePlatformPackages.platformFor("com.ubercab.passenger"))
        assertNull(RidePlatformPackages.platformFor("com.other.driver"))
    }
}
