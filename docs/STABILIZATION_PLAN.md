# BusyBee Stabilization Plan

**Status: ACTIVE — STOP-WORK / NO FEATURE DEVELOPMENT**  
**Date:** 2026-11-13  
**Scope:** Stabilize the existing Android application only. No feature additions, redesign, scope changes, PR merges or speculative APK releases.

## Accountability

BusyBee is not currently accepted as a working app. The user has reported repeated action failures, an IllegalStateException, incorrect map/routing behavior, missing or nonfunctional live navigation, and vehicle-selection problems. Historical unit-test and APK-build success did not catch these target-device failures and must not be presented as proof of stability.

## Known defect register

| ID | Severity | Area | Reproduction evidence available | Expected | Actual reported | Required evidence |
|---|---|---|---|---|---|---|
| BB-001 | Critical | General actions | Tap controls; exact sequence not supplied | Action completes or returns a meaningful recoverable error | “Couldn't complete that action, please try again later” / repeated action failure | DIAG action label, stack frames and breadcrumbs |
| BB-002 | Critical | Runtime stability | User reports IllegalStateException; exact trigger unknown | No runtime state exception during normal use | IllegalStateException | Full LAST ACTION FAILURE report or logcat |
| BB-003 | High | Map | Map opened/used; exact coordinates and gesture unknown | Correct location, stable rendering and responsive controls | Map is wrong | Device, location permission/GPS state, coordinates, action sequence |
| BB-004 | High | Routing | Destination route attempted; endpoints unknown | Intended destination and plausible road route | Routing is wrong | Start/end coordinates, selected candidate and route geometry |
| BB-005 | High | Navigation | User attempted live guidance; detailed sequence unknown | Foreground GPS position, route progress and turn prompts update | No live navigation | Permission state, GPS trace, app foreground state, prompt/progress evidence |
| BB-006 | High | Vehicle profile | User attempted vehicle selection; exact screen/sequence unknown | Vehicle can be selected, persisted and used by cost calculation | Vehicle selection missing/not working as expected | UI path, selected value before/after relaunch, calculated cost/km |

These are user-reported defects, not independently reproduced defects. Missing reproduction detail is explicitly recorded rather than invented.

## Stabilization sequence

### Gate 0 — Freeze and establish evidence
- Freeze feature work and keep PRs #3–#8 open/unmerged.
- Identify installed APK version, package, Android version and device model.
- Reproduce BB-002 once and capture the DIAG report, especially LAST ACTION FAILURE.
- If DIAG cannot be opened or copied, capture Android logcat around the reproduction.
- Do not infer root cause from exception class or generic toast text.

### Gate 1 — App launch and shell stability
Run a target-device smoke sequence:
1. Force-stop and cold-launch.
2. Complete/deny/regrant requested permissions.
3. Open each primary surface.
4. Switch tabs repeatedly in both directions.
5. Exercise back navigation.
6. Background/resume the app.
7. Recreate the process and relaunch.

Record each action, expected result, actual result, timestamp and stack/breadcrumb evidence. Fix startup and shell blockers before map or navigation tuning.

### Gate 2 — Action reliability
- Inventory every visible action on each primary surface.
- Exercise each action with valid, missing, denied-permission, offline and boundary inputs as applicable.
- Separate UI/lifecycle exceptions from network errors, permission denials and validation failures.
- Fix the first confirmed root cause; add a regression test reproducing it.
- Repeat the original failing action on the target device before moving on.

### Gate 3 — Map and route correctness
- Use known Kraków start/destination pairs and record coordinates.
- Verify location fix freshness/accuracy and selected geocoding candidate.
- Verify route geometry follows plausible roads and the intended destination.
- Test route alternatives, camera framing, no-result, timeout and offline states.
- Keep source limitations visible: public OSRM duration is not live traffic; the current profile is standard passenger car.

### Gate 4 — Navigation and vehicle settings
- Verify foreground location updates while moving.
- Verify route progress, turn sequence, spoken prompt, off-route detection, reroute and arrival.
- Verify behavior when location permission is denied, revoked, inaccurate or unavailable.
- Verify vehicle category/fuel/consumption/cost selection persists after app restart.
- Verify cost/km and Offer Desk calculations use the saved profile.
- Do not claim background navigation or vehicle-dimension routing; those are known limitations, not fixes to sneak into this stabilization scope.

### Gate 5 — Regression and acceptance
- Add a focused regression test for each confirmed root cause.
- Run full Rust workspace tests and Android unit tests.
- Assemble APK and verify package identity and signature.
- Install on the target phone and repeat the original crash reproduction plus the complete smoke matrix.
- Record test command, exact source commit, test totals, APK checksum and device results.
- No fixed status until the corresponding target-device check passes.

