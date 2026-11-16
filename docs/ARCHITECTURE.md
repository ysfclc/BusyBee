# System Architecture

**Status:** Phase 1 architecture baseline  
**Decision:** provider-neutral, multi-source intelligence platform with an Android-native client and a Rust domain core.

## 1. System shape

```text
External providers / public feeds / city datasets / Android / driver network
                              |
                     Source connector SDK
                              |
                Independent connector adapters
                              |
                Raw ingestion + source registry
                              |
          Validation / normalisation / spatial matching
                              |
              Evidence fusion + conflict handling
                              |
             Canonical road & place intelligence model
                    /                    \
          Routing orchestrator       Driver intelligence
                    \                    /
                   Map / navigation presentation
                              |
                  Android application (Kotlin)
```

A connector is an adapter, not a dependency of the whole application. The system must continue operating in a degraded but honest mode when a provider is unavailable, rate-limited or unauthorised.

## 2. Runtime boundaries

### Android client — Kotlin

Owns Android lifecycle and platform integrations:

- Location permissions and fused location updates.
- Camera, motion sensors, microphone and notifications only after explicit permission.
- Background execution, battery-aware scheduling and user-visible controls.
- Map rendering, touch, voice, accessibility and driving presentation.
- Local encrypted storage and user-controlled sync.

### Domain engine — Rust

Owns deterministic, testable business logic:

- Canonical event and observation types.
- Connector contracts and ingestion validation.
- Spatial indexing, map matching and entity resolution.
- Evidence fusion, freshness and conflict evaluation.
- Route comparison/orchestration interfaces.
- Driver analytics and privacy-aware aggregation.
- Persistence policies, event expiry and audit metadata.

Use a narrow Kotlin ↔ Rust boundary (UniFFI or JNI, selected during implementation). Keep Android APIs out of the Rust domain core. Keep provider SDK objects out of canonical domain types.

### Optional service layer

A server is introduced only where it materially enables protected API credentials, central feed polling, shared community reports, or cross-device sync. It must reuse the same canonical contracts and must not become a mandatory dependency for basic local map use.

Do not put commercial API secrets in the APK. Use provider-approved client-side authentication where supported; otherwise proxy through a controlled service with quotas and abuse protection.

## 3. Connector framework

Every connector implements a stable contract:

- Identity and version.
- Provider and product name.
- Supported data capabilities.
- Geographic coverage and spatial resolution.
- Authentication mode and credential requirements.
- Ingestion mode: pull, push/webhook, streaming, SDK callback, file import or user report.
- Polling/update expectations and provider timestamps.
- Quotas, cost model and rate-limit behaviour.
- Licence, attribution, caching, retention and redistribution constraints.
- Health state, last successful sync, latency and last error.
- Normalisation mapping and known limitations.

Connector states: **not assessed**, **documented**, **access requested**, **credentials configured**, **connected**, **degraded**, **blocked by licence/terms**, **unavailable**, **retired**.

A connector must not claim "connected" merely because an API key exists. It must pass a live capability and data validation check.

## 4. Canonical intelligence model

Preserve the raw provider representation when the licence permits it. Normalise into canonical records without erasing provider-specific detail.

Core entities:

- `SourceDefinition`: provider, product, capabilities, terms and coverage.
- `ConnectorInstance`: configured adapter, version, health and credentials reference.
- `RawObservation`: source payload or permitted reference, source ID, observed/received times and spatial footprint.
- `CanonicalEvent`: normalised event category, state, geometry, validity window and impact.
- `RoadEntity`: road, segment, lane, junction, crossing, bridge, tunnel or transport asset.
- `EntityLink`: cross-provider identifiers and match rationale.
- `EvidenceLink`: observation-to-event relationship and support/contradiction.
- `DriverReport`: consented user/community observation and moderation state.
- `RouteCandidate`: provider, geometry, ETA, distance, toll/constraint metadata and calculation time.
- `DriverTrip`, `EarningsRecord`, `VehicleCostProfile`: private driver mirror data.
- `MapLayerState`: user visibility and display preferences.

Every observation/event should carry, where available:

- Stable internal ID and source/provider ID.
- Source and connector version.
- Event category and provider-native category.
- Geometry, coordinate reference system and spatial precision.
- Source-observed time, ingestion time, last-updated time and validity/expiry.
- Direction, affected road/segment and impact/severity.
- Confidence assessment and the reasons behind it.
- Licence/retention classification.
- Raw evidence reference or permitted payload hash.
- Processing/model version and correction/supersession links.

## 5. Evidence fusion and deduplication

Do not average provider confidence scores blindly.

Evaluate:
- Freshness and expected source update cadence.
- Source track record for that event type and geography.
- Spatial agreement and location precision.
- Temporal overlap.
- Independent corroboration versus feeds that republish the same upstream source.
- Contradictory evidence and explicit provider cancellation.
- Whether the event is still plausible and within its validity window.

Deduplicate at the level of the real-world event, not merely matching text. Keep the contributing observations and their provenance. If two sources disagree, preserve both claims and represent the conflict; do not silently select one.

Confidence is a reasoned quality score until calibrated against labelled outcomes. Do not describe it as a statistical probability unless calibration has been demonstrated.

## 6. Routing and map rendering

The rendering layer is independent of the provider catalogue. MapLibre Native is the initial renderer candidate, subject to Android performance and data-licence validation. Renderer choice does not dictate the source of routing, traffic or incidents.

