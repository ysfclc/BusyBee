package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityTimelineFiltersTest {
    private fun item(id: String, kind: String, provider: String?) = UnifiedActivityItem(
        id = id,
        sourceKind = kind,
        provider = provider,
        itemType = "offer",
        sourceMethod = "ANDROID_NOTIFICATION",
        sourceOccurredAtMillis = null,
        receivedAtMillis = 1L,
        classification = "unclassified",
        lifecycleId = null,
        evidenceId = null,
        evidenceReference = null,
        evidenceDigest = null,
        payloadJson = null,
        status = null,
        amount = null,
        currency = null,
        pickup = null,
        destination = null,
        distanceKm = null,
        durationMinutes = null
    )

    @Test
    fun providerListIsDistinctSortedAndIncludesUnknownSource() {
        val items = listOf(
            item("1", "observation", "Uber"),
            item("2", "observation", "Bolt"),
            item("3", "provider_event", "uber"),
            item("4", "observation", null),
            item("5", "observation", " ")
        )

        assertEquals(listOf("Bolt", "Uber", "UNKNOWN PROVIDER"), ActivityTimelineFilters.providers(items))
    }

    @Test
    fun sourceAndProviderFiltersComposeWithoutDroppingOtherSources() {
        val items = listOf(
            item("1", "observation", "Uber"),
            item("2", "provider_event", "Uber"),
            item("3", "observation", "Bolt"),
            item("4", "observation", null)
        )

        assertEquals(listOf("1"), ActivityTimelineFilters.filter(items, "OBSERVATIONS", "uber").map { it.id })
        assertEquals(listOf("2"), ActivityTimelineFilters.filter(items, "EVENTS", "UBER").map { it.id })
        assertEquals(listOf("4"), ActivityTimelineFilters.filter(items, "ALL", "unknown provider").map { it.id })
        assertEquals(4, ActivityTimelineFilters.filter(items).size)
    }
}
