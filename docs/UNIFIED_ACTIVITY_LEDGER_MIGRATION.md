# Unified Driver Activity Ledger — migration implementation

**Status:** read-only migration, persistent provider-event storage, merged timeline query layer and background legacy-store synchronization are implemented on the feature branch. Existing screens still read the legacy stores, and no canonical provider events are inferred from imported observations.

## 1. Existing on-device stores

### `busybee_driver_mirror.db` (schema version 1)

- `imports`: import source name, import time, row count and inserted count.
- `raw_evidence`: every imported row, including duplicate rows, with import ID, row number, fingerprint, raw content and source name.
- `records`: deduplicated normalized import rows with provider, record type, external ID, occurred-at text, route, distance, gross, fees, bonus, currency, status, raw row and source name.

### `busybee_command_centre.db` (schema version 2)

- `offers`: manually/locally captured structured offers, including platform, route, fare, bonus, distances, duration, rating, captured time, source, raw text and status.
- `notices`: deduplicated notification title/body and provider with capture time.
- `driver_items`, `community_docs`, `road_reports`, `settings`: remain in the command-centre database and are outside this migration.

Neither existing database is renamed, dropped, cleared, or upgraded.

## 2. Target

The third database is `busybee_driver_activity.db`. It is a new local read/query surface and does not replace either legacy file during the migration period.

- `activity_evidence`: immutable copied evidence with source database/table/row identity, provider, timestamps, digest and content.
- `activity_observations`: nullable normalized fields, source method, unknown source time, receive time, unclassified state and evidence link.
- `provider_events`: reserved for canonical typed lifecycle events; migration does not populate it.
- `migration_sources`: per-table schema version, start/completion state, source/target counts and source identity digest.
- `migration_rows`: unique source database/table/row mapping; each marker is committed in the same target transaction as its copied record.

## 3. Implemented migration behavior

`UnifiedActivityLedgerMigrator` now:

1. Checks whether each legacy database file already exists before opening it. Missing files are reported as missing and are not created or marked complete.
2. Opens existing legacy databases with `OPEN_READONLY`.
3. Copies only `imports`, `raw_evidence`, `records`, `offers`, and `notices`. Driver-life, community, road-report and settings tables are untouched.
4. Preserves imported raw evidence (including duplicate raw rows) and links normalized mirror records to their matching raw evidence when available.
5. Copies offers and notifications as observations with their raw/structured evidence. Notification title/body is not automatically interpreted as an offer, trip, or expiry.
6. Leaves source event timestamps unknown when the old source does not provide a trustworthy epoch timestamp. Capture/import time is kept separately as local received time.
7. Commits each copied row and its `migration_rows` marker in one target transaction. A restart skips already committed origins and resumes the remaining rows.
8. Preloads import timestamps once per source database, avoiding a second database open for every imported row.
9. Verifies both source and target row-identity digests as well as row counts before marking a table complete. Missing source tables remain incomplete.

Migration runs in the background on app startup and after CSV imports, manual offer saves/status changes, and newly captured platform notifications. The new ACTIVITY tab reads the merged repository; existing Driver Mirror and Offer Desk screens still read their legacy stores until parity checks pass on-device.

## 4. Timeline and event semantics

- Observations are evidence, not conclusions.
- CSV rows labelled `trip` are not automatically treated as completed trips.
- A notification is not itself an `OfferObserved`, `TripStarted`, or `OfferExpired` event.
- Missing fields and unparseable source timestamps remain unknown.
- Canonical events are generated only by a classifier/adapter with explicit mapping rules.
- Multiple observations may support one lifecycle event; conflicting evidence remains visible with provenance.
- Out-of-order observations are retained; timeline ordering belongs to the query layer.
- Expiry requires explicit provider deadline/state or user confirmation; disappearance is not expiry.
- No cross-currency aggregation or financial assumptions are introduced by migration.

## 5. Privacy and retention

The target remains local-first. Notification title/body and imported raw rows can contain personal data; this migration copies only content already stored locally and does not upload it. Any future source-clear action must define and test how derived copies and evidence are deleted; legacy copies must not be silently removed.

## 6. Verification

Robolectric fixture tests cover:

- absent legacy databases are not created;
- both legacy stores can be copied into the new schema;
- ISO-8601 source timestamps are parsed; unparseable timestamps remain unknown;
- duplicate raw evidence rows are retained independently;
- source/target identity digests are recorded and compared;
- missing source tables are not reported as successfully migrated;
- notifications remain unclassified observations, not canonical events;
- rerunning the migration adds no duplicate evidence or observations.

Still required before switching screens: process-kill interruption simulation, larger imports, and on-device parity checks against Driver Mirror summaries, import history, evidence previews, Offer Desk entries and notification history.

## 7. Next implementation steps

1. Extend interruption/restart coverage with process-kill simulation and large-import fixtures.
2. Add evidence drill-down and provider-specific filtering to the ACTIVITY tab.
3. Only after parity passes, wire existing Driver Mirror and Offer Desk screens to the unified repository.
4. Keep legacy stores intact for at least one release and retain an explicit rollback/backup plan before considering any legacy-write deprecation.
