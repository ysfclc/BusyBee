# Source Register — Kraków Pilot

**Purpose:** catalogue every plausible input source without implying that it is already integrated or that its data can be freely combined.

The register is deliberately status-based. A source is promoted to **connected** only after credentials/access are configured and a live integration test passes.

## 1. Commercial map, traffic and routing providers

| Source | Potential capabilities | Access path | Initial status | Constraints to verify |
|---|---|---|---|---|
| TomTom | Map tiles, traffic flow/incidents, routing, geocoding | Commercial APIs/SDK | Candidate — assess | Coverage, quotas, cache/display rights, attribution |
| HERE | Traffic, incidents, routing, map content | Commercial APIs/SDK | Candidate — assess | Product entitlements, cache and derived-data rules |
| Google Maps Platform / Navigation SDK | Navigation, route guidance, disruptions and map context | Commercial SDK/API | Candidate — separate evaluation | SDK display restrictions; do not assume consumer-app data can be exported |
| Mapbox | Map rendering, tiles, directions and traffic products | Commercial APIs/SDK | Candidate — assess | Source-specific licence and storage rights |
| Esri | GIS layers, basemaps, feature services and routing products | Commercial APIs/services | Candidate — assess | Service terms, attribution, usage limits |
| Waze for Cities | Partner road alerts and traffic feed | Partner programme | Access-dependent | Not a general public API; eligibility and permitted use required |

**Rule:** run comparative trials. No provider is designated as the exclusive source or canonical database. A provider may be best for a particular capability or geography without becoming the architecture's owner.

## 2. Open map and public-sector sources

| Source | Potential capabilities | Access path | Initial status | Constraints to verify |
|---|---|---|---|---|
| OpenStreetMap / OpenFreeMap | Road labels, building geometry, names, house numbers and mapped entrances | OpenFreeMap vector basemap; on-demand Overpass lookup for named/addressed buildings and entrance/door nodes | Basemap active; building/entrance overlay implemented in draft source; build/device verification pending | ODbL attribution and derivative database obligations; tile/Overpass usage policy; local tag coverage is incomplete and no missing stairwell label may be inferred |
| Kraków MSIP | Municipal GIS, roads, infrastructure and potential 3D assets | Open datasets, WFS/ArcGIS services | Candidate — assess | Dataset-specific licence, update cadence, service terms |
| ZDMK Kraków | Roadworks and traffic restrictions | Published notices/data channels | Candidate — assess | Machine-readable feed/API availability not yet verified |
| ZTP Kraków | GTFS and real-time public transport information | GTFS / GTFS-Realtime feeds | Candidate — assess | Feed schema, update cadence, attribution and availability |
| GDDKiA / Geoportal disruption layer | Current national-road maintenance works, accidents and other disruptions; road number, start/end, chainage/extent and update time | Official Geoportal viewer layer published 2026-10-28 from KPD data | Official viewer confirmed; not connected to BusyBee | The exact reusable WMS/service endpoint and app reuse/attribution terms are not yet verified. Viewer presence is not permission to scrape/embed the website. |
| Polish National Access Point (KPD / NAP) | Current national-road disruptions in DATEX II XML / WGS84 | DATEX II 3.4/3.5/3.7; Push on Occurrence preferred; Client Snapshot Pull for recovery/snapshot | Public service catalogue; authenticated recipient integration not connected | Register as a data recipient, obtain/maintain required client certificate, inspect WSDL/schema and test coverage/freshness. This feed describes GDDKiA-managed national roads, not every municipal street. |
| Geoportal / national spatial services | Spatial reference and infrastructure datasets | WMS/WFS/other services | Candidate — assess | Dataset-level licence and update frequency |

### Building and entrance detail

BusyBee's map detail overlay reads mapped building names, `addr:street`, `addr:housenumber`, and entrance/door nodes from OpenStreetMap via Overpass. It can show `entrance=staircase` as a stairwell and preserve explicit `ref`, `level`, `door`, and access tags. It cannot reliably identify every apartment-block entrance or individual `klatka A/B`: those labels must exist in the source data. Where a building has no name, a mapped street/house number is used instead; unnamed and unaddressed buildings are not assigned invented names.

## 3. Environmental and contextual feeds

| Source family | Potential capabilities | Initial status | Notes |
|---|---|---|---|
| Weather services | Precipitation, visibility, wind, warnings | Candidate — assess | Compare local coverage, alert latency and API terms |
| Air quality | Pollution measurements and alerts | Candidate — assess | Sensor location, sampling interval and quality metadata |
| Flood / severe-weather alerts | Flooding, storms and dangerous conditions | Candidate — assess | Prefer official warnings and preserve issue/expiry times |
| Public transport | Disruptions, vehicle positions and service alerts | Candidate — assess | City feed plus national sources where available |
| Events / venues | Event timing, expected demand and road impacts | Candidate — assess | Event listings are not proof of traffic impact |
| Parking | Availability, restrictions and pricing | Candidate — assess | Real-time availability is often limited or proprietary |

