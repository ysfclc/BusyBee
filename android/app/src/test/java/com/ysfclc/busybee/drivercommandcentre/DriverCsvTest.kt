package com.ysfclc.busybee.drivercommandcentre

import java.util.Locale
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

    @Test fun padsOmittedTrailingOptionalColumnsAsUnknown() {
        val row = DriverCsv.parse("platform,record_type,gross,fees\nBolt,trip,42.50").single()
        assertEquals(42.5, DriverCsv.number(row["gross"])!!, 0.0001)
        assertNull(DriverCsv.number(row["fees"]))
    }

    @Test fun rejectsMissingRequiredHeaders() {
        assertThrows(IllegalArgumentException::class.java) {
            DriverCsv.parse("platform,gross\nUber,10")
        }
    }

    @Test fun rejectsDuplicateHeadersAfterNormalisation() {
        assertThrows(IllegalArgumentException::class.java) {
            DriverCsv.parse("platform,record_type,PLATFORM\nUber,trip,Uber")
        }
    }

    @Test fun rejectsExtraValuesInsteadOfSilentlyDiscardingThem() {
        assertThrows(IllegalArgumentException::class.java) {
            DriverCsv.parse("platform,record_type,gross\nUber,trip,42.50,unexpected")
        }
    }

    @Test fun rejectsRowsMissingRequiredValues() {
        assertThrows(IllegalArgumentException::class.java) {
            DriverCsv.parse("platform,record_type,gross\nUber,,42.50")
        }
    }

    @Test fun normalisesHeadersIndependentOfDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val row = DriverCsv.parse("PLATFORM,RECORD_TYPE,EXTERNAL_ID\nUber,trip,ride-1").single()
            assertEquals("ride-1", row["external_id"])
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun acceptsCommaDecimalAmounts() {
        assertEquals(12.75, DriverCsv.number("12,75")!!, 0.0001)
    }
}
