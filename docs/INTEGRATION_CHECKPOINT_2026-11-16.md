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

- Inspect the complete map marker/icon family and replace remaining production Unicode glyphs with consistent vector assets; test severity, freshness and source confidence at realistic map zooms.
- Verify manual map pan/follow/recenter, 2D/3D camera, day/dusk/night style transitions, route preview, reroute and lifecycle restoration on the actual phone.
- Verify Accessibility observer package allowlist, visible-text-only behaviour, disclosure, pause/revoke and battery behaviour on the device. Review Google Play Accessibility policy before Play distribution.
- Validate SQLite upgrade from populated pre-Bridge databases, rollback behaviour, duplicate replay, restart and malformed/corrupt event blobs.
- Implement cross-source event matching and correction history before treating the event ledger as reconciled analytics.
- Keep road-event sources honest: OSRM estimates are not live-traffic ETAs; OSM/Overpass observations are not necessarily current incidents.

## Next execution order

1. Run the repository's Android validation and owned-app POC workflows on this branch; inspect actual job steps and logs.
2. Fix source/test failures without weakening assertions or deleting existing project work.
3. Build debug and release variants; verify package ID, version, ABI, signature and hash. Do not create a replacement signing key.
4. Install as an update on the target phone and run the acceptance checklist.
5. Only after the foundation passes, refine the visual map/icon system and complete map acceptance.
