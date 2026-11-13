package com.ysfclc.busybee.drivercommandcentre

import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ScrollableColumnTest {
    @Test
    fun allAddViewOverloadsAppendToTheSingleInnerColumn() {
        val context = RuntimeEnvironment.getApplication()
        val scroll = ScrollableColumn(context)
        val first = TextView(context).apply { text = "one" }
        val second = TextView(context).apply { text = "two" }
        val third = TextView(context).apply { text = "three" }
        val fourth = TextView(context).apply { text = "four" }
        val fifth = TextView(context).apply { text = "five" }

        scroll.addView(first)
        scroll.addView(second, 0)
        scroll.addView(third, ViewGroup.LayoutParams(100, 40))
        scroll.addView(fourth, 100, 40)
        scroll.addView(fifth, 0, ViewGroup.LayoutParams(100, 40))

        assertEquals("ScrollView must keep exactly one direct child", 1, scroll.childCount)
        assertSame(scroll.contentLayout, scroll.getChildAt(0))
        assertEquals("All screen sections must reach the inner column", 5, scroll.contentLayout.childCount)
        assertSame(fifth, scroll.contentLayout.getChildAt(0))
        assertSame(second, scroll.contentLayout.getChildAt(1))
    }
}
