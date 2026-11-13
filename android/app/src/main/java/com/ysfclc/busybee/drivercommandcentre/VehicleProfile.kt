package com.ysfclc.busybee.drivercommandcentre

import java.util.Locale

data class VehicleProfile(
    val name: String = "",
    val category: String = "Passenger car",
    val fuelType: String = "Petrol",
    val consumptionPer100Km: Double? = null,
    val energyPricePln: Double? = null,
    val maintenanceCostPerKm: Double = 0.0
) {
    val energyCostPerKm: Double?
        get() {
            val consumption = consumptionPer100Km ?: return null
            val price = energyPricePln ?: return null
            if (!consumption.isFinite() || !price.isFinite() || consumption <= 0.0 || price <= 0.0) return null
            return consumption * price / 100.0
        }

    val totalCostPerKm: Double?
        get() {
            val energy = energyCostPerKm ?: return null
            if (!maintenanceCostPerKm.isFinite() || maintenanceCostPerKm < 0.0) return null
            return energy + maintenanceCostPerKm
        }

    fun summary(): String {
        val identity = name.trim().ifBlank { category }
        val energyUnit = if (fuelType.equals("Electric", true)) "kWh/100 km" else "L/100 km"
        val energy = consumptionPer100Km?.let { String.format(Locale.US, "%.1f %s", it, energyUnit) } ?: "consumption not set"
        val cost = totalCostPerKm?.let { String.format(Locale.US, "%.2f PLN/km", it) } ?: "running cost not calculated"
        return "$identity · $fuelType · $energy · $cost"
    }

    companion object {
        val CATEGORIES = listOf("Compact / sedan", "Estate / wagon", "SUV", "MPV / 7-seat", "Van")
        val FUEL_TYPES = listOf("Petrol", "Diesel", "LPG", "Hybrid", "Electric")
    }
}
