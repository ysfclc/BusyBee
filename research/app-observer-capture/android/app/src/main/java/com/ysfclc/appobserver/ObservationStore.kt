package com.ysfclc.appobserver

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class StoredScreenshot(
    val id: String,
    val file: File,
    val width: Int,
    val height: Int,
    val timestampEpochMs: Long,
    val packageName: String
)

/**
 * Local evidence store. Original structure/network evidence remains intact; screenshots and
 * explicitly rule-authorised semantic payloads are additional, separately linked streams.
 */
object ObservationStore {
    private const val PREFS = "observation_session"
    private const val ACTIVE_ID = "active_session_id"
    private const val LAST_ID = "last_session_id"
    private const val TARGET_PACKAGE = "target_package"
    private const val TARGET_LABEL = "target_label"
    private const val MAX_IMPORT_BYTES = 120L * 1024L * 1024L
    private val lock = Any()

    fun startSession(context: Context, packageName: String, label: String): String {
        synchronized(lock) {
            ObserverSignals.resetCaptureDiagnostics(context)
            val id = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(8)
            val directory = sessionDirectory(context, id)
            check(directory.mkdirs() || directory.isDirectory) { "Could not create session directory" }
            val now = System.currentTimeMillis()
            val metadata = JSONObject()
                .put("schema", "app-observer-session/v3")
                .put("sessionId", id)
                .put("startedAtEpochMs", now)
                .put("targetPackage", packageName)
                .put("targetLabel", label)
                .put("observationProfile", "screenshots_structure_and_user_ruled_semantics")
                .put("device", JSONObject()
                    .put("androidRelease", android.os.Build.VERSION.RELEASE)
                    .put("apiLevel", android.os.Build.VERSION.SDK_INT))
                .put("pcapdroid", JSONObject()
                    .put("requested", true)
                    .put("mode", "tcp_exporter_metadata_only")
                    .put("appFilter", packageName)
                    .put("collector", "127.0.0.1:5123")
                    .put("tlsDecryptionRequested", false)
                    .put("fullPayloadRequested", false)
                    .put("rawPacketPersistence", false)
                    .put("status", "starting"))
                .put("visualEvidence", JSONObject()
                    .put("screenshotsRequested", true)
                    .put("screenshotsStored", true)
                    .put("captureMethod", "android_accessibility_service_screenshot_api")
                    .put("userDefinedSemanticRules", true)
                    .put("semanticTextReadOnlyForExplicitRules", true))
                .put("privacy", JSONObject()
                    .put("localOnly", true)
                    .put("visibleTextStoredByDefault", false)
                    .put("typedValuesStoredByDefault", false)
                    .put("notificationContentStoredByDefault", false)
                    .put("semanticPayloadsRequireUserRule", true)
                    .put("screenshotsStored", true)
                    .put("clipboardStored", false)
                    .put("networkPayloadStored", false)
                    .put("rawIpAddressesStored", false)
                    .put("rawPacketCaptureStored", false)
                    .put("reviewBeforeSharing", true))
            writeJson(File(directory, "session.json"), metadata)
            appendLine(File(directory, "events.jsonl"), JSONObject()
                .put("type", "session_started")
                .put("timestampEpochMs", now)
                .put("targetPackage", packageName)
                .put("evidenceProfile", "visual_structure_and_user_ruled_semantics"))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(ACTIVE_ID, id)
                .putString(LAST_ID, id)
                .putString(TARGET_PACKAGE, packageName)
                .putString(TARGET_LABEL, label)
                .apply()
            return id
        }
    }

    fun hasActiveSession(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ACTIVE_ID, null) != null

