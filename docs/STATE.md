# Current engineering checkpoint — 2026-11-13 (map + workspace UX increment)

**Active branch:** `feat/map-first-route-dock-2026-11-13`  
**Base:** PR #8 head `7d34e5bc20e7d50cf3b303784932eeadc8e26ab2`  
**Latest source commit:** `7460136494ed0fa7b11f5011b56d80401ba344ac`  
**Status:** implementation authored; build/test/device verification blocked and pending. PR #9 is draft and stacked on PR #8; neither is merged.

Delivered in this increment:
- Map remains a full-bleed MapLibre canvas with no persistent header or tab bar. Its floating route dock is compact by default: destination search only. Route estimates and navigation controls appear only when needed; START/STOP state follows navigation state.
- Route search/loading feedback is shown in the dock instead of being hidden while the compact layout is active.
- Driver Mirror is reorganised around one financial focal point, compact KPI row, native-capture review inbox, source coverage, horizontal platform/hour comparisons, and compact confirmed-activity rows. Existing calculations, unknown-value handling, source review actions, vehicle-cost assumption, and legacy import history are retained.
- Offer Desk is reorganised around quick vehicle/rule controls and the user-started native driver-app capture path. Decision filters are edited in a dialog rather than occupying the default screen. Notifications are explicitly secondary; manual correction and ranked offer actions remain available.
- Driver Hub now uses compact admin rows, one-tap completion/reopen actions, a distinct WhatsApp group selection area, and compact service contacts instead of a tall stack of separate item cards.
- No new data source, provider API claim, private database access, automatic ride acceptance, or CSV-first workflow was introduced.

Verification boundary:
- GitHub diff inspection confirms the intended map, Earnings and Offers code changes are present on the isolated branch.
- No Rust/Android tests or APK build have been run for this branch. The existing Vercel Sandbox request returned HTTP 402 because the Hobby usage limit is exceeded until 2026-12-01; this was not a source build result.
- This runtime has no `/testbed` checkout, Android SDK or Gradle executable. No physical-device validation is possible here.
- Keep the branch as a draft candidate. Do not merge, distribute an APK, or call the redesign accepted until automated tests/build and target-phone checks pass. Sources still needs its own purpose-built layout pass; full visual acceptance remains unfinished.

---

# Current acceptance correction — 2026-11-13

The user has confirmed that the previous general click/action bugs are gone, but explicitly rejected the delivered app as not matching the agreed product. The old STOP-WORK freeze and earlier capability snapshot below are historical; they are superseded for product direction by [PRODUCT_CONTRACT.md](PRODUCT_CONTRACT.md).

Current code candidate on feat/live-navigation-vehicle-2026-11-13:
- User-started Android MediaProjection bridge with on-device OCR, platform label and a manual review queue. This is the native fallback path, not an approved Uber/Bolt API connection.
- Per-group WhatsApp discovery, explicit tracking choice, message review, stop and delete controls. Visibility is limited to notification content Android exposes.
- Trip duration in the local Driver Mirror schema, preserving unknowns and original evidence.
- Map screen uses the full content canvas with floating controls; full premium UX redesign remains incomplete and not accepted.
- CSV/chat export screens are no longer presented as the normal workflow; legacy CSV evidence remains for backwards compatibility.

**Verification boundary:** Vercel deployment dpl_D6JRWWpjiHuzmE4kaETzneeKP5xq built code commit 3cc846a85f892e028ae7b1109d3a8c9bda94632f and reached READY; its log confirms APK assembly and ZIP integrity. A later Vercel build for commit 4c565b20fb03db84ac2158a4325ad6f2df289e5b reached the Android unit-test stage but stopped producing logs and was cancelled. A new deployment attempt for the latest source was blocked by the Vercel Hobby daily deployment API limit (HTTP 402). GitHub Actions PR run 43507100722 failed before runner assignment (runner_id 0). Latest source at time of this note: a1471cd3d1905064b47a5eb21dfa61005579ff5b. No APK for that latest source is verified, and no target-device test of native capture/group workflows has been completed. Do not mark them accepted until tests run and the APK is exercised on the user's Android device.

---

# State Ledger
_Last updated: 2026-11-13 — GPS routing, vehicle profile and click diagnostics slice_

## 1. Product snapshot

BusyBee is the product brand for Driver Command Centre, an Android-native driver-intelligence product piloting in Kraków. Brand v0.1 now includes an original adaptive Android launcher mark and a documented navy/mint/amber visual system (`docs/BRAND.md`). It combines a private, local-first Driver Data Mirror for cross-platform activity and economics with a provider-neutral road-intelligence and navigation experience. The map is a spatial interface to those capabilities, not the product by itself. Kotlin owns Android UI and platform integration; Rust is the deterministic domain core. Completeness means evidence-backed records, honest source coverage, safe driver-facing UX, and end-to-end tested capabilities—not a populated demo map.

