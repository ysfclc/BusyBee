package com.ysfclc.busybee.drivercommandcentre

/** Explicit launcher handoffs for known ride-provider apps. */
data class ProviderAppDefinition(
    val providerId: String,
    val displayName: String,
    val packageName: String
)

object ProviderAppCatalog {
    private val apps = listOf(
        ProviderAppDefinition(
            providerId = "uber",
            displayName = "Uber Driver",
            packageName = "com.ubercab.driver"
        ),
        ProviderAppDefinition(
            providerId = "bolt",
            displayName = "Bolt Driver",
            packageName = "ee.mtakso.driver"
        )
    )

    fun all(): List<ProviderAppDefinition> = apps.toList()

    fun forProvider(provider: String): ProviderAppDefinition? {
        val normalized = ProviderCoverageCalculator.normalizeProvider(provider)
            .lowercase(java.util.Locale.ROOT)
        return apps.firstOrNull { it.providerId == normalized }
    }
}
