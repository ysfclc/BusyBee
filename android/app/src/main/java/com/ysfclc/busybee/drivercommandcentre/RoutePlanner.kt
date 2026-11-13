package com.ysfclc.busybee.drivercommandcentre

import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.*

data class DestinationCandidate(val displayName: String, val location: LatLng)

data class RouteStep(
    val instruction: String,
    val type: String,
    val modifier: String,
    val roadName: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val maneuverPoint: LatLng,
    val distanceAlongRouteMeters: Double
)

data class RoutePlan(
    val destinationName: String,
    val destination: LatLng,
    val points: List<LatLng>,
    val distanceKm: Double,
    val durationMinutes: Double,
    val steps: List<RouteStep> = emptyList()
)

object RoutePlanner {
    private const val NOMINATIM = "https://nominatim.openstreetmap.org/search"
    private const val OSRM = "https://router.project-osrm.org/route/v1/driving/"

    fun searchDestinations(query: String, near: LatLng? = null): List<DestinationCandidate> {
        require(query.isNotBlank()) { "Enter a destination" }
        val bias = near?.let {
            val bounds = listOf(it.longitude - 0.35, it.latitude + 0.35, it.longitude + 0.35, it.latitude - 0.35)
            "&viewbox=" + bounds.joinToString(",") { value -> String.format(Locale.US, "%.6f", value) }
        }.orEmpty()
        val url = "$NOMINATIM?format=jsonv2&limit=5&addressdetails=1&countrycodes=pl&accept-language=pl,en$bias&q=" +
            URLEncoder.encode(query.trim(), "UTF-8")
        return parseDestinationCandidates(request(url))
    }

    fun plan(origin: LatLng, query: String): RoutePlan = planAlternatives(origin, query).first()

    fun planAlternatives(origin: LatLng, query: String): List<RoutePlan> {
        val candidates = searchDestinations(query, origin)
        require(candidates.isNotEmpty()) { "No matching destination was found in Poland" }
        return planAlternatives(origin, candidates.first())
    }

    fun planAlternatives(origin: LatLng, destination: DestinationCandidate): List<RoutePlan> =
        planToCoordinates(origin, destination.location, destination.displayName)

    fun planToCoordinates(origin: LatLng, destination: LatLng, destinationName: String): List<RoutePlan> {
        require(validCoordinate(origin) && validCoordinate(destination)) { "Location is outside valid map coordinates" }
        val routeUrl = OSRM +
            String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", origin.longitude, origin.latitude, destination.longitude, destination.latitude) +
            "?overview=full&geometries=geojson&steps=true&alternatives=3"
        return parseRouteResponse(request(routeUrl), destination, destinationName)
    }

    internal fun parseDestinationCandidates(json: String): List<DestinationCandidate> {
        val matches = JSONArray(json)
        val out = mutableListOf<DestinationCandidate>()
        for (index in 0 until matches.length()) {
            val item = matches.optJSONObject(index) ?: continue
            val lat = item.optString("lat").toDoubleOrNull() ?: continue
            val lon = item.optString("lon").toDoubleOrNull() ?: continue
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val name = item.optString("display_name").trim()
            if (name.isNotBlank()) out.add(DestinationCandidate(name, LatLng(lat, lon)))
        }
        return out.distinctBy {
            String.format(Locale.US, "%.5f:%.5f", it.location.latitude, it.location.longitude)
        }.take(5)
    }

