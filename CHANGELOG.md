# Changelog

## 2026-11-12 — Driver Mirror CSV integrity hardening

- Reject duplicate/empty/unsupported headers, missing required row values and extra cells rather than silently losing imported data.
- Preserve omitted trailing optional cells as unknown values.
- Normalise header, trip-type and currency casing with Locale.ROOT so Turkish device locale cannot corrupt identifiers or trip classification.
- Added regression cases for malformed rows, duplicate headers, omitted optional values and locale-sensitive normalisation. Tests are authored but not executed.

## 2026-11-12 — Driver Mirror persistence integrity

- Added Robolectric-backed SQLite store tests for canonical duplicate suppression across repeated rows and separate import batches.
- Established `docs/RISKS.md` for financial integrity, persistence, provider access, privacy, road-data quality, service availability, CI and release-signing risks.
- Added tests for raw evidence retention and import counts, plus a failure-injection test asserting a mid-batch exception rolls back the import ledger, canonical records and raw evidence together.
- Added Robolectric 4.14.1 and Android unit-test resource support to the app test configuration.
- Tests are authored but not executed: GitHub-hosted Actions still fails before runner assignment, and no local Android SDK/project checkout is mounted. No build or runtime pass is claimed.
- See [coverage](docs/COVERAGE.md), [import contract](docs/DRIVER_MIRROR_IMPORT.md) and [phase log](docs/PHASE_LOG.md).

## 2026-11-12 — Earnings integrity and project continuity

- Corrected headline earnings so only recognised PLN trip rows contribute; offers and other activity records no longer inflate trip gross, fees, bonuses or known net.
- Tightened headline-net completeness: every recognised PLN trip must have explicit gross, fees and bonus values.
- Added unit tests for mixed trip/offer data, incomplete offers, missing trip amounts and non-PLN-only data.
- Added the initial project state ledger and requirement coverage matrix.
- Corrected the README checkpoint to reflect the current Map, Offers, Earnings, Driver Hub and Sources surfaces.
- GitHub Actions runs 47 and 48 ended before runner assignment (no steps executed); the previous green run 46 does not verify this change. See [the phase log](docs/PHASE_LOG.md).
