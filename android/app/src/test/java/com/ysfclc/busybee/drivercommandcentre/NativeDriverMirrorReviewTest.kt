package com.ysfclc.busybee.drivercommandcentre

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NativeDriverMirrorReviewTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var store: DriverMirrorStore

    @Before
    fun setUp() {
        context.deleteDatabase("busybee_driver_mirror.db")
        store = DriverMirrorStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase("busybee_driver_mirror.db")
    }

    @Test
    fun confirmedTripKeepsDurationAndOriginalOcrEvidence() {
        val id = store.saveReviewedNativeObservation(
            platform = "Uber",
            recordType = "trip",
            capturedAt = 1_800_000_000_000L,
            visibleText = "TRIP COMPLETE\nFare 42.50 PLN\n8.4 km\n23 min",
            gross = 42.50,
            fees = 8.50,
            bonus = 0.0,
            distanceKm = 8.4,
            durationMinutes = 23.0,
            pickup = "Main Street 12, Kraków",
            dropoff = "Rynek Główny, Kraków",
            sourceName = "Android live UI listener"
        )

        assertTrue(id > 0)
        val record = store.recent().single()
        assertEquals(23.0, record.durationMinutes ?: 0.0, 0.001)
        assertEquals(8.4, record.distanceKm ?: 0.0, 0.001)
        assertEquals("Main Street 12, Kraków", record.pickup)
        assertEquals("Rynek Główny, Kraków", record.dropoff)
        assertEquals("Android live UI listener", record.sourceName)
        assertEquals("driver-reviewed", record.status)
        assertEquals(34.0, store.summary().knownNet ?: 0.0, 0.001)
        assertTrue(store.evidencePreview().single().contains("TRIP COMPLETE"))
    }

    @Test
    fun incompleteConfirmedTripDoesNotInventFeesOrKnownNet() {
        store.saveReviewedNativeObservation(
            platform = "Bolt",
            recordType = "trip",
            capturedAt = 1_800_000_000_000L,
            visibleText = "COMPLETED RIDE\n42.50 PLN",
            gross = 42.50,
            fees = null,
            bonus = null,
            distanceKm = null,
            durationMinutes = null
        )

        val summary = store.summary()
        assertFalse(summary.netComplete)
        assertNull(summary.knownNet)
        assertEquals(42.50, summary.gross ?: 0.0, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedPlatformInsteadOfMisattributingData() {
        store.saveReviewedNativeObservation(
            platform = "?",
            recordType = "trip",
            capturedAt = 1_800_000_000_000L,
            visibleText = "trip complete",
            gross = null,
            fees = null,
            bonus = null,
            distanceKm = null,
            durationMinutes = null
        )
    }
}
