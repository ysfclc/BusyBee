package com.ysfclc.busybee.drivercommandcentre

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverFinancialSummaryTest {
    private fun row(
        type: String,
        currency: String = "PLN",
        gross: Double? = 40.0,
        fees: Double? = 8.0,
        bonus: Double? = 2.0
    ) = DriverFinancialRow(type, currency, gross, fees, bonus)

    @Test fun headlineTotalsIncludeTripsButExcludeOffersAndOtherActivity() {
        val result = DriverFinancialSummaryCalculator.calculate(
            listOf(
                row("trip"),
                row("offer", gross = 120.0, fees = 20.0, bonus = 30.0),
                row("adjustment", gross = 500.0, fees = 0.0, bonus = 0.0),
                row("ride", currency = "EUR", gross = 90.0, fees = 10.0, bonus = 0.0)
            )
        )

        assertEquals(40.0, result.gross!!, 0.0001)
        assertEquals(8.0, result.fees!!, 0.0001)
        assertEquals(2.0, result.bonuses!!, 0.0001)
        assertEquals(34.0, result.knownNet!!, 0.0001)
        assertTrue(result.netComplete)
    }

    @Test fun incompleteOfferDoesNotMakeCompleteTripEarningsIncomplete() {
        val result = DriverFinancialSummaryCalculator.calculate(
            listOf(
                row("completed_trip"),
                row("offer", gross = 70.0, fees = null, bonus = null)
            )
        )

        assertTrue(result.netComplete)
        assertEquals(34.0, result.knownNet!!, 0.0001)
    }

    @Test fun everyTripMustHaveGrossFeesAndBonusBeforeHeadlineNetIsShown() {
        val result = DriverFinancialSummaryCalculator.calculate(
            listOf(
                row("trip"),
                row("trip", gross = null, fees = 1.0, bonus = 0.0)
            )
        )

        assertFalse(result.netComplete)
        assertNull(result.knownNet)
        assertEquals(40.0, result.gross!!, 0.0001)
        assertEquals(9.0, result.fees!!, 0.0001)
        assertEquals(2.0, result.bonuses!!, 0.0001)
    }

    @Test fun recognisesUppercaseTripTypesIndependentOfDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val result = DriverFinancialSummaryCalculator.calculate(listOf(row("TRIP")))
            assertTrue(result.netComplete)
            assertEquals(34.0, result.knownNet!!, 0.0001)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun emptyAndNonPlnOnlyDataCannotProduceHeadlineNet() {
        val empty = DriverFinancialSummaryCalculator.calculate(emptyList())
        val foreign = DriverFinancialSummaryCalculator.calculate(listOf(row("trip", currency = "EUR")))

        assertFalse(empty.netComplete)
        assertNull(empty.knownNet)
        assertFalse(foreign.netComplete)
        assertNull(foreign.gross)
        assertNull(foreign.knownNet)
    }
}
