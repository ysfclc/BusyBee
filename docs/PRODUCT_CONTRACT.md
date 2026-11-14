# BusyBee — Canonical Product Contract

**Status:** authoritative product requirements; supersedes conflicting implementation shortcuts in older planning notes  
**Updated:** 2026-11-13  
**Applies to:** Android Driver Command Centre, Kraków pilot

This document records the product requirements that implementation must follow. A working build is not product acceptance. Earlier repository decisions, explicit user corrections and this contract are the source of truth.

## 1. Product definition

BusyBee is a native Android Driver Command Centre for drivers working across Uber, Bolt and other ride platforms. It combines:

1. **Driver Data Mirror:** a private, evidence-backed model of the driver's offers, trips, time, routes, earnings, fees, bonuses, adjustments, vehicle costs and outcomes across platforms.
2. **Road Intelligence Aggregator:** independent map, traffic, roadworks, safety, weather, city and driver-contributed sources reconciled into one spatial picture.
3. **Driver operations:** selected WhatsApp driver-group intelligence, vehicle and document management, insurance/payment reminders and other practical tools.

The map is the primary spatial workspace, not a decorative tab. The Data Mirror and driver workflows are equally core product capabilities.

## 2. Ride-platform data — native integration is the primary path

**Non-negotiable:** BusyBee must integrate with the driver's actual native driver apps and/or authorised provider APIs. Notification capture alone is not an acceptable integration architecture.

For each platform (Uber, Bolt, FREENOW and additional platforms as supported), implement independent adapters and clearly show capability/status:

1. **Official provider API/SDK first.** Where a provider grants driver-level access, authenticate securely, request least-privilege scopes and support disconnect/revocation. Do not imply that fleet APIs or partner-only APIs are available to an individual driver.
2. **Android read-only live UI observer.** Where no usable driver API exists, BusyBee may offer an explicitly enabled Android AccessibilityService that listens only to visible UI events from the exact driver-app package(s) selected and disclosed in BusyBee. It may read visible, non-password UI text for local parsing. It must never click, gesture, type, accept/decline, change the source app, read editable/password fields, capture audio, inspect unrelated apps, or access private app databases. The user must see a prominent in-app disclosure and affirmatively open Android Accessibility Settings to enable it; the service must be easy to disable. Some app screens use custom/secure surfaces or expose no accessibility text, so coverage must be measured and shown as partial rather than promised.
3. **Android notification signal.** A user-enabled NotificationListenerService may capture notification content Android exposes from the exact driver-app packages. This is an event signal and fallback, not the sole or primary data path. Notifications may be redacted, delayed, grouped or absent.
4. **Manual visual fallback.** For screens that do not expose readable accessibility text, a user-started Android MediaProjection session may ask the driver to select the driver-app window. OCR runs on-device; screenshots must not be saved or uploaded. This is a fallback, not the normal listening workflow.
5. **Supported intents/share/API integrations.** Use package-specific intents and share contracts only where the target app actually exposes them. Never use undocumented endpoints or private databases.

The live observer is not a provider account connection and cannot promise all data held inside Uber/Bolt. It can observe only what the active driver-app UI exposes to Android accessibility. Every observation must retain platform, capture time, source type, original visible text/evidence, parser confidence and review status. A detected amount is a suggestion, not a financial record. No earnings or trip enters trusted analytics until the driver reviews/confirms it or a verified API supplies it.

Relevant offer, trip and earnings changes should trigger a local BusyBee notification with a deep link to the appropriate review workspace. Alerts must be deduplicated and controllable by the driver; they must not expose passenger, route or earnings text on the lock screen by default.

API access constraints are real product states: Uber Driver API access may require approval and is not presumed available; Bolt Fleet API is not a universal individual-driver API. Investigate and document actual driver-level access separately for each platform. Accessibility-based reading is a separate, user-enabled Android capability and must not be labelled as an official provider API connection. For Google Play distribution, prepare the applicable Accessibility API declaration and review requirements; do not assume approval.


## 2.1 Native activity and network observation — research direction

