package com.ysfclc.appobserver

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * On-device multimodal screen-understanding pipeline.
 *
 * The screenshot is analysed with bundled ML Kit OCR and image labelling. Results are fused
 * with the nearest Accessibility snapshot, interaction events, authorised notification text,
 * notification envelopes and temporally adjacent packet metadata. OCR is never uploaded and
 * arbitrary recognised text is not written to the evidence stream.
 */
object ScreenUnderstandingService {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val imageLabeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private const val CORRELATION_WINDOW_MS = 8_000L

    fun analyse(context: Context, sessionId: String, screenshot: StoredScreenshot) {
        if (!inFlight.add(screenshot.id)) return
        val bitmap = runCatching { BitmapFactory.decodeFile(screenshot.file.absolutePath) }.getOrNull()
        if (bitmap == null) {
            inFlight.remove(screenshot.id)
            persistFailure(context, sessionId, screenshot, "screenshot_decode_failed")
            return
        }
        val image = runCatching { InputImage.fromBitmap(bitmap, 0) }.getOrNull()
        if (image == null) {
            bitmap.recycle()
            inFlight.remove(screenshot.id)
            persistFailure(context, sessionId, screenshot, "ml_input_unavailable")
            return
        }

        val textTask = runCatching { recognizer.process(image) }.getOrNull()
        val labelTask = runCatching { imageLabeler.process(image) }.getOrNull()
        if (textTask == null && labelTask == null) {
            bitmap.recycle()
            inFlight.remove(screenshot.id)
            persistFailure(context, sessionId, screenshot, "on_device_models_unavailable")
            return
        }

        val tasks = listOfNotNull(textTask, labelTask)
        Tasks.whenAllComplete(tasks).addOnCompleteListener {
            try {
                val lines = textTask?.takeIf { task -> task.isSuccessful }?.result?.textBlocks
                    ?.flatMap { block -> block.lines }
                    ?.map { line ->
                        val bounds = line.boundingBox
                        OcrLineEvidence(
                            line.text,
                            bounds?.left, bounds?.top, bounds?.right, bounds?.bottom
                        )
                    }.orEmpty()
                val visualLabels = labelTask?.takeIf { task -> task.isSuccessful }?.result
                    ?.map { VisualLabelEvidence(it.text, it.confidence) }.orEmpty()
                val evidence = fuseSources(
                    context, sessionId, screenshot, lines, visualLabels
                )
                val rawPredictions = MultimodalInference.classify(evidence)
                val featureVector = MultimodalInference.featureVector(evidence)
                val learned = LocalLearningMemory.learnedProbabilities(
                    context, evidence.packageName, featureVector
                )
                val predictions = rawPredictions.map { guess ->
                    val trainedProbability = learned[guess.concept]
                    if (trainedProbability == null) guess else guess.copy(
                        confidence = (guess.confidence * 0.82f + trainedProbability * 0.18f)
                            .coerceIn(0.03f, 0.98f)
                    )
                }.sortedByDescending { it.confidence }
                val state = LocalLearningMemory.observe(
                    context, evidence.packageName, featureVector, predictions,
                    evidence.timestampEpochMs,
                    evidence.interactionType.ifBlank { "screen_observed" }
                )
                val priceEvidence = predictions.firstOrNull {
                    it.concept == "price_or_fare" && it.confidence >= 0.48f
                }
                val entities = if (priceEvidence != null) {
                    MultimodalInference.extractEntities(evidence)
                } else emptyList()

                val concepts = JSONArray().apply {
                    predictions.forEach { guess ->
                        put(JSONObject()
                            .put("concept", guess.concept)
                            .put("confidence", guess.confidence.toDouble())
                            .put("evidenceSources", JSONArray(guess.sources))
                            .put("evidenceStrength", guess.score.toDouble())
                            .put("region", guess.region?.let {
                                JSONObject().put("left", it.left.toDouble())
                                    .put("top", it.top.toDouble())
                                    .put("right", it.right.toDouble())
                                    .put("bottom", it.bottom.toDouble())
                            } ?: JSONObject.NULL))
                    }
                }
                val entityArray = JSONArray().apply {
                    entities.forEach { (amount, region) ->
                        put(JSONObject().put("type", "currency_amount")
                            .put("amount", amount)
                            .put("region", region?.let {
                                JSONObject().put("left", it.left.toDouble())
                                    .put("top", it.top.toDouble())
                                    .put("right", it.right.toDouble())
                                    .put("bottom", it.bottom.toDouble())
                            } ?: JSONObject.NULL))
                    }
                }
                val labelArray = JSONArray().apply {
                    visualLabels.filter { it.confidence >= 0.35f }.take(8).forEach {
                        put(JSONObject().put("label", it.label.take(48))
                            .put("confidence", it.confidence.toDouble()))
                    }
                }
                val record = JSONObject()
                    .put("schema", "app-observer-screen-understanding/v1")
                    .put("timestampEpochMs", evidence.timestampEpochMs)
                    .put("packageName", evidence.packageName)
                    .put("screenshotId", evidence.screenshotId)
                    .put("screenWidth", evidence.width)
                    .put("screenHeight", evidence.height)
                    .put("analysisStatus", when {
                        textTask?.isSuccessful == true && labelTask?.isSuccessful == true -> "complete"
                        textTask?.isSuccessful == true || labelTask?.isSuccessful == true -> "partial"
                        else -> "degraded"
                    })
                    .put("ocrLineCount", lines.size)
                    .put("concepts", concepts)
                    .put("entities", entityArray)
                    .put("visualLabels", labelArray)
                    .put("state", JSONObject()
                        .put("stateId", state.stateId)
                        .put("label", state.stateLabel)
                        .put("occurrences", state.occurrences)
                        .put("similarity", state.similarity.toDouble())
                        .put("previousStateId", state.previousStateId ?: JSONObject.NULL)
                        .put("transitionOccurrences", state.transitionOccurrences))
                    .put("correlatedEvidence", JSONObject()
                        .put("accessibilityNodeCount", evidence.accessibilityClasses.size)
                        .put("clickableCount", evidence.clickableCount)
                        .put("scrollableCount", evidence.scrollableCount)
                        .put("editableCount", evidence.editableCount)
                        .put("interactionType", evidence.interactionType)
                        .put("notificationCount", evidence.notificationCount)
                        .put("notificationCategories", JSONArray(evidence.notificationCategories.distinct()))
                        .put("networkPacketCount", evidence.networkPacketCount)
                        .put("networkBytes", evidence.networkBytes)
                        .put("networkProtocols", JSONArray(evidence.networkProtocols.distinct()))
                        .put("networkPortCount", evidence.networkPorts.distinct().size))
                    .put("learningFeatures", JSONArray(featureVector))
                ObservationStore.appendUnderstanding(context, sessionId, record)
            } catch (failure: Throwable) {
                persistFailure(
                    context, sessionId, screenshot,
                    "analysis_pipeline_" + failure.javaClass.simpleName.take(60)
                )
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                inFlight.remove(screenshot.id)
            }
        }
    }

