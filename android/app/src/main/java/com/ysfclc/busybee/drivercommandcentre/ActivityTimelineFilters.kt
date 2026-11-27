package com.ysfclc.busybee.drivercommandcentre

/** Deterministic in-memory filters for the local unified activity timeline. */
object ActivityTimelineFilters {
    private const val UNKNOWN_PROVIDER = "UNKNOWN PROVIDER"

    fun providers(items: List<UnifiedActivityItem>): List<String> =
        items.map { it.provider?.takeIf(String::isNotBlank) ?: UNKNOWN_PROVIDER }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun filter(
        items: List<UnifiedActivityItem>,
        sourceFilter: String = "ALL",
        providerFilter: String? = null
    ): List<UnifiedActivityItem> {
        return items.filter { item ->
            (sourceFilter == "ALL" ||
                (sourceFilter == "OBSERVATIONS" && item.sourceKind == "observation") ||
                (sourceFilter == "EVENTS" && item.sourceKind == "provider_event")) &&
                (providerFilter == null || (item.provider?.takeIf(String::isNotBlank) ?: UNKNOWN_PROVIDER)
                    .equals(providerFilter, ignoreCase = true))
        }
    }
}
