# BusyBee — Integrated Build Plan
**Date:** 2026-11-16  
**Status:** planning checkpoint; no application code or pull request has been merged by this plan  
**Purpose:** establish one coherent, verifiable product baseline before further feature work.

## 1. Product direction — source of truth

BusyBee is an Android Driver Command Centre with three connected product pillars:

1. **Driver Data Mirror:** a private, evidence-backed view of offers, offer outcomes, trips, time, routes, earnings, fees, bonuses, adjustments, vehicle costs and cross-platform work history.
2. **Road Intelligence and Navigation:** a provider-neutral spatial system that combines legally usable map, traffic, roadworks, safety, weather, city and opt-in driver/community sources. The map is the primary spatial workspace, not a small dashboard tab.
3. **Driver Operations:** vehicle and document management, insurance/payment reminders, expenses and driver-selected WhatsApp group intelligence.

The map is the default launch surface and must use the available display as a full-screen spatial workspace. The Mirror and driver workflows remain equally important product capabilities. Map-first does not mean map-only, and it does not justify postponing the Mirror.

The canonical product contract currently exists on the PR #9 feature line, not on `main`. Its requirements must be reconciled into the main product documentation before implementation is treated as governed by them:

- Native, explicitly enabled, read-only observation of selected driver-app UI is the primary non-API capture path where permitted and supported.
- Official provider access is preferred where genuinely available to an individual driver.
- Notifications are a secondary signal, not the whole integration.
- User-started screen capture/OCR is a manual fallback only.
- CSV/statements are optional backfill and reconciliation, not the normal onboarding workflow.
- WhatsApp workflow is discover → choose groups → pause/view/delete; chat-export import is not the product workflow.
- No ride-app clicking, gestures, typing, accept/decline automation, private database access, hidden screen recording, or false API connection claims.
- Every record retains provenance, event/receive times, completeness, confidence/review state and retention rules.
- The map uses a compact floating-control/bottom-sheet model, not persistent dashboard cards or a permanent tab bar over the map.

Resolve the conflict between the older roadmap (Mirror first, map postponed) and the newer contract (map-first shell, Mirror equally core) explicitly. The agreed interpretation for implementation is: **map-first application shell; Mirror and capture data path are the first core vertical slice; road-intelligence aggregation grows independently behind the map as real sources become available.**

## 2. Repository reality checked on 2026-11-16

Repository: `ysfclc/BusyBee`.

| Line | Current state | Important boundary |
|---|---|---|
| `main` | Older Mirror-first baseline; old map/POI screen removed from the milestone | Not the complete map-first product contract |
| PR #8 / `feat/live-navigation-vehicle-2026-11-13` | Draft; based on `fix/capture-device-crash-2026-11-13` | Contains native capture, selected WhatsApp-group work, driver workspaces and product-contract documents |
| PR #9 / `feat/map-first-route-dock-2026-11-13` | Draft; stacked on PR #8's head | Contains full-screen map, compact route dock, custom vehicle/building/entrance artwork, GPS camera/navigation work and workspace changes |
| PR #14 / `feat/native-provider-bridge-core` | Open, not merged; based directly on `main` | Adds typed Provider Bridge, event codec, SQLite event repository, legacy projector, tests and owned-app plaintext POC |

PR #8 and PR #9 are a dependency chain, not independent branches. PR #14 is a separate line. The feature line changes many of the same files as PR #14, notably `MainActivity.kt` and `DriverMirrorStore.kt`; a blind merge is not an acceptable integration strategy.

The PR #9 state ledger says its source and regression tests were authored but Android compilation, test execution, APK assembly and target-device navigation checks were not verified. Earlier CI attempts failed before runner/job-step allocation; that is an infrastructure failure, not a passing or failing source build. PR #14 contains tests and CI workflows, but its implementation must also be run and verified before acceptance.

Do not infer that any open PR, source commit, test file or APK is a verified installed product. Do not merge the draft PRs merely to clear the queue.

## 3. Next checkpoint — integration and trust baseline

**This is the immediate next phase. No new feature expansion until it passes.**

### 3.1 Establish one integration candidate

- Create a dedicated integration branch from the current authoritative main baseline.
- Treat PR #8 → PR #9 as one feature lineage and preserve their dependency order.
- Port the Provider Bridge from PR #14 into that candidate as a separately reviewed integration, resolving overlapping files deliberately.
- Reconcile documentation from the feature line with main; promote the canonical Product Contract and update README, ROADMAP, STATE and PHASE_LOG so they describe the same product and same current checkpoint.
- Keep all original branches and PRs intact until the integrated candidate passes. No destructive reset, archive replacement or force-push of user work.
- Record the exact source SHAs, parent/base relationships and included commits in the integration ledger.

