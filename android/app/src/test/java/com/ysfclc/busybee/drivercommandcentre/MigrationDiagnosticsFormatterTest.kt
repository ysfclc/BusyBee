package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationDiagnosticsFormatterTest {
    @Test
    fun missingReportDoesNotClaimMigrationSuccess() {
        val result = MigrationDiagnosticsFormatter.format(null)
        assertTrue(result.contains("MIGRATION CHECK NOT RUN"))
        assertFalse(result.contains("MIGRATION COMPLETE"))
    }

    @Test
    fun completeReportIncludesPerTableCountsAndPassStatus() {
        val report = UnifiedActivityLedgerMigrator.MigrationReport(
            tables = listOf(
                UnifiedActivityLedgerMigrator.TableResult(
                    database = "legacy.db",
                    table = "records",
                    sourceRows = 12,
                    copiedRows = 12,
                    alreadyCopiedRows = 0,
                    complete = true
                )
            ),
            missingDatabases = emptyList()
        )
        val result = MigrationDiagnosticsFormatter.format(report)
        assertTrue(result.contains("MIGRATION COMPLETE"))
        assertTrue(result.contains("Source rows: 12"))
        assertTrue(result.contains("Copied now: 12"))
        assertTrue(result.contains("Identity verification: PASS"))
    }

    @Test
    fun incompleteReportNamesMissingDatabaseAndIncompleteTable() {
        val report = UnifiedActivityLedgerMigrator.MigrationReport(
            tables = listOf(
                UnifiedActivityLedgerMigrator.TableResult(
                    database = "legacy.db",
                    table = "records",
                    sourceRows = 12,
                    copiedRows = 4,
                    alreadyCopiedRows = 3,
                    complete = false
                )
            ),
            missingDatabases = listOf("old_offers.db")
        )
        val result = MigrationDiagnosticsFormatter.format(report)
        assertTrue(result.contains("MIGRATION NEEDS ATTENTION"))
        assertTrue(result.contains("MISSING DATABASE · old_offers.db"))
        assertTrue(result.contains("Identity verification: INCOMPLETE"))
    }

    @Test
    fun consolidationFormatterDoesNotClaimSuccessWhenSourceIsMissing() {
        val result = MigrationDiagnosticsFormatter.formatConsolidation(
            ActivityLedgerConsolidator.Report(emptyList(), missingSource = true)
        )
        assertTrue(result.contains("DRIVER MIRROR V6 CONSOLIDATION NEEDS ATTENTION"))
        assertTrue(result.contains("SOURCE LEDGER MISSING"))
        assertFalse(result.contains("CONSOLIDATION COMPLETE"))
    }

    @Test
    fun parityFormatterNamesMissingTargetWithoutClaimingSuccess() {
        val result = MigrationDiagnosticsFormatter.formatConsolidation(
            ActivityLedgerConsolidator.Report(emptyList(), missingSource = false, missingTarget = true)
        )
        assertTrue(result.contains("DRIVER MIRROR V6 CONSOLIDATION NEEDS ATTENTION"))
        assertTrue(result.contains("TARGET MIRROR MISSING"))
        assertFalse(result.contains("CONSOLIDATION COMPLETE"))
    }

    @Test
    fun consolidationFormatterShowsPerTableParityAndCounts() {
        val result = MigrationDiagnosticsFormatter.formatConsolidation(
            ActivityLedgerConsolidator.Report(
                tables = listOf(
                    ActivityLedgerConsolidator.TableResult("activity_evidence", "activity_evidence", 1, 1, 0, true),
                    ActivityLedgerConsolidator.TableResult("activity_observations", "activity_observations", 1, 1, 0, true),
                    ActivityLedgerConsolidator.TableResult("provider_events", "provider_event_envelopes", 3, 2, 1, true),
                    ActivityLedgerConsolidator.TableResult("migration_sources", "migration_sources", 1, 1, 0, true),
                    ActivityLedgerConsolidator.TableResult("migration_rows", "migration_rows", 1, 1, 0, true)
                ),
                missingSource = false
            )
        )
        assertTrue(result.contains("DRIVER MIRROR V6 CONSOLIDATION COMPLETE"))
        assertTrue(result.contains("provider_events → provider_event_envelopes"))
        assertTrue(result.contains("Source rows: 3"))
        assertTrue(result.contains("Row parity: PASS"))
    }
}
