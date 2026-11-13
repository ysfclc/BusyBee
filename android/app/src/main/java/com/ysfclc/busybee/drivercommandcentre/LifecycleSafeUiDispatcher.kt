package com.ysfclc.busybee.drivercommandcentre

import android.app.Activity

/**
 * Drops worker results that arrive after their owning Activity has started finishing
 * or has been destroyed. The second check runs on the main thread, closing the gap
 * between scheduling a callback and executing it.
 */
internal class LifecycleSafeUiDispatcher(private val activity: Activity) {
    fun post(action: () -> Unit) {
        if (!isActivityAlive()) return

        activity.runOnUiThread {
            if (isActivityAlive()) action()
        }
    }

    private fun isActivityAlive(): Boolean =
        !activity.isFinishing && !activity.isDestroyed
}
