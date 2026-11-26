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

## 9. Immediate next engineering slice

Continue with Driver Mirror schema convergence. Define one monotonic version path that preserves PR #16's duration/confidence/provider-events additions and PR #29's record-corrections table, while explicitly retaining the different provider-event schemas instead of overwriting one with the other. Then add versioned pre-populated upgrade fixtures before moving any activity tables out of the third database. Keep PR #29 draft and preserve PR #16 as an architectural reference until all schema paths and rollback are tested.
