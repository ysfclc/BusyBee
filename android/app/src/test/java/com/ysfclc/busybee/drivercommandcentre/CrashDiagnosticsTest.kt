package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CrashDiagnosticsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetDiagnostics() {
        CrashDiagnostics.clear(context)
    }

    @Test
    fun breadcrumbHistoryIsBoundedAndKeepsTheNewestActions() {
        repeat(60) { CrashDiagnostics.record(context, "tap-$it") }

        val report = CrashDiagnostics.snapshot(context)

        assertTrue(report.contains("tap-59"))
        assertTrue(report.contains("tap-12"))
        assertFalse(report.contains("tap-11"))
        assertFalse(report.contains("tap-0"))
    }

    @Test
    fun reportExplainsWhenNoJvmStackTraceWasCaptured() {
        val report = CrashDiagnostics.snapshot(context)

        assertTrue(report.contains("No uncaught JVM exception was captured"))
        assertTrue(report.contains("RECENT APP EVENTS"))
    }
}