## 4. On-device and driver-network sources

| Source | Potential capabilities | Access path | Initial status | Boundary |
|---|---|---|---|---|
| Android fused location | Position, speed, bearing and accuracy | Android location APIs | Available after permission | Respect background-location and battery rules |
| Motion sensors | Acceleration, orientation and movement patterns | Android sensor APIs | Available after permission | Treat inferred potholes/harsh events as observations |
| Camera | Road signs, lane markings, hazards and scene context | CameraX/user-visible capture | Future experiment | No continuous covert recording; minimise and protect footage |
| Vehicle OBD-II | Vehicle diagnostics and selected telemetry | Supported adapter/Bluetooth | Optional experiment | Device/vehicle compatibility varies |
| Android driver-app live UI listener | Visible offer/trip/earnings text exposed by the active driver-app accessibility tree | User-enabled, read-only AccessibilityService with exact package allowlist (\`com.ubercab.driver\`, \`ee.mtakso.driver\`) | Implemented in source; build/device verification pending | Visible text only; custom/secure surfaces may be unreadable; no private databases, unrelated apps, editing fields or ride actions; prominent disclosure, revocation and Google Play declaration/review required where applicable |
| App notifications | User-visible trip/offer/earnings information exposed in Android notifications | NotificationListenerService with explicit OS permission | Implemented as secondary event signal; live-device test pending | Notifications can be absent, grouped, delayed or redacted; not private app data |
| Manual visual capture | Visible trip/offer details that the app does not expose to accessibility | User-started Android MediaProjection session; app-window selection; on-device OCR | Implemented as manual fallback; build/device verification pending | Per-session Android consent, no screenshot persistence/upload; not the normal listening workflow |
| Historical import | Existing provider statements and old trip history | Android document picker / CSV import | Optional backfill/reconciliation | Not required for normal use; real provider schemas and corrections still need validation |
| Driver reports | Road hazards, closures and local observations | In-app report flow | Planned first-party source | Timestamp, location, confidence, abuse controls and expiry |
| WhatsApp/community submissions | Reports shared by participating drivers | Explicit submission/import | Candidate — assess | No scraping private groups; consent and minimisation required |

## 5. Connector implementation priority

Priority is based on access feasibility, usefulness and cost—not provider prestige.

1. **Baseline geography and local public feeds:** OSM, Kraków MSIP, ZTP, and verifiable municipal/national feeds.
2. **Commercial parallel benchmark:** trial TomTom, HERE and Mapbox in the same Kraków routes and compare coverage, latency, ETA variance, incident quality and cost. Evaluate Google Navigation SDK as a distinct navigation experience.
3. **National road feeds:** apply for GDDKiA/KPD recipient registration and client-certificate access; inspect the DATEX II 3.4 WSDL and Snapshot Pull / Push on Occurrence test flow. Treat the official Geoportal disruption layer as a reference viewer until a reusable service endpoint and terms are confirmed.
4. **First-party observations:** driver report flow and opt-in Android location.
5. **Partner-only feeds:** pursue Waze for Cities if eligibility and value justify the application.
6. **Additional contextual sources:** weather, air quality, events and parking after the ingestion pipeline is proven.

## 6. Required source record fields

Every source entry in the implementation registry must include:

- `source_id`, provider, product and connector version.
- Capability list and event types.
- Coverage polygon/region and known gaps.
- Access method, auth type and credential reference (never the secret itself).
- Update cadence, timestamp semantics and rate limits.
- Pricing/quota and operational cost.
- Attribution, display, caching, retention, derivative and redistribution rights.
- Data-quality notes and known upstream dependencies.
- Current status, owner, last tested time and test evidence.

## 7. Promotion gate

A source is eligible for production only after:
- Legal/terms review for the intended use.
- Credentials and quota setup.
- Live data sample and schema validation.
- Coverage and freshness test in the pilot area.
- Canonical mapping and provenance tests.
- Failure, timeout and stale-data behaviour verified.
- Attribution and user-facing source disclosure implemented.

A source that cannot legally be persisted may still be used in a permitted display-only or transient path. It must not be copied into the shared event store by default.


## 7. Additional road and navigation sources

| Source | Capabilities | Status / access notes |
|---|---|---|
| GDDKiA / Krajowy Punkt Dostępowy (KPD) | National-road accidents, roadworks, abnormal traffic, hazards and other DATEX II events | Public catalogue; consumer integration is not a simple unauthenticated JSON feed. KPD uses DATEX II 3.4 and a registered SOAP interface; signed requests/certificates and recipient registration must be completed before claiming a live connector. |
| Geoportal GUGiK — GDDKiA road disruptions layer | Map presentation of current GDDKiA disruptions, including maintenance works, accidents and other events | Public Geoportal presentation announced 28 October 2026; use as a discovery/validation source while the machine interface is assessed. |
| Nominatim | User-triggered destination geocoding | Integrated for manual route requests; respect usage policy and rate limits. |
| OSRM demo routing | Road route geometry, distance and estimated duration | Integrated as a low-volume route-planning fallback; not a guaranteed production SLA or a replacement for a licensed navigation provider. |

The app currently queries OpenStreetMap/Overpass for mapped roadworks and hazard tags, and Open-Meteo for current weather. These are distinct observations, not a real-time traffic-speed feed. TomTom, HERE, Mapbox, Google Navigation and Waze for Cities remain separate connector evaluations.


## 8. Vector map and 3D rendering

| Source | Capabilities | Status |
|---|---|---|
| OpenFreeMap Liberty | Vector streets, labels and 3D building extrusions over OpenStreetMap-derived data | Integrated as the map style; camera starts tilted at neighbourhood zoom. This is not 3D terrain. |

## 7. Kraków / Poland source verification — 6 November 2026

These checks establish that the sources exist and what access path is documented. They do not mean BusyBee has integrated them.

| Source | Verified capability | Integration status | Next engineering step |
|---|---|---|---|
| [MSIP Kraków open-data catalogue](https://msip.krakow.pl/aktualnosci/324198,2053,komunikat,nowa_usluga_pobierania_danych_msip.html) | Since June 2026, catalogue datasets can be downloaded without login as SHP, GeoJSON or DXF; most datasets in the open-data category also expose WFS. | Public download path verified; dataset-specific terms still apply. | Select roadworks / restrictions / transport layers, inspect metadata and licence, then implement a WFS or GeoJSON connector. |
| [MSIP transport network dataset](https://msip.krakow.pl/dataset/2961) | Vector transport network for Kraków including roads, tram and rail infrastructure; metadata identifies mixed municipal / OSM source lineage and EPSG:2178. | Dataset identified; no in-app connector yet. | Transform EPSG:2178 geometry to WGS84 and retain original dataset attribution. |
| [ZDMK city roadworks register](https://zdmk.krakow.pl/zestawienie-prac-w-miescie/) | Current roadworks are published by district with descriptions, dates and links to notices; a map is provided. | Human-readable live source verified; no supported machine-readable endpoint confirmed. | Prefer an official feed/API if ZDMK exposes one; do not scrape the page as if it were a stable API. |
| [KPD / GDDKiA DATEX II traffic-events dataset](https://kpd.gddkia.gov.pl/index.php/pl/metadane-datex/) | National-road events and roadworks in DATEX II XML, WGS84; the metadata catalogue describes public availability. | Dataset confirmed, but the documented SOAP / snapshot-pull interface requires registration and client-certificate authentication. | Register for receiver access and implement a server-side or securely provisioned connector only after credentials and terms are approved. |
| [GUGiK Geoportal GDDKiA disruption layer announcement](https://www.gov.pl/web/gugik/nowa-usluga-utrudnienia-drogowe-gddkia-dostepna-w-serwisie-wwwgeoportalgovpl) | A Geoportal service was announced on 28 October 2026, based on KPD data, showing maintenance works, accidents and other disruptions with dates, road identifiers and update information. | Public map viewer confirmed; direct feature-service endpoint and reuse terms not yet verified. | Inspect the published service metadata before treating it as an ingestible API. |

**Connector rule:** a browser-visible map or public web page is not automatically a reusable API. A source becomes connected only after its actual endpoint, licence, geographic coverage, refresh behaviour and failure mode have been tested. Never mark a candidate as live merely because a human can view it in a browser.


## 4. Driver-app native data sources — primary integration direction

| Source | Access path | Current code state | Limitations / acceptance requirements |
|---|---|---|---|
| Uber Driver | Approved driver API if granted; otherwise user-started Android MediaProjection + local OCR | Native capture candidate with manual review; notification fallback remains | No API connection claimed. Driver must grant Android capture consent each session. Validate on real Uber Driver screens. |
| Bolt Driver | Approved driver-level API if granted; otherwise user-started Android MediaProjection + local OCR | Native capture candidate with manual review; notification fallback remains | Bolt Fleet API is not presumed available to individual drivers. Validate on real Bolt Driver screens. |
| FREENOW / other driver apps | Approved API or user-named native capture session | Named-platform capture path added; package-specific API/handoff not yet verified | User-named OCR capture is not proof of a dedicated connector. Validate app-specific screen formats and source provenance. |
| Ride-app notifications | Android notification listener | Secondary fallback | Never the sole or primary integration. Only exposed notification content is available. |

## 5. WhatsApp selected-group source

Android notification listener discovers WhatsApp/WhatsApp Business group names only when Android exposes the conversation title. The driver explicitly enables each group before message previews are retained. One-to-one conversations are excluded. Android may not expose every message or group; BusyBee does not access the private WhatsApp database or guarantee complete history. Per-group stop/delete controls are required. Chat-export import is not the intended workflow.
