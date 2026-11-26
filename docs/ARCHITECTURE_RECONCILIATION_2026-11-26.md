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

## 3. Decision

**Do not merge PR #29 in its current form. Keep it draft until persistence ownership is reconciled.**

The preferred target is the PR #16 ownership model, extended with the stronger provenance, evidence, migration diagnostics and replay protections developed in PR #29:

1. Keep `DriverMirrorStore` / `busybee_driver_mirror.db` as the owner of the canonical provider-event ledger and unified activity query.
2. Add the required ledger/evidence/migration tables through an additive, versioned migration of that existing database; do not introduce `busybee_driver_activity.db`.
3. Keep `busybee_command_centre.db` as the existing source of offers, notices and command-centre data during the transition. Preserve its transactional outbox where it is needed to prevent lost event intent.
4. Project source rows into the canonical ledger with stable source identity, provenance, timestamps, evidence digests, idempotency and row-level failure handling. Do not infer provider lifecycle meaning from unclassified observations.
5. Keep existing product screens on their current stores until data parity and rollback are proven. Switch reads only after acceptance; do not delete legacy data as part of the migration.
6. Reuse PR #29's useful migration diagnostics and test fixtures, but adapt their target to the existing Driver Mirror database and avoid a second independent activity database.

This decision preserves the established product contract while avoiding a third durable copy of sensitive driver activity. It is a target architecture, not permission to change schema until the migration plan and tests are reviewed.

## 4. Risks that must be closed before implementation

- **Migration atomicity:** each copied row and its source marker must commit together in the target database.
- **Concurrency:** source writes during migration must be captured or safely replayed; a one-time snapshot cannot silently miss new rows.
- **Idempotency:** repeated source rows and cross-source duplicates must be distinguishable. Source-row identity is not proof that two providers' observations describe different real-world offers.
- **Ordering:** provider event time may be unknown. Preserve source time and receive time separately; do not invent event time.
- **Outbox delivery:** a crash between target insert and source acknowledgement must be safely replayable.
- **Data retention/deletion:** copied evidence must retain source ownership and deletion lineage. Clearing a source must not leave undeletable derived copies.
- **Rollback:** old stores and screens remain intact until parity is proven; rollback must not require uninstalling the app or deleting user data.
- **Compatibility:** database upgrades must preserve existing installs, including the target Redmi Android 14 environment.

## 5. Required acceptance evidence

Before changing the persistence implementation:

1. Inventory every table and schema version in both existing databases on `main`, PR #16 and PR #29.
2. Specify the exact additive Driver Mirror schema version and upgrade path from every supported prior version.
3. Add migration tests using pre-populated versioned databases, not only fresh in-memory databases.
4. Test missing database/table handling, partial migration, process death, retry, duplicate rows, malformed rows, out-of-order events, large imports and source writes during migration.
5. Verify row counts and stable identity digests for every migrated table; report mismatches as incomplete, never success.
6. Verify existing Offers, Earnings, Driver Mirror and Activity views against the same source fixtures.
7. Document backup/restore and rollback without uninstalling or clearing app data.
8. Pass CI on the exact PR head, then complete physical-device acceptance on the existing Redmi before release.

## 6. Explicit non-actions

- No PR was merged or closed.
- No branch was deleted, force-pushed or rewritten.
- No production database schema, app permission, provider adapter or UI was changed by this decision record.
- No APK release or physical-device acceptance is claimed.
- No signing key or repository secret was read, changed or rotated.

## 7. Immediate next engineering slice

First reconcile the schema inventory and migration contract against the existing Driver Mirror database. Only after that review should the third-database implementation be refactored. Keep PR #29 draft and preserve PR #16 as an architectural reference until a single tested implementation supersedes it.
