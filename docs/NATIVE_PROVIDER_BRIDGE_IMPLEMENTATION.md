# Native Provider Bridge — implementation checkpoint

**Status:** provider-neutral contract, local model inventory, first read-only migration, persistent SQLite provider-event storage, merged timeline query layer and legacy-ledger synchronization are implemented on the feature branch. Live provider adapters and UI integration remain pending.

## Implemented

- Provider capability and connection-health types, acquisition-method descriptors, typed offer/trip/earning/adjustment payloads and provenance-aware event envelopes.
- Adapter registry allowing multiple acquisition methods per provider while enforcing unique adapter IDs.
- Event ingestion validation for required identity, payload/type consistency, idempotency and explicit offer-expiry evidence. Delayed/out-of-order evidence is retained.
- `SqliteProviderEventStore` persists typed event envelopes and payload JSON. The database's unique idempotency constraint is authoritative under concurrent writes; duplicate inserts are rejected without overwriting the original event.
- Ledger schema version 2 adds `evidence_digest` to provider events with an additive upgrade from v1. Existing evidence and observations are not dropped.
- `UnifiedActivityRepository` merges source observations and canonical provider events into one read-only timeline. Unknown provider time remains null; receive time is used only as the display-order fallback.
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

## Deliberately deferred

- Live provider adapters or a claim of an Uber/Bolt API connection.
- Classifying raw notification text into provider events. Notifications currently remain user-visible observations unless an explicit adapter produces a typed event.
- Unified timeline / Connected Apps UI and on-device parity testing.
- Local inference runtime execution; current catalogue/inventory is metadata and selection policy only.
- A richer offer-status history: legacy offers expose current status, not a complete transition log.

## Next slice

Build the first user-facing unified activity timeline and source/provenance inspection surface. Keep existing Driver Mirror and Offer Desk screens available until on-device parity and rollback checks pass.