package com.ysfclc.busybee.drivercommandcentre

import android.content.Context
import java.io.File

/**
 * App-private storage boundary for local model files.
 * Models are not imported from arbitrary locations and are not downloaded here.
 */
class LocalModelStorage(context: Context) {
    private val appContext = context.applicationContext
    val modelsDirectory: File
        get() = File(appContext.filesDir, "local-intelligence/models")

    fun ensureDirectory(): File? {
        val dir = modelsDirectory
        return try {
            if (dir.exists() && !dir.isDirectory) return null
            if (!dir.exists() && !dir.mkdirs()) return null
            dir.canonicalFile
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun inventory(): List<DiscoveredLocalModel> {
        val dir = ensureDirectory() ?: return emptyList()
        return LocalModelInventory(dir).scan()
    }
}
