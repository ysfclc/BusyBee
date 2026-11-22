package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedActivityLedgerSchemaTest {
    @Test
    fun schemaDefinesEvidenceObservationEventsAndMigrationTracking() {
        val sql = UnifiedActivityLedgerSchema.createStatements.joinToString("\n").lowercase()
        assertTrue(sql.contains("create table activity_evidence"))
        assertTrue(sql.contains("create table activity_observations"))
        assertTrue(sql.contains("create table provider_events"))
        assertTrue(sql.contains("create table migration_sources"))
        assertTrue(sql.contains("create table migration_rows"))
    }

    @Test
    fun evidenceAndMigrationOriginsAreUniquelyIdentified() {
        val sql = UnifiedActivityLedgerSchema.createStatements.joinToString("\n").lowercase()
        assertTrue(sql.contains("origin_key text not null unique"))
        assertTrue(sql.contains("primary key(source_database, source_table, source_row_id)"))
    }

    @Test
    fun observationsPreserveUnknownSourceTimeAndEvidenceClassification() {
        val sql = UnifiedActivityLedgerSchema.createStatements.joinToString("\n").lowercase()
        assertTrue(sql.contains("source_occurred_at_ms integer"))
        assertTrue(sql.contains("received_at_ms integer not null"))
        assertTrue(sql.contains("classification text not null default 'unclassified'"))
    }

    @Test
    fun providerEventsHaveIdempotencyConstraint() {
        val sql = UnifiedActivityLedgerSchema.createStatements.joinToString("\n").lowercase()
        assertTrue(sql.contains("idempotency_key text not null unique"))
    }
}
