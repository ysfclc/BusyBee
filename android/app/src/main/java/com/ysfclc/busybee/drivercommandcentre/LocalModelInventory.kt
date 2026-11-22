package com.ysfclc.busybee.drivercommandcentre

import java.io.File
import java.security.MessageDigest

enum class DiscoveredModelFormat { GGUF, TFLITE, ONNX, LITERT_LM, UNKNOWN }

data class DiscoveredLocalModel(
    val id: String,
    val fileName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val sha256: String,
    val format: DiscoveredModelFormat,
    val capabilities: Set<IntelligenceCapability> = emptySet(),
    val metadataKnown: Boolean = false
)

/**
 * Scans only the explicit BusyBee-managed model directory. It never searches
 * arbitrary user storage. Files are inventory entries, not executable models.
 */
class LocalModelInventory(private val managedDirectory: File) {
    fun scan(): List<DiscoveredLocalModel> {
        if (!managedDirectory.exists() || !managedDirectory.isDirectory) return emptyList()
        return managedDirectory.walkTopDown()
            .filter { it.isFile && recognizedFormat(it.extension) != DiscoveredModelFormat.UNKNOWN }
            .map { file ->
                DiscoveredLocalModel(
                    id = sha256(file.absolutePath.toByteArray()).take(24),
                    fileName = file.name,
                    absolutePath = file.absolutePath,
                    sizeBytes = file.length(),
                    sha256 = sha256(file),
                    format = recognizedFormat(file.extension)
                )
            }
            .sortedBy { it.fileName.lowercase() }
            .toList()
    }

    companion object {
        fun recognizedFormat(extension: String): DiscoveredModelFormat = when (extension.lowercase()) {
            "gguf" -> DiscoveredModelFormat.GGUF
            "tflite" -> DiscoveredModelFormat.TFLITE
            "onnx" -> DiscoveredModelFormat.ONNX
            "litertlm", "litert" -> DiscoveredModelFormat.LITERT_LM
            else -> DiscoveredModelFormat.UNKNOWN
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
