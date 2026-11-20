package com.ysfclc.appobserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class PcapdroidStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CAPTURE_STATUS) return\n        val senderUid = getSendingUid()\n        val trustedSender = listOf("com.emanuelef.remote_capture", "com.emanuelef.remote_capture.debug")\n            .any { packageName ->\n                runCatching { context.packageManager.getPackageUid(packageName, 0) == senderUid }\n                    .getOrDefault(false)\n            }\n        if (!trustedSender) return
        val running = intent.getBooleanExtra("running", true)
        ObservationStore.appendSessionEvent(context, JSONObject()
            .put("type", "pcapdroid_status_broadcast")
            .put("timestampEpochMs", System.currentTimeMillis())
            .put("running", running))
        if (!running && ObservationStore.hasActiveSession(context)) {
            runCatching {
                context.startService(Intent(context, PcapdroidCaptureService::class.java)
                    .setAction(PcapdroidCaptureService.ACTION_END_SESSION)
                    .putExtra(PcapdroidCaptureService.EXTRA_FINISH_REASON, "pcapdroid_stopped_externally"))
            }
        }
    }

    companion object {
        const val ACTION_CAPTURE_STATUS = "com.emanuelef.remote_capture.CaptureStatus"
    }
}
