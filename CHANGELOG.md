## 2026-11-13 — Map navigation crash fix and first BusyBee brand

- Keep the MapLibre map screen attached to the Activity's screen host while other tabs are displayed; tab switches and the 3D toggle no longer detach/reparent the native map view.
- Add a reusable tab screen host and Robolectric regression tests for repeated tab switching.
- Add an original geometric bee-and-road adaptive Android launcher icon, BusyBee launcher label and a documented navy/mint/amber brand palette.
- Verification on Vercel Sandbox at commit `7095489fa3810082c91a81a274d0bd1784e0d389`: Rust workspace tests 4/4; Android unit tests 33/33 (0 failures/errors/skips); APK assembly and debug signature v2 verification passed. Icon-enabled APK: 48,879,532 bytes, SHA-256 `41c3ab575a72ef3d3279aa6f7bebbb89fc41f46190f96b9680d04922400fdf43`. APK manifest reports launcher label `BusyBee` and the custom adaptive icon. Real-device click validation remains required.

## 2026-11-12 — Vercel Android APK build pipeline

- Added a dedicated Vercel Hobby project (`busybee-apk`) linked to the BusyBee repository, without changing other Vercel projects.
- Added a Vercel build script that installs Java 17, Android SDK 35 / NDK 27.2.12479018, Rust Android targets, cargo-ndk and Gradle 8.9.
- The pipeline runs Rust workspace tests and Android unit tests before building native Rust libraries and assembling the APK.
- Added a protected static APK download page and build metadata with source commit, package, version, timestamp, APK size and SHA-256.
- Debug APKs remain separate from release package identity. Persistent release signing is supported only when all four keystore variables are configured; partial signing configuration fails closed.
- Verified a Vercel Sandbox build: Rust tests 4/4 and Android unit tests 31/31 passed; debug APK assembly and signature verification passed. Artifact: 48,877,022 bytes, SHA-256 `77f74c552077ce5e17b06f3bb829e90c4de2b42426ee8eaa7ca3ebfb46d66f06`.
- Standard static deployment is still pending because the team's Vercel deployment API daily cap (>100 creations/day) was reached; a temporary Sandbox route served the APK. No stable project download URL is claimed yet.

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
