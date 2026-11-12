# State Ledger
_Last updated: 2026-11-12 by ChatGPT — session 3_

## 1. Product snapshot

BusyBee is the control and delivery repository for Driver Command Centre, an Android-native driver-intelligence product piloting in Kraków. It combines a private, local-first Driver Data Mirror for cross-platform activity and economics with a provider-neutral road-intelligence and navigation experience. The map is a spatial interface to those capabilities, not the product by itself. Kotlin owns Android UI and platform integration; Rust is the deterministic domain core. Completeness means evidence-backed records, honest source coverage, safe driver-facing UX, and end-to-end tested capabilities—not a populated demo map.

## 2. Status board

| Area | State | Notes |
|---|---|---|
| Product charter / architecture | ✅ baseline | Product direction, source register and ADR-001–008 are committed |
| Android application shell | ✅ baseline | Stable package ID; Kotlin app and MapLibre integration |
| Spatial map / routing | 🟡 partial | Tilted Liberty vector map, mapped road observations, weather context and OSRM alternatives; not a complete multi-source live traffic aggregator |
| Driver Data Mirror import | 🟡 partial | CSV rejects duplicate/empty headers, missing required values and extra cells; omitted trailing optional fields stay unknown; provider-specific exports and statement reconciliation are unvalidated |
| Mirror evidence / deduplication | 🟡 partial | Store-level Robolectric tests cover duplicate suppression, raw evidence retention, import counts and rollback; authored but not executed |
| Driver economics | 🟡 partial | Platform and start-hour groupings; no validated hourly wage, weekday/zone or dead-mileage analysis |
| Headline earnings R-DM-005 | 🟡 implemented; unverified | Trip-only PLN gross/fees/bonus totals and completeness logic added with four unit cases; CI runs 47/48 did not assign a runner or execute steps |
| Offer Desk | 🟡 partial | Manual entry and opt-in notification capture/ranking; no direct Uber/Bolt partner API or auto-acceptance |
| Driver Hub | 🟡 partial | Documents, reminders, recurrence, expense/contact and WhatsApp TXT import/search surfaces |
| Source catalogue | 🟡 partial | Honest access/status catalogue; broad connector framework and live feed-health are not complete |
| Rust domain core | 🟡 partial | Road-event model, TTL logic and JNI version bridge; canonical fusion engine is not implemented |
| CI / APK | 🟡 Vercel pipeline added; first build pending | Dedicated Hobby project `busybee-apk` is linked to this repo; custom Android/Rust toolchain installer and static APK delivery are committed in PR #5. No Vercel build result or APK is verified yet |
| Release signing | ⚪ pending | Persistent release keystore secrets were not configured in the last documented successful build |

## 3. Currently in flight

- R-DM-005 — trip-only headline earnings. Implementation, four focused unit tests and docs are on branch `fix/trip-only-earnings-2026-11-12`; PR #3 is open and unmerged: https://github.com/ysfclc/BusyBee/pull/3. Next gate is a successful CI run with a real runner. Do not merge before Rust tests, Android unit tests and APK assembly pass.
- R-DM-001 / CSV integrity — parser hardening and locale-independent normalisation plus Robolectric-backed SQLite tests are on branch `feature/mirror-store-integrity-tests-2026-11-12`, in draft PR #4 (https://github.com/ysfclc/BusyBee/pull/4), based on PR #3. Tests cover malformed CSV rejection, duplicate suppression, evidence retention, import counts and rollback. No execution result exists yet.
- Vercel APK delivery — branch `feat/vercel-apk-builder-2026-11-12`, draft PR #5 (https://github.com/ysfclc/BusyBee/pull/5), based on PR #4. Dedicated Vercel Hobby project `busybee-apk` is linked to `ysfclc/BusyBee`; the first deployment/build is pending. The build script installs the Android/Rust toolchain, runs tests, assembles the APK and emits a checksum-bearing download page. No APK is available until deployment and artifact checks pass.

## 4. Known gaps & stubs

