package com.ysfclc.busybee.drivercommandcentre

/**
 * BusyBee's local intelligence capability registry.
 *
 * This is metadata and orchestration policy only. It does not download models,
 * execute inference, or assume that a runtime is installed. Runtime adapters
 * provide verified availability and execution in later integration slices.
 */
enum class IntelligenceCapability {
    OFFER_EXTRACTION,
    NOTIFICATION_CLASSIFICATION,
    DOCUMENT_EXTRACTION,
    TEXT_SUMMARIZATION,
    IMAGE_TEXT_RECOGNITION,
    SPEECH_TO_TEXT,
    SPEECH_SYNTHESIS,
    ANOMALY_DETECTION,
    GENERAL_REASONING
}

enum class ModelFormat {
    LITERT_LM,
    GGUF,
    ONNX,
    TFLITE,
    OTHER
}

enum class InferenceLocation { LOCAL, CLOUD }
enum class IntelligencePrivacyPolicy { LOCAL_ONLY, LOCAL_FIRST, CLOUD_ALLOWED, ASK_BEFORE_CLOUD }
enum class RuntimeAvailability { AVAILABLE, NOT_INSTALLED, INCOMPATIBLE, DISABLED, UNKNOWN }

data class ModelRequirements(
    val minimumRamMb: Int? = null,
    val minimumFreeStorageMb: Int? = null,
    val supportedAbis: Set<String> = emptySet(),
    val requiresNpu: Boolean = false
)

data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val version: String,
    val format: ModelFormat,
    val sizeBytes: Long?,
    val licence: String?,
    val capabilities: Set<IntelligenceCapability>,
    val requirements: ModelRequirements = ModelRequirements(),
    val installedPath: String? = null,
    val sourceUri: String? = null
) {
    init {
        require(id.isNotBlank()) { "Model id is required" }
        require(displayName.isNotBlank()) { "Model display name is required" }
        require(version.isNotBlank()) { "Model version is required" }
        require(sizeBytes == null || sizeBytes >= 0) { "Model size cannot be negative" }
        require(requirements.minimumRamMb == null || requirements.minimumRamMb >= 0)
        require(requirements.minimumFreeStorageMb == null || requirements.minimumFreeStorageMb >= 0)
    }

    val isInstalled: Boolean get() = !installedPath.isNullOrBlank()
}

data class RuntimeDescriptor(
    val id: String,
    val displayName: String,
    val supportedFormats: Set<ModelFormat>,
    val availability: RuntimeAvailability,
    val version: String? = null
) {
    init { require(id.isNotBlank()) { "Runtime id is required" } }
}

data class DeviceCapabilityProfile(
    val totalRamMb: Int?,
    val availableStorageMb: Long?,
    val supportedAbis: Set<String>,
    val hasNpu: Boolean?,
    val thermalStatus: Int? = null,
    val batteryPercent: Int? = null
)

data class IntelligenceTask(
    val capability: IntelligenceCapability,
    val privacyPolicy: IntelligencePrivacyPolicy,
    val allowCloud: Boolean = false
)

data class IntelligenceTarget(
    val location: InferenceLocation,
    val modelId: String? = null,
    val runtimeId: String? = null,
    val reason: String
)

interface LocalInferenceRuntime {
    val descriptor: RuntimeDescriptor
    fun canRun(model: ModelDescriptor, device: DeviceCapabilityProfile): Boolean
}

class LocalIntelligenceCatalogue {
    private val models = linkedMapOf<String, ModelDescriptor>()
    private val runtimes = linkedMapOf<String, LocalInferenceRuntime>()

    @Synchronized
    fun registerModel(model: ModelDescriptor) {
        models[model.id] = model
    }

    @Synchronized
    fun registerRuntime(runtime: LocalInferenceRuntime) {
        runtimes[runtime.descriptor.id] = runtime
    }

    @Synchronized
    fun models(): List<ModelDescriptor> = models.values.toList()

    @Synchronized
    fun runtimes(): List<RuntimeDescriptor> = runtimes.values.map { it.descriptor }

    @Synchronized
    fun modelsFor(capability: IntelligenceCapability): List<ModelDescriptor> =
        models.values.filter { capability in it.capabilities }

    /**
     * Select only installed models with an available compatible runtime.
     * Cloud is never selected unless policy explicitly permits it.
     */
    @Synchronized
    fun select(task: IntelligenceTask, device: DeviceCapabilityProfile): IntelligenceTarget {
        val candidates = models.values
            .filter { it.isInstalled && task.capability in it.capabilities }
            .mapNotNull { model ->
                val runtime = runtimes.values.firstOrNull {
                    it.descriptor.availability == RuntimeAvailability.AVAILABLE &&
                        model.format in it.descriptor.supportedFormats &&
                        it.canRun(model, device)
                }
                if (runtime == null) null else model to runtime
            }

        val selected = candidates.firstOrNull()
        if (selected != null) {
            return IntelligenceTarget(
                InferenceLocation.LOCAL,
                selected.first.id,
                selected.second.descriptor.id,
                "Installed model and compatible local runtime are available"
            )
        }

        val cloudPermitted = task.allowCloud &&
            task.privacyPolicy == IntelligencePrivacyPolicy.CLOUD_ALLOWED
        if (cloudPermitted) {
            return IntelligenceTarget(
                InferenceLocation.CLOUD,
                reason = "No compatible local model; task explicitly permits cloud execution"
            )
        }

        return IntelligenceTarget(
            InferenceLocation.LOCAL,
            reason = when (task.privacyPolicy) {
                IntelligencePrivacyPolicy.LOCAL_ONLY -> "No compatible local model; cloud is forbidden"
                IntelligencePrivacyPolicy.LOCAL_FIRST -> "No compatible local model; local-only fallback"
                IntelligencePrivacyPolicy.ASK_BEFORE_CLOUD -> "Cloud approval is required before fallback"
                IntelligencePrivacyPolicy.CLOUD_ALLOWED -> "Cloud fallback was not explicitly enabled for this task"
            }
        )
    }
}

/**
 * Calculations and state transitions that have deterministic answers should
 * remain native code, not be routed through a language model.
 */
object DeterministicDriverOperations {
    val aiIndependentCapabilities = setOf(
        IntelligenceCapability.ANOMALY_DETECTION
    )
}