## 2. Status board

| Area | State | Notes |
|---|---|---|
| Product charter / architecture | ✅ baseline | Product direction, source register and ADR-001–008 are committed |
| Android application shell | 🔴 BLOCKED — user reports repeated action failures and IllegalStateException on target device | Previous Robolectric tests do not reproduce the reported device failure. Exact failing action/stack trace not yet supplied; no stability claim. Freeze feature work until runtime blocker is resolved |
| Spatial map / routing | 🔴 UNVERIFIED — user reports incorrect map and routing | Candidate search, OSRM alternatives and foreground GPS guidance exist in source, but physical-device correctness, location accuracy, route selection and guidance have not been validated; OSRM is not live traffic |
| Driver Data Mirror import | 🟡 partial | CSV rejects duplicate/empty headers, missing required values and extra cells; omitted trailing optional fields stay unknown; provider-specific exports and statement reconciliation are unvalidated |
| Mirror evidence / deduplication | 🟡 partial; tests verified | Store-level Robolectric tests pass for duplicate suppression, raw evidence retention, import counts and rollback; provider correction and migration behaviour remain open |
| Driver economics | 🟡 partial | Platform and start-hour groupings; no validated hourly wage, weekday/zone or dead-mileage analysis |
| Headline earnings R-DM-005 | 🟡 implemented; unit tests verified | Trip-only PLN gross/fees/bonus totals and completeness logic; 5 financial-summary tests pass in the Vercel Sandbox build |
| Offer Desk | 🟡 partial | Manual entry and opt-in notification capture/ranking; saved vehicle running cost now feeds cost/km scoring; no direct Uber/Bolt partner API or auto-acceptance |
| Driver Hub | 🟡 partial | Documents, reminders, recurrence, expense/contact and WhatsApp TXT import/search plus persistent vehicle category/fuel/consumption/cost profile |
| Source catalogue | 🟡 partial | Honest access/status catalogue; broad connector framework and live feed-health are not complete |
| Rust domain core | 🟡 partial | Road-event model, TTL logic and JNI version bridge; canonical fusion engine is not implemented |
| CI / APK | 🟡 Vercel Sandbox build verified; ZIP-first download route retained | Build from source commit `cc1ce1d6ebc735818fe13ab27939388ac0a26ea1`: Rust 4/4 and Android 46/46 (0 failures/errors/skips); debug APK `0.1.1794540100-debug` assembled and v2 signature verified. APK SHA-256 `e40357d1b9634cbf5feb046cf755bda5ea55beed5b144db986608dcfb4ac59c5`; ZIP SHA-256 `73feb8b2979598e393348fef483b118e20c11633c48c3b8f832988c10b0c8522`. Public ZIP full download + range resume + embedded APK byte identity verified. Sandbox URL is temporary; stable Vercel deployment remains pending |
| Release signing | ⚪ pending | Persistent release keystore secrets were not configured in the last documented successful build |

## 3. Stabilization status (2026-11-13)

**Historical stabilization snapshot — superseded 2026-11-13.** The user later confirmed that the general click/action failures were gone and explicitly authorised continued product work. The remaining acceptance gates are tracked below and in the current checkpoint at the top of this file. The historical defect table is retained as evidence, not as a claim that those issues are still open. PR #3–#8 remain open/unmerged; PR #8 is the current diagnostic source branch, not a stable release.

Known user-reported defects (reproduction detail is incomplete; do not mark reproduced):

| ID | Severity | Area | Reproduction evidence | Expected | Actual | Status |
|---|---|---|---|---|---|---|
| BB-001 | Critical | General actions | Tap controls; user reports repeated failures (exact control sequence not supplied) | Action completes or reports a recoverable, actionable validation/network error | Generic action failure: “Couldn't complete that action, please try again later” | Open — capture DIAG action + stack |
| BB-002 | Critical | Runtime stability | User reports IllegalStateException; exact screen/action sequence not supplied | No runtime state exception during normal use | IllegalStateException shown | Open — root cause unknown |
| BB-003 | High | Map | Open map and use normal map controls; exact location/gesture not supplied | Correct location, stable rendering and responsive controls | User reports map is wrong | Open — device repro required |
| BB-004 | High | Routing | Select destination and request route; exact endpoints not supplied | Correct destination and viable route | User reports routing is wrong | Open — record endpoints and compare route geometry |
| BB-005 | High | Navigation | Start route and move with BusyBee foregrounded | Location follows driver and prompts/reroutes correctly | User reports no live navigation | Open — GPS/permission/device test required |
| BB-006 | High | Vehicle profile | Open vehicle selection/editor and choose a vehicle | Selection is visible, persists and is reflected in cost calculations | User reports vehicle selection is absent/not working as expected | Open — exact UI path and persisted value to verify |

