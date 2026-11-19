package com.ysfclc.appobserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class PcapdroidStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CAPTURE_STATUS) return
        val running = intent.getBooleanExtra("running", true)
        ObservationStore.appendSessionEvent(context, JSONObject()
            .put("type", "pcapdroid_status_broadcast")
            .put("timestampEpochMs", System.currentTimeMillis())
            .put("running", running))
        if (!running && ObservationStore.hasActiveSession(context)) {
            context.startService(Intent(context, PcapdroidCaptureService::class.java)
                .setAction(PcapdroidCaptureService.ACTION_STOP))
        }
    }

    companion object {
        const val ACTION_CAPTURE_STATUS = "com.emanuelef.remote_capture.CaptureStatus"
    }
}
