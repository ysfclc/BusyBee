package com.ysfclc.busybee.drivercommandcentre

/** Stable, shareable migration evidence for validating an existing installation. */
object MigrationDiagnosticsFormatter {
    fun format(report: UnifiedActivityLedgerMigrator.MigrationReport?): String {
        if (report == null) return "MIGRATION CHECK NOT RUN\nUse REFRESH MIGRATION CHECK to inspect existing local data."
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


    fun formatConsolidation(report: ActivityLedgerConsolidator.Report?): String {
        if (report == null) return "DRIVER MIRROR V6 CONSOLIDATION CHECK NOT RUN"
        return buildString {
            appendLine(if (report.complete) "DRIVER MIRROR V6 CONSOLIDATION COMPLETE" else "DRIVER MIRROR V6 CONSOLIDATION NEEDS ATTENTION")
            if (report.missingSource) appendLine("SOURCE LEDGER MISSING · ${UnifiedActivityLedgerSchema.DATABASE_NAME}")
            appendLine("Rows copied in this check: ${report.copiedRows}")
            report.tables.forEach { table ->
                appendLine()
                appendLine("${table.sourceTable} → ${table.targetTable}")
                appendLine("Source rows: ${table.sourceRows}")
                appendLine("Copied now: ${table.copiedRows}")
                appendLine("Already present: ${table.alreadyPresentRows}")
                appendLine("Row parity: ${if (table.complete) "PASS" else "INCOMPLETE"}")
            }
        }.trim()
    }