- Evaluate base-map sources separately from overlays and route engines.
- Never mix proprietary tiles or derived datasets outside their permitted use.
- Query multiple route engines when credentials, terms, latency and product UX permit.
- Compare route candidates using explicit metrics and constraints.
- Do not merge incompatible provider route geometries into a synthetic route without validation.
- Do not assume a route engine's traffic data can be exported into the common event store.
- Use OSM and municipal geometry where licensing and completeness are appropriate.
- 3D buildings/terrain can be added from permitted sources; Kraków municipal 3D assets are an enrichment candidate, not a launch dependency.

The same underlying data supports three presentation modes:
1. **Drive:** manoeuvres, route, immediate hazards and voice.
2. **Explore:** all source layers, incidents, infrastructure, transport and provenance.
3. **Driver intelligence:** private trip history, earnings, costs, demand and useful zones.

## 7. Storage and privacy

- Separate public road intelligence from personal driver records.
- Personal location, trip, offer and earnings data is private by default.
- Community contribution is explicit and revocable where technically possible.
- Store only data that the source licence permits us to store.
- Some provider data may be display-only, transient or non-cacheable.
- Keep source retention policy attached to each record so expiry jobs can enforce it.
- Encrypt sensitive local data; minimise server-side personal data.
- Do not collect microphone, camera, notification or accessibility data without clear user control and platform-policy review.
- Never upload raw camera footage by default.

## 8. Observability and resilience

The app and service must expose a source-health view:

- Connector status and last successful update.
- Coverage by geography and event type.
- Freshness/latency and stale-data count.
- API quota and error rate where available.
- Licence/credential blockers.
- Duplicate/conflict rate and unresolved entity matches.

One provider outage must not blank the map. The user must be able to distinguish "no incident reported" from "this source is unavailable" and "this source has no coverage here".


## 10. Current Android navigation and vehicle-cost slice

The current Android implementation is a first foreground navigation vertical slice, not the target multi-source road intelligence architecture:

- MapLibre renders the OpenFreeMap Liberty vector style. On startup the app requests a device location fix and centres on a fresh last-known/current position when available.
- Destination search uses public Nominatim, restricted to Poland, accepts a current-position viewbox, and returns multiple candidates for user confirmation.
- Route calculation uses the public OSRM driving endpoint with GeoJSON geometry, alternatives and step/maneuver data. The app displays explicit route candidates and keeps maneuver order from OSRM.
- During foreground navigation, Android location updates drive the user marker/camera, route progress, text-to-speech maneuver prompts, arrival detection and a bounded off-route reroute attempt. Location updates stop when the Activity pauses; no foreground service/background navigation is implemented.
- Public OSRM durations are not live-traffic ETAs. Its standard driving profile does not enforce van/truck dimensions, weights, restricted-access streets, low-emission zone eligibility or vehicle class.
- `VehicleProfile` is private local SQLite configuration for category, fuel, consumption, energy price and maintenance per kilometre. It calculates a cost/km used by Offer Desk scoring, with an explicit generic fallback until both consumption and price are configured. Vehicle categories do not change the routing profile yet.
- Nominatim/OSRM are replaceable adapters, subject to availability/rate limits and attribution/usage policies. They are not treated as authoritative, contractual or high-availability services.

## 11. Android activity and network observation runtime

BusyBee may host native activity and network observation inside its existing Android client; a separate monitoring app or custom Android distribution is not required for the initial implementation.

The proposed subsystem has independent observation adapters:

- **Activity Observer:** `UsageStatsManager` / `UsageEvents` for user-granted app activity history and foreground transitions.
- **Network Observer:** an explicitly enabled, app-scoped `VpnService` for selected driver-app network flows. `VpnService.Builder.addAllowedApplication` can keep unrelated apps outside the tunnel. Correct packet forwarding, IPv4/IPv6, TCP/UDP, DNS, QUIC, socket protection, lifecycle recovery and single-active-VPN conflicts are mandatory engineering concerns.
- **Existing UI Observer:** the package-scoped, read-only AccessibilityService remains a separate source of visible UI evidence.
- **Existing Notification Observer:** notification content remains a partial, independently permissioned signal.
- **Deep diagnostic research:** local TLS proxy inspection and runtime instrumentation may expose decrypted request/response data in compatible, controlled test environments. This is app-version- and networking-stack-specific, not a universal Android API or a guaranteed production capability.

Do not conflate network-flow metadata with HTTP/API payloads. A VPN can observe connection metadata but does not inherently decrypt HTTPS. App-process instrumentation can sometimes observe plaintext at a networking boundary, but it may be brittle, affected by app integrity controls and updates, and must not be treated as permission to replay private provider endpoints.

The Rust domain layer should receive typed, provenance-preserving observations; it should not own Android VPN or process-instrumentation APIs. Each event records its observation depth and distinguishes raw evidence from inferred business meaning. A network connection is not an offer, trip acceptance or payment.

The action coordinator separates BusyBee-owned actions, documented provider API operations with granted credentials, and user-confirmed handoffs to source apps. Reverse-engineered private endpoint replay, credential capture, private database access and automatic ride acceptance/decline remain outside the product contract.

Research, limitations, test gates and source references are maintained in [BUSYBEE_ACTIVITY_NETWORK_RUNTIME.md](BUSYBEE_ACTIVITY_NETWORK_RUNTIME.md). This section records architecture direction only; it does not authorise implementation or alter the current draft PR scope.

