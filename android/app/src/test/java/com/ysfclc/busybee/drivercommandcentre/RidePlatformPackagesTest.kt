package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RidePlatformPackagesTest {
    @Test
    fun recognisesVerifiedUberDriverPackage() {
        assertEquals("Uber", RidePlatformPackages.platformFor("com.ubercab.driver"))
    }

    @Test
    fun recognisesVerifiedBoltDriverPackage() {
        assertEquals("Bolt", RidePlatformPackages.platformFor("ee.mtakso.driver"))
    }

    @Test
    fun packageMatchingIsCaseInsensitive() {
        assertEquals("Bolt", RidePlatformPackages.platformFor("EE.MTAKSO.DRIVER"))
    }

    @Test
    fun doesNotCapturePassengerOrLookalikePackages() {
        assertNull(RidePlatformPackages.platformFor("com.ubercab"))
        assertNull(RidePlatformPackages.platformFor("com.ubercab.eats"))
        assertNull(RidePlatformPackages.platformFor("com.bolt.passenger"))
        assertNull(RidePlatformPackages.platformFor("com.example.uber.driver"))
    }

    @Test
    fun doesNotClaimUnverifiedProvidersAsIntegrated() {
        assertNull(RidePlatformPackages.platformFor("com.freenow.driver"))
        assertNull(RidePlatformPackages.platformFor("com.cabify.driver"))
        assertNull(RidePlatformPackages.platformFor("com.lyft.driver"))
    }
}
