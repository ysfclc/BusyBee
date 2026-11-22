# Native Provider Bridge — implementation checkpoint 1

**Status:** Slice 1 domain contract implemented on a feature branch; persistent event storage and the unified migration are not yet implemented.

## Scope

This checkpoint adds a provider-neutral Kotlin contract only. It deliberately does not change the two existing SQLite stores, notification collection, permissions, screens, provider integrations or BusyBee's current product behaviour.

## Contract

- Provider capability, connection-health and acquisition-method types.
- A typed event envelope with adapter identity/version, provider, lifecycle identity, source method, evidence class/reference/digest, source event time and local receive time.
- Typed offer, trip, earning and adjustment payloads. Unknown values remain nullable.
- Adapter registry permits multiple acquisition methods per provider but requires unique adapter IDs.
- Event ingestion validates payload/type consistency and required envelope identity.
- Persistent stores must enforce a unique idempotency key. The ingestor returns accepted, duplicate or rejected outcomes.
- Delayed and out-of-order evidence is retained; timeline ordering is a read/query concern, not an ingestion-time discard rule.
- An offer cannot become expired from disappearance alone. Expiry requires an explicit provider state, explicit provider deadline or user confirmation.

## Verification added

Unit tests cover partial payloads, duplicate idempotency keys, delayed/out-of-order observations, unknown source timestamps, explicit expiry evidence, payload mismatch and adapter registry uniqueness.

## Deliberately deferred

- Durable SQLite implementation and unique index.
- Migration/reconciliation of `busybee_driver_mirror.db` and `busybee_command_centre.db`.
- Evidence linkage between imported raw rows, notification records and canonical events.
- UI timeline and Connected Apps surfaces.
- Any claim of an Uber/Bolt live connection.

## Next slice

Design and implement a non-destructive local activity ledger migration. Preserve both legacy databases and their raw rows; copy source records into the new canonical ledger with source-specific provenance and idempotent migration markers. Re-running migration must not duplicate events. Verify with synthetic legacy databases, including interruption/restart and partially populated tables, before wiring the UI.
