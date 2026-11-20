package com.ysfclc.appobserver

import android.view.accessibility.AccessibilityEvent

data class ObservedApp(val label: String, val packageName: String, val family: String)

object AppCatalog {
    /** Apps that can be explicitly selected as the subject of a research session. */
    val apps = listOf(
        ObservedApp("Uber Driver", "com.ubercab.driver", "mobility"),
        ObservedApp("Bolt Driver", "ee.mtakso.driver", "mobility"),
        ObservedApp("Waze", "com.waze", "navigation"),
        ObservedApp("Google Maps", "com.google.android.apps.maps", "navigation")
    )

    fun isObservedPackage(packageName: String?): Boolean =
        packageName != null && apps.any { it.packageName == packageName }

    fun eventName(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "window_state_changed"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "window_content_changed"
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "view_scrolled"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "view_clicked"
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> "view_focused"
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "view_text_changed"
        else -> "other"
    }

    const val CONTENT_POLICY =
        "Only the selected driver or navigation app is observed during a manually started session. " +
        "Editable and password fields are excluded. Visible labels and selected-app notification text " +
        "require an explicit session toggle and are locally stored for review."
}
