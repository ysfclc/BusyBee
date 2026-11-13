package com.ysfclc.busybee.drivercommandcentre

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

internal enum class MapPlaceKind { BUILDING, ENTRANCE }

internal object RoadNamePolicy {
    fun fields(language: String): List<String> =
        if (language.equals("pl", ignoreCase = true)) {
            listOf("name:pl", "name", "name:latin", "name_en")
        } else {
            listOf("name", "name:latin", "name_en")
        }
}


internal data class MapPlaceFeature(
    val id: String,
    val kind: MapPlaceKind,
    val point: LatLng,
    val label: String,
    val detail: String
)

/**
 * Adds a small, explicitly OSM-derived address/entrance overlay to the basemap.
 * Missing entrance references are never converted into guessed stairwell labels.
 */
internal object MapPlaceLookup {
    fun query(latitude: Double, longitude: Double, radiusMeters: Int = 650): String {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        val radius = radiusMeters.coerceIn(250, 1000)
        val lat = String.format(Locale.US, "%.6f", latitude)
        val lon = String.format(Locale.US, "%.6f", longitude)
        return "[out:json][timeout:18];(" +
            "way(around:$radius,$lat,$lon)[\"building\"][\"name\"];" +
            "way(around:$radius,$lat,$lon)[\"building\"][\"name:pl\"];" +
            "way(around:$radius,$lat,$lon)[\"building\"][\"official_name\"];" +
            "way(around:$radius,$lat,$lon)[\"building\"][\"addr:housenumber\"];" +
            "relation(around:$radius,$lat,$lon)[\"building\"][\"name\"];" +
            "relation(around:$radius,$lat,$lon)[\"building\"][\"name:pl\"];" +
            "relation(around:$radius,$lat,$lon)[\"building\"][\"official_name\"];" +
            "relation(around:$radius,$lat,$lon)[\"building\"][\"addr:housenumber\"];" +
            "node(around:$radius,$lat,$lon)[\"entrance\"];" +
            "node(around:$radius,$lat,$lon)[\"door\"];" +
            ");out center tags;"
    }

    fun fetch(latitude: Double, longitude: Double): List<MapPlaceFeature> {
        val body = "data=" + java.net.URLEncoder.encode(query(latitude, longitude), "UTF-8")
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 9000
                    readTimeout = 19000
                    doOutput = true
                    setRequestProperty("User-Agent", "BusyBee-DriverCommandCentre/0.5 (OSM building and entrance overlay)")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode !in 200..299) error("Overpass HTTP ${connection.responseCode}")
                return parse(connection.inputStream.bufferedReader().use { it.readText() })
            } catch (error: Exception) {
                lastError = error
            } finally {
                connection?.disconnect()
            }
        }
        throw lastError ?: IllegalStateException("No OpenStreetMap detail source responded")
    }

    internal fun parse(json: String): List<MapPlaceFeature> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val output = ArrayList<MapPlaceFeature>()
        for (index in 0 until elements.length()) {
            val element = elements.optJSONObject(index) ?: continue
            val tags = element.optJSONObject("tags") ?: continue
            val type = element.optString("type")
            val id = element.optLong("id", -1L)
            if (id < 0L) continue
            val center = element.optJSONObject("center")
            val latitude = if (center != null) center.optDouble("lat", Double.NaN) else element.optDouble("lat", Double.NaN)
            val longitude = if (center != null) center.optDouble("lon", Double.NaN) else element.optDouble("lon", Double.NaN)
            if (!latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0
            ) continue

            if ((type == "way" || type == "relation") && tags.has("building")) {
                val name = tags.optString("name:pl").trim().ifBlank { tags.optString("name").trim() }.ifBlank { tags.optString("official_name").trim() }
                val street = tags.optString("addr:street").trim()
                val number = tags.optString("addr:housenumber").trim()
                val address = listOf(street, number).filter { it.isNotBlank() }.joinToString(" ")
                val label = name.ifBlank { address }
                if (label.isBlank()) continue
                val detail = buildList {
                    add("OpenStreetMap building")
                    if (address.isNotBlank()) add(address)
                    tags.optString("building:levels").takeIf { it.isNotBlank() }?.let { add("$it floors mapped") }
                    tags.optString("height").takeIf { it.isNotBlank() }?.let { add("Mapped height $it") }
                }.joinToString(" · ")
                output += MapPlaceFeature("$type/$id", MapPlaceKind.BUILDING, LatLng(latitude, longitude), label, detail)
            } else if (type == "node" && (tags.has("entrance") || tags.has("door"))) {
                val entrance = tags.optString("entrance").ifBlank { tags.optString("door") }
                val name = tags.optString("name:pl").trim().ifBlank { tags.optString("name").trim() }.ifBlank { tags.optString("official_name").trim() }
                val reference = tags.optString("ref").trim()
                val label = when {
                    name.isNotBlank() -> name
                    entrance == "staircase" && reference.isNotBlank() -> "Klatka $reference"
                    entrance == "staircase" -> "Klatka schodowa"
                    entrance == "main" -> "Wejście główne"
                    entrance == "service" -> "Wejście techniczne"
                    reference.isNotBlank() -> "Wejście · $reference"
                    else -> "Wejście"
                }
                val detail = buildList {
                    add("Mapped OSM entrance: $entrance")
                    if (reference.isNotBlank()) add("Reference $reference")
                    tags.optString("level").takeIf { it.isNotBlank() }?.let { add("Level $it") }
                    tags.optString("door").takeIf { it.isNotBlank() }?.let { add("Door $it") }
                    val address = listOf(tags.optString("addr:street"), tags.optString("addr:housenumber"))
                        .map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")
                    if (address.isNotBlank()) add(address)
                    tags.optString("wheelchair").takeIf { it.isNotBlank() }?.let { add("Wheelchair $it") }
                }.joinToString(" · ")
                output += MapPlaceFeature("node/$id", MapPlaceKind.ENTRANCE, LatLng(latitude, longitude), label, detail)
            }
        }
        return output.distinctBy { it.id }.take(160)
    }
}

