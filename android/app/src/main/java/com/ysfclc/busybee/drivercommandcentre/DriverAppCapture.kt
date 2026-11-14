package com.ysfclc.busybee.drivercommandcentre

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.security.MessageDigest
import java.util.Locale

/**
 * A user-started, Android system-approved bridge for reading visible Uber/Bolt
 * Driver screens. Frames are OCR'd locally; screenshots are never persisted or
 * uploaded. Extracted text and a rule-based parser-confidence hint are retained
 * for review; that hint is not a factual accuracy probability. Nothing enters earnings
 * until the driver confirms the values.
 */
data class NativeDriverObservation(
    val id: Long = 0,
    val platform: String,
    val screenType: String,
    val capturedAt: Long,
    val visibleText: String,
    val detectedAmount: Double?,
    val distanceKm: Double?,
    val durationMinutes: Double?,
    val sourceType: String = "MEDIA_PROJECTION_OCR",
    val pickup: String? = null,
    val dropoff: String? = null,
    val parseConfidence: Double = 0.0
)

data class ParsedDriverScreen(
    val platform: String,
    val screenType: String,
    val capturedAt: Long,
    val visibleText: String,
    val detectedAmount: Double?,
    val distanceKm: Double?,
    val durationMinutes: Double?,
    val sourceType: String = "MEDIA_PROJECTION_OCR",
    val pickup: String? = null,
    val dropoff: String? = null,
    val parseConfidence: Double = 0.0
)

object DriverScreenTextParser {
    private val moneyPattern = Regex(
        """(?:PLN|zł)\s*([0-9]+(?:[,.][0-9]{1,2})?)|([0-9]+(?:[,.][0-9]{1,2})?)\s*(?:PLN|zł)""",
        RegexOption.IGNORE_CASE
    )
    private val distancePattern = Regex("""([0-9]+(?:[,.][0-9]+)?)\s*(?:km|kilomet(?:er|re)s?)""", RegexOption.IGNORE_CASE)
    private val durationPattern = Regex("""([0-9]+)\s*(?:min|minutes?)""", RegexOption.IGNORE_CASE)
    private val pickupLabelPattern = Regex(
        """^(?:pickup(?:\s+(?:address|location))?|pick[\s-]?up|miejsce\s+odbioru|odbiór)(?:\s*[:：\-–]\s*|\s+)?(.*)$""",
        RegexOption.IGNORE_CASE
    )
    private val dropoffLabelPattern = Regex(
        """^(?:drop[\s-]?off(?:\s+(?:address|location))?|destination(?:\s+address)?|miejsce\s+docelowe|cel\s+podróży)(?:\s*[:：\-–]\s*|\s+)?(.*)$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        platform: String,
        text: String,
        capturedAt: Long,
        sourceType: String = "MEDIA_PROJECTION_OCR"
    ): ParsedDriverScreen? {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(160)
            .toList()
        val visibleText = lines.joinToString("\n").take(5000)
        if (visibleText.length < 12) return null

        val lower = visibleText.lowercase(Locale.ROOT)
        val relevant = listOf(
            "trip", "ride", "earnings", "earning", "fare", "pickup", "dropoff",
            "destination", "accept", "completed", "today", "payment", "payout",
            "przejazd", "kurs", "zarobki", "przychod", "przychód", "opłata",
            "odbiór", "miejsce odbioru", "cel podróży", "zaakceptuj", "zakończ",
            "ukończ", "dzisiaj", "wypłata", "zł", "pln", "km", "min"
        ).any(lower::contains)
        if (!relevant) return null

        val amount = moneyPattern.find(visibleText)?.let { match ->
            (match.groups[1]?.value ?: match.groups[2]?.value)
                ?.replace(',', '.')?.toDoubleOrNull()
        }
        val distance = distancePattern.find(visibleText)?.groupValues?.getOrNull(1)
            ?.replace(',', '.')?.toDoubleOrNull()
        val duration = durationPattern.find(visibleText)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val screenType = when {
            listOf("earnings", "payout", "payment", "today's total", "weekly total", "zarobki", "przychód", "przychody", "wypłata", "rozliczenie").any(lower::contains) -> "EARNINGS"
            listOf("accept", "pickup", "dropoff", "destination", "offer", "zaakceptuj", "odbiór", "cel podróży", "oferta").any(lower::contains) -> "OFFER"
            listOf("trip complete", "ride complete", "completed trip", "trip details", "zakończono przejazd", "przejazd zakończony", "ukończono kurs").any(lower::contains) -> "TRIP"
            else -> "DRIVER SCREEN"
        }

        val pickup = extractLabelledLocation(lines, pickupLabelPattern)
        val dropoff = extractLabelledLocation(lines, dropoffLabelPattern)
        // Heuristic confidence describes screen classification and explicit field extraction.
        // It is not a probability that a fare/address is factually correct.
        val recognisedScreen = screenType != "DRIVER SCREEN"
        val knownFieldCount = listOf(amount, distance, duration, pickup, dropoff).count { it != null }
        val screenBase = if (recognisedScreen) 0.56 else 0.30
        val sourceBonus = if (sourceType == "ACCESSIBILITY_UI") 0.08 else 0.0
        val parseConfidence = (screenBase + knownFieldCount * 0.065 + sourceBonus).coerceIn(0.0, 0.92)
        return ParsedDriverScreen(
            platform = platform,
            screenType = screenType,
            capturedAt = capturedAt,
            visibleText = visibleText,
            detectedAmount = amount,
            distanceKm = distance,
            durationMinutes = duration,
            sourceType = sourceType,
            pickup = pickup,
            dropoff = dropoff,
            parseConfidence = parseConfidence
        )
    }

    private fun isMetricOrControlLine(value: String): Boolean {
        val lower = value.lowercase(Locale.ROOT)
        if (moneyPattern.containsMatchIn(value) || distancePattern.containsMatchIn(value) || durationPattern.containsMatchIn(value)) return true
        return Regex(
            """^(?:fare|price|earnings?|total|fees?|bonus|distance|duration|time|trip|ride|offer|accept|navigate|cancel|complete|completed|details|payment|payout|zarobki|opłata|przychód|wypłata|odległość|czas|premia|prowizja)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(lower)
    }

