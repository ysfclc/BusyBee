# Unified Driver Activity Ledger — migration design

**Status:** design checkpoint; implementation not started  
**Scope:** consolidate existing Driver Mirror imports, manual offers and platform notifications into one local activity/evidence repository without deleting or rewriting either legacy database.

## 1. Existing on-device stores

The current app opens two independent SQLite databases:

### `busybee_driver_mirror.db` (schema version 1)

- `imports`: import source name, import time, row count and inserted count.
- `raw_evidence`: every imported row, including duplicate rows, with import ID, row number, fingerprint, raw content and source name.
- `records`: deduplicated normalized import rows with provider, record type, external ID, occurred-at text, route, distance, gross, fees, bonus, currency, status, raw row and source name.

### `busybee_command_centre.db` (schema version 2)

- `offers`: manually/locally captured structured offers, including platform, route, fare, bonus, distances, duration, rating, captured time, source, raw text and status.
- `notices`: deduplicated notification title/body and provider with capture time.
- `driver_items`, `community_docs`, `road_reports`, `settings`: unrelated driver-life and map data. They stay in this database and are outside the Driver Activity migration.

Neither existing database is to be renamed, dropped, cleared, or upgraded destructively.

## 2. Target

Create a third database with a new, explicit role: `busybee_driver_activity.db`. It becomes the unified read/query surface for provider observations and canonical events. It does not replace the legacy files during the migration period.

### Tables

- `activity_evidence`: immutable evidence records. Includes origin database/table/row, evidence kind, provider, source/receive timestamps when known, content digest, local evidence content or safe local reference, and a unique origin key.
- `activity_observations`: source-level observations. Includes provider, adapter/source method, observation kind, source time (nullable), receive time, classification state, normalized nullable fields and evidence reference.
- `provider_events`: canonical, typed lifecycle events from the Provider Bridge. Includes unique event ID and idempotency key, provider/adapter/version, event type, lifecycle ID, source method, evidence class/reference, occurred time (nullable), received time and typed payload fields.
- `migration_sources`: per-source database/table schema version and completion state.
- `migration_rows`: unique source database/table/row ID mapping to the target row, so retries cannot duplicate copied material.

All foreign keys and unique indexes are created before copying data. Every schema change is versioned and additive.

## 3. Migration mapping

| Legacy source | Target representation | Classification |
|---|---|---|
| Mirror `imports` | import provenance metadata | source metadata, not a driver event |
| Mirror `raw_evidence` | `activity_evidence` | immutable imported-row evidence; duplicates retained |
| Mirror `records` | `activity_observations` with structured nullable fields and evidence link | imported record; no lifecycle state inferred |
| Command `offers` | `activity_observations` with structured offer fields and local evidence | manual/local offer observation |
| Command `notices` | `activity_evidence` + `activity_observations` | notification observation, initially unclassified |
| Command `driver_items`, `community_docs`, `road_reports`, `settings` | remain in legacy command database | outside provider activity migration |

A notification's title/body is not itself an `OfferObserved`, `TripStarted` or `OfferExpired` event. A CSV row labelled `trip` is not automatically a completed trip. Classification is a separate, evidence-backed operation. Missing fields and unparseable timestamps remain unknown.

## 4. Safe execution model

1. Open the target database and verify its schema version.
2. Inspect legacy database file existence before opening. Never instantiate a legacy helper merely to check for data; that could create an empty legacy database.
3. Open existing legacy databases read-only. Do not run DDL or DML against them.
4. Copy each legacy table in bounded batches. For each row, insert the evidence/observation and its `migration_rows` marker in the same target-database transaction.
5. Use stable origin keys such as `busybee_driver_mirror.db:raw_evidence:<legacy-id>`; target unique constraints make retries idempotent.
6. Commit a source/table completion marker only after all rows have been copied and verified.
7. If the process is interrupted, committed batches remain; rerun skips rows whose origin keys already exist and continues the rest.
8. Verify per-table source/target counts and evidence digests before marking a table complete.
9. Keep both legacy databases in place and continue the existing UI reads until the unified repository passes parity checks.

Do not attach both legacy databases and assume a cross-file atomic transaction. Do not use a destructive export/import shortcut. Do not mark an absent legacy database as permanently migrated; retry discovery on a later startup.

## 5. Timeline and event semantics

The unified timeline is a query over observations and canonical events, ordered by source event time where known, then receive time as a deterministic fallback. Receive time must never be substituted into the source-time field.

- Observations are evidence, not conclusions.
- Canonical events are generated only by a classifier/adapter with explicit mapping rules.
- Multiple observations may support one lifecycle event; they must not create duplicate canonical events.
- Conflicting observations remain visible with provenance rather than being silently overwritten.
- Out-of-order observations are retained and reconciled by lifecycle identity and evidence.
- Expiry requires an explicit provider deadline/state or user confirmation; disappearance is not expiry.
- No cross-currency aggregation or financial assumptions are introduced by migration.

## 6. Privacy and retention

The target remains local-first. Notification title/body and imported raw rows can contain personal data; copy only the same user-authorised content already stored locally, preserve its source and retention class, and do not upload it. A future source-clear action must delete the target's derived copies and associated evidence according to an explicit, tested deletion policy; legacy copies must not be silently left behind or unexpectedly removed.

## 7. Acceptance gates

Before wiring the UI to the new database:

- Fixture tests cover empty/missing legacy DBs, each schema version, nullable fields, duplicate raw rows, duplicate normalized records, duplicate notifications, interrupted batches and restart.
- Re-running migration produces no extra target rows.
- Source and target row counts/digests reconcile.
- Existing Driver Mirror summaries, imports, evidence previews, Offer Desk entries and notification history remain unchanged.
- A migration failure leaves the old stores readable and recoverable.
- Only after parity passes does the UI switch to the unified repository. The legacy stores remain untouched for at least one release and are not deleted as part of this change.

## 8. Implementation order

1. Add target schema and pure mapping functions.
2. Add read-only legacy readers and idempotent batch copier.
3. Add fixture/transaction tests and count/digest verification.
4. Add unified repository queries and compare results against both existing stores.
5. Wire timeline and existing screens only after parity.
6. Consider deprecating legacy writes only after an on-device backup/export and rollback plan exists.
