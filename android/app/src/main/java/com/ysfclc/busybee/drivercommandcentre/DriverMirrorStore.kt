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
    val gross: Double,
    val fees: Double,
    val bonuses: Double,
    val platforms: List<String>,
    val imports: Int,
    val nonPlnRecords: Int
) {
    val knownNet: Double get() = gross - fees + bonuses
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
        val headers = rows.first().map { it.trim().lowercase().replace(" ", "_") }
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
    SQLiteOpenHelper(context, "busybee_driver_mirror.db", null, 1) {

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versioned migrations are added here; never destroy user evidence on upgrade.
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

    fun summary(): MirrorSummary {
        val db = readableDatabase
        fun scalar(sql: String): Double {
            db.rawQuery(sql, null).use { c -> return if (c.moveToFirst()) c.getDouble(0) else 0.0 }
        }
        val records = scalar("SELECT COUNT(*) FROM records").toInt()
        val trips = scalar("SELECT COUNT(*) FROM records WHERE lower(record_type) IN ('trip','ride','completed_trip')").toInt()
        val offers = scalar("SELECT COUNT(*) FROM records WHERE lower(record_type)='offer'").toInt()
        // Never add unlike currencies together. Headline totals are PLN-only.
        val gross = scalar("SELECT COALESCE(SUM(gross),0) FROM records WHERE currency='PLN'")
        val fees = scalar("SELECT COALESCE(SUM(fees),0) FROM records WHERE currency='PLN'")
        val bonuses = scalar("SELECT COALESCE(SUM(bonus),0) FROM records WHERE currency='PLN'")
        val imports = scalar("SELECT COUNT(*) FROM imports").toInt()
        val nonPln = scalar("SELECT COUNT(*) FROM records WHERE currency<>'PLN'").toInt()
        val platforms = mutableListOf<String>()
        db.rawQuery("SELECT DISTINCT platform FROM records ORDER BY platform", null).use { c ->
            while (c.moveToNext()) platforms.add(c.getString(0))
        }
        return MirrorSummary(records, trips, offers, gross, fees, bonuses, platforms, imports, nonPln)
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

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
