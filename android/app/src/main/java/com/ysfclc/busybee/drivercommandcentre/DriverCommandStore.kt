package com.ysfclc.busybee.drivercommandcentre

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

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
data class RoadReport(val id: Long, val category: String, val description: String, val latitude: Double, val longitude: Double, val createdAt: Long)

class DriverCommandStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "busybee_command_centre.db", null, 4) {
    private fun createObservationLedger(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS observation_ledger(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            observation_id TEXT NOT NULL,
            fingerprint TEXT NOT NULL,
            source_id TEXT NOT NULL,
            provider TEXT NOT NULL,
            observed_at INTEGER NOT NULL,
            received_at INTEGER NOT NULL,
            payload_json TEXT NOT NULL,
            UNIQUE(observation_id,fingerprint))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS observation_source_time ON observation_ledger(source_id,observed_at DESC)")
    }
    private fun createCanonicalEventLedger(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS canonical_event_ledger(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            event_id TEXT NOT NULL,
            fingerprint TEXT NOT NULL,
            lifecycle_id TEXT NOT NULL,
            event_type TEXT NOT NULL,
            status TEXT NOT NULL,
            occurred_at INTEGER,
            payload_json TEXT NOT NULL,
            UNIQUE(event_id,fingerprint))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS canonical_event_lifecycle ON canonical_event_ledger(lifecycle_id,occurred_at)")
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
        createObservationLedger(db)
        createCanonicalEventLedger(db)
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
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE driver_items ADD COLUMN recurrence TEXT")
            db.execSQL("ALTER TABLE driver_items ADD COLUMN completed_at INTEGER")
        }
        if (oldVersion < 3) createObservationLedger(db)
        if (oldVersion < 4) createCanonicalEventLedger(db)
    }

    @Synchronized fun saveOffer(o: DriverOffer): Long {
        val v = ContentValues().apply {
            put("platform",o.platform); put("pickup",o.pickup); put("destination",o.destination)
            put("fare",o.fare); put("bonus",o.bonus); put("pickup_km",o.pickupKm); put("trip_km",o.tripKm)
            put("duration_min",o.durationMin); if(o.passengerRating==null) putNull("passenger_rating") else put("passenger_rating",o.passengerRating)
            put("captured_at",o.capturedAt); put("source",o.source); put("raw",o.raw); put("status",o.status)
        }
        val id = writableDatabase.insertOrThrow("offers",null,v)
        val receivedAt = System.currentTimeMillis()
        val observedAt = o.capturedAt.coerceAtMost(receivedAt).coerceAtLeast(1L)
        val evidence = EvidenceReference("manual-offer:$id", "manual_offer_input")
        val facts = linkedMapOf(
            "offer_state" to ObservedFact("observed", listOf(evidence)),
            "platform" to ObservedFact(o.platform, listOf(evidence)),
            "pickup" to ObservedFact(o.pickup.takeIf { it.isNotBlank() }, listOf(evidence)),
            "destination" to ObservedFact(o.destination.takeIf { it.isNotBlank() }, listOf(evidence)),
            "fare" to ObservedFact(o.fare.toString(), listOf(evidence)),
            "bonus" to ObservedFact(o.bonus.toString(), listOf(evidence)),
            "pickup_km" to ObservedFact(o.pickupKm.toString(), listOf(evidence)),
            "trip_km" to ObservedFact(o.tripKm.toString(), listOf(evidence)),
            "duration_min" to ObservedFact(o.durationMin.toString(), listOf(evidence)),
            "passenger_rating" to ObservedFact(o.passengerRating?.toString(), listOf(evidence))
        )
        captureObservation(ObservationEnvelope(
            observationId = "manual-offer:$id",
            source = ObservationSource(
                sourceId = "busybee.manual_offer",
                provider = o.platform,
                kind = if (o.source.equals("manual", ignoreCase = true)) ObservationSourceKind.MANUAL else ObservationSourceKind.IMPORT,
                adapterVersion = "busybee-manual-offer/1"
            ),
            observedAtUnixMs = observedAt,
            receivedAtUnixMs = receivedAt,
            subjectId = "manual-offer:$id",
            facts = facts,
            evidence = listOf(evidence)
        ))
        return id
    }
    fun offers(limit:Int=100):List<DriverOffer> {
        val out=mutableListOf<DriverOffer>()
        readableDatabase.rawQuery("SELECT id,platform,pickup,destination,fare,bonus,pickup_km,trip_km,duration_min,passenger_rating,captured_at,source,raw,status FROM offers ORDER BY captured_at DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            while(c.moveToNext()) out.add(DriverOffer(c.getLong(0),c.getString(1),c.getString(2)?: "",c.getString(3)?: "",c.getDouble(4),c.getDouble(5),c.getDouble(6),c.getDouble(7),c.getDouble(8),if(c.isNull(9)) null else c.getDouble(9),c.getLong(10),c.getString(11),c.getString(12)?: "",c.getString(13)))
        }
        return out
    }
    fun updateOfferStatus(id:Long,status:String) { writableDatabase.execSQL("UPDATE offers SET status=? WHERE id=?", arrayOf(status,id)) }

    /**
     * Append a validated, versioned observation. Exact replays are ignored,
     * while changed payloads reusing an observation ID are retained as
     * separate variants for later reconciliation.
     */
    @Synchronized fun captureObservation(envelope: ObservationEnvelope): Boolean {
        if (envelope.validationError() != null) return false
        val source = JSONObject()
            .put("sourceId", envelope.source.sourceId)
            .put("provider", envelope.source.provider)
            .put("kind", envelope.source.kind.name.lowercase())
            .put("adapterVersion", envelope.source.adapterVersion)
        val facts = JSONObject()
        envelope.facts.forEach { (key, fact) ->
            val factEvidence = JSONArray()
            fact.evidence.forEach { ref ->
                factEvidence.put(JSONObject()
                    .put("evidenceId", ref.evidenceId)
                    .put("evidenceType", ref.evidenceType)
                    .put("localRef", ref.localRef ?: JSONObject.NULL))
            }
            facts.put(key, JSONObject()
                .put("value", fact.value ?: JSONObject.NULL)
                .put("evidence", factEvidence))
        }
        val evidence = JSONArray()
        envelope.evidence.forEach { ref ->
            evidence.put(JSONObject()
                .put("evidenceId", ref.evidenceId)
                .put("evidenceType", ref.evidenceType)
                .put("localRef", ref.localRef ?: JSONObject.NULL))
        }
        val payload = JSONObject()
            .put("schemaVersion", envelope.schemaVersion)
            .put("observationId", envelope.observationId)
            .put("source", source)
            .put("observedAtUnixMs", envelope.observedAtUnixMs)
            .put("receivedAtUnixMs", envelope.receivedAtUnixMs)
            .put("subjectId", envelope.subjectId ?: JSONObject.NULL)
            .put("facts", facts)
            .put("evidence", evidence)
            .toString()
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val values = ContentValues().apply {
            put("observation_id", envelope.observationId)
            put("fingerprint", fingerprint)
            put("source_id", envelope.source.sourceId)
            put("provider", envelope.source.provider)
            put("observed_at", envelope.observedAtUnixMs)
            put("received_at", envelope.receivedAtUnixMs)
            put("payload_json", payload)
        }
        val inserted = writableDatabase.insertWithOnConflict(
            "observation_ledger", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
        if (inserted) {
            // Rust owns provider-neutral interpretation. Keep capture operational
            // even on devices where the native library is unavailable.
            val candidate = runCatching {
                NativeBridge.classifyOfferObservation(payload)
            }.getOrNull()
            if (!candidate.isNullOrBlank()) persistCanonicalEvent(candidate)
        }
        return inserted
    }

    private fun persistCanonicalEvent(candidateJson: String) {
        val event = runCatching { JSONObject(candidateJson) }.getOrNull() ?: return
        if (!event.has("event_id") || !event.has("lifecycle_id") || !event.has("kind")) return
        val eventId = event.optString("event_id")
        val lifecycleId = event.optString("lifecycle_id")
        val eventType = event.optString("kind")
        if (eventId.isBlank() || lifecycleId.isBlank() || eventType.isBlank()) return
        if (event.optString("result") == "no_event" || event.has("error")) return

        val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest(candidateJson.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val db = writableDatabase
        db.rawQuery(
            "SELECT 1 FROM canonical_event_ledger WHERE event_id=? AND fingerprint=? LIMIT 1",
            arrayOf(eventId, fingerprint)
        ).use { if (it.moveToFirst()) return }
        val hasPriorVersion = db.rawQuery(
            "SELECT 1 FROM canonical_event_ledger WHERE event_id=? LIMIT 1",
            arrayOf(eventId)
        ).use { it.moveToFirst() }
        val status = if (hasPriorVersion) "conflict" else event.optString("status", "candidate")
        if (hasPriorVersion) {
            val prior = mutableListOf<Pair<Long, String>>()
            db.rawQuery(
                "SELECT id,payload_json FROM canonical_event_ledger WHERE event_id=?",
                arrayOf(eventId)
            ).use { cursor ->
                while (cursor.moveToNext()) prior.add(cursor.getLong(0) to cursor.getString(1))
            }
            prior.forEach { (id, payload) ->
                val conflictedPayload = runCatching { JSONObject(payload).put("status", "conflict").toString() }
                    .getOrDefault(payload)
                db.execSQL(
                    "UPDATE canonical_event_ledger SET status='conflict',payload_json=? WHERE id=?",
                    arrayOf(conflictedPayload, id)
                )
            }
            event.put("status", "conflict")
        }
        val values = ContentValues().apply {
            put("event_id", eventId)
            put("fingerprint", fingerprint)
            put("lifecycle_id", lifecycleId)
            put("event_type", eventType)
            put("status", status)
            if (event.isNull("occurred_at_unix_ms")) putNull("occurred_at")
            else put("occurred_at", event.optLong("occurred_at_unix_ms"))
            put("payload_json", event.toString())
        }
        db.insertWithOnConflict("canonical_event_ledger", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Re-run interpretation over stored evidence after interrupted native processing. */
    @Synchronized fun replayObservationLedger(limit: Int = 500) {
        observationPayloads(limit).forEach { payload ->
            val candidate = runCatching {
                NativeBridge.classifyOfferObservation(payload)
            }.getOrNull()
            if (!candidate.isNullOrBlank()) persistCanonicalEvent(candidate)
        }
    }

    /** Ordered canonical event payloads for BusyBee surfaces and replay. */
    fun canonicalEventPayloads(limit: Int = 100): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT payload_json,status FROM canonical_event_ledger ORDER BY occurred_at DESC,id DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 1000).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val payload = runCatching {
                    JSONObject(cursor.getString(0)).put("status", cursor.getString(1)).toString()
                }.getOrDefault(cursor.getString(0))
                out.add(payload)
            }
        }
        return out
    }

    /** Ordered raw envelopes for deterministic replay/reconciliation. */
    fun observationPayloads(limit: Int = 100): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT payload_json FROM observation_ledger ORDER BY observed_at,id LIMIT ?",
            arrayOf(limit.coerceIn(1, 1000).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) out.add(cursor.getString(0))
        }
        return out
    }

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
