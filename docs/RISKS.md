# BusyBee risk register

This register tracks material product, data-integrity, safety and delivery risks. Ratings are qualitative and should be revisited when source coverage, deployment or user-data handling changes.

| ID | Risk | Likelihood | Impact | Mitigation / control | Status |
|---|---|---:|---:|---|---|
| R-01 | Imported activity may be misclassified as revenue, or incomplete source values may be presented as zero/complete | Medium | High | Keep record types and currencies explicit; calculate headline earnings from recognised trip rows only; preserve unknowns; require focused tests and source-statement reconciliation before claiming financial completeness | Open — R-DM-005 implemented but unverified |
| R-02 | Duplicate imports, partial failures or schema changes could corrupt or lose personal trip/evidence history | Medium | High | Use transactional imports, deterministic fingerprints, a separate raw-evidence ledger and non-destructive migrations; run SQLite store-level regression tests and migration tests before release | Open — persistence tests authored, execution pending |
| R-03 | Uber/Bolt export formats or partner data access may differ from the generic interchange contract or be unavailable under provider terms | High | High | Do not claim provider adapters or direct API access without a real user-owned sample and documented access/licence review; preserve raw source evidence and make adapters independently testable | Open |
| R-04 | Private trip, earnings, location or chat-export data could be exposed beyond the driver's device without informed consent | Low | Critical | Keep personal records app-private and local-first; do not upload or share by default; keep community contributions separate and opt-in; review permissions and backup behaviour before adding sync | Open |
| R-05 | Stale, duplicated or low-confidence road observations could mislead a driver | Medium | High | Preserve provenance, observation time, freshness, spatial precision and confidence rationale; distinguish mapped observations from live traffic; show source outages and uncertainty; keep driving UI glanceable | Open |
| R-06 | Public geocoding/routing/weather services may rate-limit, change or become unavailable | Medium | Medium | Treat external services as replaceable adapters; avoid high-frequency polling; handle timeout/unavailable states; do not present route estimates as live-traffic predictions | Open |
| R-07 | GitHub-hosted CI runner allocation is blocked; Vercel standard deployment creation is also rate-limited, which can delay repeatable APK publishing | High | High | Vercel Sandbox now executes the pinned Android/Rust toolchain and test suite (4 Rust + 31 Android tests passed); retain exact build metadata and artifact checksum; keep standard deployment pending until the daily API cap resets and a READY deployment is verified | Mitigated for build verification; static delivery still open |
| R-08 | A debug APK or a newly generated signing key could fail to update the installed production app and may appear to lose user data | Medium | High | Keep debug application ID separate; require the persistent release keystore and signature verification for install-over-existing updates; never generate a replacement key casually | Open — debug APK verified; persistent release signing and device upgrade continuity not confirmed |
| R-10 | Public geocoding/routing results or simplified GPS map matching may choose an inaccurate destination, manoeuvre or reroute | Medium | High | Present multiple destination candidates and route alternatives; require a fresh GPS fix; project GPS fixes onto route segments for progress/off-route checks; expose source/traffic limitations; validate route traces and turn prompts on a physical device before relying on navigation | Open — logic tests pass; physical route validation pending |
| R-11 | Vehicle category selection may imply route access restrictions that the current OSRM driving profile does not enforce | Medium | High | Make clear that vehicle profiles currently affect running-cost calculations only; keep routing on standard passenger-car profile until dimension/weight/access-aware routing is implemented and tested | Open — limitation shown in Driver Hub vehicle editor |

| R-09 | MapLibre or another UI action may fail on target-device interactions | Medium | High | Keep the renderer visible beneath secondary tabs; capture bounded action/lifecycle breadcrumbs plus caught/uncaught stack frames; omit exception messages; reproduce on target device | Open — improved stack diagnostics built; target-device failure source not yet reported |

## Release gates

- No financial or import-integrity requirement is marked complete until its focused tests execute successfully.
- No provider-specific adapter is advertised until a representative user-owned export is validated and source access/licensing is documented.
- No road-intelligence source is considered trustworthy without provenance, freshness, failure handling and explicit unavailable states.
- No production update is distributed without verifying package identity and signing-certificate continuity.
- Personal Driver Mirror data remains private by default; any future sync or sharing requires an explicit product decision and consent flow.


## Additional navigation controls

- Public OSRM/Nominatim endpoints are best-effort public services; enforce timeouts, avoid high-frequency polling, surface errors and do not describe OSRM durations as live-traffic ETAs.
- Current in-app guidance is foreground-only. Location updates are removed on Activity pause; this is not a background navigation service and must not be relied upon with the app closed.
- Vehicle profiles feed cost-per-kilometre economics only. They do not currently constrain route selection for van/truck height, gross weight, width, low-emission zones or vehicle access classes.
- Stack diagnostics omit exception messages and bound frame count/length. Continue to avoid logging entered destinations, trip content or private earnings.


## R-09 — Target-device runtime instability (updated 2026-11-13)

