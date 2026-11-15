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
| OpenStreetMap | Road/building/POI geometry and tags | Extracts, Overpass or approved tile services | Candidate — assess | ODbL attribution and derivative database obligations; tile usage policy |
| Kraków MSIP | Municipal GIS, roads, infrastructure and potential 3D assets | Open datasets, WFS/ArcGIS services | Candidate — assess | Dataset-specific licence, update cadence, service terms |
| ZDMK Kraków | Roadworks and traffic restrictions | Published notices/data channels | Candidate — assess | Machine-readable feed/API availability not yet verified |
| ZTP Kraków | GTFS and real-time public transport information | GTFS / GTFS-Realtime feeds | Candidate — assess | Feed schema, update cadence, attribution and availability |
| GDDKiA | National-road traffic, roadworks and incidents | Public information/services | Candidate — assess | Geographic scope and machine-readable access |
| Polish National Access Point (NAP) | DATEX II road events and roadworks | Registration/certificates and published services | Access-dependent | Registration, certificate, terms and coverage |
| Geoportal / national spatial services | Spatial reference and infrastructure datasets | WMS/WFS/other services | Candidate — assess | Dataset-level licence and update frequency |

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
| App notifications | User-visible trip/offer/earnings information exposed in notifications | Notification listener with explicit opt-in | Capability-limited | Not access to private app databases or arbitrary screen content |
| Guided screen capture/import | Visible trip/offer details | User-initiated capture or file import | Candidate — assess | User control, platform terms and Play policy review |
| Driver reports | Road hazards, closures and local observations | In-app report flow | Planned first-party source | Timestamp, location, confidence, abuse controls and expiry |
| WhatsApp/community submissions | Reports shared by participating drivers | Explicit submission/import | Candidate — assess | No scraping private groups; consent and minimisation required |

## 5. Connector implementation priority

Priority is based on access feasibility, usefulness and cost—not provider prestige.

1. **Baseline geography and local public feeds:** OSM, Kraków MSIP, ZTP, and verifiable municipal/national feeds.
2. **Commercial parallel benchmark:** trial TomTom, HERE and Mapbox in the same Kraków routes and compare coverage, latency, ETA variance, incident quality and cost. Evaluate Google Navigation SDK as a distinct navigation experience.
3. **National road feeds:** complete NAP/GDDKiA access assessment and registration requirements.
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


## 9. Provider-app process observation research

| Source / method | Potential capability | Initial status | Constraints / next step |
|---|---|---|---|
| In-process plaintext observation in an authorised test process | Observe outbound application data before TLS encryption and inbound response data after TLS decryption | Research requirement; not a production provider connector | First prove in an owned Android test app and local HTTPS service. Do not imply that a third-party production app is compatible or authorised. |
| Frida dynamic instrumentation | Inspect application-owned methods in a controlled test process | POC candidate | Use only against the owned test app in an emulator/test environment. Runtime/library-specific; not a generic injection path from BusyBee into another stock app. |
| Frida Gadget embedded in an application | Load instrumentation within the process that includes the Gadget | Controlled-test candidate only | Embedding requires modifying/re-signing the target package. Only use in an app we own; no third-party provider APK repackaging is a production dependency. |
| Root-based process injection | Potentially inspect an unmodified process in a controlled device | Not selected for daily-device architecture | Root changes the device security model and may be detected; no root or bootloader changes are authorised by this project checkpoint. |

**Important:** these are instrumentation mechanisms, not Uber/Bolt data sources or provider APIs. Current Uber/Bolt app compatibility, permissions, terms, field coverage and stability are unverified. VPN packet metadata, notifications and screen/OCR capture remain separate capabilities and must not be represented as equivalent to process-level plaintext observation.

See [Platform Activity Integration](PLATFORM_ACTIVITY_INTEGRATION.md) and [Plaintext Observer POC design](PLAINTEXT_OBSERVER_POC.md).
