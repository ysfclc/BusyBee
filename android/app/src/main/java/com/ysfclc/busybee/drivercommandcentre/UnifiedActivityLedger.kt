package com.ysfclc.busybee.drivercommandcentre

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * First stage of BusyBee's unified activity ledger.
 *
 * This database is intentionally separate from both legacy stores. Creating it must
 * never open, modify, rename, or delete the legacy databases.
 */
object UnifiedActivityLedgerSchema {
    const val DATABASE_NAME = "busybee_driver_activity.db"
    const val VERSION = 1

    val createStatements: List<String> = listOf(
        """CREATE TABLE activity_evidence (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            origin_key TEXT NOT NULL UNIQUE,
            source_database TEXT NOT NULL,
            source_table TEXT NOT NULL,
            source_row_id TEXT NOT NULL,
            provider TEXT,
            captured_at_ms INTEGER,
            received_at_ms INTEGER NOT NULL,
            evidence_kind TEXT NOT NULL,
            content_digest TEXT,
            content TEXT,
            content_ref TEXT
        )""",
        "CREATE INDEX activity_evidence_provider_time_idx ON activity_evidence(provider, received_at_ms DESC)",
        """CREATE TABLE activity_observations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            origin_key TEXT NOT NULL UNIQUE,
            provider TEXT,
            observation_kind TEXT NOT NULL,
            source_method TEXT NOT NULL,
            source_occurred_at_ms INTEGER,
            received_at_ms INTEGER NOT NULL,
            classification TEXT NOT NULL DEFAULT 'unclassified',
            lifecycle_id TEXT,
            status TEXT,
            amount REAL,
            currency TEXT,
            pickup TEXT,
            destination TEXT,
            distance_km REAL,
            duration_min REAL,
            evidence_id INTEGER,
            FOREIGN KEY(evidence_id) REFERENCES activity_evidence(id)
        )""",
        "CREATE INDEX activity_observations_time_idx ON activity_observations(source_occurred_at_ms DESC, received_at_ms DESC)",
        "CREATE INDEX activity_observations_provider_idx ON activity_observations(provider, observation_kind)",
        """CREATE TABLE provider_events (
            event_id TEXT PRIMARYPRIMARY KEY,
            idempotency_key TEXT NOT NULL UNIQUE,
            provider TEXT NOT NULL,
            adapter_id TEXT NOT NULL,
            adapter_version TEXT NOT NULL,
            event_type TEXT NOT NULL,
            lifecycle_id TEXT,
            source_method TEXT NOT NULL,
            evidence_class TEXT NOT NULL,
            source_event_id TEXT,
            source_occurred_at_ms INTEGER,
            received_at_ms INTEGER NOT NULL,
            expires_at_ms INTEGER,
            evidence_ref TEXT,
            expiry_evidence TEXT,
            payload_json TEXT NOT NULL
        )""".replace("event_id TEXT PRIMARYPRIMARY KEY","event_id TEXT PRIMARY KEY"),
        """CREATE TABLE migration_sources (
            source_database TEXT NOT NULL,
            source_table TEXT NOT NULL,
            source_schema_version INTEGER,
            started_at_ms INTEGER NOT NULL,
            completed_at_ms INTEGER,
            source_row_count INTEGER,
            migrated_row_count INTEGER,
            verification_digest TEXT,
            state TEXT NOT NULL DEFAULT 'in_progress',
            PRIMARY KEY(source_database, source_table)
        )""",
        """CREATE TABLE migration_rows (
            source_database TEXT NOT NULL,
            source_table TEXT NOT NULL,
            source_row_id TEXT NOT NULL,
            target_table TEXT NOT NULL,
            target_row_id INTEGER,
            migrated_at_ms INTEGER NOT NULL,
            PRIMARY KEY(source_database, source_table, source_row_id)
        )"""
    )

    fun create(db: SQLiteDatabase) {
        createStatements.forEach(db::execSQL)
    }
}

class UnifiedActivityLedger(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    UnifiedActivityLedgerSchema.DATABASE_NAME,
    null,
    UnifiedActivityLedgerSchema.VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        UnifiedActivityLedgerSchema.create(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never drop evidence. Future upgrades must be explicit, additive migrations.
    }
}
