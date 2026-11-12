# Phase Log

## 2026-11-05 — Phase 1: product and architecture lock

**Status:** merged to `main`  
**Merge commit:** `70138166aabdc5143cbe1facbea22c9c705ec9d4`

Completed:
- Locked the provider-neutral, multi-source aggregator direction.
- Recorded the product charter, system architecture, source register, ADRs and phased roadmap.
- Established Rust as the deterministic domain core and Kotlin as the Android platform/UI layer.
- Defined provenance, freshness, licence, privacy, evidence-fusion and source-health requirements.
- Corrected three ShellCheck findings in the existing import workflow.
- Added Rust workspace tests and Android unit-test execution to the importer before APK assembly.
- Confirmed the existing `incoming/foundation.zip` is a 1-byte placeholder and must not be imported.

Verification boundary:
- The original importer runs actionlint before archive validation, repository creation or any push. The three reported ShellCheck findings were corrected in the committed workflow.
- The importer has not yet been re-run against a real project archive. Its first valid archive import is the final CI verification gate.
- The connected GitHub integration can edit BusyBee, but repository creation is delegated to the workflow's `REPO_CREATE_TOKEN` secret. Secret presence cannot be inspected through the repository contents API.

## Phase 2 — executable foundation

**Status:** queued

Exit criteria:
- A complete source snapshot is imported into a dedicated private `driver-command-centre` repository.
- Rust workspace tests pass.
- Android unit tests pass.
- CI produces an APK.
- The app launches on a real Android device and renders the pilot map.


## 2026-11-05 — Phase 2: executable foundation (implementation checkpoint)

**Status:** source merged to main; CI validation passed; device and release-signing gates pending.

Implemented:
- Kotlin Android app with MapLibre map surface and OpenStreetMap attribution.
- Kraków pilot camera, map gestures, recenter and pitch toggle.
- Rust road-event domain crate with freshness/TTL tests.
- JNI bridge called from the Android app.
- Stable release application ID; debug uses a separate suffix.
- Signed release workflow with persistent keystore requirement, signature verification and downloadable artifact.
- Debug APK fallback for build validation when signing secrets are not yet configured.

Signing dependency:
- The four persistent Android signing secrets are documented in docs/ANDROID_SIGNING.md.
- The same keystore is required for every future update. No random CI signing key is generated.

Scope boundary:
- This is the executable map/core foundation only. Multi-source connectors and live road intelligence begin in Phase 3.


### CI verification — 2026-11-05

- Workflow run: 42107024805 (success).
- actionlint: passed for all repository workflows.
- Rust workspace tests: 4 passed.
- Rust native libraries: built for arm64-v8a, armeabi-v7a and x86_64.
- Android unit tests: passed.
- Debug APK: assembled and uploaded as artifact BusyBee-Driver-Command-Centre-debug-9 (19,710,964 bytes; expires 2026-11-12).
- Release APK: not built because persistent Android signing secrets were absent at build time.
- Real-device install/launch: not yet verified.


## 2026-11-05 — Phase 3: Driver Data Mirror (first vertical slice)

**Status:** implementation committed; CI and real-device verification pending; provider-specific import adapters not yet validated.

Implemented:
- Replaced the irrelevant nearby-amenity map screen with the Driver Data Mirror as the app's primary experience.
- Added a local SQLite source ledger and canonical records for platform, record type, external ID, timestamp, route, distance, gross, fees, bonus, currency and status.
- Added a provider-neutral CSV import path through Android's system file picker.
- Preserved every imported raw row in a separate evidence table, including duplicates; canonical fingerprints prevent duplicate financial/activity records from being counted twice.
- Added import history, recent cross-platform activity, source-row evidence preview and initial financial rollups.
- Missing amounts remain unknown. Headline totals are PLN-only; foreign currencies are excluded rather than incorrectly converted. Known net remains incomplete unless gross, fees and bonuses are all present.
- Added parser tests for quoted fields, escaped quotes, missing values, required headers and comma decimals.
- Removed unused location permissions and the unused map renderer dependency from this milestone.

Boundaries:
- This is a generic import contract, not a direct Uber/Bolt integration. Real provider statement formats must be validated against user-owned exports before adapter claims are made.
- No account linking, scraping, notification listener, network upload or background collection is implemented.
- Vehicle costs, statement reconciliation, temporal/zone analytics and road-intelligence fusion remain future work.

Verification:
- Source and tests are committed to `main`.
- Automated build status and device install/launch have not yet been confirmed in this checkpoint.


## 2026-11-05 — Driver Command Centre product build

**Status:** route-planning build at commit 39bf6fadd6ddcf7443295377da75809a753bf01f passed Android unit tests and APK assembly; recurring-payment and source coverage additions are now in the next checkpoint.

