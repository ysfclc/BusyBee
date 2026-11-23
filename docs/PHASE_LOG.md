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


## 2026-11-22 — Native Provider Bridge and unified activity migration (implementation checkpoint)

**Status:** implementation committed on `feature/native-provider-bridge-slice-1-2026-11-22`; PR #29 remains open and unmerged.

Delivered:
- Added a provider-neutral Kotlin bridge contract, adapter registry, capability/health types, typed event payloads and evidence envelope.
- Added idempotent event-ingestion validation. Delayed/out-of-order evidence is retained; offer expiry requires explicit evidence.
- Added a local model inventory and app-private model storage catalogue. Discovery never makes a model inference-eligible without compatible runtime/capability metadata.
- Added the first read-only migration from `busybee_driver_mirror.db` and `busybee_command_centre.db` into `busybee_driver_activity.db`.
- Migration copies import metadata, every raw CSV evidence row, normalized CSV observations, saved offers and captured notifications. It leaves unrelated driver hub/map tables in place.
- Each source row and migration marker are committed together. Stable source identity keys prevent duplicate copies on restart. Source databases are opened read-only and never rewritten.
- Missing/ambiguous source timestamps remain unknown; ISO-8601 timestamps are parsed only when explicit. Imported notifications remain unclassified observations and do not become invented offers/trips/expiry events.
- Added Robolectric database fixtures for missing databases/tables, duplicate evidence retention, timestamp handling, notification provenance and idempotent reruns.
- Added CI APK artifact upload so pull-request builds can provide a retrievable validation APK rather than only building it on the runner.

Verification:
- CI run 449 passed actionlint, Rust domain tests, Android unit tests (including the initial Robolectric migration fixtures) and debug APK assembly.
- CI run 451 passed actionlint, Rust domain tests, Android Rust-library builds, Android unit tests (including digest, duplicate-row, timestamp and missing-table migration fixtures), and debug APK assembly.
- CI run 452 was blocked before test execution because Maven Central returned HTTP 429 for a Robolectric transitive dependency. Kotlin compilation had completed; the test runtime dependency could not be downloaded.
- Added a Google-hosted Maven Central mirror. CI run 453 then passed actionlint, Rust domain tests, Android native-library builds, all Android unit tests, debug APK assembly, Android APK signature verification with `apksigner`, and artifact upload.
- Run 453 uploaded artifact `busybee-debug-apk-453` (artifact ID 14766792681; expires 2026-12-06). Extracted APK size: 48,926,162 bytes; SHA-256: `b6fe30282bd7acfba01885446d9d065c5728201508364095e9be171cefd93efb`.
- Persistent Android release-signing secrets are absent, so release-signing steps were skipped. The verified debug APK uses a separate `.debug` application ID and cannot update the release package. The pull-request build did not publish a GitHub Release asset.
- No migration is invoked automatically yet; existing screens still use the legacy stores. The migration must pass on-device parity checks before the UI is switched.
- PR: https://github.com/ysfclc/BusyBee/pull/29


## 2026-11-22 — Persistent provider events and live ledger synchronization

**Status:** implementation committed in the next slice; CI verification pending.

Delivered:
- Upgraded the unified ledger schema additively to version 2 with a dedicated `evidence_digest` field for canonical provider events. Existing v1 ledgers upgrade without dropping data.
- Added `SqliteProviderEventStore`, persisting the complete event envelope and typed payload JSON. The database uniqueness constraint decides duplicate races; duplicate inserts are returned as duplicates.
- Updated `ProviderEventStore.append` to report whether the insert succeeded, closing the race between a prior idempotency read and concurrent inserts.
- Added `UnifiedActivityRepository`, merging raw observations and canonical provider events into a single timeline query while keeping unknown source time null.
- Wired legacy-store migration/synchronization to app startup, CSV imports, manual offer creation/status updates and new notification captures. All work runs off the UI thread.
- Subsequent syncs refresh the current status of already-migrated offers without duplicating their evidence or observation.
- Added Robolectric coverage for persistent idempotency across store instances, v1-to-v2 schema upgrade, merged timeline ordering, unknown source time and offer status refresh.

Verification:
- CI run #456 compiled the new code and executed 57 Android tests; 56 passed. One Robolectric test exposed an SDK runtime incompatibility with treating the SQLiteOpenHelper as AutoCloseable. The test was corrected to close the helper explicitly with try/finally.
- The migration now preloads existing row markers once per source table instead of issuing a target database query for every source row; unchanged offer statuses are not rewritten.
- CI run #458 passed actionlint, Rust domain tests, Android native-library builds, all Android unit tests, debug APK assembly, debug APK signature verification with `apksigner`, and artifact upload.
- Run 458 uploaded `busybee-debug-apk-458` (artifact ID 14767985689; expires 2026-12-06). Extracted APK size: 48,926,162 bytes; SHA-256: `f7d7c8476b4b83c76183a4ffe0c8347a40f2250992bd814a62667c7dcb6804a0`.
- No screen has been switched to the new repository yet. On-device parity and rollback verification remain mandatory before that change.


