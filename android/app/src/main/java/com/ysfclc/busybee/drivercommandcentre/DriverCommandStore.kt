package com.ysfclc.busybee.drivercommandcentre

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class DriverOffer(
    val id: Long = 0,
    val platform: String,
    val pickup: String = "",
    val destination: String = "",
    val fare: Double,
    val bonus: Double = 0.0,
    val pickupKm: Double,
    val tripKm: Double,
    val durationMin: Double,
    val passengerRating: Double? = null,
    val capturedAt: Long = System.currentTimeMillis(),
    val source: String = "manual",
    val raw: String = "",
    val status: String = "pending"
) {
    val totalKm: Double get() = pickupKm + tripKm
}

data class RankedOffer(val offer: DriverOffer, val estimatedNet: Double, val netPerHour: Double, val totalMinutes: Double)

object OfferEngine {
    fun rank(
        offers: List<DriverOffer>,
        costPerKm: Double,
        minimumNetPerHour: Double,
        maximumPickupKm: Double,
        minimumPassengerRating: Double
    ): List<RankedOffer> = offers.mapNotNull { offer ->
        if (offer.status != "pending" || offer.pickupKm > maximumPickupKm) return@mapNotNull null
        if (minimumPassengerRating > 0 && (offer.passengerRating ?: 0.0) < minimumPassengerRating) return@mapNotNull null
        val net = offer.fare + offer.bonus - offer.totalKm * costPerKm
        val pickupMinutes = offer.pickupKm / 25.0 * 60.0
        val totalMinutes = (offer.durationMin + pickupMinutes).coerceAtLeast(1.0)
        val hourly = net / (totalMinutes / 60.0)
        if (hourly < minimumNetPerHour) return@mapNotNull null
        RankedOffer(offer, net, hourly, totalMinutes)
    }.sortedWith(compareByDescending<RankedOffer> { it.netPerHour }.thenByDescending { it.estimatedNet })
}

data class DriverItem(
    val id: Long,
    val kind: String,
    val title: String,
    val details: String,
    val dueAt: Long?,
    val amount: Double?,
    val uri: String?,
    val done: Boolean,
    val recurrence: String?,
    val completedAt: Long?
)

data class CapturedNotice(val id: Long, val platform: String, val title: String, val body: String, val capturedAt: Long)
data class CommunityDocument(val id: Long, val title: String, val content: String, val importedAt: Long)
data class WhatsAppGroupWatch(
    val id: Long,
    val packageName: String,
    val title: String,
    val trackingEnabled: Boolean,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val capturedMessages: Int
)
data class WhatsAppGroupMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val messageAt: Long
)
data class RoadReport(val id: Long, val category: String, val description: String, val latitude: Double, val longitude: Double, val createdAt: Long)

/**
 * Notification identity includes Android's post timestamp. Identical text can represent
 * two separate ride offers; hashing only the text silently discarded every later offer.
 * A repost/update of the same Android notification retains its postTime and stays deduped.
 */
internal object NotificationFingerprint {
    fun create(platform: String, title: String, body: String, postedAt: Long): String {
        val identity = listOf(platform, title, body, postedAt.toString()).joinToString("\u0000")
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

class DriverCommandStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "busybee_command_centre.db", null, 3) {
    private val appContext = context.applicationContext
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE offers(
            id INTEGER PRIMARY KEY AUTOINCREMENT, platform TEXT NOT NULL, pickup TEXT, destination TEXT,
            fare REAL NOT NULL, bonus REAL NOT NULL, pickup_km REAL NOT NULL, trip_km REAL NOT NULL,
            duration_min REAL NOT NULL, passenger_rating REAL, captured_at INTEGER NOT NULL,
            source TEXT NOT NULL, raw TEXT, status TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX offers_status_time ON offers(status,captured_at DESC)")
        db.execSQL("""CREATE TABLE notices(
            id INTEGER PRIMARY KEY AUTOINCREMENT, fingerprint TEXT NOT NULL UNIQUE, platform TEXT NOT NULL,
            title TEXT NOT NULL, body TEXT NOT NULL, captured_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE driver_items(
            id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, title TEXT NOT NULL, details TEXT,
            due_at INTEGER, amount REAL, uri TEXT, done INTEGER NOT NULL DEFAULT 0,
            recurrence TEXT, completed_at INTEGER)""")
        db.execSQL("""CREATE TABLE community_docs(
            id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, content TEXT NOT NULL, imported_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE road_reports(
            id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL, description TEXT NOT NULL,
            latitude REAL NOT NULL, longitude REAL NOT NULL, created_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)""")
        createWhatsAppWatchTables(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE driver_items ADD COLUMN recurrence TEXT")
            db.execSQL("ALTER TABLE driver_items ADD COLUMN completed_at INTEGER")
        }
        if (oldVersion < 3) createWhatsAppWatchTables(db)
    }

