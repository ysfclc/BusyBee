## Product contract correction — 2026-11-13

The opening capability table and rollout order below are historical planning context where they conflict with the canonical contract. See [PRODUCT_CONTRACT.md](PRODUCT_CONTRACT.md).

- Native driver-app capture or approved provider API is primary; notification capture is fallback only.
- CSV import is optional backfill/reconciliation; no CSV upload should be required for normal use.
- WhatsApp is selected-group tracking; exported chat import is not the desired workflow.
- The map is the full-screen default workspace, with floating controls and context-sensitive overlays. The premium redesign must replace generic stacked-card layouts across every tab; the current visual work does not yet meet that bar.

## Native Android listening and realistic map checkpoint — 2026-11-13

The current feature branch now changes the primary integration model from repeated screen capture to an explicit, read-only Android observer:

- `DriverAppAccessibilityService` is restricted in its Android service configuration and runtime package allowlist to Uber Driver (`com.ubercab.driver`) and Bolt Driver (`ee.mtakso.driver`). It listens for visible window/content/text/scroll events, reads visible non-password, non-editable node text only, applies local parser/deduplication and saves a review observation.
- It does not click, gesture, type, accept/decline, record audio, save screenshots, inspect other apps, or access private app databases. The driver receives an in-app disclosure before Android Accessibility Settings is opened. The user can disable it in system settings. App screens that expose no accessibility text remain unsupported.
- Ride-app notifications are an additional event signal. New relevant notifications and newly stored live UI observations can trigger a generic BusyBee notification linking to Offers or Earnings. Raw route/address/passenger/earnings values are not put in lock-screen notification text.
- MediaProjection with on-device OCR remains a manual per-session fallback for screens whose visible content is not exposed to accessibility.
- Day, dusk and night palettes use the sun's elevation at the last permitted location. If location is unavailable, local clock is used until a fix arrives. The map switches between OpenFreeMap Liberty, Fiord and Dark styles while preserving camera/route state. The daylight native palette uses tinted green/stone surfaces rather than pure-white panels.
- The Sources workspace reports actual permissions and connection status rather than describing an unenabled capability as connected.

## Map usability correction — 2026-11-14

The draft branch now contains the following corrective implementation:

- A custom top-down vehicle marker replaces the generic map pin. Foreground GPS updates move it; navigation camera-follow uses GPS course/bearing, a look-ahead target, zoom 18.2 and 58-degree pitch in 3D mode. The locate control explicitly re-enters follow mode.
- OpenFreeMap Liberty is the daytime basemap; Fiord and Dark remain dusk/night. A MapLibre fill-extrusion building layer is installed for styles that do not already have one. This is 3D building context and a forward-facing perspective, not a photorealistic 3D road mesh.
- OpenStreetMap/Overpass supplies named buildings, house-number addresses and mapped entrance/door nodes on demand around the map centre. Labels are zoom-gated to avoid clutter. Entrance nodes can expose a mapped stairwell type, reference, level or door; individual stairwell letters are shown only when the source actually contains them.
- Nominatim destination search prefers Polish names and uses a nearby viewbox as a ranking bias, but does not hard-bound results, so destinations in other Polish cities remain searchable. OSRM maneuver instructions use a road reference when the road-name field is absent.
- The OpenFreeMap styles' default road-label expression may prefer `name_en` over the local OSM `name`. BusyBee overrides the road-name text field for Liberty, Fiord and Dark to prefer `name:pl` on Polish locales, then local `name`, followed by Latin/English fallbacks. This corrects avoidable English-alias substitutions; it cannot correct an inaccurate or missing OSM source tag.
- A compact map context line shows counts of mapped road observations, buildings and entrances. Place marker redraws are throttled by zoom changes and forced only when new place data or a new style arrives.

**Data-quality boundary:** basemap street labels and building/entrance data remain dependent on OpenStreetMap/OpenFreeMap coverage. An unlabelled entrance is not evidence that a stairwell does not exist; a missing letter must not be guessed. OSRM names/references are still OSM-derived and may themselves be incomplete or incorrect. No live traffic feed is implied.

**Verification:** parser and camera-math regression tests were added. No Android build, test run, APK or target-device navigation test has yet verified this source increment.

**Important verification boundary:** this is source code on a draft branch. It has not been built, tested on the target phone, or reviewed by Google Play. Accessibility-based reading is not an official Uber/Bolt API, cannot see hidden/private data, and may not work on custom/secure driver-app screens. If distributing through Google Play, the Accessibility API disclosure/declaration requirements must be reviewed; approval must not be assumed.


