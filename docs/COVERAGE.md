# Requirement coverage

A requirement is **done** only when its implementation, focused test, and documentation are all present and the relevant checks pass. Partial rows stay visible; a working UI surface is not evidence that the underlying source or provider capability is complete.

| Req ID | Requirement | Implementation | Test | Documentation | Status |
|---|---|---|---|---|---|
| R-DM-001 | Import the documented provider-neutral CSV format and preserve source evidence while avoiding duplicate canonical records | `DriverMirrorStore.kt` — `DriverCsv.parse`, `importRows` | `DriverCsvTest` covers quoted values, unknown optional fields, duplicate headers, extra cells, missing required values and Turkish-locale header handling; `DriverMirrorStoreTest` covers duplicate batches, raw evidence retention, import history and transaction rollback (store tests pass on Vercel Sandbox) | `DRIVER_MIRROR_IMPORT.md` | 🟡 partial — tests pass; provider samples, correction handling and migration coverage remain open |
| R-DM-002 | Keep unknown values unknown and prevent unlike currencies from being combined | `DriverMirrorStore.kt`, `DriverAnalytics.kt` | `DriverAnalyticsTest` and `DriverFinancialSummaryTest` (10 focused cases across both classes) pass on Vercel Sandbox | `DRIVER_MIRROR_IMPORT.md` | 🟡 partial — no real provider export validation |
| R-DM-003 | Rank offers using estimated net hourly value after pickup and vehicle costs, with driver-configured filters | `DriverCommandStore.kt` — offer model and ranking flow in Android app | `OfferEngineTest` | `PRODUCT_CHARTER.md`, `ROADMAP.md` | 🟡 partial — notification capture remains capability-limited |
| R-DM-004 | Calculate recurring due dates deterministically for supported reminder cadences | `DriverCommandStore.kt` — `Recurrence.nextDue` | `RecurrenceTest` | `ROADMAP.md` and README Driver Hub surface | 🟡 partial — lifecycle/reminder delivery needs device verification |
| R-DM-005 | Headline gross/fees/bonuses include recognised PLN trip rows only; offers/other activity are excluded; headline net requires gross, fee and bonus on every recognised trip | `DriverMirrorStore.kt` — `DriverFinancialSummaryCalculator` used by `summary()` | `DriverFinancialSummaryTest` (5 cases; all pass on Vercel Sandbox) | `DRIVER_MIRROR_IMPORT.md#r-dm-005--trip-only-headline-earnings` | 🟡 implemented; 5 focused tests pass on Vercel Sandbox |
| R-ROAD-001 | Road events preserve source identity and reject future/expired observations | `rust/crates/road-core/src/lib.rs` — `RoadEvent::is_current_at` | Rust unit tests: fresh, expired, future and invalid-TTL cases (4/4 pass on Vercel Sandbox) | `ARCHITECTURE.md`, `DECISIONS.md` | 🟡 partial — ingestion/fusion is not implemented |

## Coverage gaps

- Provider-specific Uber/Bolt export adapters and statement reconciliation need real user-owned sample exports.
- Store-level SQLite tests pass in the Vercel Sandbox; real-device persistence, migration behaviour and corrected-source reconciliation still need verification.
- Road source connectors, canonical event fusion, conflict handling, licence enforcement and live source-health are not a complete implementation.
- Real-device install, update-signature continuity, battery, performance and accessibility are not verified by unit tests.
- Build verification used a Vercel Sandbox rather than a successful standard Vercel deployment; release signing and device install remain pending.
