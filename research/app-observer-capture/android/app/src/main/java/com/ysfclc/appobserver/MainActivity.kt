package com.ysfclc.appobserver

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.graphics.drawable.BitmapDrawable
import android.widget.FrameLayout
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
    private lateinit var uiEvidencePreview: TextView
    private lateinit var networkEvidencePreview: TextView
    private lateinit var screenshotPreview: ImageView
    private lateinit var screenshotRuleOverlay: ScreenshotRuleOverlay
    private lateinit var screenshotCaption: TextView
    private lateinit var screenshotNavigation: TextView
    private var screenshotHistory: List<StoredScreenshot> = emptyList()
    private var selectedScreenshotIndex = -1
    private lateinit var semanticRulesPreview: TextView
    private lateinit var semanticPayloadPreview: TextView
    private lateinit var machineUnderstandingPreview: TextView
    private var displayedScreenshotPath: String? = null
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
            "Generic Android app behaviour recorder · visual ground truth + meaning rules",
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
                "Apps are discovered dynamically. The final option, App Observer (self-test), lets you verify capture against this app's own UI. Matching compares package-scoped UI structure, not your personal identity or account.",
                11f, MUTED, false
            ).apply { setPadding(0, dp(8), 0, 0) })
        })

        root.addView(section("PRIVACY BY DEFAULT"))
        root.addView(card().apply {
            addView(statusLine("Full visual screenshots during a manual session", true))
            addView(statusLine("On-device OCR and image models analyse visible screens", true))
            addView(statusLine("Raw OCR text written to evidence", false))
            addView(statusLine("Accessibility / notification text without explicit rules", false))
            addView(statusLine("Clipboard, raw network payloads and TLS decryption", false))
            addView(statusLine("Raw IP addresses and raw PCAP files", false))
            addView(label(
                "The device temporarily reads visible text locally to identify screen concepts. Raw OCR text is discarded; inferred categories, confidence, screen-state patterns and selected entities such as prices are retained in private storage. Accessibility and notification text payloads still require explicit rules. Screenshots can contain sensitive information; review exports before sharing.",
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
                "Accessibility is used for structure, screenshots and only the content regions you explicitly authorise. The observer cannot tap, swipe, type or perform app actions.",
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
            addView(action("EXPORT EVIDENCE + SCREENSHOTS + RULES") { exportEvidenceBundle() })
            addView(action("IMPORT PREVIOUS APP EVIDENCE ZIP") { importPreviousEvidence() })
            addView(action("REFRESH STATUS") { refresh() })
            addView(label(
                "After starting, switch to the selected target app and use it normally. Screenshots are captured at meaningful UI events. Return here to inspect and annotate the actual screen.",
                11f, MUTED, false
            ).apply { setPadding(0, dp(8), 0, 0) })
        })

        root.addView(section("VISUAL GROUND TRUTH · ACTUAL SCREENSHOTS"))
        root.addView(card().apply {
            screenshotCaption = label("No screenshots yet. Start a manual session and interact with the selected app.", 12f, MUTED, false)
            addView(screenshotCaption)
            val screenshotFrame = FrameLayout(this@MainActivity).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(320)
                )
            }
            screenshotPreview = ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.BLACK)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            screenshotRuleOverlay = ScreenshotRuleOverlay(this@MainActivity)
            screenshotFrame.addView(screenshotPreview)
            screenshotFrame.addView(screenshotRuleOverlay)
            addView(screenshotFrame)
            screenshotNavigation = label("No screenshot selected.", 11f, MUTED, false)
            addView(screenshotNavigation)
            addView(rowOfActions(
                action("PREVIOUS SCREEN") { selectScreenshot(-1) },
                action("NEXT SCREEN") { selectScreenshot(1) }
            ))
            addView(action("DEFINE MEANING FROM THIS SCREENSHOT", accent = true) {
                defineMeaningRuleFromLatestScreenshot()
            })
            addView(label("PRESERVED MEANING RULES", 12f, ACCENT, true).apply {
                setPadding(0, dp(14), 0, dp(4))
            })
            semanticRulesPreview = label("No meaning rules yet.", 12f, Color.WHITE, false)
            addView(semanticRulesPreview)
            addView(label("LINKED PAYLOADS · MATCHED TO MEANING RULES", 12f, ACCENT, true).apply {
                setPadding(0, dp(14), 0, dp(4))
            })
            semanticPayloadPreview = label("No authorised payloads yet.", 12f, Color.WHITE, false)
            addView(semanticPayloadPreview)
        })

        root.addView(section("MACHINE-LEARNED APP UNDERSTANDING"))
        root.addView(card().apply {
            addView(label("MULTI-SOURCE SCREEN IDENTIFICATION", 12f, ACCENT, true))
            machineUnderstandingPreview = label(
                ScreenUnderstandingService.preview(this@MainActivity),
                12f, Color.WHITE, false
            ).apply { setPadding(0, dp(8), 0, dp(8)) }
            addView(machineUnderstandingPreview)
            addView(label(
                "The device analyses screenshots with on-device OCR and image labelling, then correlates Accessibility structure, interactions, authorised notification content and nearby network activity. Repeated screens and transitions become persistent learned states. Raw OCR text is not saved. Confidence scores are currently uncalibrated evidence scores, not proof that an app operation succeeded.",
                11f, MUTED, false
            ).apply { setPadding(0, dp(8), 0, 0) })
            addView(action("CORRECT / TEACH THE LOCAL MODEL") {
                startActivity(Intent(this@MainActivity, LearningCorrectionActivity::class.java))
            })
        })

        root.addView(section("CAPTURED DATA · LIVE VIEW"))
        root.addView(card().apply {
            addView(label("UI STRUCTURE & REPEATED SCREEN MATCHES", 12f, ACCENT, true))
            uiEvidencePreview = label("No UI evidence yet.", 12f, Color.WHITE, false).apply {
                setPadding(0, dp(8), 0, dp(14))
            }
            addView(uiEvidencePreview)
            addView(label("NETWORK METADATA TIMELINE", 12f, ACCENT, true))
            networkEvidencePreview = label("No network metadata yet.", 12f, Color.WHITE, false).apply {
                setPadding(0, dp(8), 0, 0)
            }
            addView(networkEvidencePreview)
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

    private fun selectScreenshot(delta: Int) {
        if (screenshotHistory.isEmpty()) return
        selectedScreenshotIndex = (selectedScreenshotIndex + delta)
            .coerceIn(0, screenshotHistory.lastIndex)
        renderSelectedScreenshot()
    }

    private fun renderSelectedScreenshot() {
        val selected = screenshotHistory.getOrNull(selectedScreenshotIndex) ?: return
        screenshotCaption.text = selected.packageName + " · " +
            selected.width + "×" + selected.height + " · " +
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(selected.timestampEpochMs))
        screenshotNavigation.text = "SCREEN " + (selectedScreenshotIndex + 1) +
            " OF " + screenshotHistory.size + " · " + selected.id
        if (displayedScreenshotPath != selected.file.absolutePath) {
            val oldBitmap = (screenshotPreview.drawable as? BitmapDrawable)?.bitmap
            val bitmap = BitmapFactory.decodeFile(selected.file.absolutePath)
            if (bitmap != null) {
                screenshotPreview.setImageBitmap(bitmap)
                displayedScreenshotPath = selected.file.absolutePath
                if (oldBitmap != null && oldBitmap !== bitmap && !oldBitmap.isRecycled) oldBitmap.recycle()
            }
        }
        screenshotRuleOverlay.configure(
            selected.width,
            selected.height,
            SemanticRuleStore.all(this).filter {
                it.packageName == selected.packageName &&
                    it.matchesScreenLayout(selected.width, selected.height)
            },
            ScreenUnderstandingService.regionsForScreenshot(this, selected.id)
        )
    }

    private class ScreenshotRuleOverlay(context: android.content.Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x3341E6B9
            style = Paint.Style.FILL
        }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(65, 230, 185)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            style = Paint.Style.FILL
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        private val machineFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x332F8CFF
            style = Paint.Style.FILL
        }
        private val machineBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(92, 164, 255)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val machineText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            style = Paint.Style.FILL
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        private var bitmapWidth = 0
        private var bitmapHeight = 0
        private var rules: List<SemanticRule> = emptyList()
        private var machineRegions: List<MachineRegion> = emptyList()

        fun configure(
            width: Int,
            height: Int,
            rules: List<SemanticRule>,
            machineRegions: List<MachineRegion>
        ) {
            bitmapWidth = width
            bitmapHeight = height
            this.rules = rules
            this.machineRegions = machineRegions
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (bitmapWidth <= 0 || bitmapHeight <= 0) return
            val scale = minOf(
                width.toFloat() / bitmapWidth.toFloat(),
                height.toFloat() / bitmapHeight.toFloat()
            )
            if (!scale.isFinite() || scale <= 0f) return
            val leftInset = (width - bitmapWidth * scale) / 2f
            val topInset = (height - bitmapHeight * scale) / 2f
            rules.forEach { rule ->
                val rect = RectF(
                    leftInset + rule.left * bitmapWidth * scale,
                    topInset + rule.top * bitmapHeight * scale,
                    leftInset + rule.right * bitmapWidth * scale,
                    topInset + rule.bottom * bitmapHeight * scale
                )
                canvas.drawRect(rect, fill)
                canvas.drawRect(rect, border)
                val baseline = (rect.top - 8f).coerceAtLeast(text.textSize)
                canvas.drawText(rule.label, rect.left + 6f, baseline, text)
            }
            machineRegions.forEach { machine ->
                val region = machine.region
                val rect = RectF(
                    leftInset + region.left * bitmapWidth * scale,
                    topInset + region.top * bitmapHeight * scale,
                    leftInset + region.right * bitmapWidth * scale,
                    topInset + region.bottom * bitmapHeight * scale
                )
                canvas.drawRect(rect, machineFill)
                canvas.drawRect(rect, machineBorder)
                val percentage = (machine.confidence * 100f).toInt()
                val label = machine.label.take(28) + " · " + percentage + "%"
                val baseline = (rect.top - 7f).coerceAtLeast(machineText.textSize)
                canvas.drawText(label, rect.left + 6f, baseline, machineText)
            }
        }
    }

    private fun defineMeaningRuleFromLatestScreenshot() {
        val screenshot = screenshotHistory.getOrNull(selectedScreenshotIndex)
        if (screenshot == null) {
            Toast.makeText(this, "Capture a screenshot first.", Toast.LENGTH_SHORT).show()
            return
        }
        val target = ObservationStore.activePackage(this) ?: screenshot.packageName
        val intent = Intent(this, SemanticRuleActivity::class.java).apply {
            putExtra(SemanticRuleActivity.EXTRA_IMAGE_PATH, screenshot.file.absolutePath)
            putExtra(SemanticRuleActivity.EXTRA_PACKAGE, target)
            putExtra(SemanticRuleActivity.EXTRA_SCREENSHOT_ID, screenshot.id)
        }
        startActivityForResult(intent, REQUEST_RULE_EDITOR)
    }

    private fun importPreviousEvidence() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

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
                    .onSuccess { Toast.makeText(this, "Evidence ZIP with screenshots, rules and authorised payloads exported.", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(this, "Export failed: " + it.message, Toast.LENGTH_LONG).show() }
            }
            REQUEST_IMPORT -> {
                if (resultCode != RESULT_OK) return
                val uri: Uri = data?.data ?: return
                runCatching { ObservationStore.importBundle(this, uri) }
                    .onSuccess {
                        displayedScreenshotPath = null
                        screenshotHistory = emptyList()
                        selectedScreenshotIndex = -1
                        refresh()
                        Toast.makeText(this, "Previous session and meaning rules imported.", Toast.LENGTH_LONG).show()
                    }
                    .onFailure { Toast.makeText(this, "Import failed: " + it.message, Toast.LENGTH_LONG).show() }
            }
            REQUEST_RULE_EDITOR -> {
                if (resultCode == RESULT_OK) refresh()
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
        val captureCounters = report.optJSONObject("uiCaptureDiagnostics")
            ?.optJSONObject("counters") ?: JSONObject()
        val received = captureCounters.optLong("events_received")
        val matched = captureCounters.optLong("events_target_match")
        val roots = captureCounters.optLong("root_acquired")
        val snapshots = captureCounters.optLong("hierarchy_snapshots")
        val writeFailures = captureCounters.optLong("ui_write_failures")
        val pipelineFailures = captureCounters.optLong("pipeline_failures")
        val pipelineSummary = "UI pipeline: " + received + " events · " + matched +
            " target matches · " + roots + " roots · " + snapshots +
            " snapshots · " + writeFailures + " write failures · " + pipelineFailures + " processing failures"
        val screenshots = captureCounters.optLong("screenshots_stored")
        val screenshotFailures = captureCounters.optLong("screenshots_failed")
        val analysisAttempts = captureCounters.optLong("screen_analysis_attempts")
        val ocrSuccesses = captureCounters.optLong("screen_ocr_successes")
        val ocrFailures = captureCounters.optLong("screen_ocr_failures")
        val imageLabelSuccesses = captureCounters.optLong("screen_image_label_successes")
        val imageLabelFailures = captureCounters.optLong("screen_image_label_failures")
        val insightWrites = captureCounters.optLong("screen_insights_written")
        val analysisFailures = captureCounters.optLong("screen_analysis_failures")
        val statesReused = captureCounters.optLong("screen_states_reused")
        val statesCreated = captureCounters.optLong("screen_states_created")
        val visualSummary = "Screenshots: " + screenshots + " saved · " + screenshotFailures + " failed" +
            "\nMachine analysis: " + analysisAttempts + " attempted · " + insightWrites + " insights saved · " +
            analysisFailures + " failed" +
            "\nOCR: " + ocrSuccesses + " passed / " + ocrFailures + " failed · image labels: " +
            imageLabelSuccesses + " passed / " + imageLabelFailures + " failed" +
            "\nLearned states: " + statesCreated + " new · " + statesReused + " reused"
        reportPreview.text = listOf(
            "Android " + android.optString("release") + " · API " + android.optInt("apiLevel"),
            "Accessibility: " + accessibilityText,
            "Notification metadata: " + notificationText,
            "Target discovery: dynamic launchable-app list + explicit self-test",
            pipelineSummary,
            visualSummary
        ).joinToString("\n")

        val active = ObservationStore.hasActiveSession(this)
        val directory = ObservationStore.latestDirectory(this)
        val uiFile = directory?.let { File(it, "ui-events.jsonl") }
        val networkFile = directory?.let { File(it, "network-events.jsonl") }
        val uiCount = countLines(uiFile)
        val networkCount = countLines(networkFile)
        val activeTarget = ObservationStore.activePackage(this)
        val observedTarget = activeTarget ?: directory?.let {
            runCatching { JSONObject(File(it, "session.json").readText()).optString("targetPackage") }.getOrNull()
        }
        uiEvidencePreview.text = EvidencePreview.ui(uiFile, uiCount)
        networkEvidencePreview.text = EvidencePreview.network(networkFile, networkCount)
        if (::machineUnderstandingPreview.isInitialized) {
            machineUnderstandingPreview.text = ScreenUnderstandingService.preview(this)
        }
        if (::screenshotPreview.isInitialized) {
            val previousPath = displayedScreenshotPath
            val wasFollowingLatest = screenshotHistory.isEmpty() ||
                selectedScreenshotIndex >= screenshotHistory.lastIndex
            screenshotHistory = ObservationStore.screenshots(this)
            if (screenshotHistory.isEmpty()) {
                selectedScreenshotIndex = -1
                displayedScreenshotPath = null
                screenshotPreview.setImageDrawable(null)
                screenshotRuleOverlay.configure(0, 0, emptyList(), emptyList())
                screenshotCaption.text = "No screenshots yet. Start a session, switch to the selected app and interact with it."
                screenshotNavigation.text = "No screenshot selected."
            } else {
                val retainedIndex = screenshotHistory.indexOfFirst {
                    it.file.absolutePath == previousPath
                }
                selectedScreenshotIndex = if (!wasFollowingLatest && retainedIndex >= 0) {
                    retainedIndex
                } else screenshotHistory.lastIndex
                renderSelectedScreenshot()
            }
            semanticRulesPreview.text = SemanticRuleStore.describe(this)
            val semanticFile = ObservationStore.latestDirectory(this)?.let {
                File(it, "semantic-payloads.jsonl")
            }
            val semanticCount = countLines(semanticFile)
            semanticPayloadPreview.text = EvidencePreview.semanticPayloads(
                semanticFile, semanticCount, SemanticRuleStore.all(this)
            )
        }

        sessionStatus.text = if (active) {
            "OBSERVING · " + (observedTarget ?: "selected app") +
                "\nUI structure events: " + uiCount + " · network metadata events: " + networkCount +
                "\n" + pipelineSummary +
                "\nTCP exporter: " + PcapdroidCaptureService.liveStatus() +
                " · packet headers inspected: " + PcapdroidCaptureService.livePackets() +
                " · packet bytes represented: " + PcapdroidCaptureService.liveBytes()
        } else if (directory != null) {
            "LAST SESSION · " + (observedTarget ?: "selected app") +
                "\nUI structure events: " + uiCount + " · network metadata events: " + networkCount +
                "\n" + pipelineSummary +
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

    private fun rowOfActions(first: Button, second: Button): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(second, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
        private const val REQUEST_IMPORT = 7155
        private const val REQUEST_RULE_EDITOR = 7156
        private const val PCAPDROID_CAPTURE_ACTIVITY = "com.emanuelef.remote_capture.activities.CaptureCtrl"
        private const val MAX_CAPTURE_BYTES = 52_428_800
        private val ACCENT = Color.rgb(86, 225, 185)
        private val MUTED = Color.rgb(148, 164, 183)
    }
}