# BusyBee — Integrations, Map and Driver UX Roadmap

**Status:** requested product direction; planning only. This document does not claim that the integrations, live-traffic map or redesigned interface exist today.

## Product outcome

BusyBee should become the driver's command centre: a single place to understand incoming work, compare trip economics, manage driver administration, review earnings across platforms, and navigate with trustworthy road context. Ride platforms remain the systems of record for accepting and completing rides unless they provide an authorised partner API for those actions.

## Current capability — verified from source

| Area | What exists now | What it does not mean |
|---|---|---|
| Uber / Bolt / other platforms | Optional Android notification-listener capture for visible notifications from package-name matches; manual review and conversion into a structured offer; launch handoff for Uber Driver and Bolt Driver | No authenticated Uber/Bolt API, no complete offer parser, no automatic accept/decline, no trip-state sync |
| Earnings | User-selected CSV import into a local SQLite mirror; source rows retained; duplicate suppression and completeness-aware PLN trip totals | No continuous account sync, no guaranteed provider schema compatibility, no edit/reconcile workflow |
| Navigation | MapLibre rendering using OpenFreeMap's Liberty style; OSM/Overpass roadwork and hazard observations; Open-Meteo weather; Nominatim search; OSRM passenger-car routes and alternatives; foreground GPS and spoken instructions | No live traffic layer, no verified traffic-aware ETA, no guaranteed road restriction/vehicle-dimension routing, no background navigation service |
| Driver admin | Local reminders, documents, expenses, vehicle profile and private WhatsApp TXT search | No cloud sync, multi-device backup, record editing/deletion or universal export |
| Interface | Native Android views and a dark navy/mint/amber palette | Not yet a finished design system or the requested premium production UI |

## Integration principles

1. **Use official APIs first.** Any account connection must use documented provider APIs, explicit user authorisation, least-privilege scopes, secure token storage and a revocation path.
2. **Do not reverse-engineer private app databases or bypass platform protections.** A narrowly scoped, explicitly enabled, read-only Android Accessibility observer may read only visible UI text from the selected driver-app packages. It must not automate controls, inspect other apps, or be presented as provider API access. This approach is permission- and policy-sensitive, must be declared honestly, and cannot guarantee complete coverage.
3. **Keep the driver in control.** Until a provider explicitly authorises ride actions, BusyBee may capture a notification, explain the offer economics and open the relevant driver app. The driver accepts or declines inside that app.
4. **Make data provenance visible.** Every imported or synchronised record needs platform, source type, source timestamp, ingestion timestamp, completeness and correction history.
5. **Preserve evidence.** Raw provider exports and original notification evidence must remain distinguishable from parsed/normalised values. Corrections should be auditable, not destructive.
6. **Fail visibly and safely.** Show connection health, last successful sync, permission state, stale data and provider errors. Never fabricate earnings, traffic, demand or availability.

## Proposed integration architecture

Introduce a provider-neutral integration layer only after the current app passes its stability gate:

- **Connection registry:** provider identity, connection state, granted scopes, last sync, last error and revocation state.
- **Adapters:** separate implementations for notification capture, file import, Android share intents and approved provider APIs. Do not couple UI screens directly to provider-specific parsing.
- **Canonical models:** offers, trips, payouts, fees, bonuses, driver status and documents, each with stable source IDs where available.
- **Ingestion pipeline:** validate → preview → deduplicate → persist source evidence → normalise → reconcile. Import must support preview, partial failure reporting and rollback.
- **Action boundary:** read-only aggregation by default. Any future provider action (accept, decline, go online/offline) requires an approved API, explicit user confirmation and a recorded result.
- **Security:** encrypted credential storage, scoped access, no secrets in logs, local data export/deletion, and a clear disconnect/data-retention policy.

### Provider rollout order

1. **Read-only Android live UI observer:** verify exact package IDs on the target phone, check event coverage on offer/trip/earnings screens, measure missed/custom/secure surfaces, keep an explicit consent/revocation path and add review/evidence regression tests.
2. **Notification adapter:** retain Android notifications as a second signal; classify/deduplicate relevant events and send user-controlled generic BusyBee alerts with deep links.
3. **MediaProjection fallback:** keep one-session app-window capture with on-device OCR only for screens whose UI text is not exposed to accessibility; no screenshots saved/uploaded.
4. **Official partner APIs:** investigate access requirements separately for each provider. Build only when driver-level credentials, permissions and permitted use are confirmed.
5. **Optional file/share adapters:** retain real provider exports as secondary backfill/reconciliation, never required for normal use.

