package com.ysfclc.busybee.drivercommandcentre

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VehicleProfileStoreTest {
    private lateinit var context: Context
    private lateinit var store: DriverCommandStore

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("busybee_command_centre.db")
        store = DriverCommandStore(context)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase("busybee_command_centre.db")
    }

    @Test fun profilePersistsAndFeedsExistingOfferCostSetting() {
        val profile = VehicleProfile("Taxi", "Estate / wagon", "LPG", 10.0, 3.25, 0.18)
        store.saveVehicleProfile(profile)
        val loaded = store.vehicleProfile()
        assertEquals(profile, loaded)
        assertEquals("0.5050", store.setting("cost_per_km", ""))
    }

    @Test fun clearingVehicleEnergyInputsDoesNotRetainThePreviousVehiclesCost() {
        store.saveVehicleProfile(VehicleProfile("Old vehicle", "SUV", "Diesel", 8.0, 6.5, 0.2))
        store.saveVehicleProfile(VehicleProfile("New vehicle", "Van", "Electric", null, null, 0.0))
        assertEquals("New vehicle", store.vehicleProfile().name)
        assertNull(store.vehicleProfile().totalCostPerKm)
        assertEquals("0.85", store.setting("cost_per_km", ""))
    }
}
