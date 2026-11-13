package com.ysfclc.busybee.drivercommandcentre

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Stores a small, on-device breadcrumb trail and the last uncaught JVM exception.
 * It deliberately records action labels only, never destinations, earnings or imported content.
 */
internal object CrashDiagnostics {
    private const val PREFS = "busybee_crash_diagnostics"
    private const val EVENTS_KEY = "recent_events"
    private const val CRASH_FILE = "busybee-last-crash.txt"
    private const val MAX_EVENTS = 48
    private val lock = Any()

    @Volatile
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            persistCrashReport(app, thread, error)
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
        installed = true
    }

    fun record(context: Context, event: String) {
        try {
            synchronized(lock) {
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val previous = prefs.getString(EVENTS_KEY, "").orEmpty()
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .toList()
                val safeEvent = event.replace('\n', ' ').replace('\r', ' ').take(180)
                val next = (previous + "${System.currentTimeMillis()} ${safeEvent}").takeLast(MAX_EVENTS)
                prefs.edit().putString(EVENTS_KEY, next.joinToString("\n")).commit()
            }
        } catch (_: Exception) {
            // Diagnostics must never interrupt the driver workflow.
        }
    }

    fun snapshot(context: Context): String {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = prefs.getString(EVENTS_KEY, "").orEmpty()
        val crash = runCatching { File(app.filesDir, CRASH_FILE).takeIf { it.isFile }?.readText() }.getOrNull()
        @Suppress("DEPRECATION")
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
            ?: "unknown"

        return buildString {
            appendLine("BusyBee on-device diagnostics")
            appendLine("App version: $version")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("LAST JVM CRASH")
            appendLine(crash?.takeIf { it.isNotBlank() } ?: "No uncaught JVM exception was captured. A native renderer crash or OS process kill may not produce a Java stack trace.")
            appendLine()
            appendLine("RECENT APP EVENTS (newest last)")
            appendLine(events.ifBlank { "No events recorded." })
        }
    }

    fun clear(context: Context) {
        try {
            File(context.applicationContext.filesDir, CRASH_FILE).delete()
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        } catch (_: Exception) {
            // A failed cleanup must not affect normal app operation.
        }
    }

    private fun persistCrashReport(context: Context, thread: Thread, error: Throwable) {
        try {
            val app = context.applicationContext
            val writer = StringWriter()
            error.printStackTrace(PrintWriter(writer))
            @Suppress("DEPRECATION")
            val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                ?: "unknown"
            val events = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(EVENTS_KEY, "").orEmpty()
            val report = buildString {
                appendLine("BusyBee uncaught JVM exception")
                appendLine("App version: $version")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Thread: ${thread.name}")
                appendLine()
                appendLine("STACK TRACE")
                appendLine(writer.toString())
                appendLine()
                appendLine("RECENT APP EVENTS")
                appendLine(events)
            }
            val target = File(app.filesDir, CRASH_FILE)
            val temporary = File(app.filesDir, "$CRASH_FILE.tmp")
            temporary.writeText(report)
            if (!temporary.renameTo(target)) {
                target.writeText(report)
                temporary.delete()
            }
        } catch (_: Throwable) {
            // Preserve the original process crash even if diagnostics storage fails.
        }
    }
}
