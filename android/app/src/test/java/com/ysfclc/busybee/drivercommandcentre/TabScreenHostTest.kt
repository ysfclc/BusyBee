package com.ysfclc.busybee.drivercommandcentre

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TabScreenHostTest {
    @Test fun mapRemainsAttachedWhileOtherTabsAreDisplayed() {
        val context = RuntimeEnvironment.getApplication()
        val container = FrameLayout(context)
        val mapScreen = FrameLayout(context)
        val host = TabScreenHost(container, mapScreen)
        val offersScreen = TextView(context)

        host.show(offersScreen)

        assertSame(container, mapScreen.parent)
        assertEquals(View.VISIBLE, mapScreen.visibility)
        assertSame(container, offersScreen.parent)

        host.showMap()

        assertSame(container, mapScreen.parent)
        assertEquals(View.VISIBLE, mapScreen.visibility)
        assertNull(offersScreen.parent)
        assertEquals(1, container.childCount)
    }

    @Test fun repeatedTabSwitchesDoNotDuplicateOrDetachMapScreen() {
        val context = RuntimeEnvironment.getApplication()
        val container = FrameLayout(context)
        val mapScreen = FrameLayout(context)
        val host = TabScreenHost(container, mapScreen)

        repeat(12) { index ->
            host.show(TextView(context).apply { text = "Tab $index" })
            assertSame(container, mapScreen.parent)
            assertEquals(View.VISIBLE, mapScreen.visibility)
            assertEquals(2, container.childCount)
        }

        host.showMap()
        assertSame(container, mapScreen.parent)
        assertEquals(View.VISIBLE, mapScreen.visibility)
        assertEquals(1, container.childCount)
    }
}
