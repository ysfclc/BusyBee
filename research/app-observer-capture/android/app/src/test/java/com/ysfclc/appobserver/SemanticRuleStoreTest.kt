package com.ysfclc.appobserver

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticRuleStoreTest {
    @Test
    fun screenshotRegionMatchesPointsWithinItsNormalisedBounds() {
        val rule = rule()
        assertTrue(rule.containsScreenPoint(500f, 500f, 1000, 1000))
        assertFalse(rule.containsScreenPoint(100f, 100f, 1000, 1000))
    }

    @Test
    fun visualOnlyRuleDoesNotAuthoriseContentReads() {
        val rule = rule().copy(payloadSource = SemanticRule.SOURCE_VISUAL_ONLY)
        assertFalse(rule.capturesAccessibilityText)
        assertFalse(rule.capturesNotificationText)
    }

    @Test
    fun textCaptureRequiresAnExplicitPayloadSource() {
        val uiText = rule().copy(payloadSource = SemanticRule.SOURCE_ACCESSIBILITY_TEXT)
        val notification = rule().copy(payloadSource = SemanticRule.SOURCE_NOTIFICATION_TEXT)
        assertTrue(uiText.capturesAccessibilityText)
        assertTrue(notification.capturesNotificationText)
    }

    @Test
    fun persistedRuleJsonRoundTripsItsMeaningAndRegion() {
        val original = rule()
        val restored = SemanticRule.fromJson(original.toJson())
        assertTrue(restored != null)
        assertTrue(restored!!.label == original.label)
        assertTrue(restored.packageName == original.packageName)
        assertTrue(restored.left == original.left && restored.right == original.right)
    }

    @Test
    fun screenshotRuleDoesNotMatchOppositeOrientation() {
        val portrait = rule().copy(screenAspectRatio = 9f / 19.5f)
        assertTrue(portrait.matchesScreenLayout(1080, 2340))
        assertFalse(portrait.matchesScreenLayout(2340, 1080))
    }

    @Test
    fun legacyRuleWithoutAspectRatioRemainsImportable() {
        val legacy = rule().toJson().apply { remove("screenAspectRatio") }
        val restored = SemanticRule.fromJson(legacy)
        assertTrue(restored != null)
        assertTrue(restored!!.matchesScreenLayout(1080, 2340))
        assertTrue(restored.matchesScreenLayout(2340, 1080))
    }

    @Test
    fun malformedOrEmptyRulesAreRejected() {
        assertTrue(SemanticRule.fromJson(JSONObject().put("label", "empty")) == null)
    }

    private fun rule() = SemanticRule(
        id = "rule-1",
        packageName = "com.example.chat",
        label = "chat",
        payloadSource = SemanticRule.SOURCE_ACCESSIBILITY_TEXT,
        left = 0.2f,
        top = 0.2f,
        right = 0.8f,
        bottom = 0.8f,
        screenAspectRatio = 1f,
        screenshotId = "shot-1",
        createdAtEpochMs = 1L
    )
}
