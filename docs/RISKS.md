# BusyBee risk register

This register tracks material product, data-integrity, safety and delivery risks. Ratings are qualitative and should be revisited when source coverage, deployment or user-data handling changes.

| ID | Risk | Likelihood | Impact | Mitigation / control | Status |
|---|---|---:|---:|---|---|
| R-01 | Imported activity may be misclassified as revenue, or incomplete source values may be presented as zero/complete | Medium | High | Keep record types and currencies explicit; calculate headline earnings from recognised trip rows only; preserve unknowns; require focused tests and source-statement reconciliation before claiming financial completeness | Open — R-DM-005 implemented but unverified |
| R-02 | Duplicate imports, partial failures or schema changes could corrupt or lose personal trip/evidence history | Medium | High | Use transactional imports, deterministic fingerprints, a separate raw-evidence ledger and non-destructive migrations; run SQLite store-level regression tests and migration tests before release | Open — persistence tests authored, execution pending |
| R-03 | Uber/Bolt export formats or partner data access may differ from the generic interchange contract or be unavailable under provider terms | High | High | Do not claim provider adapters or direct API access without a real user-owned sample and documented access/licence review; preserve raw source evidence and make adapters independently testable | Open |
| R-04 | Private trip, earnings, location or chat-export data could be exposed beyond the driver's device without informed consent | Low | Critical | Keep personal records app-private and local-first; do not upload or share by default; keep community contributions separate and opt-in; review permissions and backup behaviour before adding sync | Open |
| R-05 | Stale, duplicated or low-confidence road observations could mislead a driver | Medium | High | Preserve provenance, observation time, freshness, spatial precision and confidence rationale; distinguish mapped observations from live traffic; show source outages and uncertainty; keep driving UI glanceable | Open |
| R-06 | Public geocoding/routing/weather services may rate-limit, change or become unavailable | Medium | Medium | Treat external services as replaceable adapters; avoid high-frequency polling; handle timeout/unavailable states; do not present route estimates as live-traffic predictions | Open |
| R-07 | GitHub-hosted CI may be unavailable because runner allocation/usage is blocked, leaving new source changes uncompiled and untested | High | High | Do not merge or call increments verified without Rust tests, Android unit tests and APK assembly; use a permitted local or self-hosted runner when available; record exact unexecuted checks | Open — current blocker |
| R-08 | A debug APK or a newly generated signing key could fail to update the installed production app and may appear to lose user data | Medium | High | Keep debug application ID separate; require the persistent release keystore and signature verification for install-over-existing updates; never generate a replacement key casually | Open — release signing not confirmed |

## Release gates

- No financial or import-integrity requirement is marked complete until its focused tests execute successfully.
- No provider-specific adapter is advertised until a representative user-owned export is validated and source access/licensing is documented.
- No road-intelligence source is considered trustworthy without provenance, freshness, failure handling and explicit unavailable states.
- No production update is distributed without verifying package identity and signing-certificate continuity.
- Personal Driver Mirror data remains private by default; any future sync or sharing requires an explicit product decision and consent flow.
