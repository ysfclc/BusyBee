package com.ysfclc.busybee.drivercommandcentre

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DriverAppCaptureStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var store: DriverAppCaptureStore

    @Before
    fun setUp() {
        context.deleteDatabase("busybee_native_app_capture.db")
        store = DriverAppCaptureStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase("busybee_native_app_capture.db")
    }

    @Test
    fun storesLiveUiSourcePickupDropoffAndParserConfidence() {
        val parsed = DriverScreenTextParser.parse(
            platform = "Uber",
            text = "TRIP DETAILS\nPickup\nMain Street 12, Kraków\nDrop-off: Rynek Główny, Kraków\nFare 42.50 PLN\nDistance 8.4 km\nDuration 23 min",
            capturedAt = 1_800_000_000_000L,
            sourceType = "ACCESSIBILITY_UI"
        )!!

        val id = store.save(parsed)
        assertTrue(id > 0L)

        val observation = store.recent().single()
        assertEquals("ACCESSIBILITY_UI", observation.sourceType)
        assertEquals("Main Street 12, Kraków", observation.pickup)
        assertEquals("Rynek Główny, Kraków", observation.dropoff)
        assertEquals(0.92, observation.parseConfidence, 0.001)
        assertTrue(observation.visibleText.contains("TRIP DETAILS"))
    }

    @Test
    fun legacyStyleObservationDefaultsToManualOcrAndZeroConfidence() {
        val parsed = DriverScreenTextParser.parse(
            platform = "Bolt",
            text = "Weekly earnings\nToday total\n42.50",
            capturedAt = 1_800_000_000_000L
        )!!

        store.save(parsed)
        val observation = store.recent().single()
        assertEquals("MEDIA_PROJECTION_OCR", observation.sourceType)
        assertEquals(0.56, observation.parseConfidence, 0.001)
    }
}