    /**
     * A newer experimental build may have opened this same production database and
     * advanced SQLite's user_version beyond 3. Older app code must not crash or erase
     * driver data just because it is being installed over that build.
     *
     * Keep all unknown tables/columns intact and make only the current app's schema
     * guarantees idempotently. SQLiteOpenHelper updates user_version after this returns.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        CrashDiagnostics.record(appContext, "database:downgrade:$oldVersion->$newVersion")
        ensureCurrentSchema(db)
    }

    private fun ensureCurrentSchema(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS offers(
            id INTEGER PRIMARY KEY AUTOINCREMENT, platform TEXT NOT NULL, pickup TEXT, destination TEXT,
            fare REAL NOT NULL, bonus REAL NOT NULL, pickup_km REAL NOT NULL, trip_km REAL NOT NULL,
            duration_min REAL NOT NULL, passenger_rating REAL, captured_at INTEGER NOT NULL,
            source TEXT NOT NULL, raw TEXT, status TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS offers_status_time ON offers(status,captured_at DESC)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS notices(
            id INTEGER PRIMARY KEY AUTOINCREMENT, fingerprint TEXT NOT NULL UNIQUE, platform TEXT NOT NULL,
            title TEXT NOT NULL, body TEXT NOT NULL, captured_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS driver_items(
            id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, title TEXT NOT NULL, details TEXT,
            due_at INTEGER, amount REAL, uri TEXT, done INTEGER NOT NULL DEFAULT 0,
            recurrence TEXT, completed_at INTEGER)""")
        ensureColumn(db, "driver_items", "recurrence", "TEXT")
        ensureColumn(db, "driver_items", "completed_at", "INTEGER")
        db.execSQL("""CREATE TABLE IF NOT EXISTS community_docs(
            id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, content TEXT NOT NULL, imported_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS road_reports(
            id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL, description TEXT NOT NULL,
            latitude REAL NOT NULL, longitude REAL NOT NULL, created_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)""")
        createWhatsAppWatchTables(db)
    }

    private fun ensureColumn(db: SQLiteDatabase, table: String, column: String, definition: String) {
        var exists = false
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) {
                    exists = true
                    break
                }
            }
        }
        if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }

    private fun createWhatsAppWatchTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS whatsapp_groups(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            package_name TEXT NOT NULL,
            group_title TEXT NOT NULL,
            tracking_enabled INTEGER NOT NULL DEFAULT 0,
            first_seen_at INTEGER NOT NULL,
            last_seen_at INTEGER NOT NULL,
            UNIQUE(package_name, group_title)
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS whatsapp_group_messages(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            group_id INTEGER NOT NULL,
            fingerprint TEXT NOT NULL UNIQUE,
            sender TEXT NOT NULL,
            body TEXT NOT NULL,
            message_at INTEGER NOT NULL,
            FOREIGN KEY(group_id) REFERENCES whatsapp_groups(id) ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS whatsapp_messages_group_time ON whatsapp_group_messages(group_id,message_at DESC)")
    }

    @Synchronized fun saveOffer(o: DriverOffer): Long {
        val v = ContentValues().apply {
            put("platform",o.platform); put("pickup",o.pickup); put("destination",o.destination)
            put("fare",o.fare); put("bonus",o.bonus); put("pickup_km",o.pickupKm); put("trip_km",o.tripKm)
            put("duration_min",o.durationMin); if(o.passengerRating==null) putNull("passenger_rating") else put("passenger_rating",o.passengerRating)
            put("captured_at",o.capturedAt); put("source",o.source); put("raw",o.raw); put("status",o.status)
        }
        return writableDatabase.insertOrThrow("offers",null,v)
    }
    fun offers(limit:Int=100):List<DriverOffer> {
        val out=mutableListOf<DriverOffer>()
        readableDatabase.rawQuery("SELECT id,platform,pickup,destination,fare,bonus,pickup_km,trip_km,duration_min,passenger_rating,captured_at,source,raw,status FROM offers ORDER BY captured_at DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            while(c.moveToNext()) out.add(DriverOffer(c.getLong(0),c.getString(1),c.getString(2)?: "",c.getString(3)?: "",c.getDouble(4),c.getDouble(5),c.getDouble(6),c.getDouble(7),c.getDouble(8),if(c.isNull(9)) null else c.getDouble(9),c.getLong(10),c.getString(11),c.getString(12)?: "",c.getString(13)))
        }
        return out
    }
    fun updateOfferStatus(id:Long,status:String) { writableDatabase.execSQL("UPDATE offers SET status=? WHERE id=?", arrayOf(status,id)) }

    @Synchronized fun captureNotice(platform:String,title:String,body:String,at:Long=System.currentTimeMillis()):Boolean {
        val fingerprint=NotificationFingerprint.create(platform,title,body,at)
        val v=ContentValues().apply { put("fingerprint",fingerprint);put("platform",platform);put("title",title);put("body",body);put("captured_at",at) }
        return writableDatabase.insertWithOnConflict("notices",null,v,SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }
    fun notices(limit:Int=30):List<CapturedNotice> {
        val out=mutableListOf<CapturedNotice>()
        readableDatabase.rawQuery("SELECT id,platform,title,body,captured_at FROM notices ORDER BY captured_at DESC LIMIT ?",arrayOf(limit.toString())).use { c ->
            while(c.moveToNext()) out.add(CapturedNotice(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4)))
        };return out
    }

    fun addItem(kind:String,title:String,details:String,dueAt:Long?,amount:Double?,uri:String?=null,recurrence:String?=null):Long {
        val v=ContentValues().apply { put("kind",kind);put("title",title);put("details",details);if(dueAt==null)putNull("due_at")else put("due_at",dueAt);if(amount==null)putNull("amount")else put("amount",amount);put("uri",uri);put("done",0);put("recurrence",recurrence) }
        return writableDatabase.insertOrThrow("driver_items",null,v)
    }
    fun items(kind:String?=null):List<DriverItem> {
        val out=mutableListOf<DriverItem>()
        val sql=if(kind==null)"SELECT id,kind,title,details,due_at,amount,uri,done,recurrence,completed_at FROM driver_items ORDER BY done,due_at IS NULL,due_at" else "SELECT id,kind,title,details,due_at,amount,uri,done,recurrence,completed_at FROM driver_items WHERE kind=? ORDER BY done,due_at IS NULL,due_at"
        readableDatabase.rawQuery(sql,if(kind==null)null else arrayOf(kind)).use { c ->
            while(c.moveToNext()) out.add(DriverItem(c.getLong(0),c.getString(1),c.getString(2),c.getString(3)?: "",if(c.isNull(4))null else c.getLong(4),if(c.isNull(5))null else c.getDouble(5),c.getString(6),c.getInt(7)==1,c.getString(8),if(c.isNull(9))null else c.getLong(9)))
        };return out
    }
    @Synchronized fun completeItem(id:Long,done:Boolean=true) {
        if (!done) {
            writableDatabase.execSQL("UPDATE driver_items SET done=0,completed_at=NULL WHERE id=?", arrayOf(id))
            return
        }
        var next: Array<Any?>? = null
        readableDatabase.rawQuery("SELECT kind,title,details,due_at,amount,recurrence,done FROM driver_items WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst() && c.getInt(6)==0 && !c.isNull(3) && !c.isNull(5)) {
                next = arrayOf(c.getString(0),c.getString(1),c.getString(2) ?: "",c.getLong(3),if(c.isNull(4))null else c.getDouble(4),c.getString(5))
            }
        }
        writableDatabase.execSQL("UPDATE driver_items SET done=1,completed_at=? WHERE id=?",arrayOf(System.currentTimeMillis(),id))
        next?.let { values ->
            val due = Recurrence.nextDue(values[3] as Long, values[5] as String)
            if (due != null) addItem(values[0] as String,values[1] as String,values[2] as String,due,values[4] as Double?,recurrence=values[5] as String)
        }
    }

    fun totalAmount(kind:String,completedOnly:Boolean=false):Double {
        val sql=if(completedOnly)"SELECT COALESCE(SUM(amount),0) FROM driver_items WHERE kind=? AND done=1" else "SELECT COALESCE(SUM(amount),0) FROM driver_items WHERE kind=?"
        readableDatabase.rawQuery(sql,arrayOf(kind)).use { c -> return if(c.moveToFirst()) c.getDouble(0) else 0.0 }
    }

    /**
     * Discovers a WhatsApp group name from Android's visible notification metadata.
     * Message bodies are intentionally not written here; they are stored only when
     * the driver has explicitly enabled tracking for this exact group.
     */
    @Synchronized
    fun observeWhatsAppGroup(packageName: String, groupTitle: String, at: Long = System.currentTimeMillis()): Long? {
        val pkg = packageName.lowercase()
        if (pkg !in setOf("com.whatsapp", "com.whatsapp.w4b")) return null
        val title = groupTitle.trim().take(160)
        if (title.isBlank()) return null
        val db = writableDatabase
        db.execSQL(
            "UPDATE whatsapp_groups SET last_seen_at=? WHERE package_name=? AND group_title=?",
            arrayOf(at, pkg, title)
        )
        db.execSQL(
            """INSERT OR IGNORE INTO whatsapp_groups(package_name,group_title,tracking_enabled,first_seen_at,last_seen_at)
               VALUES(?,?,0,?,?)""".trimIndent(),
            arrayOf(pkg, title, at, at)
        )
        db.rawQuery(
            "SELECT id FROM whatsapp_groups WHERE package_name=? AND group_title=?",
            arrayOf(pkg, title)
        ).use { cursor -> return if (cursor.moveToFirst()) cursor.getLong(0) else null }
    }