## 4. Stabilization gates (ordered; no gate is skipped)

1. **Evidence capture:** identify installed APK version and device/Android version; collect DIAG LAST ACTION FAILURE plus action breadcrumbs. If DIAG is unavailable, obtain Android logcat around one reproduction. Do not infer a root cause from exception type alone.
2. **Launch and shell baseline:** cold launch, permission flow, each primary tab, repeated tab switching, back navigation, app pause/resume and process recreation. Resolve crashes/blocking failures first.
3. **Action reliability:** exercise every visible button/control on each surface; record action, preconditions, expected result, actual result and stack trace. Separate app defects from denied permissions, offline services and invalid input.
4. **Map and route correctness:** test known Kraków start/destination pairs; inspect selected destination coordinates, route geometry, route alternatives, camera bounds and failure states. Distinguish map rendering from geocoding/routing service errors.
5. **Navigation and vehicle settings:** verify foreground GPS updates, permission denial/regrant, turn progression, off-route handling, arrival, vehicle selection persistence and cost/km calculation. Retain known product limits (no live traffic; no background tracking; no vehicle-dimension routing) rather than representing them as bugs fixed.
6. **Regression and release gate:** add a focused regression test for each confirmed root cause; run full Rust and Android tests and assemble/signature-check APK; then repeat the original device reproduction and the smoke-test matrix on the target phone. A unit-test pass or APK build alone does not satisfy this gate.

**Exit criteria for working baseline:** cold launch and all primary surfaces work on the target phone; no repeatable crash or generic action failure across the recorded smoke matrix; map displays current location; at least two documented Kraków routes select the intended destination and plausible road geometry; foreground GPS progress and turn prompts work on-device; vehicle selection persists and updates cost calculation; relevant automated tests pass. Any unverified item remains explicitly open.

**Current blocker:** the target-device IllegalStateException report has not been supplied. PR #8 added DIAG stack capture, but the report must be inspected before a source-level root cause can be asserted. Repository access is available; physical-device execution and user-captured runtime evidence are not available to the engineering agent in this session.

**ETA:** no responsible calendar estimate until BB-001/BB-002 are identified. First milestone is root-cause diagnosis, not another APK.

## 5. Currently in flight

- R-DM-005 — trip-only headline earnings. Implementation and 5 focused tests are on branch `fix/trip-only-earnings-2026-11-12`; PR #3 is open and unmerged: https://github.com/ysfclc/BusyBee/pull/3. The Vercel Sandbox build passed the Rust and Android test suites and assembled a debug APK; PR remains open for review.
- R-DM-001 / CSV integrity — parser hardening and locale-independent normalisation plus Robolectric-backed SQLite tests are on branch `feature/mirror-store-integrity-tests-2026-11-12`, in draft PR #4 (https://github.com/ysfclc/BusyBee/pull/4), based on PR #3. Tests cover malformed CSV rejection, duplicate suppression, evidence retention, import counts and rollback. The complete suite passed on the Vercel Sandbox build (31 Android tests; 4 Rust tests); PR remains open for review.
- Vercel APK delivery — branch `feat/vercel-apk-builder-2026-11-12`, draft PR #5 (https://github.com/ysfclc/BusyBee/pull/5), based on PR #4. Dedicated Vercel Hobby project `busybee-apk` is linked to `ysfclc/BusyBee`; a Vercel Sandbox build succeeded at commit `d45aa55491757d4f1155c7d9ce441aa97254187b`. Debug APK SHA-256 `77f74c552077ce5e17b06f3bb829e90c4de2b42426ee8eaa7ca3ebfb46d66f06`, 48,877,022 bytes. The standard static deployment remains blocked by the Vercel daily deployment-creation limit; a temporary Sandbox route is serving the APK.

- MapLibre device-crash investigation — branch `fix/capture-device-crash-2026-11-13`, draft PR #7 (https://github.com/ysfclc/BusyBee/pull/7), stacked on PR #6. MapLibre remains visible under secondary tabs rather than switching to `GONE`; a DIAG control exports the last 48 action/lifecycle breadcrumbs and any uncaught JVM stack trace without recording trip content. Vercel Sandbox verification: Rust 4/4, Android 35/35, APK assembly and v2 debug signature passed. Physical-device confirmation remains open.

## 6. Known gaps & stubs

