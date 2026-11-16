# BusyBee integration checkpoint — 2026-11-16

## Candidate provenance

- Integration branch: `integration/driver-command-centre-2026-11-16`
- Parent baseline: Provider Bridge branch `feat/native-provider-bridge-core` at `1f446936528643bae55325f120405b87cc648199`
- Map/capture feature source: `feat/map-first-route-dock-2026-11-13` at `90fbb9c0a6a8c3cd5d8350d727b29619d74ba426`
- Original PRs #8, #9 and #14 remain open and unmodified by this integration work.
- `main` remains unchanged.

## Integration decisions

1. Preserve the map-first application shell, custom vehicle/building/entrance artwork, route dock, GPS camera/navigation, selected native driver-app capture, review inbox, vehicle profile and selected WhatsApp-group workflows from the PR #8 → #9 feature lineage.
2. Preserve the typed Provider Bridge, codec, provider-scoped event repository, legacy projector, isolated owned-app POC and dedicated validation workflows from PR #14.
3. Resolve the overlapping `MainActivity.kt` deliberately: retain the map-first screen and add the Bridge-backed unified activity timeline and truthful partial-source status.
4. Resolve the overlapping `DriverMirrorStore.kt` deliberately. The feature line already uses database version 3 (duration and parse-confidence columns); the Provider Bridge therefore becomes version 4. Fresh databases create the event table, and upgrades from versions 1, 2 or 3 add it transactionally through SQLiteOpenHelper without deleting existing imports, records or raw evidence.
5. Keep `busybee_driver_mirror.db` as the canonical event store. The existing `busybee_command_centre.db` remains intact; no third database is introduced.
6. Keep map, Mirror and driver operations as equal product pillars. The map is the default full-screen spatial workspace, not a reason to defer data capture or driver workflows.

## Important data semantics

The legacy projector is read-only against the offers, notices and imported-record tables. It writes idempotent source events into the Provider Bridge ledger. This is **not yet full cross-source conflict reconciliation**: the same real-world trip observed through different acquisition methods may still appear as separate source events. Do not sum the unified event timeline into earnings or use it as an authoritative trip count. The reviewed Mirror records remain the financial source of truth until matching, correction and conflict-resolution rules are implemented and accepted.

Synthetic adapter data remains explicitly synthetic and cannot represent a verified Uber/Bolt connection. Notification permission, app detection, accessibility opt-in and imported history are not API connections.

## Implementation status

- [x] Integration candidate branch created from the Provider Bridge line.
- [x] Map-first feature lineage selected as the UI/source baseline.
- [x] Main screen integration prepared for unified event timeline and Connected Apps partial-source status.
- [x] Provider Bridge schema adapted to the feature line's version-3 database and advanced to version 4.
- [x] Migration regression tests added for fresh install and upgrades from existing database versions.
- [ ] Android compilation and unit/Robolectric tests pass on the integration head.
- [ ] Dedicated Provider Bridge and owned-app POC workflows pass.
- [ ] Signed APK identity and SHA-256 verified; signing key must match the installed package.
- [ ] Install-over-existing test on the target Android 14 phone; confirm all user data survives.
- [ ] Live driver-app capture, review, notification fallback, WhatsApp group controls, route guidance and all workspaces accepted on-device.

A source change is not a build result. A workflow pass is not device acceptance. Do not mark a gate complete without its evidence.

## Known acceptance work still required

- [x] Implemented one semantic icon family for roadworks, hazards, closures, weather categories, private driver reports, route start/destination, map controls and workspace tabs. Existing vehicle/building/entrance artwork retained.
- [ ] Accept icon size, overlap, tap targets, TalkBack descriptions and day/night contrast on the target phone. Severity/freshness are intentionally not shown where source data does not provide them.
- Verify manual map pan/follow/recenter, 2D/3D camera, day/dusk/night style transitions, route preview, reroute and lifecycle restoration on the actual phone.
- Verify Accessibility observer package allowlist, visible-text-only behaviour, disclosure, pause/revoke and battery behaviour on the device. Review Google Play Accessibility policy before Play distribution.
- Validate SQLite upgrade from populated pre-Bridge databases, rollback behaviour, duplicate replay, restart and malformed/corrupt event blobs.
- Implement cross-source event matching and correction history before treating the event ledger as reconciled analytics.
- Keep road-event sources honest: OSRM estimates are not live-traffic ETAs; OSM/Overpass observations are not necessarily current incidents.

## Next execution order

