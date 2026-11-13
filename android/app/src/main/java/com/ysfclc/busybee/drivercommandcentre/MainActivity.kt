package com.ysfclc.busybee.drivercommandcentre

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ComponentName
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private data class RoadEvent(val title:String,val category:String,val point:LatLng,val detail:String)

class MainActivity : AppCompatActivity() {
    private lateinit var commands:DriverCommandStore
    private lateinit var mirror:DriverMirrorStore
    private lateinit var locationManager:LocationManager
    private lateinit var mapView:MapView
    private lateinit var content:FrameLayout
    private lateinit var screenHost:TabScreenHost
    private lateinit var appHeader:View
    private var threeDButton:TextView?=null
    private lateinit var nav:LinearLayout
    private val worker=Executors.newFixedThreadPool(2)
    private val uiCallbacks by lazy { LifecycleSafeUiDispatcher(this) }
    private var map:MapLibreMap?=null
    private val mapStyleState=MapStyleState()
    private var lastLocation:Location?=null
    private var tilted=true
    private var selectedTab="MAP"
    private var daylightPhase=DaylightPhase.NIGHT
    private var palette=BusyBeePalettes.forPhase(daylightPhase)
    private var activeMapStyleUri=""
    private var activeRouteOrigin:LatLng?=null
    private val themedActions=java.util.WeakHashMap<TextView,Boolean>()
    private var mapTopChrome:LinearLayout?=null
    private var mapBottomChrome:LinearLayout?=null
    private var mapDestinationField:EditText?=null
    private var rootContainer:LinearLayout?=null
    private val appearanceHandler=Handler(Looper.getMainLooper())
    private val appearanceTick=object:Runnable {
        override fun run() {
            updateTimeBasedAppearance()
            if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                appearanceHandler.postDelayed(this,15*60*1000L)
            }
        }
    }
    private val BG get()=palette.background
    private val PANEL get()=palette.panel
    private val CARD get()=palette.card
    private val WHITE get()=palette.primaryText
    private val MUTED get()=palette.mutedText
    private val MINT get()=palette.accent
    private val AMBER get()=palette.warning
    private var roadEvents:List<RoadEvent> = emptyList()
    private val eventMarkers=mutableListOf<Marker>()
    private val placeMarkers=mutableListOf<Marker>()
    private var mapPlaces:List<MapPlaceFeature> = emptyList()
    private var lastPlaceFetchCenter:LatLng? = null
    private var lastPlaceFetchAt=0L
    private var placeFetchInProgress=false
    private var lastRenderedPlaceZoom=-1.0
    private var vehicleIcon:Icon?=null
    private val reportMarkers=mutableListOf<Marker>()
    private val routeMarkers=mutableListOf<Marker>()
    private var routePolyline:Polyline?=null
    private var routeSummary:TextView?=null
    private var routeActions:LinearLayout?=null
    private var startNavigationAction:TextView?=null
    private var stopNavigationAction:TextView?=null
    private var routeDestinationInput:EditText?=null
    private var activeRoute:RoutePlan?=null
    private var driverMarker:Marker?=null
    private var followLocation=false
    private var centeredOnFirstFix=false
    private var navigating=false
    private var navigationStepIndex=0
    private var rerouteInProgress=false
    private var offRouteReadings=0
    private var lastRerouteAt=0L
    private var textToSpeech:TextToSpeech?=null
    private var speechReady=false
    private var navigationInstruction:TextView?=null
    private var navigationStatus:TextView?=null
    private var vehicleProfileSummary:TextView?=null
    private var mapStatus:TextView?=null
    private var mapSummary:TextView?=null
    private var weatherSummary="Weather feed not checked"
    private var weatherText:TextView?=null
    private var pendingCapturePlatform = "Uber"
    private val selectedLayers= mutableSetOf("Roadworks","Hazards","Driver reports")
    private val navButtons=mutableMapOf<String,TextView>()
    private val locationListener=object:LocationListener {
        override fun onLocationChanged(location:Location) {
            lastLocation=location
            // A location callback can already be queued when onPause() removes updates.
            // Keep the fix, but do not mutate the paused MapLibre view.
            if(!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            updateTimeBasedAppearance(location)
            updateDriverLocation(location)
        }
    }
    private val documentPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)importDocument(uri)}
    private val nativeCaptureLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        val data = result.data
        if(result.resultCode==Activity.RESULT_OK && data!=null) {
            val serviceIntent=Intent(this,DriverAppCaptureService::class.java).apply {
                action=DriverAppCaptureService.ACTION_START
                putExtra(DriverAppCaptureService.EXTRA_RESULT_CODE,result.resultCode)
                putExtra(DriverAppCaptureService.EXTRA_RESULT_DATA,data)
                putExtra(DriverAppCaptureService.EXTRA_PLATFORM,pendingCapturePlatform)
            }
            try {
                ContextCompat.startForegroundService(this,serviceIntent)
                Toast.makeText(this,"Screen capture started. Switch to $pendingCapturePlatform Driver and open the screen you want BusyBee to read.",Toast.LENGTH_LONG).show()
                openPlatform(pendingCapturePlatform)
            } catch(error:Exception) {
                Toast.makeText(this,"Could not start native capture: ${error.javaClass.simpleName}",Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this,"Screen capture was not granted. Nothing was read.",Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        CrashDiagnostics.install(this)
        CrashDiagnostics.record(this, "activity:onCreate")
        commands=DriverCommandStore(this);mirror=DriverMirrorStore(this)
        locationManager=getSystemService(LOCATION_SERVICE) as LocationManager
        textToSpeech=TextToSpeech(this) { status ->
            uiCallbacks.post {
                speechReady=status==TextToSpeech.SUCCESS
                if(speechReady) {
                    val engine=textToSpeech ?: return@post
                    val language=engine.setLanguage(Locale.UK)
                    if(language==TextToSpeech.LANG_MISSING_DATA || language==TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.getDefault())
                    }
                }
            }
        }
        updateTimeBasedAppearance(force=true)
        MapLibre.getInstance(this)
        applySystemBars()
        mapView=MapView(this);mapView.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(BG)}
        appHeader=buildHeader()
        rootContainer=root
        root.addView(appHeader)
        content=FrameLayout(this);root.addView(content,LinearLayout.LayoutParams(-1,0,1f))
        nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(7),dp(7),dp(7),dp(9));setBackgroundColor(PANEL)}
        root.addView(nav);setContentView(root)
        buildNavigation()
        // Keep MapLibre's native view attached for the full Activity lifetime.
        // Reparenting it on every tab change can tear down/recreate its GL surface.
        screenHost=TabScreenHost(content,buildMapScreen())
        showTab("MAP")
        intent.getStringExtra(DriverAlertManager.EXTRA_OPEN_TAB)?.let { requested ->
            if(requested in setOf("OFFERS","EARNINGS","DRIVER HUB","SOURCES")) showTab(requested)
        }
        mapView.getMapAsync { instance ->
            uiCallbacks.post {
                CrashDiagnostics.record(this,"map:ready")
                map=instance
                instance.addOnCameraIdleListener {
                    instance.cameraPosition.target?.let { center ->
                        maybeRefreshMapPlaces(center)
                        renderMapPlaces()
                    }
                }
                val initialStyleUri=palette.mapStyleUri
                activeMapStyleUri=initialStyleUri
                instance.setStyle(Style.Builder().fromUri(initialStyleUri)) { loadedStyle ->
                    install3DBuildings(loadedStyle)
                    if(activeMapStyleUri==initialStyleUri) {
                        uiCallbacks.post {
                            mapStyleState.markReady()
                            CrashDiagnostics.record(this,"map:style-loaded")
                            activateMapAfterStyleIfResumed()
                        }
                    }
                }
            }
        }
    }

    /** Activates MapLibre only when its style is ready and this Activity is RESUMED. */
    private fun activateMapAfterStyleIfResumed() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val activeMap = map ?: return
        if (!mapStyleState.claimActivation(isResumed = true, hasMap = true)) return

        val initialTarget = lastLocation?.let { LatLng(it.latitude, it.longitude) } ?: KRAKOW
        activeMap.cameraPosition = CameraPosition.Builder().target(initialTarget).zoom(15.0).tilt(48.0).build()
        lastLocation?.let(::updateDriverLocation)
        renderRoadEvents()
        renderMapPlaces()
        lastLocation?.let { maybeRefreshMapPlaces(LatLng(it.latitude,it.longitude),force=true) }
        mapStatus?.text = "● MAP ONLINE"
        refreshRoadIntelligence()
        requestLocation()
    }

    private fun updateTimeBasedAppearance(location:Location?=lastLocation,force:Boolean=false) {
        val phase=SolarLighting.phase(location?.latitude,location?.longitude,System.currentTimeMillis())
        if(!force && phase==daylightPhase) {
            if(map!=null && activeMapStyleUri!=palette.mapStyleUri) reloadMapStyle()
            return
        }
        daylightPhase=phase
        palette=BusyBeePalettes.forPhase(phase)
        applySystemBars()
        rootContainer?.setBackgroundColor(BG)
        if(::appHeader.isInitialized) {
            rootContainer?.let { root ->
                val oldIndex=root.indexOfChild(appHeader)
                val oldParams=appHeader.layoutParams
                val oldVisibility=appHeader.visibility
                if(oldIndex>=0) {
                    root.removeView(appHeader)
                    appHeader=buildHeader()
                    appHeader.visibility=oldVisibility
                    root.addView(appHeader,oldIndex,oldParams)
                }
            }
        }
        if(::nav.isInitialized) nav.setBackgroundColor(PANEL)
        refreshActionPalettes()
        if(::screenHost.isInitialized) {
            if(selectedTab=="MAP") updateMapChromePalette() else showTab(selectedTab)
        }
        if(map!=null && activeMapStyleUri!=palette.mapStyleUri) reloadMapStyle()
    }

    private fun applySystemBars() {
        window.statusBarColor=BG
        window.navigationBarColor=PANEL
        val lightBars=daylightPhase==DaylightPhase.DAY
        window.decorView.systemUiVisibility=if(lightBars) {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else 0
    }

    private fun refreshActionPalettes() {
        themedActions.entries.toList().forEach { (view,primary) ->
            applyActionPalette(view,primary)
        }
    }

    private fun applyActionPalette(view:TextView,primary:Boolean) {
        view.setTextColor(if(primary)palette.actionText else palette.primaryText)
        view.background=GradientDrawable().apply {
            setColor(if(primary)palette.accent else palette.secondary)
            cornerRadius=dp(11).toFloat()
            if(!primary) setStroke(dp(1),palette.stroke)
        }
    }

    private fun updateMapChromePalette() {
        mapTopChrome?.background=rounded(palette.overlay,18)
        mapBottomChrome?.background=rounded(palette.overlay,18)
        mapDestinationField?.apply {
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            background=rounded(palette.field,11)
        }
        mapStatus?.setTextColor(MINT)
        weatherText?.setTextColor(MUTED)
        mapSummary?.setTextColor(MUTED)
        routeSummary?.setTextColor(MUTED)
        navigationStatus?.setTextColor(AMBER)
        navigationInstruction?.setTextColor(WHITE)
    }

    private fun clearMapAnnotations() {
        val active=map ?: return
        eventMarkers.forEach { active.removeAnnotation(it) };eventMarkers.clear()
        placeMarkers.forEach { active.removeAnnotation(it) };placeMarkers.clear()
        lastRenderedPlaceZoom=-1.0
        reportMarkers.forEach { active.removeAnnotation(it) };reportMarkers.clear()
        routeMarkers.forEach { active.removeAnnotation(it) };routeMarkers.clear()
        routePolyline?.let { active.removeAnnotation(it) };routePolyline=null
        driverMarker?.let { active.removeAnnotation(it) };driverMarker=null
    }

    private fun reloadMapStyle() {
        if(!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val active=map ?: return
        val nextStyle=palette.mapStyleUri
        if(nextStyle==activeMapStyleUri) return
        val camera=active.cameraPosition
        clearMapAnnotations()
        activeMapStyleUri=nextStyle
        mapStyleState.markLoading()
        try {
            active.setStyle(Style.Builder().fromUri(nextStyle)) { loadedStyle ->
                install3DBuildings(loadedStyle)
                if(activeMapStyleUri==nextStyle) {
                    uiCallbacks.post {
                        mapStyleState.markReady()
                        camera?.let { active.cameraPosition=it }
                        renderRoadEvents()
                        renderMapPlaces()
                        lastLocation?.let(::updateDriverLocation)
                        redrawActiveRoute()
                        mapStatus?.text=if(navigating)"● NAVIGATION GPS ACTIVE" else "● MAP "+daylightPhase.name
                    }
                }
            }
        } catch(error:Exception) {
            activeMapStyleUri=""
            mapStatus?.text="● MAP STYLE UNAVAILABLE"
            Log.w("BusyBee","Could not switch map style to $nextStyle",error)
        }
    }

    private fun redrawActiveRoute() {
        val route=activeRoute ?: return
        val active=map ?: return
        if(!mapStyleState.isReady) return
        routePolyline=active.addPolyline(PolylineOptions().addAll(route.points).color(MINT).width(5f))
        val origin=activeRouteOrigin ?: lastLocation?.let { LatLng(it.latitude,it.longitude) }
        origin?.let { point ->
            active.addMarker(MarkerOptions().position(point).title("Route starts here"))?.let { routeMarkers.add(it) }
        }
        active.addMarker(MarkerOptions().position(route.destination).title("Destination").snippet(route.destinationName))
            ?.let { routeMarkers.add(it) }
    }

    private fun buildHeader():View {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(17),dp(12),dp(17),dp(10));setBackgroundColor(BG)}
        val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        row.addView(label("BUSYBEE",21f,WHITE,true).apply{letterSpacing=.12f})
        row.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
        row.addView(label("DRIVER COMMAND CENTRE",9f,MINT,true))
        val diagnostics=label("DIAG",8f,AMBER,true).apply {
            gravity=Gravity.CENTER
            setPadding(dp(8),dp(6),dp(8),dp(6))
            background=rounded(CARD,8)
            setOnClickListener {
                safeUiAction("copy diagnostics") {
                    CrashDiagnostics.record(this@MainActivity,"diagnostics:copy")
                    val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("BusyBee diagnostics",CrashDiagnostics.snapshot(this@MainActivity)))
                    Toast.makeText(this@MainActivity,"Diagnostics copied. Paste the report into the chat.",Toast.LENGTH_LONG).show()
                }
            }
        }
        row.addView(diagnostics,LinearLayout.LayoutParams(-2,dp(30)).apply{marginStart=dp(8)})
        box.addView(row)
        box.addView(label("ROAD INTELLIGENCE  ·  OFFER DESK  ·  DRIVER LIFE",9f,MUTED,true).apply{setPadding(0,dp(3),0,0);letterSpacing=.06f})
        return box
    }

    private fun buildNavigation() {
        nav.removeAllViews()
        listOf("MAP","OFFERS","EARNINGS","DRIVER HUB","SOURCES").forEach { tab ->
            val glyph=when(tab) {
                "MAP" -> "⌖"
                "OFFERS" -> "↗"
                "EARNINGS" -> "▥"
                "DRIVER HUB" -> "◉"
                else -> "◇"
            }
            val b=label("$glyph\\n$tab",9f,MUTED,true).apply{
                gravity=Gravity.CENTER
                setLineSpacing(-dp(2).toFloat(),1f)
                setPadding(dp(3),dp(7),dp(3),dp(7))
                setOnClickListener{safeUiAction("open $tab"){showTab(tab)}}
            }
            navButtons[tab]=b;nav.addView(b,LinearLayout.LayoutParams(0,-1,1f))
        }
    }

    private fun showTab(tab:String) {
        CrashDiagnostics.record(this,"tab:$tab")
        val mapSelected = tab=="MAP"
        appHeader.visibility = if(mapSelected) View.GONE else View.VISIBLE
        nav.visibility = if(mapSelected) View.GONE else View.VISIBLE
        selectedTab=tab
        nav.setBackgroundColor(PANEL)
        navButtons.forEach{(key,b)->b.setTextColor(if(key==tab)MINT else MUTED);b.background=rounded(if(key==tab)palette.selected else Color.TRANSPARENT,11)}
        if(mapSelected) {
            screenHost.showMap()
            return
        }
        val view=when(tab) {
            "OFFERS"->buildOffersScreen()
            "EARNINGS"->buildEarningsScreen()
            "DRIVER HUB"->buildDriverHubScreen()
            else->buildSourcesScreen()
        }
        screenHost.show(view)
    }

    private fun showMainMenu() {
        val tabs=arrayOf("OFFERS","EARNINGS","DRIVER HUB","SOURCES")
        AlertDialog.Builder(this)
            .setTitle("BusyBee")
            .setItems(tabs) { _, index -> showTab(tabs[index]) }
            .show()
    }

    private fun showMapLayersDialog() {
        val names=arrayOf("Roadworks","Hazards","Driver reports")
        val checked=BooleanArray(names.size) { selectedLayers.contains(names[it]) }
        AlertDialog.Builder(this)
            .setTitle("Map layers")
            .setMultiChoiceItems(names,checked) { _, which, enabled ->
                if(enabled) selectedLayers.add(names[which]) else selectedLayers.remove(names[which])
                renderRoadEvents()
            }
            .setPositiveButton("Done",null)
            .show()
    }

    private fun buildMapScreen():View {
        val frame=FrameLayout(this)
        frame.addView(mapView,FrameLayout.LayoutParams(-1,-1))

        // Full-bleed map: no persistent app header or tab bar. Controls float
        // above the map and the route dock stays compact until a route exists.
        val top=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(9),dp(7),dp(8),dp(7))
            background=rounded(palette.overlay,18)
        }
        top.addView(action("☰",false){showMainMenu()},LinearLayout.LayoutParams(dp(40),dp(38)))
        val brand=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(9),0,0,0) }
        brand.addView(label("BUSYBEE",12f,WHITE,true))
        mapStatus=label("● CONNECTING",8f,MINT,true).apply { setPadding(0,dp(2),0,0) }
        brand.addView(mapStatus)
        weatherText=label(weatherSummary,8f,MUTED,false).apply { setPadding(0,dp(2),0,0); maxLines=1 }
        brand.addView(weatherText)
        mapSummary=label("OSM places · buildings · entrances",8f,MUTED,false).apply { setPadding(0,dp(2),0,0); maxLines=1 }
        brand.addView(mapSummary)
        top.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
        top.addView(action("LAYERS",false){showMapLayersDialog()},LinearLayout.LayoutParams(dp(62),dp(36)))
        top.addView(action(if(tilted)"3D" else "2D",false){
            if(!mapStyleState.isReady) mapStatus?.text="● LOADING"
            else {
                tilted=!tilted
                val p=map?.cameraPosition
                if(p!=null)map?.animateCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(p.target?:KRAKOW).zoom(p.zoom).bearing(p.bearing).tilt(if(tilted)55.0 else 0.0).build()
                ),350)
                threeDButton?.text=if(tilted)"3D" else "2D"
            }
        },LinearLayout.LayoutParams(dp(44),dp(36)).apply { marginStart=dp(5) })
        threeDButton=top.getChildAt(3) as TextView
        mapTopChrome=top
        frame.addView(top,FrameLayout.LayoutParams(-1,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            setMargins(dp(10),dp(10),dp(10),0)
        })

        val side=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER }
        side.addView(action("◎",true){
            followLocation=true
            val fix=lastLocation?.takeIf { System.currentTimeMillis()-it.time in 0..120000L }
            if(fix!=null) updateDriverLocation(fix) else requestLocation()
        },LinearLayout.LayoutParams(dp(44),dp(44)))
        side.addView(action("＋",false){reportRoadHazard()},LinearLayout.LayoutParams(dp(44),dp(44)).apply { topMargin=dp(7) })
        frame.addView(side,FrameLayout.LayoutParams(-2,-2,Gravity.END or Gravity.CENTER_VERTICAL).apply {
            setMargins(0,0,dp(10),dp(18))
        })

        val bottom=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(9),dp(8),dp(9),dp(8))
            background=rounded(palette.overlay,18)
        }
        routeDestinationInput=EditText(this).apply {
            hint="Where to?"
            setSingleLine(true);textSize=13f;setTextColor(WHITE);setHintTextColor(MUTED)
            setPadding(dp(12),dp(7),dp(9),dp(7));background=rounded(palette.field,11)
            layoutParams=LinearLayout.LayoutParams(0,dp(42),1f)
        }
        mapDestinationField=routeDestinationInput
        val searchRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        searchRow.addView(routeDestinationInput)
        searchRow.addView(action("GO",true){planRoute(routeDestinationInput?.text?.toString().orEmpty())},
            LinearLayout.LayoutParams(dp(54),dp(42)).apply { marginStart=dp(6) })
        bottom.addView(searchRow)
        routeSummary=label("Ready · route estimates exclude live traffic",9f,MUTED,false).apply {
            setPadding(dp(2),dp(6),0,0); maxLines=1; visibility=View.GONE
        }
        bottom.addView(routeSummary)
        navigationStatus=label("GPS · NAVIGATION OFF",8f,AMBER,true).apply {
            setPadding(dp(2),dp(5),0,0); maxLines=1; visibility=View.GONE
        }
        bottom.addView(navigationStatus)
        navigationInstruction=label("",12f,WHITE,true).apply {
            setPadding(dp(2),dp(5),0,0); maxLines=1; visibility=View.GONE
        }
        bottom.addView(navigationInstruction)

        routeActions=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
            visibility=View.GONE
        }
        val startAction=action("START",true){startNavigation()}.apply {
            layoutParams=LinearLayout.LayoutParams(0,dp(36),1f)
        }
        val stopAction=action("STOP",false){stopNavigation("Navigation stopped",announce=false)}.apply {
            layoutParams=LinearLayout.LayoutParams(0,dp(36),1f); visibility=View.GONE
        }
        startNavigationAction=startAction
        stopNavigationAction=stopAction
        routeActions?.addView(startAction)
        routeActions?.addView(stopAction,LinearLayout.LayoutParams(0,dp(36),1f).apply { marginStart=dp(6) })
        routeActions?.addView(action("MAPS",false){openExternalNavigation()},
            LinearLayout.LayoutParams(0,dp(36),1f).apply { marginStart=dp(6) })
        bottom.addView(routeActions,LinearLayout.LayoutParams(-1,dp(36)).apply { topMargin=dp(7) })
        mapBottomChrome=bottom
        frame.addView(bottom,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            setMargins(dp(10),0,dp(10),dp(10))
        })
        return frame
    }


    private fun promptOtherPlatformCapture() {
        val name=field("Driver app / platform name","FREENOW")
        AlertDialog.Builder(this)
            .setTitle("Connect another driver app")
            .setView(name)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Continue") { _,_ ->
                val platform=name.text.toString().trim()
                if(platform.length<2) {
                    Toast.makeText(this,"Enter the platform name.",Toast.LENGTH_SHORT).show()
                } else {
                    launchDriverScreenCapture(platform)
                }
            }
            .show()
    }

    private fun launchDriverScreenCapture(platform:String) {
        val normalized=platform.trim().take(60)
        if(normalized.length<2) return
        pendingCapturePlatform=normalized
        AlertDialog.Builder(this)
            .setTitle("Capture only $normalized")
            .setMessage("When Android opens its screen-sharing picker, select only the $normalized Driver app window. If Android offers only full-screen capture, cancel. BusyBee reads visible text on this device, saves no screenshots, and keeps capture active only until you stop it.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Continue") { _,_ ->
                try {
                    val manager=getSystemService(MediaProjectionManager::class.java)
                    nativeCaptureLauncher.launch(manager.createScreenCaptureIntent())
                } catch(error:Exception) {
                    Toast.makeText(this,"Android screen capture is unavailable: ${error.javaClass.simpleName}",Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun stopDriverScreenCapture() {
        stopService(Intent(this,DriverAppCaptureService::class.java).apply {
            action=DriverAppCaptureService.ACTION_STOP
        })
        Toast.makeText(this,"Native screen capture stopped.",Toast.LENGTH_SHORT).show()
    }

    private fun planRoute(query:String) {
        if(!mapStyleState.isReady) {
            routeSummary?.text="Map is still loading. Try again in a moment."
            routeSummary?.visibility=View.VISIBLE
            return
        }
        if(query.isBlank()) {
            Toast.makeText(this,"Enter a destination first.",Toast.LENGTH_SHORT).show()
            return
        }
        val fix=lastLocation?.takeIf { System.currentTimeMillis()-it.time in 0..120000L }
        val origin=fix?.let { LatLng(it.latitude,it.longitude) }
        if(origin==null) {
            routeSummary?.text="Waiting for a fresh GPS fix. Tap LOCATE and try again."
            routeSummary?.visibility=View.VISIBLE
            requestLocation()
            return
        }
        routeSummary?.text="Searching destinations near your current location…"
        routeSummary?.visibility=View.VISIBLE
        worker.execute {
            try {
                val candidates=RoutePlanner.searchDestinations(query,origin)
                require(candidates.isNotEmpty()) { "No matching destination was found in Poland" }
                uiCallbacks.post {
                    val chooseDestination: (Int)->Unit = { selected ->
                        val candidate=candidates[selected]
                        routeSummary?.text="Calculating road routes to ${candidate.displayName}…"
                        routeSummary?.visibility=View.VISIBLE
                        worker.execute {
                            try {
                                val plans=RoutePlanner.planAlternatives(origin,candidate)
                                uiCallbacks.post {
                                    val labels=plans.mapIndexed { index, plan ->
                                        val kind=if(index==0)"FASTEST" else "ALTERNATIVE $index"
                                        String.format(Locale.US,"%s · %.1f km · %.0f min",kind,plan.distanceKm,plan.durationMinutes)
                                    }.toTypedArray()
                                    if(plans.size==1) displayRoute(plans.first(),origin,"FASTEST")
                                    else AlertDialog.Builder(this)
                                        .setTitle("Choose route · ETA excludes live traffic")
                                        .setItems(labels) { _, routeIndex ->
                                            displayRoute(plans[routeIndex],origin,if(routeIndex==0)"FASTEST" else "ALTERNATIVE $routeIndex")
                                        }
                                        .setNegativeButton("Cancel",null)
                                        .show()
                                }
                            } catch(error:Exception) {
                                uiCallbacks.post {
                                    routeSummary?.text="Route unavailable: ${error.javaClass.simpleName}. Check connection and try again."
                                    mapStatus?.text="● ROUTING UNAVAILABLE"
                                }
                            }
                        }
                    }
                    if(candidates.size==1) chooseDestination(0)
                    else AlertDialog.Builder(this)
                        .setTitle("Choose destination")
                        .setItems(candidates.map { it.displayName }.toTypedArray()) { _, selected -> chooseDestination(selected) }
                        .setNegativeButton("Cancel",null)
                        .show()
                }
            } catch(error:Exception) {
                uiCallbacks.post {
                    routeSummary?.text="Destination search failed: ${error.javaClass.simpleName}"
                    mapStatus?.text="● SEARCH UNAVAILABLE"
                }
            }
        }
    }

    private fun displayRoute(plan:RoutePlan,origin:LatLng,kind:String) {
        if(!mapStyleState.isReady || isFinishing || isDestroyed) return
        stopNavigation("Route ready",announce=false)
        routePolyline?.let { map?.removeAnnotation(it) }
        routeMarkers.forEach { map?.removeAnnotation(it) };routeMarkers.clear()
        routePolyline=map?.addPolyline(PolylineOptions().addAll(plan.points).color(MINT).width(5f))
        activeRouteOrigin=origin
        map?.addMarker(MarkerOptions().position(origin).title("Route starts here"))?.let{routeMarkers.add(it)}
        map?.addMarker(MarkerOptions().position(plan.destination).title("Destination").snippet(plan.destinationName))?.let{routeMarkers.add(it)}
        activeRoute=plan
        navigationStepIndex=0
        routeSummary?.text=String.format(Locale.US,"%s · %.1f km · %.0f min · no live traffic",kind,plan.distanceKm,plan.durationMinutes)
        routeSummary?.visibility=View.VISIBLE
        navigationInstruction?.text=plan.steps.firstOrNull { it.type!="depart" }?.instruction ?: "Follow the highlighted route."
        navigationStatus?.text=if(plan.steps.isEmpty())"ROUTE READY · TURN LIST UNAVAILABLE" else "ROUTE READY · ${plan.steps.size} MANOEUVRES"
        navigationStatus?.visibility=View.GONE
        navigationInstruction?.visibility=View.GONE
        routeActions?.visibility=View.VISIBLE
        startNavigationAction?.visibility=View.VISIBLE
        stopNavigationAction?.visibility=View.GONE
        mapStatus?.text="● ROUTE READY"
        val bounds=org.maplibre.android.geometry.LatLngBounds.Builder().includes(plan.points).build()
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,dp(48)),650)
    }

    private fun startNavigation() {
        val route=activeRoute
        if(route==null) {
            Toast.makeText(this,"Plan a route first.",Toast.LENGTH_SHORT).show()
            return
        }
        val fix=lastLocation?.takeIf { System.currentTimeMillis()-it.time in 0..120000L }
        if(fix==null) {
            Toast.makeText(this,"Waiting for a fresh GPS fix. Keep BusyBee open until your position appears.",Toast.LENGTH_LONG).show()
            requestLocation()
            return
        }
        if(route.steps.isEmpty()) {
            Toast.makeText(this,"This route has no turn instructions. Plan it again later.",Toast.LENGTH_LONG).show()
            return
        }
        navigating=true
        followLocation=true
        navigationStatus?.visibility=View.VISIBLE
        navigationInstruction?.visibility=View.VISIBLE
        startNavigationAction?.visibility=View.GONE
        stopNavigationAction?.visibility=View.VISIBLE
        routeActions?.visibility=View.VISIBLE
        offRouteReadings=0
        val progress=RoutePlanner.progressMeters(LatLng(fix.latitude,fix.longitude),route.points)
        navigationStepIndex=route.steps.indexOfFirst {
            it.type!="depart" && it.type!="arrive" && it.distanceAlongRouteMeters > progress+25.0
        }.let { if(it<0) route.steps.lastIndex else it }
        startLocationUpdates()
        updateNavigationCamera(fix)
        val next=route.steps.getOrNull(navigationStepIndex)
        if(next!=null) {
            val distance=(next.distanceAlongRouteMeters-progress).coerceAtLeast(0.0)
            val announcement=if(distance>70.0) "In ${distance.toInt()} metres, ${next.instruction}" else next.instruction
            navigationInstruction?.text=announcement
            navigationStatus?.text=String.format(Locale.US,"NAVIGATION ACTIVE · %.1f KM REMAINING",((route.distanceKm*1000-progress).coerceAtLeast(0.0))/1000.0)
            speak(announcement)
        } else {
            navigationInstruction?.text="Follow the highlighted route."
            navigationStatus?.text="NAVIGATION ACTIVE"
            speak("Follow the highlighted route.")
        }
    }

    private fun stopNavigation(message:String,announce:Boolean=true) {
        val wasNavigating=navigating
        navigating=false
        followLocation=false
        rerouteInProgress=false
        offRouteReadings=0
        navigationStatus?.text=message.uppercase(Locale.getDefault())
        navigationStatus?.visibility=View.GONE
        navigationInstruction?.visibility=View.GONE
        if(activeRoute!=null) {
            routeSummary?.visibility=View.VISIBLE
            routeActions?.visibility=View.VISIBLE
            startNavigationAction?.visibility=View.VISIBLE
            stopNavigationAction?.visibility=View.GONE
        } else {
            routeActions?.visibility=View.GONE
        }
        if(announce && wasNavigating) speak(message)
    }

    private fun speak(message:String) {
        if(!speechReady) return
        runCatching { textToSpeech?.speak(message,TextToSpeech.QUEUE_FLUSH,null,"busybee-nav-${System.currentTimeMillis()}") }
    }

    private fun updateDriverLocation(location:Location) {
        lastLocation=location
        if(!mapStyleState.isReady || isFinishing || isDestroyed) return
        val point=LatLng(location.latitude,location.longitude)
        mapStatus?.text=if(navigating)"● NAVIGATION GPS ACTIVE" else "● LOCATION LIVE"
        val icon=vehicleIcon ?: IconFactory.getInstance(this).fromBitmap(MapMarkerArtwork.vehicle(this)).also { vehicleIcon=it }
        val marker=driverMarker
        if(marker!=null) {
            marker.position=point
            marker.icon=icon
        } else {
            driverMarker=map?.addMarker(
                MarkerOptions().position(point).title("Your vehicle").snippet("Live Android GPS · heading follows road travel").icon(icon)
            )
        }
        maybeRefreshMapPlaces(point)
        if(!centeredOnFirstFix || followLocation || navigating) updateNavigationCamera(location)
        centeredOnFirstFix=true
        if(navigating) updateNavigation(location)
    }

    /**
     * Forward-facing navigation camera. The camera looks ahead along the GPS
     * course so the vehicle sits lower in the viewport instead of being pinned
     * to the centre of a flat overview map.
     */
    private fun updateNavigationCamera(location:Location) {
        val point=LatLng(location.latitude,location.longitude)
        val previousBearing=map?.cameraPosition?.bearing ?: 0.0
        val bearing=if(location.hasBearing() && location.speed>1.0f) location.bearing.toDouble() else previousBearing
        val cameraTarget=if(navigating && tilted) NavigationCameraMath.aheadOf(point,bearing,if(location.speed>2.0f)48.0 else 26.0) else point
        val zoom=if(navigating)18.2 else 15.5
        val tilt=if(navigating && tilted)58.0 else if(tilted)42.0 else 0.0
        map?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(cameraTarget).zoom(zoom).bearing(bearing).tilt(tilt).build()
        ),650)
    }

    private fun install3DBuildings(style:Style) {
        if(style.getSource("openmaptiles")==null || style.getLayer("busybee-3d-buildings")!=null || style.getLayer("building-3d")!=null) return
        runCatching {
            val layer=FillExtrusionLayer("busybee-3d-buildings","openmaptiles").apply {
                sourceLayer="building"
                minZoom=15f
                setFilter(Expression.all(Expression.has("render_height"),Expression.has("render_min_height")))
                val buildingColor=when(daylightPhase) {
                    DaylightPhase.DAY -> Color.rgb(183,202,190)
                    DaylightPhase.DUSK -> Color.rgb(151,132,111)
                    DaylightPhase.NIGHT -> Color.rgb(49,78,91)
                }
                setProperties(
                    PropertyFactory.fillExtrusionColor(buildingColor),
                    PropertyFactory.fillExtrusionHeight(Expression.get("render_height")),
                    PropertyFactory.fillExtrusionBase(Expression.get("render_min_height")),
                    PropertyFactory.fillExtrusionOpacity(0.88f)
                )
            }
            val firstLabelLayer=style.layers.firstOrNull { it is SymbolLayer }?.id
            if(firstLabelLayer!=null) style.addLayerBelow(layer,firstLabelLayer) else style.addLayer(layer)
        }.onFailure { error ->
            Log.w("BusyBee","3D building layer unavailable for this basemap style",error)
        }
    }

    private fun updateMapSummary() {
        val buildings=mapPlaces.count { it.kind==MapPlaceKind.BUILDING }
        val entrances=mapPlaces.count { it.kind==MapPlaceKind.ENTRANCE }
        mapSummary?.text="${roadEvents.size} road observations · $buildings buildings · $entrances entrances"
    }

    private fun maybeRefreshMapPlaces(center:LatLng,force:Boolean=false) {
        if(placeFetchInProgress || !mapStyleState.isReady || isFinishing || isDestroyed) return
        val now=System.currentTimeMillis()
        val previous=lastPlaceFetchCenter
        if(!force && previous!=null && now-lastPlaceFetchAt<240000L &&
            RoutePlanner.distanceMeters(center,previous)<450.0) return
        placeFetchInProgress=true
        lastPlaceFetchAt=now
        lastPlaceFetchCenter=center
        worker.execute {
            val result=runCatching { MapPlaceLookup.fetch(center.latitude,center.longitude) }
            uiCallbacks.post {
                placeFetchInProgress=false
                if(result.isSuccess) {
                    mapPlaces=result.getOrDefault(emptyList())
                    renderMapPlaces(force=true)
                    updateMapSummary()
                } else {
                    Log.w("BusyBee","Building/entrance lookup unavailable",result.exceptionOrNull())
                }
            }
        }
    }

    private fun renderMapPlaces(force:Boolean=false) {
        val active=map ?: return
        if(!mapStyleState.isReady) return
        val zoom=active.cameraPosition.zoom
        if(!force && lastRenderedPlaceZoom>=0.0 && kotlin.math.abs(zoom-lastRenderedPlaceZoom)<0.25) return
        lastRenderedPlaceZoom=zoom
        placeMarkers.forEach { active.removeAnnotation(it) }
        placeMarkers.clear()
        if(zoom<15.5) return
        val night=daylightPhase==DaylightPhase.NIGHT
        val factory=IconFactory.getInstance(this)
        val buildings=mapPlaces.filter { it.kind==MapPlaceKind.BUILDING }.take(if(zoom>=17.0)55 else 24)
        buildings.forEach { feature ->
            val icon=factory.fromBitmap(MapMarkerArtwork.buildingLabel(this,feature.label,night))
            active.addMarker(MarkerOptions().position(feature.point).title(feature.label).snippet(feature.detail).icon(icon))
                ?.let { placeMarkers.add(it) }
        }
        if(zoom>=17.0) {
            mapPlaces.filter { it.kind==MapPlaceKind.ENTRANCE }.take(36).forEach { feature ->
                val icon=factory.fromBitmap(MapMarkerArtwork.entrance(this))
                active.addMarker(MarkerOptions().position(feature.point).title(feature.label).snippet(feature.detail).icon(icon))
                    ?.let { placeMarkers.add(it) }
            }
        }
    }

    private fun updateNavigation(location:Location) {
        val route=activeRoute ?: return
        val current=LatLng(location.latitude,location.longitude)
        val progress=RoutePlanner.progressMeters(current,route.points)
        val remaining=(route.distanceKm*1000.0-progress).coerceAtLeast(0.0)
        if(remaining<35.0 || RoutePlanner.distanceMeters(current,route.destination)<35.0) {
            stopNavigation("You have arrived",announce=false)
            speak("You have arrived at your destination.")
            return
        }

        val next=route.steps.getOrNull(navigationStepIndex)
        if(next!=null) {
            val metersToTurn=next.distanceAlongRouteMeters-progress
            if(metersToTurn < -40.0) {
                navigationStepIndex++
            } else if(metersToTurn<=450.0) {
                val prefix=if(metersToTurn>70.0) "In ${metersToTurn.toInt()} metres, " else ""
                val message=prefix+next.instruction
                navigationInstruction?.text=message
                navigationStatus?.text=String.format(Locale.US,"NEXT MANOEUVRE · %.0f M · %.1f KM REMAINING",metersToTurn.coerceAtLeast(0.0),remaining/1000.0)
                speak(message)
                navigationStepIndex++
            }
        } else {
            navigationInstruction?.text="Continue along the highlighted route."
            navigationStatus?.text=String.format(Locale.US,"%.1f KM REMAINING · ESTIMATES EXCLUDE LIVE TRAFFIC",remaining/1000.0)
        }

        val distanceFromRoute=RoutePlanner.distanceToRouteMeters(current,route.points)
        if(distanceFromRoute>75.0) offRouteReadings++ else offRouteReadings=0
        val now=System.currentTimeMillis()
        if(offRouteReadings>=3 && !rerouteInProgress && now-lastRerouteAt>20000L) {
            rerouteInProgress=true
            lastRerouteAt=now
            navigationStatus?.text="OFF ROUTE · RECALCULATING"
            speak("You are off the route. Recalculating.")
            worker.execute {
                try {
                    val updated=RoutePlanner.planToCoordinates(current,route.destination,route.destinationName).first()
                    uiCallbacks.post {
                        if(navigating) {
                            displayRoute(updated,current,"REROUTED")
                            navigating=true
                            followLocation=true
                            navigationStepIndex=updated.steps.indexOfFirst { it.type!="depart" && it.type!="arrive" }.let { if(it<0) updated.steps.lastIndex else it }
                            navigationStatus?.text="ROUTE UPDATED · CONTINUE"
                            speak("Route updated. ${updated.steps.getOrNull(navigationStepIndex)?.instruction ?: "Continue to destination."}")
                        }
                        rerouteInProgress=false
                    }
                } catch(error:Exception) {
                    uiCallbacks.post {
                        rerouteInProgress=false
                        offRouteReadings=0
                        navigationStatus?.text="REROUTE FAILED · FOLLOW THE MAP OR STOP"
                    }
                }
            }
        }
    }

    private fun openExternalNavigation() {
        val route=activeRoute
        if(route==null) { Toast.makeText(this,"Plan a route first.",Toast.LENGTH_SHORT).show();return }
        val uri=Uri.parse("google.navigation:q="+route.destination.latitude+","+route.destination.longitude+"&mode=d")
        try { startActivity(Intent(Intent.ACTION_VIEW,uri)) }
        catch(_:Exception) { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+route.destination.latitude+","+route.destination.longitude))) }
    }

    private fun refreshRoadIntelligence() {
        if(!mapStyleState.isReady || isFinishing || isDestroyed) {
            mapSummary?.text="Map is still loading…"
            return
        }
        val center=map?.cameraPosition?.target?:KRAKOW
        mapStatus?.text="● SOURCES UPDATING";mapSummary?.text="Checking mapped road events and live weather…"
        worker.execute {
            var events:List<RoadEvent> = emptyList();var roadError:String?=null
            try{events=fetchRoadEvents(center.latitude,center.longitude)}catch(e:Exception){roadError=e.message}
            val weather=try{fetchWeather(center.latitude,center.longitude)}catch(e:Exception){"Weather source unavailable"}
            uiCallbacks.post {
                roadEvents=events;weatherSummary=weather;weatherText?.text=weather;renderRoadEvents()
                mapStatus?.text=if(roadError==null)"● ROAD FEED UPDATED" else "● PARTIAL SOURCE COVERAGE"
                updateMapSummary()
                Toast.makeText(this,if(roadError==null)"Road intelligence refreshed" else "Some road sources are unavailable",Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchRoadEvents(lat:Double,lon:Double):List<RoadEvent> {
        val q="[out:json][timeout:25];(node(around:6500,"+lat+","+lon+")[\"highway\"=\"construction\"];way(around:6500,"+lat+","+lon+")[\"highway\"=\"construction\"];node(around:6500,"+lat+","+lon+")[\"construction\"];way(around:6500,"+lat+","+lon+")[\"construction\"];node(around:6500,"+lat+","+lon+")[\"hazard\"];way(around:6500,"+lat+","+lon+")[\"hazard\"];);out center tags;"
        val endpoints=listOf("https://overpass-api.de/api/interpreter","https://overpass.kumi.systems/api/interpreter")
        var last:Exception?=null
        for(endpoint in endpoints){
            var connection:HttpURLConnection?=null
            try{
                connection=URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod="POST";connection.connectTimeout=10000;connection.readTimeout=25000;connection.doOutput=true
                connection.setRequestProperty("User-Agent","BusyBee-DriverCommandCentre/0.3")
                connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8")
                connection.outputStream.use{it.write(("data="+URLEncoder.encode(q,"UTF-8")).toByteArray())}
                if(connection.responseCode !in 200..299)error("Overpass HTTP "+connection.responseCode)
                val arr=JSONObject(connection.inputStream.bufferedReader().use{it.readText()}).optJSONArray("elements")?:return emptyList()
                val out=mutableListOf<RoadEvent>()
                for(i in 0 until arr.length()){
                    val el=arr.optJSONObject(i)?:continue;val tags=el.optJSONObject("tags")?:continue;val center=el.optJSONObject("center")
                    val a=if(center!=null)center.optDouble("lat",Double.NaN) else el.optDouble("lat",Double.NaN)
                    val b=if(center!=null)center.optDouble("lon",Double.NaN) else el.optDouble("lon",Double.NaN)
                    if(!a.isFinite()||!b.isFinite())continue
                    val cat=if(tags.has("hazard"))"Hazards" else "Roadworks"
                    val name=tags.optString("name").takeIf{it.isNotBlank()}?:tags.optString("construction").takeIf{it.isNotBlank()}?:cat
                    val detail=listOf(tags.optString("description"),tags.optString("access"),"OpenStreetMap observation").filter{it.isNotBlank()}.joinToString(" · ")
                    out.add(RoadEvent(name,cat,LatLng(a,b),detail))
                }
                return out.distinctBy{"%.5f:%.5f".format(Locale.US,it.point.latitude,it.point.longitude)}
            }catch(e:Exception){last=e}finally{connection?.disconnect()}
        }
        throw last?:IllegalStateException("No road feed responded")
    }

    private fun fetchWeather(lat:Double,lon:Double):String {
        val url="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&current=temperature_2m,precipitation,weather_code,wind_speed_10m&timezone=auto"
        val c=URL(url).openConnection() as HttpURLConnection;c.connectTimeout=9000;c.readTimeout=12000
        try{
            val o=JSONObject(c.inputStream.bufferedReader().use{it.readText()}).getJSONObject("current")
            val t=o.optDouble("temperature_2m",Double.NaN);val r=o.optDouble("precipitation",Double.NaN);val w=o.optDouble("wind_speed_10m",Double.NaN)
            val code=o.optInt("weather_code",-1)
            val state=when(code){0->"Clear";1,2->"Mostly clear";3->"Cloudy";45,48->"Fog";51,53,55,61,63,65,80,81,82->"Rain";71,73,75,77,85,86->"Snow";95,96,99->"Storm";else->"Weather"}
            return "WEATHER  "+state+"  "+t+"°C · rain "+r+" mm · wind "+w+" km/h · Open-Meteo"
        }finally{c.disconnect()}
    }

    private fun renderRoadEvents(){
        if(!mapStyleState.isReady || isFinishing || isDestroyed) return
        val active=map?:return
        eventMarkers.forEach{active.removeAnnotation(it)};eventMarkers.clear()
        roadEvents.filter{selectedLayers.contains(it.category)}.forEach{e->active.addMarker(MarkerOptions().position(e.point).title(e.title).snippet(e.category+" · "+e.detail+" · OSM"))?.let{eventMarkers.add(it)}}
        reportMarkers.forEach{active.removeAnnotation(it)};reportMarkers.clear()
        if(selectedLayers.contains("Driver reports"))commands.roadReports().forEach{r->active.addMarker(MarkerOptions().position(LatLng(r.latitude,r.longitude)).title(r.category).snippet(r.description+" · Private driver report"))?.let{reportMarkers.add(it)}}
    }

    private fun requestLocation(follow:Boolean=true){
        val fine=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val coarse=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!fine&&!coarse){
            ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),LOCATION_REQUEST)
            mapStatus?.text="● LOCATION PERMISSION REQUIRED"
            return
        }
        followLocation=follow
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        // Permission/style callbacks may request location before Activity reaches RESUMED.
        // Defer registration until onResume() rather than registering against a paused MapView.
        if(!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        try {
            val providers=locationManager.getProviders(true).filter { it==LocationManager.GPS_PROVIDER || it==LocationManager.NETWORK_PROVIDER }
            if(providers.isEmpty()) {
                mapStatus?.text="● LOCATION SERVICES OFF"
                navigationStatus?.text="LOCATION SERVICES ARE OFF"
                return
            }
            providers.forEach { provider ->
                val cached=locationManager.getLastKnownLocation(provider)
                if(cached!=null && System.currentTimeMillis()-cached.time in 0..300000L && (lastLocation==null || cached.time>lastLocation!!.time)) {
                    lastLocation=cached
                    updateDriverLocation(cached)
                }
                locationManager.requestLocationUpdates(provider,if(navigating)2000L else 5000L,if(navigating)5f else 20f,locationListener)
            }
            mapStatus?.text="● WAITING FOR GPS FIX"
        } catch(_:SecurityException) {
            mapStatus?.text="● LOCATION PERMISSION REQUIRED"
        } catch(error:Exception) {
            mapStatus?.text="● LOCATION UNAVAILABLE"
            Log.w("BusyBee","Location update could not start",error)
        }
    }


    private fun reportRoadHazard(){
        val categories=arrayOf("Accident","Road closure","Pothole / surface","Flooding","Dangerous obstruction","Other")
        AlertDialog.Builder(this).setTitle("Report road intelligence").setItems(categories){_,i->
            val input=EditText(this).apply{hint="What should other drivers know?";minLines=2}
            AlertDialog.Builder(this).setTitle(categories[i]).setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save privately"){_,_->
                val p=map?.cameraPosition?.target?:KRAKOW;val loc=lastLocation
                commands.addRoadReport(categories[i],input.text.toString().ifBlank{"Driver observation"},loc?.latitude?:p.latitude,loc?.longitude?:p.longitude)
                renderRoadEvents();Toast.makeText(this,"Saved privately on this device. Not shared.",Toast.LENGTH_LONG).show()
            }.show()
        }.show()
    }

    private fun buildOffersScreen():View{
        val root=columnScroll()
        root.addView(section("OFFER DESK","See the real offer, check the economics, then decide inside the platform."))

        val vehicle=commands.vehicleProfile()
        root.addView(card().apply {
            val line=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            val info=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL}
            info.addView(label("ACTIVE VEHICLE",9f,MINT,true))
            info.addView(label(vehicle.summary(),11f,WHITE,true).apply{setPadding(0,dp(4),0,0)})
            line.addView(info,LinearLayout.LayoutParams(0,-2,1f))
            line.addView(action("VEHICLE",false){showTab("DRIVER HUB")},LinearLayout.LayoutParams(dp(88),dp(38)))
            addView(line)
        })

        val rulesButton=action("DECISION RULES  ·  EDIT",false) {
            val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),0,dp(18),0)}
            val cost=field("Vehicle cost per km (PLN)",commands.setting("cost_per_km","0.85"))
            val minHour=field("Minimum net per hour (PLN)",commands.setting("min_per_hour","35"))
            val maxPickup=field("Maximum pickup distance (km)",commands.setting("max_pickup","8"))
            val minRating=field("Minimum passenger rating (0 = ignore)",commands.setting("min_rating","0"))
            listOf(cost,minHour,maxPickup,minRating).forEach{panel.addView(it)}
            AlertDialog.Builder(this).setTitle("Your offer filters")
                .setView(panel)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Save rules"){_,_->
                    commands.setSetting("cost_per_km",cost.text.toString().replace(',','.').ifBlank{"0.85"})
                    commands.setSetting("min_per_hour",minHour.text.toString().replace(',','.').ifBlank{"35"})
                    commands.setSetting("max_pickup",maxPickup.text.toString().replace(',','.').ifBlank{"8"})
                    commands.setSetting("min_rating",minRating.text.toString().replace(',','.').ifBlank{"0"})
                    showTab("OFFERS")
                }.show()
        }
        root.addView(rulesButton.apply{setPadding(0,dp(9),0,dp(9))})

        val liveListenerEnabled=isDriverAppLiveListenerEnabled()
        root.addView(section("LIVE DRIVER-APP LISTENER","Read-only Android UI events · no screenshots · no button control"))
        root.addView(card().apply {
            addView(label(if(liveListenerEnabled)"● LIVE LISTENER ACTIVE" else "LIVE LISTENER NOT ENABLED",10f,if(liveListenerEnabled)MINT:AMBER,true))
            addView(label("With your Android Accessibility permission, BusyBee observes visible text-change events only inside Uber Driver and Bolt Driver. It extracts relevant trip, offer and earnings text locally, then puts it in your review inbox. It does not read other apps, tap controls, accept/decline rides, record audio or save screenshots.",9f,MUTED,false).apply{setPadding(0,dp(5),0,dp(9))})
            addView(action(if(liveListenerEnabled)"MANAGE ANDROID LIVE LISTENER" else "ENABLE ANDROID LIVE LISTENER",true){openLiveListenerSettings()})
            addView(label("NOTIFICATIONS",9f,MINT,true).apply{setPadding(0,dp(12),0,dp(4))})
            addView(label("Android's notification listener can turn new ride-app notifications into BusyBee alerts. Notifications are a second signal, not the only data path.",9f,MUTED,false).apply{setPadding(0,dp(2),0,dp(7))})
            val notificationPermission=DriverAlertManager.notificationsAllowed(this@MainActivity)
            val alertsOn=DriverAlertManager.alertsEnabled(this@MainActivity)
            addView(label(if(alertsOn)"BUSYBEE ALERTS · ON" else "BUSYBEE ALERTS · MUTED",9f,if(alertsOn)MINT:AMBER,true).apply{setPadding(0,dp(3),0,dp(4))})
            addView(action(
                when {
                    !notificationPermission -> "GRANT NOTIFICATION PERMISSION"
                    alertsOn -> "MUTE BUSYBEE ALERTS"
                    else -> "TURN ON BUSYBEE ALERTS"
                },false
            ){
                if(!notificationPermission) requestBusyBeeAlerts()
                else {
                    DriverAlertManager.setAlertsEnabled(this@MainActivity,!alertsOn)
                    showTab("OFFERS")
                }
            })
            addView(action(if(notificationAccessEnabled())"RIDE-APP NOTIFICATION ACCESS · ON" else "ENABLE RIDE-APP NOTIFICATION ACCESS",false){startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}.apply{setPadding(0,dp(7),0,dp(7))})
        })

        root.addView(section("MANUAL VISUAL FALLBACK","Use only when a driver-app screen does not expose readable UI text"))
        root.addView(card().apply {
            addView(label("ONE-SESSION SCREEN WINDOW CAPTURE",9f,AMBER,true))
            addView(label("Android asks you to select the Uber/Bolt app window for each session. OCR runs locally. This is a fallback, not the normal live-listening path.",9f,MUTED,false).apply{setPadding(0,dp(4),0,dp(8))})
            val platforms=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL}
            platforms.addView(action("CAPTURE UBER",false){launchDriverScreenCapture("Uber")},LinearLayout.LayoutParams(0,dp(38),1f))
            platforms.addView(action("CAPTURE BOLT",false){launchDriverScreenCapture("Bolt")},LinearLayout.LayoutParams(0,dp(38),1f).apply{marginStart=dp(7)})
            addView(platforms)
            val extra=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL}
            extra.addView(action("＋ OTHER APP",false){promptOtherPlatformCapture()},LinearLayout.LayoutParams(0,dp(36),1f))
            extra.addView(action("STOP",false){stopDriverScreenCapture()},LinearLayout.LayoutParams(dp(82),dp(36)).apply{marginStart=dp(7)})
            addView(extra.apply{setPadding(0,dp(7),0,dp(5))})
            addView(label("PROVIDER API ACCESS",9f,AMBER,true))
            addView(label("Uber Driver API access requires approval. Bolt Fleet API is partner-scoped, not a general individual-driver API. No provider API connection is claimed.",9f,MUTED,false).apply{setPadding(0,dp(3),0,0)})
        })

        val ranked=OfferEngine.rank(commands.offers(),num("cost_per_km",0.85),num("min_per_hour",35.0),num("max_pickup",8.0),num("min_rating",0.0))
        val add=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        add.addView(label("RANKED OFFERS",13f,WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
        add.addView(action("＋ MANUAL",true){offerDialog()},LinearLayout.LayoutParams(dp(112),dp(38)))
        root.addView(add.apply{setPadding(dp(2),dp(17),dp(2),dp(8))})
        root.addView(label("${ranked.size} offers meet your current rules · ranked by estimated net PLN/hour",9f,MUTED,false).apply{setPadding(dp(2),0,0,dp(7))})
        if(ranked.isEmpty()) root.addView(card().apply{
            addView(label("No qualifying offers yet",14f,WHITE,true))
            addView(label("Start a native capture session or add an offer for correction. BusyBee does not insert demo offers.",9f,MUTED,false).apply{setPadding(0,dp(4),0,0)})
        })
        ranked.forEach{root.addView(offerCard(it))}

        root.addView(section("SECONDARY SIGNALS","Notifications are optional evidence, never the primary integration"))
        val notices=commands.notices()
        if(notices.isEmpty()) root.addView(label("No ride-platform notifications captured.",10f,MUTED,false).apply{setPadding(dp(4),dp(3),0,dp(10))})
        notices.forEach { notice ->
            val item=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(9),dp(4),dp(9))}
            item.addView(label(notice.platform.uppercase()+"  ·  "+dateTime(notice.capturedAt),9f,MINT,true))
            item.addView(label(notice.title,11f,WHITE,true).apply{setPadding(0,dp(3),0,dp(2))})
            item.addView(label(notice.body.take(180),9f,MUTED,false).apply{maxLines=3})
            item.addView(action("REVIEW AS OFFER",false){offerDialog(notice.platform,notice.body)}.apply{setPadding(0,dp(7),0,dp(7))})
            root.addView(item)
            root.addView(divider())
        }
        return root
    }

    private fun offerCard(score:RankedOffer):View{
        val o=score.offer
        return card().apply{
            val row=LinearLayout(this@MainActivity).apply{gravity=Gravity.CENTER_VERTICAL}
            row.addView(label(o.platform.uppercase(),10f,MINT,true));row.addView(View(this@MainActivity),LinearLayout.LayoutParams(0,1,1f))
            row.addView(label(String.format(Locale.US,"%.1f PLN/h",score.netPerHour),17f,WHITE,true));addView(row)
            addView(label(o.pickup.ifBlank{"Pickup not supplied"}+" → "+o.destination.ifBlank{"Destination not supplied"},12f,WHITE,true).apply{setPadding(0,dp(7),0,dp(5))})
            addView(label("Fare "+money(o.fare+o.bonus)+" · est. net "+money(score.estimatedNet)+" · "+String.format(Locale.US,"%.1f",o.totalKm)+" km · "+score.totalMinutes.toInt()+" min",10f,MUTED,false))
            addView(label("Pickup "+o.pickupKm+" km · trip "+o.tripKm+" km · passenger "+(o.passengerRating?.toString()?:"not supplied"),10f,MUTED,false).apply{setPadding(0,dp(3),0,dp(9))})
            val actions=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL}
            actions.addView(action("OPEN "+o.platform.uppercase()+" TO REVIEW",true){openPlatform(o.platform)},LinearLayout.LayoutParams(0,dp(40),1f))
            actions.addView(action("SKIP",false){commands.updateOfferStatus(o.id,"skipped");showTab("OFFERS")},LinearLayout.LayoutParams(dp(74),dp(40)).apply{marginStart=dp(7)})
            addView(actions)
            addView(label("BusyBee ranks the offer; you make the acceptance decision inside the platform.",9f,MUTED,false).apply{setPadding(0,dp(7),0,0)})
        }
    }

    private fun offerDialog(platformDefault:String="Uber",raw:String=""){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),0,dp(18),0)}
        val platform=field("Platform",platformDefault);val pickup=field("Pickup / area","");val destination=field("Destination","")
        val fare=field("Fare (PLN)","");val bonus=field("Bonus (PLN)","0");val pickupKm=field("Pickup distance (km)","")
        val tripKm=field("Trip distance (km)","");val duration=field("Trip duration (minutes)","");val rating=field("Passenger rating (optional)","")
        val evidence=field("Notification evidence (optional)",raw)
        listOf(platform,pickup,destination,fare,bonus,pickupKm,tripKm,duration,rating,evidence).forEach{box.addView(it)}
        AlertDialog.Builder(this).setTitle("Add ride offer").setView(ScrollView(this).apply{addView(box)}).setNegativeButton("Cancel",null).setPositiveButton("Save & rank"){_,_->
            fun value(e:EditText)=e.text.toString().trim().replace(',','.').toDoubleOrNull()
            val f=value(fare);val pk=value(pickupKm);val tk=value(tripKm);val du=value(duration)
            if(f==null||pk==null||tk==null||du==null||f<0||pk<0||tk<0||du<=0){Toast.makeText(this,"Fare, distances and duration must be valid numbers.",Toast.LENGTH_LONG).show();return@setPositiveButton}
            commands.saveOffer(DriverOffer(platform=platform.text.toString().ifBlank{"Other"},pickup=pickup.text.toString(),destination=destination.text.toString(),fare=f,bonus=value(bonus)?:0.0,pickupKm=pk,tripKm=tk,durationMin=du,passengerRating=value(rating),source=if(raw.isBlank())"manual" else "notification-review",raw=evidence.text.toString()))
            showTab("OFFERS")
        }.show()
    }

    private fun buildEarningsScreen():View{
        val root=columnScroll()
        val summary=mirror.summary()
        root.addView(section("DRIVER MIRROR","Your confirmed work, evidence and economics · stored privately on this device"))

        // One clear financial focal point instead of a long stack of equal-weight cards.
        val hero=card().apply {
            addView(label("KNOWN NET · PLN TRIPS",9f,MINT,true))
            addView(label(summary.knownNet?.let{money(it)}?:"INCOMPLETE",28f,WHITE,true).apply{setPadding(0,dp(5),0,dp(2))})
            addView(label("${summary.trips} trips  ·  ${summary.offers} offers  ·  ${summary.records} records",10f,MUTED,false))
            addView(label("Gross, fees and bonuses must be known for every counted trip. Vehicle costs, taxes and missing adjustments are excluded.",9f,MUTED,false).apply{setPadding(0,dp(8),0,0)})
        }
        root.addView(hero)

        val kpis=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        kpis.addView(metric("GROSS",money(summary.gross)),LinearLayout.LayoutParams(0,dp(72),1f))
        kpis.addView(metric("FEES",money(summary.fees)),LinearLayout.LayoutParams(0,dp(72),1f).apply{marginStart=dp(6)})
        kpis.addView(metric("BONUSES",money(summary.bonuses)),LinearLayout.LayoutParams(0,dp(72),1f).apply{marginStart=dp(6)})
        root.addView(kpis)
        root.addView(label("PLATFORMS  ·  ${summary.platforms.joinToString().ifBlank{"No reviewed platforms yet"}}  ·  ${summary.imports} legacy imports",9f,MUTED,false).apply{setPadding(dp(3),dp(10),dp(3),dp(4))})

        val captureStore=DriverAppCaptureStore(this)
        val observations=captureStore.recent(20)
        root.addView(section("REVIEW INBOX","Native app observations are suggestions, not earnings, until you confirm them"))
        if(observations.isEmpty()) {
            root.addView(card().apply {
                addView(label("Nothing waiting for review",13f,WHITE,true))
                addView(label("Start a user-consented session from Offers, select the driver-app window in Android, and open trip or earnings details.",10f,MUTED,false).apply{setPadding(0,dp(4),0,0)})
                addView(action("OPEN OFFER DESK",false){showTab("OFFERS")}.apply{setPadding(0,dp(7),0,dp(7))})
            })
        } else observations.forEach { observation ->
            root.addView(card().apply {
                val heading=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
                heading.addView(label(observation.platform.uppercase()+" · "+observation.screenType,10f,MINT,true))
                heading.addView(View(this@MainActivity),LinearLayout.LayoutParams(0,1,1f))
                heading.addView(label(dateTime(observation.capturedAt),9f,MUTED,false))
                addView(heading)
                addView(label(if(observation.sourceType=="ACCESSIBILITY_UI")"LIVE UI LISTENER" else "MANUAL SCREEN OCR",8f,AMBER,true).apply{setPadding(0,dp(3),0,dp(2))})
                addView(label(observation.visibleText.take(180),10f,WHITE,false).apply{setPadding(0,dp(3),0,dp(5));maxLines=3})
                val routeText=listOfNotNull(observation.pickup,observation.dropoff).joinToString(" → ")
                if(routeText.isNotBlank()) addView(label(routeText,9f,WHITE,true).apply{setPadding(0,dp(2),0,dp(4))})
                addView(label("Parser confidence · "+String.format(Locale.US,"%.0f%%",observation.parseConfidence*100)+" · heuristic, not a financial guarantee",8f,AMBER,false).apply{setPadding(0,dp(2),0,dp(3))})
                addView(label("Detected "+money(observation.detectedAmount)+"  ·  "+(observation.distanceKm?.let{"%.1f km".format(Locale.US,it)}?:"distance unknown")+"  ·  "+(observation.durationMinutes?.let{"%.0f min".format(Locale.US,it)}?:"time unknown"),9f,MUTED,false))
                val actions=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL}
                actions.addView(action("REVIEW & SAVE",true){reviewNativeObservation(observation)},LinearLayout.LayoutParams(0,dp(38),1f))
                actions.addView(action("DISCARD",false){captureStore.delete(observation.id);showTab("EARNINGS")},LinearLayout.LayoutParams(dp(88),dp(38)).apply{marginStart=dp(7)})
                addView(actions.apply{setPadding(0,dp(8),0,0)})
            })
        }

        val expenses=commands.totalAmount("Expense",true)
        root.addView(section("VEHICLE ECONOMICS","Keep trip earnings separate from real operating costs"))
        val economics=card().apply {
            val line=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            val info=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL}
            info.addView(label("RECORDED VEHICLE EXPENSES",9f,MUTED,true))
            info.addView(label(money(expenses),19f,WHITE,true).apply{setPadding(0,dp(3),0,0)})
            line.addView(info,LinearLayout.LayoutParams(0,-2,1f))
            line.addView(action("＋ LOG COST",true){itemDialog("Expense")},LinearLayout.LayoutParams(dp(112),dp(38)))
            addView(line)
            addView(label("Completed expense items only. They are not subtracted from trip totals until matched to the same period.",9f,MUTED,false).apply{setPadding(0,dp(6),0,0)})
        }
        root.addView(economics)

        val costInput=field("Vehicle cost per km (PLN)",commands.setting("cost_per_km","0.85"))
        costInput.layoutParams=LinearLayout.LayoutParams(0,dp(42),1f)
        root.addView(card().apply {
            addView(label("COST MODEL",10f,MINT,true))
            val line=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            line.addView(costInput)
            line.addView(action("SAVE",true){commands.setSetting("cost_per_km",costInput.text.toString().replace(',','.').ifBlank{"0.85"});showTab("EARNINGS")},LinearLayout.LayoutParams(dp(78),dp(42)).apply{marginStart=dp(7)})
            addView(line)
            addView(label("This same saved assumption is used by Offer Desk ranking. It is not a tax or full vehicle-cost calculation.",9f,MUTED,false).apply{setPadding(0,dp(5),0,0)})
        })

        val analytics=DriverAnalyticsEngine.calculate(mirror.recent(5000),num("cost_per_km",0.85))
        root.addView(section("EARNINGS INTELLIGENCE","Only confirmed records · missing values stay unknown"))
        root.addView(card().apply {
            addView(label("SOURCE COVERAGE",10f,MINT,true))
            addView(label("${analytics.plnTripCount} PLN trips  ·  gross ${analytics.tripsWithGross}  ·  fees ${analytics.tripsWithFees}  ·  bonus ${analytics.tripsWithBonus}",10f,WHITE,false).apply{setPadding(0,dp(5),0,dp(2))})
            addView(label("Distance ${analytics.tripsWithDistance}  ·  duration ${analytics.tripsWithTime}",9f,MUTED,false))
            addView(label("ESTIMATED NET AFTER VEHICLE COST",9f,MUTED,true).apply{setPadding(0,dp(10),0,dp(2))})
            addView(label(analytics.estimatedNetPln?.let{money(it)}?:"NOT ENOUGH COMPLETE DATA",21f,MINT,true))
            addView(label("${analytics.estimatedNetTrips} trips include gross, fees, bonus and distance. Excludes tax, unpaid waiting time and costs outside your per-km assumption.",9f,MUTED,false).apply{setPadding(0,dp(4),0,0)})
        })

        if(analytics.platforms.isNotEmpty()) {
            root.addView(section("PLATFORM COMPARISON","Swipe horizontally · incomplete net rows are excluded"))
            val strip=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            analytics.platforms.forEach { platform ->
                strip.addView(card().apply {
                    layoutParams=LinearLayout.LayoutParams(dp(238),-2).apply{marginEnd=dp(8)}
                    addView(label(platform.platform.uppercase()+"  ·  "+platform.tripCount+" TRIPS",10f,MINT,true))
                    addView(label("Gross  "+money(platform.grossPln),10f,WHITE,false).apply{setPadding(0,dp(7),0,dp(2))})
                    addView(label("Fees  "+money(platform.feesPln)+"  ·  bonuses  "+money(platform.bonusesPln),9f,MUTED,false))
                    addView(label("Known net  "+money(platform.knownNetBeforeVehiclePln),10f,WHITE,true).apply{setPadding(0,dp(7),0,dp(2))})
                    addView(label("After vehicle  "+money(platform.estimatedNetAfterVehiclePln),10f,MINT,true))
                    addView(label("${platform.completeNetTrips}/${platform.tripCount} complete net  ·  ${platform.tripsWithDistance}/${platform.tripCount} with distance",9f,AMBER,false).apply{setPadding(0,dp(6),0,0)})
                })
            }
            root.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(strip)})
        }

        if(analytics.hours.isNotEmpty()) {
            root.addView(section("START-TIME SIGNAL","Recorded trip start hour, not hourly wage · swipe for more"))
            val strip=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            analytics.hours.take(12).forEach { hour ->
                strip.addView(card().apply {
                    layoutParams=LinearLayout.LayoutParams(dp(132),dp(94)).apply{marginEnd=dp(7)}
                    addView(label(String.format(Locale.US,"%02d:00",hour.hour),14f,WHITE,true))
                    addView(label("${hour.trips} trips",9f,MUTED,false).apply{setPadding(0,dp(4),0,dp(5))})
                    addView(label(money(hour.grossPln),10f,MINT,true))
                    addView(label("net "+money(hour.completeNetPln),9f,MUTED,false).apply{setPadding(0,dp(3),0,0)})
                })
            }
            root.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(strip)})
        }

        if(analytics.dataWarnings.isNotEmpty()) {
            root.addView(card().apply {
                addView(label("DATA GAPS · READ BEFORE COMPARING",10f,AMBER,true))
                analytics.dataWarnings.forEach { warning -> addView(label("• "+warning,9f,MUTED,false).apply{setPadding(0,dp(5),0,0)}) }
            })
        }

        root.addView(section("RECENT CONFIRMED ACTIVITY","Source evidence remains attached to each saved record"))
        val recent=mirror.recent(30)
        if(recent.isEmpty()) root.addView(label("No confirmed trips or offers yet.",11f,MUTED,false).apply{setPadding(dp(4),dp(4),0,dp(12))})
        recent.forEach { record ->
            val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(10),dp(4),dp(10))}
            val title=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            title.addView(label(record.platform.uppercase()+"  ·  "+record.type.uppercase(),10f,MINT,true))
            title.addView(View(this@MainActivity),LinearLayout.LayoutParams(0,1,1f))
            title.addView(label(record.occurredAt?: "Date unknown",9f,MUTED,false))
            row.addView(title)
            row.addView(label((record.pickup?: "Pickup unknown")+"  →  "+(record.dropoff?: "Destination unknown"),11f,WHITE,true).apply{setPadding(0,dp(4),0,dp(3))})
            row.addView(label("Gross "+money(record.gross)+"  ·  fees "+money(record.fees)+"  ·  bonus "+money(record.bonus),9f,MUTED,false))
            root.addView(row)
            root.addView(divider())
        }

        val history=mirror.importHistory()
        root.addView(section("LEGACY IMPORT HISTORY","Optional historical evidence · never required for normal use"))
        if(history.isEmpty()) root.addView(label("No legacy imports.",9f,MUTED,false).apply{setPadding(dp(4),0,0,dp(8))})
        else history.take(8).forEach { entry -> root.addView(label("• "+entry,9f,MUTED,false).apply{setPadding(dp(4),dp(3),0,dp(3))}) }
        return root
    }

    private fun reviewNativeObservation(observation: NativeDriverObservation) {
        val form=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),0,dp(18),0)
        }
        val types=arrayOf("offer","trip","payout","adjustment")
        val type=Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,types)
            val defaultType=when(observation.screenType) {
                "OFFER" -> "offer"
                "TRIP" -> "trip"
                "EARNINGS" -> "payout"
                else -> "trip"
            }
            setSelection(types.indexOf(defaultType).coerceAtLeast(0))
        }
        val amount=field("Amount / gross (confirm or correct)",observation.detectedAmount?.toString().orEmpty())
        val fees=field("Platform fees (blank = unknown)","")
        val bonus=field("Bonus (blank = unknown)","")
        val pickup=field("Pickup / start address (optional)",observation.pickup.orEmpty())
        val dropoff=field("Drop-off / destination (optional)",observation.dropoff.orEmpty())
        val distance=field("Trip distance km",observation.distanceKm?.toString().orEmpty())
        val duration=field("Trip duration minutes",observation.durationMinutes?.toString().orEmpty())
        val currency=field("Currency code","PLN")
        val sourceLabel=if(observation.sourceType=="ACCESSIBILITY_UI")"LIVE ANDROID UI OBSERVATION" else "MANUAL ANDROID SCREEN OCR"
        form.addView(label("SOURCE · "+sourceLabel,9f,MINT,true).apply{setPadding(0,0,0,dp(3))})
        form.addView(label("PARSER CONFIDENCE · "+String.format(Locale.US,"%.0f%%",observation.parseConfidence*100)+" · rule-based signal; verify every value",8f,AMBER,false).apply{setPadding(0,0,0,dp(5))})
        form.addView(label("RECORD TYPE",10f,MUTED,true))
        form.addView(type)
        listOf(amount,fees,bonus,pickup,dropoff,distance,duration,currency).forEach { form.addView(it) }
        form.addView(label("Original on-device evidence",10f,MINT,true).apply { setPadding(0,dp(7),0,dp(4)) })
        form.addView(label(observation.visibleText,10f,MUTED,false).apply { maxLines=8 })
        val scroll=ScrollView(this).apply { addView(form) }
        AlertDialog.Builder(this)
            .setTitle("Review ${observation.platform} data")
            .setView(scroll)
            .setNegativeButton("Cancel",null)
            .setNeutralButton("Discard") { _,_ ->
                DriverAppCaptureStore(this).delete(observation.id)
                showTab("EARNINGS")
            }
            .setPositiveButton("Confirm & save") { _,_ ->
                fun parsed(edit:EditText, fieldName:String):Double? {
                    val raw=edit.text.toString().trim()
                    if(raw.isBlank()) return null
                    val value=DriverCsv.number(raw)
                    require(value!=null && value>=0.0) { "$fieldName must be a valid non-negative number" }
                    return value
                }
                try {
                    mirror.saveReviewedNativeObservation(
                        platform=observation.platform,
                        recordType=type.selectedItem.toString(),
                        capturedAt=observation.capturedAt,
                        visibleText=observation.visibleText,
                        gross=parsed(amount,"Amount"),
                        fees=parsed(fees,"Fees"),
                        bonus=parsed(bonus,"Bonus"),
                        distanceKm=parsed(distance,"Distance"),
                        durationMinutes=parsed(duration,"Duration"),
                        currency=currency.text.toString(),
                        pickup=pickup.text.toString(),
                        dropoff=dropoff.text.toString(),
                        sourceName=if(observation.sourceType=="ACCESSIBILITY_UI")"Android live UI listener" else "Android screen capture",
                        parseConfidence=observation.parseConfidence
                    )
                    DriverAppCaptureStore(this).delete(observation.id)
                    Toast.makeText(this,"Driver-reviewed record saved with original screen evidence.",Toast.LENGTH_LONG).show()
                    showTab("EARNINGS")
                } catch(error:Exception) {
                    Toast.makeText(this,error.message ?: "Record could not be saved",Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun openWhatsAppGroup(group: WhatsAppGroupWatch) {
        if(!group.trackingEnabled) {
            AlertDialog.Builder(this)
                .setTitle(group.title)
                .setMessage("Tracking is off. Enable this group first. BusyBee stores no message previews for groups that are off.")
                .setPositiveButton("OK",null)
                .show()
            return
        }
        val messages=commands.whatsappGroupMessages(group.id,80)
        val content=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(8),dp(18),dp(8))
        }
        if(messages.isEmpty()) content.addView(label("No message previews have been exposed by Android notifications for this group yet.",11f,MUTED,false))
        messages.asReversed().forEach { message ->
            content.addView(label(message.sender+" · "+dateTime(message.messageAt),9f,MINT,true))
            content.addView(label(message.body,11f,WHITE,false).apply { setPadding(0,dp(3),0,dp(9)) })
            content.addView(divider())
        }
        AlertDialog.Builder(this)
            .setTitle(group.title)
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("Close",null)
            .setNeutralButton("Stop tracking") { _,_ ->
                commands.setWhatsAppGroupTracking(group.id,false)
                showTab("DRIVER HUB")
            }
            .setNegativeButton("Delete group data") { _,_ ->
                AlertDialog.Builder(this)
                    .setTitle("Delete group data?")
                    .setMessage("This removes saved message previews and the group's tracking choice from this device.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Delete") { _,_ ->
                        commands.deleteWhatsAppGroup(group.id)
                        showTab("DRIVER HUB")
                    }.show()
            }
            .show()
    }

    private fun buildDriverHubScreen():View{
        val root=columnScroll()
        root.addView(section("DRIVER HUB","Vehicle, documents, payments and the driver groups you choose to follow."))
        root.addView(buildVehicleCard())

        val quick=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        quick.addView(action("＋ REMINDER / PAYMENT",true){itemDialog()},LinearLayout.LayoutParams(0,dp(42),1f))
        quick.addView(action("＋ DOCUMENT",false){documentPicker.launch(arrayOf("*/*"))},LinearLayout.LayoutParams(0,dp(42),1f).apply{marginStart=dp(7)})
        root.addView(quick.apply{setPadding(0,dp(5),0,dp(12))})

        root.addView(section("YOUR ADMIN LIST","Tap an item to mark it completed; linked documents stay on this device."))
        val items=commands.items()
        if(items.isEmpty()) root.addView(label("No reminders yet. Add an insurance renewal, payment, document expiry, maintenance or expense.",10f,MUTED,false).apply{setPadding(dp(4),dp(4),0,dp(12))})
        items.filter{!it.done}.forEach { item ->
            val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(10),dp(4),dp(10))}
            val title=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            title.addView(label(item.title,12f,WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
            title.addView(label(item.dueAt?.let{date(it)}?:"NO DUE DATE",9f,if(item.dueAt!=null&&item.dueAt<System.currentTimeMillis())AMBER else MUTED,true))
            row.addView(title)
            row.addView(label(item.kind.uppercase()+"  ·  "+(item.recurrence?: "one-time")+(item.amount?.let{"  ·  "+money(it)}?:""),9f,MINT,true).apply{setPadding(0,dp(3),0,dp(3))})
            row.addView(label(item.details.ifBlank{"No additional details"},9f,MUTED,false))
            val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            actions.addView(action("MARK DONE",true){commands.completeItem(item.id,true);showTab("DRIVER HUB")},LinearLayout.LayoutParams(0,dp(36),1f))
            if(item.uri!=null) actions.addView(action("OPEN FILE",false){openUri(Uri.parse(item.uri))},LinearLayout.LayoutParams(dp(100),dp(36)).apply{marginStart=dp(7)})
            row.addView(actions.apply{setPadding(0,dp(8),0,0)})
            root.addView(row);root.addView(divider())
        }

        val completed=items.filter{it.done}
        root.addView(section("COMPLETED HISTORY",completed.size.toString()+" paid, renewed or serviced items"))
        if(completed.isEmpty()) root.addView(label("Completed items will appear here.",9f,MUTED,false).apply{setPadding(dp(4),0,0,dp(8))})
        completed.take(20).forEach { item ->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(7),dp(4),dp(7))}
            val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
            info.addView(label(item.title,10f,WHITE,true))
            info.addView(label(item.kind.uppercase()+"  ·  "+(item.completedAt?.let{dateTime(it)}?:"Completed")+"  ·  "+(item.amount?.let{money(it)}?:"amount not recorded"),8f,MUTED,false).apply{setPadding(0,dp(3),0,0)})
            row.addView(info,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(action("REOPEN",false){commands.completeItem(item.id,false);showTab("DRIVER HUB")},LinearLayout.LayoutParams(dp(78),dp(34)))
            root.addView(row);root.addView(divider())
        }

        root.addView(section("WHATSAPP GROUP WATCH","Discover notification-visible groups, then choose which ones BusyBee may track."))
        val groupCard=card()
        groupCard.addView(label("PRIVATE BY DEFAULT",9f,MINT,true))
        groupCard.addView(label("Android exposes only notification content. BusyBee cannot read WhatsApp's private database or recover full chat history. No message body is stored while a group is off.",9f,MUTED,false).apply{setPadding(0,dp(4),0,dp(8))})
        groupCard.addView(action(if(notificationAccessEnabled())"NOTIFICATION ACCESS ENABLED" else "ENABLE ANDROID NOTIFICATION ACCESS",false){
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        },LinearLayout.LayoutParams(-1,dp(38)))
        val groups=commands.whatsappGroups()
        if(groups.isEmpty()) groupCard.addView(label("No groups discovered. Open WhatsApp and receive a group message notification; only groups exposed by Android can appear here.",9f,MUTED,false).apply{setPadding(0,dp(8),0,0)})
        groups.forEach { group ->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(9),0,dp(9))}
            val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
            info.addView(label(group.title,10f,WHITE,true))
            info.addView(label((if(group.trackingEnabled)"TRACKING ON" else "DISCOVERED · OFF")+"  ·  "+group.capturedMessages+" saved",8f,if(group.trackingEnabled)MINT else MUTED,true).apply{setPadding(0,dp(3),0,0)})
            row.addView(info,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(action(if(group.trackingEnabled)"PAUSE" else "TRACK",group.trackingEnabled){
                commands.setWhatsAppGroupTracking(group.id,!group.trackingEnabled);showTab("DRIVER HUB")
            },LinearLayout.LayoutParams(dp(76),dp(34)))
            row.addView(action("VIEW",false){openWhatsAppGroup(group)},LinearLayout.LayoutParams(dp(62),dp(34)).apply{marginStart=dp(5)})
            groupCard.addView(row);groupCard.addView(divider())
        }
        root.addView(groupCard)

        root.addView(section("TRUSTED SERVICES","Mechanics, tyre shops, insurers, car washes and fleet contacts."))
        val services=commands.items("Service")
        root.addView(action("＋ SAVE SERVICE / CONTACT",false){itemDialog("Service")}.apply{setPadding(0,dp(9),0,dp(9))})
        if(services.isEmpty()) root.addView(label("No service contacts saved.",9f,MUTED,false).apply{setPadding(dp(4),0,0,dp(8))})
        services.forEach { service ->
            root.addView(label(service.title+"  ·  "+service.details,10f,WHITE,false).apply{setPadding(dp(4),dp(8),0,dp(8))})
            root.addView(divider())
        }
        return root
    }

    private fun buildVehicleCard():View {
        val profile=commands.vehicleProfile()
        val box=card()
        box.addView(label("MY VEHICLE",11f,MINT,true))
        vehicleProfileSummary=label(
            if(profile.totalCostPerKm==null) "Choose your vehicle and add real consumption and energy costs. Offer Desk currently uses its generic 0.85 PLN/km fallback until your vehicle cost is configured."
            else profile.summary(),
            11f,WHITE,false
        ).apply { setPadding(0,dp(6),0,dp(8)) }
        box.addView(vehicleProfileSummary)
        box.addView(action(if(profile.totalCostPerKm==null)"＋ SELECT VEHICLE & COSTS" else "EDIT VEHICLE PROFILE",true){vehicleDialog()})
        return box
    }

    private fun vehicleDialog() {
        val existing=commands.vehicleProfile()
        val form=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),0,dp(18),0)
        }
        val name=field("Vehicle name / model (optional)",existing.name)
        val category=Spinner(this)
        category.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,VehicleProfile.CATEGORIES)
        category.setSelection(VehicleProfile.CATEGORIES.indexOf(existing.category).coerceAtLeast(0))
        val fuel=Spinner(this)
        fuel.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,VehicleProfile.FUEL_TYPES)
        fuel.setSelection(VehicleProfile.FUEL_TYPES.indexOf(existing.fuelType).coerceAtLeast(0))
        val consumption=field(if(existing.fuelType=="Electric")"Consumption (kWh / 100 km)" else "Consumption (L / 100 km)",existing.consumptionPer100Km?.toString().orEmpty())
        val energyPrice=field(if(existing.fuelType=="Electric")"Energy price (PLN / kWh)" else "Fuel price (PLN / litre)",existing.energyPricePln?.toString().orEmpty())
        val maintenance=field("Maintenance / tyres / other (PLN per km)",existing.maintenanceCostPerKm.toString())
        listOf(name,category,fuel,consumption,energyPrice,maintenance).forEach { view -> form.addView(view) }
        fuel.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) {
                val electric=VehicleProfile.FUEL_TYPES[position]=="Electric"
                consumption.hint=if(electric)"Consumption (kWh / 100 km)" else "Consumption (L / 100 km)"
                energyPrice.hint=if(electric)"Energy price (PLN / kWh)" else "Fuel price (PLN / litre)"
            }
        }
        form.addView(label("Routing currently uses a standard passenger-car road profile. Vehicle height, weight and access restrictions are not yet enforced.",10f,MUTED,false))
        AlertDialog.Builder(this)
            .setTitle("Vehicle profile")
            .setView(form)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save vehicle") { _,_ ->
                val consumptionValue=consumption.text.toString().replace(',','.').trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()
                val energyValue=energyPrice.text.toString().replace(',','.').trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()
                val maintenanceValue=maintenance.text.toString().replace(',','.').trim().toDoubleOrNull()
                if((consumption.text.isNotBlank() && (consumptionValue==null || consumptionValue<=0.0)) ||
                    (energyPrice.text.isNotBlank() && (energyValue==null || energyValue<=0.0)) ||
                    ((consumptionValue==null)!=(energyValue==null)) ||
                    maintenanceValue==null || maintenanceValue<0.0) {
                    Toast.makeText(this,"Enter valid consumption and energy price together; maintenance must be zero or more.",Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val profile=VehicleProfile(
                    name=name.text.toString(),
                    category=category.selectedItem.toString(),
                    fuelType=fuel.selectedItem.toString(),
                    consumptionPer100Km=consumptionValue,
                    energyPricePln=energyValue,
                    maintenanceCostPerKm=maintenanceValue
                )
                commands.saveVehicleProfile(profile)
                Toast.makeText(this,if(profile.totalCostPerKm==null)"Vehicle saved. Add consumption and price to calculate cost/km." else "Vehicle saved · ${String.format(Locale.US,"%.3f",profile.totalCostPerKm)} PLN/km used for offer ranking.",Toast.LENGTH_LONG).show()
                showTab("DRIVER HUB")
            }
            .show()
    }

    private fun itemDialog(defaultKind:String="Insurance / payment / document / expense"){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),0,dp(18),0)}
        val kind=field("Type",defaultKind);val title=field("Title","");val details=field("Details / provider / policy","")
        val due=field("Due date (YYYY-MM-DD, optional)","");val amount=field("Amount (PLN, optional)","")
        val recurrence=field("Repeat: none / weekly / monthly / yearly","none")
        listOf(kind,title,details,due,amount,recurrence).forEach{box.addView(it)}
        AlertDialog.Builder(this).setTitle("Save driver item").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->
            val dueAt=due.text.toString().trim().takeIf{it.isNotBlank()}?.let{parseDate(it)}
            if(due.text.isNotBlank()&&dueAt==null){Toast.makeText(this,"Use YYYY-MM-DD for due date",Toast.LENGTH_LONG).show();return@setPositiveButton}
            val cadence=recurrence.text.toString().trim().lowercase().takeIf{it in setOf("weekly","monthly","yearly")}
            if(cadence!=null&&dueAt==null){Toast.makeText(this,"A recurring item needs a due date.",Toast.LENGTH_LONG).show();return@setPositiveButton}
            commands.addItem(kind.text.toString().ifBlank{"Reminder"},title.text.toString().ifBlank{"Untitled"},details.text.toString(),dueAt,amount.text.toString().replace(',','.').toDoubleOrNull(),recurrence=cadence)
            showTab("DRIVER HUB")
        }.show()
    }

    private fun importDocument(uri:Uri){
        try{
            contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name=contentResolver.query(uri,null,null,null,null)?.use{c->val i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(c.moveToFirst()&&i>=0)c.getString(i)else"Driver document"}?:"Driver document"
            commands.addItem("Document",name,"Private document reference stored on device",null,null,uri.toString());showTab("DRIVER HUB")
        }catch(e:Exception){Toast.makeText(this,"Could not save document: "+e.message,Toast.LENGTH_LONG).show()}
    }

    private fun buildSourcesScreen():View{
        val root=columnScroll()
        val locationGranted =
            ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val watchedGroupCount=commands.whatsappGroups().count{it.trackingEnabled}
        root.addView(section("LIVE INTELLIGENCE","Real data sources, their permission state and what BusyBee can actually read."))

        val active=card()
        active.addView(label("ACTIVE MAP & DEVICE SOURCES",10f,MINT,true))
        sourceRow(active,"Map · OpenFreeMap / OpenStreetMap","Vector road network, names, land use and building geometry · current ${daylightPhase.name.lowercase()} style","ACTIVE BASE MAP · ${daylightPhase.name}")
        sourceRow(active,"Mapped road observations · Overpass","On-demand OpenStreetMap-tagged construction and hazard objects · not live traffic speed","ON-DEMAND · NOT TRAFFIC SPEED")
        sourceRow(active,"Weather · Open-Meteo","On-demand current weather context near the map centre · not a road-incident feed","ON-DEMAND · WEATHER ONLY")
        sourceRow(active,"Driver location · Android GPS","Location, speed and bearing used for the driver marker and foreground navigation",if(locationGranted)"PERMISSION GRANTED" else "PERMISSION REQUIRED")
        sourceRow(active,"Driver apps · read-only live listener","Uber Driver and Bolt Driver visible UI text-change events, filtered by package · explicit Android Accessibility opt-in",if(isDriverAppLiveListenerEnabled())"ENABLED BY YOU" else "AVAILABLE · USER OPT-IN")
        sourceRow(active,"Driver app · visual fallback","One-session Android app-window capture with on-device OCR for screens that expose no readable UI text","MANUAL · PER-SESSION CONSENT")
        sourceRow(active,"BusyBee driver alerts","Local notifications for newly detected offer, trip and earnings events",if(DriverAlertManager.notificationsAllowed(this))"ALLOWED" else "NOTIFICATION PERMISSION REQUIRED")
        sourceRow(active,"Ride-app notifications","Secondary signal from OS notifications; never the only driver-app integration",if(notificationAccessEnabled())"LISTENER PERMISSION ENABLED" else "OPTIONAL PERMISSION")
        sourceRow(active,"WhatsApp selected groups","${watchedGroupCount} group(s) currently enabled · notification content only · no private database access",if(watchedGroupCount>0)"USER SELECTED" else "NO GROUPS SELECTED")
        root.addView(active)

        val public=card()
        public.addView(label("PUBLIC ROAD DATA · NEXT CONNECTORS",10f,MINT,true))
        sourceRow(public,"GDDKiA / National Access Point","Polish road disruption events in DATEX II; account/client certificate registration and connector implementation required","PUBLIC DATA · AUTHENTICATION REQUIRED")
        sourceRow(public,"GDDKiA Geoportal · Road disruptions","Official visualisation service for GDDKiA road disruptions · layer/service still needs technical integration and attribution check","CANDIDATE · NOT CONNECTED")
        sourceRow(public,"Kraków MSIP / ZDMK","Municipal roadworks and restrictions · validate dataset freshness, geometry and service terms","OPEN DATA · CONNECTOR NOT BUILT")
        sourceRow(public,"ZTP Kraków / GTFS-RT","Transit disruption context · feed discovery and schema validation","NOT CONNECTED")
        sourceRow(public,"Air quality / severe weather","Official feed selection and quality validation","NOT CONNECTED")
        root.addView(public)

        val gated=card();gated.addView(label("COMMERCIAL / PARTNER CONNECTORS",10f,AMBER,true))
        listOf(
            Pair("TomTom Traffic / Incidents / Routing","Commercial credentials, Kraków coverage and display/storage rights"),
            Pair("HERE Traffic / Routing","Commercial credentials, incident coverage and entitlements"),
            Pair("Mapbox Traffic / Directions","Token, traffic coverage and product licence review"),
            Pair("Google Navigation SDK","Separate navigation SDK and usage-policy evaluation"),
            Pair("Waze for Cities","Partner programme access; no general public traffic API"),
            Pair("Uber Driver API","Approved driver-level access; no API connection is currently established"),
            Pair("Bolt Fleet API","Fleet-company integration; not a general individual-driver API"),
            Pair("Additional driver-app APIs","Provider-specific authorised read scopes and revocation path")
        ).forEach{sourceRow(gated,it.first,it.second,"NOT CONNECTED")}
        root.addView(gated)
        root.addView(card().apply{
            addView(label("DATA HONESTY",10f,MINT,true))
            addView(label("Map geometry is not traffic. Mapped roadworks are not a live closure feed. Weather is not a crash alert. Every future road event must carry its source, observed time, freshness, geometry, access terms and confidence. BusyBee never invents traffic flow or demand heatmaps.",9f,MUTED,false).apply{setPadding(0,dp(5),0,dp(8))})
            addView(action("REFRESH ROAD SOURCES",true){showTab("MAP");refreshRoadIntelligence()})
        })
        return root
    }
    private fun sourceRow(parent:LinearLayout,name:String,detail:String,status:String){
        val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(9),0,dp(9))}
        val healthy=status.startsWith("LIVE") || status.startsWith("ACTIVE") || status.startsWith("ENABLED") || status.startsWith("ALLOWED") || status.startsWith("PERMISSION GRANTED") || status=="USER SELECTED"
        row.addView(label(name,11f,WHITE,true))
        row.addView(label(detail,9f,MUTED,false).apply{setPadding(0,dp(3),0,dp(4))})
        row.addView(label(status,8f,if(healthy)MINT else AMBER,true))
        parent.addView(row);parent.addView(divider())
    }

    private fun isDriverAppLiveListenerEnabled():Boolean {
        val enabled=Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val targetClass=DriverAppAccessibilityService::class.java.name
        return enabled.split(':').any { raw ->
            ComponentName.unflattenFromString(raw)?.className==targetClass
        }
    }

    private fun openLiveListenerSettings() {
        if(isDriverAppLiveListenerEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Enable BusyBee live driver-app listening?")
            .setMessage("BusyBee will read visible text and screen-change events only from Uber Driver and Bolt Driver. It may capture trip amounts, distance, duration, route/address text and earnings values shown on those screens. The text stays in BusyBee's local review inbox. BusyBee will not read other apps, record audio, take screenshots, tap buttons, accept or decline rides, or change anything inside Uber/Bolt. You can turn the Android service off at any time in Accessibility Settings. Some app screens may not expose readable text.")
            .setNegativeButton("Not now",null)
            .setPositiveButton("I understand · OPEN SETTINGS") { _,_ ->
                getSharedPreferences("busybee_privacy",MODE_PRIVATE)
                    .edit().putBoolean("driver_app_listener_disclosure_v1",true).apply()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .show()
    }

    private fun requestBusyBeeAlerts() {
        if(Build.VERSION.SDK_INT>=33 &&
            ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.POST_NOTIFICATIONS),NOTIFICATION_REQUEST)
            return
        }
        val manager=getSystemService(android.app.NotificationManager::class.java)
        if(!manager.areNotificationsEnabled()) {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
            return
        }
        DriverAlertManager.setAlertsEnabled(this,true)
        Toast.makeText(this,"BusyBee driver alerts are enabled.",Toast.LENGTH_SHORT).show()
        showTab("OFFERS")
    }

    private fun notificationAccessEnabled():Boolean=try{
        Settings.Secure.getString(contentResolver,"enabled_notification_listeners")?.contains(packageName)==true
    }catch(_:Exception){false}
    private fun openPlatform(platform:String){
        val pkg=when(platform.lowercase()) {
            "bolt" -> "ee.mtakso.driver"
            "uber" -> "com.ubercab.driver"
            else -> null
        }
        if(pkg==null) {
            Toast.makeText(this,"Open "+platform+" Driver manually. This platform has no verified app handoff yet.",Toast.LENGTH_LONG).show()
            return
        }
        val i=packageManager.getLaunchIntentForPackage(pkg)
        if(i!=null)startActivity(i) else Toast.makeText(this,platform+" Driver app is not installed.",Toast.LENGTH_LONG).show()
    }
    private fun openUri(uri:Uri){try{startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}catch(_:Exception){Toast.makeText(this,"No app can open this document.",Toast.LENGTH_SHORT).show()}}

    private fun columnScroll(): ScrollView = ScrollableColumn(this).apply {
        isFillViewport = true
        setBackgroundColor(BG)
        contentLayout.setPadding(dp(14), dp(12), dp(14), dp(24))
    }
    private fun section(title:String,subtitle:String)=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(dp(2),dp(17),dp(2),dp(10))
        addView(label(title,13f,WHITE,true))
        addView(label(subtitle,11f,MUTED,false).apply{setPadding(0,dp(4),0,0)})
    }
    private fun card()=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(dp(16),dp(15),dp(16),dp(15))
        background=GradientDrawable().apply{
            setColor(CARD)
            cornerRadius=dp(17).toFloat()
            setStroke(dp(1),palette.stroke)
        }
        elevation=dp(2).toFloat()
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(11)}
    }
    private fun metric(title:String,value:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(9),dp(10),dp(7),dp(9));background=rounded(palette.secondary,12);addView(label(title,9f,MUTED,true));addView(label(value,12f,WHITE,true).apply{setPadding(0,dp(6),0,0)})}
    private fun label(value:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply{text=value;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD}
    private fun field(hintText:String,value:String)=EditText(this).apply{hint=hintText;setText(value);textSize=13f;setTextColor(WHITE);setHintTextColor(MUTED);setSingleLine(true);setPadding(dp(10),dp(9),dp(10),dp(9));background=rounded(palette.field,10);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(7)}}
    private fun action(title:String,primary:Boolean,onClick:()->Unit):TextView {
        val button=TextView(this).apply {
            text=title
            gravity=Gravity.CENTER
            textSize=10f
            typeface=Typeface.DEFAULT_BOLD
            letterSpacing=.025f
            minHeight=dp(42)
            setOnClickListener{safeUiAction(title){onClick()}}
        }
        themedActions[button]=primary
        applyActionPalette(button,primary)
        return button
    }
    private fun safeUiAction(actionName:String, action:()->Unit) {
        if(isFinishing || isDestroyed) return
        CrashDiagnostics.record(this,"tap:start:$actionName")
        try {
            action()
            CrashDiagnostics.record(this,"tap:complete:$actionName")
        } catch(error:Exception) {
            CrashDiagnostics.record(this,"tap:exception:$actionName:${error::class.java.simpleName}")
            CrashDiagnostics.recordActionFailure(this,actionName,error)
            Log.e("BusyBee","UI action failed: $actionName",error)
            Toast.makeText(this,"$actionName failed (${error.javaClass.simpleName}). Open DIAG for details.",Toast.LENGTH_LONG).show()
        }
    }

    private fun addViewAction(parent:LinearLayout,title:String,onClick:()->Unit){parent.addView(action(title,true,onClick),LinearLayout.LayoutParams(-1,dp(40)).apply{topMargin=dp(9)})}
    private fun divider()=View(this).apply{setBackgroundColor(palette.divider);layoutParams=LinearLayout.LayoutParams(-1,dp(1))}
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun num(key:String,default:Double)=commands.setting(key,default.toString()).replace(',','.').toDoubleOrNull()?:default
    private fun money(value:Double?)=value?.let{String.format(Locale.US,"%.2f PLN",it)}?:"—"
    private fun date(ms:Long)=SimpleDateFormat("dd MMM yyyy",Locale.getDefault()).format(Date(ms))
    private fun dateTime(ms:Long)=SimpleDateFormat("dd MMM · HH:mm",Locale.getDefault()).format(Date(ms))
    private fun parseDate(s:String):Long?=try{SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}.parse(s)?.time}catch(_:Exception){null}

    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(DriverAlertManager.EXTRA_OPEN_TAB)?.let { requested ->
            if(requested in setOf("OFFERS","EARNINGS","DRIVER HUB","SOURCES")) showTab(requested)
        }
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        when(requestCode) {
            LOCATION_REQUEST -> if(grantResults.any{it==PackageManager.PERMISSION_GRANTED}) requestLocation()
            NOTIFICATION_REQUEST -> {
                val granted=grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED
                DriverAlertManager.setAlertsEnabled(this,granted)
                Toast.makeText(this,if(granted)"BusyBee driver alerts enabled." else "BusyBee alerts remain off. You can enable them in Android settings.",Toast.LENGTH_LONG).show()
                if(selectedTab=="OFFERS") showTab("OFFERS")
            }
        }
    }
    override fun onStart(){CrashDiagnostics.record(this,"lifecycle:onStart");super.onStart();mapView.onStart()}
    override fun onResume(){
        CrashDiagnostics.record(this,"lifecycle:onResume")
        super.onResume()
        updateTimeBasedAppearance()
        appearanceHandler.removeCallbacks(appearanceTick)
        appearanceHandler.postDelayed(appearanceTick,15*60*1000L)
        mapView.onResume()
        if(map!=null && activeMapStyleUri!=palette.mapStyleUri) reloadMapStyle()
        if(::locationManager.isInitialized) {
            // Apply only a fresh fix if a callback arrived after onPause() removed updates.
            lastLocation?.takeIf { System.currentTimeMillis()-it.time in 0..120000L }?.let(::updateDriverLocation)
            if(navigating || followLocation) startLocationUpdates()
        }
    }
    override fun onPostResume() {
        super.onPostResume()
        // onResume() can run before lifecycle reaches RESUMED. MapLibre style
        // switches are deferred until this boundary to avoid mutating a paused view.
        if(map!=null && activeMapStyleUri!=palette.mapStyleUri) reloadMapStyle()
        activateMapAfterStyleIfResumed()
    }

    override fun onPause(){CrashDiagnostics.record(this,"lifecycle:onPause");appearanceHandler.removeCallbacks(appearanceTick);try{locationManager.removeUpdates(locationListener)}catch(_:Exception){};mapView.onPause();super.onPause()}
    override fun onStop(){CrashDiagnostics.record(this,"lifecycle:onStop");mapView.onStop();super.onStop()}
    override fun onLowMemory(){super.onLowMemory();mapView.onLowMemory()}
    override fun onSaveInstanceState(outState:Bundle){super.onSaveInstanceState(outState);mapView.onSaveInstanceState(outState)}
    override fun onDestroy(){
        CrashDiagnostics.record(this,"lifecycle:onDestroy")
        appearanceHandler.removeCallbacks(appearanceTick)
        if(::locationManager.isInitialized) runCatching { locationManager.removeUpdates(locationListener) }
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        worker.shutdownNow()
        mapView.onDestroy()
        super.onDestroy()
    }

    companion object{
        private const val LOCATION_REQUEST=731
        private const val NOTIFICATION_REQUEST=732
        private val KRAKOW=LatLng(50.0647,19.9450)
    }
}