## Map target

Keep MapLibre as the rendering foundation unless a measured technical or licensing reason requires a change. The map should have:

- A polished, legible vector base style with clear road hierarchy, labels, POIs and solar-aware day/dusk/night map themes. Use the driver's last permitted location for solar elevation; local-clock fallback before a location fix.
- Stable 2D/3D camera controls, a clear driver marker, heading-aware navigation camera and reliable recenter behaviour.
- A route overview and navigation mode with clear next manoeuvre, distance, remaining route and arrival state.
- Distinct, source-labelled layers for roadworks, hazards, driver reports, weather and (only when licensed and actually available) traffic. Public Polish road-event sources worth integrating include GDDKiA/KPD DATEX II and the GDDKiA Geoportal disruption service; do not label them connected until an app-usable endpoint, access terms, freshness and geometry have been verified.
- Freshness timestamps, confidence/coverage states and explicit unavailable states for every external feed.
- Vehicle-aware routing only when the routing provider supports the relevant dimensions, restrictions and profile.
- Correct attribution and compliance with each map, tile, routing and traffic provider's licence and caching terms.

**Important:** OSRM route durations are not live-traffic ETAs. Overpass observations are not a live traffic feed. These labels must remain explicit until a licensed traffic source is integrated and validated.

## Design-system target

The present screen construction is largely programmatic native TextViews and LinearLayouts. A redesign should not be a colour-only refresh. Establish:

- A small token system for colour, typography, spacing, elevation, shape, motion and touch targets.
- A consistent component library for cards, metrics, status, map controls, bottom sheets, forms, empty/error/loading states and confirmations.
- A map-first command surface that prioritises navigation and incoming-offer decisions, with secondary workflows one tap away.
- Clear visual hierarchy for earnings versus estimates, confirmed versus inferred data, and live versus stale source states.
- Accessibility: scalable text, contrast, screen-reader labels, large touch targets and one-handed use.
- A coherent interaction model, including undo/confirmation for destructive or consequential actions.

## Delivery gates

### Gate 0 — Stabilise current baseline (blocking)

- Restore a functioning build runner.
- Compile current PR head and run Android unit tests.
- Produce and verify a signed APK from the exact tested commit.
- Install and exercise on the target device: launch, tab switching, map lifecycle, GPS, route planning, day/dusk/night style transition, notification capture, explicit Accessibility enable/disable, Uber/Bolt UI text observations, review alerts, manual visual fallback and repeated navigation actions.
- Capture and diagnose the original action failure from DIAG/logcat; do not infer that it is fixed from source inspection alone.

### Gate 1 — Integration foundation

- Write provider-specific data contracts and permissions matrix.
- Implement connection status, disconnect and data-retention controls.
- Harden notification capture and add import preview/reconciliation tests.
- Validate against genuine, user-provided Uber/Bolt exports and notifications.

### Gate 2 — Map quality

- Select a traffic provider based on Poland/Kraków coverage, pricing, SDK/API terms and vehicle routing support.
- Prototype and benchmark map rendering, camera behaviour, route matching and traffic freshness on the target phone.
- Add source provenance and stale-data handling before exposing traffic claims.

### Gate 3 — Product UI

- Create and test the design system and map-first information architecture.
- Redesign the command centre, offers, earnings, driver hub and source connections as one coherent product.
- Validate usability in one-handed, in-car stationary workflows; do not encourage interaction while driving.

## Acceptance criteria for the requested end state

- A driver can see the state of every connected provider and when each source last updated.
- Supported incoming offers are captured with source evidence, parsed with confidence and shown for review.
- BusyBee can calculate comparable offer economics without silently inventing missing values.
- Earnings imports can be previewed, reconciled, corrected and rolled back while preserving originals.
- Ride actions are only performed through an authorised provider interface; otherwise BusyBee hands off to the provider app.
- The map clearly distinguishes base-map data, observed road events, weather and licensed live traffic.
- The interface is consistent, accessible, responsive and verified on the target Android device.
- A reproducible, signed APK is built from the exact commit whose tests passed.

## Explicit blockers and dependencies

- The current PR head has not been compiled or tested because recent GitHub Actions jobs failed without allocating steps; the latest observed run returned no job steps and no artifacts.
- The previous Vercel Android build sandbox resume was blocked by the account's Hobby usage cap. No billing or plan change was made.
- No official ride-platform API credentials or partner approvals are currently established in this repository.
- No live-traffic provider has been selected or licensed.
- Physical-device validation and the original reported IllegalStateException diagnosis remain open.