**Status: Open — release blocker.** User reports repeated action failures and an IllegalStateException, plus incorrect map/routing and non-working navigation/vehicle selection. Existing Robolectric tests and Vercel builds have not reproduced or cleared these failures on the target phone. Capture the DIAG LAST ACTION FAILURE report (or Android logcat), identify the exact app-owned stack frame, reproduce the root cause, add a regression test, and verify the original action on the target device. Do not infer a root cause from the exception class. No stability/release claim until the target-device smoke matrix passes.


## R-12 — Missing user record controls (added 2026-11-13)

**Status: Open — product completeness blocker.** Source audit found create-only or limited-status flows for driver items, documents, offers, community imports, road reports and imported trip records. Users cannot reliably correct or remove saved personal data. Add tested edit/delete/archive/undo and explicit destructive confirmations after the runtime baseline is accepted.

## R-13 — Import correction and rollback gap (added 2026-11-13)

**Status: Open — data integrity risk.** CSV imports are committed immediately without preview, row selection or post-commit import rollback. Raw evidence is retained and SQLite import is transactional, but there is no separate correction/exclusion ledger for normalized trip data. Preserve original evidence and implement auditable corrections only after validating the import contract against real exports.

## R-14 — Asynchronous Activity lifecycle callbacks (source-confirmed risk; runtime impact unverified, 2026-11-13)

**Status: Candidate — not reproduced; not attributed to BB-002.** A source review of `MainActivity.kt` at `ea654235a127e189757dd2b912d6ed040815fe9c` found multiple worker-to-main-thread callbacks without a common Activity-liveness guard:

- `planRoute()`: destination search posts a callback at approximately lines 287–291; route-alternative lookup posts success/error callbacks at lines 295–313; destination-search failure posts at lines 328 onward. The callbacks update view references and may construct/show `AlertDialog` instances.
- `updateNavigation()`: off-route recalculation posts success/error callbacks at lines 464–479. The success callback may call `displayRoute()`, update navigation views and speak; the error callback also mutates Activity-owned state/views.
- `refreshRoadIntelligence()`: road/weather refresh posts a callback at lines 504–508 that updates view references, renders map annotations and shows a Toast.
- `onDestroy()` shuts down the executor at the end of the Activity lifecycle, but that does not cancel callbacks already queued on the main thread. The existing `displayRoute()` guard does not protect the other callback bodies.

A narrowly scoped mitigation is now committed on the same branch: `LifecycleSafeUiDispatcher` checks Activity finishing/destroyed state before posting and again on the main thread immediately before executing the callback. All seven worker-originated `runOnUiThread` sites in `MainActivity.kt` now use this dispatcher. Two Robolectric tests cover dropping a queued result after Activity destruction and delivering a result while the Activity remains alive. Source and test files were read back from GitHub. **Execution is pending**: GitHub Actions run 43420894556 failed before producing any job steps, and Vercel Sandbox resume is blocked by the Hobby usage cap (402; reset 2026-12-01). This is a code change with authored tests, not a verified runtime fix.

This establishes an asynchronous lifecycle-safety gap in source, not proof that it caused the user's IllegalStateException. Required next evidence: interrupt each operation by navigating away, backgrounding, rotating/recreating where supported, or closing the Activity while the worker is in flight; inspect DIAG/logcat. Any fix must guard the entire posted callback (including dialogs and Toasts), handle rejected/cancelled work, and have a lifecycle regression test. No patch has been made because the exact device failure remains unobserved and STOP-WORK forbids speculative fixes.


## R-15 — MapLibre and speech callbacks outliving Activity state (source-confirmed risk; runtime impact unverified, 2026-11-13)

**Status: Candidate — not reproduced; not attributed to BB-002.** A follow-up read of the current PR #8 head found asynchronous callbacks outside the worker dispatcher coverage:

- MapView.getMapAsync callback records diagnostics, stores the map and calls setStyle; its style-loaded callback marks readiness, updates camera/view state, renders annotations and starts road/location work without checking whether the Activity is finishing or destroyed.
- The TextToSpeech initialization callback mutates speechReady and configures the TTS engine without checking Activity lifetime. onDestroy() shuts the engine down, but an already-pending initialization callback is a separate asynchronous completion path.
- The LocationListener caches a fix and calls updateDriverLocation. onPause() removes location updates, but a callback already queued before removal can still arrive; updateDriverLocation checks finishing/destroyed and map-style readiness but not whether the Activity is resumed.

These are source-level lifecycle gaps, not evidence that MapLibre or TTS caused the reported exception. The worker dispatcher added earlier covers only the seven worker-originated UI callbacks. Next narrowly scoped hardening: route MapLibre/TTS completions through the same pre-post and execution-time liveness guard, and prevent a late location callback from touching the paused map while retaining its location fix. Add focused regression coverage where the lifecycle contract can be tested without a real MapLibre renderer. Device validation remains required.
