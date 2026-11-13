package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class DriverScreenTextParserTest {
    @Test
    fun parsesExplicitMoneyDistanceAndDurationWithoutCommittingARecord() {
        val parsed = DriverScreenTextParser.parse(
            platform = "Uber",
            text = "TRIP COMPLETE\nFare 42,50 PLN\nDistance 8.4 km\nDuration 23 min",
            capturedAt = 1_800_000_000_000L
        )

        assertNotNull(parsed)
        assertEquals("TRIP", parsed?.screenType)
        assertEquals(42.5, parsed?.detectedAmount ?: 0.0, 0.001)
        assertEquals(8.4, parsed?.distanceKm ?: 0.0, 0.001)
        assertEquals(23.0, parsed?.durationMinutes ?: 0.0, 0.001)
    }

    @Test
    fun recognisesEarningsButDoesNotInventCurrencyOrMoney() {
        val parsed = DriverScreenTextParser.parse(
            platform = "Bolt",
            text = "Weekly earnings\nToday total\n42.50",
            capturedAt = 1_800_000_000_000L
        )

        assertNotNull(parsed)
        assertEquals("EARNINGS", parsed?.screenType)
        assertNull(parsed?.detectedAmount)
    }

    @Test
    fun ignoresUnrelatedDriverAppScreenText() {
        assertNull(
            DriverScreenTextParser.parse(
                platform = "Uber",
                text = "Settings\nPreferences\nApp version 1.0",
                capturedAt = 1_800_000_000_000L
            )
        )
    }
}
