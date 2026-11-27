package com.ysfclc.busybee.drivercommandcentre

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DriverCommandStoreDowngradeTest {
    private val context = RuntimeEnvironment.getApplication()
    private var store: DriverCommandStore? = null

    @Before
    fun setUp() {
        context.deleteDatabase("busybee_command_centre.db")
    }

    @After
    fun tearDown() {
        store?.close()
        context.deleteDatabase("busybee_command_centre.db")
    }

    @Test
    fun newerDatabaseVersionIsOpenedWithoutDeletingExistingOrUnknownData() {
        val original = DriverCommandStore(context)
        original.saveOffer(
            DriverOffer(
                platform = "bolt",
                fare = 24.5,
                pickupKm = 1.2,
                tripKm = 8.4,
                durationMin = 22.0
            )
        )
        original.close()

        // Simulate a prior app build that advanced SQLite user_version and added
        // an unrelated table. A downgrade must preserve both, not reset the DB.
        val futureDb = context.openOrCreateDatabase(
            "busybee_command_centre.db",
            Context.MODE_PRIVATE,
            null
        )
        futureDb.execSQL("CREATE TABLE future_build_data(id INTEGER PRIMARY KEY, payload TEXT)")
        futureDb.execSQL("INSERT INTO future_build_data(payload) VALUES('keep-me')")
        futureDb.version = 4
        futureDb.close()

        store = DriverCommandStore(context)
        val current = store!!

        assertEquals(1, current.offers().size)
        assertEquals("bolt", current.offers().single().platform)
        assertEquals(3, current.readableDatabase.version)

        current.readableDatabase.rawQuery(
            "SELECT payload FROM future_build_data",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("keep-me", cursor.getString(0))
        }

        // All tables used by the current map-first screen remain queryable.
        assertTrue(current.roadReports().isEmpty())
        assertTrue(current.notices().isEmpty())
        assertTrue(current.items().isEmpty())
        assertTrue(current.whatsappGroups().isEmpty())
    }
}
