package com.ysfclc.busybee.drivercommandcentre

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private lateinit var nav:LinearLayout
    private val worker=Executors.newFixedThreadPool(2)
    private var map:MapLibreMap?=null
    private var lastLocation:Location?=null
    private var tilted=true
    private var roadEvents:List<RoadEvent> = emptyList()
    private val eventMarkers=mutableListOf<Marker>()
    private val reportMarkers=mutableListOf<Marker>()
    private val routeMarkers=mutableListOf<Marker>()
    private var routePolyline:Polyline?=null
    private var routeSummary:TextView?=null
    private var routeDestinationInput:EditText?=null
    private var activeRoute:RoutePlan?=null
    private var mapStatus:TextView?=null
    private var mapSummary:TextView?=null
    private var weatherSummary="Weather feed not checked"
    private var weatherText:TextView?=null
    private val selectedLayers= mutableSetOf("Roadworks","Hazards","Driver reports")
    private val navButtons=mutableMapOf<String,TextView>()
    private var activeTab="MAP"
    private var activityFilter="ALL"
    private var activityProviderFilter:String?=null
    private var activityItems:List<UnifiedActivityItem> = emptyList()
    private var activityContainer:LinearLayout?=null
    private var activityProviderFilterContainer:LinearLayout?=null
    private val activityFilterButtons=mutableMapOf<String,TextView>()
    private val activityProviderFilterButtons=mutableMapOf<String,TextView>()
    private var ledgerMigrationStatus="Migration diagnostics have not run yet."
    private var ledgerMigrationContainer:LinearLayout?=null
    private val locationListener=object:LocationListener {
        override fun onLocationChanged(location:Location) {
            lastLocation=location
            mapStatus?.text="● LOCATION LIVE"
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude,location.longitude),15.5),500)
        }
    }
    private val documentPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)importDocument(uri)}
    private val chatPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)importCommunity(uri)}
    private val csvPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)importCsv(uri)}

    private fun syncUnifiedActivityLedger() {
        worker.execute {
            try {
                val report = UnifiedActivityLedgerMigrator(applicationContext).migrateIfNeeded()
                val status=MigrationDiagnosticsFormatter.format(report)
                android.util.Log.d(
                    "BusyBeeLedger",
                    "Legacy sync copied=${report.copiedRows}, complete=${report.complete}, missing=${report.missingDatabases.joinToString()}"
                )
                runOnUiThread {
                    ledgerMigrationStatus=status
                    if (activeTab == "ACTIVITY") refreshUnifiedActivity()
                    if (activeTab == "SOURCES") renderMigrationDiagnostics()
                }
            } catch (error: Exception) {
                val status="MIGRATION ERROR\n${error.javaClass.simpleName}: ${error.message ?: "No details"}"
                android.util.Log.e("BusyBeeLedger", "Legacy activity sync failed", error)
                runOnUiThread { ledgerMigrationStatus=status; if(activeTab=="SOURCES") renderMigrationDiagnostics() }
            }
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        commands=DriverCommandStore(this);mirror=DriverMirrorStore(this)
        syncUnifiedActivityLedger()
        locationManager=getSystemService(LOCATION_SERVICE) as LocationManager
        MapLibre.getInstance(this)
        window.statusBarColor=BG;window.navigationBarColor=BG
        mapView=MapView(this);mapView.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(BG)}
        root.addView(buildHeader())
        content=FrameLayout(this);root.addView(content,LinearLayout.LayoutParams(-1,0,1f))
        nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(7),dp(7),dp(7),dp(9));setBackgroundColor(PANEL)}
        root.addView(nav);setContentView(root)
        buildNavigation();showTab("MAP")
        mapView.getMapAsync { instance ->
            map=instance
            instance.setStyle(Style.Builder().fromUri(OPENFREE_MAP_STYLE)) {
                instance.cameraPosition=CameraPosition.Builder().target(KRAKOW).zoom(15.0).tilt(48.0).build()
                mapStatus?.text="● MAP ONLINE";refreshRoadIntelligence()
            }
        }
    }

    private fun buildHeader():View {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(17),dp(12),dp(17),dp(10));setBackgroundColor(BG)}
        val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        row.addView(label("BUSYBEE",21f,WHITE,true).apply{letterSpacing=.12f})
        row.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
        row.addView(label("DRIVER COMMAND CENTRE",9f,MINT,true))
        box.addView(row)
        box.addView(label("ROAD INTELLIGENCE  ·  OFFER DESK  ·  DRIVER LIFE",9f,MUTED,true).apply{setPadding(0,dp(3),0,0);letterSpacing=.06f})
        return box
    }

    private fun buildNavigation() {
        nav.removeAllViews()
        listOf("MAP","OFFERS","ACTIVITY","EARNINGS","DRIVER HUB","SOURCES").forEach { tab ->
            val display = if (tab == "DRIVER HUB") "HUB" else tab
            val b=label(display,8f,MUTED,true).apply{gravity=Gravity.CENTER;setPadding(dp(3),dp(12),dp(3),dp(12));setOnClickListener{showTab(tab)}}
            navButtons[tab]=b;nav.addView(b,LinearLayout.LayoutParams(0,-1,1f))
        }
    }

    private fun showTab(tab:String) {
        activeTab=tab
        content.removeAllViews()
        navButtons.forEach{(key,b)->b.setTextColor(if(key==tab)MINT else MUTED);b.background=rounded(if(key==tab)0xFF1B343B.toInt() else Color.TRANSPARENT,11)}
        val view=when(tab) {
            "MAP"->buildMapScreen()
            "OFFERS"->buildOffersScreen()
            "EARNINGS"->buildEarningsScreen()
            "DRIVER HUB"->buildDriverHubScreen()
            "ACTIVITY"->buildActivityScreen()
            else->buildSourcesScreen()
        }
        content.addView(view,FrameLayout.LayoutParams(-1,-1))
    }

    private fun buildMapScreen():View {
        val frame=FrameLayout(this);frame.addView(mapView,FrameLayout.LayoutParams(-1,-1))
        val overlay=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10))}
        val top=card()
        val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        mapStatus=label("● CONNECTING MAP",10f,MINT,true);row.addView(mapStatus)
        row.addView(View(this),LinearLayout.LayoutParams(0,1,1f));row.addView(label("3D 42°",10f,WHITE,true))
        top.addView(row)
        mapSummary=label("Road intelligence loading · Kraków pilot",10f,MUTED,false).apply{setPadding(0,dp(5),0,0)}
        top.addView(mapSummary)
        val layerScroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;setPadding(0,dp(9),0,0)}
        val layerRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("Roadworks","Hazards","Driver reports").forEach{name->
            val chip=label(name.uppercase(),9f,WHITE,true).apply{
                gravity=Gravity.CENTER;setPadding(dp(11),dp(9),dp(11),dp(9))
                background=rounded(if(selectedLayers.contains(name))MINT else 0xFF263B46.toInt(),12)
                setTextColor(if(selectedLayers.contains(name))BG else WHITE)
                setOnClickListener{if(!selectedLayers.add(name))selectedLayers.remove(name);setTextColor(if(selectedLayers.contains(name))BG else WHITE);background=rounded(if(selectedLayers.contains(name))MINT else 0xFF263B46.toInt(),12);renderRoadEvents()}
            }
            layerRow.addView(chip,LinearLayout.LayoutParams(-2,dp(34)).apply{marginEnd=dp(6)})
        }
        layerScroll.addView(layerRow);top.addView(layerScroll);overlay.addView(top)
        overlay.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
        val bottom=card()
        bottom.addView(label("LIVE ROAD PICTURE",11f,MINT,true))
        weatherText=label(weatherSummary,11f,WHITE,false).apply{setPadding(0,dp(5),0,dp(5))}
        bottom.addView(weatherText)
        routeSummary=label("Plan a route to compare distance and estimated time.",9f,MUTED,false).apply{setPadding(0,dp(2),0,dp(7))}
        bottom.addView(routeSummary)
        val routeRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        routeDestinationInput=EditText(this).apply{
            hint="Where are you going?"
            setSingleLine(true);textSize=12f;setTextColor(WHITE);setHintTextColor(MUTED)
            setPadding(dp(10),dp(8),dp(10),dp(8));background=rounded(0xFF1B303A.toInt(),10)
            layoutParams=LinearLayout.LayoutParams(0,dp(42),1f)
        }
        routeRow.addView(routeDestinationInput)
        routeRow.addView(action("ROUTE",true){planRoute(routeDestinationInput?.text?.toString().orEmpty())},LinearLayout.LayoutParams(dp(78),dp(42)).apply{marginStart=dp(7)})
        bottom.addView(routeRow)
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        actions.addView(action("◎ LOCATE",true){requestLocation()},LinearLayout.LayoutParams(0,dp(42),1f))
        actions.addView(action("↻ REFRESH",false){refreshRoadIntelligence()},LinearLayout.LayoutParams(0,dp(42),1f).apply{marginStart=dp(7)})
        actions.addView(action(if(tilted)"3D ON" else "3D OFF",false){
            tilted=!tilted;val p=map?.cameraPosition
            if(p!=null)map?.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(p.target?:KRAKOW).zoom(p.zoom).bearing(p.bearing).tilt(if(tilted)42.0 else 0.0).build()),300)
            showTab("MAP")
        },LinearLayout.LayoutParams(0,dp(42),1f).apply{marginStart=dp(7)})
        bottom.addView(actions)
        bottom.addView(action("START TURN-BY-TURN IN MAPS",false){openExternalNavigation()},LinearLayout.LayoutParams(-1,dp(36)).apply{topMargin=dp(7)})
        bottom.addView(action("＋ REPORT A ROAD HAZARD",false){reportRoadHazard()},LinearLayout.LayoutParams(-1,dp(36)).apply{topMargin=dp(6)})
        overlay.addView(bottom);frame.addView(overlay,FrameLayout.LayoutParams(-1,-1));return frame
    }

    private fun planRoute(query:String) {
        if(query.isBlank()) { Toast.makeText(this,"Enter a destination first.",Toast.LENGTH_SHORT).show(); return }
        val fix=lastLocation
        val camera=map?.cameraPosition?.target?:KRAKOW
        val origin=if(fix!=null)LatLng(fix.latitude,fix.longitude) else camera
        routeSummary?.text="Finding destination and comparing route options…"
        worker.execute {
            try {
                val plans=RoutePlanner.planAlternatives(origin,query)
                runOnUiThread {
                    displayRoute(plans.first(),origin,"FASTEST")
                    if(plans.size>1) {
                        val labels=plans.mapIndexed { index, plan ->
                            val kind=if(index==0)"FASTEST" else "ALTERNATIVE $index"
                            String.format(Locale.US,"%s  ·  %.1f km  ·  %.0f min",kind,plan.distanceKm,plan.durationMinutes)
                        }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Compare routes · OSRM estimates")
                            .setItems(labels) { _, selected -> displayRoute(plans[selected],origin,if(selected==0)"FASTEST" else "ALTERNATIVE $selected") }
                            .setNegativeButton("Keep fastest",null)
                            .show()
                    }
                }
            } catch(e:Exception) {
                runOnUiThread { routeSummary?.text="Route unavailable: "+(e.message?:"source error");mapStatus?.text="● ROUTING UNAVAILABLE" }
            }
        }
    }

    private fun displayRoute(plan:RoutePlan,origin:LatLng,kind:String) {
        routePolyline?.let { map?.removeAnnotation(it) }
        routeMarkers.forEach { map?.removeAnnotation(it) };routeMarkers.clear()
        routePolyline=map?.addPolyline(PolylineOptions().addAll(plan.points).color(MINT).width(5f))
        map?.addMarker(MarkerOptions().position(origin).title("Route starts here"))?.let{routeMarkers.add(it)}
        map?.addMarker(MarkerOptions().position(plan.destination).title("Destination").snippet(plan.destinationName))?.let{routeMarkers.add(it)}
        activeRoute=plan
        routeSummary?.text=String.format(Locale.US,"%s · %.1f km · %.0f min estimated · OSRM (not live traffic)",kind,plan.distanceKm,plan.durationMinutes)
        mapStatus?.text="● ROUTE READY"
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(plan.destination,13.0),500)
    }

    private fun openExternalNavigation() {
        val route=activeRoute
        if(route==null) { Toast.makeText(this,"Plan a route first.",Toast.LENGTH_SHORT).show();return }
        val uri=Uri.parse("google.navigation:q="+route.destination.latitude+","+route.destination.longitude+"&mode=d")
        try { startActivity(Intent(Intent.ACTION_VIEW,uri)) }
        catch(_:Exception) { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+route.destination.latitude+","+route.destination.longitude))) }
    }

    private fun refreshRoadIntelligence() {
        val center=map?.cameraPosition?.target?:KRAKOW
        mapStatus?.text="● SOURCES UPDATING";mapSummary?.text="Checking mapped road events and live weather…"
        worker.execute {
            var events:List<RoadEvent> = emptyList();var roadError:String?=null
            try{events=fetchRoadEvents(center.latitude,center.longitude)}catch(e:Exception){roadError=e.message}
            val weather=try{fetchWeather(center.latitude,center.longitude)}catch(e:Exception){"Weather source unavailable"}
            runOnUiThread{
                roadEvents=events;weatherSummary=weather;weatherText?.text=weather;renderRoadEvents()
                mapStatus?.text=if(roadError==null)"● ROAD FEED UPDATED" else "● PARTIAL SOURCE COVERAGE"
                mapSummary?.text=events.size.toString()+" mapped road observations · OpenStreetMap / Overpass"
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
        val active=map?:return
        eventMarkers.forEach{active.removeAnnotation(it)};eventMarkers.clear()
        roadEvents.filter{selectedLayers.contains(it.category)}.forEach{e->active.addMarker(MarkerOptions().position(e.point).title(e.title).snippet(e.category+" · "+e.detail+" · OSM"))?.let{eventMarkers.add(it)}}
        reportMarkers.forEach{active.removeAnnotation(it)};reportMarkers.clear()
        if(selectedLayers.contains("Driver reports"))commands.roadReports().forEach{r->active.addMarker(MarkerOptions().position(LatLng(r.latitude,r.longitude)).title(r.category).snippet(r.description+" · Private driver report"))?.let{reportMarkers.add(it)}}
    }

    private fun requestLocation(){
        val fine=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val coarse=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!fine&&!coarse){ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),LOCATION_REQUEST);return}
        try{
            val providers=locationManager.getProviders(true).filter{it==LocationManager.GPS_PROVIDER||it==LocationManager.NETWORK_PROVIDER}
            providers.forEach{p->locationManager.getLastKnownLocation(p)?.let{if(lastLocation==null||it.time>lastLocation!!.time)lastLocation=it};locationManager.requestLocationUpdates(p,5000L,20f,locationListener)}
            lastLocation?.let{map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude,it.longitude),15.5),500)}
            mapStatus?.text="● LOCATION REQUESTED"
        }catch(_:SecurityException){mapStatus?.text="● LOCATION PERMISSION REQUIRED"}
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
        root.addView(section("OFFER COMMAND","Capture platform offers, calculate trip economics, then decide."))
        val rules=card();rules.addView(label("YOUR DECISION RULES",11f,MINT,true))
        val cost=field("Vehicle cost per km (PLN)",commands.setting("cost_per_km","0.85"))
        val minHour=field("Minimum net per hour (PLN)",commands.setting("min_per_hour","35"))
        val maxPickup=field("Maximum pickup distance (km)",commands.setting("max_pickup","8"))
        val minRating=field("Minimum passenger rating (0 = ignore)",commands.setting("min_rating","0"))
        listOf(cost,minHour,maxPickup,minRating).forEach{rules.addView(it)}
        rules.addView(action("SAVE OFFER RULES",true){
            commands.setSetting("cost_per_km",cost.text.toString().replace(',','.').ifBlank{"0.85"})
            commands.setSetting("min_per_hour",minHour.text.toString().replace(',','.').ifBlank{"35"})
            commands.setSetting("max_pickup",maxPickup.text.toString().replace(',','.').ifBlank{"8"})
            commands.setSetting("min_rating",minRating.text.toString().replace(',','.').ifBlank{"0"})
            showTab("OFFERS")
        });root.addView(rules)
        val capture=card();capture.addView(label("PLATFORM SIGNALS",11f,MINT,true))
        capture.addView(label("Read visible ride-platform notifications after you explicitly enable Android notification access. No private app database access.",11f,MUTED,false))
        capture.addView(action(if(notificationAccessEnabled())"NOTIFICATION ACCESS ENABLED · SETTINGS" else "ENABLE UBER / BOLT OFFER CAPTURE",false){startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))})
        root.addView(capture)
        val add=card();add.addView(label("MANUAL OFFER",11f,MINT,true));add.addView(label("Complete details when the platform notification does not expose enough information.",10f,MUTED,false))
        addViewAction(add,"＋ ADD OFFER"){offerDialog()};root.addView(add)
        val ranked=OfferEngine.rank(commands.offers(),num("cost_per_km",0.85),num("min_per_hour",35.0),num("max_pickup",8.0),num("min_rating",0.0))
        root.addView(section("RANKED OFFERS",ranked.size.toString()+" offers meet your rules · sorted by estimated net PLN/hour"))
        if(ranked.isEmpty())root.addView(card().apply{addView(label("No qualifying offers yet.",15f,WHITE,true));addView(label("Enable notification capture or add an offer. No demo offers are inserted.",11f,MUTED,false))})
        ranked.forEach{root.addView(offerCard(it))}
        root.addView(section("CAPTURED PLATFORM NOTIFICATIONS","Raw notification text; review and convert into a structured offer."))
        val notices=commands.notices()
        if(notices.isEmpty())root.addView(card().apply{addView(label("No ride-platform notifications captured.",11f,MUTED,false))})
        notices.forEach{n->root.addView(card().apply{
            addView(label(n.platform+" · "+dateTime(n.capturedAt),10f,MINT,true));addView(label(n.title,13f,WHITE,true))
            addView(label(n.body,11f,MUTED,false).apply{setPadding(0,dp(4),0,dp(7))})
            addViewAction(this,"CREATE OFFER FROM NOTICE"){offerDialog(n.platform,n.body)}
        })}
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
            actions.addView(action("SKIP",false){commands.updateOfferStatus(o.id,"skipped");syncUnifiedActivityLedger();showTab("OFFERS")},LinearLayout.LayoutParams(dp(74),dp(40)).apply{marginStart=dp(7)})
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
            syncUnifiedActivityLedger()
            showTab("OFFERS")
        }.show()
    }

    private fun buildEarningsScreen():View{
        val root=columnScroll();val s=mirror.summary()
        root.addView(section("YOUR DRIVER MIRROR","Private trip and earnings history across imported platforms."))
        val totals=card();totals.addView(label(s.trips.toString()+" TRIPS  ·  "+s.offers+" OFFERS  ·  "+s.records+" RECORDS",13f,WHITE,true))
        totals.addView(label("Platforms: "+s.platforms.joinToString().ifBlank{"none yet"}+" · "+s.imports+" imports",10f,MUTED,false).apply{setPadding(0,dp(4),0,dp(10))})
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        row.addView(metric("GROSS",money(s.gross)),LinearLayout.LayoutParams(0,dp(77),1f))
        row.addView(metric("FEES",money(s.fees)),LinearLayout.LayoutParams(0,dp(77),1f).apply{marginStart=dp(6)})
        row.addView(metric("BONUSES",money(s.bonuses)),LinearLayout.LayoutParams(0,dp(77),1f).apply{marginStart=dp(6)})
        totals.addView(row);totals.addView(label("KNOWN NET",9f,MUTED,true).apply{setPadding(0,dp(11),0,dp(2))})
        totals.addView(label(s.knownNet?.let{money(it)}?:"INCOMPLETE",24f,MINT,true))
        totals.addView(label("PLN records only. Fees and bonuses must be explicit for every gross record. Vehicle costs, taxes and missing adjustments are excluded.",10f,MUTED,false).apply{setPadding(0,dp(4),0,0)})
        root.addView(totals)
        val recordedExpenses=commands.totalAmount("Expense",true)
        root.addView(card().apply {
            addView(label("RECORDED VEHICLE EXPENSES · PAID",9f,MUTED,true))
            addView(label(money(recordedExpenses),19f,WHITE,true).apply{setPadding(0,dp(5),0,dp(2))})
            addView(label("Completed Expense items only. Kept separate until costs are matched to the same trip/date period.",10f,MUTED,false))
            addView(action("＋ LOG VEHICLE EXPENSE",true){itemDialog("Expense")},LinearLayout.LayoutParams(-1,dp(38)).apply{topMargin=dp(8)})
        })
        root.addView(section("VEHICLE ECONOMICS","The same cost model is used to rank incoming offers."))
        val cost=field("All-in vehicle cost per km (PLN)",commands.setting("cost_per_km","0.85"))
        root.addView(card().apply{addView(cost);addView(action("SAVE COST MODEL",true){commands.setSetting("cost_per_km",cost.text.toString().replace(',','.').ifBlank{"0.85"});showTab("EARNINGS")})})
        val analytics=DriverAnalyticsEngine.calculate(mirror.recent(5000),num("cost_per_km",0.85))
        root.addView(section("WHAT YOUR DATA SAYS","Evidence-based economics. Estimated vehicle cost uses your saved per-km assumption; no missing values are filled in."))
        root.addView(card().apply {
            addView(label("COVERAGE",11f,MINT,true))
            addView(label("${analytics.plnTripCount} PLN trips · gross ${analytics.tripsWithGross} · fees ${analytics.tripsWithFees} · bonuses ${analytics.tripsWithBonus} · distance ${analytics.tripsWithDistance} · time ${analytics.tripsWithTime}",10f,WHITE,false).apply{setPadding(0,dp(5),0,dp(5))})
            addView(label("Estimated net after vehicle cost",9f,MUTED,true))
            addView(label(analytics.estimatedNetPln?.let{money(it)}?:"NOT ENOUGH COMPLETE DATA",21f,MINT,true).apply{setPadding(0,dp(3),0,dp(3))})
            addView(label("${analytics.estimatedNetTrips} trips include gross, fees, bonus and distance. This estimate excludes tax, unpaid waiting time and costs not represented by your per-km setting.",10f,MUTED,false))
        })
        if(analytics.platforms.isNotEmpty()) {
            root.addView(section("PLATFORM COMPARISON","Only comparable PLN trips. Incomplete net rows are excluded from net calculations."))
            analytics.platforms.forEach { p ->
                root.addView(card().apply {
                    addView(label(p.platform.uppercase()+" · "+p.tripCount+" TRIPS",11f,MINT,true))
                    addView(label("Gross "+money(p.grossPln)+" · fees "+money(p.feesPln)+" · bonuses "+money(p.bonusesPln),10f,WHITE,false).apply{setPadding(0,dp(5),0,dp(3))})
                    addView(label("Known net before vehicle "+money(p.knownNetBeforeVehiclePln),10f,MUTED,false))
                    addView(label("Estimated vehicle cost "+money(p.estimatedVehicleCostPln)+" · net after vehicle "+money(p.estimatedNetAfterVehiclePln),10f,MUTED,false).apply{setPadding(0,dp(3),0,dp(3))})
                    addView(label("${p.completeNetTrips}/${p.tripCount} complete net rows · ${p.tripsWithDistance}/${p.tripCount} with distance",9f,AMBER,true))
                })
            }
        }
        if(analytics.hours.isNotEmpty()) {
            root.addView(section("START-TIME SIGNAL","Gross and complete pre-vehicle net grouped by recorded trip start hour. Not an hourly wage."))
            analytics.hours.take(8).forEach { h ->
                root.addView(card().apply {
                    addView(label(String.format(Locale.US,"%02d:00–%02d:00",h.hour,h.hour+1)+" · "+h.trips+" trips",12f,WHITE,true))
                    addView(label("Gross "+money(h.grossPln)+" · complete net before vehicle "+money(h.completeNetPln),10f,MUTED,false).apply{setPadding(0,dp(4),0,0)})
                })
            }
        }
        if(analytics.dataWarnings.isNotEmpty()) {
            root.addView(card().apply {
                addView(label("DATA GAPS · DO NOT OVER-INTERPRET",10f,AMBER,true))
                analytics.dataWarnings.forEach { warning -> addView(label("• "+warning,10f,MUTED,false).apply{setPadding(0,dp(5),0,0)}) }
            })
        }
        root.addView(section("IMPORT HISTORY & ORIGINAL EVIDENCE","Every import is retained with its raw rows and source filename."))
        mirror.importHistory().forEach { entry -> root.addView(label("• "+entry,10f,MUTED,false).apply{setPadding(dp(5),dp(3),0,dp(3))}) }
        mirror.evidencePreview(5).forEach { evidence -> root.addView(card().apply { addView(label(evidence,9f,MUTED,false)) }) }
        root.addView(section("RECENT MIRRORED ACTIVITY","Source-backed records; missing fields remain unknown."))
        mirror.recent(30).forEach{r->root.addView(card().apply{
            addView(label(r.platform+" · "+r.type.uppercase(),11f,MINT,true))
            addView(label((r.occurredAt?:"Date not supplied")+" · "+(r.pickup?:"?")+" → "+(r.dropoff?:"?"),11f,WHITE,true).apply{setPadding(0,dp(4),0,dp(4))})
            addView(label("Gross "+money(r.gross)+" · Fees "+money(r.fees)+" · Bonus "+money(r.bonus),10f,MUTED,false))
        })}
        root.addView(card().apply{addView(action("IMPORT PLATFORM STATEMENT",true){showTab("DRIVER HUB")})})
        return root
    }

    private fun buildDriverHubScreen():View{
        val root=columnScroll()
        root.addView(section("DRIVER LIFE","Documents, insurance, payment plans, vehicle services and community knowledge."))
        val top=card();top.addView(label("YOUR DRIVER ADMIN",11f,MINT,true))
        top.addView(label("Keep insurance renewals, instalments, recurring payments, documents, vehicle costs and service reminders together.",11f,MUTED,false))
        val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        buttons.addView(action("＋ REMINDER",true){itemDialog()},LinearLayout.LayoutParams(0,dp(42),1f))
        buttons.addView(action("＋ DOCUMENT",false){documentPicker.launch(arrayOf("*/*"))},LinearLayout.LayoutParams(0,dp(42),1f).apply{marginStart=dp(7)})
        top.addView(buttons)
        top.addView(action("＋ IMPORT PLATFORM CSV",false){csvPicker.launch(arrayOf("text/*","application/csv","application/vnd.ms-excel"))},LinearLayout.LayoutParams(-1,dp(40)).apply{topMargin=dp(7)})
        root.addView(top)
        root.addView(section("UPCOMING & SAVED ITEMS","Tap complete when paid, renewed or serviced. Documents can be opened from here."))
        val items=commands.items()
        if(items.isEmpty())root.addView(card().apply{addView(label("Nothing saved yet. Add an insurance renewal, payment, document expiry, maintenance or expense.",11f,MUTED,false))})
        items.forEach{item->root.addView(card().apply{
            addView(label(item.kind.uppercase()+" · "+(item.dueAt?.let{date(it)}?:"No due date")+(item.recurrence?.let{" · "+it}?: ""),9f,MINT,true))
            addView(label(item.title,14f,WHITE,true).apply{setPadding(0,dp(4),0,dp(3))})
            addView(label(item.details.ifBlank{"No details"}+(item.amount?.let{" · "+money(it)}?:""),10f,MUTED,false))
            val row=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL}
            row.addView(action(if(item.done)"COMPLETED" else "MARK DONE",false){commands.completeItem(item.id,!item.done);showTab("DRIVER HUB")},LinearLayout.LayoutParams(0,dp(36),1f))
            if(item.uri!=null)row.addView(action("OPEN FILE",true){openUri(Uri.parse(item.uri))},LinearLayout.LayoutParams(0,dp(36),1f).apply{marginStart=dp(7)})
            addView(row.apply{setPadding(0,dp(9),0,0)})
        })}
        val paymentHistory=items.filter{it.done}
        root.addView(section("PAYMENT & COMPLETION HISTORY",paymentHistory.size.toString()+" completed payments, renewals and services"))
        if(paymentHistory.isEmpty()) root.addView(card().apply{addView(label("Completed items will appear here as your payment and service history.",10f,MUTED,false))})
        paymentHistory.forEach{item->root.addView(card().apply{
            addView(label(item.kind.uppercase()+" · "+(item.completedAt?.let{dateTime(it)}?:"Completed"),9f,MINT,true))
            addView(label(item.title,12f,WHITE,true))
            addView(label(item.details+" · "+(item.amount?.let{money(it)}?:"Amount not recorded"),10f,MUTED,false))
        })}
        root.addView(section("WHATSAPP KNOWLEDGE BASE","Import a chat export yourself; BusyBee indexes it locally and privately."))
        val community=card();community.addView(action("IMPORT WHATSAPP .TXT EXPORT",true){chatPicker.launch(arrayOf("text/plain"))})
        val search=field("Search driver group knowledge","");community.addView(search)
        val results=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};community.addView(results)
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){results.removeAllViews();commands.communitySearch(s?.toString().orEmpty()).forEach{results.addView(label(it,10f,WHITE,false).apply{setPadding(0,dp(6),0,dp(6))})}}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        commands.communityDocuments().forEach{community.addView(label("INDEXED · "+it.title,10f,MINT,true).apply{setPadding(0,dp(7),0,0)})}
        root.addView(community)
        root.addView(section("VEHICLE SERVICES & DRIVER NETWORK","Save trusted mechanics, tyre shops, insurers, car washes, fleet contacts and deals."))
        root.addView(card().apply{
            addView(action("＋ SAVE SERVICE / CONTACT",false){itemDialog("Service")})
            commands.items("Service").forEach{addView(label(it.title+" · "+it.details,11f,WHITE,false).apply{setPadding(0,dp(7),0,dp(2))})}
        })
        return root
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
    private fun importCommunity(uri:Uri){
        try{
            val name=contentResolver.query(uri,null,null,null,null)?.use{c->val i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(c.moveToFirst()&&i>=0)c.getString(i)else"WhatsApp chat export"}?:"WhatsApp chat export"
            val text=contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:error("File could not be read")
            commands.addCommunityDocument(name,text);Toast.makeText(this,"Imported into private local search",Toast.LENGTH_LONG).show();showTab("DRIVER HUB")
        }catch(e:Exception){Toast.makeText(this,"Chat import failed: "+e.message,Toast.LENGTH_LONG).show()}
    }
    private fun importCsv(uri:Uri){
        try{
            val name=contentResolver.query(uri,null,null,null,null)?.use{c->val i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(c.moveToFirst()&&i>=0)c.getString(i)else"platform-export.csv"}?:"platform-export.csv"
            val text=contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:error("File could not be opened")
            val rows=DriverCsv.parse(text);require(rows.isNotEmpty()){"No data rows found"};val result=mirror.importRows(name,rows)
            syncUnifiedActivityLedger()
            Toast.makeText(this,"Imported "+result.second+" new records from "+result.first+" rows",Toast.LENGTH_LONG).show();showTab("EARNINGS")
        }catch(e:Exception){Toast.makeText(this,"Import failed: "+e.message,Toast.LENGTH_LONG).show()}
    }

    private fun buildActivityScreen():View {
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(13),dp(12),dp(13),dp(8))
            setBackgroundColor(BG)
        }
        val intro=card()
        intro.addView(label("UNIFIED ACTIVITY",12f,MINT,true))
        intro.addView(label("Local evidence ledger · provider observations and canonical events",10f,MUTED,false).apply {
            setPadding(0,dp(4),0,dp(7))
        })
        intro.addView(action("↻ REFRESH LOCAL LEDGER",true){refreshUnifiedActivity()},LinearLayout.LayoutParams(-1,dp(38)))
        root.addView(intro)

        val filters=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding(0,0,0,dp(8))
        }
        activityFilterButtons.clear()
        listOf("ALL","OBSERVATIONS","EVENTS").forEach { filter ->
            val chip=label(filter,8f,MUTED,true).apply {
                gravity=Gravity.CENTER
                setPadding(dp(9),dp(9),dp(9),dp(9))
                background=rounded(0xFF263B46.toInt(),10)
                setOnClickListener {
                    activityFilter=filter
                    updateActivityFilterStyles()
                    renderUnifiedActivity()
                }
            }
            activityFilterButtons[filter]=chip
            filters.addView(chip,LinearLayout.LayoutParams(0,dp(34),1f).apply{marginEnd=dp(5)})
        }
        root.addView(filters)

        root.addView(label("PROVIDER SOURCE",8f,MUTED,true).apply{setPadding(dp(2),0,0,dp(5))})
        val providerScroll=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
        activityProviderFilterContainer=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        providerScroll.addView(activityProviderFilterContainer)
        root.addView(providerScroll,LinearLayout.LayoutParams(-1,dp(42)))

        val scroll=ScrollView(this).apply { isFillViewport=true }
        activityContainer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(activityContainer)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        updateActivityFilterStyles()
        refreshUnifiedActivity()
        return root
    }

    private fun updateActivityFilterStyles() {
        activityFilterButtons.forEach { (filter,button) ->
            val selected=filter==activityFilter
            button.setTextColor(if(selected)BG else MUTED)
            button.background=rounded(if(selected)MINT else 0xFF263B46.toInt(),10)
        }
        activityProviderFilterButtons.forEach { (provider,button) ->
            val selected=if(provider=="ALL") activityProviderFilter==null else provider.equals(activityProviderFilter,ignoreCase=true)
            button.setTextColor(if(selected)BG else MUTED)
            button.background=rounded(if(selected)MINT else 0xFF263B46.toInt(),10)
        }
    }

    private fun renderProviderFilters() {
        val target=activityProviderFilterContainer ?: return
        target.removeAllViews()
        activityProviderFilterButtons.clear()
        val providers=ActivityTimelineFilters.providers(activityItems)
        (listOf("ALL")+providers).forEach { provider ->
            val chip=label(if(provider=="ALL")"ALL PROVIDERS" else provider.uppercase(Locale.getDefault()),8f,MUTED,true).apply {
                gravity=Gravity.CENTER
                setPadding(dp(11),dp(8),dp(11),dp(8))
                background=rounded(0xFF263B46.toInt(),10)
                setOnClickListener {
                    activityProviderFilter=provider.takeUnless { it=="ALL" }
                    updateActivityFilterStyles()
                    renderUnifiedActivity()
                }
            }
            activityProviderFilterButtons[provider]=chip
            target.addView(chip,LinearLayout.LayoutParams(-2,dp(34)).apply{marginEnd=dp(6)})
        }
        updateActivityFilterStyles()
    }

    private fun refreshUnifiedActivity() {
        val target=activityContainer ?: return
        target.removeAllViews()
        target.addView(label("READING LOCAL ACTIVITY…",10f,MUTED,false).apply{setPadding(dp(4),dp(12),dp(4),dp(12))})
        worker.execute {
            val result=runCatching {
                UnifiedActivityRepository(applicationContext).use { it.timeline(250) }
            }
            runOnUiThread {
                if(activeTab!="ACTIVITY") return@runOnUiThread
                if(result.isSuccess) {
                    activityItems=result.getOrDefault(emptyList())
                    renderUnifiedActivity()
                } else {
                    activityContainer?.removeAllViews()
                    activityContainer?.addView(card().apply {
                        addView(label("LEDGER UNAVAILABLE",11f,AMBER,true))
                        addView(label(result.exceptionOrNull()?.message ?: "Could not read local activity.",10f,MUTED,false))
                    })
                }
            }
        }
    }

    private fun renderUnifiedActivity() {
        val target=activityContainer ?: return
        target.removeAllViews()
        renderProviderFilters()
        val visible=ActivityTimelineFilters.filter(activityItems,activityFilter,activityProviderFilter)
        if(visible.isEmpty()) {
            target.addView(card().apply {
                addView(label(if(activityFilter=="EVENTS")"NO CANONICAL EVENTS YET" else "NO LOCAL ACTIVITY YET",11f,MINT,true))
                addView(label(
                    if(activityFilter=="EVENTS") "Typed provider adapters have not produced canonical events yet. Captured notifications remain observations until explicitly interpreted."
                    else "Import a driver statement or allow a supported app notification to be captured. Evidence stays on this device.",
                    10f,MUTED,false
                ).apply{setPadding(0,dp(5),0,0)})
            })
            return
        }
        visible.forEach { item ->
            val entry=card()
            val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            head.addView(label((item.provider ?: "UNKNOWN PROVIDER").uppercase(Locale.getDefault()),9f,MINT,true))
            head.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
            head.addView(label(if(item.sourceKind=="provider_event")"CANONICAL EVENT" else "OBSERVATION",8f,if(item.sourceKind=="provider_event")MINT else AMBER,true))
            entry.addView(head)
            entry.addView(label(item.itemType.replace('_',' ').uppercase(Locale.getDefault()),12f,WHITE,true).apply{setPadding(0,dp(7),0,dp(4))})
            val timeText=if(item.sourceOccurredAtMillis==null) {
                "RECEIVED · ${dateTime(item.receivedAtMillis)} · SOURCE TIME UNKNOWN"
            } else {
                "SOURCE · ${dateTime(item.sourceOccurredAtMillis)}  /  RECEIVED · ${dateTime(item.receivedAtMillis)}"
            }
            entry.addView(label(timeText,8f,MUTED,false).apply{setPadding(0,0,0,dp(6))})
            val details=mutableListOf<String>()
            item.status?.let{details.add("STATUS · ${it.uppercase(Locale.getDefault())}")}
            item.amount?.let{details.add("AMOUNT · ${String.format(Locale.US,"%.2f",it)} ${item.currency ?: ""}".trim())}
            item.pickup?.let{details.add("FROM · $it")}
            item.destination?.let{details.add("TO · $it")}
            item.distanceKm?.let{details.add("DISTANCE · ${String.format(Locale.US,"%.1f",it)} km")}
            item.durationMinutes?.let{details.add("DURATION · ${String.format(Locale.US,"%.0f",it)} min")}
            item.lifecycleId?.let{details.add("LIFECYCLE · $it")}
            details.add("METHOD · ${item.sourceMethod}")
            details.forEach { entry.addView(label(it,9f,WHITE,false).apply{setPadding(0,dp(2),0,dp(2))}) }
            entry.addView(label("CLASSIFICATION · ${item.classification.uppercase(Locale.getDefault())}",8f,AMBER,true).apply{setPadding(0,dp(6),0,0)})
            item.evidenceDigest?.let {
                entry.addView(label("EVIDENCE SHA-256 · ${it.take(20)}…",8f,MUTED,false).apply{setPadding(0,dp(4),0,0)})
            }
            item.payloadJson?.let {
                entry.addView(label("PAYLOAD · ${it.take(220)}",8f,MUTED,false).apply{setPadding(0,dp(4),0,0)})
            }
            entry.addView(action("INSPECT EVIDENCE",false){showActivityEvidence(item)},LinearLayout.LayoutParams(-1,dp(34)).apply{topMargin=dp(8)})
            activityContainer?.addView(entry)
        }
    }

    private fun showActivityEvidence(item:UnifiedActivityItem) {
        val message=buildString {
            appendLine("Record: ${item.id}")
            appendLine("Kind: ${item.sourceKind} / ${item.itemType}")
            appendLine("Provider: ${item.provider ?: "Unknown"}")
            appendLine("Source method: ${item.sourceMethod}")
            appendLine("Classification: ${item.classification}")
            appendLine("Source event time: ${item.sourceOccurredAtMillis?.let(::dateTime) ?: "Unknown"}")
            appendLine("Received locally: ${dateTime(item.receivedAtMillis)}")
            appendLine("Lifecycle: ${item.lifecycleId ?: "Not linked"}")
            appendLine("Evidence row: ${item.evidenceId ?: "Not available"}")
            appendLine("Evidence reference: ${item.evidenceReference ?: "Not available"}")
            appendLine("Evidence SHA-256: ${item.evidenceDigest ?: "Not available"}")
            appendLine("Status: ${item.status ?: "Unknown"}")
            appendLine("Amount: ${item.amount?.let { String.format(Locale.US,"%.2f",it) } ?: "Unknown"} ${item.currency ?: ""}")
            appendLine("Pickup: ${item.pickup ?: "Unknown"}")
            appendLine("Destination: ${item.destination ?: "Unknown"}")
            appendLine("Distance: ${item.distanceKm?.let { String.format(Locale.US,"%.1f km",it) } ?: "Unknown"}")
            appendLine("Duration: ${item.durationMinutes?.let { String.format(Locale.US,"%.0f min",it) } ?: "Unknown"}")
            item.payloadJson?.let { appendLine("\nCanonical payload:\n$it") }
        }
        AlertDialog.Builder(this).setTitle("ACTIVITY EVIDENCE").setMessage(message)
            .setPositiveButton("CLOSE",null).show()
    }

    private fun buildSourcesScreen():View{
        val root=columnScroll()
        root.addView(section("INTELLIGENCE NETWORK","Independent source status. Candidate does not mean connected."))
        val live=card();live.addView(label("AVAILABLE IN THIS BUILD",11f,MINT,true))
        sourceRow(live,"OpenFreeMap / OpenStreetMap","Vector streets, labels and 3D building extrusions","LIVE · OSM attribution")
        sourceRow(live,"Overpass API","Mapped construction and hazard observations","LIVE WHEN ENDPOINT RESPONDS")
        sourceRow(live,"Open-Meteo","Current weather context","LIVE WHEN ENDPOINT RESPONDS")
        sourceRow(live,"Android location","Driver position after permission","DEVICE PERMISSION")
        sourceRow(live,"Uber / Bolt / FreeNow / iTaxi / Cabify / Lyft","Visible notifications from supported ride apps after opt-in","USER AUTHORIZATION REQUIRED")
        sourceRow(live,"User exports","Statement and history CSV import","LOCAL IMPORT");root.addView(live)
        val migrationCard=card()
        migrationCard.addView(label("LOCAL DATA MIGRATION",11f,MINT,true))
        migrationCard.addView(label("Read-only legacy inspection · source rows are checked against migrated row identities. This does not modify or delete the old databases.",10f,MUTED,false).apply{setPadding(0,dp(5),0,dp(9))})
        ledgerMigrationContainer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        migrationCard.addView(ledgerMigrationContainer)
        migrationCard.addView(action("REFRESH MIGRATION CHECK",true){syncUnifiedActivityLedger()},LinearLayout.LayoutParams(-1,dp(40)).apply{topMargin=dp(8)})
        migrationCard.addView(action("SHARE DIAGNOSTIC REPORT",false){shareMigrationDiagnostics()},LinearLayout.LayoutParams(-1,dp(40)).apply{topMargin=dp(7)})
        root.addView(migrationCard)
        renderMigrationDiagnostics()

        val gated=card();gated.addView(label("CONNECTOR CATALOGUE · NOT CONNECTED",11f,AMBER,true))
        listOf(
            Pair("TomTom Traffic / Incidents / Routing","Commercial API credentials and storage/display rights"),
            Pair("HERE Traffic / Routing","Commercial credentials and entitlements"),
            Pair("Mapbox Traffic / Directions","Token and product licence review"),
            Pair("Google Navigation SDK","Separate navigation SDK evaluation"),
            Pair("Waze for Cities","Partner programme access; not a public API"),
            Pair("Kraków MSIP / ZDMK","Municipal feed and dataset licence validation"),
            Pair("ZTP Kraków / GTFS-RT","Transit feed discovery and schema validation"),
            Pair("Polish NAP / DATEX II / GDDKiA","Registration, credentials and coverage"),
            Pair("Air quality / severe weather","Official feed selection and quality validation"),
            Pair("Driver community reports","Consent-based sharing and moderation service")
        ).forEach{sourceRow(gated,it.first,it.second,"NOT CONNECTED")}
        root.addView(gated)
        root.addView(card().apply{
            addView(label("EVIDENCE RULE",11f,MINT,true))
            addView(label("Every road event needs source, observed time, spatial precision, freshness, licence and conflict state. No fake traffic, demand heatmaps or police-location claims.",11f,MUTED,false))
            addView(action("REFRESH ROAD SOURCES",true){showTab("MAP");refreshRoadIntelligence()})
        })
        return root
    }
    private fun renderMigrationDiagnostics() {
        val target=ledgerMigrationContainer ?: return
        target.removeAllViews()
        target.addView(label(ledgerMigrationStatus,10f,if(ledgerMigrationStatus.startsWith("MIGRATION COMPLETE"))MINT else AMBER,false))
    }

    private fun shareMigrationDiagnostics() {
        val intent=Intent(Intent.ACTION_SEND).apply {
            type="text/plain"
            putExtra(Intent.EXTRA_SUBJECT,"BusyBee local data migration report")
            putExtra(Intent.EXTRA_TEXT,ledgerMigrationStatus)
        }
        startActivity(Intent.createChooser(intent,"Share migration report"))
    }

    private fun sourceRow(parent:LinearLayout,name:String,detail:String,status:String){
        val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(9),0,dp(9))}
        row.addView(label(name,12f,WHITE,true));row.addView(label(detail,10f,MUTED,false).apply{setPadding(0,dp(2),0,dp(3))})
        row.addView(label(status,9f,if(status.startsWith("LIVE"))MINT else AMBER,true));parent.addView(row);parent.addView(divider())
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

    private fun columnScroll()=ScrollView(this).apply{isFillViewport=true;setBackgroundColor(BG);addView(LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(24))})}
    private fun section(title:String,subtitle:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(2),dp(13),dp(2),dp(9));addView(label(title,12f,WHITE,true));addView(label(subtitle,10f,MUTED,false).apply{setPadding(0,dp(3),0,0)})}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(13),dp(14),dp(13));background=rounded(CARD,17);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)}}
    private fun metric(title:String,value:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(9),dp(10),dp(7),dp(9));background=rounded(0xFF1A2D37.toInt(),12);addView(label(title,9f,MUTED,true));addView(label(value,12f,WHITE,true).apply{setPadding(0,dp(6),0,0)})}
    private fun label(value:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply{text=value;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD}
    private fun field(hintText:String,value:String)=EditText(this).apply{hint=hintText;setText(value);textSize=13f;setTextColor(WHITE);setHintTextColor(MUTED);setSingleLine(true);setPadding(dp(10),dp(9),dp(10),dp(9));background=rounded(0xFF1B303A.toInt(),10);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(7)}}
    private fun action(title:String,primary:Boolean,onClick:()->Unit)=TextView(this).apply{text=title;gravity=Gravity.CENTER;textSize=9f;typeface=Typeface.DEFAULT_BOLD;letterSpacing=.02f;setTextColor(if(primary)BG else WHITE);background=rounded(if(primary)MINT else 0xFF263B46.toInt(),11);setOnClickListener{onClick()}}
    private fun addViewAction(parent:LinearLayout,title:String,onClick:()->Unit){parent.addView(action(title,true,onClick),LinearLayout.LayoutParams(-1,dp(40)).apply{topMargin=dp(9)})}
    private fun divider()=View(this).apply{setBackgroundColor(0xFF263943.toInt());layoutParams=LinearLayout.LayoutParams(-1,dp(1))}
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun num(key:String,default:Double)=commands.setting(key,default.toString()).replace(',','.').toDoubleOrNull()?:default
    private fun money(value:Double?)=value?.let{String.format(Locale.US,"%.2f PLN",it)}?:"—"
    private fun date(ms:Long)=SimpleDateFormat("dd MMM yyyy",Locale.getDefault()).format(Date(ms))
    private fun dateTime(ms:Long)=SimpleDateFormat("dd MMM · HH:mm",Locale.getDefault()).format(Date(ms))
    private fun parseDate(s:String):Long?=try{SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}.parse(s)?.time}catch(_:Exception){null}

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==LOCATION_REQUEST&&grantResults.any{it==PackageManager.PERMISSION_GRANTED})requestLocation()}
    override fun onStart(){super.onStart();mapView.onStart()}
    override fun onResume(){super.onResume();mapView.onResume()}
    override fun onPause(){try{locationManager.removeUpdates(locationListener)}catch(_:Exception){};mapView.onPause();super.onPause()}
    override fun onStop(){mapView.onStop();super.onStop()}
    override fun onLowMemory(){super.onLowMemory();mapView.onLowMemory()}
    override fun onSaveInstanceState(outState:Bundle){super.onSaveInstanceState(outState);mapView.onSaveInstanceState(outState)}
    override fun onDestroy(){worker.shutdownNow();mapView.onDestroy();super.onDestroy()}

    companion object{
        private const val LOCATION_REQUEST=731
        private const val BG=0xFF07131B.toInt()
        private const val PANEL=0xFF0C1C26.toInt()
        private const val CARD=0xFF10232E.toInt()
        private const val WHITE=Color.WHITE
        private const val MUTED=0xFF9AAEB8.toInt()
        private const val MINT=0xFF8FE7C1.toInt()
        private const val AMBER=0xFFFFC978.toInt()
        private val KRAKOW=LatLng(50.0647,19.9450)
        private const val OPENFREE_MAP_STYLE="https://tiles.openfreemap.org/styles/liberty"
    }
}
