package com.ysfclc.busybee.drivercommandcentre

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverMoneyEngineTest {
    private fun trip(
        id: Long,
        at: String? = "2026-11-25T12:00:00",
        gross: Double? = 50.0,
        fees: Double? = 10.0,
        bonus: Double? = 5.0,
        distance: Double? = 10.0,
        currency: String = "PLN",
        platform: String = "Uber"
    ) = DriverRecord(
        id = id, platform = platform, type = "trip", externalId = "ride-$id",
        occurredAt = at, pickup = null, dropoff = null, distanceKm = distance,
        gross = gross, fees = fees, bonus = bonus, currency = currency,
        status = "completed", raw = "", sourceName = "fixture.csv"
    )

    private val today = LocalDate.of(2026, 11, 25)
    private val warsaw = ZoneId.of("Europe/Warsaw")

    @Test fun todayIncludesOnlyLocalCalendarDay() {
        val report = DriverMoneyEngine.calculate(
            listOf(
                trip(1, "2026-11-25T00:10:00"),
                trip(2, "2026-11-24T23:59:00"),
                trip(3, "2026-11-25T23:59:00")
            ), 0.8, DriverMoneyPeriod.TODAY, today, warsaw
        )
        assertEquals(2, report.tripCount)
        assertEquals(2, report.completeNetTrips)
        assertEquals(74.0, report.estimatedNetAfterVehiclePln!!, 0.0001)
    }

    @Test fun sevenAndThirtyDayWindowsIncludeTodayAndTheirStartDate() {
        val records = listOf(
            trip(1, "2026-11-19T12:00:00"),
            trip(2, "2026-11-18T23:59:59"),
            trip(3, "2026-10-27T12:00:00"),
            trip(4, "2026-10-26T23:59:59")
        )
        assertEquals(1, DriverMoneyEngine.calculate(records, 0.8, DriverMoneyPeriod.SEVEN_DAYS, today, warsaw).tripCount)
        assertEquals(3, DriverMoneyEngine.calculate(records, 0.8, DriverMoneyPeriod.THIRTY_DAYS, today, warsaw).tripCount)
    }

    @Test fun undatedTripsAreExcludedFromFinitePeriodsAndReported() {
        val report = DriverMoneyEngine.calculate(
            listOf(trip(1), trip(2, at = null)),
            0.8, DriverMoneyPeriod.TODAY, today, warsaw
        )
        assertEquals(1, report.tripCount)
        assertEquals(1, report.undatedTripCount)
        assertEquals(1, report.datedTripCount)
    }

    @Test fun allTimeIncludesUndatedTripsWithoutInventingTheirDate() {
        val report = DriverMoneyEngine.calculate(
            listOf(trip(1), trip(2, at = null)),
            0.8, DriverMoneyPeriod.ALL_TIME, today, warsaw
        )
        assertEquals(2, report.tripCount)
        assertEquals(1, report.datedTripCount)
        assertEquals(1, report.undatedTripCount)
    }

    @Test fun currencyAndIncompleteRowsAreNeverFilledOrMixed() {
        val report = DriverMoneyEngine.calculate(
            listOf(
                trip(1),
                trip(2, gross = 40.0, fees = null),
                trip(3, currency = "EUR")
            ), 0.8, DriverMoneyPeriod.ALL_TIME, today, warsaw
        )
        assertEquals(3, report.tripCount)
        assertEquals(1, report.nonPlnTripCount)
        assertEquals(1, report.completeNetTrips)
        assertEquals(37.0, report.estimatedNetAfterVehiclePln!!, 0.0001)
        assertEquals(90.0, report.grossPln!!, 0.0001)
        assertNull(DriverMoneyEngine.calculate(
            listOf(trip(3, currency = "EUR")), 0.8, DriverMoneyPeriod.ALL_TIME, today, warsaw
        ).estimatedNetAfterVehiclePln)
    }

    @Test fun offsetTimestampsAreConvertedToTheSelectedLocalDay() {
        val report = DriverMoneyEngine.calculate(
            listOf(trip(1, "2026-11-24T23:30:00Z")),
            0.8, DriverMoneyPeriod.TODAY, today, warsaw
        )
        assertEquals(1, report.tripCount)
    }

    @Test fun invalidVehicleCostIsRejected() {
        val error = runCatching {
            DriverMoneyEngine.calculate(emptyList(), -0.1, DriverMoneyPeriod.ALL_TIME, today, warsaw)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
