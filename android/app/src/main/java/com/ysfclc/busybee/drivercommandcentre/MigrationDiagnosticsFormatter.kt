package com.ysfclc.busybee.drivercommandcentre

/** Stable, shareable migration evidence for validating an existing installation. */
object MigrationDiagnosticsFormatter {
    fun format(report: UnifiedActivityLedgerMigrator.MigrationReport?): String {
        if (report == null) return "MIGRATION CHECK NOT RUN\\nUse REFRESH MIGRATION CHECK to inspect existing local data."
        return buildString {
            appendLine(if (report.complete) "MIGRATION COMPLETE" else "MIGRATION NEEDS ATTENTION")
            appendLine("Rows copied in this check: ${report.copiedRows}")
            appendLine("Legacy databases missing: ${report.missingDatabases.size}")
            report.missingDatabases.forEach { appendLine("MISSING DATABASE · $it") }
            report.tables.forEach { table ->
                appendLine()
                appendLine("${table.database} / ${table.table}")
                appendLine("Source rows: ${table.sourceRows}")
                appendLine("Copied now: ${table.copiedRows}")
                appendLine("Already present: ${table.alreadyCopiedRows}")
                appendLine("Identity verification: ${if (table.complete) "PASS" else "INCOMPLETE"}")
            }
        }.trim()
    }
}