- Provider-specific Uber/Bolt exports have not been validated against real user-owned sample files; current CSV is a documented interchange contract, not a provider adapter. Parser rejects duplicate/empty headers, extra cells and missing required values; optional trailing blanks remain unknown.
- Financial statement reconciliation, adjustment handling and correction workflows remain incomplete.
- Weekday, zone/corridor, duration-based hourly wage, waiting-time and dead-mileage metrics lack the required source fields/session model.
- CSV parser regression and SQLite store-level tests pass in the Vercel Sandbox build. Provider correction/reconciliation semantics and schema migration tests remain open.
- Road intelligence is not yet a provider-neutral connector/fusion system. Current mapped OSM observations and weather are not live traffic-speed coverage.
- Route planning uses public Nominatim/OSRM, with candidate selection, route alternatives and in-app foreground turn prompts. OSRM estimates do not include live traffic; navigation requires BusyBee to remain foreground, is not a persistent background service, and uses standard passenger-car routing rather than vehicle-specific height/weight restrictions.
- Community road-report moderation, expiry, reputation and shared backend are not complete.
- Real-device install, GPS accuracy, turn prompts, off-route recalculation, upgrade/signature continuity, battery/performance and accessibility checks are unverified.
- The Vercel Sandbox successfully completed the Android/Rust build. The first standard Vercel preview deployment failed on a script URL interpolation bug; the fix is committed. Subsequent deployment creation is blocked by the team's daily limit of more than 100 deployment API creations, so the protected static download page is not yet deployed.
- docs/SPEC.md is absent; docs/PRODUCT_CHARTER.md is the canonical product specification. docs/RISKS.md now records data integrity, privacy, source quality, safety, CI and release-signing risks. docs/STATE.md, docs/COVERAGE.md and root CHANGELOG.md now exist.
- The README checkpoint inconsistency found at session start was corrected to describe the five implemented pilot surfaces and the current limits of the map and offer capture.

## 7. Environment & how to run

- Rust tests: `cargo test --workspace --manifest-path rust/Cargo.toml` (run from repository root).
- Android unit tests: `cd android && ./gradlew --no-daemon testDebugUnitTest`.
- Android debug build: `cd android && ./gradlew --no-daemon assembleDebug`.
- CI build: `.github/workflows/build-signed-apk.yml` (Rust tests, Android unit tests, native Android Rust libraries, APK assembly; persistent signing secrets enable signed release output).
- Vercel build: `vercel.json` invokes `bash scripts/vercel-build-apk.sh`; output is `public/` with `BusyBee.apk` and `build-info.json`. Vercel project: `busybee-apk` (Hobby, team `yusufculcu`). Signing env names and pipeline details: `docs/VERCEL_APK_BUILD.md`.
- Android baseline: Java 17, compile/target SDK 35, NDK 27.2.12479018, Gradle 8.9 in CI.
- The local container has no `/testbed` checkout, Gradle executable, Android SDK or Cargo toolchain. No local project build/test was executed. A Vercel Sandbox (4 vCPU / 8 GB) ran the repository build successfully; see the phase log for exact test and artifact verification.
- GitHub Actions runs 47 (43146041937), 48 (both attempts), 43283672002 and 43283810594 for updated PR #4 heads failed before runner assignment. Latest job 131411762866 has an empty step list; no test or build command ran. This is a pre-execution CI blocker, not a result from the source tests.

## 8. Next increments

1. Install the debug APK on the target Android device and verify launch, map, navigation, and data persistence. It has a `.debug` package ID and cannot update a release installation.
2. When the team's Vercel deployment API quota resets, trigger the standard protected static deployment and confirm `/BusyBee.apk` and `/build-info.json` are available there.
3. Configure the same persistent release keystore in Vercel only if an install-over-existing release APK is required; never create a replacement key.
4. Keep PR #3, #4, #5 and #6 open for review; PR #6 is stacked on PR #5 and covers the Android tab-host fix plus brand v0.1. Do not merge without review.
3. Validate the generic CSV contract against a real user-owned Uber or Bolt export and implement only evidence-backed provider adapters.
4. Resolve duplicate/correction semantics before claiming statement reconciliation; then choose one additional road-intelligence source for a provenance/freshness/failure-tested vertical slice.

## 6. Session handoff — 2026-11-13

