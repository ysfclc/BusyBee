package com.ysfclc.busybee.drivercommandcentre
import org.junit.Assert.assertEquals
import org.junit.Test
class AppIdentityTest {
    @Test fun packageIdentityIsStableForInPlaceUpdates() {
        assertEquals(true, BuildConfig.APPLICATION_ID.startsWith("com.ysfclc.busybee.drivercommandcentre"))
    }
}
