# Unified Driver Activity Ledger — migration implementation

**Status:** the original read-only migration, persistent provider-event storage, merged timeline query layer and background legacy-store synchronization remain implemented on the feature branch. A one-time consolidator into Driver Mirror v6 is implemented, covered by CI and now invoked after app startup/legacy sync, notification capture, and provider-event outbox drains. The existing migrator still writes to the third ledger and the repository read path has not switched. Existing screens continue reading their current stores, and no canonical provider events are inferred from imported observations.

## 1. Existing on-device stores

### `busybee_driver_mirror.db` (schema version 6 on the current feature branch)

- `imports`: import source name, import time, row count and inserted count.
- `raw_evidence`: every imported row, including duplicate rows, with import ID, row number, fingerprint, raw content and source name.
- `records`: deduplicated normalized import rows with provider, record type, external ID, occurred-at text, route, distance, duration, parse confidence, gross, fees, bonus, currency, status, raw row and source name.
- `record_corrections`: append-only correction history.
- `provider_events`: the legacy PR #16 provider-event shape; this table is retained as-is during branch convergence.
- Schema v6 also prepares `activity_evidence`, `activity_observations`, `provider_event_envelopes`, `migration_sources` and `migration_rows` as the destination for eventual consolidation.

### `busybee_command_centre.db` (schema version 4 on the current feature branch)

- `offers`: manually/locally captured structured offers, including platform, route, fare, bonus, distances, duration, rating, captured time, source, raw text and status.
- `notices`: deduplicated notification title/body and provider with capture time.
- `whatsapp_groups` and `whatsapp_group_messages`: group-watch data from the map-first branch.
- `provider_event_outbox`: durable offer-event intent from the native-bridge branch.
- `driver_items`, `community_docs`, `road_reports`, `settings`: remain in the command-centre database and are outside this migration.

The migration reader opens source files read-only and never renames, drops or clears them. The current feature branch does apply additive, versioned schema upgrades through Driver Mirror v6 and Command Centre v4; the converged upgrade paths preserve both branch lineages.

## 2. Target

The third database is `busybee_driver_activity.db`. It is a new local read/query surface and does not replace either legacy file during the migration period.

- `activity_evidence`: immutable copied evidence with source database/table/row identity, provider, timestamps, digest and content.
- `activity_observations`: nullable normalized fields, source method, unknown source time, receive time, unclassified state and evidence link.
- `provider_events`: reserved for canonical typed lifecycle events; migration does not populate it.
- `migration_sources`: per-table schema version, start/completion state, source/target counts and source identity digest.
- `migration_rows`: unique source database/table/row mapping; each marker is committed in the same target transaction as its copied record.

## 2a. Prepared consolidation destination

Driver Mirror v6 now contains idempotently created destination tables for evidence, observations, typed event envelopes and migration markers. The rich PR #29 event schema is named `provider_event_envelopes` so it cannot collide with the legacy PR #16 `provider_events` table.

This destination schema is prepared and tested. The current `UnifiedActivityLedgerMigrator` still writes to `busybee_driver_activity.db`. The app now invokes the separate `ActivityLedgerConsolidator` after startup/legacy sync, notification capture and provider-event outbox drains, so the Driver Mirror copy is refreshed at those integration points. This is retryable synchronization, not an atomic cross-database dual-write. No production-device rows are claimed to have been consolidated, and the ACTIVITY repository still defaults to the third database. An explicit `DRIVER_MIRROR` read source is available for tests/controlled validation but is not selected by the app. The third database remains the active source of the current timeline until a later read-path cutover is implemented and verified.

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


## End-to-end user-curated offer verification (2026-11-23)

The Robolectric test ManualOfferEventFlowTest now exercises the complete local path:

1. Save a structured offer in the existing Command Centre store.
2. Emit a typed OFFER_OBSERVED event with manual-entry provenance and no invented provider event time.
3. Emit a separate OFFER_DECISION_OBSERVED event for the driver's explicit BusyBee-local skip action.
4. Persist the canonical events through the SQLite idempotency boundary.
5. Run the read-only offer migration and query the merged Activity timeline.
6. Verify the migrated legacy observation and both canonical events coexist, full offer fields and digests survive, repeat inserts are rejected, and the original legacy offer row/status/raw evidence remain intact.

