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

## 2026-11-15 — Uber/Bolt provider activity integration stress test and design baseline

**Status:** research/design recorded on `docs/provider-activity-integration-design`; no production integration or app code changed.

User requirement clarified:
- BusyBee should investigate observing ride-hailing activity inside the provider app process at the plaintext boundary: outbound request data before TLS encryption and inbound response data after TLS decryption.
- This is materially different from VPN packet capture, notification access, screen capture, OCR or Android usage statistics.
- Desired downstream capability is a unified, provider-neutral offer/trip activity stream feeding the Driver Data Mirror and Offer Desk.

Repository baseline confirmed:
- Phase 3 already contains a local-first Driver Data Mirror, CSV evidence ledger, notification/manual Offer Desk inputs and source-provenance direction.
- Existing app/repo documentation does not claim a direct Uber/Bolt API or in-process integration.
- No existing implementation was changed during this design pass.

Technical findings:
- In-process plaintext observation is technically plausible in controlled/authorised instrumentation environments.
- Frida Server generally relies on privileged/root access for injection into an unmodified target process; Frida Gadget is loaded by the target app and the Gadget route requires a modified/re-signed target APK.
- Re-signing a third-party app can invalidate its original signature and may break integrity/attestation, updates, pinning, login or other functionality. Current Uber/Bolt compatibility is unverified.
- A BusyBee APK cannot simply inject Gadget into untouched Uber/Bolt processes on stock Android.
- VPN packet observation does not ordinarily reveal HTTPS plaintext.
- Android notifications and user-visible capture are separate, limited adapters, not substitutes for the requested process-level observation.
- No evidence currently establishes that current production Uber/Bolt builds can be instrumented reliably, permissibly and without compromising their operation on the driver's daily phone.

Decisions:
- Preserve plaintext-boundary observation as an explicit research requirement; do not silently downgrade the goal to OCR or VPN metadata.
- First validate outbound and inbound plaintext observation in an owned Android test app and local test endpoint.
- Then assess documented/provider-permitted Uber and Bolt input paths independently.
- Keep production adapters source-neutral and decouple the domain model from Frida, TLS internals, provider networking libraries and UI structure.
- Separate observation from action. Initial flow is observe → normalise → explain → hand back to the official provider app. No request replay or unsupported auto-accept.
- Do not make root, APK repackaging, bypassing anti-tamper/authentication/pinning, or modified provider apps a production dependency.
- Require provenance, event/receive timestamps, field completeness, redaction, stale-event handling, deduplication, conflict visibility and explicit source-health states.

Next design/engineering sequence:
1. Design the owned Android plaintext-observation test harness and synthetic offer lifecycle.
2. Define and test the provider-neutral adapter/evidence envelope against the current Rust/SQLite domain.
3. Complete separate Uber and Bolt access/terms/capability assessments.
4. Validate real user-owned export samples and reconciliation.
5. Only then decide whether any provider-specific live adapter is supportable.

Detailed record: [Platform Activity Integration — Research and Design](PLATFORM_ACTIVITY_INTEGRATION.md). The next experiment is specified in [Plaintext Observer Proof of Concept — Design](PLAINTEXT_OBSERVER_POC.md): owned Android app, local HTTPS fixture, outbound/inbound observation, synthetic lifecycle, redaction and instrumentation-health tests.

Verification boundary:
- This commit is documentation/architecture only. No provider APK was modified, no third-party app was instrumented, no device root or access-control bypass was attempted, and no BusyBee APK was built for this change.
- The owned test harness is the next implementation task; no plaintext-capture acceptance test has passed yet.


## 2026-11-15 — Native Provider Bridge product design

**Status:** design baseline committed to branch `design/native-provider-bridge`; implementation not started.

User outcome clarified:
- The driver wants to see their Uber/Bolt driver-app data inside BusyBee through a native-feeling, API-like integration.
- This is a product outcome, not a request to make an instrumentation console or expose raw network payloads.
- BusyBee must present one unified view of offers, offer outcomes, active/completed trips, trip details, fare components, payouts and history wherever the source actually exposes those fields.

