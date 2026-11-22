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
}
