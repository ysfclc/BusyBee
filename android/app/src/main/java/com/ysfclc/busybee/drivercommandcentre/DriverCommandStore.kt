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
    val done: Boolean
)

data class CapturedNotice(val id: Long, val platform: String, val title: String, val body: String, val capturedAt: Long)
data class CommunityDocument(val id: Long, val title: String, val content: String, val importedAt: Long)
data class RoadReport(val id: Long, val category: String, val description: String, val latitude: Double, val longitude: Double, val createdAt: Long)

class DriverCommandStore(context: Context) : SQLiteOpenHelper(context, "busybee_command_centre.db", null, 1) {
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
            due_at INTEGER, amount REAL, uri TEXT, done INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE community_docs(
            id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, content TEXT NOT NULL, imported_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE road_reports(
            id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL, description TEXT NOT NULL,
            latitude REAL NOT NULL, longitude REAL NOT NULL, created_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

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

    @Synchronized fun captureNotice(platform:String,title:String,body:String,at:Long=System.currentTimeMillis()) {
        val fingerprint=java.security.MessageDigest.getInstance("SHA-256").digest("$platform|$title|$body".toByteArray()).joinToString(""){"%02x".format(it)}
        val v=ContentValues().apply { put("fingerprint",fingerprint);put("platform",platform);put("title",title);put("body",body);put("captured_at",at) }
        writableDatabase.insertWithOnConflict("notices",null,v,SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun notices(limit:Int=30):List<CapturedNotice> {
        val out=mutableListOf<CapturedNotice>()
        readableDatabase.rawQuery("SELECT id,platform,title,body,captured_at FROM notices ORDER BY captured_at DESC LIMIT ?",arrayOf(limit.toString())).use { c ->
            while(c.moveToNext()) out.add(CapturedNotice(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4)))
        };return out
    }

    fun addItem(kind:String,title:String,details:String,dueAt:Long?,amount:Double?,uri:String?=null):Long {
        val v=ContentValues().apply { put("kind",kind);put("title",title);put("details",details);if(dueAt==null)putNull("due_at")else put("due_at",dueAt);if(amount==null)putNull("amount")else put("amount",amount);put("uri",uri);put("done",0) }
        return writableDatabase.insertOrThrow("driver_items",null,v)
    }
    fun items(kind:String?=null):List<DriverItem> {
        val out=mutableListOf<DriverItem>()
        val sql=if(kind==null)"SELECT id,kind,title,details,due_at,amount,uri,done FROM driver_items ORDER BY done,due_at IS NULL,due_at" else "SELECT id,kind,title,details,due_at,amount,uri,done FROM driver_items WHERE kind=? ORDER BY done,due_at IS NULL,due_at"
        readableDatabase.rawQuery(sql,if(kind==null)null else arrayOf(kind)).use { c ->
            while(c.moveToNext()) out.add(DriverItem(c.getLong(0),c.getString(1),c.getString(2),c.getString(3)?: "",if(c.isNull(4))null else c.getLong(4),if(c.isNull(5))null else c.getDouble(5),c.getString(6),c.getInt(7)==1))
        };return out
    }
    fun completeItem(id:Long,done:Boolean=true) { writableDatabase.execSQL("UPDATE driver_items SET done=? WHERE id=?",arrayOf(if(done)1 else 0,id)) }

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
}