### 3.2 Protect existing driver data

The integration must preserve the existing local databases and user data:

- Keep `busybee_driver_mirror.db` as the canonical local mirror store; do not introduce a third disconnected database.
- Preserve `busybee_command_centre.db` and its existing offers/notices until migration is proven.
- Use additive, versioned, transactional SQLite migrations only.
- Project legacy offers, notices and imported records into the Provider Bridge ledger without deleting or mutating their source rows.
- Verify idempotency across repeated launches and repeated projection.
- Detect duplicates across notification, accessibility observation, manual review and imported statement sources without double-counting an offer/trip/earning.
- Preserve raw evidence and correction history; redact secrets and unrelated personal data before persistence.
- Test upgrade from a populated pre-Bridge database, rollback on migration failure, app restart, partial/corrupt event blobs and duplicate replay.

The current PR #14 projector is a useful starting point, not proof of complete reconciliation. Review event identity, dedupe keys, source timestamps, evidence redaction, nullable amounts/currencies and whether offers/trips/earnings can be counted twice. Synthetic events must remain visibly synthetic.

### 3.3 Prove one end-to-end data path

Before adding more providers, prove:

`selected driver app → explicit read-only capture → local observation → review/correction → Provider Bridge event → unified timeline → Mirror totals`

The first acceptance fixture should include an offer update, an offer outcome, a completed trip, a fare adjustment and an incomplete observation. Verify that:
- unknown values remain unknown;
- an observation is not silently promoted into trusted financial data;
- corrections preserve original evidence;
- event ordering and repeated delivery do not duplicate totals;
- the source method and capability coverage are visible;
- synthetic/test input never appears as a real Uber/Bolt connection.

## 4. Map and interface work — after the integration candidate is buildable

The map work must solve the actual product experience, not just replace colours or decorate the screen.

### 4.1 Map visual language

Create a small, coherent icon and marker system with reusable vector assets and semantic states. Do not use Unicode characters as production navigation icons.

Required marker families:
- Driver vehicle: custom top-down vehicle silhouette, heading-aware and visually distinct from destinations.
- Navigation: route start, destination, selected alternative and active manoeuvre.
- Road intelligence: roadworks, closure/restriction, crash/hazard, weather/visibility, public transport and community report.
- Context: mapped building/address/entrance labels, visually subordinate to road events.

Each road event must visually communicate category, severity where known, freshness and source confidence without relying on colour alone. Use accessible contrast, consistent stroke/shape, readable scaling and sensible clustering/zoom thresholds. Never imply an event is live if it is merely an OSM-mapped observation.

### 4.2 Map behaviour

- Full-screen map canvas; compact floating top controls and contextual bottom dock/sheets.
- No permanent dashboard cards or tab bar covering the map.
- Reliable GPS marker, recenter/follow, compass, 2D/3D camera, route preview and active-navigation state.
- Manual pan suspends follow; recenter restores it.
- Route alternatives and manoeuvre progression must remain stable across style changes and lifecycle events.
- Preserve camera, route and driver state during day/dusk/night style changes.
- Show explicit loading, no-permission, offline, no-results and source-unavailable states.
- Keep route engine and base-map provider separate. OSRM estimates must not be called live-traffic ETAs.
- Avoid excessive building/entrance labels; road safety and navigation have visual priority while moving.

### 4.3 Other workspaces

- **Offers:** one review queue across sources, clear live/stale/expired states, editable extracted fields, evidence access, cost calculation only when required inputs exist, and handoff to the official driver app for decisions.
- **Driver Mirror:** one cross-platform timeline, confirmed vs unreviewed activity, source coverage, earnings breakdown and missing-data explanations. Optional imports remain available without dominating onboarding.
- **Driver Hub:** vehicle profile, documents, expenses, insurance/payment reminders and selected WhatsApp groups with per-group pause/view/delete/retention controls. Do not foreground chat export imports.
- **Sources:** real per-provider/per-capability status, acquisition method, permissions, last event/sync, freshness, errors and limitations. App detection or permission grant alone must never say “connected”.

Use one consistent premium design system across all workspaces: typography, spacing, surface hierarchy, iconography, motion, haptics, light/dark/solar palettes and accessibility. Every control must work. A visual redesign is accepted only after comparison on the actual phone, not from source inspection alone.

## 5. Capture and integration safety gates

