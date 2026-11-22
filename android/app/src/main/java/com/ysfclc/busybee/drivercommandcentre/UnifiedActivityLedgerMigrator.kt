package com.ysfclc.busybee.drivercommandcentre

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * Copies the provider-activity portions of the two legacy stores into the new
 * ledger. Legacy files are opened read-only and are never created or modified.
 *
 * Each source row and its migration marker are committed in one target transaction.
 * A process death therefore leaves either both records or neither, making retries safe.
 */
class UnifiedActivityLedgerMigrator(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val appContext = context.applicationContext
    private val target = UnifiedActivityLedger(appContext)

    data class TableResult(
        val database: String,
        val table: String,
        val sourceRows: Int,
        val copiedRows: Int,
        val alreadyCopiedRows: Int,
        val complete: Boolean
    )

    data class MigrationReport(
        val tables: List<TableResult>,
        val missingDatabases: List<String>
    ) {
        val complete: Boolean get() = missingDatabases.isEmpty() && tables.all { it.complete }
        val copiedRows: Int get() = tables.sumOf { it.copiedRows }
    }

    fun migrateIfNeeded(): MigrationReport {
        val results = mutableListOf<TableResult>()
        val missing = mutableListOf<String>()
        val mirrorName = "busybee_driver_mirror.db"
        val commandName = "busybee_command_centre.db"

        migrateDatabase(mirrorName, listOf("imports", "raw_evidence", "records"), results, missing)
        migrateDatabase(commandName, listOf("offers", "notices"), results, missing)
        return MigrationReport(results, missing)
    }

    private fun migrateDatabase(
        databaseName: String,
        tables: List<String>,
        results: MutableList<TableResult>,
        missing: MutableList<String>
    ) {
        val file = appContext.getDatabasePath(databaseName)
        if (!file.isFile) {
            // Absence is not completion: the app may create/import this database later.
            missing.add(databaseName)
            return
        }

        val source = try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (error: Exception) {
            throw IllegalStateException("Cannot open existing legacy database read-only: $databaseName", error)
        }

        source.use { db ->
            tables.forEach { table ->
                if (!tableExists(db, table)) {
                    results.add(TableResult(databaseName, table, 0, 0, 0, true))
                } else {
                    results.add(copyTable(db, databaseName, table))
                }
            }
        }
    }

    private fun copyTable(source: SQLiteDatabase, databaseName: String, table: String): TableResult {
        val sourceCount = source.rawQuery("SELECT COUNT(*) FROM $table", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        val startedAt = now()
        target.writableDatabase.execSQL(
            """INSERT OR IGNORE INTO migration_sources
               (source_database,source_table,source_schema_version,started_at_ms,state)
               VALUES(?,?,?,?, 'in_progress')""",
            arrayOf(databaseName, table, source.version, startedAt)
        )
        target.writableDatabase.execSQL(
            """UPDATE migration_sources SET source_schema_version=?,state='in_progress'
               WHERE source_database=? AND source_table=?""",
            arrayOf(source.version, databaseName, table)
        )

        var copied = 0
        var skipped = 0
        source.rawQuery("SELECT * FROM $table ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) {
                val rowId = cursor.longOrNull("id")?.toString()
                    ?: throw IllegalStateException("$databaseName.$table has no stable id column")
                val origin = "$databaseName:$table:$rowId"
                if (migrationRowExists(databaseName, table, rowId)) {
                    skipped++
                    continue
                }
                val db = target.writableDatabase
                db.beginTransaction()
                try {
                    val targetRowId = when {
                        databaseName == "busybee_driver_mirror.db" && table == "imports" ->
                            copyImportMetadata(db, databaseName, table, cursor, origin, rowId)
                        databaseName == "busybee_driver_mirror.db" && table == "raw_evidence" ->
                            copyMirrorEvidence(db, databaseName, table, cursor, origin, rowId)
                        databaseName == "busybee_driver_mirror.db" && table == "records" ->
                            copyMirrorRecord(db, databaseName, table, cursor, origin, rowId)
                        databaseName == "busybee_command_centre.db" && table == "offers" ->
                            copyOffer(db, databaseName, table, cursor, origin, rowId)
                        databaseName == "busybee_command_centre.db" && table == "notices" ->
                            copyNotice(db, databaseName, table, cursor, origin, rowId)
                        else -> null
                    }
                    val marker = ContentValues().apply {
                        put("source_database", databaseName)
                        put("source_table", table)
                        put("source_row_id", rowId)
                        put("target_table", if (table == "imports" || table == "raw_evidence" || table == "notices") "activity_evidence" else "activity_observations")
                        if (targetRowId == null) putNull("target_row_id") else put("target_row_id", targetRowId)
                        put("migrated_at_ms", now())
                    }
                    db.insertOrThrow("migration_rows", null, marker)
                    db.setTransactionSuccessful()
                    copied++
                } finally {
                    db.endTransaction()
                }
            }
        }

        val migratedCount = target.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM migration_rows WHERE source_database=? AND source_table=?",
            arrayOf(databaseName, table)
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val complete = migratedCount == sourceCount
        val digest = digestSourceIds(source, table)
        target.writableDatabase.execSQL(
            """UPDATE migration_sources SET completed_at_ms=?,source_row_count=?,
               migrated_row_count=?,verification_digest=?,state=? WHERE source_database=? AND source_table=?""",
            arrayOf(if (complete) now() else null, sourceCount, migratedCount, digest,
                if (complete) "complete" else "incomplete", databaseName, table)
        )
        return TableResult(databaseName, table, sourceCount, copied, skipped, complete)
    }

    private fun copyImportMetadata(
        db: SQLiteDatabase, database: String, table: String, c: Cursor, origin: String, rowId: String
    ): Long {
        val content = JSONObject().apply {
            put("source_name", c.stringOrNull("source_name"))
            put("imported_at", c.longOrNull("imported_at"))
            put("row_count", c.longOrNull("row_count"))
            put("inserted_count", c.longOrNull("inserted_count"))
        }.toString()
        return insertEvidence(db, database, table, origin, rowId, null,
            c.longOrNull("imported_at"), "import_metadata", content, sha256(content), null)
    }

    private fun copyMirrorEvidence(
        db: SQLiteDatabase, database: String, table: String, c: Cursor, origin: String, rowId: String
    ): Long {
        val raw = c.stringOrNull("raw").orEmpty()
        return insertEvidence(db, database, table, origin, rowId, null, null,
            "csv_row", raw, c.stringOrNull("fingerprint") ?: sha256(raw), c.stringOrNull("source_name"))
    }

    private fun copyMirrorRecord(
        db: SQLiteDatabase, database: String, table: String, c: Cursor, origin: String, rowId: String
    ): Long {
        val fingerprint = c.stringOrNull("fingerprint")
        val evidenceId = fingerprint?.let {
            db.rawQuery(
                """SELECT id FROM activity_evidence WHERE source_database=?
                   AND source_table='raw_evidence' AND content_digest=? ORDER BY id LIMIT 1""",
                arrayOf(database, it)
            ).use { rows -> if (rows.moveToFirst()) rows.getLong(0) else null }
        }
        val provider = c.stringOrNull("platform")
        val receivedAt = importTime(c.longOrNull("import_id"))
        val values = ContentValues().apply {
            put("origin_key", origin)
            put("provider", provider)
            put("observation_kind", c.stringOrNull("record_type")?.lowercase() ?: "unknown")
            put("source_method", ProviderSourceMethod.USER_EXPORT.name)
            putNull("source_occurred_at_ms") // CSV date strings are not assumed to be epoch timestamps.
            put("received_at_ms", receivedAt ?: now())
            put("classification", "unclassified")
            put("lifecycle_id", c.stringOrNull("external_id"))
            put("status", c.stringOrNull("status"))
            putNullableDouble("amount", c.doubleOrNull("gross"))
            put("currency", c.stringOrNull("currency") ?: "PLN")
            put("pickup", c.stringOrNull("pickup"))
            put("destination", c.stringOrNull("dropoff"))
            putNullableDouble("distance_km", c.doubleOrNull("distance_km"))
            putNull("duration_min")
            if (evidenceId == null) putNull("evidence_id") else put("evidence_id", evidenceId)
        }
        return db.insertOrThrow("activity_observations", null, values)
    }

    private fun copyOffer(
        db: SQLiteDatabase, database: String, table: String, c: Cursor, origin: String, rowId: String
    ): Long {
        val capturedAt = c.longOrNull("captured_at") ?: now()
        val raw = c.stringOrNull("raw").orEmpty()
        val snapshot = JSONObject().apply {
            put("raw", raw)
            put("fare", c.doubleOrNull("fare"))
            put("bonus", c.doubleOrNull("bonus"))
            put("pickup_km", c.doubleOrNull("pickup_km"))
            put("trip_km", c.doubleOrNull("trip_km"))
            put("duration_min", c.doubleOrNull("duration_min"))
            put("passenger_rating", c.doubleOrNull("passenger_rating"))
            put("source", c.stringOrNull("source"))
            put("status", c.stringOrNull("status"))
        }.toString()
        val evidenceId = insertEvidence(db, database, table, origin, rowId,
            c.stringOrNull("platform"), capturedAt, "local_offer_record", snapshot, sha256(snapshot), null)
        val values = ContentValues().apply {
            put("origin_key", origin)
            put("provider", c.stringOrNull("platform"))
            put("observation_kind", "offer")
            put("source_method", if (c.stringOrNull("source").equals("manual", true))
                ProviderSourceMethod.MANUAL_ENTRY.name else ProviderSourceMethod.USER_CAPTURE.name)
            putNull("source_occurred_at_ms")
            put("received_at_ms", capturedAt)
            put("classification", "unclassified")
            put("lifecycle_id", null as String?)
            put("status", c.stringOrNull("status"))
            putNullableDouble("amount", c.doubleOrNull("fare"))
            putNull("currency")
            put("pickup", c.stringOrNull("pickup"))
            put("destination", c.stringOrNull("destination"))
            putNullableDouble("distance_km", sumNullable(c.doubleOrNull("pickup_km"), c.doubleOrNull("trip_km")))
            putNullableDouble("duration_min", c.doubleOrNull("duration_min"))
            put("evidence_id", evidenceId)
        }
        return db.insertOrThrow("activity_observations", null, values)
    }

    private fun copyNotice(
        db: SQLiteDatabase, database: String, table: String, c: Cursor, origin: String, rowId: String
    ): Long {
        val platform = c.stringOrNull("platform")
        val title = c.stringOrNull("title").orEmpty()
        val body = c.stringOrNull("body").orEmpty()
        val capturedAt = c.longOrNull("captured_at") ?: now()
        val content = JSONObject().apply { put("title", title); put("body", body) }.toString()
        val evidenceId = insertEvidence(db, database, table, origin, rowId, platform,
            capturedAt, "notification", content, sha256("$platform|$title|$body"), null)
        val values = ContentValues().apply {
            put("origin_key", origin)
            put("provider", platform)
            put("observation_kind", "notification")
            put("source_method", ProviderSourceMethod.ANDROID_NOTIFICATION.name)
            putNull("source_occurred_at_ms")
            put("received_at_ms", capturedAt)
            put("classification", "unclassified")
            putNull("lifecycle_id")
            putNull("status")
            putNull("amount")
            putNull("currency")
            putNull("pickup")
            putNull("destination")
            putNull("distance_km")
            putNull("duration_min")
            put("evidence_id", evidenceId)
        }
        db.insertOrThrow("activity_observations", null, values)
        return evidenceId
    }

    private fun insertEvidence(
        db: SQLiteDatabase, database: String, table: String, origin: String, rowId: String,
        provider: String?, capturedAt: Long?, kind: String, content: String, digest: String, contentRef: String?
    ): Long {
        val values = ContentValues().apply {
            put("origin_key", origin)
            put("source_database", database)
            put("source_table", table)
            put("source_row_id", rowId)
            put("provider", provider)
            if (capturedAt == null) putNull("captured_at_ms") else put("captured_at_ms", capturedAt)
            put("received_at_ms", capturedAt ?: now())
            put("evidence_kind", kind)
            put("content_digest", digest)
            put("content", content)
            put("content_ref", contentRef)
        }
        db.insertWithOnConflict("activity_evidence", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        return db.rawQuery("SELECT id FROM activity_evidence WHERE origin_key=?", arrayOf(origin)).use {
            if (it.moveToFirst()) it.getLong(0) else throw IllegalStateException("Evidence insert failed for $origin")
        }
    }

    private fun importTime(importId: Long?): Long? {
        if (importId == null) return null
        val file = appContext.getDatabasePath("busybee_driver_mirror.db")
        if (!file.isFile) return null
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { source ->
            return source.rawQuery("SELECT imported_at FROM imports WHERE id=?", arrayOf(importId.toString())).use {
                if (it.moveToFirst()) it.longOrNull("imported_at") else null
            }
        }
    }

    private fun migrationRowExists(database: String, table: String, rowId: String): Boolean =
        target.readableDatabase.rawQuery(
            "SELECT 1 FROM migration_rows WHERE source_database=? AND source_table=? AND source_row_id=?",
            arrayOf(database, table, rowId)
        ).use { it.moveToFirst() }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }

    private fun digestSourceIds(db: SQLiteDatabase, table: String): String {
        val ids = mutableListOf<String>()
        db.rawQuery("SELECT id FROM $table ORDER BY id", null).use { c ->
            while (c.moveToNext()) ids.add(c.getString(0))
        }
        return sha256(ids.joinToString("|"))
    }

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val value = stringOrNull(column) ?: return null
        return value.toLongOrNull()
    }

    private fun Cursor.doubleOrNull(column: String): Double? {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return null
        return getDouble(index).takeIf { it.isFinite() }
    }

    private fun ContentValues.putNullableDouble(key: String, value: Double?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun sumNullable(a: Double?, b: Double?): Double? = if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