- Provider-specific Uber/Bolt exports have not been validated against real user-owned sample files; current CSV is a documented interchange contract, not a provider adapter. Parser rejects duplicate/empty headers, extra cells and missing required values; optional trailing blanks remain unknown.
- Financial statement reconciliation, adjustment handling and correction workflows remain incomplete.
- Weekday, zone/corridor, duration-based hourly wage, waiting-time and dead-mileage metrics lack the required source fields/session model.
- CSV parser regression tests and SQLite store-level tests are authored but unexecuted; do not treat import/persistence integrity as verified yet.
- Road intelligence is not yet a provider-neutral connector/fusion system. Current mapped OSM observations and weather are not live traffic-speed coverage.
- Route planning uses low-volume public Nominatim/OSRM services and hands turn-by-turn guidance to external navigation; it is not a production SLA or live-traffic route engine.
- Community road-report moderation, expiry, reputation and shared backend are not complete.
- Real-device install, upgrade/signature continuity, battery/performance and accessibility checks are unverified.
- Vercel's first Android toolchain build has not run to completion; Hobby build time, memory, disk and network limits may prevent the full SDK/NDK/Rust build. The deployment logs determine feasibility.
- docs/SPEC.md is absent; docs/PRODUCT_CHARTER.md is the canonical product specification. docs/RISKS.md now records data integrity, privacy, source quality, safety, CI and release-signing risks. docs/STATE.md, docs/COVERAGE.md and root CHANGELOG.md now exist.
- The README checkpoint inconsistency found at session start was corrected to describe the five implemented pilot surfaces and the current limits of the map and offer capture.

## 5. Environment & how to run

- Rust tests: `cargo test --workspace --manifest-path rust/Cargo.toml` (run from repository root).
- Android unit tests: `cd android && ./gradlew --no-daemon testDebugUnitTest`.
- Android debug build: `cd android && ./gradlew --no-daemon assembleDebug`.
- CI build: `.github/workflows/build-signed-apk.yml` (Rust tests, Android unit tests, native Android Rust libraries, APK assembly; persistent signing secrets enable signed release output).
- Vercel build: `vercel.json` invokes `bash scripts/vercel-build-apk.sh`; output is `public/` with `BusyBee.apk` and `build-info.json`. Vercel project: `busybee-apk` (Hobby, team `yusufculcu`). Signing env names and pipeline details: `docs/VERCEL_APK_BUILD.md`.
- Android baseline: Java 17, compile/target SDK 35, NDK 27.2.12479018, Gradle 8.9 in CI.
- A JDK and Kotlin compiler are present in the current container, but `/testbed` is absent and there is no repository checkout, Gradle executable, Android SDK or Cargo toolchain. No local project build/test was executed this session.
- GitHub Actions runs 47 (43146041937), 48 (both attempts), 43283672002 and 43283810594 for updated PR #4 heads failed before runner assignment. Latest job 131411762866 has an empty step list; no test or build command ran. This is a pre-execution CI blocker, not a result from the source tests.

## 6. Next increments

1. Complete the first Vercel APK build; inspect logs, confirm `/BusyBee.apk` and `/build-info.json`, verify APK package/version/signature and install on the target Android device. Do not call it ready until these checks pass.
2. Keep PR #3 and PR #4 unmerged until their Rust/Android tests and APK assembly execute successfully. The Vercel pipeline is intended to provide that execution path without paid GitHub-hosted minutes.
3. Validate the generic CSV contract against a real user-owned Uber or Bolt export and implement only evidence-backed provider adapters.
4. Resolve duplicate/correction semantics before claiming statement reconciliation; then choose one additional road-intelligence source for a provenance/freshness/failure-tested vertical slice.

## 7. Open questions for the human

- [ ] Which real export should be used first for adapter validation (Uber or Bolt)? Default: do not claim provider-specific support until a sample export is available and tested.
- [ ] Are the persistent Android release-signing secrets configured? Default: treat existing builds as debug-only until a signed workflow run verifies the certificate.
- [ ] Can GitHub Actions hosted runners be started for this private repository? Default: do not switch to a paid runner or merge unverified work.
