## 2026-11-14 — Map navigation and real-world detail correction

**Status:** source committed on draft PR #9; Android build, tests and device verification pending.

**Source revision:** `48dce0278c266a196423b5de119c5771dbf73bb6`  
**Branch:** `feat/map-first-route-dock-2026-11-13`  
**PR:** [#9 — compact map dock and purpose-built driver workspaces](https://github.com/ysfclc/BusyBee/pull/9) (draft, unmerged)

Delivered in source:
- Replaced the generic map pin with a custom top-down car marker; foreground GPS moves it.
- Navigation camera follows smoothed GPS heading, targets ahead of the vehicle, and uses zoom 18.2 / 58° pitch in 3D mode. Manual pan suspends follow; recenter resumes it.
- Daytime basemap switched to OpenFreeMap Liberty; dusk/night remain Fiord/Dark. Runtime 3D building extrusion is added to styles that do not already provide it.
- Replaced pure-white daytime UI surfaces with a tinted green/stone palette.
- Fixed local street-name precedence: the default OpenFreeMap style can prefer `name_en` over local OSM `name`; BusyBee now prefers `name:pl` (on Polish locales), then local `name`, then Latin/English fallbacks across the three styles.
- Nominatim destination search prefers Polish names and biases to the driver's area without hard-bounding intercity destinations. OSRM maneuver steps fall back to road references when names are missing.
- Added on-demand Overpass detail lookup for named/addressed building ways and multipolygon relations, plus entrance/door nodes. Building names/addresses and amber entrance/Klatka labels render at zoom-gated levels. A label such as `Klatka A` is shown only when OSM explicitly maps a staircase entrance and its reference.
- Added map context counts with OSM attribution and throttled marker redraws.

Regression tests added for local road-name policy, OSM building/entrance/relation parsing, no fabricated stairwell letters, camera look-ahead, heading wrap smoothing, Polish geocoding bias and road-reference fallback.

**Scope boundary:** this is GPS-driven forward-perspective navigation with 3D building context. It is not a proprietary Waze-like live-traffic engine or photorealistic 3D road mesh. Map labels, building names and stairwell/entrance coverage depend on OSM data quality.

**Verification:** the current PR head has no PR-triggered Actions run. The latest available run #258 (for the preceding source revision) failed before runner/job-step allocation; its retry was cancelled before allocation. No Gradle/test command ran. Vercel reports a build-rate-limit failure. Android compilation, unit-test execution, APK/signature/hash, emulator and Redmi driving checks remain unverified.

Do not merge PR #9 until the source builds, tests run, a signed APK is produced, and the map is verified on the target device.

---

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


## 2026-11-12 — Vercel Sandbox build verification

**Status:** successful Vercel Sandbox build; standard static Vercel deployment still pending.

Build execution:
- Sandbox: `busybee-android-build`, Vercel Hobby, 4 vCPU / 8 GB RAM, Ubuntu 26.04.
- Source commit: `d45aa55491757d4f1155c7d9ce441aa97254187b` on `feat/vercel-apk-builder-2026-11-12`.
- Added native compiler installation when `cc` is missing (`build-essential` on apt images; GCC/make on dnf images).
- Corrected the Robolectric API usage in `DriverMirrorStoreTest`: `RuntimeEnvironment.getApplication()` is not generic in the selected Robolectric version.
- Disabled Kotlin incremental compilation and used the in-process compiler for the Vercel test step to avoid Kotlin daemon cache failures in the sandbox.

Verified results:
- Rust workspace tests: **4 passed, 0 failed**.
- Android unit tests: **31 passed, 0 failed, 0 errors, 0 skipped** across 8 test-result XML files.
- Android debug APK assembly: **BUILD SUCCESSFUL**.
- APK manifest: package `com.ysfclc.busybee.drivercommandcentre.debug`, versionCode `1794515558`, versionName `0.1.1794515558-debug`.
- APK signature: verified with Android `apksigner` (debug key, APK Signature Scheme v2).
- Native Rust library present in APK for `arm64-v8a`, `armeabi-v7a` and `x86_64`.
- Artifact: 48,877,022 bytes; SHA-256 `77f74c552077ce5e17b06f3bb829e90c4de2b42426ee8eaa7ca3ebfb46d66f06`.
- The APK and `build-info.json` were served from the running Vercel Sandbox port route for immediate download. The route is temporary and tied to the sandbox session.

Remaining gates:
- The standard `busybee-apk` static deployment did not complete. The first preview exposed and fixed a shell URL interpolation bug; later deployment creation hit the Vercel team's daily deployment API limit (>100 creations/day). The stable project URL must not be represented as live until a deployment reaches READY and its APK/metadata are checked.
- Debug package identity cannot update an existing release package. Release signing requires the same persistent keystore; no replacement key was generated.
- Real-device installation, launch, upgrade continuity, battery/performance and accessibility checks remain outstanding.


## 2026-11-13 — Native-first product correction

**Branch:** feat/live-navigation-vehicle-2026-11-13

- Added user-consented Android MediaProjection capture with on-device OCR and a manual review queue for Uber, Bolt and user-named additional driver apps.
- Added trip duration to the local Driver Mirror schema and native reviewed-record persistence; missing amounts remain unknown.
- Replaced the foreground WhatsApp chat-export workflow with selected-group discovery and per-group controls. Android notification visibility remains a platform limitation.
- Removed the prominent CSV import action from the normal Earnings path; legacy import history remains accessible as optional backfill evidence.
- Map workspace uses the full available content area with floating controls; a full premium redesign of all workspaces remains unfinished.
- Added tests for native OCR parsing, WhatsApp notification parsing, per-group consent and reviewed-record evidence. These new tests still require a successful run in a build of the latest test commit.

Build evidence: deployment dpl_D6JRWWpjiHuzmE4kaETzneeKP5xq reached READY for code commit 3cc846a85f892e028ae7b1109d3a8c9bda94632f; Android APK assembly and ZIP integrity verification succeeded. Test-only commit 5e8f9dd720bbdbc2c9a27b8bd9828f384d9ce562 is newer than that APK. No physical-device validation or product acceptance is claimed.


### Verification blocker update — 2026-11-13

Latest source checkpoint: a1471cd3d1905064b47a5eb21dfa61005579ff5b. The Vercel build that included the new tests stopped after Gradle started Android unit tests and produced no further logs; it was cancelled rather than reported as passed. The next deployment request was rejected by the Vercel Hobby daily deployment API limit (HTTP 402). GitHub Actions PR run 43507100722 failed before runner assignment (runner_id 0), with no downloadable job logs. Therefore no passing test count or latest-source APK is claimed. The previous READY APK is an earlier code snapshot only.



## 2026-11-13 — Map-first dock and Driver workspace UX increment

**Branch:** `feat/map-first-route-dock-2026-11-13` (stacked on PR #8 head; draft candidate only)

Delivered:
- Reduced persistent map chrome to a compact destination dock over the full-bleed map. Route status and START/STOP/external-navigation controls now appear contextually after route planning or while navigating.
- Preserved the MapLibre view lifetime and the map-first hide-header/hide-tab-bar behaviour from the parent branch.
- Rebuilt Earnings hierarchy around a single known-net focal point, compact KPI row, native capture review inbox, coverage/economics, horizontal platform and start-hour comparisons, and compact recent activity. Kept original calculation semantics and evidence/review flows.
- Rebuilt Offer Desk around native driver-app capture as the primary path, quick vehicle/rule controls, manual correction, ranked offers and clearly secondary notification evidence.
- Reworked Driver Hub into compact admin, payment-history, group-watch and service-contact workflows. Existing item completion/reopen, document opening, selected-group tracking and group viewing controls remain available.
- Updated README product-surface descriptions to reflect native-first capture, optional CSV backfill, selected WhatsApp groups and the remaining Sources redesign gate.
- Kept all user-facing provider/API limitations explicit; no API connection or unsupported data access is claimed.

Verification:
- GitHub source diff inspected; branch is two commits ahead of PR #8 base at the first source checkpoint, with follow-up commits for Earnings and Offers UI.
- Automated Rust/Android tests and APK assembly were **not run**. Vercel Sandbox API returned HTTP 402 (Hobby usage limit exceeded through 2026-12-01); this is an infrastructure blocker, not a passing or failing source test.
- No local Android SDK/Gradle or `/testbed` checkout is available in this runtime. Physical-device verification remains outstanding.
- Sources still needs its own purpose-built premium layout pass. This increment does not satisfy the complete UI redesign acceptance gate.