Delivered:
- Five product surfaces: spatial map, offer command, earnings mirror, driver hub and source network.
- MapLibre map with tilted camera, OpenStreetMap attribution, Overpass mapped roadwork/hazard observations, Open-Meteo current weather, private driver reports and destination route planning using Nominatim + OSRM, with external turn-by-turn handoff.
- Offer Desk with opt-in Android notification listener, manual structured offer entry, vehicle cost per km, pickup-distance, minimum net/hour and passenger-rating rules; ranking is deterministic by estimated net hourly value.
- Driver Hub with local document references, reminders, recurring insurance/payment schedules, completion history, WhatsApp TXT export import/search and trusted service contacts.
- Source catalogue explicitly separates live integrations from credential/partner-dependent providers.
- Unit tests cover offer economics and filtering.

Boundaries:
- Platform notifications may not contain all trip fields; missing details require driver review. No private app database access, unsupported partner API claims or automatic offer acceptance.
- Route planning uses public low-volume OSM geocoding/routing services; turn-by-turn guidance is handed to an installed navigation app.
- Current map event data is mapped road observations, not a real-time traffic-speed service.
- Personal data stays on-device; chat exports and notification capture are opt-in.


## 2026-11-05 — Spatial map upgrade

The map renderer moved from a flat OSM raster layer to OpenFreeMap Liberty vector styling with 3D building extrusions and a tilted neighbourhood camera. MapLibre's official Android example documents Liberty's building extrusion support. The app still labels roadwork/hazard data as mapped observations, not live traffic speeds.

## 2026-11-06 — Driver economics and distribution checkpoint

**Status:** implementation committed; workflow run 42 succeeded end-to-end. Rust tests, Android unit tests, debug APK assembly and publication as a private-repository GitHub Release asset all passed. The earlier Actions artifact upload failure was resolved by moving APK distribution to GitHub Releases.

Delivered in this checkpoint:
- Added deterministic driver analytics grouped by platform and recorded trip start hour.
- Estimated net after vehicle cost only when gross, fees, bonus and distance are all present; incomplete rows remain excluded and surfaced as data gaps.
- Added per-platform economics, source coverage, import history and original evidence previews to the Driver Mirror screen.
- Documented analytics semantics and explicit limitations: no effective hourly wage, weekday/zone analysis or dead-mileage claim without duration and empty-distance source data.
- Verified Kraków MSIP open-data access options, ZDMK's human-readable roadworks register and the GDDKiA DATEX / Geoportal access boundaries in the source register.
- Replaced Actions artifact distribution with a versioned prerelease asset to avoid the exhausted artifact quota.

Verification:
- Workflow run 41: actionlint passed, Rust tests passed, Android unit tests passed, debug APK assembly passed; Actions artifact upload failed solely because storage quota was exhausted.
- Workflow run 42 (success): actionlint passed, Rust tests passed, Android unit tests passed, debug APK assembled and published at https://github.com/ysfclc/BusyBee/releases/tag/build-42.
- Persistent release-signing secrets were not present in run 42; the published APK is debug-only (`.debug` application ID) and cannot update the signed production package. Configure the four secrets in docs/ANDROID_SIGNING.md to publish an install-over-existing signed APK.

## 2026-11-06 — Route-choice improvement

Implemented route alternatives in the map: the route planner requests up to three OSRM candidates, ranks fastest first with distance as a tie-breaker, removes duplicate metric pairs, and lets the driver select an alternative in-app. The selected route remains explicitly labelled as an OSRM estimate, not live-traffic guidance. Added unit tests for ordering, deduplication and candidate limits.

Workflow run 46 succeeded end-to-end: actionlint, Rust tests, Android unit tests, debug APK assembly and GitHub Release asset publication all passed. Download: https://github.com/ysfclc/BusyBee/releases/tag/build-46. The APK is debug-only because persistent release-signing secrets are still not configured.


## 2026-11-12 — Trip-only headline earnings

**Status:** implementation and focused tests committed to `fix/trip-only-earnings-2026-11-12`; GitHub Actions execution blocked before runner assignment; PR #3 remains open.

Delivered:
- Corrected the Earnings headline to include only recognised PLN trip records (`trip`, `ride`, `completed_trip`); offers and other activity no longer inflate earnings.
- Kept gross, fees and bonuses as partial sums of known trip values, but require every recognised PLN trip to have gross, fees and bonus before showing `KNOWN NET`.
- Added focused unit tests for mixed trip/offer data, incomplete offers, missing trip amounts and non-PLN-only data.
- Established the missing state ledger, coverage matrix and changelog; corrected the stale README checkpoint that contradicted the current five-surface Android app.

