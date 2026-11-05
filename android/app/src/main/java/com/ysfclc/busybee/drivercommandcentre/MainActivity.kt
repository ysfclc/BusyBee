package com.ysfclc.busybee.drivercommandcentre

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var tilted = true
    private var mapStatusView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 19, 27)
        window.navigationBarColor = Color.rgb(7, 19, 27)
        MapLibre.getInstance(this)
        val engineVersion = NativeBridge.engineVersion()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(7, 19, 27)) }
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        root.addView(buildHeader(engineVersion), topParams())
        root.addView(buildBottomPanel(), bottomParams())
        setContentView(root)
        mapView.getMapAsync { mapInstance ->
            map = mapInstance
            mapInstance.setStyle(Style.Builder().fromJson(OSM_STYLE)) {
                mapStatusView?.text = "  ●  MAP ONLINE"
                mapInstance.cameraPosition = CameraPosition.Builder()
                    .target(KRAKOW).zoom(12.3).tilt(38.0).build()
            }
        }
    }

    private fun buildHeader(engineVersion: String): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = panelBackground(0xE811202A.toInt(), 22)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "BUSYBEE"; setTextColor(Color.WHITE); textSize = 19f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL); letterSpacing = 0.12f
        }
        val status = TextView(this).apply {
            text = "  ●  CONNECTING"; setTextColor(Color.rgb(143, 231, 193))
            textSize = 10f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
        }
        mapStatusView = status
        row.addView(brand); row.addView(status)
        val subtitle = TextView(this).apply {
            text = "DRIVER COMMAND CENTRE  /  KRAKÓW  ·  $engineVersion"
            setTextColor(0xFF9AAEB8.toInt()); textSize = 10f; letterSpacing = 0.13f
            setPadding(0, dp(5), 0, 0)
        }
        container.addView(row); container.addView(subtitle)
        return container
    }

    private fun buildBottomPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = panelBackground(0xF20A1821.toInt(), 24)
        }
        val eyebrow = TextView(this).apply {
            text = "PHASE 02  /  LIVE BASE MAP"; setTextColor(0xFF8FE7C1.toInt())
            textSize = 10f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.14f
        }
        val headline = TextView(this).apply {
            text = "Your road picture starts here."; setTextColor(Color.WHITE)
            textSize = 20f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(7), 0, dp(4))
        }
        val detail = TextView(this).apply {
            text = "© OpenStreetMap contributors · Kraków pilot · connectors next"
            setTextColor(0xFF9AAEB8.toInt()); textSize = 12f
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        val center = actionButton("CENTER KRAKÓW", true)
        center.setOnClickListener {
            map?.animateCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(KRAKOW).zoom(12.3)
                    .tilt(if (tilted) 38.0 else 0.0).build()), 550)
        }
        val viewMode = actionButton("3D CAMERA  ON", false)
        viewMode.setOnClickListener {
            tilted = !tilted
            viewMode.text = if (tilted) "3D CAMERA  ON" else "3D CAMERA  OFF"
            map?.animateCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(map?.cameraPosition?.target ?: KRAKOW)
                    .zoom(map?.cameraPosition?.zoom ?: 12.3)
                    .tilt(if (tilted) 38.0 else 0.0)
                    .bearing(map?.cameraPosition?.bearing ?: 0.0).build()), 350)
        }
        actions.addView(center, LinearLayout.LayoutParams(0, dp(46), 1f))
        actions.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        actions.addView(viewMode, LinearLayout.LayoutParams(0, dp(46), 1f))
        panel.addView(eyebrow); panel.addView(headline); panel.addView(detail); panel.addView(actions)
        return panel
    }

    private fun actionButton(label: String, primary: Boolean): TextView =
        TextView(this).apply {
            text = label; gravity = Gravity.CENTER; textSize = 11f
            letterSpacing = 0.06f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (primary) 0xFF07131B.toInt() else Color.WHITE)
            background = panelBackground(if (primary) 0xFF8FE7C1.toInt() else 0xFF20323D.toInt(), 14)
        }

    private fun panelBackground(color: Int, radiusDp: Int) =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    private fun topParams() = FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
        setMargins(dp(14), dp(14), dp(14), 0)
    }
    private fun bottomParams() = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
        setMargins(dp(14), 0, dp(14), dp(18))
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState)
    }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }

    companion object {
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
