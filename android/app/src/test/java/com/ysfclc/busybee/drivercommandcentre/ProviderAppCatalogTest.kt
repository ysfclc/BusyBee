package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAppCatalogTest {
    @Test
    fun mapsOnlyTheTwoExplicitlySupportedRideAppHandoffs() {
        assertEquals("com.ubercab.driver", ProviderAppCatalog.forProvider("Uber Driver")?.packageName)
        assertEquals("ee.mtakso.driver", ProviderAppCatalog.forProvider("Taxify")?.packageName)
        assertEquals("Bolt Driver", ProviderAppCatalog.forProvider("BOLT")?.displayName)
    }

    @Test
    fun doesNotInventLauncherPackagesForOtherProviders() {
        assertNull(ProviderAppCatalog.forProvider("FreeNow"))
        assertNull(ProviderAppCatalog.forProvider("CityCab"))
        assertEquals(2, ProviderAppCatalog.all().size)
    }

    @Test
    fun providerHandoffDoesNotImplyAConnectionState() {
        val uber = ProviderAppCatalog.forProvider("Uber")!!
        assertTrue(uber.packageName.isNotBlank())
        // The catalog contains only an explicit package handoff, not credentials,
        // capabilities, event access or a verified provider connection.
        assertEquals("uber", uber.providerId)
    }
}