## Source-review update — asynchronous callback risk (2026-11-13)

A read-only inspection of `MainActivity.kt` at `ea654235a127e189757dd2b912d6ed040815fe9c` found these concrete worker-to-main-thread paths:

| Flow | Worker operation | Main-thread callback | Unprotected Activity-owned work |
|---|---|---|---|
| Destination search | `planRoute()` | approx. lines 287–291 and 328+ | Updates route/map labels; may show destination chooser |
| Route alternatives | nested in `planRoute()` | approx. lines 295–313 | Updates labels; may show route chooser |
| Off-route reroute | `updateNavigation()` | approx. lines 464–479 | Reads navigation state, updates views, calls `displayRoute()`, speaks |
| Road/weather refresh | `refreshRoadIntelligence()` | approx. lines 504–508 | Updates labels, adds/removes map annotations, displays Toast |

The callbacks are posted with `runOnUiThread`; they do not all check `isFinishing`/`isDestroyed` before touching Activity-owned state or showing UI. `onDestroy()` calls `worker.shutdownNow()`, but already-posted main-thread callbacks are not withdrawn by executor shutdown. `displayRoute()` has its own liveness guard, but that does not cover the rest of each callback.

**Assessment:** confirmed source-level lifecycle-safety gap; actual crash/reproduction and relation to BB-002 remain unknown. This is not a diagnosis of the reported IllegalStateException. Narrow preventive guards for confirmed callback-lifetime gaps are now committed and documented below; they do not authorize unrelated speculative fixes. Next verification should deliberately interrupt each network operation while it is in flight and inspect DIAG/logcat evidence.

## Lifecycle callback mitigation — implementation status (2026-11-13)

- **Changed:** Added `LifecycleSafeUiDispatcher`; it rejects work if the Activity is finishing/destroyed before scheduling and rechecks on the main thread immediately before callback execution.
- **Applied:** Replaced all seven worker-originated `runOnUiThread` callback sites in `MainActivity.kt`: destination search, route alternatives (success and failure), destination-search failure, off-route reroute (success and failure), and road/weather refresh.
- **Regression tests authored:** `LifecycleSafeUiDispatcherTest` checks that a queued worker result is discarded after Activity destruction and that a result is delivered while the Activity is alive.
- **Source verification:** All seven sites and both new files were read back from GitHub. No remaining `runOnUiThread` call exists in `MainActivity.kt`.
- **Execution status:** Not run. GitHub Actions run `43420894556` (run 99) failed before any job steps were allocated; the job endpoint returned no steps and no logs. Vercel Sandbox resume returned HTTP 402 because the Hobby usage limit is exceeded; reset is reported as `2026-12-01T00:00:00Z`. No billing or plan changes were made.
- **Assessment:** This mitigates the source-confirmed lifecycle gap but does not establish that BB-002 is fixed. Do not release or claim stability until the new tests execute, a device lifecycle interruption is exercised, and the original IllegalStateException is diagnosed independently.

## Working-baseline acceptance criteria

All must pass:
- Cold launch and primary navigation complete without crash.
- No repeatable IllegalStateException or generic action failure across the documented smoke matrix.
- Map renders the driver's current location with a valid fix.
- At least two recorded Kraków routes choose the intended destination and plausible road geometry.
- Foreground GPS route progress and turn prompts work on the target device.
- Vehicle selection persists across restart and updates cost/km.
- Full automated test suites pass.
- Any untested scenario remains listed as unverified; no blanket stability claim.

## Current blockers and escalation

1. **Primary blocker:** the exact target-device IllegalStateException stack/action report is not present in the engineering context. PR #8 added DIAG capture, but the report must be inspected before attributing the failure to a source line.
2. **Device access:** repository access is available; direct control of the user's physical Android device is not. The user must provide the DIAG/logcat output and perform the final on-device reproduction.
3. **Build infrastructure:** Vercel Sandbox previously built the source and tests, but that does not replace physical-device verification. No new APK should be built merely to create an appearance of progress.
4. **Known product limitations:** OSRM is not live traffic; navigation is foreground-only; standard passenger-car routing does not enforce van/truck dimensions or restrictions; release-signing continuity remains unverified.

## ETA policy

No calendar ETA is promised until BB-001/BB-002 have a captured failure path. The next deliverable is a root-cause diagnosis and verified fix for the first critical blocker, not another feature or speculative build.

## Change control

- PRs #3–#8 remain open and unmerged.
- No branch is promoted or merged without review and explicit approval.
- Every source change must be tied to a defect ID and regression evidence.
- If evidence cannot be obtained or the failure cannot be reproduced, escalate that limitation clearly instead of guessing.


