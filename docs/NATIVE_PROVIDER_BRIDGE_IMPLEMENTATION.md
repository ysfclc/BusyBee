# Native Provider Bridge — implementation checkpoint

**Status:** provider-neutral contract, local model inventory, read-only migration, persistent SQLite provider-event storage, merged timeline query layer, legacy-ledger synchronization and a first user-facing Activity timeline are implemented on the feature branch. Live provider adapters and full screen migration remain pending.

## Implemented

- Provider capability and connection-health types, acquisition-method descriptors, typed offer/trip/earning/adjustment payloads and provenance-aware event envelopes.
- Adapter registry allowing multiple acquisition methods per provider while enforcing unique adapter IDs.
- Event ingestion validation for required identity, payload/type consistency, idempotency and explicit offer-expiry evidence. Delayed/out-of-order evidence is retained.
- `SqliteProviderEventStore` persists typed event envelopes and payload JSON. The database's unique idempotency constraint is authoritative under concurrent writes; duplicate inserts are rejected without overwriting the original event.
- Ledger schema version 2 adds `evidence_digest` to provider events with an additive upgrade from v1. Existing evidence and observations are not dropped.
- `UnifiedActivityRepository` merges source observations and canonical provider events into one read-only timeline. Unknown provider time remains null; receive time is used only as the display-order fallback.
- A new `ACTIVITY` tab exposes the merged timeline with All / Observations / Events filters. Each card distinguishes source time from receive time and shows classification, source method, status/route/amount fields, evidence digest and a bounded payload preview.
- The Activity tab now derives provider filters from providers actually present in the local ledger. Provider and source-type filters compose, and missing provider identity is grouped as `UNKNOWN PROVIDER` rather than silently omitted.
- Each Activity card has an evidence-inspection action showing record identity, provenance, source/receive timestamps, lifecycle linkage, evidence row/reference/digest, known payload fields and the complete stored canonical payload where present.
- The Sources screen now exposes an on-device migration diagnostic panel with a manual re-check action and a shareable report. It lists each legacy database/table, source row count, copied/already-present counts, identity-verification state and missing databases; errors are visible instead of being hidden in logcat.
- Local model inventory and app-private model storage. File discovery is metadata-only and cannot make a model eligible for inference without a compatible runtime and verified capabilities.
- New `busybee_driver_activity.db` schema for evidence, observations, canonical provider events and migration tracking.
- `UnifiedActivityLedgerMigrator` copies the existing Driver Mirror import metadata, raw CSV evidence, normalized records, saved offers and captured notifications. Legacy databases are opened read-only; unrelated Driver Hub/map data remains in the original database.
- Each source row and its migration marker are committed in one target transaction. Re-runs are idempotent, source and target row-identity digests are compared, and missing source tables are not reported as complete.
- Existing offers' current status is refreshed on later syncs without duplicating their original evidence/observation.
- Legacy activity sync runs on app startup and after CSV imports, manual offer saves/status changes, and newly captured platform notifications. The old stores remain the write source and remain intact.
- Explicit ISO-8601 source timestamps are parsed; unparseable timestamps remain unknown. Capture/import time is preserved separately as local receive time. Imported notifications remain unclassified observations.

## Verification

- Provider Bridge unit tests cover partial payloads, duplicate idempotency keys, delayed/out-of-order observations, unknown timestamps, expiry evidence, payload mismatch and adapter identity.
- Robolectric database fixtures cover absent legacy databases, missing source tables, duplicate raw evidence, timestamp parsing, notification provenance, digest checks, offer status refresh, idempotent reruns, persistent event idempotency, schema upgrade and merged timeline ordering.
- CI run 453 passed actionlint, Rust domain tests, Android native-library build, Android unit tests, debug APK assembly, debug APK signature verification and artifact upload.
- The debug APK is a validation build with a separate `.debug` application ID. Persistent release-signing secrets are not configured, so no install-over-existing release APK was produced.
- CI run #463 passed actionlint, Rust domain tests, Android native-library builds, all Android unit tests, debug APK assembly, APK v2 signature verification and artifact upload. Run: https://github.com/ysfclc/BusyBee/actions/runs/45063014977.
- Run #463 artifact `busybee-debug-apk-463` (artifact ID `14770805750`) contains a 48,942,546-byte APK. APK SHA-256: `a777850852b36cbd66b2ac70ee54a7b5ad7f9e59481a7122bbfccbcc81acb934`; artifact ZIP SHA-256: `9c0684a3c54f597fcb3d6235df92c4eef841a7bf716b4a2394baa634a1a260c6`.
- Debug package is `com.ysfclc.busybee.drivercommandcentre.debug` (version `0.1.463-debug`). Release-signing secrets are not configured; this debug APK cannot update or preserve data from the release package. Physical-device acceptance remains outstanding.