BusyBee may add an internal Android Activity & Network Runtime; this does not require a separate application or custom Android OS.

Potential native capabilities include:

1. **UsageStatsManager:** user-granted app/activity usage history and foreground transitions. This is a queryable, short-retention history, not a guaranteed live callback or a record of every touch.
2. **App-scoped VpnService:** an explicitly enabled local VPN can route only selected driver-app packages through BusyBee using `VpnService.Builder.addAllowedApplication`. This can support connection metadata capture (protocol, endpoints, timing, byte counts and available DNS/TLS metadata). It requires a real packet-forwarding implementation and must preserve ride-app connectivity. Android allows only one active VPN per user/profile.
3. **Deeper diagnostics:** local TLS inspection or controlled runtime instrumentation may expose decrypted request/response data for compatible app versions and networking stacks. HTTPS payload visibility is not guaranteed; certificate trust, pinning, QUIC, native networking and app-integrity controls can limit or prevent inspection.

**Observation depth must be labelled honestly.** A network flow is not an HTTP request; a decrypted request is not automatically a valid provider integration; and observing a private endpoint does not authorise BusyBee to invoke or replay it.

Deeper request/response research is limited to controlled, owner-authorised diagnostics. BusyBee must not capture or retain bearer tokens, cookies, refresh tokens, passenger personal data or other secrets; replay or mutate reverse-engineered private provider requests; access private app databases; or use instrumentation to automate ride acceptance/decline. The existing read-only Accessibility boundary remains in force.

The proposed event sources are `ANDROID_USAGE_EVENT`, `ANDROID_VPN_FLOW`, `ANDROID_DNS_OBSERVATION`, `ANDROID_TLS_METADATA` and, only where permitted and validated, `ANDROID_TLS_DECRYPTED_DIAGNOSTIC`. All observations require source provenance, timestamps, app/package identity, observation depth and confidence. Correlation must distinguish raw evidence from inferred offers, trips or earnings.

Actions remain divided into BusyBee-owned operations, documented provider API operations for which access is granted, and user-confirmed handoffs to the original app. No private endpoint becomes an action interface merely because it was observed.

This is a research and architecture direction, not an implementation commitment or change to the current PR scope. See [BusyBee Activity & Network Runtime research](BUSYBEE_ACTIVITY_NETWORK_RUNTIME.md) for technical options, constraints, references and acceptance gates.

## 3. Earnings and trip data — no CSV-first workflow

CSV upload is **not required** for normal use and must not be the primary or default onboarding path. The core workflow is:

**Open driver app → Android observes its visible UI/notification events where permitted → capture locally with source provenance → notify the driver → review/confirm → persist evidence → reconcile → calculate analytics.**

CSV/statement import may remain an optional secondary path for backfill, reconciliation, archive or recovery. It must not block access to the product, dominate the UI, or be required to see useful functionality. Label it as optional. Do not remove historical imports or source evidence during migration.

The canonical model must support trip and offer timestamps, duration, distance, pickup/dropoff or route where available, gross, platform fees, bonuses, adjustments, currency, vehicle cost and platform/source identifiers. Unknown values remain unknown; never infer zero. Effective hourly earnings require explicit duration/time coverage and must disclose missing waiting/dead time. Preserve original source evidence and an auditable correction history.

## 4. WhatsApp — choose groups, do not import chat exports

The intended workflow is **discover driver groups → choose the specific groups to track → inspect/pause/delete each group's data**. Importing WhatsApp .txt chat exports is not the product workflow and must not be required or foregrounded.

Android can expose only notification content permitted by WhatsApp and the OS. BusyBee must be honest about that boundary: group discovery and previews are limited to group names/messages that Android actually exposes in notifications; it cannot silently read WhatsApp's private chat database or guarantee complete group history. The driver chooses which discovered groups to track. No message body is persisted while a group is off. Provide per-group on/off, view, stop, delete and retention controls. Do not monitor private one-to-one conversations.

If notification metadata is unavailable, show the limitation rather than inventing a group or claiming full monitoring. Do not fall back to requesting chat exports as a workaround.

