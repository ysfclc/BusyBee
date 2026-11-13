package com.ysfclc.busybee.drivercommandcentre

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** Keeps the native MapLibre screen attached while other app surfaces are shown. */
internal class TabScreenHost(
    private val container: FrameLayout,
    private val mapScreen: View
) {
    private var secondaryScreen: View? = null

    init {
        check(mapScreen.parent == null) { "Map screen must not already have a parent" }
        container.addView(
            mapScreen,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    fun showMap() {
        removeSecondaryScreen()
        mapScreen.visibility = View.VISIBLE
        mapScreen.bringToFront()
    }

    fun show(screen: View) {
        require(screen !== mapScreen) { "Use showMap() for the persistent map screen" }
        removeSecondaryScreen()
        mapScreen.visibility = View.GONE
        val parent = screen.parent
        if (parent is ViewGroup) parent.removeView(screen)
        check(screen.parent == null) { "Screen is already attached to a non-removable parent" }
        container.addView(
            screen,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        secondaryScreen = screen
        screen.bringToFront()
    }

    private fun removeSecondaryScreen() {
        secondaryScreen?.let(container::removeView)
        secondaryScreen = null
    }
}
