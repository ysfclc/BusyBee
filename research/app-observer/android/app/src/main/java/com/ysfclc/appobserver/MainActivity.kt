package com.ysfclc.appobserver

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var reportPreview: TextView
    private var pendingExport = ""

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

        root.addView(label("APP OBSERVER LAB", 25f, Color.WHITE, true))
        root.addView(label("A separate Android capability survey for selected apps.", 14f, MUTED, false).apply {
            setPadding(0, dp(6), 0, dp(16))
        })
        root.addView(card().apply {
            addView(label("OBSERVATION POLICY", 12f, ACCENT, true))
            addView(label(
                "Metadata only. No screen text, notification bodies, senders, credentials, private databases or third-party app actions. Counters stay on this phone.",
                13f, Color.WHITE, false
            ).apply { setPadding(0, dp(8), 0, 0) })
        })

        root.addView(section("TARGET APPLICATIONS"))
        AppCatalog.apps.forEach { root.addView(appCard(it)) }

        root.addView(section("ANDROID ACCESS"))
        root.addView(card().apply {
            addView(statusLine("Usage history", isUsageAccessEnabled()))
            addView(action("OPEN USAGE ACCESS SETTINGS") { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) })
            addView(statusLine("Notification metadata", isNotificationAccessEnabled()))
            addView(action("OPEN NOTIFICATION ACCESS SETTINGS") {
                openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            })
            addView(statusLine("Read-only UI structure", isAccessibilityEnabled()))
            addView(action("OPEN ACCESSIBILITY SETTINGS") { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) })
        })

        root.addView(section("OBSERVATION REPORT"))
        root.addView(card().apply {
            addView(label("Grant the access you are comfortable with, then use the selected apps normally. Refresh and export the JSON report to send it back.", 13f, Color.WHITE, false))
            addView(action("REFRESH CAPABILITY REPORT") { refresh() })
            addView(action("EXPORT JSON REPORT", accent = true) { exportReport() })
            addView(action("SHARE REPORT SUMMARY") { shareReport() })
            addView(action("RESET OBSERVATION COUNTERS") { confirmReset() })
            reportPreview = label("No report generated yet.", 12f, MUTED, false).apply {
                setPadding(0, dp(12), 0, 0)
            }
            addView(reportPreview)
        })

        root.addView(label(
            "NETWORK LIMIT: this build only reports the device's active VPN state. It does not capture packets, inspect per-app flows or decrypt HTTPS. Plaintext-boundary research remains a separate controlled experiment.",
            12f, Color.rgb(255, 202, 121), false
        ).apply { setPadding(0, dp(14), 0, 0) })
    }

    private fun appCard(app: ObservedApp): View {
        val card = card()
        val info = runCatching { packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        card.addView(label(app.label, 17f, Color.WHITE, true))
        card.addView(label(app.packageName, 11f, MUTED, false).apply { setPadding(0, dp(3), 0, dp(7)) })
        card.addView(label(
            if (info != null) "INSTALLED · " + (info.versionName ?: "version unknown") else "NOT INSTALLED / NOT VISIBLE",
            12f, if (info != null) ACCENT else Color.rgb(255, 178, 113), true
        ))
        card.addView(action("OPEN " + app.label.uppercase(Locale.ROOT)) {
            val launch = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launch != null) startActivity(launch)
            else Toast.makeText(this, "No launch screen available for " + app.label, Toast.LENGTH_SHORT).show()
        }, enabled = info != null)
        return card
    }

    private fun refresh() {
        val report = ObserverReport.build(this)
        val apps = report.getJSONArray("apps")
        val lines = StringBuilder()
        lines.append("Generated: ").append(report.optString("generatedAt")).append("\n")
        lines.append("Observation since: ").append(report.optString("observationStartedAt")).append("\n\n")
        for (index in 0 until apps.length()) {
            val app = apps.getJSONObject(index)
            val usage = app.getJSONObject("usage")
            val signals = app.getJSONObject("signalsSinceObservationStarted")
            val notifications = signals.getJSONObject("notification")
            val accessibility = signals.getJSONObject("accessibility")
            lines.append(app.optString("label")).append(": ")
                .append(if (app.optBoolean("installed")) "installed" else "not installed")
                .append(" · notifications ").append(notifications.optInt("count"))
                .append(" · UI events ").append(accessibility.optInt("eventCount"))
            if (usage.optBoolean("available")) {
                lines.append(" · foreground ").append(usage.optLong("foregroundMilliseconds") / 60_000).append(" min / 24h")
            } else {
                lines.append(" · usage permission needed")
            }
            lines.append("\n")
        }
        val access = report.getJSONObject("androidAccess")
        lines.append("\nPermissions: usage=").append(access.optBoolean("usageAccessEnabled"))
            .append(", notifications=").append(access.optBoolean("notificationAccessEnabled"))
            .append(", accessibility=").append(access.optBoolean("accessibilityEnabled"))
        reportPreview.text = lines.toString()
    }

    private fun exportReport() {
        pendingExport = ObserverReport.build(this).toString(2)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "app-observer-report-$stamp.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun shareReport() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "App Observer Lab capability report")
            putExtra(Intent.EXTRA_TEXT, ObserverReport.build(this@MainActivity).toString(2))
        }
        startActivity(Intent.createChooser(intent, "Share diagnostic report"))
    }

    @Deprecated("Deprecated by Android; retained for minSdk 26 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        runCatching {
            contentResolver.openOutputStream(uri)?.use {
                it.write(pendingExport.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open the selected destination")
        }.onSuccess {
            Toast.makeText(this, "JSON report exported. Review it before sharing.", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "Export failed: " + it.javaClass.simpleName, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmReset() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Reset local observation counters?")
            .setMessage("Only aggregate counters are deleted. Android permissions are unchanged.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ ->
                ObserverSignals.reset(this)
                refresh()
            }
            .show()
    }

    private fun isUsageAccessEnabled(): Boolean {
        val manager = getSystemService(android.app.AppOpsManager::class.java)
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) {
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
        componentEnabled(Settings.Secure.ENABLED_NOTIFICATION_LISTENERS, ObserverNotificationListener::class.java)

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

    private fun action(name: String, accent: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) =
        Button(this).apply {
            text = name
            isEnabled = enabled
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
        private const val REQUEST_EXPORT = 7151
        private val ACCENT = Color.rgb(86, 225, 185)
        private val MUTED = Color.rgb(148, 164, 183)
    }
}