    internal fun parseRouteResponse(json: String, destination: LatLng, destinationName: String): List<RoutePlan> {
        val response = JSONObject(json)
        val code = response.optString("code")
        require(code == "Ok") { response.optString("message").ifBlank { "Routing service returned $code" } }
        val routes = response.optJSONArray("routes") ?: error("Routing response has no routes")
        require(routes.length() > 0) { "No drivable route found" }

        val plans = mutableListOf<RoutePlan>()
        for (routeIndex in 0 until routes.length()) {
            val route = routes.optJSONObject(routeIndex) ?: continue
            val coordinates = route.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val points = ArrayList<LatLng>(coordinates.length())
            for (pointIndex in 0 until coordinates.length()) {
                val pair = coordinates.optJSONArray(pointIndex) ?: continue
                if (pair.length() < 2) continue
                val lon = pair.optDouble(0, Double.NaN)
                val lat = pair.optDouble(1, Double.NaN)
                if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    points.add(LatLng(lat, lon))
                }
            }
            if (points.size < 2) continue
            plans.add(
                RoutePlan(
                    destinationName = destinationName,
                    destination = destination,
                    points = points,
                    distanceKm = route.optDouble("distance", 0.0) / 1000.0,
                    durationMinutes = route.optDouble("duration", 0.0) / 60.0,
                    steps = parseSteps(route.optJSONArray("legs"), points)
                )
            )
        }
        require(plans.isNotEmpty()) { "Routing service returned incomplete routes" }
        return RouteOptionRanker.rank(plans)
    }

    private fun parseSteps(legs: JSONArray?, points: List<LatLng>): List<RouteStep> {
        if (legs == null) return emptyList()
        val out = mutableListOf<RouteStep>()
        for (legIndex in 0 until legs.length()) {
            val steps = legs.optJSONObject(legIndex)?.optJSONArray("steps") ?: continue
            for (stepIndex in 0 until steps.length()) {
                val step = steps.optJSONObject(stepIndex) ?: continue
                val maneuver = step.optJSONObject("maneuver") ?: continue
                val location = maneuver.optJSONArray("location") ?: continue
                if (location.length() < 2) continue
                val point = LatLng(location.optDouble(1, Double.NaN), location.optDouble(0, Double.NaN))
                if (!validCoordinate(point)) continue
                val type = maneuver.optString("type", "continue")
                val modifier = maneuver.optString("modifier", "")
                val road = step.optString("name", "").trim()
                val alongRoute = projectToRoute(point, points).progressMeters
                out.add(
                    RouteStep(
                        instruction(type, modifier, road, maneuver.optInt("exit", -1)),
                        type, modifier, road,
                        step.optDouble("distance", 0.0).coerceAtLeast(0.0),
                        step.optDouble("duration", 0.0).coerceAtLeast(0.0),
                        point, alongRoute
                    )
                )
            }
        }
        // OSRM emits maneuvers in road-traversal order. Keep that order; sorting by
        // nearest geometry vertex can reorder steps on loops or tight junctions.
        return out
    }

    private fun instruction(type: String, modifier: String, road: String, exit: Int): String {
        val direction = modifier.replace('-', ' ').ifBlank { "straight" }
        val onto = road.takeIf { it.isNotBlank() }?.let { " onto $it" }.orEmpty()
        val result = when (type.lowercase(Locale.ROOT)) {
            "depart" -> "Head out$onto"
            "arrive" -> "Arrive at your destination"
            "roundabout", "rotary" -> if (exit > 0) "At the roundabout, take exit $exit$onto" else "At the roundabout, continue$onto"
            "merge" -> "Merge $direction$onto"
            "fork" -> "Keep $direction$onto"
            "on ramp" -> "Take the ramp$onto"
            "off ramp" -> "Take the exit ramp$onto"
            "end of road" -> "At the end of the road, turn $direction$onto"
            "new name", "continue" -> "Continue$onto"
            "turn" -> "Turn $direction$onto"
            else -> if (direction == "straight") "Continue$onto" else "Turn $direction$onto"
        }
        return result.replace("  ", " ").trim().replaceFirstChar { it.uppercase(Locale.getDefault()) }
    }

    fun distanceMeters(from: LatLng, to: LatLng): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun distanceToRouteMeters(location: LatLng, points: List<LatLng>): Double =
        projectToRoute(location, points).distanceMeters

    fun progressMeters(location: LatLng, points: List<LatLng>): Double =
        projectToRoute(location, points).progressMeters

    private data class RouteProjection(val distanceMeters: Double, val progressMeters: Double)

    /**
     * Project GPS/maneuver coordinates onto route segments in a local
     * equirectangular plane. Vertex-only snapping caused progress jumps between
     * geometry points and could trigger false off-route readings.
     */
    private fun projectToRoute(location: LatLng, points: List<LatLng>): RouteProjection {
        if (points.isEmpty()) return RouteProjection(Double.POSITIVE_INFINITY, 0.0)
        if (points.size == 1) return RouteProjection(distanceMeters(location, points.first()), 0.0)

        val earthRadius = 6_371_000.0
        var cumulative = 0.0
        var bestDistance = Double.POSITIVE_INFINITY
        var bestProgress = 0.0

        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            val meanLatitude = Math.toRadians((start.latitude + end.latitude + location.latitude) / 3.0)
            val scaleX = earthRadius * cos(meanLatitude)
            val startX = Math.toRadians(start.longitude) * scaleX
            val startY = Math.toRadians(start.latitude) * earthRadius
            val endX = Math.toRadians(end.longitude) * scaleX
            val endY = Math.toRadians(end.latitude) * earthRadius
            val locationX = Math.toRadians(location.longitude) * scaleX
            val locationY = Math.toRadians(location.latitude) * earthRadius
            val dx = endX - startX
            val dy = endY - startY
            val segmentSquared = dx * dx + dy * dy
            val fraction = if (segmentSquared <= 1e-9) 0.0 else
                (((locationX - startX) * dx + (locationY - startY) * dy) / segmentSquared).coerceIn(0.0, 1.0)
            val projectedX = startX + fraction * dx
            val projectedY = startY + fraction * dy
            val offset = hypot(locationX - projectedX, locationY - projectedY)
            val segmentLength = distanceMeters(start, end)
            if (offset < bestDistance) {
                bestDistance = offset
                bestProgress = cumulative + fraction * segmentLength
            }
            cumulative += segmentLength
        }
        return RouteProjection(bestDistance, bestProgress)
    }

    private fun validCoordinate(point: LatLng): Boolean =
        point.latitude.isFinite() && point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

    private fun request(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "BusyBee-DriverCommandCentre/0.4 (Android route planning)")
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Routing source HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

object RouteOptionRanker {
    fun rank(plans: List<RoutePlan>): List<RoutePlan> =
        plans.distinctBy { String.format(Locale.US, "%.3f:%.3f", it.distanceKm, it.durationMinutes) }
            .sortedWith(compareBy<RoutePlan> { it.durationMinutes }.thenBy { it.distanceKm })
            .take(3)
}
