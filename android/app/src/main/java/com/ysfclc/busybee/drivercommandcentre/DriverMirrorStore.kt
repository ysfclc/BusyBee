package com.ysfclc.busybee.drivercommandcentre

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

data class DriverRecord(
    val id: Long,
    val platform: String,
    val type: String,
    val externalId: String?,
    val occurredAt: String?,
    val pickup: String?,
    val dropoff: String?,
    val distanceKm: Double?,
    val gross: Double?,
    val fees: Double?,
    val bonus: Double?,
    val currency: String,
    val status: String?,
    val raw: String,
    val sourceName: String
)

data class DriverRecordCorrection(
    val occurredAt: String?,
    val pickup: String?,
    val dropoff: String?,
    val distanceKm: Double?,
    val gross: Double?,
    val fees: Double?,
    val bonus: Double?,
    val currency: String,
    val status: String?
)

data class MirrorSummary(
    val records: Int,
    val trips: Int,
    val offers: Int,
    val gross: Double?,
    val fees: Double?,
    val bonuses: Double?,
    val platforms: List<String>,
    val imports: Int,
    val nonPlnRecords: Int,
    val netComplete: Boolean
) {
    val knownNet: Double? get() = if (netComplete && gross != null && fees != null && bonuses != null) gross - fees + bonuses else null
}

object DriverCsv {
    val requiredHeaders = setOf("platform", "record_type")
    val supportedHeaders = setOf(
        "platform", "record_type", "external_id", "occurred_at", "pickup", "dropoff",
        "distance_km", "gross", "fees", "bonus", "currency", "status"
    )

    fun parse(text: String): List<Map<String, String>> {
        val rows = csvRows(text)
        if (rows.isEmpty()) return emptyList()
        val headers = rows.first().mapIndexed { index, value ->
            (if (index == 0) value.removePrefix("\uFEFF") else value).trim().lowercase().replace(" ", "_")
        }
        require(headers.distinct().size == headers.size) {
            "CSV contains duplicate column names after normalisation"
        }
        require(headers.toSet().containsAll(requiredHeaders)) {
            "CSV needs platform and record_type columns"
        }
        require(headers.all { it in supportedHeaders }) {
            "Unsupported column. Supported: ${supportedHeaders.joinToString()}"
        }
        return rows.drop(1).mapNotNull { cells ->
            if (cells.all { it.isBlank() }) return@mapNotNull null
            headers.mapIndexed { index, key ->
                key to cells.getOrNull(index).orEmpty().trim()
            }.toMap()
        }
    }

    private fun csvRows(input: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '"' && quoted && i + 1 < input.length && input[i + 1] == '"' -> {
                    cell.append('"'); i++
                }
                c == '"' -> quoted = !quoted
                (c == ',' || c == '\n' || c == '\r') && !quoted -> {
                    row.add(cell.toString()); cell.setLength(0)
                    if (c == ',') {
                        // Continue the current row.
                    } else {
                        if (row.any { it.isNotBlank() }) result.add(row)
                        row = mutableListOf()
                        if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    }
                }
                else -> cell.append(c)
            }
            i++
        }
        require(!quoted) { "CSV contains an unclosed quoted field" }
        row.add(cell.toString())
        if (row.any { it.isNotBlank() }) result.add(row)
        return result
    }

    fun number(value: String?): Double? = value?.trim()?.takeIf { it.isNotEmpty() }
        ?.replace(" ", "")?.replace(",", ".")?.toDoubleOrNull()?.takeIf { it.isFinite() }

    fun validationErrors(rows: List<Map<String, String>>): List<String> = buildList {
        rows.forEachIndexed { index, row ->
            listOf("distance_km", "gross", "fees", "bonus").forEach { field ->
                val raw = row[field]?.trim().orEmpty()
                if (raw.isNotEmpty() && number(raw) == null) {
                    add("Row " + (index + 2) + ": " + field + " is not a valid number.")
                }
            }
            val distance = number(row["distance_km"])
            if (distance != null && distance < 0.0) {
                add("Row " + (index + 2) + ": distance_km cannot be negative.")
            }
        }
    }
}

