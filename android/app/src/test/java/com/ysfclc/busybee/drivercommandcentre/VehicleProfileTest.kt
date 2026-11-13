package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleProfileTest {
    @Test fun calculatesEnergyAndMaintenanceCostPerKilometre() {
        val vehicle = VehicleProfile("Taxi", "Estate / wagon", "LPG", 10.0, 3.25, 0.18)
        assertEquals(0.325, vehicle.energyCostPerKm!!, 0.0001)
        assertEquals(0.505, vehicle.totalCostPerKm!!, 0.0001)
    }

    @Test fun doesNotInventRunningCostWhenEnergyInputsAreMissing() {
        val vehicle = VehicleProfile(category = "SUV")
        assertNull(vehicle.energyCostPerKm)
        assertNull(vehicle.totalCostPerKm)
        assertTrue(vehicle.summary().contains("running cost not calculated"))
    }

    @Test fun electricVehiclesUseEnergyConsumptionWithoutChangingFormula() {
        val vehicle = VehicleProfile(fuelType = "Electric", consumptionPer100Km = 18.0, energyPricePln = 1.10, maintenanceCostPerKm = 0.10)
        assertEquals(0.298, vehicle.totalCostPerKm!!, 0.0001)
        assertTrue(vehicle.summary().contains("kWh/100 km"))
    }
}