- **Done:** Added `TabScreenHost`, which keeps the MapLibre screen attached to the Activity's `FrameLayout` while non-map tabs are displayed. Map selection restores visibility without reconstructing or reparenting the native map. The 3D control updates its own label/camera without rebuilding the screen. UI action entry points now log and surface recoverable exceptions rather than silently crashing through uncaught handler exceptions.
- **Done:** Added two Robolectric regression tests proving the map view remains attached and screen children do not accumulate across repeated tab switches.
- **Done:** Added BusyBee adaptive launcher icon resources and manifest icon/label references; documented the first working brand system in `docs/BRAND.md`. The icon is a vector bee with road-like bands, using the existing navy/mint palette plus amber.
- **Verified:** Vercel Sandbox ran Rust workspace tests (4/4), Android `testDebugUnitTest` successfully, and `assembleDebug` successfully. Compiler emitted existing MapLibre deprecation warnings. Build commit `7095489fa3810082c91a81a274d0bd1784e0d389`; Rust tests 4/4, Android tests 33/33 (0 failures/errors/skips); APK size 48,879,532 bytes; SHA-256 `41c3ab575a72ef3d3279aa6f7bebbb89fc41f46190f96b9680d04922400fdf43`; debug signature verified (v2). Android package and app label/icon verified from the built APK. Temporary endpoint `https://sb-3k9hxvpwg8wx.vercel.run/BusyBee.apk` returned HTTP 200.
- **Review:** Draft PR #6: https://github.com/ysfclc/BusyBee/pull/6 (stacked on PR #5; keep open for review).
- **Not done:** Physical-device tap sequence and crash reproduction/confirmation; production release signing; persistent Vercel download deployment; legal trademark/domain clearance.
- **Next:** Install the new APK, repeatedly switch all five tabs, toggle 3D, use layer chips, refresh and locate. If any crash remains, collect the exact Android crash log and fix that specific path.

## 6. Session handoff — 2026-11-13 (device-crash follow-up)

- **Done:** Changed `TabScreenHost` so the MapLibre screen remains visible and attached beneath secondary tab screens. This avoids hiding/showing the native renderer surface during tab selection. Added a persistent on-device diagnostic trail (last 48 UI/lifecycle action labels) and uncaught JVM exception report, accessible through the `DIAG` header control.
- **Privacy:** Diagnostics store action names, timestamps, Android/device model, app version and JVM stack traces only. They do not capture route destinations, imported text, earnings or trip rows. The user explicitly copies the report to the clipboard.
- **Verified:** Vercel Sandbox: Rust 4/4; Android 35/35 (0 failures, errors or skipped); `assembleDebug` successful; APK Signature Scheme v2 verified. APK: 48,879,532 bytes, SHA-256 `d93f077a2e1bef082918eb9d41b604493eb4c1751f95ac6ee29b6b778d5f2c3e`. Build metadata: version `0.1.1794536823-debug`, package `com.ysfclc.busybee.drivercommandcentre.debug`. External build-info URL returned the expected metadata for the exact commit.
- **Review:** Draft PR #7: https://github.com/ysfclc/BusyBee/pull/7 (stacked on PR #6; keep open).
- **Not done:** The crash has not been reproduced on the user's physical Android device. The diagnostics can capture JVM crashes and breadcrumbs but cannot directly catch every native SIGSEGV/OS kill. A stable Vercel download deployment and release signing remain pending.
- **Next:** Install this APK over the previous debug build, reproduce the close once, relaunch, tap `DIAG` in the header, and paste the copied report. If it is a native renderer failure, the last recorded action/lifecycle markers will narrow it down even when Android produced no JVM stack trace.

## 9. Open questions for the human

- [ ] Which real export should be used first for adapter validation (Uber or Bolt)? Default: do not claim provider-specific support until a sample export is available and tested.
- [ ] Are the persistent Android release-signing secrets configured? Default: treat existing builds as debug-only until a signed workflow run verifies the certificate.
- [ ] Can GitHub Actions hosted runners be started for this private repository? Default: do not switch to a paid runner or merge unverified work.


## 7. Session handoff — 2026-11-13 (GPS routing, vehicle profile and click diagnostics)

- **Done:** Added GPS-biased Polish destination candidate search (up to five matches), OSRM alternative route selection with maneuver-step parsing, route-bound camera framing, foreground GPS following, spoken turn prompts and automatic reroute attempts after repeated off-route readings. Added a saved vehicle profile (vehicle category, fuel type, consumption, energy price and maintenance per kilometre) in the existing local SQLite settings store. Its calculated cost/km now feeds the Offer Desk; incomplete profiles reset to the existing generic 0.85 PLN/km fallback rather than retaining stale costs from a prior vehicle.
- **Done:** Replaced generic caught-action feedback with action name and exception type. DIAG now captures bounded stack frames for caught actions and uncaught JVM failures while omitting exception messages. Legacy crash files containing messages are not included in reports.
- **Done:** Made the ZIP archive the primary Android download route and added ZIP generation/integrity metadata to the Vercel APK build script. Keep using the Brave-tested ZIP path.
- **Verified:** Vercel Sandbox build from `cc1ce1d6ebc735818fe13ab27939388ac0a26ea1`: Android unit tests 46/46 (0 failures, 0 errors, 0 skipped); Rust workspace tests 4/4; `bash -n scripts/vercel-build-apk.sh` passed; debug APK assembled and v2 signature verified; ZIP archive integrity passed and embedded APK matches byte-for-byte. Public route returned HTTP 200 for full download and HTTP 206 for a range request. APK SHA-256 `e40357d1b9634cbf5feb046cf755bda5ea55beed5b144db986608dcfb4ac59c5`; ZIP SHA-256 `73feb8b2979598e393348fef483b118e20c11633c48c3b8f832988c10b0c8522`. Segment projection refinement is covered by the 46th Android test.
- **Not done:** Physical-device GPS/map/navigation validation; reproduce the universal click error using the new DIAG stack report; live traffic source; background navigation service; truck/van dimension and access restrictions; persistent production signing; standard Vercel static deployment.
- **Known limitations:** Public Nominatim/OSRM can be rate-limited or unavailable. OSRM's driving estimates are not live traffic and do not account for vehicle height, weight or access restrictions. Navigation currently runs only while BusyBee remains in the foreground; leaving the app pauses location updates. Vehicle categories are stored for driver costing but do not yet change road routing.
- **Next:** Install the debug APK over the previous BusyBee debug build (same `.debug` package identity and debug signer). Verify current location, candidate destination selection, route alternatives, voice prompts and rerouting. Configure the real vehicle values, then check Offer Desk cost/km. If a tap still fails, copy DIAG and share the report; it now contains the caught failure stack frames without exception messages.