class DriverMirrorStore(context: Context) :
    SQLiteOpenHelper(context, "busybee_driver_mirror.db", null, 6) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE imports (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_name TEXT NOT NULL,
                imported_at INTEGER NOT NULL,
                row_count INTEGER NOT NULL,
                inserted_count INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE raw_evidence (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                import_id INTEGER NOT NULL,
                row_number INTEGER NOT NULL,
                fingerprint TEXT NOT NULL,
                raw TEXT NOT NULL,
                source_name TEXT NOT NULL,
                FOREIGN KEY(import_id) REFERENCES imports(id)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fingerprint TEXT NOT NULL UNIQUE,
                import_id INTEGER NOT NULL,
                platform TEXT NOT NULL,
                record_type TEXT NOT NULL,
                external_id TEXT,
                occurred_at TEXT,
                pickup TEXT,
                dropoff TEXT,
                distance_km REAL,
                duration_minutes REAL,
                parse_confidence REAL,
                gross REAL,
                fees REAL,
                bonus REAL,
                currency TEXT NOT NULL,
                status TEXT,
                raw TEXT NOT NULL,
                source_name TEXT NOT NULL,
                FOREIGN KEY(import_id) REFERENCES imports(id)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX records_time_idx ON records(occurred_at DESC)")
        db.execSQL("CREATE INDEX records_platform_idx ON records(platform)")
        createRecordCorrectionsTable(db)
        createProviderEventsTable(db)
        UnifiedActivityLedgerSchema.createInDriverMirror(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createRecordCorrectionsTable(db)
        if (oldVersion < 3) db.execSQL("ALTER TABLE records ADD COLUMN duration_minutes REAL")
        if (oldVersion < 4) db.execSQL("ALTER TABLE records ADD COLUMN parse_confidence REAL")
        if (oldVersion < 5) {
            // PR #16 schema v4 already has provider_events but lacks corrections.
            // PR #29 schema v2 has corrections but lacks duration/confidence/events.
            // IF NOT EXISTS preserves whichever branch-local additions already exist.
            createRecordCorrectionsTable(db)
            createProviderEventsTable(db)
        }
        if (oldVersion < 6) {
            // Prepare the existing Driver Mirror database as the future activity-ledger owner.
            UnifiedActivityLedgerSchema.createInDriverMirror(db)
        }
    }

    private fun createRecordCorrectionsTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS record_corrections (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                record_id INTEGER NOT NULL,
                corrected_at INTEGER NOT NULL,
                before_values TEXT NOT NULL,
                after_values TEXT NOT NULL,
                FOREIGN KEY(record_id) REFERENCES records(id)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS record_corrections_record_idx ON record_corrections(record_id, corrected_at DESC)")
    }

    private fun createProviderEventsTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS provider_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                provider TEXT NOT NULL,
                dedupe_key TEXT NOT NULL,
                event_time INTEGER NOT NULL,
                received_at INTEGER NOT NULL,
                event_blob TEXT NOT NULL,
                UNIQUE(provider, dedupe_key)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS provider_events_time_idx ON provider_events(event_time DESC, received_at DESC)")
    }

    @Synchronized
    fun importRows(sourceName: String, rows: List<Map<String, String>>): Pair<Int, Int> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val import = ContentValues().apply {
                put("source_name", sourceName)
                put("imported_at", System.currentTimeMillis())
                put("row_count", rows.size)
                put("inserted_count", 0)
            }
            val importId = db.insertOrThrow("imports", null, import)
            var inserted = 0
            rows.forEachIndexed { rowIndex, row ->
                val platform = row["platform"].orEmpty().trim().ifBlank { "Unknown" }
                val type = row["record_type"].orEmpty().trim().lowercase().ifBlank { "unknown" }
                val raw = row.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
                val fingerprint = sha256(listOf(platform.lowercase(), type, row["external_id"].orEmpty(), row["occurred_at"].orEmpty(), raw).joinToString("|"))
                val evidence = ContentValues().apply {
                    put("import_id", importId)
                    put("row_number", rowIndex + 1)
                    put("fingerprint", fingerprint)
                    put("raw", raw)
                    put("source_name", sourceName)
                }
                db.insertOrThrow("raw_evidence", null, evidence)
                val values = ContentValues().apply {
                    put("fingerprint", fingerprint)
                    put("import_id", importId)
                    put("platform", platform)
                    put("record_type", type)
                    put("external_id", row["external_id"])
                    put("occurred_at", row["occurred_at"])
                    put("pickup", row["pickup"])
                    put("dropoff", row["dropoff"])
                    put("distance_km", DriverCsv.number(row["distance_km"]) )
                    put("gross", DriverCsv.number(row["gross"]))
                    put("fees", DriverCsv.number(row["fees"]))
                    put("bonus", DriverCsv.number(row["bonus"]))
                    put("currency", row["currency"]?.trim()?.uppercase() ?: "PLN")
                    put("status", row["status"])
                    put("raw", raw)
                    put("source_name", sourceName)
                }
                if (db.insertWithOnConflict("records", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) inserted++
            }
            db.execSQL("UPDATE imports SET inserted_count=? WHERE id=?", arrayOf(inserted, importId))
            db.setTransactionSuccessful()
            return rows.size to inserted
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun correctRecord(recordId: Long, correction: DriverRecordCorrection): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val before = JSONObject()
            val columns = listOf("occurred_at", "pickup", "dropoff", "distance_km", "gross", "fees", "bonus", "currency", "status")
            val found = db.rawQuery(
                "SELECT occurred_at,pickup,dropoff,distance_km,gross,fees,bonus,currency,status FROM records WHERE id=?",
                arrayOf(recordId.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) false else {
                    columns.forEachIndexed { index, column ->
                        before.put(
                            column,
                            if (cursor.isNull(index)) JSONObject.NULL
                            else if (column in setOf("distance_km", "gross", "fees", "bonus")) cursor.getDouble(index)
                            else cursor.getString(index)
                        )
                    }
                    true
                }
            }
            if (!found) return false

            val after = JSONObject().apply {
                put("occurred_at", correction.occurredAt ?: JSONObject.NULL)
                put("pickup", correction.pickup ?: JSONObject.NULL)
                put("dropoff", correction.dropoff ?: JSONObject.NULL)
                put("distance_km", correction.distanceKm ?: JSONObject.NULL)
                put("gross", correction.gross ?: JSONObject.NULL)
                put("fees", correction.fees ?: JSONObject.NULL)
                put("bonus", correction.bonus ?: JSONObject.NULL)
                put("currency", correction.currency.trim().uppercase())
                put("status", correction.status ?: JSONObject.NULL)
            }
            val values = ContentValues().apply {
                put("occurred_at", correction.occurredAt)
                put("pickup", correction.pickup)
                put("dropoff", correction.dropoff)
                put("distance_km", correction.distanceKm)
                put("gross", correction.gross)
                put("fees", correction.fees)
                put("bonus", correction.bonus)
                put("currency", correction.currency.trim().uppercase())
                put("status", correction.status)
            }
            if (db.update("records", values, "id=?", arrayOf(recordId.toString())) != 1) return false
            val history = ContentValues().apply {
                put("record_id", recordId)
                put("corrected_at", System.currentTimeMillis())
                put("before_values", before.toString())
                put("after_values", after.toString())
            }
            db.insertOrThrow("record_corrections", null, history)
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun correctionHistory(recordId: Long): List<String> {
        val history = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT corrected_at,before_values,after_values FROM record_corrections WHERE record_id=? ORDER BY id DESC",
            arrayOf(recordId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val changedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(cursor.getLong(0)))
                history.add(
                    "Changed at " + changedAt +
                        "\nBefore: " + cursor.getString(1) +
                        "\nAfter: " + cursor.getString(2)
                )
            }
        }
        return history
    }

    fun summary(): MirrorSummary {
        val db = readableDatabase
        fun scalar(sql: String): Double {
            db.rawQuery(sql, null).use { c -> return if (c.moveToFirst()) c.getDouble(0) else 0.0 }
        }
        fun nullableSum(column: String): Double? {
            db.rawQuery("SELECT SUM($column) FROM records WHERE currency='PLN' AND $column IS NOT NULL", null).use { c ->
                return if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0) else null
            }
        }
        val records = scalar("SELECT COUNT(*) FROM records").toInt()
        val trips = scalar("SELECT COUNT(*) FROM records WHERE lower(record_type) IN ('trip','ride','completed_trip')").toInt()
        val offers = scalar("SELECT COUNT(*) FROM records WHERE lower(record_type)='offer'").toInt()
        // Never add unlike currencies together. Headline totals are PLN-only.
        val gross = nullableSum("gross")
        val fees = nullableSum("fees")
        val bonuses = nullableSum("bonus")
        val imports = scalar("SELECT COUNT(*) FROM imports").toInt()
        val nonPln = scalar("SELECT COUNT(*) FROM records WHERE currency<>'PLN'").toInt()
        val grossRows = scalar("SELECT COUNT(*) FROM records WHERE currency='PLN' AND gross IS NOT NULL")
        val incompleteNetRows = scalar("SELECT COUNT(*) FROM records WHERE currency='PLN' AND gross IS NOT NULL AND (fees IS NULL OR bonus IS NULL)")
        val netComplete = grossRows > 0 && incompleteNetRows == 0.0
        val platforms = mutableListOf<String>()
        db.rawQuery("SELECT DISTINCT platform FROM records ORDER BY platform", null).use { c ->
            while (c.moveToNext()) platforms.add(c.getString(0))
        }
        return MirrorSummary(records, trips, offers, gross, fees, bonuses, platforms, imports, nonPln, netComplete)
    }

    fun recent(limit: Int = 30): List<DriverRecord> = queryRecords(limit)

    /** Full local ledger; period reports must never silently truncate at a recent-row limit. */
    fun allRecords(): List<DriverRecord> = queryRecords(null)

    private fun queryRecords(limit: Int?): List<DriverRecord> {
        val records = mutableListOf<DriverRecord>()
        val baseSql = "SELECT id,platform,record_type,external_id,occurred_at,pickup,dropoff,distance_km,gross,fees,bonus,currency,status,raw,source_name FROM records ORDER BY COALESCE(occurred_at,'') DESC,id DESC"
        val sql = if (limit == null) baseSql else "$baseSql LIMIT ?"
        val args = limit?.let { arrayOf(it.toString()) }
        readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) records.add(DriverRecord(
                cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4),
                cursor.getString(5), cursor.getString(6),
                if (cursor.isNull(7)) null else cursor.getDouble(7),
                if (cursor.isNull(8)) null else cursor.getDouble(8),
                if (cursor.isNull(9)) null else cursor.getDouble(9),
                if (cursor.isNull(10)) null else cursor.getDouble(10),
                cursor.getString(11), cursor.getString(12), cursor.getString(13), cursor.getString(14)
            ))
        }
        return records
    }

    fun importHistory(): List<String> {
        val items = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT source_name,imported_at,row_count,inserted_count FROM imports ORDER BY id DESC LIMIT 8", null).use { c ->
            while (c.moveToNext()) items.add("${c.getString(0)} · ${c.getInt(3)} new / ${c.getInt(2)} rows")
        }
        return items
    }

    fun evidencePreview(limit: Int = 8): List<String> {
        val items = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT source_name,row_number,raw FROM raw_evidence ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) items.add("${c.getString(0)} · row ${c.getInt(1)}\n${c.getString(2)}")
        }
        return items
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