Repository inspection:
- DriverMirrorStore.kt holds CSV-imported trip/earnings records and raw import evidence in busybee_driver_mirror.db.
- DriverCommandStore.kt holds offers and captured notification records in a separate busybee_command_centre.db.
- PlatformNotificationListener.kt extracts visible notification title/body for recognised ride-platform packages.
- MainActivity.kt has Offers, Earnings and Sources surfaces, but no typed provider adapter registry, capability discovery, unified event stream or verified connection-health model.
- Thus the current app has useful partial inputs, but not a first-class provider integration. Its two data stores are not yet reconciled.

Design decisions:
- Add a first-class internal Provider Bridge API with one typed adapter contract per provider.
- Normalise every input into provider-neutral offer/trip/earnings events; UI and analytics never parse provider-specific notification text, JSON or CSV directly.
- Add capability discovery (offers, offer lifecycle, active/completed trips, route/distance/duration, fare breakdown, payouts, session and history) and explicit states for available, partial, permission-required, approval-required, unsupported, blocked and unknown.
- Connected Apps UX shows source method, last event/sync, capability coverage and health. Detecting an installed app or granting notification access does not equal a verified API connection.
- Driver Mirror becomes the unified timeline and overview. Every event shows provider, source method, event/receive time, freshness and evidence/field completeness.
- Keep provider acquisition separate from canonical API and UI. Official documented access first; user-owned exports for authoritative history; Android-native notification/share signals as partial live inputs; plaintext instrumentation remains a separate feasibility track.
- Do not fabricate fields, call gross profit, replay requests, bypass app protections or claim provider API access without proof.
- Reconcile existing offer/notice and import stores with non-destructive versioned migrations; no third disconnected store.

Next implementation slices:
1. Typed Provider Bridge domain contract, adapter registry, capability and health models.
2. Unified event repository/ledger and non-destructive reconciliation of existing stores.
3. Connected Apps UX and Driver Mirror activity timeline using current notification/import data.
4. Validate provider-specific source access and add only supported live adapters.
5. Reconcile trips and earnings against user-owned statements and build verified driver economics.

Detailed design: [Native Provider Bridge](NATIVE_PROVIDER_BRIDGE.md).

Verification boundary:
- This checkpoint is design only. No Android app code, database schema, provider integration or APK was changed. The next implementation issue tracks the Provider Bridge contract and unified timeline.


## 2026-11-15 — Native Provider Bridge core contract

**Status:** first implementation slice committed on `feat/native-provider-bridge-core`; review/CI pending.

Implemented:
- Added provider-neutral Kotlin domain types for Uber, Bolt and future providers.
- Added capability reports and explicit connection states; a synthetic fixture can only report `PARTIAL`, never a verified provider connection.
- Added typed offer, trip and earnings payloads with provider identity, adapter identity/version, acquisition method, event/receive/expiry times, correlation/external IDs, completeness, evidence retention/redaction and dedupe key.
- Added adapter registry keyed by adapter identity, allowing multiple acquisition methods for one provider.
- Added repository ingestion boundary and in-memory implementation with provider-scoped idempotency and chronological ordering.
- Added deterministic synthetic adapter and unit tests for idempotency across adapter sources, provider-scoped keys, ordering, partial fields, stale/late-expired events, multi-source registration and honest synthetic connection state.

Safety/correctness details:
- A late-arriving event whose expiry is already in the past remains ingestible and is marked stale; expiry is not treated as a reason to erase evidence.
- Missing fields remain null and are represented in completeness metadata.
- No third SQLite database was introduced. Existing DriverMirrorStore and DriverCommandStore schemas/data remain unchanged.
- No Uber/Bolt live adapter or provider access is claimed.

Verification boundary:
- Source and test files are committed to the feature branch. A dedicated Android validation workflow has been added; its execution result is pending.
- This is the domain/API foundation only; existing offers, notices and imported records are not yet routed through the new repository or shown in a unified timeline.


## 2026-11-15 — First unified Driver Mirror projection

**Status:** implemented on the same feature branch; Android validation workflow running.

