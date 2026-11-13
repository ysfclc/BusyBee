package com.ysfclc.busybee.drivercommandcentre

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.util.Locale

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
    val sourceName: String,
    val durationMinutes: Double? = null
)

data class DriverFinancialRow(
    val recordType: String,
    val currency: String,
    val gross: Double?,
    val fees: Double?,
    val bonus: Double?
)

data class DriverFinancialTotals(
    val gross: Double?,
    val fees: Double?,
    val bonuses: Double?,
    val netComplete: Boolean
) {
    val knownNet: Double?
        get() = if (netComplete && gross != null && fees != null && bonuses != null) {
            gross - fees + bonuses
        } else null
}

object DriverFinancialSummaryCalculator {
    private val earningTripTypes = setOf("trip", "ride", "completed_trip")

    fun calculate(records: Iterable<DriverFinancialRow>): DriverFinancialTotals {
        val trips = records.filter {
            it.recordType.trim().lowercase(java.util.Locale.ROOT) in earningTripTypes &&
                it.currency.equals("PLN", ignoreCase = true)
        }

        fun total(value: (DriverFinancialRow) -> Double?): Double? =
            trips.mapNotNull(value).takeIf { it.isNotEmpty() }?.sum()

        val allTripsComplete = trips.isNotEmpty() &&
            trips.all { it.gross != null && it.fees != null && it.bonus != null }

        return DriverFinancialTotals(
            gross = total { it.gross },
            fees = total { it.fees },
            bonuses = total { it.bonus },
            netComplete = allTripsComplete
        )
    }
}

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
        "distance_km", "duration_minutes", "gross", "fees", "bonus", "currency", "status"
    )

    fun parse(text: String): List<Map<String, String>> {
        val rows = csvRows(text)
        if (rows.isEmpty()) return emptyList()
        val headers = rows.first().mapIndexed { index, value ->
            (if (index == 0) value.removePrefix("\uFEFF") else value).trim().lowercase(Locale.ROOT).replace(" ", "_")
        }
        require(headers.none { it.isBlank() }) { "CSV contains an empty column header" }
        require(headers.size == headers.toSet().size) { "CSV contains duplicate column headers" }
        require(headers.toSet().containsAll(requiredHeaders)) {
            "CSV needs platform and record_type columns"
        }
        require(headers.all { it in supportedHeaders }) {
            "Unsupported column. Supported: ${supportedHeaders.joinToString()}"
        }
        return rows.drop(1).mapNotNull { cells ->
            if (cells.all { it.isBlank() }) return@mapNotNull null
            require(cells.size <= headers.size) {
                "CSV row has ${cells.size} values but the header defines ${headers.size} columns"
            }
            val row = headers.mapIndexed { index, key ->
                key to cells.getOrElse(index) { "" }.trim()
            }.toMap()
            require(row["platform"].orEmpty().isNotBlank() && row["record_type"].orEmpty().isNotBlank()) {
                "Every CSV data row must include platform and record_type"
            }
            row
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
        if (oldVersion < 2) db.execSQL("ALTER TABLE records ADD COLUMN duration_minutes REAL")
        // Never destroy user evidence on upgrade.
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
                val type = row["record_type"].orEmpty().trim().lowercase(Locale.ROOT).ifBlank { "unknown" }
                val raw = row.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
                val fingerprint = sha256(listOf(platform.lowercase(Locale.ROOT), type, row["external_id"].orEmpty(), row["occurred_at"].orEmpty(), raw).joinToString("|"))
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
                    put("duration_minutes", DriverCsv.number(row["duration_minutes"]))
                    put("gross", DriverCsv.number(row["gross"]))
                    put("fees", DriverCsv.number(row["fees"]))
                    put("bonus", DriverCsv.number(row["bonus"]))
                    put("currency", row["currency"].orEmpty().ifBlank { "PLN" }.uppercase(Locale.ROOT))
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

    /**
     * Persists a screen observation only after the driver reviews and confirms it.
     * The raw OCR text is retained as evidence; unknown financial fields remain NULL.
     */
    @Synchronized
    fun saveReviewedNativeObservation(
        platform: String,
        recordType: String,
        capturedAt: Long,
        visibleText: String,
        gross: Double?,
        fees: Double?,
        bonus: Double?,
        distanceKm: Double?,
        durationMinutes: Double?,
        currency: String = "PLN",
        pickup: String? = null,
        dropoff: String? = null,
        sourceName: String = "Android screen capture"
    ): Long {
        val safePlatform = platform.trim().take(60)
        require(safePlatform.matches(Regex("[\\p{L}0-9 ._-]{2,60}"))) { "Enter a valid driver platform name" }
        val type = recordType.trim().lowercase(Locale.ROOT)
        require(type in setOf("offer", "trip", "ride", "completed_trip", "payout", "adjustment")) {
            "Choose a supported record type"
        }
        require(visibleText.isNotBlank()) { "Original screen evidence is missing" }
        listOf(gross, fees, bonus, distanceKm, durationMinutes).filterNotNull().forEach {
            require(it.isFinite() && it >= 0.0) { "Reviewed values must be valid non-negative numbers" }
        }
        val safeCurrency = currency.trim().uppercase(Locale.ROOT).ifBlank { "PLN" }
        require(safeCurrency.matches(Regex("[A-Z]{3}"))) { "Use a three-letter currency code" }
        val raw = visibleText.take(5000)
        val safePickup = pickup?.trim()?.take(180)?.takeIf { it.isNotBlank() }
        val safeDropoff = dropoff?.trim()?.take(180)?.takeIf { it.isNotBlank() }
        val safeSourceName = sourceName.trim().takeIf {
            it in setOf("Android live UI listener", "Android screen capture", "Android MediaProjection OCR")
        } ?: "Android screen capture"
        val occurredAt = java.time.Instant.ofEpochMilli(capturedAt).toString()
        val fingerprint = sha256(
            listOf(safePlatform, type, occurredAt, raw, gross, fees, bonus, distanceKm, safeCurrency, safePickup, safeDropoff, safeSourceName).joinToString("|")
        )
        val db = writableDatabase
        db.beginTransaction()
        try {
            val importedAt = System.currentTimeMillis()
            val importId = db.insertOrThrow("imports", null, ContentValues().apply {
                put("source_name", safeSourceName)
                put("imported_at", importedAt)
                put("row_count", 1)
                put("inserted_count", 0)
            })
            db.insertOrThrow("raw_evidence", null, ContentValues().apply {
                put("import_id", importId)
                put("row_number", 1)
                put("fingerprint", fingerprint)
                put("raw", raw)
                put("source_name", safeSourceName)
            })
            val recordId = db.insertOrThrow("records", null, ContentValues().apply {
                put("fingerprint", fingerprint)
                put("import_id", importId)
                put("platform", safePlatform)
                put("record_type", type)
                putNull("external_id")
                put("occurred_at", occurredAt)
                if (safePickup == null) putNull("pickup") else put("pickup", safePickup)
                if (safeDropoff == null) putNull("dropoff") else put("dropoff", safeDropoff)
                if (distanceKm == null) putNull("distance_km") else put("distance_km", distanceKm)
                if (durationMinutes == null) putNull("duration_minutes") else put("duration_minutes", durationMinutes)
                if (gross == null) putNull("gross") else put("gross", gross)
                if (fees == null) putNull("fees") else put("fees", fees)
                if (bonus == null) putNull("bonus") else put("bonus", bonus)
                put("currency", safeCurrency)
                put("status", "driver-reviewed")
                put("raw", raw)
                put("source_name", safeSourceName)
            })
            db.execSQL("UPDATE imports SET inserted_count=1 WHERE id=?", arrayOf(importId))
            db.setTransactionSuccessful()
            return recordId
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
        val financialRows = mutableListOf<DriverFinancialRow>()
        db.rawQuery("SELECT record_type,currency,gross,fees,bonus FROM records WHERE lower(record_type) IN ('trip','ride','completed_trip') AND upper(currency)='PLN'", null).use { c ->
            while (c.moveToNext()) {
                financialRows.add(
                    DriverFinancialRow(
                        recordType = c.getString(0),
                        currency = c.getString(1),
                        gross = if (c.isNull(2)) null else c.getDouble(2),
                        fees = if (c.isNull(3)) null else c.getDouble(3),
                        bonus = if (c.isNull(4)) null else c.getDouble(4)
                    )
                )
            }
        }
        // Earnings include recognised PLN trip rows only; offers and other activity are not revenue.
        val financials = DriverFinancialSummaryCalculator.calculate(financialRows)
        val imports = scalar("SELECT COUNT(*) FROM imports").toInt()
        val nonPln = scalar("SELECT COUNT(*) FROM records WHERE currency<>'PLN'").toInt()
        val platforms = mutableListOf<String>()
        db.rawQuery("SELECT DISTINCT platform FROM records ORDER BY platform", null).use { c ->
            while (c.moveToNext()) platforms.add(c.getString(0))
        }
        return MirrorSummary(
            records, trips, offers,
            financials.gross, financials.fees, financials.bonuses,
            platforms, imports, nonPln, financials.netComplete
        )
    }

    fun recent(limit: Int = 30): List<DriverRecord> {
        val records = mutableListOf<DriverRecord>()
        readableDatabase.rawQuery(
            "SELECT id,platform,record_type,external_id,occurred_at,pickup,dropoff,distance_km,duration_minutes,gross,fees,bonus,currency,status,raw,source_name FROM records ORDER BY COALESCE(occurred_at,'') DESC,id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) records.add(DriverRecord(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
                c.getString(5), c.getString(6),
                if (c.isNull(7)) null else c.getDouble(7),
                if (c.isNull(9)) null else c.getDouble(9),
                if (c.isNull(10)) null else c.getDouble(10),
                if (c.isNull(11)) null else c.getDouble(11),
                c.getString(12), c.getString(13), c.getString(14), c.getString(15),
                if (c.isNull(8)) null else c.getDouble(8)
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