    private fun extractLabelledLocation(lines: List<String>, pattern: Regex): String? {
        for (index in lines.indices) {
            val match = pattern.find(lines[index]) ?: continue
            var value = match.groupValues.getOrNull(1).orEmpty().trim()
            if (value.isBlank()) {
                value = lines.drop(index + 1).firstOrNull { candidate ->
                    val normalized = candidate.trim()
                    normalized.isNotBlank() &&
                        !pickupLabelPattern.containsMatchIn(normalized) &&
                        !dropoffLabelPattern.containsMatchIn(normalized) &&
                        !isMetricOrControlLine(normalized)
                }.orEmpty()
            }
            val cleaned = value
                .replace(Regex("\\s+"), " ")
                .trim(' ', ':', '-', '–', '—', '·')
                .take(180)
            if (cleaned.length < 3) continue
            if (Regex("""^(?:in\s+)?[0-9]+(?:[,.][0-9]+)?\s*(?:m|min|mins|minutes?|km)$""", RegexOption.IGNORE_CASE).matches(cleaned)) continue
            if (cleaned.lowercase(Locale.ROOT) in setOf("accept","navigate","cancel","complete","dropoff","pickup","destination")) continue
            return cleaned
        }
        return null
    }
}

class DriverAppCaptureStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "busybee_native_app_capture.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE observations(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fingerprint TEXT NOT NULL UNIQUE,
                platform TEXT NOT NULL,
                screen_type TEXT NOT NULL,
                captured_at INTEGER NOT NULL,
                visible_text TEXT NOT NULL,
                detected_amount REAL,
                distance_km REAL,
                duration_minutes REAL,
                source_type TEXT NOT NULL DEFAULT 'MEDIA_PROJECTION_OCR',
                pickup TEXT,
                dropoff TEXT,
                parse_confidence REAL NOT NULL DEFAULT 0.0
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX observations_time_idx ON observations(captured_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE observations ADD COLUMN source_type TEXT NOT NULL DEFAULT 'MEDIA_PROJECTION_OCR'")
            db.execSQL("ALTER TABLE observations ADD COLUMN pickup TEXT")
            db.execSQL("ALTER TABLE observations ADD COLUMN dropoff TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE observations ADD COLUMN parse_confidence REAL NOT NULL DEFAULT 0.0")
        }
    }

    @Synchronized
    fun save(observation: ParsedDriverScreen): Long {
        val fingerprint = sha256(
            listOf(
                observation.platform,
                observation.screenType,
                observation.visibleText,
                (observation.capturedAt / 5000L).toString()
            ).joinToString("\u0000")
        )
        val values = android.content.ContentValues().apply {
            put("fingerprint", fingerprint)
            put("platform", observation.platform)
            put("screen_type", observation.screenType)
            put("captured_at", observation.capturedAt)
            put("visible_text", observation.visibleText)
            put("source_type", observation.sourceType)
            if (observation.pickup == null) putNull("pickup") else put("pickup", observation.pickup)
            if (observation.dropoff == null) putNull("dropoff") else put("dropoff", observation.dropoff)
            put("parse_confidence", observation.parseConfidence.coerceIn(0.0, 1.0))
            if (observation.detectedAmount == null) putNull("detected_amount") else put("detected_amount", observation.detectedAmount)
            if (observation.distanceKm == null) putNull("distance_km") else put("distance_km", observation.distanceKm)
            if (observation.durationMinutes == null) putNull("duration_minutes") else put("duration_minutes", observation.durationMinutes)
        }
        return writableDatabase.insertWithOnConflict(
            "observations", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun recent(limit: Int = 30): List<NativeDriverObservation> {
        val result = mutableListOf<NativeDriverObservation>()
        readableDatabase.rawQuery(
            """SELECT id,platform,screen_type,captured_at,visible_text,detected_amount,distance_km,duration_minutes,source_type,pickup,dropoff,parse_confidence
               FROM observations ORDER BY captured_at DESC,id DESC LIMIT ?""".trimIndent(),
            arrayOf(limit.coerceIn(1, 200).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    NativeDriverObservation(
                        id = cursor.getLong(0),
                        platform = cursor.getString(1),
                        screenType = cursor.getString(2),
                        capturedAt = cursor.getLong(3),
                        visibleText = cursor.getString(4),
                        detectedAmount = if (cursor.isNull(5)) null else cursor.getDouble(5),
                        distanceKm = if (cursor.isNull(6)) null else cursor.getDouble(6),
                        durationMinutes = if (cursor.isNull(7)) null else cursor.getDouble(7),
                        sourceType = cursor.getString(8),
                        pickup = if (cursor.isNull(9)) null else cursor.getString(9),
                        dropoff = if (cursor.isNull(10)) null else cursor.getString(10),
                        parseConfidence = cursor.getDouble(11)
                    )
                )
            }
        }
        return result
    }

    fun delete(id: Long) {
        writableDatabase.delete("observations", "id=?", arrayOf(id.toString()))
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

class DriverAppCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recognizerLazy = lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val recognizer by recognizerLazy
    private var platform = "Uber"
    private var processingFrame = false
    private var lastFrameAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }

        platform = intent.getStringExtra(EXTRA_PLATFORM)?.trim()?.take(60)?.takeIf { it.isNotBlank() } ?: "Uber"

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        }
        if (resultData == null || resultCode == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader?.setOnImageAvailableListener(::readLatestFrame, handler)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCapture()
                }
            }, handler)
            display = projection?.createVirtualDisplay(
                "BusyBee-$platform-DriverScreen",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                handler
            )
        } catch (_: SecurityException) {
            stopCapture()
        } catch (_: Exception) {
            stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun readLatestFrame(source: ImageReader) {
        val image = try { source.acquireLatestImage() } catch (_: Exception) { null } ?: return
        val now = System.currentTimeMillis()
        if (processingFrame || now - lastFrameAt < FRAME_INTERVAL_MS) {
            image.close()
            return
        }
        processingFrame = true
        lastFrameAt = now
        var bitmap: Bitmap? = null
        try {
            val plane = image.planes.firstOrNull() ?: run {
                processingFrame = false
                return
            }
            val pixelStride = plane.pixelStride
            val rowPadding = plane.rowStride - pixelStride * image.width
            val paddedWidth = image.width + rowPadding / pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            bitmap = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
            padded.recycle()
        } catch (_: Exception) {
            processingFrame = false
        } finally {
            image.close()
        }

        val frame = bitmap
        if (frame == null) return
        try {
            recognizer.process(InputImage.fromBitmap(frame, 0))
                .addOnSuccessListener { result ->
                    val parsed = DriverScreenTextParser.parse(platform, result.text, System.currentTimeMillis())
                    if (parsed != null) {
                        runCatching { DriverAppCaptureStore(applicationContext).save(parsed) }
                    }
                }
                .addOnCompleteListener {
                    frame.recycle()
                    processingFrame = false
                }
        } catch (_: Exception) {
            frame.recycle()
            processingFrame = false
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BusyBee driver app capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Visible-screen capture session started by the driver"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, DriverAppCaptureService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("BusyBee is reading $platform Driver")
            .setContentText("On-device text extraction · no screenshots saved · tap Stop to end")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "Stop capture", stopPending)
            .build()
    }

    private fun stopCapture() {
        display?.release()
        display = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        val activeProjection = projection
        projection = null
        processingFrame = false
        // Clear our reference before stop(): MediaProjection invokes onStop()
        // synchronously/asynchronously and must not recurse into stopCapture().
        activeProjection?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        display?.release()
        imageReader?.close()
        val activeProjection = projection
        projection = null
        activeProjection?.stop()
        if (recognizerLazy.isInitialized()) recognizer.close()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.ysfclc.busybee.action.START_DRIVER_CAPTURE"
        const val ACTION_STOP = "com.ysfclc.busybee.action.STOP_DRIVER_CAPTURE"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val EXTRA_PLATFORM = "driver_platform"
        private const val CHANNEL_ID = "busybee_driver_capture"
        private const val NOTIFICATION_ID = 4307
        private const val FRAME_INTERVAL_MS = 1800L
    }
}