    private fun fuseSources(
        context: Context,
        sessionId: String,
        screenshot: StoredScreenshot,
        rawLines: List<OcrLineEvidence>,
        visualLabels: List<VisualLabelEvidence>
    ): LearningEvidence {
        val uiRecords = readRecords(context, sessionId, "ui-events.jsonl", 180)
        val screenshotTime = screenshot.timestampEpochMs
        val latestTree = uiRecords.lastOrNull {
            it.optJSONArray("nodes") != null &&
                it.optLong("timestampEpochMs") <= screenshotTime + 2_000L
        }
        val nodes = latestTree?.optJSONArray("nodes") ?: JSONArray()
        val accessibilityClasses = ArrayList<String>()
        val resourceIds = ArrayList<String>()
        val protected = ArrayList<Rect>()
        var clickable = 0
        var scrollable = 0
        var editable = 0
        for (index in 0 until nodes.length()) {
            val node = nodes.optJSONObject(index) ?: continue
            node.optString("className").takeIf { it.isNotBlank() && it != "null" }
                ?.let(accessibilityClasses::add)
            node.optString("viewId").takeIf { it.isNotBlank() && it != "null" }
                ?.let(resourceIds::add)
            if (node.optBoolean("clickable")) clickable++
            if (node.optBoolean("scrollable")) scrollable++
            if (node.optBoolean("editable")) editable++
            if (node.optBoolean("editable") || node.optBoolean("password")) {
                val bounds = node.optJSONObject("bounds") ?: continue
                val rect = Rect(
                    bounds.optInt("left"), bounds.optInt("top"),
                    bounds.optInt("right"), bounds.optInt("bottom")
                )
                if (!rect.isEmpty) protected += rect
            }
        }

        // OCR remains ephemeral. Lines that overlap editable/password controls are discarded.
        val safeLines = rawLines.filter { line ->
            if (line.left == null || line.top == null || line.right == null || line.bottom == null) {
                protected.isEmpty()
            } else {
                val lineRect = Rect(line.left, line.top, line.right, line.bottom)
                protected.none { guarded ->
                    val overlap = Rect()
                    if (!overlap.setIntersect(lineRect, guarded)) false
                    else {
                        val area = overlap.width().toLong() * overlap.height().toLong()
                        val lineArea = max(1L, lineRect.width().toLong() * lineRect.height().toLong())
                        area.toFloat() / lineArea.toFloat() > 0.18f
                    }
                }
            }
        }

        val from = screenshotTime - CORRELATION_WINDOW_MS
        val to = screenshotTime + 2_000L
        val notifications = uiRecords.filter {
            it.optString("eventType") == "notification_posted" &&
                it.optLong("timestampEpochMs") in from..to
        }
        val notificationCategories = notifications.mapNotNull {
            it.optString("category").takeIf { category -> category.isNotBlank() && category != "null" }
        }
        val recentUi = uiRecords.lastOrNull {
            it.optLong("timestampEpochMs") in from..to &&
                it.optString("eventType") in setOf(
                    "view_clicked", "view_long_clicked", "view_selected", "view_scrolled",
                    "view_focused", "window_state_changed", "window_content_changed"
                )
        }
        val network = readRecords(context, sessionId, "network-events.jsonl", 500).filter {
            it.optLong("timestampEpochMs") in from..to
        }
        val packets = network.size
        val networkBytes = network.sumOf { it.optLong("frameBytes").coerceAtLeast(0L) }
        val protocols = network.map { it.optString("protocol") }.filter { it.isNotBlank() }
        val ports = network.flatMap {
            listOf(it.optInt("sourcePort", -1), it.optInt("destinationPort", -1))
        }.filter { it in 1..65535 }

        // Notification bodies are available to learning only when a pre-existing rule
        // explicitly authorised them. They are consumed in memory and never copied to this record.
        val authorisedText = readRecords(context, sessionId, "semantic-payloads.jsonl", 40)
            .filter { it.optLong("timestampEpochMs") in from..to }
            .flatMap { record ->
                listOf(record.optString("payloadTitle"), record.optString("payloadText"))
                    .filter { it.isNotBlank() && it != "null" }
            }

        return LearningEvidence(
            packageName = screenshot.packageName,
            screenshotId = screenshot.id,
            timestampEpochMs = screenshotTime,
            width = screenshot.width,
            height = screenshot.height,
            lines = safeLines,
            visualLabels = visualLabels,
            accessibilityClasses = accessibilityClasses,
            resourceIds = resourceIds,
            clickableCount = clickable,
            scrollableCount = scrollable,
            editableCount = editable,
            interactionType = recentUi?.optString("eventType").orEmpty(),
            notificationCategories = notificationCategories,
            notificationCount = notifications.size,
            networkPacketCount = packets,
            networkBytes = networkBytes,
            networkProtocols = protocols,
            networkPorts = ports,
            authorisedText = authorisedText
        )
    }

