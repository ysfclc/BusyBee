package com.ysfclc.busybee.drivercommandcentre

import java.util.Locale

enum class ProviderCoverageState {
    LOCAL_INPUTS_PRESENT,
    PERMISSION_REQUIRED,
    NO_DATA_YET
}

/**
 * A local, evidence-backed view of what BusyBee has actually received for a provider.
 * This is deliberately not a provider connection state: no direct provider API is implied.
 */
data class ProviderCoverageSummary(
    val provider: String,
    val notificationAccessGranted: Boolean,
    val visibleNotificationCount: Int,
    val manualOfferCount: Int,
    val notificationReviewedOfferCount: Int,
    val otherOfferCount: Int,
    val importedRecordCount: Int,
    val lastLocalRecordAtMillis: Long?,
    val state: ProviderCoverageState,
    val dataGaps: List<String>
) {
    val statusLabel: String
        get() = when (state) {
            ProviderCoverageState.LOCAL_INPUTS_PRESENT -> "LOCAL INPUTS · PARTIAL COVERAGE"
            ProviderCoverageState.PERMISSION_REQUIRED -> "PERMISSION REQUIRED · NO LOCAL DATA"
            ProviderCoverageState.NO_DATA_YET -> "NO LOCAL DATA YET"
        }
}

/**
 * Summarises only locally stored, user-authorised evidence. It never treats app presence,
 * a notification permission, or an imported row as proof of a provider API connection.
 */
object ProviderCoverageCalculator {
    private val knownProviders = listOf("Uber", "Bolt", "FreeNow", "iTaxi", "Cabify", "Lyft")

    fun summarize(
        notifications: List<CapturedNotice>,
        offers: List<DriverOffer>,
        importedRecords: List<DriverRecord>,
        notificationAccessGranted: Boolean
    ): List<ProviderCoverageSummary> {
        val providerNames = linkedMapOf<String, String>()
        knownProviders.forEach { providerNames[it.lowercase(Locale.ROOT)] = it }

        (notifications.map { it.platform } + offers.map { it.platform } + importedRecords.map { it.platform })
            .map(::normalizeProvider)
            .forEach { providerNames.putIfAbsent(it.lowercase(Locale.ROOT), it) }

        return providerNames.values.map { provider ->
            val providerNotifications = notifications.filter { normalizeProvider(it.platform).equals(provider, true) }
            val providerOffers = offers.filter { normalizeProvider(it.platform).equals(provider, true) }
            val providerRecords = importedRecords.filter { normalizeProvider(it.platform).equals(provider, true) }

            val manualOffers = providerOffers.count { it.source.equals("manual", ignoreCase = true) }
            val reviewedOffers = providerOffers.count { it.source.equals("notification-review", ignoreCase = true) }
            val otherOffers = (providerOffers.size - manualOffers - reviewedOffers).coerceAtLeast(0)
            val lastLocalRecord = (
                providerNotifications.map { it.capturedAt } +
                    providerOffers.map { it.capturedAt }
                ).filter { it > 0L }.maxOrNull()

            val hasLocalInputs = providerNotifications.isNotEmpty() ||
                providerOffers.isNotEmpty() || providerRecords.isNotEmpty()
            val state = when {
                hasLocalInputs -> ProviderCoverageState.LOCAL_INPUTS_PRESENT
                !notificationAccessGranted -> ProviderCoverageState.PERMISSION_REQUIRED
                else -> ProviderCoverageState.NO_DATA_YET
            }

            val gaps = buildList {
                if (!notificationAccessGranted) {
                    add("Android notification access is not granted.")
                } else if (providerNotifications.isEmpty()) {
                    add("No visible notifications captured yet.")
                }
                if (providerOffers.isEmpty()) add("No structured offer saved or reviewed yet.")
                if (providerRecords.isEmpty()) add("No statement or history rows imported yet.")
                add("No verified direct provider API connection.")
            }

            ProviderCoverageSummary(
                provider = provider,
                notificationAccessGranted = notificationAccessGranted,
                visibleNotificationCount = providerNotifications.size,
                manualOfferCount = manualOffers,
                notificationReviewedOfferCount = reviewedOffers,
                otherOfferCount = otherOffers,
                importedRecordCount = providerRecords.size,
                lastLocalRecordAtMillis = lastLocalRecord,
                state = state,
                dataGaps = gaps
            )
        }
    }

    fun normalizeProvider(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return "Unknown"
        val compact = trimmed.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
        return when {
            compact.contains("taxify") || compact.contains("bolt") -> "Bolt"
            compact.contains("uber") -> "Uber"
            compact.contains("freenow") || compact.contains("mytaxi") -> "FreeNow"
            compact.contains("itaxi") -> "iTaxi"
            compact.contains("cabify") -> "Cabify"
            compact.contains("lyft") -> "Lyft"
            else -> trimmed.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }
}