- Added a read-only projector that adapts existing offers, captured notifications and imported Driver Mirror rows into the canonical ProviderEvent envelope.
- Existing SQLite databases remain the source of truth. The projector performs no schema change, migration, deletion or rewrite; it creates an in-memory unified query from the two existing stores.
- Added a Connected Apps section for Uber Driver and Bolt Driver that distinguishes notification permission and observed/imported record counts from a verified provider API connection.
- Added a unified Driver Mirror activity list with provider, event type, event time, receive time, source method, field completeness and missing-field disclosure.
- Imported currency values are only represented as Money when they are valid three-letter currency codes; malformed/unknown currency does not crash the timeline or get silently converted.
- Non-finite legacy offer values are omitted from typed amounts and marked missing rather than crashing projection.
- Provider-side acceptance/decline actions remain in the official driver apps.

Remaining work:
- Durable cross-source reconciliation and conflict/evidence handling.
- Persisted source capability/health and last-sync tracking.
- Real provider access validation and real-device acceptance.

Verification:
- Dedicated Android validation workflow was added and is executing for this PR. No test/build result is claimed until the run completes.

## 2026-11-15 — Durable Provider Bridge event ledger

**Status:** implemented on `feat/native-provider-bridge-core`; CI verification pending.

Delivered:
- Added a versioned `provider_events` table to the existing `busybee_driver_mirror.db`; no third database was created.
- Added an additive SQLiteOpenHelper migration from schema version 1 to 2. Existing imports, raw evidence and records are neither rewritten nor deleted.
- Added a durable `SqliteProviderEventRepository` implementing the same ProviderEventRepository contract used by the in-memory test repository.
- Added a versioned binary event codec (Base64 storage envelope) covering every typed payload, nullable fields, provider/adapter/source metadata, completeness, evidence and dedupe identity. Unknown format versions and malformed payloads fail rather than being silently accepted.
- Changed the legacy projector to ingest into the durable repository and read the unified timeline from it. Existing source stores remain authoritative; projection is repeatable and idempotent.
- Offer lifecycle dedupe keys now include status, so a pending offer and its later accepted/rejected/expired/cancelled state can coexist as distinct observations rather than the first state hiding later changes.
- Added codec round-trip tests for offer, trip, earnings and observation payloads, plus an unsupported-version test.

Data-safety boundaries:
- The migration is additive and transactional under Android's SQLiteOpenHelper upgrade flow.
- The event ledger stores the canonical normalised event envelope; original import evidence remains in the existing raw_evidence table and notification/offer evidence remains in the command-centre store.
- No existing financial totals or source rows are rewritten, merged or deleted. Durable event persistence is not yet a claim that source records have been fully reconciled or conflict-resolved.
- No provider API or plaintext observation path is implemented or claimed.

Next:
- Verify migration and repository behaviour on Android, including persistence across process restart and preservation of a pre-existing version-1 database.
- Add durable conflict/reconciliation records and persisted adapter health only after the storage acceptance tests pass.
- Continue owned-app plaintext-boundary validation separately from production provider access.

## 2026-11-15 — Owned Android plaintext-observer harness (implementation checkpoint)

**Status:** source implemented on `feat/native-provider-bridge-core`; Android CI and controlled emulator execution pending. No provider app or provider account was touched.

Delivered:
- Added an isolated Android application project under `research/plaintext-observer/android`; it has a separate application ID and does not add dependencies or runtime permissions to BusyBee.
- Added explicit app-owned plaintext boundaries: outbound JSON after serialisation and immediately before HTTPS transport; inbound response body after TLS decryption and before JSON parsing/state handling.
- Added a deterministic local Python HTTPS fixture that returns a synthetic offer-observed → offer-updated → offer-expired lifecycle.
- Added an ephemeral local CA/server-certificate generator. Private keys and certificates are ignored by Git; only the user-installed local CA is used by the test app's dedicated network-security config.
- Added a Frida script that hooks only the two methods in the owned POC package and emits allowlisted fields. Secret/token/cookie/password-like fields are never printed; raw payloads and headers are omitted.
- Added a reproducible runbook covering build, local HTTPS, adb reverse, test CA installation, Frida attachment, expected evidence and cleanup.
- Extended Android validation CI to test and assemble the separate POC app whenever its source changes.

