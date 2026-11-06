package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverAnalyticsTest {
    private fun trip(
        platform: String = "Uber",
        at: String? = "2026-11-05T18:30:00",
        gross: Double? = 50.0,
        fees: Double? = 10.0,
        bonus: Double? = 5.0,
        distance: Double? = 10.0,
        currency: String = "PLN"
    ) = DriverRecord(
        id = 1, platform = platform, type = "trip", externalId = null,
        occurredAt = at, pickup = null, dropoff = null, distanceKm = distance,
        gross = gross, fees = fees, bonus = bonus, currency = currency,
        status = "completed", raw = "", sourceName = "fixture.csv"
    )

    @Test fun computesNetOnlyWhenGrossFeesBonusAndDistanceAreKnown() {
        val result = DriverAnalyticsEngine.calculate(listOf(trip()), 0.8)
        assertEquals(1, result.estimatedNetTrips)
        assertEquals(37.0, result.estimatedNetPln!!, 0.0001)
        assertEquals(37.0, result.platforms.single().estimatedNetAfterVehiclePln!!, 0.0001)
    }

    @Test fun missingFeesNeverBecomeZero() {
        val result = DriverAnalyticsEngine.calculate(listOf(trip(fees = null)), 0.8)
        assertEquals(0, result.estimatedNetTrips)
        assertNull(result.estimatedNetPln)
        assertTrue(result.dataWarnings.any { it.contains("no fee value") })
    }

    @Test fun missingDistanceKeepsKnownNetButDoesNotInventVehicleCost() {
        val result = DriverAnalyticsEngine.calculate(listOf(trip(distance = null)), 0.8)
        assertEquals(0, result.estimatedNetTrips)
        assertNull(result.estimatedNetPln)
        assertEquals(45.0, result.platforms.single().knownNetBeforeVehiclePln!!, 0.0001)
        assertNull(result.platforms.single().estimatedNetAfterVehiclePln)
    }

    @Test fun unlikeCurrenciesAreNotMixed() {
        val result = DriverAnalyticsEngine.calculate(listOf(trip(), trip(platform = "Bolt", currency = "EUR")), 0.8)
        assertEquals(2, result.tripCount)
        assertEquals(1, result.plnTripCount)
        assertEquals(37.0, result.estimatedNetPln!!, 0.0001)
    }

    @Test fun groupsTripsByLocalStartHour() {
        val result = DriverAnalyticsEngine.calculate(
            listOf(trip(at = "2026-11-05T18:30:00"), trip(at = "2026-11-05T18:55:00"), trip(at = "2026-11-05T09:10:00")),
            0.8
        )
        assertEquals(2, result.hours.size)
        assertEquals(18, result.hours.first().hour)
        assertEquals(2, result.hours.first().trips)
    }
}
