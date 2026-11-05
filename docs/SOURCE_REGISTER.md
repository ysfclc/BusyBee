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
