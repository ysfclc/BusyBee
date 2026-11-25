package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DriverCsvTest {
    @Test fun parsesQuotedCommasAndEscapedQuotes() {
        val rows = DriverCsv.parse(
            "platform,record_type,pickup,dropoff\nUber,trip,\"Old Town, Krakow\",\"Driver said \"\"hello\"\"\""
        )
        assertEquals("Old Town, Krakow", rows.single()["pickup"])
        assertEquals("Driver said \"hello\"", rows.single()["dropoff"])
    }

    @Test fun preservesMissingAmountsAsUnknown() {
        val row = DriverCsv.parse("platform,record_type,gross,fees\nBolt,trip,42.50,").single()
        assertEquals(42.5, DriverCsv.number(row["gross"])!!, 0.0001)
        assertNull(DriverCsv.number(row["fees"]))
    }

    @Test fun rejectsMissingRequiredHeaders() {
        assertThrows(IllegalArgumentException::class.java) {
            DriverCsv.parse("platform,gross\nUber,10")
        }
    }

    @Test fun acceptsCommaDecimalAmounts() {
        assertEquals(12.75, DriverCsv.number("12,75")!!, 0.0001)
    }

    @Test fun blankCurrencyIsPreservedInsteadOfDefaultingToPln() {
        val blank = DriverCsv.parse("platform,record_type,currency\\nBolt,trip,").single()
        val omitted = DriverCsv.parse("platform,record_type\\nBolt,trip").single()
        assertEquals("", blank["currency"])
        assertNull(omitted["currency"])
    }

    @Test fun missingTrailingCellInPresentCurrencyColumnIsUnknown() {
        val row = DriverCsv.parse("platform,record_type,currency\\nBolt,trip").single()
        assertEquals("", row["currency"])
    }

    @Test fun rejectsDuplicateHeadersAfterNormalisation() {
        assertThrows(IllegalArgumentException::class.java) {
            DriverCsv.parse("platform,record_type,Gross,gross\\nUber,trip,10,11")
        }
    }

    @Test fun importReviewFindsInvalidNumbersAndNegativeDistance() {
        val rows = DriverCsv.parse(
            "platform,record_type,distance_km,gross,fees\\nUber,trip,-2,not-money,3"
        )
        val errors = DriverCsv.validationErrors(rows)
        assertEquals(2, errors.size)
    }

    @Test fun importReviewAllowsBlankAmountsAsUnknown() {
        val rows = DriverCsv.parse("platform,record_type,gross,fees,bonus\\nBolt,trip,42.50,,")
        assertEquals(emptyList<String>(), DriverCsv.validationErrors(rows))
    }
}
