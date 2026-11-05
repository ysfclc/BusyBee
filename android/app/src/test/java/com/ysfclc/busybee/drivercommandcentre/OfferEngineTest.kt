package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferEngineTest {
    @Test fun subtractsVehicleCostFromPickupAndTripDistance() {
        val offer = DriverOffer(platform="Uber", fare=50.0, bonus=5.0, pickupKm=4.0, tripKm=10.0, durationMin=20.0)
        val result = OfferEngine.rank(listOf(offer), costPerKm=1.0, minimumNetPerHour=0.0, maximumPickupKm=10.0, minimumPassengerRating=0.0).single()
        assertEquals(41.0, result.estimatedNet, 0.0001)
        assertEquals(29.6, result.totalMinutes, 0.1)
    }

    @Test fun ranksByNetHourlyValueNotGrossFare() {
        val highFareLongRide = DriverOffer(platform="Uber", fare=100.0, pickupKm=5.0, tripKm=40.0, durationMin=100.0)
        val lowerFareShortRide = DriverOffer(platform="Bolt", fare=35.0, pickupKm=1.0, tripKm=5.0, durationMin=15.0)
        val ranked = OfferEngine.rank(listOf(highFareLongRide, lowerFareShortRide), 0.0, 0.0, 10.0, 0.0)
        assertEquals("Bolt", ranked.first().offer.platform)
    }

    @Test fun appliesPickupAndHourlyThresholds() {
        val near = DriverOffer(platform="Bolt", fare=30.0, pickupKm=1.0, tripKm=4.0, durationMin=10.0, passengerRating=4.9)
        val far = DriverOffer(platform="Uber", fare=90.0, pickupKm=12.0, tripKm=10.0, durationMin=20.0, passengerRating=4.9)
        val ranked = OfferEngine.rank(listOf(near, far), 0.5, 30.0, 5.0, 4.8)
        assertEquals(1, ranked.size)
        assertEquals("Bolt", ranked.single().offer.platform)
    }

    @Test fun minimumRatingRejectsOffersWithoutRatingWhenEnabled() {
        val unknown = DriverOffer(platform="Uber", fare=50.0, pickupKm=1.0, tripKm=5.0, durationMin=15.0)
        assertTrue(OfferEngine.rank(listOf(unknown), 0.0, 0.0, 5.0, 4.7).isEmpty())
    }
}
