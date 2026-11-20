package com.ysfclc.appobserver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultimodalInferenceTest {
    @Test
    fun identifiesPriceFromOcrAndCurrencyAndReturnsRegion() {
        val evidence = evidence(
            lines = listOf(OcrLineEvidence("Fare 18.50 PLN", 100, 300, 300, 350))
        )
        val result = MultimodalInference.classify(evidence)
            .firstOrNull { it.concept == "price_or_fare" }
        assertNotNull(result)
        assertTrue(result!!.confidence >= 0.22f)
        assertTrue(result.sources.contains("on_device_ocr"))
        assertTrue(result.sources.contains("ocr_currency_pattern"))
        assertNotNull(result.region)
        assertTrue(MultimodalInference.extractEntities(evidence).any { it.first.contains("18.50") })
    }

    @Test
    fun combinesImageLabelsAndAccessibilityForMapIdentification() {
        val evidence = evidence(
            visualLabels = listOf(VisualLabelEvidence("map", 0.94f)),
            accessibilityClasses = listOf("com.google.android.gms.maps.MapView")
        )
        val result = MultimodalInference.classify(evidence)
            .firstOrNull { it.concept == "map_view" }
        assertNotNull(result)
        assertTrue(result!!.sources.contains("on_device_image_labeling"))
        assertTrue(result.sources.contains("accessibility_structure"))
    }

    @Test
    fun identifiesMapRegionFromAccessibilityElementGeometry() {
        val evidence = evidence(
            accessibilityClasses = listOf("com.google.android.gms.maps.MapView"),
            accessibilityElements = listOf(
                AccessibilityElementEvidence(
                    className = "com.google.android.gms.maps.MapView",
                    resourceId = "com.example:id/map",
                    region = NormalisedRegion(0.05f, 0.18f, 0.95f, 0.86f),
                    clickable = false,
                    scrollable = true,
                    editable = false
                )
            )
        )
        val result = MultimodalInference.classify(evidence)
            .firstOrNull { it.concept == "map_view" }
        assertNotNull(result)
        assertNotNull(result!!.region)
        assertTrue(result.region!!.left < 0.1f)
        assertTrue(result.region!!.bottom > 0.8f)
    }

    @Test
    fun combinesNotificationAndUiStructureToIdentifyChat() {
        val evidence = evidence(
            accessibilityClasses = listOf("androidx.recyclerview.widget.RecyclerView"),
            editableCount = 1,
            notificationCategories = listOf("msg"),
            notificationCount = 1
        )
        val result = MultimodalInference.classify(evidence)
            .firstOrNull { it.concept == "chat_conversation" }
        assertNotNull(result)
        assertTrue(result!!.sources.contains("notification_listener_metadata"))
        assertTrue(result.sources.contains("accessibility_structure"))
    }

    @Test
    fun learningFeaturesNeverContainArbitraryRecognisedPersonalText() {
        val evidence = evidence(
            lines = listOf(OcrLineEvidence("Alice private@example.com message from Yusuf", 1, 1, 200, 30))
        )
        val features = MultimodalInference.featureVector(evidence)
        val joined = features.joinToString(" ")
        assertFalse(joined.contains("alice"))
        assertFalse(joined.contains("example"))
        assertFalse(joined.contains("yusuf"))
        assertTrue(features.contains("ocr:chat_conversation"))
    }

    private fun evidence(
        lines: List<OcrLineEvidence> = emptyList(),
        visualLabels: List<VisualLabelEvidence> = emptyList(),
        accessibilityClasses: List<String> = emptyList(),
        editableCount: Int = 0,
        notificationCategories: List<String> = emptyList(),
        notificationCount: Int = 0,
        accessibilityElements: List<AccessibilityElementEvidence> = emptyList()
    ) = LearningEvidence(
        packageName = "com.example.target",
        screenshotId = "shot-test",
        timestampEpochMs = 1_700_000_000_000L,
        width = 1080,
        height = 2400,
        lines = lines,
        visualLabels = visualLabels,
        accessibilityClasses = accessibilityClasses,
        resourceIds = emptyList(),
        accessibilityElements = accessibilityElements,
        clickableCount = 4,
        scrollableCount = 1,
        editableCount = editableCount,
        interactionType = "view_clicked",
        notificationCategories = notificationCategories,
        notificationCount = notificationCount,
        networkPacketCount = 5,
        networkBytes = 24_000L,
        networkProtocols = listOf("tcp"),
        networkPorts = listOf(443),
        authorisedText = emptyList()
    )
}
