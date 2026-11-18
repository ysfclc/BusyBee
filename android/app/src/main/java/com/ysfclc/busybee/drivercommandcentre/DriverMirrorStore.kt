package com.ysfclc.busybee.drivercommandcentre

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

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

data class PcapdroidCaptureSummary(
    val id: Long,
    val sourceName: String,
    val provider: String,
    val importedAt: Long,
    val callCount: Int,
    val normalizedCount: Int,
    val insertedCount: Int
)

data class PcapdroidStoredCall(
    val sequence: Int,
    val host: String,
    val method: String,
    val path: String,
    val status: Int?,
    val startedAt: String?,
    val requestMime: String?,
    val responseMime: String?,
    val classification: String,
    val requestKind: String,
    val responseKind: String,
    val requestBytes: Int,
    val responseBytes: Int,
    val requestPreview: String,
    val responsePreview: String
)

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
        require(headers.toSet().containsAll(requiredHeaders)) {
            "CSV needs platform and record_type columns"
        }
        require(headers.all { it in supportedHeaders }) {
            "Unsupported column. Supported: ${supportedHeaders.joinToString()}"
        }
        return rows.drop(1).mapNotNull { cells ->
            if (cells.all { it.isBlank() }) return@mapNotNull null
            headers.mapIndexedNotNull { index, key ->
                if (index < cells.size) key to cells[index].trim() else null
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
}

class DriverMirrorStore(context: Context) :
    SQLiteOpenHelper(context, "busybee_driver_mirror.db", null, 2) {

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
                capture_id INTEGER,
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
                gross REAL,
                fees REAL,
                bonus REAL,
                currency TEXT NOT NULL,
                status TEXT,
                raw TEXT NOT NULL,
                source_name TEXT NOT NULL,
                capture_id INTEGER,
                FOREIGN KEY(import_id) REFERENCES imports(id)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX records_time_idx ON records(occurred_at DESC)")
        db.execSQL("CREATE INDEX records_platform_idx ON records(platform)")
        createPcapdroidTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE raw_evidence ADD COLUMN capture_id INTEGER")
            db.execSQL("ALTER TABLE records ADD COLUMN capture_id INTEGER")
            createPcapdroidTables(db)
        }
    }

    private fun createPcapdroidTables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE pcapdroid_captures (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_name TEXT NOT NULL,
                provider TEXT NOT NULL,
                imported_at INTEGER NOT NULL,
                call_count INTEGER NOT NULL,
                normalized_count INTEGER NOT NULL,
                inserted_count INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE pcapdroid_calls (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                capture_id INTEGER NOT NULL,
                sequence INTEGER NOT NULL,
                host TEXT NOT NULL,
                method TEXT NOT NULL,
                path TEXT NOT NULL,
                status INTEGER,
                started_at TEXT,
                request_mime TEXT,
                response_mime TEXT,
                classification TEXT NOT NULL,
                request_kind TEXT NOT NULL,
                response_kind TEXT NOT NULL,
                request_bytes INTEGER NOT NULL,
                response_bytes INTEGER NOT NULL,
                request_preview TEXT NOT NULL,
                response_preview TEXT NOT NULL,
                FOREIGN KEY(capture_id) REFERENCES pcapdroid_captures(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX pcapdroid_calls_capture_idx ON pcapdroid_calls(capture_id, sequence)")
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
                    put("currency", row["currency"].orEmpty().ifBlank { "PLN" }.uppercase())
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
    fun importPcapdroidCapture(sourceName: String, analysis: PcapdroidCaptureAnalysis): PcapdroidCaptureSummary {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val importedAt = System.currentTimeMillis()
            val captureValues = ContentValues().apply {
                put("source_name", sourceName)
                put("provider", analysis.provider)
                put("imported_at", importedAt)
                put("call_count", analysis.calls.size)
                put("normalized_count", analysis.normalizedRows.size)
                put("inserted_count", 0)
            }
            val captureId = db.insertOrThrow("pcapdroid_captures", null, captureValues)

            analysis.calls.forEach { call ->
                val values = ContentValues().apply {
                    put("capture_id", captureId)
                    put("sequence", call.index)
                    put("host", call.host)
                    put("method", call.method)
                    put("path", call.path)
                    if (call.status == null) putNull("status") else put("status", call.status)
                    put("started_at", call.startedAt)
                    put("request_mime", call.requestMimeType)
                    put("response_mime", call.responseMimeType)
                    put("classification", call.classification)
                    put("request_kind", call.requestKind)
                    put("response_kind", call.responseKind)
                    put("request_bytes", call.requestBytes)
                    put("response_bytes", call.responseBytes)
                    put("request_preview", call.requestPreview.take(3_500))
                    put("response_preview", call.responsePreview.take(3_500))
                }
                db.insertOrThrow("pcapdroid_calls", null, values)
            }

            var inserted = 0
            val rows = analysis.normalizedRows
            if (rows.isNotEmpty()) {
                val importValues = ContentValues().apply {
                    put("source_name", sourceName)
                    put("imported_at", importedAt)
                    put("row_count", rows.size)
                    put("inserted_count", 0)
                }
                val importId = db.insertOrThrow("imports", null, importValues)
                rows.forEachIndexed { rowIndex, row ->
                    val platform = row["platform"].orEmpty().trim().ifBlank { analysis.provider }
                    val type = row["record_type"].orEmpty().trim().lowercase().ifBlank { "unknown" }
                    val externalId = row["external_id"].orEmpty()
                    val occurredAt = row["occurred_at"].orEmpty()
                    val raw = row.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
                    val fingerprint = sha256(listOf(
                        platform.lowercase(), type, externalId, occurredAt,
                        row["pickup"].orEmpty(), row["dropoff"].orEmpty(), row["distance_km"].orEmpty(),
                        row["gross"].orEmpty(), row["fees"].orEmpty(), row["bonus"].orEmpty(),
                        row["currency"].orEmpty(), row["status"].orEmpty()
                    ).joinToString("|"))
                    val evidence = ContentValues().apply {
                        put("import_id", importId)
                        put("row_number", rowIndex + 1)
                        put("fingerprint", fingerprint)
                        put("raw", raw)
                        put("source_name", sourceName)
                        put("capture_id", captureId)
                    }
                    db.insertOrThrow("raw_evidence", null, evidence)

                    val record = ContentValues().apply {
                        put("fingerprint", fingerprint)
                        put("import_id", importId)
                        put("platform", platform)
                        put("record_type", type)
                        put("external_id", externalId)
                        put("occurred_at", row["occurred_at"])
                        put("pickup", row["pickup"])
                        put("dropoff", row["dropoff"])
                        put("distance_km", DriverCsv.number(row["distance_km"]))
                        put("gross", DriverCsv.number(row["gross"]))
                        put("fees", DriverCsv.number(row["fees"]))
                        put("bonus", DriverCsv.number(row["bonus"]))
                        put("currency", row["currency"].orEmpty().ifBlank { "UNKNOWN" }.uppercase())
                        put("status", row["status"])
                        put("raw", raw)
                        put("source_name", sourceName)
                        put("capture_id", captureId)
                    }
                    if (db.insertWithOnConflict("records", null, record, SQLiteDatabase.CONFLICT_IGNORE) != -1L) inserted++
                }
                db.execSQL("UPDATE imports SET inserted_count=? WHERE id=?", arrayOf(inserted, importId))
            }

            db.execSQL("UPDATE pcapdroid_captures SET inserted_count=? WHERE id=?", arrayOf(inserted, captureId))
            db.setTransactionSuccessful()
            return PcapdroidCaptureSummary(
                captureId, sourceName, analysis.provider, importedAt,
                analysis.calls.size, rows.size, inserted
            )
        } finally {
            db.endTransaction()
        }
    }

    fun latestPcapdroidCapture(): PcapdroidCaptureSummary? {
        readableDatabase.rawQuery(
            "SELECT id,source_name,provider,imported_at,call_count,normalized_count,inserted_count FROM pcapdroid_captures ORDER BY id DESC LIMIT 1",
            null
        ).use { c ->
            if (!c.moveToFirst()) return null
            return PcapdroidCaptureSummary(
                c.getLong(0), c.getString(1), c.getString(2), c.getLong(3),
                c.getInt(4), c.getInt(5), c.getInt(6)
            )
        }
    }

    fun pcapdroidCalls(captureId: Long, limit: Int = 30): List<PcapdroidStoredCall> {
        val calls = mutableListOf<PcapdroidStoredCall>()
        readableDatabase.rawQuery(
            """SELECT sequence,host,method,path,status,started_at,request_mime,response_mime,
                      classification,request_kind,response_kind,request_bytes,response_bytes,
                      request_preview,response_preview
               FROM pcapdroid_calls WHERE capture_id=? ORDER BY sequence LIMIT ?""".trimIndent(),
            arrayOf(captureId.toString(), limit.coerceIn(1, 200).toString())
        ).use { c ->
            while (c.moveToNext()) calls.add(PcapdroidStoredCall(
                c.getInt(0), c.getString(1), c.getString(2), c.getString(3),
                if (c.isNull(4)) null else c.getInt(4), c.getString(5),
                c.getString(6), c.getString(7), c.getString(8), c.getString(9),
                c.getString(10), c.getInt(11), c.getInt(12), c.getString(13), c.getString(14)
            ))
        }
        return calls
    }

    fun summary(): MirrorSummary {
        val db = readableDatabase
        val realisedTripFilter = "lower(record_type) IN ('trip','ride','completed_trip')"
        fun scalar(sql: String): Double {
            db.rawQuery(sql, null).use { c -> return if (c.moveToFirst()) c.getDouble(0) else 0.0 }
        }
        fun nullableSum(column: String): Double? {
            db.rawQuery(
                "SELECT SUM($column) FROM records WHERE currency='PLN' AND $column IS NOT NULL AND $realisedTripFilter",
                null
            ).use { c ->
                return if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0) else null
            }
        }
        val records = scalar("SELECT COUNT(*) FROM records").toInt()
        val trips = scalar("SELECT COUNT(*) FROM records WHERE $realisedTripFilter").toInt()
        val offers = scalar("SELECT COUNT(*) FROM records WHERE lower(record_type)='offer'").toInt()
        // Offers are estimates, not realised income. Only completed trip records feed earnings totals.
        val gross = nullableSum("gross")
        val fees = nullableSum("fees")
        val bonuses = nullableSum("bonus")
        val imports = scalar("SELECT COUNT(*) FROM imports").toInt()
        val nonPln = scalar("SELECT COUNT(*) FROM records WHERE currency<>'PLN'").toInt()
        val grossRows = scalar("SELECT COUNT(*) FROM records WHERE currency='PLN' AND gross IS NOT NULL AND $realisedTripFilter")
        val incompleteNetRows = scalar("SELECT COUNT(*) FROM records WHERE currency='PLN' AND gross IS NOT NULL AND (fees IS NULL OR bonus IS NULL) AND $realisedTripFilter")
        val netComplete = grossRows > 0 && incompleteNetRows == 0.0
        val platforms = mutableListOf<String>()
        db.rawQuery("SELECT DISTINCT platform FROM records ORDER BY platform", null).use { c ->
            while (c.moveToNext()) platforms.add(c.getString(0))
        }
        return MirrorSummary(records, trips, offers, gross, fees, bonuses, platforms, imports, nonPln, netComplete)
    }

    fun recent(limit: Int = 30): List<DriverRecord> {
        val records = mutableListOf<DriverRecord>()
        readableDatabase.rawQuery(
            "SELECT id,platform,record_type,external_id,occurred_at,pickup,dropoff,distance_km,gross,fees,bonus,currency,status,raw,source_name FROM records ORDER BY COALESCE(occurred_at,'') DESC,id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) records.add(DriverRecord(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
                c.getString(5), c.getString(6),
                if (c.isNull(7)) null else c.getDouble(7),
                if (c.isNull(8)) null else c.getDouble(8),
                if (c.isNull(9)) null else c.getDouble(9),
                if (c.isNull(10)) null else c.getDouble(10),
                c.getString(11), c.getString(12), c.getString(13), c.getString(14)
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