CI run #474 passed all Android unit tests and the build/signature/artifact stages. This is automated Robolectric evidence, not physical-device migration acceptance.


## Durable offer-event outbox (2026-11-23)

The offer source and event source are separate SQLite files during this migration stage. To avoid losing an event between a legacy offer write and its canonical event write, the existing Command Centre database now persists an outbox entry in the same transaction as:
- a newly saved offer plus its `OFFER_OBSERVED` event intent; or
- a BusyBee-local offer status decision plus its `OFFER_DECISION_OBSERVED` event intent.

The background drain writes pending records into the canonical provider-event table. It only marks the source outbox row delivered after a durable insert or a confirmed duplicate. Replaying after a process interruption is therefore idempotent. Existing source rows remain intact. The Command Centre v3-to-v4 migration is additive and creates whichever of the WhatsApp group tables or provider-event outbox is missing, preserving both known v3 branch lineages.

Robolectric coverage exercises both the process-restart replay and the additive upgrade. CI run #485 is in_progress on 8cb9a426af4a0a8db467af6d6857104c6c1d5f14; validation has not completed. Run: https://github.com/ysfclc/BusyBee/actions/runs/45134736851



## 8. Driver Mirror v6 consolidation copier (2026-11-27)

A dedicated `ActivityLedgerConsolidator` now copies the existing `busybee_driver_activity.db` into the prepared Driver Mirror v6 tables without changing the existing migrator or repository read path.

- Opens the source only when the file exists and uses `SQLiteDatabase.OPEN_READONLY`; a missing source returns an incomplete report and does not create either database.
- Copies `activity_evidence` before `activity_observations`, preserving primary IDs and evidence foreign keys.
- Copies rich `provider_events` into `provider_event_envelopes`, preserving event IDs, idempotency keys, provenance, timestamps, evidence fields and payload JSON. The legacy Driver Mirror `provider_events` table is untouched.
- Copies historical `migration_sources` and `migration_rows` records. New consolidation markers use a separate source-database namespace so historical markers remain intact.
- Uses one destination transaction per source row and marker. A retry verifies already-marked rows against every stored field; a conflicting primary/unique-key row fails instead of being silently accepted.
- Verifies source counts, stable source-row identity digests, marker counts and row-by-row payload parity before marking each table complete.
- Never deletes, renames, truncates or writes to the source database. The old file remains available for rollback.
- Requires exact source/destination column-set compatibility; unknown schema variants fail closed rather than being guessed.

Robolectric coverage: full copy of all five tables, ID/FK preservation, historical migration markers, preservation of the legacy Driver Mirror event table, idempotent second run, conflicting target payload rejection, and missing-source behavior.

