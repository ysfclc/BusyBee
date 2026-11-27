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
    fun legacyCrashReportWithExceptionMessageIsNotCopiedIntoDiagnostics() {
        java.io.File(context.filesDir, "busybee-last-crash.txt").writeText("Legacy exception: private destination text")
        val report = CrashDiagnostics.snapshot(context)
        assertFalse(report.contains("private destination text"))
        assertTrue(report.contains("No uncaught JVM exception was captured"))
    }

    @Test
    fun caughtActionFailureStoresStackFramesWithoutExceptionMessage() {
        val error = IllegalStateException("sensitive destination text")
        CrashDiagnostics.recordActionFailure(context, "open map", error)
        val report = CrashDiagnostics.snapshot(context)
        assertTrue(report.contains("Action: open map"))
        assertTrue(report.contains("IllegalStateException"))
        assertTrue(report.contains("CrashDiagnosticsTest"))
        assertFalse(report.contains("sensitive destination text"))
    }

    @Test
    fun startupFailureCanBeCopiedWithoutExceptionMessage() {
        CrashDiagnostics.recordStartupFailure(context, IllegalStateException("private destination text"))

        val report = CrashDiagnostics.snapshot(context)

        assertTrue(report.contains("IllegalStateException"))
        assertTrue(report.contains("CrashDiagnosticsTest"))
        assertTrue(report.contains("STACK FRAMES"))
        assertFalse(report.contains("private destination text"))
    }

    @Test
    fun reportExplainsWhenNoJvmStackTraceWasCaptured() {
        val report = CrashDiagnostics.snapshot(context)

        assertTrue(report.contains("No uncaught JVM exception was captured"))
        assertTrue(report.contains("RECENT APP EVENTS"))
    }
}
