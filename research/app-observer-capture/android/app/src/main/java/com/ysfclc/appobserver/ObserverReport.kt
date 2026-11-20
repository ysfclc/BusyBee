package com.ysfclc.appobserver

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal capability report. It deliberately avoids enumerating installed apps,
 * inspecting their requested permissions, or exporting usage history and device identifiers.
 */
object ObserverReport {
    fun build(context: Context): JSONObject {
        val accessibilityEnabled = isComponentEnabled(
            context,
            ObserverAccessibilityService::class.java,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val notificationEnabled = isComponentEnabled(
            context,
            ObserverNotificationListener::class.java,
            "enabled_notification_listeners"
        )

        return JSONObject()
            .put("schema", "app-observer-capability-report/v2")
            .put("generatedAt", iso(System.currentTimeMillis()))
            .put("android", JSONObject()
                .put("release", Build.VERSION.RELEASE)
                .put("apiLevel", Build.VERSION.SDK_INT))
            .put("access", JSONObject()
                .put("accessibilityEnabled", accessibilityEnabled)
                .put("accessibilityServiceConnected", ObserverSignals.serviceConnected(context, "accessibility"))
                .put("notificationAccessEnabled", notificationEnabled)
                .put("notificationServiceConnected", ObserverSignals.serviceConnected(context, "notifications")))
            .put("captureCapabilities", JSONObject()
                .put("targetSelection", "dynamic_launchable_apps_no_provider_allowlist")
                .put("uiEvidence", "structure_only_selected_app_during_manual_session")
                .put("notificationEvidence", "metadata_only_selected_app_during_manual_session")
                .put("networkEvidence", "packet_header_metadata_only_when_pcapdroid_is_available")
                .put("rawPacketPersistence", false)
                .put("applicationPayloadCapture", false)
                .put("inProcessObservation", "not_implemented"))
            .put("privacy", JSONObject()
                .put("localOnly", true)
                .put("noOutboundUpload", true)
                .put("visibleTextStored", false)
                .put("typedValuesStored", false)
                .put("notificationContentStored", false)
                .put("screenshotsStored", false)
                .put("clipboardStored", false)
                .put("networkPayloadStored", false)
                .put("rawIpAddressesStored", false)
                .put("rawPacketCaptureStored", false)
                .put("legacyEvidenceSanitisedOnExport", true))
            .put("limitations", JSONArray()
                .put("The app picker discovers launchable applications visible through Android package visibility; hidden and non-launchable packages are not enumerated.")
                .put("Only the one app explicitly selected for a manually started session is observed.")
                .put("Some apps expose incomplete or custom UI structures; missing controls are not inferred as absent.")
                .put("PCAPdroid packet records are inspected in memory to derive allowlisted metadata and are not written as raw PCAP.")
                .put("Packet metadata does not reveal HTTPS request or response bodies and does not prove a business operation occurred.")
                .put("Observation is read-only; no app actions, gestures or request replay are performed."))
    }

    private fun isComponentEnabled(context: Context, type: Class<*>, setting: String): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, setting).orEmpty()
        val component = ComponentName(context, type)
        return enabled.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .contains(component)
    }

    private fun iso(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(time))
}