    fun activeSessionId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ACTIVE_ID, null)

    fun activePackage(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TARGET_PACKAGE, null)

    fun activeDirectory(context: Context): File? =
        activeSessionId(context)?.let { sessionDirectory(context, it) }

    fun latestDirectory(context: Context): File? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(ACTIVE_ID, null) ?: prefs.getString(LAST_ID, null) ?: return null
        val directory = sessionDirectory(context, id)
        return directory.takeIf { it.isDirectory }
    }

    fun screenshots(context: Context, limit: Int = 80): List<StoredScreenshot> {
        if (limit <= 0) return emptyList()
        val directory = latestDirectory(context) ?: return emptyList()
        val index = File(directory, "screenshots/index.jsonl")
        if (!index.isFile) return emptyList()
        return runCatching {
            val records = index.useLines { lines ->
                lines.filter { it.isNotBlank() }.toList().takeLast(limit)
            }
            records.mapNotNull { line ->
                runCatching {
                    val record = JSONObject(line)
                    val relative = record.optString("file")
                    if (!relative.matches(Regex("screenshots/[A-Za-z0-9_-]+\\.jpg"))) return@runCatching null
                    val image = File(directory, relative)
                    if (!image.isFile) return@runCatching null
                    StoredScreenshot(
                        record.optString("screenshotId"), image,
                        record.optInt("width"), record.optInt("height"),
                        record.optLong("timestampEpochMs"), record.optString("packageName")
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    fun latestScreenshot(context: Context): StoredScreenshot? =
        screenshots(context, limit = 1).lastOrNull()

    fun storeScreenshot(
        context: Context,
        sessionId: String,
        packageName: String,
        eventType: String,
        timestampEpochMs: Long,
        bitmap: Bitmap
    ): StoredScreenshot? = synchronized(lock) {
        if (activeSessionId(context) != sessionId) return@synchronized null
        val directory = sessionDirectory(context, sessionId)
        val id = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(8)
        val image = File(directory, "screenshots/$id.jpg")
        image.parentFile?.mkdirs()
        val stored = runCatching {
            FileOutputStream(image).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Screenshot compression failed." }
                output.flush()
            }
            val record = JSONObject()
                .put("screenshotId", id)
                .put("timestampEpochMs", timestampEpochMs)
                .put("packageName", packageName)
                .put("eventType", eventType)
                .put("file", "screenshots/$id.jpg")
                .put("width", bitmap.width)
                .put("height", bitmap.height)
            appendLine(File(directory, "screenshots/index.jsonl"), record)
            appendLine(File(directory, "ui-events.jsonl"), JSONObject()
                .put("timestampEpochMs", timestampEpochMs)
                .put("packageName", packageName)
                .put("eventType", "visual_screenshot_captured")
                .put("screenshotId", id)
                .put("screenshotFile", "screenshots/$id.jpg")
                .put("screenshotWidth", bitmap.width)
                .put("screenshotHeight", bitmap.height)
                .put("linkedEventType", eventType))
            StoredScreenshot(id, image, bitmap.width, bitmap.height, timestampEpochMs, packageName)
        }.getOrNull()
        if (stored == null) image.delete()
        stored
    }

    fun appendUiEvent(context: Context, event: JSONObject) =
        appendToActive(context, "ui-events.jsonl", event)

    fun appendNetworkEvent(context: Context, event: JSONObject) =
        appendToActive(context, "network-events.jsonl", ObservationPrivacy.safeNetworkMetadata(event))

    fun appendSessionEvent(context: Context, event: JSONObject) =
        appendToActive(context, "events.jsonl", event)

    fun appendSemanticPayload(context: Context, sessionId: String, event: JSONObject): Boolean =
        synchronized(lock) {
            if (activeSessionId(context) != sessionId) return@synchronized false
            runCatching {
                appendLine(
                    File(sessionDirectory(context, sessionId), "semantic-payloads.jsonl"),
                    event
                )
            }.isSuccess
        }

    fun latestScreenshotId(context: Context, sessionId: String? = activeSessionId(context)): String? {
        val id = sessionId ?: return null
        val file = File(sessionDirectory(context, id), "screenshots/index.jsonl")
        if (!file.isFile) return null
        return runCatching {
            file.useLines { lines ->
                lines.filter { it.isNotBlank() }.lastOrNull()
                    ?.let { JSONObject(it).optString("screenshotId").takeIf(String::isNotBlank) }
            }
        }.getOrNull()
    }

    fun markPcapdroidCommandAccepted(context: Context, versionName: String?, versionCode: Int) {
        updateActiveMetadata(context) { root ->
            root.getJSONObject("pcapdroid")
                .put("status", "command_accepted")
                .put("versionName", versionName ?: JSONObject.NULL)
                .put("versionCode", versionCode)
                .put("startCommandAcceptedAtEpochMs", System.currentTimeMillis())
        }
        appendSessionEvent(context, JSONObject()
            .put("type", "pcapdroid_start_command_accepted")
            .put("timestampEpochMs", System.currentTimeMillis())
            .put("versionName", versionName ?: JSONObject.NULL)
            .put("versionCode", versionCode))
    }

    fun hasPcapdroidCommandAccepted(context: Context): Boolean {
        val directory = activeDirectory(context) ?: return false
        val file = File(directory, "session.json")
        if (!file.isFile) return false
        return runCatching {
            JSONObject(file.readText()).getJSONObject("pcapdroid")
                .optString("status") == "command_accepted"
        }.getOrDefault(false)
    }

    fun markPcapdroidUnavailable(context: Context, reason: String) {
        updateActiveMetadata(context) { root ->
            root.getJSONObject("pcapdroid")
                .put("status", "unavailable")
                .put("reason", reason)
        }
    }

    fun finishSession(context: Context, reason: String) {
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val id = prefs.getString(ACTIVE_ID, null) ?: return
            val directory = sessionDirectory(context, id)
            val now = System.currentTimeMillis()
            val metadataFile = File(directory, "session.json")
            if (metadataFile.exists()) {
                val root = runCatching { JSONObject(metadataFile.readText()) }.getOrElse { JSONObject() }
                root.put("finishedAtEpochMs", now)
                    .put("durationMs", now - root.optLong("startedAtEpochMs", now))
                    .put("finishReason", reason)
                root.optJSONObject("pcapdroid")?.put("status", "stopped")
                val networkIndex = File(directory, "network-events.jsonl")
                root.put("networkCapture", JSONObject()
                    .put("metadataOnly", true)
                    .put("rawPacketFileCreated", false)
                    .put("metadataFilePresent", networkIndex.isFile)
                    .put("metadataFileBytes", if (networkIndex.isFile) networkIndex.length() else 0L))
                root.put("semanticRuleCount", SemanticRuleStore.all(context).count { it.packageName == activePackage(context) })
                writeJson(metadataFile, root)
            }
            appendLine(File(directory, "events.jsonl"), JSONObject()
                .put("type", "ui_capture_diagnostics")
                .put("timestampEpochMs", now)
                .put("diagnostics", ObserverSignals.captureDiagnostics(context)))
            appendLine(File(directory, "events.jsonl"), JSONObject()
                .put("type", "session_finished")
                .put("timestampEpochMs", now)
                .put("reason", reason))
            prefs.edit().remove(ACTIVE_ID).apply()
        }
    }

    fun exportBundle(context: Context, destination: Uri) {
        val directory = latestDirectory(context) ?: error("No observation session exists yet")
        context.contentResolver.openOutputStream(destination)?.use { output ->
            ZipOutputStream(output).use { zip ->
                val sessionFile = File(directory, "session.json")
                if (sessionFile.isFile) {
                    zip.putNextEntry(ZipEntry("session.json"))
                    val safe = ObservationPrivacy.sanitizeForExport(JSONObject(sessionFile.readText()))
                    zip.write(safe.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                listOf("events.jsonl", "ui-events.jsonl", "network-events.jsonl",
                    "semantic-payloads.jsonl").forEach { name ->
                    val file = File(directory, name)
                    if (file.isFile) writeSanitisedJsonLines(zip, name, file)
                }
                zip.putNextEntry(ZipEntry("semantic-rules.json"))
                zip.write(SemanticRuleStore.exportJson(context).toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                addScreenshotFiles(zip, directory)

                val diagnostics = ObserverReport.build(context).toString(2).toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("device-capability-report.json"))
                zip.write(diagnostics)
                zip.closeEntry()

                val notice = ("User-reviewed App Observer evidence bundle. Screenshots are stored as visual evidence. " +
                    "Text payloads are present only for regions where the user created an explicit meaning rule allowing " +
                    "Accessibility text or notification content. These payloads may contain private information; review " +
                    "the bundle before sharing. No network payloads, raw IP addresses or raw PCAP files are included. " +
                    "Semantic rules and their screenshot region selectors are included so the evidence can be interpreted.").toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("PRIVACY-AND-REVIEW.txt"))
                zip.write(notice)
                zip.closeEntry()
            }
        } ?: error("Could not open the selected export destination")
    }

    fun importBundle(context: Context, source: Uri): String {
        val input = context.contentResolver.openInputStream(source) ?: error("Could not open the evidence ZIP.")
        val id = "imported-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().take(6)
        val directory = sessionDirectory(context, id)
        check(directory.mkdirs()) { "Could not create imported session." }
        var totalBytes = 0L
        var importedPackage = ""
        var importedLabel = "Imported evidence"
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    val safePath = name.matches(Regex(
                        "(session\\.json|events\\.jsonl|ui-events\\.jsonl|network-events\\.jsonl|semantic-payloads\\.jsonl|semantic-rules\\.json|device-capability-report\\.json|PRIVACY-AND-REVIEW\\.txt|screenshots/[A-Za-z0-9_-]+\\.jpg|screenshots/index\\.jsonl)"
                    ))
                    if (!entry.isDirectory && safePath) {
                        val target = File(directory, name)
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                totalBytes += read
                                require(totalBytes <= MAX_IMPORT_BYTES) { "Evidence ZIP exceeds the 120 MB import limit." }
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                        if (name == "session.json") {
                            val meta = JSONObject(target.readText())
                            importedPackage = meta.optString("targetPackage")
                            importedLabel = meta.optString("targetLabel", importedLabel)
                            meta.put("sessionId", id)
                                .put("importedAtEpochMs", System.currentTimeMillis())
                                .put("importedFromPreviousInstall", true)
                            writeJson(target, meta)
                        } else if (name.endsWith(".jsonl")) {
                            sanitiseJsonLinesInPlace(target)
                        } else if (name == "semantic-rules.json") {
                            runCatching { SemanticRuleStore.importJson(context, JSONObject(target.readText())) }
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (failure: Throwable) {
            directory.deleteRecursively()
            throw failure
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(LAST_ID, id)
            .putString(TARGET_PACKAGE, importedPackage)
            .putString(TARGET_LABEL, importedLabel)
            .remove(ACTIVE_ID)
            .apply()
        appendLine(File(directory, "events.jsonl"), JSONObject()
            .put("type", "session_imported")
            .put("timestampEpochMs", System.currentTimeMillis())
            .put("source", "user_selected_evidence_zip"))
        return id
    }

    private fun addScreenshotFiles(zip: ZipOutputStream, directory: File) {
        val screenshotDir = File(directory, "screenshots")
        if (!screenshotDir.isDirectory) return
        screenshotDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(directory).invariantSeparatorsPath
            if (relative.matches(Regex("screenshots/[A-Za-z0-9_-]+\\.jpg|screenshots/index\\.jsonl"))) {
                zip.putNextEntry(ZipEntry(relative))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun sanitiseJsonLinesInPlace(file: File) {
        val temp = File(file.parentFile, file.name + ".sanitised")
        file.useLines { lines ->
            FileOutputStream(temp, false).bufferedWriter(Charsets.UTF_8).use { writer ->
                lines.forEach { line ->
                    val safe = runCatching {
                        ObservationPrivacy.sanitizeForExport(JSONObject(line)).toString()
                    }.getOrNull()
                    if (safe != null) writer.append(safe).append('\n')
                }
            }
        }
        if (temp.isFile) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private fun writeSanitisedJsonLines(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        val writer = OutputStreamWriter(zip, Charsets.UTF_8)
        file.useLines { lines ->
            lines.forEach { line ->
                val safe = runCatching {
                    ObservationPrivacy.sanitizeForExport(JSONObject(line)).toString()
                }.getOrNull()
                if (safe != null) {
                    writer.write(safe)
                    writer.write("\n")
                }
            }
        }
        writer.flush()
        zip.closeEntry()
    }

    private fun appendToActive(context: Context, fileName: String, event: JSONObject) {
        synchronized(lock) {
            val id = activeSessionId(context) ?: return
            appendLine(File(sessionDirectory(context, id), fileName), event)
        }
    }

    private fun updateActiveMetadata(context: Context, update: (JSONObject) -> Unit) {
        synchronized(lock) {
            val id = activeSessionId(context) ?: return
            val file = File(sessionDirectory(context, id), "session.json")
            if (!file.exists()) return
            val root = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
            update(root)
            writeJson(file, root)
        }
    }

    private fun sessionDirectory(context: Context, id: String) =
        File(File(context.filesDir, "observer-sessions"), id)

    private fun writeJson(file: File, value: JSONObject) {
        file.parentFile?.mkdirs()
        FileOutputStream(file, false).bufferedWriter(Charsets.UTF_8).use {
            it.write(ObservationPrivacy.sanitizeForExport(value).toString(2))
        }
    }

    private fun appendLine(file: File, value: JSONObject) {
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use {
            it.append(ObservationPrivacy.sanitizeForExport(value).toString()).append('\n')
        }
    }
}
