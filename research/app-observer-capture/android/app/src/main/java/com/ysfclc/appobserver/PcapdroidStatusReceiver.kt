package com.ysfclc.appobserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class PcapdroidStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CAPTURE_STATUS) return

        // This receiver must be exported for PCAPdroid to notify us. Do not trust
        // an arbitrary app's spoofed "capture stopped" broadcast.
        val senderUid = getSendingUid()
        val trustedSender = listOf(
            "com.emanuelef.remote_capture",
            "com.emanuelef.remote_capture.debug"
        ).any { packageName ->
            runCatching { context.packageManager.getPackageUid(packageName, 0) == senderUid }
                .getOrDefault(false)
        }
        if (!trustedSender) return

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
