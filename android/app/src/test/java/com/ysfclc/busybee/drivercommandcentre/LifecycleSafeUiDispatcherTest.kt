package com.ysfclc.busybee.drivercommandcentre

import android.app.Activity
import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class LifecycleSafeUiDispatcherTest {
    @Test
    fun queuedWorkerResultIsDroppedIfActivityIsDestroyedBeforeMainThreadRunsIt() {
        val controller = Robolectric.buildActivity(Activity::class.java).create()
        val activity = controller.get()
        val invoked = AtomicBoolean(false)
        val dispatcher = LifecycleSafeUiDispatcher(activity)

        val worker = Thread {
            dispatcher.post { invoked.set(true) }
        }
        worker.start()
        worker.join(5_000)

        assertFalse("Worker should finish scheduling the callback", worker.isAlive)
        controller.destroy()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("A queued result must not touch a destroyed Activity", invoked.get())
    }

    @Test
    fun callbackIsDroppedWhenActivityIsFinishingBeforePost() {
        val controller = Robolectric.buildActivity(Activity::class.java).create()
        val activity = controller.get()
        val invoked = AtomicBoolean(false)
        val dispatcher = LifecycleSafeUiDispatcher(activity)

        activity.finish()
        dispatcher.post { invoked.set(true) }
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("A finishing Activity must not receive a callback", invoked.get())
        controller.destroy()
    }

    @Test
    fun workerResultRunsWhenActivityIsStillAlive() {
        val controller = Robolectric.buildActivity(Activity::class.java).create()
        val activity = controller.get()
        val invoked = AtomicBoolean(false)
        val dispatcher = LifecycleSafeUiDispatcher(activity)

        val worker = Thread {
            dispatcher.post { invoked.set(true) }
        }
        worker.start()
        worker.join(5_000)
        assertFalse("Worker should finish scheduling the callback", worker.isAlive)

        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("A live Activity should receive its worker result", invoked.get())
        controller.destroy()
    }
}
