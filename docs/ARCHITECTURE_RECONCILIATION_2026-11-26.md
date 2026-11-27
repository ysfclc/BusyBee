# BusyBee Architecture Reconciliation — 2026-11-26

**Status:** engineering decision record; no production architecture change authorised by this document  
**Scope:** reconcile the Native Provider Bridge implementation line (PR #29) with the earlier map-first integration line (PR #16) and the fresh-start product boundary (PR #27).

## 1. Product and data ownership

- BusyBee remains the driver-facing product and canonical owner of provider-neutral driver activity.
- App Observer remains a separate research instrument. Its capture UI, research storage and experimental capture mechanisms do not become BusyBee production dependencies by default.
- Provider adapters produce evidence and typed events. The BusyBee domain owns event meaning, lifecycle rules, provenance, idempotency and user-facing workflows.
- Existing user records and raw evidence must remain recoverable throughout migration and rollback.

## 2. Verified branch comparison

### PR #16 — map-first integration line

Source inspected on `integration/driver-command-centre-2026-11-16`:

- `DriverMirrorStore` owns the existing `busybee_driver_mirror.db`.
- `SqliteProviderEventRepository` explicitly owns no database; it receives `DriverMirrorStore` and stores canonical provider events through that existing store.
- The design describes an additive Driver Mirror schema migration and a read-only projection of existing offers, notifications and imported records.
- The Command Centre store remains a source for its existing product data. This is not a global one-database rewrite; it avoids introducing a third activity database.

### PR #29 — Native Provider Bridge implementation line

Source inspected on `feature/native-provider-bridge-slice-1-2026-11-22`:

- `DriverMirrorStore` still owns `busybee_driver_mirror.db`.
- `DriverCommandStore` still owns `busybee_command_centre.db` and its offer outbox.
- `UnifiedActivityLedger` introduces a third file, `busybee_driver_activity.db`.
- `UnifiedActivityLedgerMigrator` opens the two existing stores read-only and copies selected import, evidence, record, offer and notice rows into the third database, with per-row migration markers and source/target identity-digest checks.
- Existing Driver Mirror and Offer Desk screens continue reading their legacy stores; the new Activity screen reads the copied ledger. The PR documentation explicitly describes the third database as separate from both legacy stores.
- The Command Centre outbox and the third-database event ledger add a durable cross-database delivery path for manually curated offer events.

These are two different persistence ownership strategies. They must not be combined or merged wholesale without an explicit decision.

## 3. Schema inventory and branch compatibility

The baseline source inventory was checked directly on all three refs before the first convergence implementation:

| Database | main | PR #16 branch | PR #29 branch |
|---|---:|---:|---:|
| busybee_driver_mirror.db | schema 1 | schema 4 | schema 2 |
| busybee_command_centre.db | schema 2 | schema 3 | schema 3 at audit baseline; schema 4 after convergence slice |
| busybee_driver_activity.db | absent | absent | schema 2 |

Important shape differences:

- Main Driver Mirror has imports, raw_evidence and records; records do not yet have the later duration/confidence fields.
- PR #16 Driver Mirror schema 4 adds duration_minutes, parse_confidence and the durable provider_events table. Its upgrade path is explicit: v1→2 adds duration, v2→3 adds parse confidence, v3→4 creates provider events.
- PR #29 Driver Mirror schema 2 adds record_corrections, but does not contain PR #16's duration/confidence/provider-events additions.
- PR #29's separate activity database is schema 2 and owns activity_evidence, activity_observations, provider_events, migration_sources and migration_rows.
- The two provider_events tables are not schema-compatible: PR #16 stores provider, dedupe_key, event_time, received_at and event_blob; PR #29 stores typed event identity, adapter/version, lifecycle, source/evidence metadata and payload_json. If the third database is retired, existing PR #16 rows need an explicit preservation/translation path before the richer PR #29 table can own that name.
- PR #29's record_corrections table is absent from PR #16's Driver Mirror schema, so it must be retained in any consolidated migration.
- Command Centre schema 2 on main becomes schema 3 on both feature branches. PR #16 schema 3 adds WhatsApp group watch tables; PR #29 schema 3 instead adds the provider-event outbox. The two schema-3 definitions are not interchangeable.

**Compatibility consequence:** these branch-local version numbers are not a merge plan. A database at Driver Mirror v4 must never be opened by a branch that declares version 2; Android's default SQLiteOpenHelper downgrade path can fail. Likewise, a Command Centre v3 database must not be assumed to have both PR #16's WhatsApp tables and PR #29's outbox. The final schema must use one monotonically increasing version sequence and explicitly create/preserve every required table/column from both lines. No branch may lower a shipped database version or rely on CREATE TABLE IF NOT EXISTS to hide a missing migration.

## 4. Decision

**Do not merge PR #29 in its current form. Keep it draft until persistence ownership is reconciled.**

The preferred target is the PR #16 ownership model, extended with the stronger provenance, evidence, migration diagnostics and replay protections developed in PR #29:

1. Keep `DriverMirrorStore` / `busybee_driver_mirror.db` as the owner of the canonical provider-event ledger and unified activity query.
2. Add the required ledger/evidence/migration tables through an additive, versioned migration of that existing database; do not introduce `busybee_driver_activity.db`.
3. Keep `busybee_command_centre.db` as the existing source of offers, notices and command-centre data during the transition. Preserve its transactional outbox where it is needed to prevent lost event intent.
4. Project source rows into the canonical ledger with stable source identity, provenance, timestamps, evidence digests, idempotency and row-level failure handling. Do not infer provider lifecycle meaning from unclassified observations.
5. Keep existing product screens on their current stores until data parity and rollback are proven. Switch reads only after acceptance; do not delete legacy data as part of the migration.
6. Reuse PR #29's useful migration diagnostics and test fixtures, but adapt their target to the existing Driver Mirror database and avoid a second independent activity database.

This decision preserves the established product contract while avoiding a third durable copy of sensitive driver activity. It is a target architecture, not permission to change schema until the migration plan and tests are reviewed.

## 5. Risks that must be closed before implementation

- **Migration atomicity:** each copied row and its source marker must commit together in the target database.
- **Concurrency:** source writes during migration must be captured or safely replayed; a one-time snapshot cannot silently miss new rows.
- **Idempotency:** repeated source rows and cross-source duplicates must be distinguishable. Source-row identity is not proof that two providers' observations describe different real-world offers.
- **Ordering:** provider event time may be unknown. Preserve source time and receive time separately; do not invent event time.
- **Outbox delivery:** a crash between target insert and source acknowledgement must be safely replayable.
- **Data retention/deletion:** copied evidence must retain source ownership and deletion lineage. Clearing a source must not leave undeletable derived copies.
- **Rollback:** old stores and screens remain intact until parity is proven; rollback must not require uninstalling the app or deleting user data.
- **Compatibility:** database upgrades must preserve existing installs, including the target Redmi Android 14 environment.

## 6. Required acceptance evidence

Before changing the persistence implementation:

1. Inventory every table and schema version in both existing databases on `main`, PR #16 and PR #29.
2. Specify the exact additive Driver Mirror schema version and upgrade path from every supported prior version.
3. Add migration tests using pre-populated versioned databases, not only fresh in-memory databases.
4. Test missing database/table handling, partial migration, process death, retry, duplicate rows, malformed rows, out-of-order events, large imports and source writes during migration.
5. Verify row counts and stable identity digests for every migrated table; report mismatches as incomplete, never success.
6. Verify existing Offers, Earnings, Driver Mirror and Activity views against the same source fixtures.
7. Document backup/restore and rollback without uninstalling or clearing app data.
8. Pass CI on the exact PR head, then complete physical-device acceptance on the existing Redmi before release.

## 7. Explicit non-actions

- No PR was merged or closed.
- No branch was deleted, force-pushed or rewritten.
- No production database schema, app permission, provider adapter or UI was changed by this decision record.
- No APK release or physical-device acceptance is claimed.
- No signing key or repository secret was read, changed or rotated.

## 8. Implemented compatibility slice — Command Centre v4

The first part of the reconciliation is now implemented on PR #29's feature branch:

- `DriverCommandStore` advances the Command Centre schema monotonically from v3 to v4.
- Fresh databases create both `whatsapp_groups` / `whatsapp_group_messages` and `provider_event_outbox`.
- The v3→v4 upgrade uses idempotent `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` helpers. It preserves whichever feature set already exists and adds the missing set.
- This explicitly supports both incompatible v3 lineages: PR #16's map-first WhatsApp tables and PR #29's native-bridge outbox.
- Existing offer rows and existing outbox / WhatsApp rows are preserved by tests. The older v2→v4 path is also covered by the existing outbox migration test.
- New tests: `DriverCommandStoreSchemaConvergenceTest` exercises both v3 upgrade directions and fresh creation.

Source commits: `1cb3067` (schema v4), `3cc6ba9` (existing migration expectation), `7bfc1e8` (branch-convergence tests), and `4dfc5e9` (feature-push signing gate correction).

**Verification:** PR run [#539](https://github.com/ysfclc/BusyBee/actions/runs/45890531206) completed successfully, including Rust tests/build, Android unit tests, the Bouncy Castle dependency assertion, debug APK assembly and debug signature verification. Its debug artifact is `busybee-debug-apk-539` (artifact ID `15152060393`); it is validation-only. Push run [#538](https://github.com/ysfclc/BusyBee/actions/runs/45890521999) also completed successfully; feature-branch push correctly skipped APK production.

Gradle dependencyInsight explicitly resolved `org.bouncycastle:bcprov-jdk18on:1.85`, overriding Robolectric 4.14.1's transitive 1.78.1. The CI assertion passed.

## 9. Implemented compatibility slice — Driver Mirror v5

The second part of persistence convergence is now implemented on the same feature branch:

- `DriverMirrorStore` advances the database monotonically to schema v5.
- The canonical `records` table now includes `duration_minutes` and `parse_confidence`.
- `record_corrections` is created idempotently, preserving the PR #29 v2 lineage and adding the table to the PR #16 v4 lineage.
- The existing PR #16 `provider_events` table and time index are created idempotently. Existing event rows are retained; this does not overwrite or reinterpret the richer provider-event table in PR #29's separate activity database.
- Upgrade paths are explicit: v1→v2 corrections, v2→v3 duration, v3→v4 confidence, v4→v5 provider events plus correction-table convergence. The v5 step uses `IF NOT EXISTS` so both known v2/v4 branch lineages converge without duplicate-table failures.
- New tests cover main v1, native-bridge v2, map-first v4 and fresh-install creation. They assert the target schema version, required columns/tables and preservation of existing records, correction history and provider-event rows.

Source commits: `e4d0450` (schema v5) and `b5f4cfd` / `8bee88c` (versioned migration fixtures and fixture correction).

**Verification:** PR run [#546](https://github.com/ysfclc/BusyBee/actions/runs/45891277811) completed successfully. Rust domain tests, Rust Android libraries, Android unit tests, the Bouncy Castle 1.85 dependency assertion, debug APK assembly and debug signature verification all passed. Gradle reported `BUILD SUCCESSFUL`; the dependencyInsight output showed `bcprov-jdk18on:1.85` selected over Robolectric's 1.78.1. Test report artifact: `busybee-android-test-reports-546` (ID `15152216181`). Debug-only validation artifact: `busybee-debug-apk-546` (ID `15152495152`).

The separate Vercel status check reports its free-plan build-rate limit (`upgradeToPro=build-rate-limit`). This is not a Rust, Android, Gradle or test failure. No deployment is claimed.

## 10. Prepared consolidation destination — Driver Mirror v6

The Driver Mirror schema now contains the destination structures required for the eventual consolidation:

- Schema v6 adds `activity_evidence`, `activity_observations`, `migration_sources` and `migration_rows` to the existing Driver Mirror database.
- The richer PR #29 typed event schema is stored as `provider_event_envelopes`, deliberately separate from the older PR #16 `provider_events` table. Neither table overwrites the other.
- All new tables and indexes use idempotent creation. The v5→v6 migration preserves existing records, corrections and legacy provider-event rows.
- Fresh-install creation is covered alongside the v5 upgrade path.

New tests: the Driver Mirror schema suite now covers main v1→v6, native-bridge v2→v6, map-first v4→v6, v5→v6, and fresh schema creation. The v5 fixture explicitly verifies that existing legacy provider events remain intact while the new envelope/evidence/migration tables appear.

Source commits: `2ab5d28` (target schema), `bc8c16d` (Driver Mirror v6 integration), `804a7f2` and `c66e37e` (upgrade tests and corrected version expectations).

**Verification:** PR run [#555](https://github.com/ysfclc/BusyBee/actions/runs/45892056363) completed successfully. Rust tests/build, Android unit tests, Bouncy Castle 1.85 resolution, debug APK assembly and debug signature verification all passed. Gradle reported `BUILD SUCCESSFUL`. Artifacts: `busybee-android-test-reports-555` (ID `15152406194`) and debug-only `busybee-debug-apk-555` (ID `15151967281`).

As above, the Vercel status check is separately blocked by its free-plan build-rate limit. It does not represent a source/test failure. No APK release, database switch or merge is claimed.

## 11. Immediate next engineering slice

Implement the live migration handoff: keep new writes synchronized while the one-time copier runs, then add a controlled repository cutover with parity checks and rollback. Only after those stages are proven should the third database be retired. Keep PR #29 draft until the full migration and read-path transition are tested.


## 12. Implemented one-time consolidator — Driver Mirror v6 (2026-11-27)

Added `ActivityLedgerConsolidator` and dedicated Robolectric tests on the PR #29 feature branch.

- Source `busybee_driver_activity.db` is opened read-only and is never deleted, renamed, cleared or modified.
- A missing source is reported incomplete without creating a source or destination database.
- The copier preserves IDs and foreign-key references, copying evidence before observations.
- Rich typed events are mapped to `provider_event_envelopes`; the legacy Driver Mirror `provider_events` table remains separate and unchanged.
- Historical `migration_sources` and `migration_rows` are copied. New progress markers use a distinct `__consolidation__` source namespace.
- Every copied row is compared field-by-field with its destination counterpart. Already-marked rows are rechecked on retry; conflicting data stops the migration.
- Completion requires source/marker count parity, a stable row-identity digest and successful row-by-row payload verification.
- The existing `UnifiedActivityLedgerMigrator` and `UnifiedActivityRepository` read path remain unchanged. App startup/legacy sync, notification capture and provider-event outbox drains now invoke the consolidator as a retryable mirror refresh. No read-path cutover or on-device data migration is claimed.

Tests cover successful copying of all five source tables, ID/FK preservation, historical migration markers, idempotent second run, conflicting target payload rejection, and missing-source behavior.

**Verification:** PR run [#565](https://github.com/ysfclc/BusyBee/actions/runs/46019739390) passed Rust domain tests, Rust Android library builds, Android/Robolectric tests, Bouncy Castle 1.85 dependency verification, debug APK assembly and debug signature verification. Test artifact: `busybee-android-test-reports-565` (ID `15208042227`). Debug-only validation artifact: `busybee-debug-apk-565` (ID `15207962619`). Release signing was skipped on the feature branch.

**Remaining work:** dual-write or another proven write-capture strategy during the migration window; reconciliation of source mutations; repository read-path cutover; on-device parity; rollback exercise. PR #29 remains draft and no release APK is claimed.


## 13. Retryable Driver Mirror synchronization triggers (2026-11-27)

The new consolidator is now integrated after the existing source writes without changing database ownership or the ACTIVITY read path:

- MainActivity startup and manual migration refresh: run legacy migration, then consolidate to Driver Mirror v6 and display both outcomes.
- CSV import and manual offer save/decision: reuse the same refresh path.
- Provider-event outbox drain: consolidate after the source event-store batch so newly persisted provider events are mirrored.
- Platform notification listener: after a new notice is saved in Command Centre, run legacy migration and consolidation on its existing background executor.

This is sequential, retryable synchronization between two SQLite files, not a cross-database atomic transaction. A failure is logged and a later trigger retries. The third activity database remains canonical; the Activity repository still reads it. No schema switch, data deletion, release APK or physical-device acceptance is claimed.

**Verification:** PR run [#582](https://github.com/ysfclc/BusyBee/actions/runs/46021873820) passed all Rust/Android tests, dependency verification, debug APK assembly and debug APK signature verification on exact code commit `4674a9ea2c35608c0df51f4ccaad1c762737114a`. Artifacts: test reports `15210820217`; debug-only validation APK `15210490908`. Release APK assembly was skipped.


## 14. Opt-in read-source validation (2026-11-27)

Added `UnifiedActivityReadSource` to the timeline repository. `ORIGINAL_LEDGER` remains the default; `DRIVER_MIRROR` selects Driver Mirror v6 and its `provider_event_envelopes` table. Both paths map into the same timeline model and sorting rules.

A Robolectric parity test copies evidence/observation/event data with the consolidator and asserts that both repository sources return equal timeline items. No production caller opts into Driver Mirror yet; the app's Activity screen remains on the original database until runtime parity, fallback and rollback are implemented.

The consolidator now implements `AutoCloseable`; app and test call sites close its Driver Mirror helper after each run.

**Verification:** PR run [#593](https://github.com/ysfclc/BusyBee/actions/runs/46023111698) passed the full Rust/Android suite, the new read-source parity test, dependency verification, debug APK assembly and debug signature verification. Test report artifact `15210357271`; debug-only validation artifact `15211875593`. No release APK was produced.
