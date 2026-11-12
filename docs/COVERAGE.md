# Requirement coverage

A requirement is **done** only when its implementation, focused test, and documentation are all present and the relevant checks pass. Partial rows stay visible; a working UI surface is not evidence that the underlying source or provider capability is complete.

| Req ID | Requirement | Implementation | Test | Documentation | Status |
|---|---|---|---|---|---|
| R-DM-001 | Import the documented provider-neutral CSV format and preserve source evidence while avoiding duplicate canonical records | `DriverMirrorStore.kt` — `DriverCsv.parse`, `importRows` | `DriverCsvTest` covers parsing and missing values; SQLite persistence/dedupe has no direct unit test yet | `DRIVER_MIRROR_IMPORT.md` | 🟡 partial |
| R-DM-002 | Keep unknown values unknown and prevent unlike currencies from being combined | `DriverMirrorStore.kt`, `DriverAnalytics.kt` | `DriverAnalyticsTest.missingFeesNeverBecomeZero`, `unlikeCurrenciesAreNotMixed` | `DRIVER_MIRROR_IMPORT.md` | 🟡 partial |
| R-DM-003 | Rank offers using estimated net hourly value after pickup and vehicle costs, with driver-configured filters | `DriverCommandStore.kt` — offer model and ranking flow in Android app | `OfferEngineTest` | `PRODUCT_CHARTER.md`, `ROADMAP.md` | 🟡 partial — notification capture remains capability-limited |
| R-DM-004 | Calculate recurring due dates deterministically for supported reminder cadences | `DriverCommandStore.kt` — `Recurrence.nextDue` | `RecurrenceTest` | `ROADMAP.md` and README Driver Hub surface | 🟡 partial — lifecycle/reminder delivery needs device verification |
| R-DM-005 | Headline gross/fees/bonuses include recognised PLN trip rows only; offers/other activity are excluded; headline net requires gross, fee and bonus on every recognised trip | `DriverMirrorStore.kt` — `DriverFinancialSummaryCalculator` used by `summary()` | `DriverFinancialSummaryTest` | `DRIVER_MIRROR_IMPORT.md#r-dm-005--trip-only-headline-earnings` | 🟡 pending CI |
| R-ROAD-001 | Road events preserve source identity and reject future/expired observations | `rust/crates/road-core/src/lib.rs` — `RoadEvent::is_current_at` | Rust unit tests: fresh, expired, future and invalid-TTL cases | `ARCHITECTURE.md`, `DECISIONS.md` | 🟡 partial — ingestion/fusion is not implemented |

## Coverage gaps

- Provider-specific Uber/Bolt export adapters and statement reconciliation need real user-owned sample exports.
- SQLite import transaction, raw-evidence retention and duplicate behaviour need direct store-level tests.
- Road source connectors, canonical event fusion, conflict handling, licence enforcement and live source-health are not a complete implementation.
- Real-device install, update-signature continuity, battery, performance and accessibility are not verified by unit tests.
