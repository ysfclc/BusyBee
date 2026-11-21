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
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
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
    private val analysisExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AppObserver-Understanding").apply { isDaemon = true }
    }
    private const val CORRELATION_WINDOW_MS = 8_000L
    private const val MAX_JSONL_TAIL_BYTES = 1_048_576L
    private const val MAX_UI_TREE_AGE_MS = 3_000L

    fun analyse(context: Context, sessionId: String, screenshot: StoredScreenshot) {
        if (!inFlight.add(screenshot.id)) return
        ObserverSignals.recordCaptureDiagnostic(context, "screen_analysis_attempts")
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
        Tasks.whenAllComplete(tasks).addOnCompleteListener(analysisExecutor) {
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
                ObserverSignals.recordCaptureDiagnostic(
                    context, if (textTask?.isSuccessful == true) "screen_ocr_successes" else "screen_ocr_failures"
                )
                ObserverSignals.recordCaptureDiagnostic(
                    context, if (labelTask?.isSuccessful == true) "screen_image_label_successes" else "screen_image_label_failures"
                )
                val evidence = fuseSources(
                    context, sessionId, screenshot, lines, visualLabels
                )
                val rawPredictions = MultimodalInference.classify(evidence)
                val featureVector = MultimodalInference.featureVector(evidence)
                val learned = LocalLearningMemory.learnedProbabilities(
                    context, evidence.packageName, featureVector
                )
                val heuristicByConcept = rawPredictions.associateBy { it.concept }
                val predictions = (rawPredictions.map { it.concept } + learned.keys).distinct()
                    .mapNotNull { concept ->
                        val heuristic = heuristicByConcept[concept]
                        val learnedProbability = learned[concept]
                        when {
                            heuristic != null && learnedProbability != null -> heuristic.copy(
                                confidence = (heuristic.confidence * 0.78f + learnedProbability * 0.22f)
                                    .coerceIn(0.03f, 0.98f),
                                sources = (heuristic.sources + "local_feedback_model").distinct().sorted()
                            )
                            heuristic != null -> heuristic
                            learnedProbability != null && learnedProbability >= 0.20f ->
                                ConceptHypothesis(
                                    concept = concept,
                                    confidence = (0.30f + learnedProbability * 0.55f).coerceIn(0.30f, 0.88f),
                                    score = learnedProbability * 4f,
                                    sources = listOf("local_feedback_model"),
                                    region = null
                                )
                            else -> null
                        }
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
                    .put("confidenceSemantics", "uncalibrated_multi_source_evidence_score")
                    .put("modelFeedbackApplied", learned.isNotEmpty())
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
                    .put("evidenceCorrelationWindowMs", CORRELATION_WINDOW_MS)
                    .put("sourceAvailability", JSONObject()
                        .put("screenshot", true)
                        .put("onDeviceOcr", textTask?.isSuccessful == true)
                        .put("imageLabelling", labelTask?.isSuccessful == true)
                        .put("accessibilityTreeMatched", evidence.accessibilityTreeMatched)
                        .put("notificationListenerConnected", evidence.notificationListenerConnected)
                        .put("authorisedNotificationTextAvailable", evidence.authorisedText.isNotEmpty())
                        .put("networkMetadataStreamAvailable", evidence.networkStreamAvailable))
                    .put("missingEvidenceSources", JSONArray().apply {
                        if (textTask?.isSuccessful != true) put("on_device_ocr")
                        if (labelTask?.isSuccessful != true) put("image_labelling")
                        if (!evidence.accessibilityTreeMatched) put("accessibility_hierarchy")
                        if (!evidence.notificationListenerConnected) put("notification_listener")
                        if (!evidence.networkStreamAvailable) put("network_metadata")
                    })
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
                    .put("sceneGraph", ScreenSceneGraphBuilder.build(evidence).toJson())
                if (ObservationStore.appendUnderstanding(context, sessionId, record)) {
                    ObserverSignals.recordCaptureDiagnostic(context, "screen_insights_written")
                } else {
                    ObserverSignals.recordCaptureDiagnostic(context, "screen_analysis_failures")
                }
                ObserverSignals.recordCaptureDiagnostic(
                    context, if (state.occurrences > 1) "screen_states_reused" else "screen_states_created"
                )
            } catch (failure: Throwable) {
                ObserverSignals.recordCaptureDiagnostic(context, "screen_analysis_failures")
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
        val latestTree = uiRecords.asSequence()
            .filter { it.optJSONArray("nodes") != null }
            .minByOrNull { kotlin.math.abs(it.optLong("timestampEpochMs") - screenshotTime) }
            ?.takeIf {
                kotlin.math.abs(it.optLong("timestampEpochMs") - screenshotTime) <= MAX_UI_TREE_AGE_MS
            }
        val nodes = latestTree?.optJSONArray("nodes") ?: JSONArray()
        val accessibilityClasses = ArrayList<String>()
        val resourceIds = ArrayList<String>()
        val accessibilityElements = ArrayList<AccessibilityElementEvidence>()
        val protected = ArrayList<Rect>()
        var clickable = 0
        var scrollable = 0
        var editable = 0
        for (index in 0 until nodes.length()) {
            val node = nodes.optJSONObject(index) ?: continue
            val className = node.optString("className").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
            val resourceId = node.optString("viewId").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
            className.takeIf { it.isNotBlank() }?.let(accessibilityClasses::add)
            resourceId.takeIf { it.isNotBlank() }?.let(resourceIds::add)
            val clickableNode = node.optBoolean("clickable")
            val scrollableNode = node.optBoolean("scrollable")
            val editableNode = node.optBoolean("editable")
            val passwordNode = node.optBoolean("password")
            if (clickableNode) clickable++
            if (scrollableNode) scrollable++
            if (editableNode) editable++
            val bounds = node.optJSONObject("bounds")
            if (bounds != null) {
                val rect = Rect(
                    bounds.optInt("left"), bounds.optInt("top"),
                    bounds.optInt("right"), bounds.optInt("bottom")
                )
                if (!rect.isEmpty) {
                    if (editableNode || passwordNode) protected += rect
                    val region = if (screenshot.width > 0 && screenshot.height > 0) {
                        NormalisedRegion(
                            (rect.left / screenshot.width.toFloat()).coerceIn(0f, 1f),
                            (rect.top / screenshot.height.toFloat()).coerceIn(0f, 1f),
                            (rect.right / screenshot.width.toFloat()).coerceIn(0f, 1f),
                            (rect.bottom / screenshot.height.toFloat()).coerceIn(0f, 1f)
                        ).takeIf { it.left < it.right && it.top < it.bottom }
                    } else null
                    accessibilityElements += AccessibilityElementEvidence(
                        className = className,
                        resourceId = resourceId,
                        region = region,
                        clickable = clickableNode,
                        scrollable = scrollableNode,
                        editable = editableNode,
                        elementIndex = index,
                        parentIndex = node.optInt("parentIndex", -1),
                        depth = node.optInt("depth", 0),
                        childIndex = node.optInt("childIndex", 0),
                        enabled = node.optBoolean("enabled", true),
                        selected = node.optBoolean("selected", false),
                        checked = node.optBoolean("checked", false)
                    )
                }
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
        val recentUi = uiRecords.asSequence()
            .filter {
                it.optLong("timestampEpochMs") in from..to &&
                    it.optString("eventType") in setOf(
                        "view_clicked", "view_long_clicked", "view_selected", "view_scrolled",
                        "view_focused", "window_state_changed", "window_content_changed"
                    )
            }
            .minByOrNull { kotlin.math.abs(it.optLong("timestampEpochMs") - screenshotTime) }
        val interactionSource = recentUi?.optJSONObject("sourceNode")
        interactionSource?.optString("className")
            ?.takeIf { it.isNotBlank() && it != "null" }?.let(accessibilityClasses::add)
        interactionSource?.optString("viewId")
            ?.takeIf { it.isNotBlank() && it != "null" }?.let(resourceIds::add)
        val interactionSourceClass = interactionSource?.optString("className").orEmpty()
        val interactionSourceId = interactionSource?.optString("viewId").orEmpty()
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
            accessibilityElements = accessibilityElements,
            clickableCount = clickable,
            scrollableCount = scrollable,
            editableCount = editable,
            interactionType = listOf(
                recentUi?.optString("eventType").orEmpty(),
                interactionSourceClass,
                interactionSourceId
            ).filter { it.isNotBlank() && it != "null" }.joinToString(" "),
            notificationCategories = notificationCategories,
            notificationCount = notifications.size,
            networkPacketCount = packets,
            networkBytes = networkBytes,
            networkProtocols = protocols,
            networkPorts = ports,
            authorisedText = authorisedText,
            accessibilityTreeMatched = latestTree != null,
            notificationListenerConnected = ObserverSignals.serviceConnected(context, "notifications"),
            networkStreamAvailable = ObservationStore.evidenceFile(context, sessionId, "network-events.jsonl") != null
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
            val bytes = RandomAccessFile(file, "r").use { input ->
                val start = (input.length() - MAX_JSONL_TAIL_BYTES).coerceAtLeast(0L)
                input.seek(start)
                if (start > 0L) input.readLine()
                val remaining = (input.length() - input.filePointer).toInt().coerceAtLeast(0)
                ByteArray(remaining).also { input.readFully(it) }
            }
            val lines = String(bytes, Charsets.UTF_8).lineSequence()
                .filter { it.isNotBlank() }.toList()
            lines.takeLast(limit).mapNotNull { line ->
                runCatching { JSONObject(line) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun persistFailure(
        context: Context,
        sessionId: String,
        screenshot: StoredScreenshot,
        reason: String
    ) {
        ObserverSignals.recordCaptureDiagnostic(context, "screen_analysis_failures")
        ObservationStore.appendUnderstanding(context, sessionId, JSONObject()
            .put("schema", "app-observer-screen-understanding/v1")
            .put("timestampEpochMs", screenshot.timestampEpochMs)
            .put("packageName", screenshot.packageName)
            .put("screenshotId", screenshot.id)
            .put("analysisStatus", "failed")
            .put("failureReason", reason))
    }

    fun regionsForScreenshot(context: Context, screenshotId: String): List<MachineRegion> {
        if (screenshotId.isBlank()) return emptyList()
        val directory = ObservationStore.latestDirectory(context) ?: return emptyList()
        val file = java.io.File(directory, "screen-understanding.jsonl")
        if (!file.isFile) return emptyList()
        val matching = runCatching {
            val bytes = RandomAccessFile(file, "r").use { input ->
                val start = (input.length() - MAX_JSONL_TAIL_BYTES).coerceAtLeast(0L)
                input.seek(start)
                if (start > 0L) input.readLine()
                val remaining = (input.length() - input.filePointer).toInt().coerceAtLeast(0)
                ByteArray(remaining).also { input.readFully(it) }
            }
            String(bytes, Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.toList()
                .asReversed().firstNotNullOfOrNull { line ->
                    runCatching { JSONObject(line) }.getOrNull()
                        ?.takeIf { it.optString("screenshotId") == screenshotId }
                }
        }.getOrNull() ?: return emptyList()
        val concepts = matching.optJSONArray("concepts") ?: JSONArray()
        val regions = ArrayList<MachineRegion>()
        for (index in 0 until concepts.length()) {
            val concept = concepts.optJSONObject(index) ?: continue
            val raw = concept.optJSONObject("region") ?: continue
            val region = NormalisedRegion(
                raw.optDouble("left", -1.0).toFloat(),
                raw.optDouble("top", -1.0).toFloat(),
                raw.optDouble("right", -1.0).toFloat(),
                raw.optDouble("bottom", -1.0).toFloat()
            )
            if (region.left !in 0f..1f || region.top !in 0f..1f ||
                region.right !in 0f..1f || region.bottom !in 0f..1f ||
                region.left >= region.right || region.top >= region.bottom) continue
            regions += MachineRegion(
                concept.optString("concept").replace('_', ' ').uppercase(Locale.ROOT),
                concept.optDouble("confidence").toFloat().coerceIn(0f, 1f),
                region,
                concept.optJSONArray("evidenceSources")?.let { sources ->
                    (0 until sources.length()).joinToString("+") { sources.optString(it) }
                }.orEmpty()
            )
        }
        val entities = matching.optJSONArray("entities") ?: JSONArray()
        val priceConfidence = (0 until concepts.length()).mapNotNull { index ->
            concepts.optJSONObject(index)
                ?.takeIf { it.optString("concept") == "price_or_fare" }
                ?.optDouble("confidence")?.toFloat()
        }.maxOrNull()?.coerceIn(0f, 1f) ?: 0f
        for (index in 0 until entities.length()) {
            val entity = entities.optJSONObject(index) ?: continue
            val raw = entity.optJSONObject("region") ?: continue
            val region = NormalisedRegion(
                raw.optDouble("left", -1.0).toFloat(),
                raw.optDouble("top", -1.0).toFloat(),
                raw.optDouble("right", -1.0).toFloat(),
                raw.optDouble("bottom", -1.0).toFloat()
            )
            if (region.left !in 0f..1f || region.top !in 0f..1f ||
                region.right !in 0f..1f || region.bottom !in 0f..1f ||
                region.left >= region.right || region.top >= region.bottom) continue
            regions += MachineRegion(
                entity.optString("amount").take(32),
                priceConfidence,
                region,
                "ocr_currency_pattern"
            )
        }
        return regions.take(16)
    }

    fun preview(context: Context): String {
        val directory = ObservationStore.latestDirectory(context)
        val file = directory?.let { java.io.File(it, "screen-understanding.jsonl") }
        val latest = if (file?.isFile == true) runCatching {
            val bytes = RandomAccessFile(file, "r").use { input ->
                val start = (input.length() - MAX_JSONL_TAIL_BYTES).coerceAtLeast(0L)
                input.seek(start)
                if (start > 0L) input.readLine()
                val remaining = (input.length() - input.filePointer).toInt().coerceAtLeast(0)
                ByteArray(remaining).also { input.readFully(it) }
            }
            val lines = String(bytes, Charsets.UTF_8).lineSequence()
                .filter { it.isNotBlank() }.toList()
            lines.takeLast(6).mapNotNull { line ->
                runCatching { JSONObject(line) }.getOrNull()
            }
        }.getOrDefault(emptyList()) else emptyList()

        val summary = LocalLearningMemory.summary(context)
        if (latest.isEmpty()) return "No machine analysis yet. Start a session and the on-device models will inspect each captured screen.\n$summary"
        return buildString {
            append("$summary\n\nLATEST MACHINE IDENTIFICATIONS\n")
            append("Confidence is an uncalibrated evidence score; inspect the listed sources.\n\n")
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
