package com.ysfclc.busybee.drivercommandcentre

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

internal object DriverAlertManager {
    const val EXTRA_OPEN_TAB = "busybee_open_tab"
    private const val CHANNEL_ID = "busybee_driver_signals"
    private const val PREFERENCES = "busybee_alert_preferences"
    private const val ALERTS_ENABLED = "driver_alerts_enabled"

    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    fun setAlertsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(ALERTS_ENABLED, enabled).apply()
    }

    fun alertsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(ALERTS_ENABLED, true)

    fun notifyDriverAppUpdate(context: Context, platform: String, screenType: String, source: String) {
        if (!alertsEnabled(context) || !notificationsAllowed(context)) return
        if (screenType == "DRIVER SCREEN" || screenType == "UPDATE") return

        val openTab = when (screenType) {
            "OFFER" -> "OFFERS"
            "TRIP", "EARNINGS", "PAYOUT", "ADJUSTMENT" -> "EARNINGS"
            else -> "OFFERS"
        }
        val detail = when (screenType) {
            "OFFER" -> "New offer information detected. Open BusyBee to review it."
            "TRIP" -> "Trip information changed. Review the captured details."
            "EARNINGS", "PAYOUT", "ADJUSTMENT" -> "Earnings information changed. Review the captured values."
            else -> "A new driver-app update is ready for review."
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "BusyBee driver updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reviewable updates detected from driver apps"
                    setShowBadge(true)
                }
            )
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_TAB, openTab)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            openTab.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$platform driver update")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$detail\nSource: $source · captured locally · review required"))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // A stable per-platform/workspace ID updates an existing alert instead of
        // flooding the shade while the driver app emits several related UI events.
        notificationManager.notify((platform + openTab).hashCode(), notification)
    }

    fun classifyNotification(title: String, body: String): String {
        val text = "$title\n$body".lowercase()
        return when {
            listOf("earnings", "payout", "payment", "weekly total", "today total", "zarobki", "wypłata", "rozliczenie").any(text::contains) -> "EARNINGS"
            listOf("new trip", "new ride", "offer", "request", "pickup", "accept", "nowa oferta", "nowy kurs", "odbiór", "zaakceptuj").any(text::contains) -> "OFFER"
            listOf("trip complete", "ride complete", "completed trip", "przejazd zakończony", "ukończono kurs").any(text::contains) -> "TRIP"
            else -> "UPDATE"
        }
    }
}
