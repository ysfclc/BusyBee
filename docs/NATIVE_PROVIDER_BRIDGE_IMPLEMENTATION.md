# Native Provider Bridge — implementation checkpoint

**Status:** provider-neutral domain contract, local model inventory and the first non-destructive activity migration are implemented on the feature branch. Durable provider-event storage, live provider adapters and UI integration remain pending.

## Implemented

- Provider capability and connection-health types, acquisition-method descriptors, typed offer/trip/earning/adjustment payloads and provenance-aware event envelopes.
- Adapter registry allowing multiple acquisition methods per provider while enforcing unique adapter IDs.
- Event ingestion validation for required identity, payload/type consistency, idempotency and explicit offer-expiry evidence. Delayed/out-of-order evidence is retained.
- Local model inventory and app-private model storage. File discovery is metadata-only and cannot make a model eligible for inference without a compatible runtime and verified capabilities.
- New `busybee_driver_activity.db` schema for evidence, observations, canonical provider events and migration tracking.
- `UnifiedActivityLedgerMigrator` copies the existing Driver Mirror import metadata, raw CSV evidence, normalized records, saved offers and captured notifications. Legacy databases are opened read-only; unrelated Driver Hub/map data remains in the original database.
- Each source row and its migration marker are committed in one target transaction. Re-runs are idempotent, source and target row-identity digests are compared, and missing source tables are not reported as complete.
- Explicit ISO-8601 source timestamps are parsed; unparseable timestamps remain unknown. Capture/import time is preserved separately as local receive time. Imported notifications remain unclassified observations.

## Verification

- Provider Bridge unit tests cover partial payloads, duplicate idempotency keys, delayed/out-of-order observations, unknown timestamps, expiry evidence, payload mismatch and adapter identity.
- Robolectric database fixtures cover absent legacy databases, missing source tables, duplicate raw evidence, timestamp parsing, notification provenance, digest checks and idempotent reruns.
- CI run 451 passed actionlint, Rust domain tests, Android native-library build, Android unit tests and debug APK assembly.
- The debug APK is a validation build with a separate `.debug` application ID. Persistent release-signing secrets are not configured, so no install-over-existing release APK was produced.

## Deliberately deferred

- Durable SQLite implementation of `ProviderEventStore` and canonical provider-event insertion.
- Live provider adapters or a claim of an Uber/Bolt API connection.
- Normalizing future notifications and CSV imports into the unified ledger as they arrive.
- Unified activity repository queries, Driver Mirror timeline and Connected Apps UI.
- Local inference runtime execution; current catalogue/inventory is metadata and selection policy only.
- On-device migration parity and rollback verification.

## Next slice

Implement the SQLite-backed provider-event store with its unique idempotency constraint, then add unified repository reads over observations and events. Wire existing notification/import writes into the ledger without removing the legacy stores. Verify data parity before switching any screen to the new repository.
