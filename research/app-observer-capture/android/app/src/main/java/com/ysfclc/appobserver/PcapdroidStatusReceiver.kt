package com.ysfclc.appobserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject

class PcapdroidStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CAPTURE_STATUS) return

        // This receiver must be exported for PCAPdroid to notify us. Android 14+
        // exposes the original sender identity; on older versions we ignore the
        // optional broadcast rather than trust an unauthenticated stop signal.
        if (Build.VERSION.SDK_INT < 34) return
        val senderPackage = getSentFromPackage()
        val senderUid = getSentFromUid()
        if (senderPackage !in TRUSTED_PACKAGES) return
        val installedUid = runCatching {
            context.packageManager.getPackageUid(senderPackage!!, 0)
        }.getOrNull()
        if (installedUid == null || installedUid != senderUid) return

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
        private val TRUSTED_PACKAGES = setOf(
            "com.emanuelef.remote_capture",
            "com.emanuelef.remote_capture.debug"
        )
    }
}
