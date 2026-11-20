package com.ysfclc.appobserver

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UiStructureMatcherTest {
    @Test
    fun ignoresPixelBoundsWhenMatchingTheSameStructure() {
        val first = tree(left = 10, top = 20)
        val second = tree(left = 44, top = 80)
        assertEquals(UiStructureMatcher.fingerprint(first), UiStructureMatcher.fingerprint(second))
    }

    @Test
    fun changesWhenControlStructureChanges() {
        val first = tree(left = 10, top = 20, viewId = "com.example:id/confirm")
        val second = tree(left = 10, top = 20, viewId = "com.example:id/cancel")
        assertNotEquals(UiStructureMatcher.fingerprint(first), UiStructureMatcher.fingerprint(second))
    }

    @Test
    fun changesWhenControlStateChanges() {
        val first = tree(left = 10, top = 20, checked = false)
        val second = tree(left = 10, top = 20, checked = true)
        assertNotEquals(UiStructureMatcher.fingerprint(first), UiStructureMatcher.fingerprint(second))
    }

    private fun tree(
        left: Int,
        top: Int,
        viewId: String = "com.example:id/confirm",
        checked: Boolean = false
    ) = JSONArray().put(
        JSONObject()
            .put("index", 0)
            .put("parentIndex", -1)
            .put("depth", 0)
            .put("childIndex", 0)
            .put("className", "android.widget.Button")
            .put("viewId", viewId)
            .put("visible", true)
            .put("clickable", true)
            .put("scrollable", false)
            .put("editable", false)
            .put("password", false)
            .put("enabled", true)
            .put("selected", false)
            .put("checked", checked)
            .put("childCount", 0)
            .put("bounds", JSONObject().put("left", left).put("top", top))
    )
}
