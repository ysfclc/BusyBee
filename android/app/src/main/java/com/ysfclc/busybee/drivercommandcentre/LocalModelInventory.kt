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
 * arbitrary user storage. Inventory entries are not executable models.
 *
 * Model identity is content-based, so moving/renaming an identical file does
 * not change its ID. Canonical-path checks prevent symlinks escaping the
 * managed directory from being inventoried.
 */
class LocalModelInventory(private val managedDirectory: File) {
    fun scan(): List<DiscoveredLocalModel> {
        if (!managedDirectory.exists() || !managedDirectory.isDirectory) return emptyList()
        val root = try { managedDirectory.canonicalFile } catch (_: Exception) { return emptyList() }
        return managedDirectory.walkTopDown()
            .filter { it.isFile && recognizedFormat(it.extension) != DiscoveredModelFormat.UNKNOWN }
            .mapNotNull { file ->
                val canonical = try { file.canonicalFile } catch (_: Exception) { return@mapNotNull null }
                if (!canonical.path.startsWith(root.path + File.separator) && canonical != root) return@mapNotNull null
                val digest = try { sha256(file) } catch (_: Exception) { return@mapNotNull null }
                DiscoveredLocalModel(
                    id = digest.take(24),
                    fileName = file.name,
                    absolutePath = file.absolutePath,
                    sizeBytes = file.length(),
                    sha256 = digest,
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
    }
}
