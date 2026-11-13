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

1. Official provider API/SDK where the provider grants the required driver-level access. Authenticate securely, request least-privilege scopes and support disconnect/revocation. Do not imply that fleet APIs or partner-only APIs are available to an individual driver.
2. Android-native, driver-initiated integration where an API is unavailable: a visible user-started Android MediaProjection session with Android's system consent, on-device OCR/text extraction, platform selection and a review queue. Never silently record the screen, upload screenshots, bypass app security, or automate accept/decline actions.
3. Package-specific Android integrations/intents only where the target app exposes a supported interface. Do not access private app databases, undocumented endpoints or use accessibility scraping as a covert substitute.
4. Notifications are an optional secondary signal/fallback. They must never be represented as the sole or primary source of driver activity.

The native bridge must support more than one named driver platform. A platform without a verified API or native app handoff must be marked with its actual capability, not advertised as connected. Every captured observation must retain platform, capture time, original visible text/evidence, extraction confidence and review status. OCR is a suggestion, not a financial record. No earnings or trip enters trusted analytics until the driver reviews/confirms it or a verified API supplies it.

API access constraints are real product states: Uber Driver API access may require approval and is not presumed available; Bolt Fleet API is not a universal individual-driver API. Investigate and document actual driver-level access separately for each platform.

## 3. Earnings and trip data — no CSV-first workflow

CSV upload is **not required** for normal use and must not be the primary or default onboarding path. The core workflow is:

**Connect/open native platform → capture actual driver-app information → review/confirm → persist with source evidence → reconcile → calculate analytics.**

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

- Local-first personal data by default; explicit consent for screen capture, notifications and group tracking.
- No silent screen recording, screenshot upload, private app database access or covert accessibility automation.
- No automatic ride acceptance/decline.
- No fabricated data, inferred zeros, false live labels or unsupported API claims.
- Preserve raw evidence, provenance, timestamps, confidence and correction history.
- Per-source licensing, access, retention and revocation constraints are first-class.

## 8. Delivery and acceptance gates

A phase is not complete because code compiles or an APK exists.

For each delivery:
1. Add tests for parsing, consent, storage/migration, evidence and failure paths.
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
