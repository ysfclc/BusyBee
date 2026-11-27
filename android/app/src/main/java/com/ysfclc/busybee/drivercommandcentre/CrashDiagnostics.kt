package com.ysfclc.busybee.drivercommandcentre

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import kotlin.system.exitProcess

/**
 * Stores bounded on-device breadcrumbs and exception stack frames.
 * Exception messages are omitted so destinations, earnings and imported content are not copied.
 */
internal object CrashDiagnostics {
    private const val PREFS = "busybee_crash_diagnostics"
    private const val EVENTS_KEY = "recent_events"
    private const val CRASH_FILE = "busybee-last-crash.txt"
    private const val ACTION_FAILURE_FILE = "busybee-last-action-failure.txt"
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

    fun recordStartupFailure(context: Context, error: Throwable) {
        persistCrashReport(context, Thread.currentThread(), error)
        record(context, "startup:recovery-screen")
    }

    fun recordActionFailure(context: Context, action: String, error: Throwable) {
        try {
            val frames = formatStackFrames(error)
            val report = buildString {
                appendLine("Action: ${action.take(100)}")
                appendLine("Exception: ${error.javaClass.name}")
                appendLine("Exception messages intentionally omitted to avoid copying destinations or imported content.")
                appendLine(frames)
            }
            File(context.applicationContext.filesDir, ACTION_FAILURE_FILE).writeText(report)
        } catch (_: Throwable) {
            // Diagnostics must not alter the failed action or process state.
        }
    }

    private fun formatStackFrames(error: Throwable): String = buildString {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 4) {
            appendLine("Cause $depth: ${current.javaClass.name}")
            current.stackTrace.take(18).forEach { frame ->
                appendLine("  at ${frame.className}.${frame.methodName}(${frame.fileName ?: "Unknown"}:${frame.lineNumber})")
            }
            current = current.cause
            depth++
        }
    }.take(7000)

    fun snapshot(context: Context): String {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = prefs.getString(EVENTS_KEY, "").orEmpty()
        val crash = runCatching {
            File(app.filesDir, CRASH_FILE).takeIf { it.isFile }?.readText()
                ?.takeIf { it.contains("STACK FRAMES (exception messages omitted)") }
        }.getOrNull()
        val actionFailure = runCatching { File(app.filesDir, ACTION_FAILURE_FILE).takeIf { it.isFile }?.readText() }.getOrNull()
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
            appendLine("LAST ACTION FAILURE (stack frames only; exception messages are omitted)")
            appendLine(actionFailure?.takeIf { it.isNotBlank() } ?: "No caught UI action failure was captured.")
            appendLine()
            appendLine("RECENT APP EVENTS (newest last)")
            appendLine(events.ifBlank { "No events recorded." })
        }
    }

    fun clear(context: Context) {
        try {
            File(context.applicationContext.filesDir, CRASH_FILE).delete()
            File(context.applicationContext.filesDir, ACTION_FAILURE_FILE).delete()
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        } catch (_: Exception) {
            // A failed cleanup must not affect normal app operation.
        }
    }

    private fun persistCrashReport(context: Context, thread: Thread, error: Throwable) {
        try {
            val app = context.applicationContext
            val safeFrames = formatStackFrames(error)
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
                appendLine("STACK FRAMES (exception messages omitted)")
                appendLine(error.javaClass.name)
                appendLine(safeFrames)
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
