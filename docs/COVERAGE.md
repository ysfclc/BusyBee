# Requirement coverage

A requirement is **done** only when its implementation, focused test, and documentation are all present and the relevant checks pass. Partial rows stay visible; a working UI surface is not evidence that the underlying source or provider capability is complete.

| Req ID | Requirement | Implementation | Test | Documentation | Status |
|---|---|---|---|---|---|
| R-DM-001 | Import the documented provider-neutral CSV format and preserve source evidence while avoiding duplicate canonical records | `DriverMirrorStore.kt` — `DriverCsv.parse`, `importRows` | `DriverCsvTest` covers quoted values, unknown optional fields, duplicate headers, extra cells, missing required values and Turkish-locale header handling; `DriverMirrorStoreTest` covers duplicate batches, raw evidence retention, import history and transaction rollback (store tests authored; not executed) | `DRIVER_MIRROR_IMPORT.md` | 🟡 partial — tests await execution |
| R-DM-002 | Keep unknown values unknown and prevent unlike currencies from being combined | `DriverMirrorStore.kt`, `DriverAnalytics.kt` | `DriverAnalyticsTest.missingFeesNeverBecomeZero`, `unlikeCurrenciesAreNotMixed`; `DriverFinancialSummaryTest.recognisesUppercaseTripTypesIndependentOfDeviceLocale` | `DRIVER_MIRROR_IMPORT.md` | 🟡 partial |
| R-DM-003 | Rank offers using estimated net hourly value after pickup and vehicle costs, with driver-configured filters | `DriverCommandStore.kt` — offer model and ranking flow in Android app | `OfferEngineTest` | `PRODUCT_CHARTER.md`, `ROADMAP.md` | 🟡 partial — notification capture remains capability-limited |
| R-DM-004 | Calculate recurring due dates deterministically for supported reminder cadences | `DriverCommandStore.kt` — `Recurrence.nextDue` | `RecurrenceTest` | `ROADMAP.md` and README Driver Hub surface | 🟡 partial — lifecycle/reminder delivery needs device verification |
| R-DM-005 | Headline gross/fees/bonuses include recognised PLN trip rows only; offers/other activity are excluded; headline net requires gross, fee and bonus on every recognised trip | `DriverMirrorStore.kt` — `DriverFinancialSummaryCalculator` used by `summary()` | `DriverFinancialSummaryTest` (5 cases; not executed in current CI) | `DRIVER_MIRROR_IMPORT.md#r-dm-005--trip-only-headline-earnings` | 🟡 implemented; CI blocked before runner (runs 47/48) |
| R-ROAD-001 | Road events preserve source identity and reject future/expired observations | `rust/crates/road-core/src/lib.rs` — `RoadEvent::is_current_at` | Rust unit tests: fresh, expired, future and invalid-TTL cases | `ARCHITECTURE.md`, `DECISIONS.md` | 🟡 partial — ingestion/fusion is not implemented |

## Coverage gaps

- Provider-specific Uber/Bolt export adapters and statement reconciliation need real user-owned sample exports.
- Store-level SQLite tests are authored but remain unexecuted; runtime behaviour is not yet verified.
- Road source connectors, canonical event fusion, conflict handling, licence enforcement and live source-health are not a complete implementation.
- Real-device install, update-signature continuity, battery, performance and accessibility are not verified by unit tests.
