package com.ysfclc.busybee.drivercommandcentre

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * A ScrollView with one permanent direct child. All public addView overloads are
 * redirected to the inner vertical column so screen builders can safely append
 * sections without violating ScrollView's single-child contract.
 */
internal class ScrollableColumn(context: Context) : ScrollView(context) {
    internal val contentLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    init {
        // Call the three-argument superclass implementation directly. The
        // two-argument ViewGroup overload delegates virtually to addView(child,
        // index, params), which would re-enter our redirect override and recurse.
        super.addView(
            contentLayout,
            -1,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
    }

    override fun addView(child: View) {
        contentLayout.addView(child)
    }

    override fun addView(child: View, index: Int) {
        contentLayout.addView(child, index)
    }

    override fun addView(child: View, params: ViewGroup.LayoutParams) {
        contentLayout.addView(child, params)
    }

    override fun addView(child: View, width: Int, height: Int) {
        contentLayout.addView(child, width, height)
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        contentLayout.addView(child, index, params)
    }
}