What this demonstrates, once executed:
- The application has plaintext available before handing its request body to TLS transport.
- The application receives plaintext after the TLS stack decrypts the response body.
- The observer can capture those two deliberately exposed app-owned boundaries without packet interception.

What is not yet proven:
- The Gradle build and emulator execution for this new project have not yet completed.
- Frida hook attachment and the full outbound/inbound lifecycle have not yet been observed in a running emulator.
- This is not a generic TLS/OkHttp hook and says nothing about Uber/Bolt internal method names, libraries, protections, terms or compatibility.
- No provider app, credentials, live trip data, request replay or provider-side action is involved.

Next verification:
1. Pass Android CI for both BusyBee and the isolated POC app.
2. Execute the runbook in a controlled emulator and retain redacted console evidence for all six boundary events.
3. Add malformed, duplicate, delayed and out-of-order fixture cases plus instrumentation-overhead comparison.
4. Keep Uber and Bolt access/terms/capability assessments separate; do not move to provider instrumentation absent a documented, permitted path.
 
## 2026-11-16 — Integrated map-first Driver Command Centre candidate

**Status:** integration candidate assembled on `integration/driver-command-centre-2026-11-16`; automated validation and target-device acceptance pending.

- Combined the PR #8 → #9 map-first/native-capture feature lineage with the PR #14 Provider Bridge implementation without changing the original branches or `main`.
- Kept the full-screen map as the default spatial workspace and retained Driver Mirror, capture/review and Driver Operations as equally core workflows.
- Merged the Provider Bridge timeline and Connected Apps partial-source status into the map-first MainActivity.
- Advanced `busybee_driver_mirror.db` from feature-line version 3 to version 4. Existing duration and parse-confidence migrations remain ordered; the provider event table is created on fresh install and added on upgrade. Existing records, imports and raw evidence are not rewritten.
- Added Robolectric migration regression coverage for a fresh install and populated version-1/version-3 databases.
- Added an integration checkpoint with source SHAs, known limitations and separate implementation/build/device acceptance gates.

**Not yet verified:** Android compilation, unit/Robolectric test execution, signed APK assembly, update signature compatibility, installation over the existing phone app, or real driver-app/map workflows. The unified event timeline is not yet a cross-source reconciled financial ledger; do not use it for earnings totals.


## 2026-11-16 — CI verification and provenance correction

- GitHub Actions Android validation run #39 passed Rust native-library compilation, Android unit/Robolectric tests, debug APK assembly, and the owned-app POC test/build step.
- APK workflow #358 passed actionlint, Rust tests, native-library builds, Android tests, debug APK assembly, signature verification and checksum/provenance generation.
- Release signing was skipped because persistent signing secrets were not configured. The generated package is the separate .debug application and cannot update the installed release app.
- The separate POC-only workflow encountered Maven Central HTTP 429 while running concurrently; its tests and assembly passed in the combined Android validation workflow. The redundant standalone workflow is removed from this candidate.
- Corrected legacy event provenance so Accessibility UI and MediaProjection OCR observations retain their actual acquisition method. Notification deduplication now uses the stored notification identity, so separate identical notifications do not collapse. Trip duration is carried into Provider Bridge events and completeness reporting.
- Added pure unit tests for acquisition provenance and stable source identities.

**Still pending:** rerun CI on this correction commit, release signing with the existing key, and target-device acceptance.


## 2026-11-16 — Map icon family implementation

- Replaced Unicode glyphs in map controls and workspace navigation with locally drawn icons and accessible control descriptions.
- Replaced generic route endpoint, road event and private driver report pins with semantic custom marker artwork. Existing custom vehicle/building/entrance markers remain.
- Added pure category-policy tests for roadworks, hazards, closures, weather and unknown categories.
- Kept source honesty: no severity or freshness badges without source evidence. Target-phone visual and interaction acceptance remains pending.
