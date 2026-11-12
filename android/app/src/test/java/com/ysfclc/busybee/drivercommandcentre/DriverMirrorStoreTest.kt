package com.ysfclc.busybee.drivercommandcentre

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DriverMirrorStoreTest {
    private val databaseName = "busybee_driver_mirror.db"

    private fun newStore(): DriverMirrorStore {
        val context = RuntimeEnvironment.getApplication<Application>()
        context.deleteDatabase(databaseName)
        return DriverMirrorStore(context)
    }

    @Test
    fun retainsEveryRawImportRowWhileDeduplicatingCanonicalRecordsAcrossBatches() {
        val store = newStore()
        try {
            val trip = mapOf(
                "platform" to "Uber",
                "record_type" to "trip",
                "external_id" to "ride-123",
                "occurred_at" to "2026-11-12T10:15:00+01:00",
                "pickup" to "Old Town, Krakow",
                "dropoff" to "Kazimierz",
                "distance_km" to "8.4",
                "gross" to "42.50",
                "fees" to "8.50",
                "bonus" to "2.00",
                "currency" to "PLN"
            )

            assertEquals(2 to 1, store.importRows("uber-export.csv", listOf(trip, trip)))
            assertEquals(1 to 0, store.importRows("uber-export-resend.csv", listOf(trip)))

            val summary = store.summary()
            assertEquals(1, summary.records)
            assertEquals(1, summary.trips)
            assertEquals(2, summary.imports)

            val evidence = store.evidencePreview(10)
            assertEquals(3, evidence.size)
            assertTrue(evidence.any { it.startsWith("uber-export.csv · row 1") })
            assertTrue(evidence.any { it.startsWith("uber-export.csv · row 2") })
            assertTrue(evidence.any { it.startsWith("uber-export-resend.csv · row 1") })
            assertTrue(evidence.any { it.contains("pickup=Old Town, Krakow") })

            val history = store.importHistory()
            assertEquals(2, history.size)
            assertTrue(history.any { it.contains("1 new / 2 rows") })
            assertTrue(history.any { it.contains("0 new / 1 rows") })
        } finally {
            store.close()
            RuntimeEnvironment.getApplication<Application>().deleteDatabase(databaseName)
        }
    }

    @Test
    fun rollsBackImportBatchAndEvidenceWhenARowFailsMidTransaction() {
        val store = newStore()
        try {
            val validTrip = mapOf(
                "platform" to "Bolt",
                "record_type" to "trip",
                "external_id" to "bolt-456",
                "gross" to "30.00"
            )
            val failingRow = object : Map<String, String> {
                override val entries: Set<Map.Entry<String, String>> = emptySet()
                override val keys: Set<String> = emptySet()
                override val values: Collection<String> = emptyList()
                override val size: Int = 0
                override fun containsKey(key: String) = false
                override fun containsValue(value: String) = false
                override fun isEmpty() = true
                override fun get(key: String): String? = throw IllegalStateException("injected row failure")
            }

            assertThrows(IllegalStateException::class.java) {
                store.importRows("broken-batch.csv", listOf(validTrip, failingRow))
            }

            assertEquals(0, store.summary().records)
            assertEquals(0, store.summary().imports)
            assertTrue(store.importHistory().isEmpty())
            assertTrue(store.evidencePreview(10).isEmpty())
        } finally {
            store.close()
            RuntimeEnvironment.getApplication<Application>().deleteDatabase(databaseName)
        }
    }
}