## 5. Map and navigation — map-first, spatial, not stacked cards

The map is the default launch surface and must use the available screen as a full-screen spatial workspace. App navigation should not consume permanent vertical space on the map. Use compact floating controls and context-sensitive bottom sheets/overlays; do not stack a dashboard of cards over the map.

Required map behaviours:
- Map canvas fills the usable display; top controls are compact and floating.
- Driver location, heading-aware camera, reliable recenter, 2D/3D camera, route preview and active-navigation presentation.
- During navigation, prioritise next manoeuvre, route progress and immediate hazards; secondary analytics must not obscure the road.
- Day/dusk/night appearance derived from solar elevation at the last permitted driver location; use a local-clock fallback before location is available. Map style and interface palette must change together without resetting camera, active route or driver position.
- A Waze-like event layer must be built from actual traffic/incident/closure sources with provenance and expiry. Road geometry, mapped OSM tags and weather are not traffic speed or live congestion.
- Layer controls for roadworks, hazards, driver reports and any future licensed traffic/weather sources.
- Distinguish actual live feeds, static map data, user reports, stale data and unavailable feeds. OSRM estimates are not live-traffic ETAs.
- Route and destination selection must be deliberate and visible; no accidental candidate substitution.

The map is not a substitute for driver analytics. Offers, earnings and driver operations need purpose-designed workspaces with clear hierarchy and appropriate density.

## 6. UI/UX — production redesign, not recolouring

A premium visual system is required across all primary surfaces. A palette/launcher update or card padding adjustment is not a redesign.

Design requirements:
- Strong typographic hierarchy, consistent spacing, deliberate negative space and legible contrast.
- Purpose-built layouts for Map, Offers, Earnings/Data Mirror, Driver Hub and Sources; avoid one generic vertically stacked-card template.
- Clear distinction between live, captured, reviewed, estimated, unknown, stale and unavailable data.
- Large touch targets, one-handed use, responsive layout, accessible labels and scalable text.
- Meaningful transitions, restrained motion and tactile feedback; no decorative noise.
- Map-first launch and a bottom-sheet/overlay model for map interactions.
- Every visible control must perform its advertised action and expose permission/error/empty states.

## 7. Privacy, safety and data integrity

- Local-first personal data by default; explicit, revocable consent for Accessibility-based app observation, screen capture, notifications and group tracking.
- The live listener is package-scoped and read-only; no hidden observation of other apps, no actions inside ride apps, and no private database access. Tell the driver exactly what is read and stored before enabling it.
- Lock-screen alerts must use generic text by default; the details remain inside BusyBee after device unlock.
- No silent screen recording, screenshot upload, private app database access or covert accessibility automation.
- No automatic ride acceptance/decline.
- No fabricated data, inferred zeros, false live labels or unsupported API claims.
- Preserve raw evidence, provenance, timestamps, confidence and correction history.
- Per-source licensing, access, retention and revocation constraints are first-class.

## 8. Delivery and acceptance gates

A phase is not complete because code compiles or an APK exists.

For each delivery:
1. Add tests for parsing, package allowlists, Accessibility scope/disclosure, notification classification/deduplication, solar theme transitions, storage/migration, evidence and failure paths.
2. Run Rust and Android unit tests; record actual counts and failures.
3. Build APK and verify package, version, signing, SHA-256 and embedded metadata.
4. Install/run on the target Android device and exercise the real workflow.
5. Compare the installed UI to the agreed product direction; do not claim redesign based on code-only changes.
6. Update docs/STATE.md, docs/PHASE_LOG.md, source register and this contract with exact verification boundaries.

## 9. Explicitly rejected shortcuts

- Notification-only Uber/Bolt integration.
- CSV upload as the required or primary earnings workflow.
- WhatsApp chat-export import as the required group-tracking workflow.
- A generic stacked-card dashboard in place of the Driver Command Centre.
- A map tab that is merely a small panel inside a conventional dashboard.
- Cosmetic recolouring described as a premium UI redesign.
- Treating a successful build/unit test as proof of real-device acceptance.