**Verification:** PR run [#565](https://github.com/ysfclc/BusyBee/actions/runs/46019739390) passed Rust domain tests, Rust Android library builds, Android/Robolectric tests, the Bouncy Castle 1.85 dependency assertion, debug APK assembly and debug APK signature verification. Test report artifact: `busybee-android-test-reports-565` (ID `15208042227`). Debug-only validation artifact: `busybee-debug-apk-565` (ID `15207962619`). Release signing and release APK assembly were correctly skipped on the feature branch.

**Not yet done:** no app code calls the consolidator, no live database was migrated, the old repository read path is unchanged, and no production release APK or physical-device acceptance is claimed. Next: define and test how new writes are kept in sync during the migration window, then add a controlled repository cutover with parity checks and rollback.


## 9. App integration — retryable mirror synchronization (2026-11-27)

The consolidator is now called at the existing activity-update boundaries:

- MainActivity startup and the existing legacy migration refresh run the legacy migrator first, then refresh Driver Mirror v6. The Sources diagnostics show both reports separately.
- CSV import and manual offer save/decision already call the same refresh path, so their new evidence/observations are copied after the legacy ledger is updated.
- The provider-event outbox drain refreshes Driver Mirror after the batch has been delivered to the third-database event store, including the duplicate/retry path.
- The Android notification listener runs the legacy migrator and then the consolidator after a newly captured Uber/Bolt/FreeNow/iTaxi/Cabify/Lyft notice.

The third database remains the source of truth during this stage. Each consolidator run is idempotent and row-verified; a failed/partial run can be retried by the next existing trigger. These are sequential operations against separate SQLite files, not a cross-database atomic commit. The ACTIVITY repository and its current provider-event query still read the third database. No cutover or deletion is performed.

**Verification:** PR run [#582](https://github.com/ysfclc/BusyBee/actions/runs/46021873820) on commit `4674a9ea2c35608c0df51f4ccaad1c762737114a` passed Rust tests/build, Android/Robolectric tests, Bouncy Castle 1.85 dependency verification, debug APK assembly and debug APK signature verification. Test report artifact: `busybee-android-test-reports-582` (ID `15210820217`). Debug-only validation artifact: `busybee-debug-apk-582` (ID `15210490908`). Release signing and release APK assembly were skipped on the feature branch.


## 10. Opt-in Driver Mirror read path and parity test (2026-11-27)

`UnifiedActivityRepository` now accepts an explicit `UnifiedActivityReadSource`:

- `ORIGINAL_LEDGER` remains the default and continues reading `busybee_driver_activity.db` / `provider_events`.
- `DRIVER_MIRROR` reads the corresponding `DriverMirrorStore` tables / `provider_event_envelopes`.
- The same `UnifiedActivityItem` mapping and ordering rules are used for both sources.
- A Robolectric test creates observation + canonical-event data, runs the verified consolidator, then asserts the full returned timeline objects from both sources are equal.

No production caller selects `DRIVER_MIRROR`; MainActivity remains on the original source. This creates a tested opt-in path without exposing users to a premature cutover.

**Verification:** PR run [#593](https://github.com/ysfclc/BusyBee/actions/runs/46023111698) on commit `42242ee6a5587ab0572a18cf3162854cf1f3fd4a` passed Rust tests/build, Android/Robolectric tests (including source parity), Bouncy Castle 1.85 dependency verification, debug APK assembly and debug signature verification. Test report artifact: `busybee-android-test-reports-593` (ID `15210357271`). Debug-only validation APK: `busybee-debug-apk-593` (ID `15211875593`). Release signing was skipped on the feature branch.


## 11. Read-only parity verifier and Sources-screen action (2026-11-27)

Added `ActivityLedgerConsolidator.verifyParity()` as a read-only acceptance check:

- Verifies all five source/target table pairs, stable row identity markers, counts and row payloads without copying or mutating rows.
- Detects new source rows that have not yet been mirrored, missing markers, missing target rows and changed target payloads.
- Returns incomplete if the source is missing or if the Driver Mirror database does not already exist. In particular, the verifier does not create an empty target database.
- Uses the same table-level report formatter as the consolidation step.

The Sources screen now exposes **VERIFY DRIVER MIRROR PARITY** separately from **REFRESH MIGRATION CHECK**. The first is read-only; the second runs the existing legacy migration followed by the retryable copy. Both results are included in the shareable diagnostic report.

Tests cover successful parity, detection of a new source row, successful parity after retry, conflicting target payloads, and the missing-target no-create behavior.

**Verification:** PR run [#609](https://github.com/ysfclc/BusyBee/actions/runs/46025748291) on exact code commit `b7bbcb462ab8d61a39b45cbe255ea86252a4f7a5` passed Rust tests/build, Android/Robolectric tests, dependency verification, debug APK assembly and debug signature verification. Test report artifact: `busybee-android-test-reports-609` (ID `15212960120`). Debug-only validation APK: `busybee-debug-apk-609` (ID `15212228762`). Release signing was skipped on the feature branch.

**Still deliberately unchanged:** the Activity screen reads `ORIGINAL_LEDGER`; `DRIVER_MIRROR` remains opt-in. No production read switch, database deletion, release APK or physical-device acceptance is claimed.