- **Download:** `https://sb-3k9hxvpwg8wx.vercel.run/BusyBee-download.zip` (temporary sandbox route; use ZIP and extract `BusyBee.apk`).


## 10. Session handoff — 2026-11-13 (stabilization plan)

- **Done:** Audited current PR state and existing state/risk documentation. Added docs/STABILIZATION_PLAN.md and recorded six user-reported defects with severity, available reproduction evidence, expected/actual behavior, blockers and explicit exit gates. Marked runtime shell and map/routing status blocked or unverified.
- **State changed:** STOP-WORK is active. No feature work, speculative fix, PR merge or release claim is authorised before stability gates pass. PR #3–#8 remain open/unmerged.
- **Verified:** Repository documentation and PR metadata were read from GitHub. Existing Vercel test/build results are historical evidence only; they do not verify physical-device stability.
- **Not done/next:** Inspect DIAG LAST ACTION FAILURE; if unavailable, obtain one logcat reproduction. Then reproduce and fix the first confirmed blocker, add a regression test, and proceed through the device smoke matrix.
- **Open questions:** Which exact action triggers IllegalStateException? Which APK version is currently installed?
- **Risks/landmines:** PR #8 is stacked on PR #7 and neither is merged. A debug APK does not establish release signing continuity. OSRM is not live traffic; navigation is foreground-only; vehicle category does not enforce road access restrictions.


## 11. Session handoff — 2026-11-13 (product control audit)

- **Done:** Added `docs/PRODUCT_CONTROL_AUDIT.md` after source inspection of the Android UI, command store, mirror store, vehicle profile, map style state, diagnostics, route planner, manifest and focused tests.
- **Finding:** Most saved entities are create-only. Driver items, documents, offers, community imports, road reports and imported trip rows lack complete edit/delete/undo controls. Trip imports retain raw evidence and use a transaction, but have no preview or post-commit import rollback/correction ledger.
- **Finding:** Source-level asynchronous lifecycle risks remain candidates only; no root cause for the target-device IllegalStateException has been established. Map-style readiness guards exist, but the tests exercise only the helper state, not MapLibre on-device behavior.
- **State changed:** Documentation only. No product code, database schema, UI, APK or PR merge changed. STOP-WORK remains active.
- **Verified:** Audit document was committed to this branch and must be read back before treating it as persisted.
- **Not done/next:** Resolve BB-001/BB-002 from the device DIAG/logcat evidence; continue target-device stability gates. After acceptance, prioritize safe record correction/deletion, import review/rollback and user data export.
- **Risks/landmines:** Do not add CRUD writes while the app is unstable; any future corrections to imported earnings must preserve immutable raw evidence and be separately auditable.


## Session handoff — 2026-11-13 (read-only asynchronous lifecycle review)

- **Done:** Inspected the current PR #8 source at `ea654235a127e189757dd2b912d6ed040815fe9c`, including route search, route-alternative selection, off-route rerouting, road/weather refresh, and Activity teardown.
- **Finding:** Four worker-to-main-thread UI flows contain callbacks that do not consistently guard the complete callback against a finishing/destroyed Activity. Executor shutdown in `onDestroy()` does not retract main-thread callbacks already queued. This is a confirmed source-level safety gap, not proof of the user's IllegalStateException.
- **State changed:** Documentation only. No Kotlin code, schema, UI, APK or PR merge changed. STOP-WORK remains active.
- **Verified:** Source line locations were read from GitHub; the risk and stabilization records are being updated with exact flow names and verification requirements.
- **Not done/next:** No root cause is assigned to BB-002. Capture the DIAG LAST ACTION FAILURE/logcat and reproduce lifecycle interruption while each worker is in flight before authorizing a fix.
- **Risks/landmines:** Guarding only `displayRoute()` is insufficient; the complete callback body, dialogs, Toasts, map annotation calls and Activity-owned state need lifecycle consideration. A source-level guard still requires target-device verification.


