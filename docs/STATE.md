# State Ledger
_Last updated: 2026-11-13 by ChatGPT — map crash and brand pass_

## 1. Product snapshot

BusyBee is the product brand for Driver Command Centre, an Android-native driver-intelligence product piloting in Kraków. Brand v0.1 now includes an original adaptive Android launcher mark and a documented navy/mint/amber visual system (`docs/BRAND.md`). It combines a private, local-first Driver Data Mirror for cross-platform activity and economics with a provider-neutral road-intelligence and navigation experience. The map is a spatial interface to those capabilities, not the product by itself. Kotlin owns Android UI and platform integration; Rust is the deterministic domain core. Completeness means evidence-backed records, honest source coverage, safe driver-facing UX, and end-to-end tested capabilities—not a populated demo map.

## 2. Status board

| Area | State | Notes |
|---|---|---|
| Product charter / architecture | ✅ baseline | Product direction, source register and ADR-001–008 are committed |
| Android application shell | 🟡 stability fix built; device validation pending | Stable package ID; persistent MapLibre screen host prevents reparenting during tab navigation; real-device click test still required |
| Spatial map / routing | 🟡 partial | Tilted Liberty vector map, mapped road observations, weather context and OSRM alternatives; not a complete multi-source live traffic aggregator |
| Driver Data Mirror import | 🟡 partial | CSV rejects duplicate/empty headers, missing required values and extra cells; omitted trailing optional fields stay unknown; provider-specific exports and statement reconciliation are unvalidated |
| Mirror evidence / deduplication | 🟡 partial; tests verified | Store-level Robolectric tests pass for duplicate suppression, raw evidence retention, import counts and rollback; provider correction and migration behaviour remain open |
| Driver economics | 🟡 partial | Platform and start-hour groupings; no validated hourly wage, weekday/zone or dead-mileage analysis |
| Headline earnings R-DM-005 | 🟡 implemented; unit tests verified | Trip-only PLN gross/fees/bonus totals and completeness logic; 5 financial-summary tests pass in the Vercel Sandbox build |
| Offer Desk | 🟡 partial | Manual entry and opt-in notification capture/ranking; no direct Uber/Bolt partner API or auto-acceptance |
| Driver Hub | 🟡 partial | Documents, reminders, recurrence, expense/contact and WhatsApp TXT import/search surfaces |
| Source catalogue | 🟡 partial | Honest access/status catalogue; broad connector framework and live feed-health are not complete |
| Rust domain core | 🟡 partial | Road-event model, TTL logic and JNI version bridge; canonical fusion engine is not implemented |
| CI / APK | 🟡 Vercel Sandbox build verified; static deployment pending | Prior build: Android 31 tests. Crash/brand fix build: 2 tab-host regression tests added; Rust 4/4, Android suite and debug APK assembly passed on Vercel Sandbox. Standard Vercel deployment remains pending |
| Release signing | ⚪ pending | Persistent release keystore secrets were not configured in the last documented successful build |

## 3. Currently in flight

- R-DM-005 — trip-only headline earnings. Implementation and 5 focused tests are on branch `fix/trip-only-earnings-2026-11-12`; PR #3 is open and unmerged: https://github.com/ysfclc/BusyBee/pull/3. The Vercel Sandbox build passed the Rust and Android test suites and assembled a debug APK; PR remains open for review.
- R-DM-001 / CSV integrity — parser hardening and locale-independent normalisation plus Robolectric-backed SQLite tests are on branch `feature/mirror-store-integrity-tests-2026-11-12`, in draft PR #4 (https://github.com/ysfclc/BusyBee/pull/4), based on PR #3. Tests cover malformed CSV rejection, duplicate suppression, evidence retention, import counts and rollback. The complete suite passed on the Vercel Sandbox build (31 Android tests; 4 Rust tests); PR remains open for review.
- Vercel APK delivery — branch `feat/vercel-apk-builder-2026-11-12`, draft PR #5 (https://github.com/ysfclc/BusyBee/pull/5), based on PR #4. Dedicated Vercel Hobby project `busybee-apk` is linked to `ysfclc/BusyBee`; a Vercel Sandbox build succeeded at commit `d45aa55491757d4f1155c7d9ce441aa97254187b`. Debug APK SHA-256 `77f74c552077ce5e17b06f3bb829e90c4de2b42426ee8eaa7ca3ebfb46d66f06`, 48,877,022 bytes. The standard static deployment remains blocked by the Vercel daily deployment-creation limit; a temporary Sandbox route is serving the APK.

