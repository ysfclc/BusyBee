package com.ysfclc.busybee.drivercommandcentre

import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UnifiedActivityReadCoordinatorTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sourceName = UnifiedActivityLedgerSchema.DATABASE_NAME
    private val targetName = "busybee_driver_mirror.db"

    @Before
    fun cleanBefore() = clean()

    @After
    fun cleanAfter() = clean()

    @Test
    fun defaultReadUsesOriginalLedgerAndDoesNotCreateMirror() {
        createSourceFixture()

        val result = UnifiedActivityReadCoordinator(context).timeline()

        assertEquals(UnifiedActivityReadSource.ORIGINAL_LEDGER, result.source)
        assertNull(result.fallbackReason)
        assertEquals(1, result.items.size)
        assertFalse(context.getDatabasePath(targetName).exists())
    }

    @Test
    fun preferredMirrorFallsBackWhenTargetDoesNotExistWithoutCreatingIt() {
        createSourceFixture()

        val result = UnifiedActivityReadCoordinator(context, preferDriverMirror = true).timeline()

        assertEquals(UnifiedActivityReadSource.ORIGINAL_LEDGER, result.source)
        assertNotNull(result.fallbackReason)
        assertTrue(result.fallbackReason!!.contains("database is missing"))
        assertTrue(result.parity?.missingTarget == true)
        assertFalse(context.getDatabasePath(targetName).exists())
        assertEquals(1, result.items.size)
    }

    @Test
    fun preferredMirrorIsUsedOnlyAfterCompleteParity() {
        createSourceFixture()
        ActivityLedgerConsolidator(context).use { assertTrue(it.consolidateIfNeeded().complete) }

        val result = UnifiedActivityReadCoordinator(context, preferDriverMirror = true).timeline()

        assertEquals(UnifiedActivityReadSource.DRIVER_MIRROR, result.source)
        assertNull(result.fallbackReason)
        assertTrue(result.parity?.complete == true)
        assertEquals(1, result.items.size)
    }

    @Test
    fun preferredMirrorFallsBackWhenTargetPayloadNoLongerMatches() {
        createSourceFixture()
        ActivityLedgerConsolidator(context).use { assertTrue(it.consolidateIfNeeded().complete) }
        val mirror = DriverMirrorStore(context)
        mirror.writableDatabase.execSQL("UPDATE activity_observations SET status='tampered'")
        mirror.close()

        val result = UnifiedActivityReadCoordinator(context, preferDriverMirror = true).timeline()
        val original = UnifiedActivityRepository(context, UnifiedActivityReadSource.ORIGINAL_LEDGER).use {
            it.timeline()
        }

        assertEquals(UnifiedActivityReadSource.ORIGINAL_LEDGER, result.source)
        assertNotNull(result.fallbackReason)
        assertFalse(result.parity?.complete == true)
        assertEquals(original, result.items)
        assertEquals("new", result.items.single().status)
    }

    private fun createSourceFixture() {
        val ledger = UnifiedActivityLedger(context)
        try {
            ledger.writableDatabase.execSQL(
                """INSERT INTO activity_observations
                   (origin_key,provider,observation_kind,source_method,source_occurred_at_ms,
                    received_at_ms,classification,status,amount,currency,pickup,destination,distance_km,duration_min)
                   VALUES('notice:coordinator','Uber','notification','ANDROID_NOTIFICATION',NULL,1000,'unclassified',
                          'new',12.5,'PLN','Old Town','Airport',8.2,20)"""
            )
        } finally {
            ledger.close()
        }
    }

    private fun clean() {
        context.deleteDatabase(sourceName)
        context.deleteDatabase(targetName)
    }
}
