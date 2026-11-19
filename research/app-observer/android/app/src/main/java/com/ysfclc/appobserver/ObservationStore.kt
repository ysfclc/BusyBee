package com.ysfclc.appobserver

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Local evidence store for one explicitly started research session.
 * Nothing in this object uploads data or opens a remote connection.
 */
object ObservationStore {
    private const val PREFS = "observation_session"
    private const val ACTIVE_ID = "active_session_id"
    private const val LAST_ID = "last_session_id"
    private const val TARGET_PACKAGE = "target_package"
    private const val TARGET_LABEL = "target_label"
    private const val INCLUDE_UI_TEXT = "include_ui_text"
    private val lock = Any()

    fun startSession(context: Context, packageName: String, label: String, includeUiText: Boolean, includePayloads: Boolean): String {
        synchronized(lock) {
            val id = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(8)
            val directory = sessionDirectory(context, id)
            check(directory.mkdirs() || directory.isDirectory) { "Could not create session directory" }
            val now = System.currentTimeMillis()
            val metadata = JSONObject()
                .put("schema", "app-observer-session/v1")
                .put("sessionId", id)
                .put("startedAtEpochMs", now)
                .put("targetPackage", packageName)
                .put("targetLabel", label)
                .put("includeVisibleUiText", includeUiText)
                .put("device", JSONObject()
                    .put("manufacturer", android.os.Build.MANUFACTURER)
                    .put("model", android.os.Build.MODEL)
                    .put("androidRelease", android.os.Build.VERSION.RELEASE)
                    .put("apiLevel", android.os.Build.VERSION.SDK_INT))
                .put("pcapdroid", JSONObject()
                    .put("requested", true)
                    .put("mode", "udp_exporter")
                    .put("appFilter", packageName)
                    .put("collector", "127.0.0.1:5123")
                    .put("tlsDecryptionRequested", includePayloads)
                    .put("fullPayloadRequested", includePayloads)
                    .put("status", "starting"))
                .put("privacy", JSONObject()
                    .put("localOnly", true)
                    .put("visibleTextOptIn", includeUiText)
                    .put("editableFieldsExcluded", true)
                    .put("passwordFieldsExcluded", true)
                    .put("reviewBeforeSharing", true))
            writeJson(File(directory, "session.json"), metadata)
            appendLine(File(directory, "events.jsonl"), JSONObject()
                .put("type", "session_started")
                .put("timestampEpochMs", now)
                .put("targetPackage", packageName))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(ACTIVE_ID, id)
                .putString(LAST_ID, id)
                .putString(TARGET_PACKAGE, packageName)
                .putString(TARGET_LABEL, label)
                .putBoolean(INCLUDE_UI_TEXT, includeUiText)
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

    fun includeUiText(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(INCLUDE_UI_TEXT, false)

    fun appendUiEvent(context: Context, event: JSONObject) = appendToActive(context, "ui-events.jsonl", event)
    fun appendNetworkEvent(context: Context, event: JSONObject) = appendToActive(context, "network-events.jsonl", event)

    fun appendSessionEvent(context: Context, event: JSONObject) = appendToActive(context, "events.jsonl", event)

    fun markPcapdroidStarted(context: Context, versionName: String?, versionCode: Int) {
        updateActiveMetadata(context) { root ->
            root.getJSONObject("pcapdroid")
                .put("status", "running")
                .put("versionName", versionName ?: JSONObject.NULL)
                .put("versionCode", versionCode)
        }
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
                val pcapFile = File(directory, "network.pcap")
                root.put("networkCapture", JSONObject()
                    .put("filePresent", pcapFile.exists())
                    .put("fileBytes", if (pcapFile.exists()) pcapFile.length() else 0L))
                writeJson(metadataFile, root)
            }
            appendLine(File(directory, "events.jsonl"), JSONObject()
                .put("type", "session_finished")
                .put("timestampEpochMs", now)
                .put("reason", reason))
            prefs.edit().remove(ACTIVE_ID).apply()
        }
    }

    fun activeDirectory(context: Context): File? =
        activeSessionId(context)?.let { sessionDirectory(context, it) }

    fun latestDirectory(context: Context): File? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(ACTIVE_ID, null) ?: prefs.getString(LAST_ID, null) ?: return null
        val directory = sessionDirectory(context, id)
        return directory.takeIf { it.isDirectory }
    }

    fun exportBundle(context: Context, destination: Uri) {
        val directory = latestDirectory(context) ?: error("No observation session exists yet")
        context.contentResolver.openOutputStream(destination)?.use { output ->
            ZipOutputStream(output).use { zip ->
                listOf("session.json", "events.jsonl", "ui-events.jsonl", "network-events.jsonl", "network.pcap")
                    .forEach { name ->
                        val file = File(directory, name)
                        if (file.isFile) {
                            zip.putNextEntry(ZipEntry(name))
                            file.inputStream().use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                val diagnostics = ObserverReport.build(context).toString(2).toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("device-capability-report.json"))
                zip.write(diagnostics)
                zip.closeEntry()
                val notice = ("Review the exported evidence before sharing. UI text may contain names, addresses, " +
                    "trip details or other personal information that automatic redaction cannot reliably identify. " +
                    "Network capture may contain decrypted application payloads.").toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("REVIEW-BEFORE-SHARING.txt"))
                zip.write(notice)
                zip.closeEntry()
            }
        } ?: error("Could not open the selected export destination")
    }

    fun sanitizeVisibleText(value: CharSequence?): String {
        var safe = value?.toString()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (safe.isBlank()) return ""
        safe = safe.replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer [REDACTED]")
        safe = safe.replace(Regex("(?i)(access[_-]?token|refresh[_-]?token|authorization|password|session[_-]?id)\\s*[:=]\\s*[^\\s,;]+")) { match -> match.groupValues[1] + "=[REDACTED]" }
        safe = safe.replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "[EMAIL]")
        safe = safe.replace(Regex("(?<!\\w)\\+?\\d[\\d ().-]{7,}\\d(?!\\w)"), "[PHONE]")
        safe = safe.replace(Regex("\\b[A-Za-z0-9_-]{40,}\\b"), "[LONG_TOKEN]")
        return safe.take(240)
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
        FileOutputStream(file, false).bufferedWriter(Charsets.UTF_8).use { it.write(value.toString(2)) }
    }

    private fun appendLine(file: File, value: JSONObject) {
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use {
            it.append(value.toString()).append('\n')
        }
    }
}
