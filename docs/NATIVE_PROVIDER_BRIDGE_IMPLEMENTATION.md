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
