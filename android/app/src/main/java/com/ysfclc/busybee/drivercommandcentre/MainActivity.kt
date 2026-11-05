package com.ysfclc.busybee.drivercommandcentre

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

private data class RadarPlace(
    val name: String,
    val category: String,
    val position: LatLng,
    val detail: String
)

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private val worker = Executors.newSingleThreadExecutor()
    private var map: MapLibreMap? = null
    private var tilted = true
    private var mapStatusView: TextView? = null
    private var placesCountView: TextView? = null
    private var sourceView: TextView? = null
    private var scanButton: TextView? = null
    private var locationMarker: Marker? = null
    private val placeMarkers = mutableListOf<Marker>()
    private var places: List<RadarPlace> = emptyList()
    private val enabledLayers = mutableSetOf("Fuel", "Parking", "EV", "Taxi", "Rest")
    private val layerButtons = mutableMapOf<String, TextView>()
    private var lastFix: Location? = null
    private var locationTracking = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastFix = location
            showLocation(location, moveCamera = !locationTracking)
            locationTracking = true
            mapStatusView?.text = "  ●  GPS LIVE"
            sourceView?.text = "PHONE GPS  •  OPENSTREETMAP DATA"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 19, 27)
        window.navigationBarColor = Color.rgb(7, 19, 27)
        MapLibre.getInstance(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(7, 19, 27)) }
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        root.addView(buildHeader(), topParams())
        root.addView(buildRadarPanel(), bottomParams())
        setContentView(root)

        mapView.getMapAsync { mapInstance ->
            map = mapInstance
            mapInstance.setStyle(Style.Builder().fromJson(OSM_STYLE)) {
                mapStatusView?.text = "  ●  MAP ONLINE"
                mapInstance.cameraPosition = CameraPosition.Builder()
                    .target(KRAKOW).zoom(13.0).tilt(38.0).build()
                scanArea()
            }
        }
    }

    private fun buildHeader(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(13), dp(17), dp(12))
            background = panelBackground(0xED0A1821.toInt(), 20)
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply {
            text = "BUSYBEE"
            setTextColor(Color.WHITE)
            textSize = 19f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            letterSpacing = 0.12f
        }
        val status = TextView(this).apply {
            text = "  ●  STARTING"
            setTextColor(0xFF8FE7C1.toInt())
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
        }
        mapStatusView = status
        row.addView(brand)
        row.addView(status)
        val subtitle = TextView(this).apply {
            text = "DRIVER RADAR  /  KRAKÓW PILOT"
            setTextColor(0xFF9AAEB8.toInt())
            textSize = 10f
            letterSpacing = 0.13f
            setPadding(0, dp(4), 0, 0)
        }
        container.addView(row)
        container.addView(subtitle)
        return container
    }

    private fun buildRadarPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = panelBackground(0xF20A1821.toInt(), 22)
        }
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val heading = TextView(this).apply {
            text = "NEARBY DRIVER SERVICES"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        }
        placesCountView = TextView(this).apply {
            text = "SCANNING…"
            setTextColor(0xFF8FE7C1.toInt())
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
        }
        titleRow.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(placesCountView)

        val layerScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dp(11), 0, dp(7))
        }
        val layerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Fuel", "Parking", "EV", "Taxi", "Rest").forEach { category ->
            val chip = TextView(this).apply {
                text = category.uppercase()
                gravity = Gravity.CENTER
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.04f
                setPadding(dp(13), dp(9), dp(13), dp(9))
                setOnClickListener {
                    if (enabledLayers.contains(category)) enabledLayers.remove(category)
                    else enabledLayers.add(category)
                    refreshLayerButtons()
                    renderPlaces()
                }
            }
            layerButtons[category] = chip
            val lp = LinearLayout.LayoutParams(-2, dp(34))
            lp.marginEnd = dp(7)
            layerRow.addView(chip, lp)
        }
        layerScroll.addView(layerRow)
        refreshLayerButtons()

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val gps = actionButton("◎  NEAR ME", true)
        gps.setOnClickListener { requestLocation() }
        scanButton = actionButton("↻  SCAN AREA", false).also { button ->
            button.setOnClickListener { scanArea() }
        }
        val viewMode = actionButton("3D  ON", false)
        viewMode.setOnClickListener {
            tilted = !tilted
            viewMode.text = if (tilted) "3D  ON" else "3D  OFF"
            map?.let { activeMap ->
                activeMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(activeMap.cameraPosition.target ?: KRAKOW)
                        .zoom(activeMap.cameraPosition.zoom)
                        .bearing(activeMap.cameraPosition.bearing)
                        .tilt(if (tilted) 38.0 else 0.0)
                        .build()), 350)
            }
        }
        listOf(gps, scanButton!!, viewMode).forEachIndexed { index, button ->
            val lp = LinearLayout.LayoutParams(0, dp(43), 1f)
            if (index > 0) lp.marginStart = dp(7)
            actions.addView(button, lp)
        }
        sourceView = TextView(this).apply {
            text = "SOURCE: OPENSTREETMAP  •  WAITING FOR FIRST SCAN"
            setTextColor(0xFF92A7B2.toInt())
            textSize = 9f
            letterSpacing = 0.04f
            setPadding(0, dp(10), 0, 0)
        }

        panel.addView(titleRow)
        panel.addView(layerScroll)
        panel.addView(actions)
        panel.addView(sourceView)
        return panel
    }

    private fun refreshLayerButtons() {
        layerButtons.forEach { (category, button) ->
            val selected = enabledLayers.contains(category)
            button.setTextColor(if (selected) 0xFF07131B.toInt() else 0xFFB7C6CE.toInt())
            button.background = panelBackground(
                if (selected) 0xFF8FE7C1.toInt() else 0xFF20323D.toInt(), 12
            )
        }
    }

    private fun actionButton(label: String, primary: Boolean): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 10f
            letterSpacing = 0.03f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (primary) 0xFF07131B.toInt() else Color.WHITE)
            background = panelBackground(
                if (primary) 0xFF8FE7C1.toInt() else 0xFF20323D.toInt(), 13
            )
        }

    private fun scanArea() {
        val center = map?.cameraPosition?.target ?: KRAKOW
        scanButton?.isEnabled = false
        scanButton?.alpha = 0.55f
        mapStatusView?.text = "  ●  FETCHING DATA"
        placesCountView?.text = "SCANNING…"
        sourceView?.text = "QUERYING OPENSTREETMAP  •  4.5 KM RADIUS"
        worker.execute {
            try {
                val found = fetchNearbyPlaces(center.latitude, center.longitude)
                runOnUiThread {
                    places = found
                    renderPlaces()
                    mapStatusView?.text = "  ●  MAP + DATA LIVE"
                    placesCountView?.text = "${found.size} FOUND"
                    sourceView?.text = "OPENSTREETMAP  •  ${found.size} FEATURES  •  JUST UPDATED"
                    scanButton?.isEnabled = true
                    scanButton?.alpha = 1f
                }
            } catch (error: Exception) {
                runOnUiThread {
                    mapStatusView?.text = "  ●  MAP ONLINE / DATA OFFLINE"
                    placesCountView?.text = "DATA UNAVAILABLE"
                    sourceView?.text = "OPEN DATA REQUEST FAILED  •  TAP SCAN TO RETRY"
                    scanButton?.isEnabled = true
                    scanButton?.alpha = 1f
                }
            }
        }
    }

    private fun fetchNearbyPlaces(latitude: Double, longitude: Double): List<RadarPlace> {
        val amenityPattern = "fuel|parking|charging_station|taxi|toilets|cafe|restaurant|fast_food"
        val query = """
            [out:json][timeout:25];
            (
              node(around:4500,$latitude,$longitude)["amenity"~"^($amenityPattern)$"];
              way(around:4500,$latitude,$longitude)["amenity"~"^($amenityPattern)$"];
            );
            out center tags;
        """.trimIndent()
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
        )
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000
                    readTimeout = 35000
                    doOutput = true
                    setRequestProperty("User-Agent", "BusyBee-DriverRadar/0.2 (Android)")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
                val body = "data=" + URLEncoder.encode(query, "UTF-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("Overpass HTTP $code")
                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                val elements = JSONObject(payload).optJSONArray("elements")
                    ?: throw IllegalStateException("Overpass returned no elements array")
                val parsed = ArrayList<RadarPlace>(elements.length())
                val seen = HashSet<String>()
                for (index in 0 until elements.length()) {
                    val element = elements.optJSONObject(index) ?: continue
                    val tags = element.optJSONObject("tags") ?: continue
                    val amenity = tags.optString("amenity")
                    val category = when (amenity) {
                        "fuel" -> "Fuel"
                        "parking" -> "Parking"
                        "charging_station" -> "EV"
                        "taxi" -> "Taxi"
                        "toilets", "cafe", "restaurant", "fast_food" -> "Rest"
                        else -> continue
                    }
                    val center = element.optJSONObject("center")
                    val lat = if (center != null) center.optDouble("lat", Double.NaN)
                              else element.optDouble("lat", Double.NaN)
                    val lon = if (center != null) center.optDouble("lon", Double.NaN)
                              else element.optDouble("lon", Double.NaN)
                    if (!lat.isFinite() || !lon.isFinite()) continue
                    val key = "${"%.5f".format(java.util.Locale.US, lat)}:${"%.5f".format(java.util.Locale.US, lon)}"
                    if (!seen.add(key)) continue
                    val name = tags.optString("name").takeIf { it.isNotBlank() }
                        ?: tags.optString("brand").takeIf { it.isNotBlank() }
                        ?: category
                    val detail = listOf(
                        tags.optString("opening_hours").takeIf { it.isNotBlank() },
                        tags.optString("operator").takeIf { it.isNotBlank() }
                    ).filterNotNull().joinToString(" • ").ifBlank { "Hours not listed" }
                    parsed.add(RadarPlace(name, category, LatLng(lat, lon), detail))
                }
                return parsed.sortedWith(compareBy<RadarPlace> { it.category }.thenBy { it.name })
            } catch (error: Exception) {
                lastError = error
            } finally {
                connection?.disconnect()
            }
        }
        throw lastError ?: IllegalStateException("No OpenStreetMap data endpoint responded")
    }

    private fun renderPlaces() {
        val activeMap = map ?: return
        placeMarkers.forEach { activeMap.removeAnnotation(it) }
        placeMarkers.clear()
        val visible = places.filter { enabledLayers.contains(it.category) }
        visible.forEach { place ->
            val snippet = "${place.category}  •  ${place.detail}\nOpenStreetMap"
            activeMap.addMarker(
                MarkerOptions().position(place.position).title(place.name).snippet(snippet)
            )?.let { placeMarkers.add(it) }
        }
        placesCountView?.text = "${visible.size} / ${places.size} SHOWN"
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_REQUEST
            )
            return
        }
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        try {
            val fineGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val allowedProviders = if (fineGranted) {
                setOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            } else {
                setOf(LocationManager.NETWORK_PROVIDER)
            }
            val providers = locationManager.getProviders(true).filter { it in allowedProviders }
            if (providers.isEmpty()) {
                mapStatusView?.text = "  ●  LOCATION DISABLED"
                sourceView?.text = "TURN ON PHONE LOCATION SERVICES"
                return
            }
            providers.forEach { provider ->
                locationManager.getLastKnownLocation(provider)?.let { location ->
                    if (lastFix == null || location.time > lastFix!!.time) lastFix = location
                }
                locationManager.requestLocationUpdates(
                    provider, 5000L, 20f, locationListener, Looper.getMainLooper()
                )
            }
            locationTracking = false
            lastFix?.let { showLocation(it, moveCamera = true) }
            mapStatusView?.text = "  ●  FINDING GPS"
            sourceView?.text = "WAITING FOR PHONE LOCATION FIX"
        } catch (security: SecurityException) {
            mapStatusView?.text = "  ●  LOCATION PERMISSION NEEDED"
        }
    }

    private fun showLocation(location: Location, moveCamera: Boolean) {
        val point = LatLng(location.latitude, location.longitude)
        locationMarker?.let { map?.removeAnnotation(it) }
        locationMarker = map?.addMarker(
            MarkerOptions().position(point).title("YOU ARE HERE")
                .snippet("Phone location • accuracy ±${location.accuracy.toInt()} m")
        )
        if (moveCamera) {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15.5), 650)
            scanArea()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) startLocationUpdates()
            else {
                mapStatusView?.text = "  ●  MAP ONLINE"
                sourceView?.text = "LOCATION DENIED  •  SCAN AREA STILL WORKS"
            }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() {
        try { locationManager.removeUpdates(locationListener) } catch (_: Exception) {}
        locationTracking = false
        mapView.onPause()
        super.onPause()
    }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        worker.shutdownNow()
        mapView.onDestroy()
        super.onDestroy()
    }

    private fun panelBackground(color: Int, radiusDp: Int) =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }

    private fun topParams() = FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
        setMargins(dp(13), dp(13), dp(13), 0)
    }

    private fun bottomParams() = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
        setMargins(dp(12), 0, dp(12), dp(14))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val LOCATION_REQUEST = 731
        private val KRAKOW = LatLng(50.0647, 19.9450)
        private const val OSM_STYLE = """{
          "version": 8,
          "sources": {"osm": {
            "type": "raster",
            "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
            "tileSize": 256,
            "attribution": "© OpenStreetMap contributors"
          }},
          "layers": [{"id": "osm-raster", "type": "raster", "source": "osm"}]
        }"""
    }
}