## Session handoff — 2026-11-13 (lifecycle callback mitigation)

- **Done:** Added `LifecycleSafeUiDispatcher` and routed all seven worker-to-main-thread callback sites in `MainActivity.kt` through its pre-post and execution-time Activity lifecycle checks.
- **Done:** Added two Robolectric regression tests: drop a callback queued before Activity destruction; deliver a callback while Activity remains alive.
- **State changed:** Source code and tests committed to the existing PR #8 branch. No PR merge, APK build, release or billing change.
- **Verified:** GitHub read-back confirms all seven callback replacements and both new files. Static source scan found no remaining `runOnUiThread` calls in `MainActivity.kt`.
- **Not verified:** Tests have not executed. GitHub Actions run 99 (43420894556) failed before any job steps were allocated; no steps or job logs were returned. Vercel Sandbox resume was rejected with HTTP 402 due to Hobby usage limit, with reset reported as 2026-12-01T00:00:00Z.
- **Not done/next:** Execute Android unit tests when a runner is available; then exercise destination search, route alternatives, reroute and road refresh while destroying/backgrounding the Activity. Capture the user's original DIAG/logcat separately to determine whether BB-002 shares this cause.
- **Risks/landmines:** The dispatcher protects the worker callback paths found in the audit; it does not prove MapLibre native lifecycle safety, fix other possible causes of IllegalStateException, or provide device verification. No stability/release claim.


## Session handoff — 2026-11-13 (remaining asynchronous lifecycle paths)

- **Done:** Read the current PR #8 MainActivity.kt end-to-end and traced getMapAsync / style-loaded, TextToSpeech initialization and location-listener completion paths.
- **Finding:** Three additional Activity-owned asynchronous paths are not covered by LifecycleSafeUiDispatcher: MapLibre map/style callbacks can mutate map and view state after Activity teardown; TTS initialization can complete after shutdown; and a location callback already queued before onPause() removes updates can still reach map mutation while the Activity is paused. These are source-confirmed lifecycle gaps, not reproduced failures and not an assigned cause for BB-002.
- **State changed:** Documentation only. No code, UI, database, APK or PR merge changed in this review step. STOP-WORK remains active.
- **Next:** Apply only narrow lifecycle-boundary guards to those callback paths, add focused regression coverage where possible, and verify the complete source read-back. Do not claim BB-002 fixed without the original DIAG/logcat and device reproduction.
- **Risks/landmines:** The MapLibre native renderer is not exercised by the existing boolean readiness test. A Robolectric dispatcher test cannot substitute for physical-device MapLibre lifecycle validation.


## Session handoff — 2026-11-13 (MapLibre/TTS/location lifecycle hardening)

- **Done:** Routed MapView map acquisition and style-loaded callbacks through LifecycleSafeUiDispatcher, so late completions are discarded when the Activity is finishing/destroyed.
- **Done:** Routed TextToSpeech initialization completion through the same guard and resolved the active engine only inside the guarded callback.
- **Done:** LocationListener now retains the newest fix but avoids map mutations unless Activity lifecycle is RESUMED. Location registration is deferred until RESUMED and onDestroy removes the listener defensively. onResume reapplies only a fix no older than 120 seconds after MapView resumes and restarts active/follow location updates.
- **Done:** Added a third Robolectric regression test for callback rejection after Activity.finish(). Existing destruction/live-Activity tests remain.
- **State changed:** Kotlin source and test changes committed to PR #8 branch. Documentation updated. No merge, APK build, release, UI redesign or feature work.
- **Verified:** Source files and docs have been read from GitHub after the changes; static checks confirm map/style/TTS callbacks are guarded and location updates are gated on RESUMED. **Tests and compilation have not run.**
- **Not done:** No physical-device test; no root cause assigned to BB-001/BB-002; MapLibre native renderer behavior remains unverified.
- **Risks/landmines:** Lifecycle dispatcher tests validate Activity liveness policy, not MapLibre internals. The original user-reported IllegalStateException still requires DIAG LAST ACTION FAILURE or logcat and a target-device reproduction.


- **Runner update:** GitHub Actions run 112 (43424684264), triggered by the latest GPS lifecycle source commit, also failed before any job steps were exposed (steps=null; no job log URL). No compile/test command ran. The Vercel sandbox remains stopped; its previous resume attempt was blocked by HTTP 402. No repeated resume attempt or billing change was made.