## Follow-up source review — MapLibre, TTS and location lifecycle (2026-11-13)

The worker callback mitigation does not cover all asynchronous completions in MainActivity.kt:

| Path | Current behavior | Lifecycle gap | Status |
|---|---|---|---|
| MapView.getMapAsync / Style loaded callback | Stores the map, changes camera, renders road annotations, starts refresh and location work | No finishing/destroyed check around either callback | Source-confirmed candidate; no runtime reproduction |
| TextToSpeech initialization callback | Mutates speechReady and configures language | Can complete after onDestroy() has shut down the engine | Source-confirmed candidate; no runtime reproduction |
| LocationListener.onLocationChanged | Caches fix and calls map update | A queued callback may arrive after onPause() removed updates; update path does not require RESUMED state | Source-confirmed candidate; no runtime reproduction |

This review does not identify the user's IllegalStateException root cause and does not change BB-002's status. The next permitted stabilization increment is narrowly limited to these callback lifetime boundaries, with tests for the lifecycle helper and source read-back. Do not expand into navigation behavior, MapLibre rendering changes or UI work. Physical-device interruption tests remain mandatory.


## Follow-up lifecycle hardening — implementation status (2026-11-13)

- MapLibre map acquisition and style-loaded callbacks now use LifecycleSafeUiDispatcher, checking Activity lifetime before and immediately before callback execution.
- TextToSpeech initialization completion now uses the same dispatcher and resolves the current engine reference only inside the guarded callback.
- Location callbacks retain the latest fix but do not touch the map unless the Activity is RESUMED. Location registration is deferred until RESUMED; onDestroy removes the listener defensively. onResume reapplies only a fix no older than 120 seconds after MapView resumes and restarts active/follow updates.
- LifecycleSafeUiDispatcherTest now covers queued callback rejection after destruction, rejection after finish, and delivery while alive (3 tests authored).
- **Not verified:** No test or compile run has succeeded for this increment. The available GitHub Actions and Vercel runner blockers remain. Source read-back is the only verification until a runner becomes available.
- **Assessment:** Narrow lifecycle hardening only. It does not establish that BB-002 is fixed, and it does not replace MapLibre physical-device lifecycle tests or the original DIAG/logcat evidence.


### Latest runner check — 2026-11-13

After the GPS lifecycle changes, GitHub Actions run 112 (43424684264) also completed as failure before any job steps were exposed. Its job response has steps=null and no job log URL; no compile or test command ran. The Vercel Android sandbox remains stopped after the previously reported HTTP 402 Hobby usage-cap rejection. The current lifecycle changes therefore remain source-readback-only and uncompiled.


## Latest verification and build attempt — 2026-11-13

- PR #8 head remains `727a1289b8618c28ac15b4eca2d322d5be1c422e`, open, draft and unmerged.
- GitHub Actions run #117 (`43424986985`) failed before job steps were allocated. A single retry was accepted, queued, then failed again; the retry job returned an empty step list. No Gradle command, unit test, APK assembly or signing step ran.
- The Vercel Android build sandbox remains stopped. A fresh resume attempt returned HTTP 402 `payment_required`: the Vercel Hobby usage limit is exceeded, with reset stated as `2026-12-01T00:00:00Z`. No plan or billing changes were made.
- **APK status:** no APK was produced from PR #8's current head. The historical APK referenced in the earlier PR description belongs to an older source commit and must not be represented as this build.

## Additional lifecycle boundary found during follow-up review

The MapLibre style-loaded callback is protected against a finishing/destroyed Activity by `LifecycleSafeUiDispatcher`, but the dispatcher deliberately does not reject a merely paused Activity. The callback then sets camera position, updates the driver marker, renders road annotations, updates map status and starts road-intelligence refresh. A style completion arriving after `onPause()` can therefore still mutate the paused MapView. This is a source-confirmed lifecycle boundary, not a reproduced crash and not a diagnosis of BB-002.

The next narrow stabilization change should separate “style is ready” from “activate the map against a RESUMED Activity”: retain a pending activation when style completion arrives while paused, and apply it once in `onResume()`. Add focused state tests and do not expand into map design or navigation behavior. Because the current source has not compiled in this environment, avoid stacking further unverified code changes until a runner is available.


- Follow-up run #118 (`43432885303`), triggered by the documentation checkpoint commit on the current PR head, also failed before job steps were allocated (job `131910538716`, `steps: null`). This confirms the current GitHub Actions runner path is still not reaching the build workflow; no Gradle/APK step executed.
