package com.ysfclc.busybee.drivercommandcentre

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

/**
 * Reads coarse device facts needed for local model suitability decisions.
 * NPU availability is deliberately unknown: Android does not expose a
 * dependable generic NPU capability probe across vendors.
 */
class DeviceCapabilityProfiler(context: Context) {
    private val appContext = context.applicationContext

    fun profile(): DeviceCapabilityProfile {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val storage = StatFs(appContext.filesDir.absolutePath)
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { powerManager?.currentThermalStatus }.getOrNull()
        } else null

        val battery = runCatching {
            val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra("level", -1) ?: -1
            val scale = intent?.getIntExtra("scale", -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100 / scale) else null
        }.getOrNull()

        val totalRamMb = memoryInfo.totalMem.takeIf { it > 0L }?.div(1024L * 1024L)?.toInt()
        val freeStorageMb = storage.availableBytes.takeIf { it > 0L }?.div(1024L * 1024L)

        return DeviceCapabilityProfile(
            totalRamMb = totalRamMb,
            availableStorageMb = freeStorageMb,
            supportedAbis = Build.SUPPORTED_ABIS?.toSet().orEmpty(),
            hasNpu = null,
            thermalStatus = thermal,
            batteryPercent = battery
        )
    }
}
