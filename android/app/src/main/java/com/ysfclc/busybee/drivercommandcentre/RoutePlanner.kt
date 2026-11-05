package com.ysfclc.busybee.drivercommandcentre

import org.json.JSONArray
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RoutePlan(
    val destinationName: String,
    val destination: LatLng,
    val points: List<LatLng>,
    val distanceKm: Double,
    val durationMinutes: Double
)

object RoutePlanner {
    fun plan(origin: LatLng, query: String): RoutePlan {
        require(query.isNotBlank()) { "Enter a destination" }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val geocode = request("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=$encoded")
        val matches = JSONArray(geocode)
        require(matches.length() > 0) { "Destination not found" }
        val match = matches.getJSONObject(0)
        val destination = LatLng(match.getString("lat").toDouble(), match.getString("lon").toDouble())
        val routeUrl = "https://router.project-osrm.org/route/v1/driving/" +
            origin.longitude + "," + origin.latitude + ";" +
            destination.longitude + "," + destination.latitude +
            "?overview=full&geometries=geojson&steps=true"
        val routeJson = org.json.JSONObject(request(routeUrl))
        val routes = routeJson.optJSONArray("routes") ?: error("Routing response has no routes")
        require(routes.length() > 0) { "No drivable route found" }
        val route = routes.getJSONObject(0)
        val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
        val points = ArrayList<LatLng>(coordinates.length())
        for (i in 0 until coordinates.length()) {
            val pair = coordinates.getJSONArray(i)
            points.add(LatLng(pair.getDouble(1), pair.getDouble(0)))
        }
        require(points.size >= 2) { "Routing service returned an incomplete route" }
        return RoutePlan(
            match.optString("display_name", query),
            destination,
            points,
            route.getDouble("distance") / 1000.0,
            route.getDouble("duration") / 60.0
        )
    }

    private fun request(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "BusyBee-DriverCommandCentre/0.3 (Android route planning)")
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Routing source HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