/** Camera target offset keeps the vehicle in the lower part of a forward-facing 3D view. */
internal object NavigationCameraMath {
    fun aheadOf(location: LatLng, bearingDegrees: Double, distanceMeters: Double): LatLng {
        require(location.latitude.isFinite() && location.latitude in -90.0..90.0)
        require(location.longitude.isFinite() && location.longitude in -180.0..180.0)
        require(bearingDegrees.isFinite() && distanceMeters.isFinite() && distanceMeters >= 0.0)
        val earthRadius = 6_371_000.0
        val angularDistance = distanceMeters / earthRadius
        val bearing = Math.toRadians(bearingDegrees)
        val latitude1 = Math.toRadians(location.latitude)
        val longitude1 = Math.toRadians(location.longitude)
        val latitude2 = asin(
            sin(latitude1) * cos(angularDistance) +
                cos(latitude1) * sin(angularDistance) * cos(bearing)
        )
        val longitude2 = longitude1 + atan2(
            sin(bearing) * sin(angularDistance) * cos(latitude1),
            cos(angularDistance) - sin(latitude1) * sin(latitude2)
        )
        return LatLng(Math.toDegrees(latitude2), Math.toDegrees(longitude2))
    }
}

/** Small, legible native marker artwork; the vehicle points to the top of the screen. */
internal object MapMarkerArtwork {
    fun vehicle(context: Context): Bitmap {
        val size = (58f * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = size / 58f
        canvas.save()
        canvas.scale(scale, scale)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
        canvas.drawCircle(29f, 29f, 25f, shadow)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF092A31.toInt() }
        canvas.drawCircle(29f, 29f, 23f, halo)
        val body = Path().apply {
            moveTo(29f, 5f)
            cubicTo(23f, 5f, 20f, 11f, 18f, 18f)
            lineTo(13f, 40f)
            cubicTo(12f, 47f, 16f, 52f, 22f, 52f)
            lineTo(36f, 52f)
            cubicTo(42f, 52f, 46f, 47f, 45f, 40f)
            lineTo(40f, 18f)
            cubicTo(38f, 11f, 35f, 5f, 29f, 5f)
            close()
        }
        val shell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawPath(body, shell)
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF244957.toInt(); style = Paint.Style.FILL }
        canvas.drawRoundRect(RectF(19f, 16f, 39f, 29f), 5f, 5f, glass)
        canvas.drawRoundRect(RectF(19f, 33f, 39f, 43f), 3f, 3f, glass)
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF27D9A0.toInt(); strokeWidth = 2.8f; strokeCap = Paint.Cap.ROUND }
        canvas.drawLine(21f, 10f, 37f, 10f, accent)
        canvas.restore()
        return bitmap
    }

    fun buildingLabel(context: Context, text: String, night: Boolean, accentColor: Int = if (night) 0xFF5AD9B0.toInt() else 0xFF168467.toInt()): Bitmap {
        val density = context.resources.displayMetrics.density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f * density
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val safeText = text.take(27)
        val width = (paint.measureText(safeText) + 22f * density).toInt().coerceIn((54 * density).toInt(), (176 * density).toInt())
        val height = (29f * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (night) 0xF21A3540.toInt() else 0xF2F8FCF8.toInt() }
        canvas.drawRoundRect(RectF(1f, 1f, width - 1f, height - 1f), 9f * density, 9f * density, background)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }
        canvas.drawRoundRect(RectF(1f, 1f, width - 1f, height - 1f), 9f * density, 9f * density, border)
        paint.color = if (night) Color.WHITE else 0xFF183A3C.toInt()
        val x = 10f * density
        val y = (height - (paint.ascent() + paint.descent())) / 2f
        canvas.drawText(safeText, x, y, paint)
        return bitmap
    }

    fun entranceLabel(context: Context, text: String, night: Boolean): Bitmap =
        buildingLabel(context, text, night, 0xFFFFB34F.toInt())
}
