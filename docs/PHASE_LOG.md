# Phase Log

## 2026-11-12 — Driver Mirror SQLite persistence integrity

**Status:** persistence regression tests authored on `feature/mirror-store-integrity-tests-2026-11-12`; execution pending because hosted runners cannot be allocated.

Delivered:
- Added Robolectric test support to the Android unit-test configuration.
- Added a store-level test proving repeated rows are deduplicated in canonical records both within a batch and across imports.
- The same test asserts that all raw evidence rows survive, including duplicates, and that import history reports accurate received/inserted counts.
- Added failure injection after one valid row and assertions that the enclosing transaction leaves no import batch, canonical record or evidence row behind.
- Updated the coverage matrix and CSV import contract to describe the new persistence checks without claiming they passed.

Verification boundary:
- Source and documentation were committed to the feature branch.
- No Gradle test or APK build was executed in this session: the repository checkout and Android SDK are not mounted locally, and GitHub Actions attempts fail before runner assignment.
- Run `cd android && ./gradlew --no-daemon testDebugUnitTest` when a runner is available. Do not mark R-DM-001 verified until the Robolectric tests pass.

## 2026-11-12 — Trip-only headline earnings

**Status:** implementation and focused tests committed to `fix/trip-only-earnings-2026-11-12`; GitHub Actions execution blocked before runner assignment; PR #3 remains open.

Delivered:
- Corrected the Earnings headline to include only recognised PLN trip records (`trip`, `ride`, `completed_trip`); offers and other activity no longer inflate earnings.
- Kept gross, fees and bonuses as partial sums of known trip values, but require every recognised PLN trip to have gross, fees and bonus before showing `KNOWN NET`.
- Added focused unit tests for mixed trip/offer data, incomplete offers, missing trip amounts and non-PLN-only data.
- Established the missing state ledger, coverage matrix and changelog; corrected the stale README checkpoint that contradicted the current five-surface Android app.

Verification:
- Pull request: https://github.com/ysfclc/BusyBee/pull/3 (open; not merged).
- Workflow run 47 (43146041937), run 48 initial attempt (43146277412 attempt 1), and a manual retry of run 48 (attempt 2) all terminated within seconds before a runner was assigned. The retry was accepted and queued, then failed without creating any steps; job 131208270290 has an empty steps list. The job records report `runner_id: 0`, an empty steps list and no downloadable logs. These are CI infrastructure failures; no Kotlin/Rust step is shown as executed.
- Run 46 is the previous successful baseline (Rust tests, Android unit tests, debug APK assembly and Release publication). It does not verify this change.
- No local build is claimed because a repository checkout/Android SDK/Gradle project was not mounted in this session. R-DM-005 remains unverified until a runner executes the workflow.