1. Configure the existing release-signing secrets, without generating a replacement key.
2. Install the debug candidate side-by-side if desired, then verify the release update path once the original key is available.
3. Run the on-device acceptance checklist for data persistence, capture/review, WhatsApp groups, navigation and every workspace.
4. Refine the visual map/icon system and complete map acceptance.


## CI evidence recorded — 2026-11-16

- Android validation run #39 (43815933532): passed Rust native-library compilation, Android unit/Robolectric tests, debug APK assembly, and the owned-app POC test/build step.
- APK validation run #358 (43815933430): passed actionlint, Rust workspace tests, Android native-library compilation, Android unit/Robolectric tests, debug APK assembly, apksigner verification, provenance and SHA-256 generation.
- Release-keystore preparation and signed-release assembly were skipped because persistent release-signing secrets were not configured. The verified artifact is therefore a debug APK, not an install-over-existing update.
- Artifact busybee-pr-16-358: package com.ysfclc.busybee.drivercommandcentre.debug, version 0.1.358-debug, SHA-256 45284351da920b88189e8f61161aeaabaf5acc2c2b149a9986510821e1895cdc, size 92,417,693 bytes.
- The separate POC-only workflow run #4 (43815933431) hit Maven Central HTTP 429 dependency rate limiting while running concurrently. The same POC tests and APK assembly passed inside Android validation run #39. The redundant standalone POC workflow is removed from this integration candidate; the combined validation workflow remains the POC acceptance path.
- The Vercel status check reported its existing build-rate-limit condition; it is not the Android build result. GitHub Actions above provide the verified build evidence.


## Map icon implementation — 2026-11-16

- Added a local drawn icon family for road event categories, private driver reports, route endpoints, map controls and workspace navigation. The vehicle, building and entrance artwork remains.
- Removed production Unicode icon glyphs from the map controls and workspace navigation; replaced plus-symbol button prefixes with plain action labels.
- Added category-policy tests. No unsupported severity, freshness or confidence indicators are fabricated.
- Device-level visual, touch-target, TalkBack and day/night acceptance remains pending.


## CI workflow consolidation — 2026-11-16

- The map-icon candidate passed the combined Android validation workflow (#41), including Android unit/Robolectric tests, debug assembly and POC tests/assembly.
- The parallel APK workflow (#360) failed before compilation because Maven Central returned HTTP 429 for Android Gradle Plugin transitive dependencies. This is a dependency rate-limit collision, not an application compile failure.
- Consolidated app tests, POC tests/build and APK assembly into one serialized workflow. Gradle dependency caching is now writable for same-repository PRs and remains read-only for fork PRs. The redundant Android validation workflow is removed from this candidate.
- A fresh single-workflow run is required before marking the candidate build verified again.


## Single-workflow build evidence — 2026-11-16

- Consolidated workflow run #361 (43818601799) passed actionlint, Rust workspace tests, Android native-library compilation, Android unit/Robolectric tests, owned-app POC tests and APK assembly, apksigner verification, provenance and checksum generation.
- Artifact busybee-pr-16-361: debug package com.ysfclc.busybee.drivercommandcentre.debug, version 0.1.361-debug, SHA-256 a3e8ff49cac44c5d07f0cb243393d54aaad5828e57fd63a9b9f8a2b3d9d04aea, size 92,417,689 bytes.
- Release signing was skipped because persistent release-signing secrets are not configured. The artifact is not compatible as an update to the installed release package.
- This run also proved the consolidated workflow and Gradle cache strategy avoid the earlier parallel Maven Central 429 collision.


## Latest integrated build — run #362

- Workflow run #362 (43819480240) on commit `6ca1f81190d915f4469762531fb7ff5a4466fbf5` completed successfully: 29/29 steps, no failed steps.
- This run includes the malformed-event resilience implementation and its Robolectric regression test; the Android test suite passed after that change.
- Artifact `busybee-pr-16-362`: debug package `com.ysfclc.busybee.drivercommandcentre.debug`, version `0.1.362-debug`, SHA-256 `0a506b4669c02d9852b0243b223679187b77ed6015b13f9641960cbd5ac31978`, size 92,417,693 bytes.
- The artifact manifest and SHA256SUMS were checked against the downloaded APK; the checksum matches.
- Release signing is still unavailable because persistent release keystore secrets are not configured. This debug APK is for a separate debug installation and cannot update the installed release app.
- Target-phone acceptance, including install/update compatibility, map appearance, GPS/route behaviour, permissions, accessibility and lifecycle, remains pending.