    fun whatsappGroups(): List<WhatsAppGroupWatch> {
        val result = mutableListOf<WhatsAppGroupWatch>()
        readableDatabase.rawQuery(
            """SELECT g.id,g.package_name,g.group_title,g.tracking_enabled,g.first_seen_at,g.last_seen_at,
                      (SELECT COUNT(*) FROM whatsapp_group_messages m WHERE m.group_id=g.id)
               FROM whatsapp_groups g ORDER BY g.last_seen_at DESC,g.group_title COLLATE NOCASE""".trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(
                WhatsAppGroupWatch(
                    cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                    cursor.getInt(3) == 1, cursor.getLong(4), cursor.getLong(5), cursor.getInt(6)
                )
            )
        }
        return result
    }

    @Synchronized
    fun setWhatsAppGroupTracking(groupId: Long, enabled: Boolean) {
        writableDatabase.execSQL(
            "UPDATE whatsapp_groups SET tracking_enabled=? WHERE id=?",
            arrayOf(if (enabled) 1 else 0, groupId)
        )
    }

    fun isWhatsAppGroupTrackingEnabled(groupId: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT tracking_enabled FROM whatsapp_groups WHERE id=?",
            arrayOf(groupId.toString())
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 1 }

    @Synchronized
    fun captureTrackedWhatsAppMessage(
        groupId: Long,
        sender: String,
        body: String,
        messageAt: Long
    ): Boolean {
        val cleanBody = body.trim().take(4000)
        if (cleanBody.isBlank() || !isWhatsAppGroupTrackingEnabled(groupId)) return false
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$groupId\\u0000$sender\\u0000$cleanBody\\u0000$messageAt".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val values = ContentValues().apply {
            put("group_id", groupId)
            put("fingerprint", fingerprint)
            put("sender", sender.trim().take(120))
            put("body", cleanBody)
            put("message_at", messageAt)
        }
        return writableDatabase.insertWithOnConflict(
            "whatsapp_group_messages", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun whatsappGroupMessages(groupId: Long, limit: Int = 80): List<WhatsAppGroupMessage> {
        if (!isWhatsAppGroupTrackingEnabled(groupId)) return emptyList()
        val result = mutableListOf<WhatsAppGroupMessage>()
        readableDatabase.rawQuery(
            """SELECT id,sender,body,message_at FROM whatsapp_group_messages
               WHERE group_id=? ORDER BY message_at DESC,id DESC LIMIT ?""".trimIndent(),
            arrayOf(groupId.toString(), limit.coerceIn(1, 200).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(
                WhatsAppGroupMessage(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3))
            )
        }
        return result
    }

    @Synchronized
    fun deleteWhatsAppGroup(groupId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("whatsapp_group_messages", "group_id=?", arrayOf(groupId.toString()))
            db.delete("whatsapp_groups", "id=?", arrayOf(groupId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addCommunityDocument(title:String,content:String) {
        val v=ContentValues().apply { put("title",title);put("content",content);put("imported_at",System.currentTimeMillis()) }
        writableDatabase.insertOrThrow("community_docs",null,v)
    }
    fun communityDocuments():List<CommunityDocument> {
        val out=mutableListOf<CommunityDocument>()
        readableDatabase.rawQuery("SELECT id,title,content,imported_at FROM community_docs ORDER BY imported_at DESC",null).use { c ->
            while(c.moveToNext()) out.add(CommunityDocument(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3)))
        };return out
    }
    fun communitySearch(query:String):List<String> {
        if(query.isBlank()) return emptyList()
        val out=mutableListOf<String>()
        communityDocuments().forEach { doc ->
            doc.content.lineSequence().filter { it.contains(query,true) }.take(8).forEach { out.add("${doc.title}: ${it.take(260)}") }
        };return out.take(30)
    }

    fun addRoadReport(category:String,description:String,lat:Double,lon:Double):Long {
        val v=ContentValues().apply { put("category",category);put("description",description);put("latitude",lat);put("longitude",lon);put("created_at",System.currentTimeMillis()) }
        return writableDatabase.insertOrThrow("road_reports",null,v)
    }
    fun roadReports():List<RoadReport> {
        val out=mutableListOf<RoadReport>()
        readableDatabase.rawQuery("SELECT id,category,description,latitude,longitude,created_at FROM road_reports ORDER BY created_at DESC LIMIT 100",null).use { c ->
            while(c.moveToNext()) out.add(RoadReport(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getDouble(4),c.getLong(5)))
        };return out
    }
    fun setting(key:String,default:String):String = readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?",arrayOf(key)).use { c -> if(c.moveToFirst()) c.getString(0) else default }
    fun setSetting(key:String,value:String) { val v=ContentValues().apply { put("key",key);put("value",value) };writableDatabase.insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE) }

    fun vehicleProfile(): VehicleProfile = VehicleProfile(
        name = setting("vehicle_name", ""),
        category = setting("vehicle_category", "Passenger car"),
        fuelType = setting("vehicle_fuel_type", "Petrol"),
        consumptionPer100Km = setting("vehicle_consumption_per_100km", "").toDoubleOrNull(),
        energyPricePln = setting("vehicle_energy_price_pln", "").toDoubleOrNull(),
        maintenanceCostPerKm = setting("vehicle_maintenance_per_km", "0").toDoubleOrNull() ?: 0.0
    )

    @Synchronized fun saveVehicleProfile(profile: VehicleProfile) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            setSetting("vehicle_name", profile.name.trim())
            setSetting("vehicle_category", profile.category)
            setSetting("vehicle_fuel_type", profile.fuelType)
            setSetting("vehicle_consumption_per_100km", profile.consumptionPer100Km?.toString().orEmpty())
            setSetting("vehicle_energy_price_pln", profile.energyPricePln?.toString().orEmpty())
            setSetting("vehicle_maintenance_per_km", profile.maintenanceCostPerKm.toString())
            setSetting("cost_per_km", profile.totalCostPerKm?.let { String.format(java.util.Locale.US, "%.4f", it) } ?: "0.85")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}


object Recurrence {
    fun nextDue(dueAt:Long,cadence:String):Long? {
        val calendar=java.util.Calendar.getInstance().apply { timeInMillis=dueAt }
        when(cadence.lowercase()) {
            "weekly" -> calendar.add(java.util.Calendar.WEEK_OF_YEAR,1)
            "monthly" -> calendar.add(java.util.Calendar.MONTH,1)
            "yearly" -> calendar.add(java.util.Calendar.YEAR,1)
            else -> return null
        }
        return calendar.timeInMillis
    }
}
