package com.ysfclc.appobserver

import android.app.Activity
import android.content.ActivityNotFoundException
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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var reportPreview: TextView
    private lateinit var sessionStatus: TextView
    private lateinit var selectedAppGroup: RadioGroup
    private lateinit var includeUiText: CheckBox
    private lateinit var includePayloads: CheckBox
    private var pcapdroidPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ObserverSignals.ensureSession(this)
        buildScreen()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::reportPreview.isInitialized) refresh()
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
            "Driver-app research recorder · UI evidence + PCAPdroid network capture",
            14f, MUTED, false
        ).apply { setPadding(0, dp(6), 0, dp(14)) })

        root.addView(card().apply {
            addView(label("WHY THIS EXISTS", 12f, ACCENT, true))
            addView(label(
                "App Observer watches a selected driver or navigation app while you use it, and captures that same app's network traffic through PCAPdroid. The combined timeline is evidence for engineering BusyBee integrations.",
                13f, Color.WHITE, false
            ).apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(label(
                "Nothing is uploaded. Network packets and optional UI text stay in this app's private storage until you export a reviewable evidence ZIP.",
                12f, MUTED, false
            ))
        })

        root.addView(section("RESEARCH TARGET"))
        root.addView(card().apply {
            selectedAppGroup = RadioGroup(this@MainActivity).apply {
                orientation = RadioGroup.VERTICAL
            }
            AppCatalog.apps.forEachIndexed { index, app ->
                val installed = isPackageInstalled(app.packageName)
                selectedAppGroup.addView(RadioButton(this@MainActivity).apply {
                    id = APP_RADIO_BASE + index
                    text = app.label + if (installed) " · installed" else " · not installed"
                    setTextColor(if (installed) Color.WHITE else MUTED)
                    isEnabled = installed
                    textSize = 15f
                })
            }
            selectedAppGroup.check(APP_RADIO_BASE)
            addView(selectedAppGroup)
            addView(label(
                "Only the selected package is observed and passed to PCAPdroid's app filter.",
                12f, MUTED, false
            ).apply { setPadding(0, dp(8), 0, dp(4)) })

            includeUiText = CheckBox(this@MainActivity).apply {
                text = "Include visible UI labels and selected-app notification text"
                setTextColor(Color.WHITE)
                textSize = 13f
                isChecked = false
            }
            addView(includeUiText)
            addView(label(
                "Optional and off by default. Editable/password fields are excluded. Text is locally redacted for common phone, email and token patterns, but names and addresses may remain.",
                11f, MUTED, false
            ))

            includePayloads = CheckBox(this@MainActivity).apply {
                text = "Request full network payloads and TLS decryption where supported"
                setTextColor(Color.WHITE)
                textSize = 13f
                isChecked = true
            }
            addView(includePayloads)
            addView(label(
                "PCAPdroid will show its own capture-consent prompt. TLS pinning or app restrictions may still prevent decryption.",
                11f, MUTED, false
            ).apply { setPadding(0, 0, 0, dp(4)) })
        })

        root.addView(section("ANDROID ACCESS"))
        root.addView(card().apply {
            addView(statusLine("Usage history", isUsageAccessEnabled()))
            addView(action("OPEN USAGE ACCESS SETTINGS") { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) })
            addView(statusLine("Notification observation", isNotificationAccessEnabled()))
            addView(action("OPEN NOTIFICATION ACCESS SETTINGS") {
                openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            })
            addView(statusLine("Selected-app UI observation", isAccessibilityEnabled()))
            addView(action("OPEN ACCESSIBILITY SETTINGS") { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) })
            addView(label(
                "Accessibility is read-only. It records visible labels and UI structure during an active session; it does not tap, swipe or enter data.",
                11f, MUTED, false
            ).apply { setPadding(0, dp(8), 0, 0) })
        })

        root.addView(section("RESEARCH SESSION"))
        root.addView(card().apply {
            sessionStatus = label("No active session.", 13f, MUTED, false).apply {
                setPadding(0, 0, 0, dp(8))
            }
            addView(sessionStatus)
            addView(action("START UI + PCAPDROID SESSION", accent = true) { startResearchSession() })
            addView(action("STOP SESSION") { stopResearchSession() })
            addView(action("EXPORT LAST EVIDENCE ZIP") { exportEvidenceBundle() })
            addView(action("REFRESH STATUS") { refresh() })
        })

        root.addView(section("DEVICE CAPABILITY REPORT"))
        root.addView(card().apply {
            addView(label(
                "The capability report describes Android access and installed app versions. The evidence ZIP includes this report alongside the session timeline and PCAP.",
                12f, Color.WHITE, false
            ))
            reportPreview = label("No report generated yet.", 12f, MUTED, false).apply {
                setPadding(0, dp(10), 0, 0)
            }
            addView(reportPreview)
        })

        root.addView(label(
            "Research evidence can contain sensitive trip, location or account data. Review the ZIP before sharing it. No automatic upload, request replay or third-party app control is performed.",
            12f, Color.rgb(255, 202, 121), false
        ).apply { setPadding(0, dp(14), 0, 0) })
    }

    private fun selectedApp(): ObservedApp {
        val index = (selectedAppGroup.checkedRadioButtonId - APP_RADIO_BASE)
            .coerceIn(0, AppCatalog.apps.lastIndex)
        return AppCatalog.apps[index]
    }

    private fun startResearchSession() {
        if (ObservationStore.hasActiveSession(this)) {
            Toast.makeText(this, "A research session is already active.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Allow the capture notification, then start the session again.", Toast.LENGTH_LONG).show()
            return
        }

        val app = selectedApp()
        if (!isPackageInstalled(app.packageName)) {
            Toast.makeText(this, app.label + " is not installed.", Toast.LENGTH_LONG).show()
            return
        }
        val id = ObservationStore.startSession(this, app.packageName, app.label, includeUiText.isChecked, includePayloads.isChecked)
        startPcapReceiverService()
        pcapdroidPackage = findPcapdroidPackage()
        if (pcapdroidPackage == null) {
            ObservationStore.markPcapdroidUnavailable(this, "PCAPdroid is not installed")
            Toast.makeText(this, "UI observation started. Install PCAPdroid to add network capture.", Toast.LENGTH_LONG).show()
        } else {
            window.decorView.postDelayed({ launchPcapdroidStart(app, id) }, 800L)
        }
        refresh()
    }

    private fun launchPcapdroidStart(app: ObservedApp, sessionId: String) {
        if (!ObservationStore.hasActiveSession(this)) return
        ObservationStore.appendSessionEvent(this, JSONObject()
            .put("type", "pcapdroid_start_requested")
            .put("timestampEpochMs", System.currentTimeMillis())
            .put("targetPackage", app.packageName)
            .put("sessionId", sessionId))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClassName(pcapdroidPackage!!, PCAPDROID_CAPTURE_ACTIVITY)
            putExtra("action", "start")
            putExtra("broadcast_receiver", PcapdroidStatusReceiver::class.java.name)
            putExtra("pcap_dump_mode", "udp_exporter")
            putExtra("collector_host", PcapdroidCaptureService.LOOPBACK)
            // Deprecated alias supports older PCAPdroid versions.
            putExtra("collector_ip_address", PcapdroidCaptureService.LOOPBACK)
            putExtra("collector_port", PcapdroidCaptureService.PORT.toString())
            putExtra("app_filter", app.packageName)
            putExtra("max_dump_size", MAX_CAPTURE_BYTES)
            putExtra("full_payload", includePayloads.isChecked)
            putExtra("tls_decryption", includePayloads.isChecked)
        }
        runCatching { startActivityForResult(intent, REQUEST_PCAP_START) }
            .onFailure {
                ObservationStore.markPcapdroidUnavailable(this, it.javaClass.simpleName)
                Toast.makeText(this, "Could not launch PCAPdroid. UI observation remains active.", Toast.LENGTH_LONG).show()
            }
    }

    private fun stopResearchSession() {
        if (!ObservationStore.hasActiveSession(this)) {
            Toast.makeText(this, "There is no active session.", Toast.LENGTH_SHORT).show()
            return
        }
        val packageName = pcapdroidPackage
        if (packageName == null || !ObservationStore.isPcapdroidRunning(this)) {
            endLocalSession("user_stopped_without_active_pcapdroid")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClassName(packageName, PCAPDROID_CAPTURE_ACTIVITY)
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
        listOf("com.emanuelef.remote_capture", "com.emanuelef.remote_capture.debug").forEach { pkg ->
            if (isPackageInstalled(pkg)) return pkg
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
            Toast.makeText(this, "Stop the research session before exporting its evidence.", Toast.LENGTH_LONG).show()
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
                if (resultCode == RESULT_OK) {
                    val versionName = data?.getStringExtra("version_name")
                    val versionCode = data?.getIntExtra("version_code", 0) ?: 0
                    ObservationStore.markPcapdroidStarted(this, versionName, versionCode)
                    Toast.makeText(this, "PCAPdroid capture started for the selected app.", Toast.LENGTH_LONG).show()
                } else {
                    ObservationStore.markPcapdroidUnavailable(this, "capture consent denied or start failed")
                    Toast.makeText(this, "PCAPdroid did not start. UI observation is still active.", Toast.LENGTH_LONG).show()
                }
                refresh()
            }
            REQUEST_PCAP_STOP -> {
                endLocalSession(if (resultCode == RESULT_OK) "user_stopped" else "pcapdroid_stop_failed")
            }
            REQUEST_EXPORT -> {
                if (resultCode != RESULT_OK) return
                val uri: Uri = data?.data ?: return
                runCatching { ObservationStore.exportBundle(this, uri) }
                    .onSuccess { Toast.makeText(this, "Evidence ZIP exported. Review it before sharing.", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(this, "Export failed: " + it.message, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun refresh() {
        if (!::reportPreview.isInitialized) return
        val report = ObserverReport.build(this)
        val lines = StringBuilder()
        lines.append("Generated: ").append(report.optString("generatedAt")).append("\n")
        val apps = report.getJSONArray("apps")
        for (index in 0 until apps.length()) {
            val app = apps.getJSONObject(index)
            lines.append(app.optString("label")).append(": ")
                .append(if (app.optBoolean("installed")) "installed" else "not installed")
                .append("\n")
        }
        val access = report.getJSONObject("androidAccess")
        lines.append("\nUsage access: ").append(access.optBoolean("usageAccessEnabled"))
            .append("\nNotification access: ").append(access.optBoolean("notificationAccessEnabled"))
            .append("\nAccessibility: ").append(access.optBoolean("accessibilityEnabled"))
            .append("\nPCAPdroid: ").append(if (findPcapdroidPackage() == null) "not installed" else "available")
        reportPreview.text = lines.toString()

        val active = ObservationStore.hasActiveSession(this)
        val directory = ObservationStore.latestDirectory(this)
        val uiCount = countLines(directory?.let { File(it, "ui-events.jsonl") })
        val packetCount = countLines(directory?.let { File(it, "network-events.jsonl") })
        val pcapBytes = directory?.let { File(it, "network.pcap").takeIf(File::exists)?.length() } ?: 0L
        sessionStatus.text = if (active) {
            "SESSION ACTIVE · " + (ObservationStore.activePackage(this) ?: "selected app") +
                "\nUI records: $uiCount · packet records: $packetCount · PCAP: " + (pcapBytes / 1024) + " KB"
        } else if (directory != null) {
            "LAST SESSION · UI records: $uiCount · packet records: $packetCount · PCAP: " +
                (pcapBytes / 1024) + " KB\nExport the evidence ZIP to share the session with the engineering assistant."
        } else "No active session."
        sessionStatus.setTextColor(if (active) ACCENT else MUTED)
    }

    private fun countLines(file: File?): Long =
        if (file?.isFile == true) runCatching { file.useLines { it.count().toLong() } }.getOrDefault(0L) else 0L

    private fun isUsageAccessEnabled(): Boolean {
        val manager = getSystemService(android.app.AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            manager.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            manager.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun isNotificationAccessEnabled(): Boolean =
        componentEnabled("enabled_notification_listeners", ObserverNotificationListener::class.java)

    private fun isAccessibilityEnabled(): Boolean {
        val enabledFlag = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        return enabledFlag && componentEnabled(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ObserverAccessibilityService::class.java)
    }

    private fun componentEnabled(setting: String, type: Class<*>): Boolean {
        val raw = Settings.Secure.getString(contentResolver, setting).orEmpty()
        val component = ComponentName(this, type)
        return raw.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.contains(component)
    }

    private fun openSettings(action: String) {
        runCatching { startActivity(Intent(action)) }
            .onFailure { Toast.makeText(this, "Could not open Android settings", Toast.LENGTH_SHORT).show() }
    }

    private fun section(value: String) = label(value, 12f, ACCENT, true).apply {
        setPadding(dp(2), dp(18), 0, dp(8))
    }

    private fun statusLine(name: String, enabled: Boolean) = label(
        "$name · " + if (enabled) "ENABLED" else "NOT ENABLED",
        13f, if (enabled) ACCENT else Color.rgb(255, 183, 112), true
    ).apply { setPadding(0, dp(4), 0, dp(4)) }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = rounded(Color.rgb(21, 34, 50), dp(16))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(10)
        }
    }

    private fun action(name: String, accent: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = name
        if (accent) {
            setTextColor(Color.rgb(8, 25, 32))
            background = rounded(ACCENT, dp(10))
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(7)
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PCAP_START = 7151
        private const val REQUEST_PCAP_STOP = 7152
        private const val REQUEST_EXPORT = 7153
        private const val REQUEST_NOTIFICATIONS = 7154
        private const val APP_RADIO_BASE = 8100
        private const val PCAPDROID_CAPTURE_ACTIVITY = "com.emanuelef.remote_capture.activities.CaptureCtrl"
        private const val MAX_CAPTURE_BYTES = 52_428_800
        private val ACCENT = Color.rgb(86, 225, 185)
        private val MUTED = Color.rgb(148, 164, 183)
    }
}