## Deliberately deferred

- Live provider adapters or a claim of an Uber/Bolt API connection.
- Classifying raw notification text into provider events. Notifications currently remain user-visible observations unless an explicit adapter produces a typed event.
- Connected Apps capability/health UI and on-device parity testing.
- Local inference runtime execution; current catalogue/inventory is metadata and selection policy only.
- A richer offer-status history: legacy offers expose current status, not a complete transition log.

## Next slice

- The next device-validation slice is to verify the migrated ledger against pre-existing user data, record parity for legacy Driver Mirror and Offer Desk surfaces, and exercise rollback. Keep the legacy screens available until those checks pass.

## Latest increment — user-curated offer events (2026-11-23)

- Structured offers saved from manual entry or after a driver reviews a platform notification now emit a typed `OFFER_OBSERVED` event into `provider_events`.
- The event records the canonical provider ID, offer lifecycle ID, pickup/dropoff, fare, bonus, currency, distance, duration, passenger rating, source method, evidence class, legacy row reference and stable evidence digest.
- The app's local capture time is kept as receive time; provider event time remains unknown because the entry/review timestamp is not proof of when the provider created the offer.
- The explicit BusyBee **SKIP** action emits `OFFER_DECISION_OBSERVED` with the decision clearly named `skipped_by_driver_in_busybee`. This is not represented as an acceptance/rejection sent to Uber/Bolt.
- Events use the existing SQLite-backed idempotency boundary. Legacy offers and their evidence remain untouched; the Activity timeline can show the source observation separately from its normalized event.
- Raw notification text remains an unclassified observation until the driver reviews and converts it. Imported CSV records are not promoted automatically.

Validation:
- Added focused factory tests for provenance, nullable source time, structured fields (including bonus and passenger rating), stable digests/idempotency, explicit driver-action time and unsaved-offer rejection.
- CI run #471 passed actionlint, Rust domain tests, Android native-library builds, all Android unit tests, debug APK assembly, debug APK signature verification and artifact upload: https://github.com/ysfclc/BusyBee/actions/runs/45128390056.
- Artifact busybee-debug-apk-471 (ID 14796742452), 19,874,453-byte ZIP, SHA-256 c8558dc16d7b3401472d681453b691431fd03f206a42427e250337f5442a0fd4; package com.ysfclc.busybee.drivercommandcentre.debug, version 0.1.471-debug.
- The run did not produce a release APK: the workflow received empty values for all four expected release-signing secrets, so the release steps were skipped. This CI run therefore does not yet verify the persistent release certificate.
- Physical-device migration parity and rollback remain unverified.


## Latest verification — end-to-end user-curated offer path (2026-11-23)

- Added Robolectric coverage for saving a manual offer in DriverCommandStore, emitting its canonical offer event and local skip-decision event, persisting both through SQLite, migrating the legacy offer as an observation and querying all three records in the Activity timeline.
- The integration test verifies the original offer row/status/raw evidence remain intact, the event store remains idempotent after reopening, and bonus/passenger-rating fields survive JSON serialization.
- CI run #474 passed actionlint, Rust tests, Android native-library builds, all Android unit tests, debug APK assembly, debug APK signature verification and artifact upload: https://github.com/ysfclc/BusyBee/actions/runs/45129506262.
- Artifact busybee-debug-apk-474 (ID 14796953779), 19,874,624-byte ZIP, SHA-256 e1d0f127f83e8e28942b119e0ac0ca8978056b1aed1debf082b3e8a1296bae10. Package com.ysfclc.busybee.drivercommandcentre.debug, version 0.1.474-debug.
- The workflow again received empty values for the four expected release-signing secrets; release assembly and release-certificate verification were skipped. Debug certificate SHA-256: 43d8a3314d95dd21f19807fc715bb1e871cad5790047f226b90fc08c38b3c287. This is not the persistent release certificate.
- Real-device migration parity, legacy-screen rollback, full Connected Apps coverage and direct provider access validation remain open.


## Latest increment — per-provider local source coverage (2026-11-23)

