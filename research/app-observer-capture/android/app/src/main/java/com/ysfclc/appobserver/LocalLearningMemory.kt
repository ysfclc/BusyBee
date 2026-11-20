package com.ysfclc.appobserver

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.exp

data class LearnedState(
    val stateId: String,
    val stateLabel: String,
    val occurrences: Int,
    val similarity: Float,
    val previousStateId: String?,
    val transitionOccurrences: Int
)

/**
 * Persistent, on-device learning memory.
 *
 * Screen clustering is unsupervised and learns recurring states and transitions.
 * The multinomial Naive Bayes model is updated only from explicit review feedback;
 * inferred OCR text is never retained in the model feature vector.
 */
object LocalLearningMemory {
    private const val FILE_NAME = "app-observer-learning-model.json"
    private const val SCHEMA = "app-observer-learning-model/v1"
    private const val MAX_STATES_PER_APP = 120
    private const val MAX_TRANSITIONS_PER_APP = 240
    private const val MAX_FEATURES_PER_STATE = 48
    private val lock = Any()

    fun observe(
        context: Context,
        packageName: String,
        features: List<String>,
        predictions: List<ConceptHypothesis>,
        timestampEpochMs: Long,
        trigger: String
    ): LearnedState = synchronized(lock) {
        val root = read(context)
        val apps = root.optJSONObject("apps") ?: JSONObject().also { root.put("apps", it) }
        val app = apps.optJSONObject(packageName) ?: JSONObject().also { apps.put(packageName, it) }
        val states = app.optJSONArray("states") ?: JSONArray().also { app.put("states", it) }
        val currentFeatures = features.toSet().take(MAX_FEATURES_PER_STATE).toSet()

        var bestIndex = -1
        var bestSimilarity = 0f
        for (index in 0 until states.length()) {
            val state = states.optJSONObject(index) ?: continue
            val prior = state.optJSONArray("features").toStringSet()
            val similarity = jaccard(currentFeatures, prior)
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestIndex = index
            }
        }

        val matched = bestIndex >= 0 && bestSimilarity >= 0.58f
        val state: JSONObject
        if (matched) {
            state = states.getJSONObject(bestIndex)
            state.put("occurrences", state.optInt("occurrences") + 1)
                .put("lastSeenAtEpochMs", timestampEpochMs)
                .put("lastSimilarity", bestSimilarity.toDouble())
            val merged = (state.optJSONArray("features").toStringSet() + currentFeatures)
                .toList().takeLast(MAX_FEATURES_PER_STATE).toSet()
            state.put("features", JSONArray(merged))
        } else {
            state = JSONObject()
                .put("stateId", "state-" + UUID.randomUUID().toString().take(12))
                .put("createdAtEpochMs", timestampEpochMs)
                .put("lastSeenAtEpochMs", timestampEpochMs)
                .put("occurrences", 1)
                .put("features", JSONArray(currentFeatures.sorted()))
                .put("labelVotes", JSONObject())
            states.put(state)
            while (states.length() > MAX_STATES_PER_APP) states.remove(0)
        }

        val votes = state.optJSONObject("labelVotes") ?: JSONObject().also { state.put("labelVotes", it) }
        predictions.filter { it.confidence >= 0.52f }.take(4).forEach { prediction ->
            votes.put(prediction.concept, votes.optInt(prediction.concept) + 1)
        }

        val stateId = state.optString("stateId")
        val previousStateId = app.optString("lastStateId").takeIf { it.isNotBlank() && it != "null" }
        val transitions = app.optJSONArray("transitions") ?: JSONArray().also { app.put("transitions", it) }
        var transitionOccurrences = 0
        if (previousStateId != null && previousStateId != stateId) {
            val existing = (0 until transitions.length()).mapNotNull { transitions.optJSONObject(it) }
                .firstOrNull {
                    it.optString("fromStateId") == previousStateId &&
                        it.optString("toStateId") == stateId &&
                        it.optString("trigger") == trigger
                }
            if (existing != null) {
                transitionOccurrences = existing.optInt("occurrences") + 1
                existing.put("occurrences", transitionOccurrences)
                    .put("lastSeenAtEpochMs", timestampEpochMs)
            } else {
                transitions.put(JSONObject()
                    .put("fromStateId", previousStateId)
                    .put("toStateId", stateId)
                    .put("trigger", trigger.take(80))
                    .put("occurrences", 1)
                    .put("firstSeenAtEpochMs", timestampEpochMs)
                    .put("lastSeenAtEpochMs", timestampEpochMs))
                transitionOccurrences = 1
                while (transitions.length() > MAX_TRANSITIONS_PER_APP) transitions.remove(0)
            }
        }
        app.put("lastStateId", stateId)
            .put("lastObservedAtEpochMs", timestampEpochMs)
        root.put("schema", SCHEMA).put("updatedAtEpochMs", timestampEpochMs)
        write(context, root)

