package com.ysfclc.busybee.drivercommandcentre

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
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

data class PendingProviderOfferEvent(
    val outboxId: Long,
    val event: ProviderEventEnvelope
)
data class CommunityDocument(val id: Long, val title: String, val content: String, val importedAt: Long)
data class RoadReport(val id: Long, val category: String, val description: String, val latitude: Double, val longitude: Double, val createdAt: Long)

class DriverCommandStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "busybee_command_centre.db",
    null,
    DRIVER_COMMAND_SCHEMA_VERSION
) {
    companion object {
        private const val DRIVER_COMMAND_SCHEMA_VERSION = 3
    }
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
        createProviderEventOutbox(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE driver_items ADD COLUMN recurrence TEXT")
            db.execSQL("ALTER TABLE driver_items ADD COLUMN completed_at INTEGER")
        }
        if (oldVersion < 3) {
            createProviderEventOutbox(db)
        }
    }

    private fun createProviderEventOutbox(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS provider_event_outbox(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            idempotency_key TEXT NOT NULL UNIQUE,
            offer_id INTEGER NOT NULL,
            event_type TEXT NOT NULL,
            decision TEXT,
            occurred_at_ms INTEGER,
            created_at_ms INTEGER NOT NULL,
            delivered_at_ms INTEGER,
            attempt_count INTEGER NOT NULL DEFAULT 0,
            last_error TEXT,
            FOREIGN KEY(offer_id) REFERENCES offers(id)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS provider_event_outbox_pending_idx ON provider_event_outbox(delivered_at_ms,id)")
    }

    @Synchronized fun saveOffer(o: DriverOffer): Long {
        return writableDatabase.insertOrThrow("offers", null, offerValues(o))
    }

    /**
     * Saves the offer and its canonical-event outbox row in one SQLite transaction.
     * If the app process dies before the Provider Bridge write completes, the
     * pending event remains recoverable from this same database on next startup.
     */
    @Synchronized
    fun saveOfferWithPendingEvent(offer: DriverOffer): DriverOffer {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insertOrThrow("offers", null, offerValues(offer))
            val saved = offer.copy(id = id)
            enqueueProviderOfferEvent(db, id, ManualOfferEventFactory.observed(saved))
            db.setTransactionSuccessful()
            return saved
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Applies the driver's local decision and enqueues its event atomically with
     * the offer status update. This does not send a decision to the ride provider.
     */
    @Synchronized
    fun recordOfferDecision(
        offerId: Long,
        status: String,
        decision: String,
        decidedAtMillis: Long
    ): DriverOffer? {
        require(status.isNotBlank()) { "Offer status is required" }
        require(decision.isNotBlank()) { "Decision is required" }
        require(decidedAtMillis > 0L) { "Decision time must be positive" }

        val db = writableDatabase
        db.beginTransaction()
        try {
            val updated = db.update(
                "offers",
                ContentValues().apply { put("status", status) },
                "id=?",
                arrayOf(offerId.toString())
            )
            if (updated == 0) return null
            val saved = readOffer(db, offerId) ?: return null
            enqueueProviderOfferEvent(
                db,
                offerId,
                ManualOfferEventFactory.decisionObserved(saved, decision, decidedAtMillis)
            )
            db.setTransactionSuccessful()
            return saved
        } finally {
            db.endTransaction()
        }
    }

    /** Highest durable outbox row ID, including already delivered rows. */
    @Synchronized
    fun latestProviderOfferOutboxId(): Long =
        readableDatabase.rawQuery(
            "SELECT COALESCE(MAX(id),0) FROM provider_event_outbox",
            null
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }

    /**
     * Returns an ordered snapshot of pending events through the supplied row ID.
     * The upper bound lets the caller detect records enqueued while a drain is running.
     */
    @Synchronized
    fun pendingProviderOfferEvents(throughId: Long, limit: Int = 200): List<PendingProviderOfferEvent> {
        require(limit in 1..1000) { "limit must be between 1 and 1000" }
        if (throughId <= 0L) return emptyList()

        val events = mutableListOf<PendingProviderOfferEvent>()
        readableDatabase.rawQuery(
            """SELECT q.id,q.idempotency_key,q.event_type,q.decision,q.occurred_at_ms,
                      o.id,o.platform,o.pickup,o.destination,o.fare,o.bonus,o.pickup_km,
                      o.trip_km,o.duration_min,o.passenger_rating,o.captured_at,o.source,o.raw,o.status
               FROM provider_event_outbox q
               JOIN offers o ON o.id=q.offer_id
               WHERE q.delivered_at_ms IS NULL AND q.id<=?
               ORDER BY q.id ASC
               LIMIT ?""",
            arrayOf(throughId.toString(), limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val offer = readOffer(cursor, 5)
                    ?: throw IllegalStateException("Outbox row references a missing offer")
                val event = when (ProviderEventType.valueOf(cursor.getString(2))) {
                    ProviderEventType.OFFER_OBSERVED ->
                        ManualOfferEventFactory.observed(offer)
                    ProviderEventType.OFFER_DECISION_OBSERVED ->
                        ManualOfferEventFactory.decisionObserved(
                            offer,
                            cursor.getString(3) ?: throw IllegalStateException("Outbox decision is missing"),
                            if (cursor.isNull(4)) throw IllegalStateException("Outbox decision time is missing") else cursor.getLong(4)
                        )
                    else -> throw IllegalStateException("Unsupported offer outbox event type: ${cursor.getString(2)}")
                }
                val storedKey = cursor.getString(1)
                check(event.idempotencyKey == storedKey) {
                    "Reconstructed event idempotency key does not match its outbox row"
                }
                events.add(PendingProviderOfferEvent(cursor.getLong(0), event))
            }
        }
        return events
    }

    @Synchronized
    fun markProviderOfferEventDelivered(outboxId: Long, deliveredAtMillis: Long = System.currentTimeMillis()) {
        writableDatabase.execSQL(
            "UPDATE provider_event_outbox SET delivered_at_ms=?,last_error=NULL WHERE id=? AND delivered_at_ms IS NULL",
            arrayOf(deliveredAtMillis, outboxId)
        )
    }

    @Synchronized
    fun markProviderOfferEventFailed(outboxId: Long, error: String) {
        writableDatabase.execSQL(
            "UPDATE provider_event_outbox SET attempt_count=attempt_count+1,last_error=? WHERE id=? AND delivered_at_ms IS NULL",
            arrayOf(error.take(1000), outboxId)
        )
    }

    @Synchronized
    fun hasPendingProviderOfferEventsAfter(outboxId: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM provider_event_outbox WHERE delivered_at_ms IS NULL AND id>? LIMIT 1",
            arrayOf(outboxId.toString())
        ).use { it.moveToFirst() }

    @Synchronized
    fun pendingProviderOfferEventCount(): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM provider_event_outbox WHERE delivered_at_ms IS NULL",
            null
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun enqueueProviderOfferEvent(
        db: SQLiteDatabase,
        offerId: Long,
        event: ProviderEventEnvelope
    ) {
        require(offerId > 0L) { "Offer ID must be positive" }
        val payload = event.payload as? ProviderEventPayload.Offer
            ?: throw IllegalArgumentException("Offer outbox only accepts offer payloads")
        val values = ContentValues().apply {
            put("idempotency_key", event.idempotencyKey)
            put("offer_id", offerId)
            put("event_type", event.eventType.name)
            put("decision", payload.decision)
            if (event.occurredAtMillis == null) putNull("occurred_at_ms")
            else put("occurred_at_ms", event.occurredAtMillis)
            put("created_at_ms", System.currentTimeMillis())
        }
        val inserted = db.insertWithOnConflict(
            "provider_event_outbox",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        if (event.eventType == ProviderEventType.OFFER_OBSERVED && inserted == -1L) {
            throw IllegalStateException("Offer-observed outbox event already exists for a new offer")
        }
    }

    private fun offerValues(offer: DriverOffer) = ContentValues().apply {
        put("platform", offer.platform)
        put("pickup", offer.pickup)
        put("destination", offer.destination)
        put("fare", offer.fare)
        put("bonus", offer.bonus)
        put("pickup_km", offer.pickupKm)
        put("trip_km", offer.tripKm)
        put("duration_min", offer.durationMin)
        if (offer.passengerRating == null) putNull("passenger_rating") else put("passenger_rating", offer.passengerRating)
        put("captured_at", offer.capturedAt)
        put("source", offer.source)
        put("raw", offer.raw)
        put("status", offer.status)
    }

    private fun readOffer(db: SQLiteDatabase, offerId: Long): DriverOffer? =
        db.rawQuery(
            """SELECT id,platform,pickup,destination,fare,bonus,pickup_km,trip_km,
                      duration_min,passenger_rating,captured_at,source,raw,status
               FROM offers WHERE id=?""",
            arrayOf(offerId.toString())
        ).use { cursor -> if (cursor.moveToFirst()) readOffer(cursor) else null }

    private fun readOffer(cursor: Cursor, offset: Int = 0): DriverOffer =
        DriverOffer(
            id = cursor.getLong(offset),
            platform = cursor.getString(offset + 1),
            pickup = cursor.getString(offset + 2) ?: "",
            destination = cursor.getString(offset + 3) ?: "",
            fare = cursor.getDouble(offset + 4),
            bonus = cursor.getDouble(offset + 5),
            pickupKm = cursor.getDouble(offset + 6),
            tripKm = cursor.getDouble(offset + 7),
            durationMin = cursor.getDouble(offset + 8),
            passengerRating = if (cursor.isNull(offset + 9)) null else cursor.getDouble(offset + 9),
            capturedAt = cursor.getLong(offset + 10),
            source = cursor.getString(offset + 11),
            raw = cursor.getString(offset + 12) ?: "",
            status = cursor.getString(offset + 13)
        )

    fun offers(limit:Int=100):List<DriverOffer> {
        val out=mutableListOf<DriverOffer>()
        readableDatabase.rawQuery("SELECT id,platform,pickup,destination,fare,bonus,pickup_km,trip_km,duration_min,passenger_rating,captured_at,source,raw,status FROM offers ORDER BY captured_at DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            while(c.moveToNext()) out.add(DriverOffer(c.getLong(0),c.getString(1),c.getString(2)?: "",c.getString(3)?: "",c.getDouble(4),c.getDouble(5),c.getDouble(6),c.getDouble(7),c.getDouble(8),if(c.isNull(9)) null else c.getDouble(9),c.getLong(10),c.getString(11),c.getString(12)?: "",c.getString(13)))
        }
        return out
    }
    fun updateOfferStatus(id:Long,status:String) { writableDatabase.execSQL("UPDATE offers SET status=? WHERE id=?", arrayOf(status,id)) }

    @Synchronized fun captureNotice(platform:String,title:String,body:String,at:Long=System.currentTimeMillis()): Long {
        val fingerprint=java.security.MessageDigest.getInstance("SHA-256").digest("$platform|$title|$body".toByteArray()).joinToString(""){"%02x".format(it)}
        val v=ContentValues().apply { put("fingerprint",fingerprint);put("platform",platform);put("title",title);put("body",body);put("captured_at",at) }
        return writableDatabase.insertWithOnConflict("notices",null,v,SQLiteDatabase.CONFLICT_IGNORE)
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