- Sources now computes a local coverage summary for known ride platforms and additional provider identities in notifications, saved offers and imported records.
- Each provider shows notification permission state, captured notification count, manual/reviewed/other structured offers, imported history count, latest local notification/offer capture and specific data gaps.
- Android notification access is explicitly described as a partial user-visible source. It is not presented as a direct or verified provider API connection.
- Counts are derived from the latest 1,000 locally queried records per source table in this implementation; this is a current local coverage snapshot, not a historical completeness guarantee.
- Added tests for provider aliases, unknown providers, source counts, notification-reviewed/manual offer distinction and permission/data gaps.
- CI run #476 passed actionlint, Rust domain tests, Android native-library builds, all Android unit tests, debug APK assembly, debug APK signature verification and artifact upload: https://github.com/ysfclc/BusyBee/actions/runs/45130369253.
- Artifact busybee-debug-apk-476 (ID 14797357509), 19,884,900-byte ZIP, SHA-256 996052dffdf46e3187d533a4d39c4a5eeb95f5895208fde1b5ab12515c9359ac; package com.ysfclc.busybee.drivercommandcentre.debug, version 0.1.476-debug.
- Release signing was not exercised: all four expected release-signing secret inputs were empty, so the release steps were skipped. The debug APK's certificate SHA-256 is eae426830bd5011c49c81ab8b7868f46fef37564f9807163a215091076bdd06d and is not the persistent release certificate.
- Complete provider capability handshakes, verified live provider adapters, physical-device migration parity, rollback and the single-vs-separate local ledger architecture decision remain open.


## Provider coverage copy hardening (2026-11-23)

- The UI now discloses that current counts use at most the latest 1,000 local entries from each source table; it does not present those counts as lifetime totals or provider-side sync counts.
- The timestamp caption is “latest notice / offer capture” rather than “latest local record”, because imported statement timestamps are a separate source-time domain and are not included in this value.
- CI run #478 passed actionlint, Rust tests, Android native-library builds, all Android unit tests, debug APK assembly, debug APK signature verification and artifact upload: https://github.com/ysfclc/BusyBee/actions/runs/45131173423.
- Artifact busybee-debug-apk-478 (ID 14798520217), 19,884,619-byte ZIP; SHA-256 2943566d83598a43441af8549cd9e047abc3aa35c2f8275455f8ff703b487e8e; package com.ysfclc.busybee.drivercommandcentre.debug, version 0.1.478-debug.
- Release signing was again skipped because the four configured secret inputs were empty. Debug APK certificate SHA-256 154c6bbad2a197fedd45eaffad98798eafc69fd39e910f37121e707106be9721 is not the persistent release certificate.


## Latest increment — explicit Uber / Bolt Connected Apps cards (2026-11-23)

- The Sources panel is now titled CONNECTED APPS · LOCAL PROVIDER COVERAGE and presents a card for each local provider summary.
- Uber Driver and Bolt Driver have explicit launch handoff cards, showing app detection/launchability separately from notification permission and local data coverage.
- Cards show visible notices, manual/reviewed/other saved offers, imported history, latest local notice/offer capture, data gaps and an explicit NOT CONNECTED / NOT VERIFIED provider API state.
- Added Android package-visibility queries for only com.ubercab.driver and ee.mtakso.driver; no broad QUERY_ALL_PACKAGES permission.
- Added ProviderAppCatalog and tests for Uber/Bolt aliases and unsupported-provider handling. Unknown provider names remain visible but receive no invented app package or launch button.
- CI #481 passed all actionlint, Rust, Android unit test, APK build and debug-signature steps: https://github.com/ysfclc/BusyBee/actions/runs/45132114510. Debug artifact busybee-debug-apk-481 (ID 14798067702), 19,886,969-byte ZIP, SHA-256 b52c09fb32a4edee8e5f89ac8d435604c71542fa9866fcfaf12d1c43c13d8ab2.
- CI #482 passed after the final user-facing heading/copy change: https://github.com/ysfclc/BusyBee/actions/runs/45132847686. Debug artifact busybee-debug-apk-482 (ID 14798677356), 19,887,053-byte ZIP, SHA-256 4927f6fbb46ac7039d0063ad934a9559a2f7389652534346fe39ce11e2015362.
- Both were PR debug builds because release-signing inputs were not available to the pull-request job. They do not verify the persistent release certificate.

## Release workflow safety

- Pull requests may produce debug-only validation APKs.
- Pushes to main and manual release dispatches now fail before building/publishing when any of the four required persistent signing inputs are unavailable.
- This closes the previous fallback that could have published a debug APK as a prerelease asset.
- The first successful non-PR signed run remains the release-certificate acceptance gate.
