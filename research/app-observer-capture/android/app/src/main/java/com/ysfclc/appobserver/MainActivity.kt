package com.ysfclc.appobserver

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generic, read-only observer. The user chooses one installed launchable app per explicit session.
 * The recorder stores structural UI evidence and privacy-allowlisted network metadata only.
 */
class MainActivity : Activity() {
    private lateinit var reportPreview: TextView
    private lateinit var sessionStatus: TextView
    private lateinit var appSpinner: Spinner
    private lateinit var appAdapter: ArrayAdapter<ObservedApp>
    private var pcapdroidPackage: String? = null

    private val liveRefresh = object : Runnable {
        override fun run() {
            if (::sessionStatus.isInitialized) refresh()
            if (ObservationStore.hasActiveSession(this@MainActivity) && ::sessionStatus.isInitialized) {
                sessionStatus.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        refreshAppCatalog()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::appSpinner.isInitialized) refreshAppCatalog()
        if (::sessionStatus.isInitialized && ObservationStore.hasActiveSession(this)) {
            sessionStatus.removeCallbacks(liveRefresh)
            sessionStatus.post(liveRefresh)
        }
        if (::reportPreview.isInitialized) refresh()
    }

    override fun onPause() {
        if (::sessionStatus.isInitialized) sessionStatus.removeCallbacks(liveRefresh)
        super.onPause()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
            setBackgroundColor(Color.rgb(9, 17, 28))
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(label("APP OBSERVER", 27f, Color.WHITE, true))
        root.addView(label(
            "Generic Android app behaviour recorder · structure + metadata only",
            14f, MUTED, false
        ).apply { setPadding(0, dp(6), 0, dp(14)) })

        root.addView(card().apply {
            addView(label("PURPOSE", 12f, ACCENT, true))
            addView(label(
                "Observe how an app behaves so its screens, controls, transitions and network timing can later be classified into reusable patterns. Choose any launchable app visible to Android's app picker; no provider-specific allowlist is used.",
                13f, Color.WHITE, false
            ).apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(label(
                "One selected app is observed per manually started session. Observation is read-only: this build does not tap, swipe, type, replay requests or automate actions.",
                12f, MUTED, false
            ))
        })

        root.addView(section("OBSERVATION TARGET"))
        root.addView(card().apply {
            appSpinner = Spinner(this@MainActivity)
            appAdapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                mutableListOf<ObservedApp>()
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            appSpinner.adapter = appAdapter
            addView(appSpinner)
            addView(label(
                "The list is discovered dynamically from installed apps with launcher activities. Hidden and non-launchable packages are not enumerated.",
                11f, MUTED, false
            ).apply { setPadding(0, dp(8), 0, 0) })
        })

        root.addView(section("PRIVACY BY DEFAULT"))
        root.addView(card().apply {
            addView(statusLine("Visible UI text and typed values", false))
            addView(statusLine("Notification titles and bodies", false))
            addView(statusLine("Screenshots and clipboard contents", false))
            addView(statusLine("Network payloads and TLS decryption", false))
            addView(statusLine("Raw IP addresses and raw PCAP files", false))
            addView(label(
                "Stored: UI structure (control class, resource ID, hierarchy, bounds and state flags), notification envelope metadata, and packet-header metadata (protocol, ports, sizes and timing). Packet bytes are inspected in memory and discarded. Evidence stays in private app storage until you explicitly export it.",
                11f, MUTED, false
            ).apply { setPadding(0, dp(10), 0, 0) })
        })

        root.addView(section("ANDROID ACCESS"))
        root.addView(card().apply {
            addView(statusLine("Read-only Accessibility observation", isAccessibilityEnabled()))
            addView(action("OPEN ACCESSIBILITY SETTINGS") {
                openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            })
            addView(statusLine("Notification metadata (optional)", isNotificationAccessEnabled()))
            addView(action("OPEN NOTIFICATION ACCESS SETTINGS") {
                openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            })
            addView(label(
                "Accessibility receives events for Android apps, but this recorder filters them to the one package selected for the active session. It cannot perform gestures.",
                11f, MUTED, false
            ).apply { setPadding(0, dp(8), 0, 0) })
        })

        root.addView(section("RESEARCH SESSION"))
        root.addView(card().apply {
            sessionStatus = label("No active session.", 13f, MUTED, false).apply {
                setPadding(0, 0, 0, dp(8))
            }
            addView(sessionStatus)
            addView(action("START OBSERVATION", accent = true) { startResearchSession() })
            addView(action("STOP OBSERVATION") { stopResearchSession() })
            addView(action("EXPORT PRIVACY-MINIMISED EVIDENCE ZIP") { exportEvidenceBundle() })
            addView(action("REFRESH STATUS") { refresh() })
        })

        root.addView(section("CAPABILITY REPORT"))
        root.addView(card().apply {
            reportPreview = label("No report generated yet.", 12f, MUTED, false).apply {
                setPadding(0, dp(8), 0, 0)
            }
            addView(reportPreview)
        })

        root.addView(label(
            "No automatic upload. Review the target app and evidence before sharing. Old evidence from earlier test builds is sanitised again during export; legacy raw PCAP files are never included.",
            12f, Color.rgb(255, 202, 121), false
        ).apply { setPadding(0, dp(14), 0, 0) })
    }

    private fun refreshAppCatalog() {
        if (!::appAdapter.isInitialized) return
        val previousPackage = (appSpinner.selectedItem as? ObservedApp)?.packageName
        val discovered = AppCatalog.installedLaunchableApps(packageManager, packageName)
        appAdapter.clear()
        appAdapter.addAll(discovered)
        appAdapter.notifyDataSetChanged()
        if (discovered.isNotEmpty()) {
            val restored = discovered.indexOfFirst { it.packageName == previousPackage }
            appSpinner.setSelection(if (restored >= 0) restored else 0)
        }
    }

    private fun selectedApp(): ObservedApp? = appSpinner.selectedItem as? ObservedApp

    private fun startResearchSession() {
        if (ObservationStore.hasActiveSession(this)) {
            Toast.makeText(this, "An observation session is already active.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Enable App Observer's read-only Accessibility service first.", Toast.LENGTH_LONG).show()
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            Toast.makeText(this, "Allow the session notification, then start observation again.", Toast.LENGTH_LONG).show()
            return
        }

        val app = selectedApp()
        if (app == null) {
            Toast.makeText(this, "No launchable app is visible to Android's app picker.", Toast.LENGTH_LONG).show()
            return
        }

        val sessionId = ObservationStore.startSession(this, app.packageName, app.label)
        pcapdroidPackage = findPcapdroidPackage()
        if (pcapdroidPackage == null) {
            ObservationStore.markPcapdroidUnavailable(this, "PCAPdroid is not installed")
            Toast.makeText(this, "UI structure observation started. Install PCAPdroid for network metadata.", Toast.LENGTH_LONG).show()
        } else {
            startPcapReceiverService()
            waitForReceiverAndStart(app, sessionId, 0)
        }
        refresh()
        if (::sessionStatus.isInitialized) sessionStatus.post(liveRefresh)
    }

    private fun waitForReceiverAndStart(app: ObservedApp, sessionId: String, attempt: Int) {
        if (!ObservationStore.hasActiveSession(this)) return
        if (PcapdroidCaptureService.isReceiverReady()) {
            launchPcapdroidStart(app, sessionId)
            return
        }
        if (attempt >= 50) {
            ObservationStore.markPcapdroidUnavailable(this, "Local TCP receiver did not become ready")
            Toast.makeText(this, "Local metadata receiver did not start. Stop the session and retry.", Toast.LENGTH_LONG).show()
            refresh()
            return
        }
        window.decorView.postDelayed({ waitForReceiverAndStart(app, sessionId, attempt + 1) }, 100L)
    }

    private fun launchPcapdroidStart(app: ObservedApp, sessionId: String) {
        if (!ObservationStore.hasActiveSession(this)) return
        val capturePackage = pcapdroidPackage ?: findPcapdroidPackage()
        if (capturePackage == null) {
            ObservationStore.markPcapdroidUnavailable(this, "PCAPdroid disappeared before capture start")
            return
        }
        pcapdroidPackage = capturePackage
        ObservationStore.appendSessionEvent(this, JSONObject()
            .put("type", "pcapdroid_start_requested")
            .put("timestampEpochMs", System.currentTimeMillis())
            .put("targetPackage", app.packageName)
            .put("sessionId", sessionId)
            .put("privacyMode", "metadata_only_no_payload"))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClassName(capturePackage, PCAPDROID_CAPTURE_ACTIVITY)
            putExtra("action", "start")
            putExtra("broadcast_receiver", PcapdroidStatusReceiver::class.java.name)
            putExtra("pcap_dump_mode", "tcp_exporter")
            putExtra("collector_host", PcapdroidCaptureService.LOOPBACK)
            putExtra("collector_ip_address", PcapdroidCaptureService.LOOPBACK)
            putExtra("collector_port", PcapdroidCaptureService.PORT.toString())
            putExtra("app_filter", app.packageName)
            putExtra("max_dump_size", MAX_CAPTURE_BYTES)
            putExtra("full_payload", false)
            putExtra("tls_decryption", false)
        }
        runCatching { startActivityForResult(intent, REQUEST_PCAP_START) }
            .onFailure {
                ObservationStore.markPcapdroidUnavailable(this, it.javaClass.simpleName)
                Toast.makeText(this, "Could not launch PCAPdroid. UI structure observation remains active.", Toast.LENGTH_LONG).show()
            }
    }

    private fun stopResearchSession() {
        if (!ObservationStore.hasActiveSession(this)) {
            Toast.makeText(this, "There is no active session.", Toast.LENGTH_SHORT).show()
            return
        }
        val capturePackage = pcapdroidPackage ?: findPcapdroidPackage()
        if (capturePackage == null || !ObservationStore.hasPcapdroidCommandAccepted(this)) {
            endLocalSession("user_stopped_without_active_pcapdroid")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClassName(capturePackage, PCAPDROID_CAPTURE_ACTIVITY)
            putExtra("action", "stop")
        }
        runCatching { startActivityForResult(intent, REQUEST_PCAP_STOP) }
            .onFailure { endLocalSession("pcapdroid_stop_unavailable") }
    }

    private fun endLocalSession(reason: String) {
        startService(Intent(this, PcapdroidCaptureService::class.java)
            .setAction(PcapdroidCaptureService.ACTION_END_SESSION)
            .putExtra(PcapdroidCaptureService.EXTRA_FINISH_REASON, reason))
        refresh()
    }

    private fun startPcapReceiverService() {
        val intent = Intent(this, PcapdroidCaptureService::class.java)
            .setAction(PcapdroidCaptureService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun findPcapdroidPackage(): String? {
        listOf("com.emanuelef.remote_capture", "com.emanuelef.remote_capture.debug").forEach { candidate ->
            if (isPackageInstalled(candidate)) return candidate
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
        }.isSuccess

    private fun exportEvidenceBundle() {
        val directory = ObservationStore.latestDirectory(this)
        if (directory == null) {
            Toast.makeText(this, "No session evidence exists yet.", Toast.LENGTH_SHORT).show()
            return
        }
        if (ObservationStore.hasActiveSession(this)) {
            Toast.makeText(this, "Stop observation before exporting the evidence.", Toast.LENGTH_LONG).show()
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "app-observer-evidence-$stamp.zip")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    @Deprecated("Deprecated by Android; retained for minSdk 26 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PCAP_START -> {
                ObservationStore.appendSessionEvent(this, JSONObject()
                    .put("type", "pcapdroid_start_activity_result")
                    .put("timestampEpochMs", System.currentTimeMillis())
                    .put("resultCode", resultCode)
                    .put("resultOk", resultCode == RESULT_OK))
                if (resultCode == RESULT_OK) {
                    val versionName = data?.getStringExtra("version_name")
                    val versionCode = data?.getIntExtra("version_code", 0) ?: 0
                    ObservationStore.markPcapdroidCommandAccepted(this, versionName, versionCode)
                    Toast.makeText(this, "PCAPdroid accepted the command. Packet receipt is still being verified.", Toast.LENGTH_LONG).show()
                } else {
                    ObservationStore.markPcapdroidUnavailable(this, "capture consent denied or start failed")
                    Toast.makeText(this, "PCAPdroid did not start. UI structure observation remains active.", Toast.LENGTH_LONG).show()
                }
                refresh()
            }
            REQUEST_PCAP_STOP -> endLocalSession(
                if (resultCode == RESULT_OK) "user_stopped" else "pcapdroid_stop_failed"
            )
            REQUEST_EXPORT -> {
                if (resultCode != RESULT_OK) return
                val uri: Uri = data?.data ?: return
                runCatching { ObservationStore.exportBundle(this, uri) }
                    .onSuccess { Toast.makeText(this, "Privacy-minimised evidence ZIP exported.", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(this, "Export failed: " + it.message, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun refresh() {
        if (!::reportPreview.isInitialized) return
        val report = ObserverReport.build(this)
        val access = report.getJSONObject("access")
        val android = report.getJSONObject("android")
        val accessibilityText = if (access.optBoolean("accessibilityEnabled")) "enabled" else "not enabled"
        val notificationText = if (access.optBoolean("notificationAccessEnabled")) "enabled" else "not enabled"
        reportPreview.text = listOf(
            "Android " + android.optString("release") + " · API " + android.optInt("apiLevel"),
            "Accessibility: " + accessibilityText,
            "Notification metadata: " + notificationText,
            "Target discovery: dynamic launchable-app list"
        ).joinToString("\n")

        val active = ObservationStore.hasActiveSession(this)
        val directory = ObservationStore.latestDirectory(this)
        val uiCount = countLines(directory?.let { File(it, "ui-events.jsonl") })
        val networkCount = countLines(directory?.let { File(it, "network-events.jsonl") })
        val activeTarget = ObservationStore.activePackage(this)
        val observedTarget = activeTarget ?: directory?.let {
            runCatching { JSONObject(File(it, "session.json").readText()).optString("targetPackage") }.getOrNull()
        }
        sessionStatus.text = if (active) {
            "OBSERVING · " + (observedTarget ?: "selected app") +
                "\nUI structure events: " + uiCount + " · network metadata events: " + networkCount +
                "\nTCP exporter: " + PcapdroidCaptureService.liveStatus() +
                " · packet headers inspected: " + PcapdroidCaptureService.livePackets() +
                " · packet bytes represented: " + PcapdroidCaptureService.liveBytes()
        } else if (directory != null) {
            "LAST SESSION · " + (observedTarget ?: "selected app") +
                "\nUI structure events: " + uiCount + " · network metadata events: " + networkCount +
                "\nExport creates a privacy-minimised evidence bundle. No raw PCAP is retained by this build."
        } else {
            "No active session."
        }
        sessionStatus.setTextColor(if (active) ACCENT else MUTED)
    }

    private fun countLines(file: File?): Long =
        if (file?.isFile == true) {
            runCatching { file.useLines { it.count().toLong() } }.getOrDefault(0L)
        } else 0L

    private fun isAccessibilityEnabled(): Boolean =
        isComponentEnabled(ObserverAccessibilityService::class.java, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    private fun isNotificationAccessEnabled(): Boolean =
        isComponentEnabled(ObserverNotificationListener::class.java, "enabled_notification_listeners")

    private fun isComponentEnabled(type: Class<*>, setting: String): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, setting).orEmpty()
        val component = ComponentName(this, type)
        return enabled.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .contains(component)
    }

    private fun openSettings(action: String) {
        runCatching { startActivity(Intent(action)) }
            .onFailure { Toast.makeText(this, "Could not open Android settings.", Toast.LENGTH_LONG).show() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun section(text: String): TextView =
        label(text, 11f, ACCENT, true).apply { setPadding(dp(2), dp(18), 0, dp(8)) }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = GradientDrawable().apply {
            setColor(Color.rgb(18, 30, 46))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.rgb(39, 57, 77))
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(3) }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(2))
        }

    private fun statusLine(text: String, enabled: Boolean): TextView =
        label((if (enabled) "● " else "○ ") + text, 12f, if (enabled) ACCENT else MUTED, false)

    private fun action(text: String, accent: Boolean = false, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            setTextColor(if (accent) Color.rgb(7, 24, 27) else Color.WHITE)
            if (accent) backgroundTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            else backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(41, 59, 78))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5) }
        }

    companion object {
        private const val REQUEST_PCAP_START = 7151
        private const val REQUEST_PCAP_STOP = 7152
        private const val REQUEST_EXPORT = 7153
        private const val REQUEST_NOTIFICATIONS = 7154
        private const val PCAPDROID_CAPTURE_ACTIVITY = "com.emanuelef.remote_capture.activities.CaptureCtrl"
        private const val MAX_CAPTURE_BYTES = 52_428_800
        private val ACCENT = Color.rgb(86, 225, 185)
        private val MUTED = Color.rgb(148, 164, 183)
    }
}
