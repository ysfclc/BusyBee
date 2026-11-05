package com.ysfclc.busybee.drivercommandcentre
import org.junit.Assert.assertEquals
import org.junit.Test
class AppIdentityTest {
    @Test fun packageIdentityIsStableForInPlaceUpdates() {
        assertEquals("com.ysfclc.busybee.drivercommandcentre", BuildConfig.APPLICATION_ID)
    }
}
