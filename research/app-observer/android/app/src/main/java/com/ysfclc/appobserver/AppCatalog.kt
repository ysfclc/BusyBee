package com.ysfclc.appobserver

data class ObservedApp(val label: String, val packageName: String, val family: String)

object AppCatalog {
    val apps = listOf(
        ObservedApp("Uber Driver", "com.ubercab.driver", "mobility"),
        ObservedApp("Bolt Driver", "ee.mtakso.driver", "mobility"),
        ObservedApp("Waze", "com.waze", "navigation"),
        ObservedApp("Google Maps", "com.google.android.apps.maps", "navigation"),
        ObservedApp("WhatsApp", "com.whatsapp", "messaging"),
        ObservedApp("WhatsApp Business", "com.whatsapp.w4b", "messaging")
    )

    fun isObservedPackage(packageName: String?): Boolean =
        packageName != null && apps.any { it.packageName == packageName }

    fun eventName(type: Int): String = when (type) {
        android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "window_state_changed"
        android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "window_content_changed"
        android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED -> "view_scrolled"
        android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED -> "view_clicked"
        android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED -> "view_focused"
        android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "view_text_changed"
        else -> "other"
    }

    const val CONTENT_POLICY =
        "No notification text, screen text, content descriptions, view IDs, message bodies, sender names, credentials or raw network payloads are collected."
}
