package com.ysfclc.appobserver

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import java.util.Locale

data class ObservedApp(val label: String, val packageName: String) {
    override fun toString(): String = "$label · $packageName"
}

/**
 * Discovers launchable applications dynamically. There is deliberately no provider allowlist.
 * Android package visibility means this returns apps exposed through the launcher-intent query,
 * not every hidden/system package on the device.
 */
object AppCatalog {
    @Suppress("DEPRECATION")
    fun installedLaunchableApps(
        packageManager: PackageManager,
        observerPackage: String
    ): List<ObservedApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        return resolved.mapNotNull { result ->
            val activity = result.activityInfo ?: return@mapNotNull null
            val name = activity.packageName
            if (name == observerPackage || name.isBlank()) return@mapNotNull null
            val label = runCatching { activity.loadLabel(packageManager).toString() }
                .getOrDefault(name)
                .ifBlank { name }
            ObservedApp(label, name)
        }.distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.packageName })
    }

    fun isSessionTarget(packageName: String?, selectedPackage: String?): Boolean =
        !packageName.isNullOrBlank() && packageName == selectedPackage

    fun eventName(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "window_state_changed"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "window_content_changed"
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "view_scrolled"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "view_clicked"
        AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "view_long_clicked"
        AccessibilityEvent.TYPE_VIEW_SELECTED -> "view_selected"
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> "view_focused"
        else -> "other"
    }

    const val CONTENT_POLICY =
        "Only the app explicitly selected for a manually started session is observed. " +
        "Visible text, typed values, screenshots, clipboard contents, notification bodies, " +
        "network payloads, IP addresses and raw packet captures are not retained."
}