    private fun readRecords(
        context: Context,
        sessionId: String,
        fileName: String,
        limit: Int
    ): List<JSONObject> {
        val file = ObservationStore.evidenceFile(context, sessionId, fileName) ?: return emptyList()
        return runCatching {
            file.useLines { lines ->
                lines.filter { it.isNotBlank() }.toList().takeLast(limit).mapNotNull {
                    runCatching { JSONObject(it) }.getOrNull()
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistFailure(
        context: Context,
        sessionId: String,
        screenshot: StoredScreenshot,
        reason: String
    ) {
        ObservationStore.appendUnderstanding(context, sessionId, JSONObject()
            .put("schema", "app-observer-screen-understanding/v1")
            .put("timestampEpochMs", screenshot.timestampEpochMs)
            .put("packageName", screenshot.packageName)
            .put("screenshotId", screenshot.id)
            .put("analysisStatus", "failed")
            .put("failureReason", reason))
    }

    fun preview(context: Context): String {
        val directory = ObservationStore.latestDirectory(context)
        val file = directory?.let { java.io.File(it, "screen-understanding.jsonl") }
        val latest = if (file?.isFile == true) runCatching {
            file.useLines { lines ->
                lines.filter { it.isNotBlank() }.toList().takeLast(6).mapNotNull {
                    runCatching { JSONObject(it) }.getOrNull()
                }
            }
        }.getOrDefault(emptyList()) else emptyList()

        val summary = LocalLearningMemory.summary(context)
        if (latest.isEmpty()) return "No machine analysis yet. Start a session and the on-device models will inspect each captured screen.\n$summary"
        return buildString {
            append("$summary\n\nLATEST MACHINE IDENTIFICATIONS\n")
            latest.asReversed().take(4).forEach { record ->
                append(record.optString("analysisStatus", "pending").uppercase())
                    .append(" · ").append(record.optString("screenshotId").take(18)).append('\n')
                val state = record.optJSONObject("state")
                if (state != null) {
                    append("State: ").append(state.optString("label", "unclassified"))
                        .append(" · seen ").append(state.optInt("occurrences")).append(" times\n")
                }
                val concepts = record.optJSONArray("concepts") ?: JSONArray()
                if (concepts.length() == 0) append("No confident concept yet.\n")
                for (index in 0 until minOf(4, concepts.length())) {
                    val concept = concepts.optJSONObject(index) ?: continue
                    append("  • ").append(concept.optString("concept"))
                        .append(" · ").append((concept.optDouble("confidence") * 100).toInt()).append("%")
                        .append(" · ").append(concept.optJSONArray("evidenceSources")?.length() ?: 0)
                        .append(" sources\n")
                }
                val entities = record.optJSONArray("entities") ?: JSONArray()
                if (entities.length() > 0) {
                    append("Detected amounts: ")
                    append((0 until minOf(3, entities.length())).mapNotNull {
                        entities.optJSONObject(it)?.optString("amount")
                    }.joinToString(", ")).append('\n')
                }
                append('\n')
            }
        }.trimEnd()
    }
}
