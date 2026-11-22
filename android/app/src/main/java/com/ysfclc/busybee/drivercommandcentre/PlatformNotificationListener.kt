package com.ysfclc.busybee.drivercommandcentre

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors

class PlatformNotificationListener : NotificationListenerService() {
    private val ledgerSyncExecutor = Executors.newSingleThreadExecutor()
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName.lowercase()
        val platform = when {
            pkg.contains("uber") -> "Uber"
            pkg.contains("bolt") || pkg.contains("taxify") -> "Bolt"
            pkg.contains("freenow") || pkg.contains("mytaxi") -> "FreeNow"
            pkg.contains("itaxi") -> "iTaxi"
            pkg.contains("cabify") -> "Cabify"
            pkg.contains("lyft") -> "Lyft"
            else -> return
        }
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val body = listOf(
            extras.getCharSequence("android.text")?.toString(),
            extras.getCharSequence("android.bigText")?.toString(),
            extras.getCharSequence("android.subText")?.toString()
        ).filterNotNull().filter { it.isNotBlank() }.distinct().joinToString("\n")
        if (title.isBlank() && body.isBlank()) return
        val insertedId = DriverCommandStore(applicationContext).captureNotice(platform, title, body, sbn.postTime)
        if (insertedId > 0L) {
            ledgerSyncExecutor.execute {
                try {
                    UnifiedActivityLedgerMigrator(applicationContext).migrateIfNeeded()
                } catch (error: Exception) {
                    android.util.Log.w("BusyBeeLedger", "Notification ledger sync failed", error)
                }
            }
        }
    }

    override fun onDestroy() {
        ledgerSyncExecutor.shutdown()
        super.onDestroy()
    }
}