## 2026-11-22 — First unified Activity timeline surface

**Status:** implementation committed on the feature branch; CI verification pending.

Delivered:
- Added an ACTIVITY destination to the existing navigation without removing MAP, OFFERS, EARNINGS, DRIVER HUB or SOURCES.
- The screen reads from `UnifiedActivityRepository` off the UI thread and merges imported observations with canonical provider events.
- Added All / Observations / Events filters and provenance-first cards.
- Cards distinguish source event time from local receive time. Unknown provider time is explicitly labelled unknown rather than inferred from capture time.
- Observation cards show source method, classification, current status, route, amount/currency, distance/duration and evidence digest. Canonical event cards show their bounded typed payload preview.
- Existing Driver Mirror and Offer Desk screens remain on their legacy data sources pending on-device parity; the new Activity surface does not silently replace them.

Verification:
- CI run #461 passed actionlint, Rust domain tests, Android native-library builds, Android unit tests, debug APK assembly, signature verification and artifact upload.
- Device-level visual/layout testing on a narrow Android screen remains required.


## 2026-11-22 — Activity evidence inspection and provider filters

**Status:** implemented on the existing Native Provider Bridge feature branch; CI run #463 passed.

Delivered:
- Added provider-specific Activity filters generated from the actual providers present in the local ledger, including a visible unknown-provider group.
- Provider filters compose with All / Observations / Events filters and use case-insensitive matching.
- Added an evidence-inspection dialog for each Activity item, exposing record identity, provider/source provenance, source and receive timestamps, lifecycle, evidence row/reference/digest, structured fields and the full stored canonical payload where available.
- Added deterministic JVM tests for provider-list normalization, unknown providers, case-insensitive matching and combined filters.

Verification:
- CI run #463: https://github.com/ysfclc/BusyBee/actions/runs/45063014977 — actionlint, Rust domain tests, Android native-library builds, Android unit tests, debug APK assembly, APK v2 signature verification and artifact upload all passed.
- Artifact: `busybee-debug-apk-463` (ID `14770805750`), 48,942,546-byte APK.
- APK SHA-256: `a777850852b36cbd66b2ac70ee54a7b5ad7f9e59481a7122bbfccbcc81acb934`.
- Package/version: `com.ysfclc.busybee.drivercommandcentre.debug`, `0.1.463-debug`.
- Debug signing is CI validation only; persistent release-signing secrets remain unavailable. This artifact cannot update the release application ID.
- Physical-device validation and legacy-screen parity/rollback checks remain outstanding.


## 2026-11-22 — On-device migration parity diagnostics

**Status:** implemented on the Native Provider Bridge feature branch; CI verification pending.

Delivered:
- Sources screen includes a local migration status panel and manual re-check action.
- The panel reports missing legacy databases, per-table source counts, copied/already-present rows and identity-digest verification.
- A share action exports the diagnostic report as plain text, allowing device validation without logcat or screenshots.
- Migration exceptions are shown in the panel; the old databases remain read-only and are not modified or deleted.
- Added formatter tests for not-run, complete and incomplete reports.

Acceptance boundary:
- A complete migration report verifies source-row identity/count parity for the migrated tables. It does not prove every legacy screen calculation is visually or semantically identical.
- Physical-device validation on a pre-existing installation and rollback testing are still required before migrating legacy screen reads.


## 2026-11-23 — User-curated offer events enter the Provider Bridge

**Status:** implementation committed on the Native Provider Bridge branch; CI verification pending.

Delivered:
- Manual structured offers and offers explicitly reviewed from a captured platform notification now emit typed `OFFER_OBSERVED` provider events.
- Provenance distinguishes manual user entry from notification-backed user capture; neither is labelled an official provider API.
- The app's local capture timestamp remains the receive timestamp. Source event time stays unknown unless the source supplies it.
- The BusyBee **SKIP** action emits a separate `OFFER_DECISION_OBSERVED` event named `skipped_by_driver_in_busybee`; it is not confused with a provider-side acceptance or rejection.
- Events are persisted through the existing SQLite idempotency store. Existing command-centre rows, imported records and raw evidence are not rewritten or deleted.
- Added focused JVM tests for event mapping, source provenance, stable idempotency, evidence digest, explicit decision time and invalid unsaved offers.

