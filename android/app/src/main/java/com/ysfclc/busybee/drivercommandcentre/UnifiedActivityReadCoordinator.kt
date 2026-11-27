package com.ysfclc.busybee.drivercommandcentre

import android.content.Context

/**
 * Controlled read-source selection for the activity timeline.
 *
 * Driver Mirror is opt-in. When preferred, the coordinator requires a fresh row-level parity
 * check before reading it and falls back to the original ledger on any mismatch or read error.
 */
class UnifiedActivityReadCoordinator(
    context: Context,
    private val preferDriverMirror: Boolean = false
) {
    private val appContext = context.applicationContext

    data class Result(
        val items: List<UnifiedActivityItem>,
        val source: UnifiedActivityReadSource,
        val fallbackReason: String? = null,
        val parity: ActivityLedgerConsolidator.Report? = null
    )

    fun timeline(limit: Int = 100): Result {
        if (!preferDriverMirror) {
            return Result(read(UnifiedActivityReadSource.ORIGINAL_LEDGER, limit), UnifiedActivityReadSource.ORIGINAL_LEDGER)
        }

        val parity = try {
            ActivityLedgerConsolidator(appContext).use { it.verifyParity() }
        } catch (error: Exception) {
            return fallback(limit, null, "Parity check failed: ${error.javaClass.simpleName}: ${error.message ?: "No details"}")
        }

        if (!parity.complete) {
            val reason = when {
                parity.missingSource -> "Original activity ledger is missing"
                parity.missingTarget -> "Driver Mirror database is missing"
                else -> "Source and Driver Mirror row parity is incomplete"
            }
            return fallback(limit, parity, reason)
        }

        return try {
            Result(
                items = read(UnifiedActivityReadSource.DRIVER_MIRROR, limit),
                source = UnifiedActivityReadSource.DRIVER_MIRROR,
                parity = parity
            )
        } catch (error: Exception) {
            fallback(
                limit,
                parity,
                "Driver Mirror read failed: ${error.javaClass.simpleName}: ${error.message ?: "No details"}"
            )
        }
    }

    private fun fallback(
        limit: Int,
        parity: ActivityLedgerConsolidator.Report?,
        reason: String
    ): Result = Result(
        items = read(UnifiedActivityReadSource.ORIGINAL_LEDGER, limit),
        source = UnifiedActivityReadSource.ORIGINAL_LEDGER,
        fallbackReason = reason,
        parity = parity
    )

    private fun read(source: UnifiedActivityReadSource, limit: Int): List<UnifiedActivityItem> =
        UnifiedActivityRepository(appContext, source).use { it.timeline(limit) }
}
