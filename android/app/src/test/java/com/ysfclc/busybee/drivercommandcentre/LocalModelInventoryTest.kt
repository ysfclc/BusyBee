package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalModelInventoryTest {
    @Test fun scanRecognizesKnownModelFilesAndIgnoresUnknownFiles() {
        val root = createTempDir(prefix = "busybee-inventory-")
        try {
            File(root, "a.gguf").writeBytes(byteArrayOf(1, 2, 3))
            File(root, "b.tflite").writeBytes(byteArrayOf(4, 5))
            File(root, "c.onnx").writeBytes(byteArrayOf(6))
            File(root, "notes.txt").writeText("not a model")
            val models = LocalModelInventory(root).scan()
            assertEquals(3, models.size)
            assertEquals(listOf("a.gguf", "b.tflite", "c.onnx"), models.map { it.fileName })
            assertEquals(DiscoveredModelFormat.GGUF, models[0].format)
            assertEquals(3L, models[0].sizeBytes)
            assertEquals(64, models[0].sha256.length)
            assertTrue(models.all { !it.metadataKnown && it.capabilities.isEmpty() })
        } finally { root.deleteRecursively() }
    }

    @Test fun missingDirectoryReturnsEmptyInventory() {
        val root = File(createTempDir(), "not-created")
        assertTrue(LocalModelInventory(root).scan().isEmpty())
    }

    @Test fun formatExtensionMappingIsCaseInsensitive() {
        assertEquals(DiscoveredModelFormat.GGUF, LocalModelInventory.recognizedFormat("GGUF"))
        assertEquals(DiscoveredModelFormat.LITERT_LM, LocalModelInventory.recognizedFormat("litertlm"))
        assertEquals(DiscoveredModelFormat.UNKNOWN, LocalModelInventory.recognizedFormat("zip"))
    }
}