Verification:
- CI run #471: https://github.com/ysfclc/BusyBee/actions/runs/45128390056 — actionlint, Rust domain tests, Android native-library builds, all Android unit tests, debug APK assembly, debug APK signature verification with apksigner and artifact upload all passed.
- Artifact: busybee-debug-apk-471 (ID 14796742452), 19,874,453-byte ZIP; artifact ZIP SHA-256: c8558dc16d7b3401472d681453b691431fd03f206a42427e250337f5442a0fd4; expires 2026-12-07.
- Debug package: com.ysfclc.busybee.drivercommandcentre.debug, version 0.1.471-debug. This is a validation build and cannot update the installed release package.
- Release signing was not exercised in this run. The workflow received empty values for all four expected signing inputs (ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD), so release keystore preparation, release assembly and release-certificate verification were skipped. The debug APK's Android Debug certificate is not the persistent release certificate.
- Physical-device migration parity and rollback acceptance remain outstanding.

Verification boundary:
- Raw notifications remain observations until reviewed; CSV imports remain observations pending a source-specific mapping contract.
- No direct Uber/Bolt API connection, auto-acceptance or provider-side action is claimed.


## 2026-11-23 — End-to-end user-curated offer event verification

**Status:** implemented and verified on the Native Provider Bridge branch.

Delivered:
- Added a Robolectric end-to-end test that saves a structured offer in the existing Command Centre store, emits its typed OFFER_OBSERVED event, emits a separate BusyBee-local skip decision and persists both through the SQLite Provider Bridge store.
- The test runs the read-only legacy-offer migration and verifies that the Activity repository shows both the original offer observation and the two canonical events.
- It verifies the full offer payload (fare, bonus, distance, duration, passenger rating), unknown provider event time for the user-curated offer, explicit local decision time, stable idempotency after reopening the store, and preservation of the original legacy offer row/status/evidence.
- Corrected the Activity empty-state wording so it no longer says canonical events can only come from provider adapters; manual or notification-reviewed offers are also supported event producers.

Verification:
- CI run #474: https://github.com/ysfclc/BusyBee/actions/runs/45129506262 — actionlint, Rust domain tests, Android native-library builds, all Android unit tests, debug APK assembly, debug APK signature verification and artifact upload passed.
- Artifact: busybee-debug-apk-474 (ID 14796953779), 19,874,624-byte ZIP; artifact ZIP SHA-256 e1d0f127f83e8e28942b119e0ac0ca8978056b1aed1debf082b3e8a1296bae10; expires 2026-12-07.
- Package/version: com.ysfclc.busybee.drivercommandcentre.debug, 0.1.474-debug.
- Release keystore preparation and signed APK verification were skipped because the four expected release-signing secret values were empty in the workflow. The certificate verified in this run is the Android Debug certificate, not the persistent release certificate.
- Physical-device migration parity and rollback acceptance remain open.


## 2026-11-23 — Per-provider local source coverage in Sources

**Status:** implemented and verified on the Native Provider Bridge branch.

Delivered:
- Added a provider-coverage panel to Sources for Uber, Bolt, FreeNow, iTaxi, Cabify, Lyft and any additional provider names present in local data.
- The panel reports notification-permission state, visible captured-notification count, manually entered offers, notification-reviewed offers, other saved offers, imported-history record count and local capture recency.
- It exposes source gaps explicitly: permission not granted, no visible notices, no structured offer, no imported history and no verified direct provider API connection.
- The notification source row now changes its label based on the real Android notification-listener permission and explicitly calls this a partial user-visible signal, not a provider API.
- All provider summaries are calculated from local records on a background worker. Returning from Android Settings refreshes the coverage panel.
- Unknown provider names are preserved rather than discarded.
- Added unit tests for Uber/Bolt aliases, local input counts, manual vs notification-reviewed offers, imported history, unknown providers, permission gaps and the explicit unverified-API state.
- This is local evidence coverage, not a capability handshake with Uber/Bolt. The panel does not label a provider connected or verified.

Verification:
- CI run #476: https://github.com/ysfclc/BusyBee/actions/runs/45130369253 — actionlint, Rust domain tests, Android native-library builds, all Android unit tests, debug APK assembly, debug APK signature verification and artifact upload passed.
- Artifact: busybee-debug-apk-476 (ID 14797357509), 19,884,900-byte ZIP; artifact ZIP SHA-256 996052dffdf46e3187d533a4d39c4a5eeb95f5895208fde1b5ab12515c9359ac; expires 2026-12-07.
- Package/version: com.ysfclc.busybee.drivercommandcentre.debug, 0.1.476-debug.
- The release keystore and release-signature steps were skipped. The workflow again received empty values for the four expected release-signing secrets. The certificate verified here is the Android Debug certificate (SHA-256 eae426830bd5011c49c81ab8b7868f46fef37564f9807163a215091076bdd06d), not the persistent release certificate.
- Physical-device migration parity and rollback acceptance remain outstanding.
