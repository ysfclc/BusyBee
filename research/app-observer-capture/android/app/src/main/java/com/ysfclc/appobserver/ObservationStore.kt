package com.ysfclc.appobserver

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Local, explicit-session evidence store. New sessions retain structure and allowlisted
 * metadata only; sensitive legacy fields are stripped again at export.
 */
object ObservationStore {
    private const val PREFS = "observation_session"
    private const val ACTIVE_ID = "active_session_id"
    private const val LAST_ID = "last_session_id"
    private const val TARGET_PACKAGE = "target_package"
    private const val TARGET_LABEL = "target_label"
    private val lock = Any()

    fun startSession(context: Context, packageName: String, label: String): String {
        synchronized(lock) {
            val id = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(8)
            val directory = sessionDirectory(context, id)
            check(directory.mkdirs() || directory.isDirectory) { "Could not create session directory" }
            val now = System.currentTimeMillis()
            val metadata = JSONObject()
                .put("schema", "app-observer-session/v2")
                .put("sessionId", id)
                .put("startedAtEpochMs", now)
                .put("targetPackage", packageName)
                .put("targetLabel", label)
                .put("observationProfile", "structure_and_network_metadata_only")
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
                .put("privacy", JSONObject()
                    .put("localOnly", true)
                    .put("visibleTextStored", false)
                    .put("typedValuesStored", false)
                    .put("notificationContentStored", false)
                    .put("screenshotsStored", false)
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
                .put("evidenceProfile", "privacy_minimised"))
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

    fun appendUiEvent(context: Context, event: JSONObject) =
        appendToActive(context, "ui-events.jsonl", event)

    fun appendNetworkEvent(context: Context, event: JSONObject) =
        appendToActive(context, "network-events.jsonl", ObservationPrivacy.safeNetworkMetadata(event))

    fun appendSessionEvent(context: Context, event: JSONObject) =
        appendToActive(context, "events.jsonl", event)

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
                writeJson(metadataFile, root)
            }
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
                listOf("events.jsonl", "ui-events.jsonl", "network-events.jsonl").forEach { name ->
                    val file = File(directory, name)
                    if (file.isFile) writeSanitisedJsonLines(zip, name, file)
                }

                val diagnostics = ObserverReport.build(context).toString(2).toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("device-capability-report.json"))
                zip.write(diagnostics)
                zip.closeEntry()

                val notice = ("Privacy-minimised evidence export. No visible or typed text, notification bodies, " +
                    "screenshots, clipboard contents, network payloads, raw IP addresses or raw PCAP files are included. " +
                    "Network packet headers are parsed in memory and only allowlisted metadata is retained. " +
                    "The selected app identifier and structural UI metadata remain in the evidence so patterns can be matched. " +
                    "Review the target app and exported records before sharing.").toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("PRIVACY-AND-REVIEW.txt"))
                zip.write(notice)
                zip.closeEntry()
            }
        } ?: error("Could not open the selected export destination")
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
            appendLine(
                File(sessionDirectory(context, id), fileName),
                ObservationPrivacy.sanitizeForExport(event)
            )
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
