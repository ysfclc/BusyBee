package com.ysfclc.busybee.drivercommandcentre

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.security.MessageDigest

/**
 * One-time, additive copy from the original activity-ledger database into Driver Mirror v6.
 *
 * The source is opened read-only and is never deleted or modified. Repository reads and the
 * existing UnifiedActivityLedgerMigrator remain pointed at the source database until a later,
 * separately verified cutover.
 */
class ActivityLedgerConsolidator(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val appContext = context.applicationContext
    private val sourceName = UnifiedActivityLedgerSchema.DATABASE_NAME
    private val target = DriverMirrorStore(appContext)

    data class TableResult(
        val sourceTable: String,
        val targetTable: String,
        val sourceRows: Int,
        val copiedRows: Int,
        val alreadyPresentRows: Int,
        val complete: Boolean
    )

    data class Report(
        val tables: List<TableResult>,
        val missingSource: Boolean
    ) {
        val complete: Boolean get() = !missingSource && tables.size == TABLES.size && tables.all { it.complete }
        val copiedRows: Int get() = tables.sumOf { it.copiedRows }
    }

    private data class TableSpec(
        val source: String,
        val target: String,
        val primaryKey: List<String>
    )

    fun consolidateIfNeeded(): Report = synchronized(CONSOLIDATION_LOCK) {
        val file = appContext.getDatabasePath(sourceName)
        if (!file.isFile) return@synchronized Report(emptyList(), missingSource = true)

        val source = try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (error: Exception) {
            throw IllegalStateException("Cannot open existing activity ledger read-only: $sourceName", error)
        }

        source.use { sourceDb ->
            val destination = target.writableDatabase
            val results = TABLES.map { spec ->
                requireTable(sourceDb, spec.source)
                requireTable(destination, spec.target)
                copyTable(sourceDb, destination, spec)
            }
            Report(results, missingSource = false)
        }
    }

    private fun copyTable(source: SQLiteDatabase, destination: SQLiteDatabase, spec: TableSpec): TableResult {
        val sourceColumns = columns(source, spec.source)
        val targetColumns = columns(destination, spec.target)
        require(sourceColumns.toSet() == targetColumns.toSet()) {
            "Schema mismatch for ${spec.source} -> ${spec.target}: source=${sourceColumns.toSet()}, target=${targetColumns.toSet()}"
        }
        require(spec.primaryKey.all { it in sourceColumns }) {
            "Missing stable primary key for ${spec.source}: ${spec.primaryKey}"
        }

        val sourceCount = scalar(source, "SELECT COUNT(*) FROM ${spec.source}")
        val startedAt = now()
        destination.execSQL(
            """INSERT OR IGNORE INTO migration_sources
               (source_database,source_table,source_schema_version,started_at_ms,state)
               VALUES(?,?,?,?, 'in_progress')""",
            arrayOf(MARKER_DATABASE, spec.source, source.version, startedAt)
        )
        destination.execSQL(
            """UPDATE migration_sources SET source_schema_version=?,state='in_progress'
               WHERE source_database=? AND source_table=?""",
            arrayOf(source.version, MARKER_DATABASE, spec.source)
        )

        var copied = 0
        var alreadyPresent = 0
        val orderBy = spec.primaryKey.joinToString(",") { quoteIdentifier(it) }
        source.rawQuery("SELECT * FROM ${quoteIdentifier(spec.source)} ORDER BY $orderBy", null).use { rows ->
            while (rows.moveToNext()) {
                val sourceRowId = stableRowId(rows, spec.primaryKey)
                val existingMarker = markerExists(destination, spec.source, sourceRowId)
                val destinationRowId = rows.longOrNull("id")
                if (existingMarker) {
                    verifyTargetRow(destination, rows, spec, sourceRowId)
                    alreadyPresent++
                    continue
                }

                destination.beginTransaction()
                try {
                    val values = rows.toContentValues()
                    val inserted = destination.insertWithOnConflict(
                        quoteIdentifier(spec.target), null, values, SQLiteDatabase.CONFLICT_IGNORE
                    )
                    // A pre-existing identical row is acceptable; any differing unique-key
                    // collision is rejected by verifyTargetRow rather than silently ignored.
                    verifyTargetRow(destination, rows, spec, sourceRowId)
                    insertMarker(destination, spec, sourceRowId, destinationRowId)
                    destination.setTransactionSuccessful()
                    if (inserted == -1L) alreadyPresent++ else copied++
                } finally {
                    destination.endTransaction()
                }
            }
        }

        val markerCount = scalar(
            destination,
            """SELECT COUNT(*) FROM migration_rows WHERE source_database=? AND source_table=?""",
            arrayOf(MARKER_DATABASE, spec.source)
        )
        val sourceDigest = digestSourceKeys(source, spec)
        val markerDigest = digestMarkerKeys(destination, spec.source)
        val complete = markerCount == sourceCount && sourceDigest == markerDigest
        destination.execSQL(
            """UPDATE migration_sources SET completed_at_ms=?,source_row_count=?,
               migrated_row_count=?,verification_digest=?,state=?
               WHERE source_database=? AND source_table=?""",
            arrayOf(
                if (complete) now() else null,
                sourceCount,
                markerCount,
                sourceDigest,
                if (complete) "complete" else "incomplete",
                MARKER_DATABASE,
                spec.source
            )
        )
        check(complete) {
            "Consolidation verification failed for ${spec.source}: source=$sourceCount markers=$markerCount"
        }
        return TableResult(spec.source, spec.target, sourceCount, copied, alreadyPresent, complete)
    }

    private fun verifyTargetRow(
        destination: SQLiteDatabase,
        sourceRow: Cursor,
        spec: TableSpec,
        sourceRowId: String
    ) {
        val where = spec.primaryKey.joinToString(" AND ") { "${quoteIdentifier(it)}=?" }
        val args = spec.primaryKey.map { sourceRow.stringOrNull(it) ?: error("Null primary key column: $it") }.toTypedArray()
        destination.rawQuery(
            "SELECT * FROM ${quoteIdentifier(spec.target)} WHERE $where",
            args
        ).use { targetRow ->
            check(targetRow.moveToFirst()) {
                "Target row missing after insert/conflict for ${spec.source}[$sourceRowId]"
            }
            check(rowsEqual(sourceRow, targetRow)) {
                "Conflicting target payload for ${spec.target}[$sourceRowId]; refusing to mark copied"
            }
        }
    }

    private fun rowsEqual(source: Cursor, destination: Cursor): Boolean {
        for (index in 0 until source.columnCount) {
            val name = source.getColumnName(index)
            val targetIndex = destination.getColumnIndex(name)
            if (targetIndex < 0 || source.getType(index) != destination.getType(targetIndex)) return false
            when (source.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> Unit
                Cursor.FIELD_TYPE_INTEGER -> if (source.getLong(index) != destination.getLong(targetIndex)) return false
                Cursor.FIELD_TYPE_FLOAT -> if (source.getDouble(index).toBits() != destination.getDouble(targetIndex).toBits()) return false
                Cursor.FIELD_TYPE_STRING -> if (source.getString(index) != destination.getString(targetIndex)) return false
                Cursor.FIELD_TYPE_BLOB -> if (!source.getBlob(index).contentEquals(destination.getBlob(targetIndex))) return false
            }
        }
        return true
    }

    private fun insertMarker(
        destination: SQLiteDatabase,
        spec: TableSpec,
        sourceRowId: String,
        targetRowId: Long?
    ) {
        val marker = ContentValues().apply {
            put("source_database", MARKER_DATABASE)
            put("source_table", spec.source)
            put("source_row_id", sourceRowId)
            put("target_table", spec.target)
            if (targetRowId == null) putNull("target_row_id") else put("target_row_id", targetRowId)
            put("migrated_at_ms", now())
        }
        destination.insertOrThrow("migration_rows", null, marker)
    }

    private fun markerExists(destination: SQLiteDatabase, table: String, rowId: String): Boolean =
        destination.rawQuery(
            """SELECT 1 FROM migration_rows
               WHERE source_database=? AND source_table=? AND source_row_id=?""",
            arrayOf(MARKER_DATABASE, table, rowId)
        ).use { it.moveToFirst() }

    private fun digestSourceKeys(source: SQLiteDatabase, spec: TableSpec): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val orderBy = spec.primaryKey.joinToString(",") { quoteIdentifier(it) }
        val keys = mutableListOf<String>()
        source.rawQuery("SELECT * FROM ${quoteIdentifier(spec.source)} ORDER BY $orderBy", null).use { rows ->
            while (rows.moveToNext()) keys.add(stableRowId(rows, spec.primaryKey))
        }
        keys.sorted().forEach { updateDigest(digest, it) }
        return digest.finishHex()
    }

    private fun digestMarkerKeys(destination: SQLiteDatabase, table: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        destination.rawQuery(
            """SELECT source_row_id FROM migration_rows
               WHERE source_database=? AND source_table=? ORDER BY source_row_id""",
            arrayOf(MARKER_DATABASE, table)
        ).use { rows ->
            while (rows.moveToNext()) updateDigest(digest, rows.getString(0))
        }
        return digest.finishHex()
    }

    private fun stableRowId(cursor: Cursor, keys: List<String>): String =
        keys.joinToString("") { key ->
            val value = cursor.stringOrNull(key) ?: "<NULL>"
            "${value.length}:$value;"
        }

    private fun columns(db: SQLiteDatabase, table: String): List<String> {
        val result = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info(${quoteIdentifier(table)})", null).use { cursor ->
            while (cursor.moveToNext()) result.add(cursor.getString(1))
        }
        return result
    }

    private fun requireTable(db: SQLiteDatabase, table: String) {
        val exists = db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table)
        ).use { it.moveToFirst() }
        check(exists) { "Required activity-ledger table is missing: $table" }
    }

    private fun scalar(db: SQLiteDatabase, sql: String, args: Array<String>? = null): Int =
        db.rawQuery(sql, args).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun Cursor.toContentValues(): ContentValues = ContentValues().apply {
        for (index in 0 until columnCount) {
            val name = getColumnName(index)
            when (getType(index)) {
                Cursor.FIELD_TYPE_NULL -> putNull(name)
                Cursor.FIELD_TYPE_INTEGER -> put(name, getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> put(name, getDouble(index))
                Cursor.FIELD_TYPE_STRING -> put(name, getString(index))
                Cursor.FIELD_TYPE_BLOB -> put(name, getBlob(index))
            }
        }
    }

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }

    private fun updateDigest(digest: MessageDigest, value: String) {
        digest.update(value.toByteArray(Charsets.UTF_8))
        digest.update(0)
    }

    private fun MessageDigest.finishHex(): String =
        digest().joinToString("") { "%02x".format(it) }

    private fun quoteIdentifier(value: String): String = "`" + value.replace("`", "``") + "`"

    companion object {
        private const val MARKER_DATABASE = "__consolidation__:busybee_driver_activity.db"
        private val CONSOLIDATION_LOCK = Any()
        private val TABLES = listOf(
            TableSpec("activity_evidence", "activity_evidence", listOf("id")),
            TableSpec("activity_observations", "activity_observations", listOf("id")),
            TableSpec("provider_events", "provider_event_envelopes", listOf("event_id")),
            TableSpec("migration_sources", "migration_sources", listOf("source_database", "source_table")),
            TableSpec("migration_rows", "migration_rows", listOf("source_database", "source_table", "source_row_id"))
        )
    }
}