## 4. Known gaps & stubs

- Provider-specific Uber/Bolt exports have not been validated against real user-owned sample files; current CSV is a documented interchange contract, not a provider adapter. Parser rejects duplicate/empty headers, extra cells and missing required values; optional trailing blanks remain unknown.
- Financial statement reconciliation, adjustment handling and correction workflows remain incomplete.
- Weekday, zone/corridor, duration-based hourly wage, waiting-time and dead-mileage metrics lack the required source fields/session model.
- CSV parser regression and SQLite store-level tests pass in the Vercel Sandbox build. Provider correction/reconciliation semantics and schema migration tests remain open.
- Road intelligence is not yet a provider-neutral connector/fusion system. Current mapped OSM observations and weather are not live traffic-speed coverage.
- Route planning uses low-volume public Nominatim/OSRM services and hands turn-by-turn guidance to external navigation; it is not a production SLA or live-traffic route engine.
- Community road-report moderation, expiry, reputation and shared backend are not complete.
- Real-device install, upgrade/signature continuity, battery/performance and accessibility checks are unverified.
- The Vercel Sandbox successfully completed the Android/Rust build. The first standard Vercel preview deployment failed on a script URL interpolation bug; the fix is committed. Subsequent deployment creation is blocked by the team's daily limit of more than 100 deployment API creations, so the protected static download page is not yet deployed.
- docs/SPEC.md is absent; docs/PRODUCT_CHARTER.md is the canonical product specification. docs/RISKS.md now records data integrity, privacy, source quality, safety, CI and release-signing risks. docs/STATE.md, docs/COVERAGE.md and root CHANGELOG.md now exist.
- The README checkpoint inconsistency found at session start was corrected to describe the five implemented pilot surfaces and the current limits of the map and offer capture.

## 5. Environment & how to run

- Rust tests: `cargo test --workspace --manifest-path rust/Cargo.toml` (run from repository root).
- Android unit tests: `cd android && ./gradlew --no-daemon testDebugUnitTest`.
- Android debug build: `cd android && ./gradlew --no-daemon assembleDebug`.
- CI build: `.github/workflows/build-signed-apk.yml` (Rust tests, Android unit tests, native Android Rust libraries, APK assembly; persistent signing secrets enable signed release output).
- Vercel build: `vercel.json` invokes `bash scripts/vercel-build-apk.sh`; output is `public/` with `BusyBee.apk` and `build-info.json`. Vercel project: `busybee-apk` (Hobby, team `yusufculcu`). Signing env names and pipeline details: `docs/VERCEL_APK_BUILD.md`.
- Android baseline: Java 17, compile/target SDK 35, NDK 27.2.12479018, Gradle 8.9 in CI.
- The local container has no `/testbed` checkout, Gradle executable, Android SDK or Cargo toolchain. No local project build/test was executed. A Vercel Sandbox (4 vCPU / 8 GB) ran the repository build successfully; see the phase log for exact test and artifact verification.
- GitHub Actions runs 47 (43146041937), 48 (both attempts), 43283672002 and 43283810594 for updated PR #4 heads failed before runner assignment. Latest job 131411762866 has an empty step list; no test or build command ran. This is a pre-execution CI blocker, not a result from the source tests.

## 6. Next increments

1. Install the debug APK on the target Android device and verify launch, map, navigation, and data persistence. It has a `.debug` package ID and cannot update a release installation.
2. When the team's Vercel deployment API quota resets, trigger the standard protected static deployment and confirm `/BusyBee.apk` and `/build-info.json` are available there.
3. Configure the same persistent release keystore in Vercel only if an install-over-existing release APK is required; never create a replacement key.
4. Keep PR #3, #4 and #5 open for review; test execution is now verified on the Vercel Sandbox, but do not merge without review.
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

## 7. Open questions for the human

- [ ] Which real export should be used first for adapter validation (Uber or Bolt)? Default: do not claim provider-specific support until a sample export is available and tested.
- [ ] Are the persistent Android release-signing secrets configured? Default: treat existing builds as debug-only until a signed workflow run verifies the certificate.
- [ ] Can GitHub Actions hosted runners be started for this private repository? Default: do not switch to a paid runner or merge unverified work.