        val label = dominantLabel(votes) ?: "Unclassified screen state"
        LearnedState(
            stateId = stateId,
            stateLabel = label,
            occurrences = state.optInt("occurrences", 1),
            similarity = if (matched) bestSimilarity else 1f,
            previousStateId = previousStateId,
            transitionOccurrences = transitionOccurrences
        )
    }

    fun train(
        context: Context,
        packageName: String,
        concept: String,
        features: List<String>
    ) = synchronized(lock) {
        require(concept in MultimodalInference.concepts) { "Unsupported concept." }
        require(packageName.isNotBlank()) { "Target app is missing." }
        val root = read(context)
        val training = root.optJSONObject("training") ?: JSONObject().also { root.put("training", it) }
        val app = training.optJSONObject(packageName) ?: JSONObject().also { training.put(packageName, it) }
        val classes = app.optJSONObject("classes") ?: JSONObject().also { app.put("classes", it) }
        val label = classes.optJSONObject(concept) ?: JSONObject()
            .put("documents", 0)
            .put("featureCounts", JSONObject())
            .also { classes.put(concept, it) }
        val featureCounts = label.optJSONObject("featureCounts")
            ?: JSONObject().also { label.put("featureCounts", it) }
        label.put("documents", label.optInt("documents") + 1)
        features.distinct().take(MAX_FEATURES_PER_STATE).forEach { feature ->
            featureCounts.put(feature, featureCounts.optInt(feature) + 1)
        }
        app.put("totalDocuments", app.optInt("totalDocuments") + 1)
        root.put("schema", SCHEMA).put("updatedAtEpochMs", System.currentTimeMillis())
        write(context, root)
    }

    fun learnedProbabilities(context: Context, packageName: String, features: List<String>): Map<String, Float> =
        synchronized(lock) {
            val root = read(context)
            val app = root.optJSONObject("training")?.optJSONObject(packageName) ?: return@synchronized emptyMap()
            val classes = app.optJSONObject("classes") ?: return@synchronized emptyMap()
            val trained = MultimodalInference.concepts.mapNotNull { concept ->
                val record = classes.optJSONObject(concept) ?: return@mapNotNull null
                val documents = record.optInt("documents")
                if (documents < 2) return@mapNotNull null
                val counts = record.optJSONObject("featureCounts") ?: JSONObject()
                val total = counts.keys().asSequence().sumOf { counts.optInt(it) }
                concept to Triple(documents, counts, total)
            }
            if (trained.isEmpty()) return@synchronized emptyMap()
            if (trained.size < 2) return@synchronized emptyMap()
            val vocabularySize = trained.flatMap { (_, value) ->
                value.second.keys().asSequence().toList()
            }.toSet().size.coerceAtLeast(1)
            val totalDocuments = trained.sumOf { it.second.first }.coerceAtLeast(1)
            val scores = trained.associate { (concept, stats) ->
                val (documents, counts, totalFeatures) = stats
                var score = kotlin.math.ln((documents + 1.0) / (totalDocuments + trained.size).toDouble())
                features.distinct().forEach { feature ->
                    val probability = (counts.optInt(feature) + 1.0) /
                        (totalFeatures + vocabularySize + 1.0)
                    score += kotlin.math.ln(probability)
                }
                concept to score
            }
            val maxScore = scores.values.maxOrNull() ?: return@synchronized emptyMap()
            val expScores = scores.mapValues { exp(it.value - maxScore) }
            val total = expScores.values.sum().coerceAtLeast(1e-12)
            expScores.mapValues { (it.value / total).toFloat() }
        }

    fun summary(context: Context): String = synchronized(lock) {
        val root = read(context)
        val apps = root.optJSONObject("apps") ?: JSONObject()
        val training = root.optJSONObject("training") ?: JSONObject()
        var states = 0
        var transitions = 0
        val appKeys = apps.keys()
        while (appKeys.hasNext()) {
            val app = apps.optJSONObject(appKeys.next()) ?: continue
            states += app.optJSONArray("states")?.length() ?: 0
            transitions += app.optJSONArray("transitions")?.length() ?: 0
        }
        var trainedExamples = 0
        for (key in training.keys()) {
            val classes = training.optJSONObject(key)?.optJSONObject("classes") ?: continue
            for (label in classes.keys()) trainedExamples += classes.optJSONObject(label)?.optInt("documents") ?: 0
        }
        "$states recurring screen states · $transitions learned transitions · $trainedExamples reviewed training examples"
    }

    fun exportJson(context: Context): JSONObject = synchronized(lock) { read(context) }

    fun importJson(context: Context, incoming: JSONObject) = synchronized(lock) {
        if (incoming.optString("schema") != SCHEMA) return@synchronized
        val current = read(context)
        mergeTraining(current, incoming)
        mergeStates(current, incoming)
        current.put("schema", SCHEMA).put("importedAtEpochMs", System.currentTimeMillis())
        write(context, current)
    }

    private fun mergeTraining(target: JSONObject, incoming: JSONObject) {
        val targetTraining = target.optJSONObject("training") ?: JSONObject().also { target.put("training", it) }
        val sourceTraining = incoming.optJSONObject("training") ?: return
        for (pkg in sourceTraining.keys()) {
            val targetApp = targetTraining.optJSONObject(pkg) ?: JSONObject().also { targetTraining.put(pkg, it) }
            val sourceApp = sourceTraining.optJSONObject(pkg) ?: continue
            targetApp.put("totalDocuments", targetApp.optInt("totalDocuments") + sourceApp.optInt("totalDocuments"))
            val targetClasses = targetApp.optJSONObject("classes") ?: JSONObject().also { targetApp.put("classes", it) }
            val sourceClasses = sourceApp.optJSONObject("classes") ?: continue
            for (concept in sourceClasses.keys()) {
                val sourceLabel = sourceClasses.optJSONObject(concept) ?: continue
                val targetLabel = targetClasses.optJSONObject(concept) ?: JSONObject()
                    .put("documents", 0).put("featureCounts", JSONObject())
                    .also { targetClasses.put(concept, it) }
                targetLabel.put("documents", targetLabel.optInt("documents") + sourceLabel.optInt("documents"))
                val targetCounts = targetLabel.optJSONObject("featureCounts")
                    ?: JSONObject().also { targetLabel.put("featureCounts", it) }
                val sourceCounts = sourceLabel.optJSONObject("featureCounts") ?: continue
                for (feature in sourceCounts.keys()) {
                    targetCounts.put(feature, targetCounts.optInt(feature) + sourceCounts.optInt(feature))
                }
            }
        }
    }

    private fun mergeStates(target: JSONObject, incoming: JSONObject) {
        val targetApps = target.optJSONObject("apps") ?: JSONObject().also { target.put("apps", it) }
        val sourceApps = incoming.optJSONObject("apps") ?: return
        for (pkg in sourceApps.keys()) {
            val sourceApp = sourceApps.optJSONObject(pkg) ?: continue
            val targetApp = targetApps.optJSONObject(pkg) ?: JSONObject().also { targetApps.put(pkg, it) }
            val targetStates = targetApp.optJSONArray("states") ?: JSONArray().also { targetApp.put("states", it) }
            val knownIds = (0 until targetStates.length()).mapNotNull { targetStates.optJSONObject(it)?.optString("stateId") }.toSet()
            val sourceStates = sourceApp.optJSONArray("states") ?: JSONArray()
            for (index in 0 until sourceStates.length()) {
                val state = sourceStates.optJSONObject(index) ?: continue
                if (state.optString("stateId") !in knownIds) targetStates.put(state)
            }
            while (targetStates.length() > MAX_STATES_PER_APP) targetStates.remove(0)
            val targetTransitions = targetApp.optJSONArray("transitions") ?: JSONArray().also { targetApp.put("transitions", it) }
            val sourceTransitions = sourceApp.optJSONArray("transitions") ?: JSONArray()
            for (index in 0 until sourceTransitions.length()) targetTransitions.put(sourceTransitions.getJSONObject(index))
            while (targetTransitions.length() > MAX_TRANSITIONS_PER_APP) targetTransitions.remove(0)
        }
    }

    private fun dominantLabel(votes: JSONObject): String? =
        votes.keys().asSequence().mapNotNull { key -> key to votes.optInt(key) }
            .maxByOrNull { it.second }?.takeIf { it.second > 0 }?.first

    private fun jaccard(first: Set<String>, second: Set<String>): Float {
        if (first.isEmpty() || second.isEmpty()) return 0f
        val intersection = first.intersect(second).size.toFloat()
        val union = first.union(second).size.toFloat()
        return if (union == 0f) 0f else intersection / union
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet {
            for (index in 0 until length()) optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun read(context: Context): JSONObject {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return JSONObject().put("schema", SCHEMA)
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .getOrElse { JSONObject().put("schema", SCHEMA) }
    }

    private fun write(context: Context, value: JSONObject) {
        val file = File(context.filesDir, FILE_NAME)
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        FileOutputStream(temp, false).bufferedWriter(Charsets.UTF_8).use { it.write(value.toString()) }
        if (file.exists()) file.delete()
        check(temp.renameTo(file)) { "Could not commit the on-device learning model." }
    }
}
