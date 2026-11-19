package com.ysfclc.appobserver

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ObserverReport {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private val watchedPermissions = listOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_PHONE_STATE"
    )

    fun build(context: Context): JSONObject {
        ObserverSignals.ensureSession(context)
        val apps = JSONArray()
        AppCatalog.apps.forEach { apps.put(appInfo(context, it)) }
        val usageEnabled = hasUsageAccess(context)
        val notificationEnabled = isComponentEnabled(
            context, ObserverNotificationListener::class.java, "enabled_notification_listeners"
        )
        val accessibilityEnabled = isComponentEnabled(
            context, ObserverAccessibilityService::class.java, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        return JSONObject()
            .put("schema", "app-observer-report/v1")
            .put("generatedAt", iso(System.currentTimeMillis()))
            .put("observationStartedAt", iso(ObserverSignals.sessionStartedAt(context)))
            .put("device", JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("androidRelease", Build.VERSION.RELEASE)
                .put("apiLevel", Build.VERSION.SDK_INT)
                .put("adbEnabled", Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1))
            .put("androidAccess", JSONObject()
                .put("usageAccessEnabled", usageEnabled)
                .put("notificationAccessEnabled", notificationEnabled)
                .put("notificationServiceConnected", ObserverSignals.serviceConnected(context, "notifications"))
                .put("accessibilityEnabled", accessibilityEnabled)
                .put("accessibilityServiceConnected", ObserverSignals.serviceConnected(context, "accessibility")))
            .put("network", networkSummary(context))
            .put("apps", apps)
            .put("captureCapabilities", JSONObject()
                .put("installedAppInventory", "implemented")
                .put("appVersionAndSelectedPermissionInventory", "implemented")
                .put("usageHistory", if (usageEnabled) "available_with_permission" else "permission_required")
                .put("selectedAppUiTimeline", "implemented_during_explicit_session")
                .put("visibleUiText", "optional_explicit_opt_in_with_common_pattern_redaction")
                .put("selectedAppNotifications", "metadata_and_optional_redacted_title_text")
                .put("pcapdroidControlApi", "start_stop_status_via_user_consent")
                .put("perAppPacketCapture", "implemented_via_pcapdroid_localhost_udp_exporter")
                .put("packetExport", "standard_raw_ip_pcap_without_requiring_paid_metadata_extensions")
                .put("httpsPayloadDecryption", "requested_from_pcapdroid_when_opted_in_subject_to_app_tls_controls")
                .put("inProcessPlaintextObservation", "separate_owned_app_test_harness_only")
                .put("providerPrivateDatabaseAccess", "not_attempted")
                .put("thirdPartyAppActions", "not_implemented"))
            .put("privacy", JSONObject()
                .put("localOnly", true)
                .put("internetPermissionDeclared", true)
                .put("internetPermissionPurpose", "localhost UDP receive socket for PCAPdroid exporter only")
                .put("noOutboundUpload", true)
                .put("pcapdroidCollectorBindAddress", "127.0.0.1")
                .put("accessNetworkStatePermissionDeclared", true)
                .put("visibleTextRequiresSessionOptIn", true)
                .put("editableAndPasswordFieldsCaptured", false)
                .put("automaticRedactionIsComplete", false)
                .put("credentialsOrTokensIntentionallyCollected", false)
                .put("userCanResetCounters", true)
                .put("contentPolicy", AppCatalog.CONTENT_POLICY))
            .put("limitations", JSONArray()
                .put("Usage history is Android-provided history, not a guaranteed real-time event stream.")
                .put("UI and notification text is captured only for the selected app during a manually started session and only when the visible-text option is enabled.")
                .put("Editable and password fields are excluded; common phone, email and token patterns are masked, but names and addresses may remain.")
                .put("PCAPdroid must be installed and the user must approve its capture prompt; TLS pinning or app restrictions may prevent payload decryption.")
                .put("The packet capture includes full packet evidence when enabled and may contain personal or account data; inspect the ZIP before sharing.")
                .put("Packets are written to a standard PCAP file for offline analysis; this app does not replay requests or infer unknown payload schemas.")
                .put("Android and OEM background restrictions may interrupt one of the observation services; check the session event timeline."))
    }

    private fun appInfo(context: Context, app: ObservedApp): JSONObject {
        val signals = ObserverSignals.appSignals(context, app.packageName)
        return try {
            val packageInfo = getPackageInfo(context.packageManager, app.packageName)
            val appInfo = packageInfo.applicationInfo
            val requested = packageInfo.requestedPermissions?.toSet() ?: emptySet()
            val permissions = JSONObject()
            watchedPermissions.forEach { permission ->
                val isRequested = permission in requested
                permissions.put(permission.substringAfterLast('.'), JSONObject()
                    .put("requested", isRequested)
                    .put("granted", isRequested &&
                        context.packageManager.checkPermission(permission, app.packageName) == PackageManager.PERMISSION_GRANTED))
            }
            JSONObject()
                .put("label", app.label)
                .put("family", app.family)
                .put("packageName", app.packageName)
                .put("installed", true)
                .put("versionName", packageInfo.versionName ?: JSONObject.NULL)
                .put("versionCode", if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong())
                .put("targetSdk", appInfo?.targetSdkVersion ?: JSONObject.NULL)
                .put("debuggable", appInfo != null && (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                .put("requestedPermissionCount", requested.size)
                .put("watchedPermissions", permissions)
                .put("usage", usageSummary(context, app.packageName))
                .put("signalsSinceObservationStarted", signals)
        } catch (_: PackageManager.NameNotFoundException) {
            JSONObject()
                .put("label", app.label)
                .put("family", app.family)
                .put("packageName", app.packageName)
                .put("installed", false)
                .put("usage", usageSummary(context, app.packageName))
                .put("signalsSinceObservationStarted", signals)
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(pm: PackageManager, packageName: String) =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun usageSummary(context: Context, packageName: String): JSONObject {
        if (!hasUsageAccess(context)) return JSONObject()
            .put("available", false).put("reason", "usage_access_required")
        return try {
            val now = System.currentTimeMillis()
            val start = now - DAY_MS
            val manager = context.getSystemService(UsageStatsManager::class.java)
            val aggregate = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
                ?.filter { it.packageName == packageName }
                ?.maxByOrNull { it.lastTimeUsed }
            var foreground = 0
            var background = 0
            val events = manager.queryEvents(start, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> foreground++
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> background++
                }
            }
            JSONObject()
                .put("available", true)
                .put("windowHours", 24)
                .put("foregroundMilliseconds", aggregate?.totalTimeInForeground ?: 0L)
                .put("lastUsedAt", if ((aggregate?.lastTimeUsed ?: 0L) > 0L) iso(aggregate!!.lastTimeUsed) else JSONObject.NULL)
                .put("activityTransitions", JSONObject()
                    .put("foreground", foreground)
                    .put("background", background))
        } catch (error: RuntimeException) {
            JSONObject().put("available", false).put("reason", "query_failed:" + error.javaClass.simpleName)
        }
    }

    private fun isComponentEnabled(context: Context, type: Class<*>, setting: String): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, setting).orEmpty()
        val component = ComponentName(context, type)
        return enabled.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.contains(component)
    }

    private fun networkSummary(context: Context): JSONObject {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        val transports = JSONArray()
        if (capabilities != null) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.put("wifi")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports.put("cellular")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.put("ethernet")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transports.put("vpn")
        }
        return JSONObject()
            .put("activeNetworkAvailable", network != null)
            .put("transports", transports)
            .put("validated", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false)
            .put("metered", manager.isActiveNetworkMetered)
            .put("activeVpnTransportDetected", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false)
            .put("perAppFlowsObserved", false)
    }

    private fun iso(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(time))
}
