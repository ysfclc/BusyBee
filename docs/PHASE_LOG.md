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