- Accessibility observer must be restricted to explicitly selected driver-app package IDs and read visible, non-password, non-editable UI text only.
- It must not click, gesture, type, accept/decline, inspect unrelated apps, access private app databases or capture audio.
- Show clear disclosure before opening Android Accessibility Settings; make disable/revoke controls obvious.
- Notifications remain a secondary source and must use generic lock-screen alerts by default.
- MediaProjection/OCR is user-started per session, on-device and not silently persisted/uploaded.
- Review Google Play Accessibility API declaration and policy implications before any Play distribution; do not assume approval.
- Official provider APIs are preferred only when driver-level access is actually available and approved.
- Keep plaintext observation as an isolated, owner-authorised research track using the owned test app/fixture. It does not establish Uber/Bolt compatibility and must not become a production dependency or a route to bypassing app protections.
- No request replay, mutation or automated provider action.

## 6. Verification and release gates

A source change is not a release. A CI pass is not device acceptance.

### Automated
- Run actionlint and every repository workflow.
- Run all Rust tests.
- Run Android unit/Robolectric tests, including new migration, event codec, dedupe, capture-scope, parser, route/camera and lifecycle cases.
- Run the owned plaintext POC tests separately from the main app.
- Build debug and release variants; record exact commit, Gradle result, test counts and APK metadata.
- Verify package ID, version, ABI, signature and SHA-256.
- Preserve the existing application ID and signing identity. Never generate a replacement key or distribute an APK that cannot update the installed app.

### Real-device
- Install as an update over the existing BusyBee installation; confirm app data survives.
- Cold launch, permission onboarding, revoke/re-enable, pause/resume, process recreation and repeated tab/workspace switching.
- Test Uber Driver and Bolt Driver separately with real user-controlled sessions; verify actual accessible fields and honest partial states.
- Test notification fallback, manual capture fallback, review/edit, duplicate event delivery and source disconnect.
- Drive known Kraków routes: GPS accuracy, marker heading, recenter, manual pan, 2D/3D, manoeuvre timing, reroute/error behaviour, screen readability and safe interaction.
- Test map with each source unavailable and with no location permission.
- Verify Driver Hub reminders, selected WhatsApp group controls and deletion/retention.
- Compare all five workspaces against the product contract on the actual phone.

### Acceptance rule
A feature is marked **implemented** when source exists, **automated-verified** when its tests/build pass, and **device-accepted** only after the target-phone workflow succeeds. Keep these states separate in the ledger.

## 7. Delivery sequence after the integration baseline

1. **Integrated foundation (next):** one coherent branch, product contract reconciled, non-destructive Provider Bridge storage, all workflows/builds passing, existing data preserved.
2. **First user-facing vertical slice:** map-first shell + selected native capture + review inbox + unified Provider Bridge timeline. This must be useful with current partial data and must not claim unsupported provider access.
3. **Map experience acceptance:** custom marker/icon system, floating dock, camera/navigation correctness, source-aware event layers and real Kraków device tests.
4. **Driver economics:** verified trip reconciliation, costs, hourly/zone/platform comparisons only where source coverage supports the calculation.
5. **Road Intelligence expansion:** source register becomes executable connectors; prioritise Kraków municipal/public feeds and independently licensed commercial traffic trials. Build provenance, freshness, conflict and expiry handling before adding visual density.
6. **Driver operations completion:** selected WhatsApp groups, vehicle/documents/payments/expenses with reliable persistence and retention.
7. **Hardening and release:** accessibility, battery, offline behaviour, privacy/security review, signed upgrade path and pilot acceptance.

Do not start multiple phases at once. Each phase ends with an updated state ledger, phase log, test evidence, APK identity and known limitations.

## 8. Explicit non-goals for the next checkpoint

- Do not build another generic dashboard.
- Do not add decorative POIs or more map layers just to make the map look populated.
- Do not claim live traffic, demand heatmaps, complete Uber/Bolt account sync or provider API access without verified evidence.
- Do not merge PR #8, #9 and #14 blindly.
- Do not discard old database rows, imports, notification evidence or project history.
- Do not ship a debug APK as an update to the installed production package.
- Do not declare the redesign accepted from screenshots or source code alone.

## 9. Immediate execution order

1. Re-read the exact current heads and all branch dependencies immediately before integration.
2. Compare the feature-line product contract and documentation against main; resolve contradictions in one documented decision.
3. Build the integration candidate without changing user data or merging upstream PRs prematurely.
4. Port and review Provider Bridge/storage changes against the candidate's actual store schema.
5. Run automated tests/build and resolve every source failure.
6. Produce a signed, install-over-existing candidate only if the existing signing identity is available.
7. Install on the target phone and run the acceptance checklist.
8. Only after this checkpoint passes, proceed to the next product slice.

**Current status after this planning commit:** planning only. No application code, database, open PR or APK was changed or verified by this document.
