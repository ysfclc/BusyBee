package com.ysfclc.appobserver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSceneGraphTest {
    @Test
    fun linksOcrTextToContainingAccessibilityRegionWithoutPersistingText() {
        val evidence = sample(
            lines = listOf(OcrLineEvidence("Private fare 42.50 PLN", 120, 240, 360, 290)),
            elements = listOf(
                AccessibilityElementEvidence(
                    className = "android.view.ViewGroup",
                    resourceId = "com.example:id/offer_card",
                    region = NormalisedRegion(0.05f, 0.05f, 0.95f, 0.45f),
                    clickable = false,
                    scrollable = false,
                    editable = false
                )
            )
        )
        val graph = ScreenSceneGraphBuilder.build(evidence)
        assertTrue(graph.relations.any { it.kind == "contains_text_region" })
        val json = graph.toJson().toString()
        assertTrue(json.contains("currency"))
        assertFalse(json.contains("Private fare"))
        assertFalse(json.contains("42.50"))
        assertFalse(json.contains("PLN"))
    }

    @Test
    fun representsGenericUiAndInteractionNetworkSignals() {
        val evidence = sample(
            elements = listOf(
                AccessibilityElementEvidence(
                    "android.widget.Button", "com.example:id/action",
                    NormalisedRegion(0.2f, 0.4f, 0.8f, 0.5f), true, false, false
                ),
                AccessibilityElementEvidence(
                    "android.widget.TextView", "com.example:id/label",
                    NormalisedRegion(0.2f, 0.35f, 0.8f, 0.4f), false, false, false
                )
            ),
            interaction = "view_clicked"
        )
        val graph = ScreenSceneGraphBuilder.build(evidence)
        assertTrue(graph.nodes.any { it.kind == "interactive_control" })
        assertTrue(graph.nodes.any { it.kind == "interaction_observation" })
        assertTrue(graph.nodes.any { it.kind == "network_activity_signal" })
        assertTrue(graph.relations.any {
            it.kind == "aligned_horizontally" ||
                it.kind == "aligned_vertically" || it.kind == "adjacent"
        })
    }

    @Test
    fun keepsUnknownTextShapeWithoutInventingSemanticCategory() {
        val graph = ScreenSceneGraphBuilder.build(
            sample(lines = listOf(OcrLineEvidence("unrecognised phrase", 10, 10, 180, 35)))
        )
        assertTrue(graph.nodes.any {
            it.kind == "text_region" && it.attributes["shape"] == "short_text"
        })
    }

    @Test
    fun preservesAccessibilityParentChildRelations() {
        val graph = ScreenSceneGraphBuilder.build(
            sample(elements = listOf(
                AccessibilityElementEvidence(
                    className = "android.view.ViewGroup",
                    resourceId = "com.example:id/card",
                    region = NormalisedRegion(0.1f, 0.1f, 0.9f, 0.7f),
                    clickable = false, scrollable = false, editable = false,
                    elementIndex = 2, parentIndex = 1, depth = 2
                ),
                AccessibilityElementEvidence(
                    className = "android.widget.Button",
                    resourceId = "com.example:id/confirm",
                    region = NormalisedRegion(0.2f, 0.5f, 0.8f, 0.6f),
                    clickable = true, scrollable = false, editable = false,
                    elementIndex = 3, parentIndex = 2, depth = 3
                )
            ))
        )
        assertTrue(graph.relations.any {
            it.from == "ui-2" && it.to == "ui-3" && it.kind == "parent_of"
        })
    }

    private fun sample(
        lines: List<OcrLineEvidence> = emptyList(),
        elements: List<AccessibilityElementEvidence> = emptyList(),
        interaction: String = ""
    ) = LearningEvidence(
        packageName = "com.example.target",
        screenshotId = "scene-test",
        timestampEpochMs = 1_700_000_000_000L,
        width = 1080,
        height = 2400,
        lines = lines,
        visualLabels = listOf(VisualLabelEvidence("vehicle", 0.8f)),
        accessibilityClasses = elements.map { it.className },
        resourceIds = elements.map { it.resourceId },
        accessibilityElements = elements,
        clickableCount = elements.count { it.clickable },
        scrollableCount = elements.count { it.scrollable },
        editableCount = elements.count { it.editable },
        interactionType = interaction,
        notificationCategories = listOf("service"),
        notificationCount = 1,
        networkPacketCount = 4,
        networkBytes = 1024,
        networkProtocols = listOf("tcp"),
        networkPorts = listOf(443),
        authorisedText = emptyList()
    )
}