Verification:
- Pull request: https://github.com/ysfclc/BusyBee/pull/3 (open; not merged).
- Workflow run 47 (43146041937), run 48 initial attempt (43146277412 attempt 1), and a manual retry of run 48 (attempt 2) all terminated within seconds before a runner was assigned. The retry was accepted and queued, then failed without creating any steps; job 131208270290 has an empty steps list. The job records report `runner_id: 0`, an empty steps list and no downloadable logs. These are CI infrastructure failures, not test results; no Kotlin/Rust step is shown as executed.
- Run 46 is the previous successful baseline (Rust tests, Android unit tests, debug APK assembly and Release publication). It does not verify this change.
- No local build is claimed because a repository checkout/Android SDK/Gradle project was not mounted in this session. R-DM-005 remains unverified until a runner executes the workflow.


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


## 2026-11-12 — Driver Mirror CSV integrity hardening

**Status:** implementation and focused parser regressions committed on `feature/mirror-store-integrity-tests-2026-11-12`; test execution pending.

Delivered:
- Reject duplicate and empty normalised headers instead of allowing duplicate keys to overwrite each other.
- Reject extra row cells instead of silently dropping source values; omitted trailing optional cells remain explicit blank/unknown values.
- Reject data rows with blank required platform or record type.
- Use locale-independent casing for CSV headers, imported record types, platform fingerprinting and currency normalisation.
- Added parser regression tests for duplicate headers, extra values, missing required values, omitted optional cells and Turkish-locale header casing; added a Turkish-locale trip-type regression to the headline calculator tests.

Verification boundary:
- Changes and focused tests are committed to the feature branch.
- No Gradle or APK build was executed. GitHub-hosted runners remain unavailable and no local Android SDK/project checkout is mounted.
- Required check remains `cd android && ./gradlew --no-daemon testDebugUnitTest`; do not mark the import contract verified until it passes.


### Latest CI retry — 2026-11-12

- Latest PR #4 head at the time of this check: `94bf00205329ff33263f198ebc8d8f257453af01`.
- Workflow run: `43283672002` — failed.
- Job: `131411281742` — completed with failure and an empty step list. No runner was assigned and no validation command ran.
- This confirms the same pre-execution hosted-runner blocker on the updated CSV parser/tests. It does not indicate a Kotlin source or test failure.
- The current container has a JDK and Kotlin compiler but no `/testbed` checkout, Gradle executable, Android SDK or Cargo toolchain; local project verification is not possible here.


- Follow-up documentation-head run: `43283810594` (head `abeb5d4dc8dde2b30319eceaab45411c6c259a31`) also failed before runner allocation; job `131411762866` returned an empty step list. No build/test command executed.


## 2026-11-12 — Vercel-hosted Android build pipeline

**Status:** build pipeline and dedicated Vercel project configured; first build/deployment pending.

Delivered:
- Created a separate Vercel Hobby project `busybee-apk`, linked to `ysfclc/BusyBee`. Team SSO protection is enabled for non-custom deployment domains.
- Added `vercel.json`, a custom Android/Rust build script and a static APK download page.
- The script installs Java 17, Android command-line tools revision 11076708, platform 35, build-tools 35.0.0, NDK 27.2.12479018, Rust Android targets, cargo-ndk and Gradle 8.9.
- The script runs Rust workspace tests, builds Rust JNI libraries for arm64-v8a / armeabi-v7a / x86_64, runs Android unit tests, and builds a debug APK unless all four persistent release-signing variables are present.
- Added build metadata (commit, branch, package, version, timestamp, APK size and SHA-256) and a stable `/BusyBee.apk` artifact path.
- Android versionCode now accepts `BUSYBEE_BUILD_NUMBER`; the Vercel build uses a UTC epoch-second value when no CI run number exists.

Verification boundary:
- PR #5: https://github.com/ysfclc/BusyBee/pull/5 (draft; based on PR #4).
- Vercel project is linked and build settings are configured. No completed build, passing test, or APK artifact is claimed until deployment logs and output files are inspected.
- First preview attempt (commit `d06532a`) successfully installed Temurin Java 17, then stopped at the Android command-line-tools download because Bash parsed `$CMDLINE_TOOLS_REVISION_latest` as a different variable. Corrected the URL interpolation in commit `d6aebd9`; the corrected preview deployment is queued.
- The first build may still be constrained by Vercel Hobby build duration, memory, disk or download limits. Inspect exact logs before choosing a different no-cost builder.
- Debug package ID is `com.ysfclc.busybee.drivercommandcentre.debug`; it cannot update the release package. Release builds require the same persistent keystore used for all prior releases.