## Current stabilization/build checkpoint — 2026-11-13

- Current branch: `feat/live-navigation-vehicle-2026-11-13`; PR #8 remains open/draft/unmerged at `727a1289b8618c28ac15b4eca2d322d5be1c422e`.
- Lifecycle guards are source-read back, but latest code remains uncompiled and tests remain unexecuted.
- Latest GitHub Actions run #117 and its one retry failed before allocating steps; retry step list is empty.
- Vercel Android build sandbox resume is blocked by HTTP 402 Hobby usage cap (reset 2026-12-01 UTC).
- No APK has been built from current PR head. Do not install/share the historical APK as though it contains the latest lifecycle changes.
- New source-review finding: MapLibre style-loaded callback can mutate MapView while Activity is paused; record as BB-007 in RISKS.md. Narrow correction is pending build-capable verification.
- BB-001 and BB-002 remain open; original IllegalStateException root cause is still unknown.


- Follow-up CI run #118 (`43432885303`) for the updated PR head also failed before job steps were allocated (`131910538716`, `steps: null`). No Gradle or APK task ran.


## BB-007 source correction checkpoint — 2026-11-13

- Implemented one-time MapLibre activation gating in `MapStyleState` and `MainActivity`; deferred activation is retried in `onPostResume()`.
- Added pure unit tests for paused deferral, map availability and one-time activation.
- This is source-level only. No Android compile/test/APK verification is available: GitHub Actions runs fail before job steps and Vercel Sandbox is blocked by the Hobby usage cap.


- After the BB-007 code change, GitHub Actions run #124 (`43433780437`) and its retry (`131913684624`) both failed before allocating job steps. No Gradle, tests, APK assembly or signing ran.


## Session handoff — 2026-11-13 (repeated notification identity correction)

- **Done:** Source review found that notification deduplication used only platform/title/body. Since the notices table has a UNIQUE fingerprint and inserts use CONFLICT_IGNORE, two distinct ride offers with identical visible text could collapse into one record forever.
- **Changed:** Added `NotificationFingerprint.create(platform, title, body, postedAt)`; `captureNotice` now includes the Android notification post timestamp in the SHA-256 identity. Same content and same post time remain idempotent; a later notification with identical text is retained.
- **Tests authored:** Four `NotificationFingerprintTest` cases cover duplicate repost, later identical offer, changed body and platform separation.
- **Documentation:** BB-008 added to `docs/RISKS.md` and `docs/STABILIZATION_PLAN.md` with severity, reproduction, expected/actual, correction and remaining device validation.
- **State changed:** Kotlin source, one new unit-test file and documentation committed to PR #8 branch. No schema migration, merge, release or APK was performed.
- **Verified:** GitHub read-back confirms the helper is used by `captureNotice`, the old time-independent fingerprint expression is gone, four tests exist, and BB-008 appears in both ledgers. Static source checks only.
- **Not verified:** Android compilation, unit-test execution, live notification listener behaviour and physical-device validation. The Vercel named Android sandbox is stopped and its resume request again returned HTTP 402 due to the Hobby usage limit (reset 2026-12-01 UTC). No APK was built from this commit.
- **Next:** Run the four tests and full Android suite when a permitted runner is available; validate real notification postTime semantics and notification reconnect/update behavior on device.
- **Risks/landmines:** This fixes a confirmed source-level data-loss path but does not yet parse notification text into structured offers, authenticate ride-platform accounts, or control acceptance/decline actions.


## Session handoff — 2026-11-13 (driver-app notification allowlist)

- **Finding:** Notification listener used substring matching across six platform names. A passenger or unrelated app whose package contained a platform keyword could be ingested as a driver notification.
- **Changed:** Added exact package allowlist for `com.ubercab.driver` and `ee.mtakso.driver`, the driver package IDs already used in BusyBee's app-launch handoff. Unknown and passenger packages are ignored. Updated Sources UI to state only these two verified package mappings are active.
- **Tests authored:** Five `RidePlatformPackagesTest` cases cover the two known driver IDs, case-insensitive matching, passenger/lookalike rejection and unverified-provider rejection.
- **Documentation:** BB-009 added to the risk register and stabilization plan.
- **Verification:** GitHub source read-back/static inspection only. The package IDs still need confirmation on the target phone; no Android compile/test or APK build has run.
- **Product limitation:** This is notification capture, not authenticated account integration or ride-action control. Structured parsing and official APIs remain separate work.

## Session handoff — 2026-11-13 (Android driver-app package visibility)

- **Changed:** Added manifest `<queries>` entries for the exact Uber Driver and Bolt Driver packages so Android 11+ package visibility does not hide them from BusyBee's launch-intent lookup.
- **Verified:** Manifest source read-back only. No APK build, merged-manifest inspection or physical-device handoff test is available.
