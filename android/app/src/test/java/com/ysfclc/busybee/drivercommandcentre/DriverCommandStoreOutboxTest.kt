package com.ysfclc.busybee.drivercommandcentre

import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DriverCommandStoreOutboxTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun cleanBefore() {
        context.deleteDatabase("busybee_command_centre.db")
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase("busybee_command_centre.db")
    }

    @Test
    fun versionTwoDatabaseUpgradesAdditivelyAndKeepsExistingOffers() {
        val file: File = context.getDatabasePath("busybee_command_centre.db")
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        legacy.execSQL(
            """CREATE TABLE offers(
                id INTEGER PRIMARY KEY AUTOINCREMENT, platform TEXT NOT NULL, pickup TEXT,
                destination TEXT, fare REAL NOT NULL, bonus REAL NOT NULL, pickup_km REAL NOT NULL,
                trip_km REAL NOT NULL, duration_min REAL NOT NULL, passenger_rating REAL,
                captured_at INTEGER NOT NULL, source TEXT NOT NULL, raw TEXT, status TEXT NOT NULL
            )"""
        )
        legacy.execSQL(
            """INSERT INTO offers(platform,pickup,destination,fare,bonus,pickup_km,trip_km,
                 duration_min,passenger_rating,captured_at,source,raw,status)
               VALUES('Uber','Centre','Airport',20,2,1,10,20,4.9,1700000000000,'manual','legacy row','pending')"""
        )
        legacy.version = 2
        legacy.close()

        DriverCommandStore(context).use { upgraded ->
            assertEquals(3, upgraded.writableDatabase.version)
            val saved = upgraded.offers().single()
            assertEquals("legacy row", saved.raw)
            assertEquals("pending", saved.status)
            assertEquals(0, upgraded.pendingProviderOfferEventCount())

            val added = upgraded.saveOfferWithPendingEvent(
                DriverOffer(
                    platform = "Bolt",
                    pickup = "Old Town",
                    destination = "Station",
                    fare = 18.0,
                    pickupKm = 1.0,
                    tripKm = 7.0,
                    durationMin = 15.0,
                    capturedAt = 1_700_000_100_000L,
                    source = "manual"
                )
            )
            assertTrue(added.id > saved.id)
            assertEquals(1, upgraded.pendingProviderOfferEventCount())
        }
    }
    @Test
    fun malformedPendingEventDoesNotBlockLaterValidEvents() {
        DriverCommandStore(context).use { store ->
            store.saveOfferWithPendingEvent(
                DriverOffer(
                    platform = "Uber",
                    fare = 20.0,
                    pickupKm = 1.0,
                    tripKm = 8.0,
                    durationMin = 20.0,
                    capturedAt = 1_700_000_000_000L
                )
            )
            val malformedOutboxId = store.latestProviderOfferOutboxId()
            store.writableDatabase.execSQL(
                "UPDATE provider_event_outbox SET event_type=? WHERE id=?",
                arrayOf("UNKNOWN_EVENT", malformedOutboxId)
            )

            val healthyOffer = store.saveOfferWithPendingEvent(
                DriverOffer(
                    platform = "Bolt",
                    fare = 18.0,
                    pickupKm = 0.5,
                    tripKm = 6.0,
                    durationMin = 15.0,
                    capturedAt = 1_700_000_100_000L
                )
            )
            val pending = store.pendingProviderOfferEvents(store.latestProviderOfferOutboxId())

            assertEquals(2, pending.size)
            val malformed = pending.single { it.outboxId == malformedOutboxId }
            assertNull(malformed.event)
            assertTrue(malformed.failureReason.orEmpty().contains("UNKNOWN_EVENT"))

            val healthy = pending.single { it.outboxId != malformedOutboxId }
            assertNotNull(healthy.event)
            assertEquals(ProviderEventType.OFFER_OBSERVED, healthy.event?.eventType)
            assertTrue(healthy.event?.idempotencyKey.orEmpty().contains(":" + healthyOffer.id + ":observed"))
        }
    }

}
