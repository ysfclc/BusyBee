package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCoverageCalculatorTest {
    @Test
    fun combinesNotificationOfferAndImportCoverageWithoutClaimingApiConnection() {
        val baseTime = 1_700_000_000_000L
        val notifications = listOf(
            CapturedNotice(1L, "Uber Driver", "New offer", "Offer visible", baseTime + 100L),
            CapturedNotice(2L, "Taxify", "Ride", "Ride visible", baseTime + 200L)
        )
        val offers = listOf(
            offer("Uber", "manual", baseTime + 300L),
            offer("uber", "notification-review", baseTime + 400L),
            offer("Bolt", "manual", baseTime + 500L)
        )
        val records = listOf(
            record(1L, "UBER", "trip"),
            record(2L, "Free Now", "earning")
        )

        val summaries = ProviderCoverageCalculator.summarize(
            notifications = notifications,
            offers = offers,
            importedRecords = records,
            notificationAccessGranted = true
        )
        val uber = summaries.single { it.provider == "Uber" }
        val bolt = summaries.single { it.provider == "Bolt" }
        val freeNow = summaries.single { it.provider == "FreeNow" }

        assertEquals(ProviderCoverageState.LOCAL_INPUTS_PRESENT, uber.state)
        assertEquals(1, uber.visibleNotificationCount)
        assertEquals(1, uber.manualOfferCount)
        assertEquals(1, uber.notificationReviewedOfferCount)
        assertEquals(1, uber.importedRecordCount)
        assertEquals(baseTime + 400L, uber.lastLocalRecordAtMillis)
        assertTrue(uber.dataGaps.any { it.contains("No verified direct provider API") })

        assertEquals(1, bolt.visibleNotificationCount)
        assertEquals(1, bolt.manualOfferCount)
        assertEquals(0, bolt.importedRecordCount)
        assertEquals(baseTime + 500L, bolt.lastLocalRecordAtMillis)

        assertEquals(1, freeNow.importedRecordCount)
        assertEquals(0, freeNow.visibleNotificationCount)
    }

    @Test
    fun reportsPermissionAndDataGapsWhenNoProviderEvidenceExists() {
        val summaries = ProviderCoverageCalculator.summarize(
            notifications = emptyList(),
            offers = emptyList(),
            importedRecords = emptyList(),
            notificationAccessGranted = false
        )
        val uber = summaries.first { it.provider == "Uber" }

        assertEquals(ProviderCoverageState.PERMISSION_REQUIRED, uber.state)
        assertEquals(0, uber.visibleNotificationCount)
        assertEquals(0, uber.importedRecordCount)
        assertTrue(uber.dataGaps.any { it.contains("notification access is not granted") })
        assertTrue(uber.dataGaps.any { it.contains("No structured offer") })
        assertTrue(uber.dataGaps.any { it.contains("No statement or history") })
    }

    @Test
    fun exposesUnknownProvidersWithoutDiscardingThem() {
        val summaries = ProviderCoverageCalculator.summarize(
            notifications = listOf(CapturedNotice(1L, "CityCab", "Trip", "Visible", 10L)),
            offers = emptyList(),
            importedRecords = emptyList(),
            notificationAccessGranted = true
        )

        val custom = summaries.single { it.provider == "CityCab" }
        assertEquals(1, custom.visibleNotificationCount)
        assertEquals(ProviderCoverageState.LOCAL_INPUTS_PRESENT, custom.state)
    }

    private fun offer(platform: String, source: String, capturedAt: Long) = DriverOffer(
        platform = platform,
        pickup = "Centre",
        destination = "Airport",
        fare = 25.0,
        pickupKm = 2.0,
        tripKm = 10.0,
        durationMin = 20.0,
        capturedAt = capturedAt,
        source = source
    )

    private fun record(id: Long, platform: String, type: String) = DriverRecord(
        id = id,
        platform = platform,
        type = type,
        externalId = "record-$id",
        occurredAt = null,
        pickup = "Centre",
        dropoff = "Airport",
        distanceKm = 10.0,
        gross = 25.0,
        fees = null,
        bonus = null,
        currency = "PLN",
        status = null,
        raw = "source row",
        sourceName = "statement.csv"
    )
}
