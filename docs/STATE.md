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
| CI / APK | 🟡 baseline verified only | Run 46 is the last documented successful build/test baseline; PR runs 47 and 48 (including retry attempt 2) ended before runner assignment (runner_id: 0, no steps); current changes are unverified |
| Release signing | ⚪ pending | Persistent release keystore secrets were not configured in the last documented successful build |

## 3. Currently in flight

- R-DM-005 — trip-only headline earnings. Implementation, four focused unit tests and docs are on branch `fix/trip-only-earnings-2026-11-12`; PR #3 is open and unmerged: https://github.com/ysfclc/BusyBee/pull/3. Next gate is a successful CI run with a real runner. Do not merge before Rust tests, Android unit tests and APK assembly pass.
- R-DM-001 / CSV integrity — parser hardening and locale-independent normalisation plus Robolectric-backed SQLite tests are on branch `feature/mirror-store-integrity-tests-2026-11-12`, in draft PR #4 (https://github.com/ysfclc/BusyBee/pull/4), based on PR #3. Tests cover malformed CSV rejection, duplicate suppression, evidence retention, import counts and rollback. No execution result exists yet.

## 4. Known gaps & stubs

- Provider-specific Uber/Bolt exports have not been validated against real user-owned sample files; current CSV is a documented interchange contract, not a provider adapter. Parser rejects duplicate/empty headers, extra cells and missing required values; optional trailing blanks remain unknown.
- Financial statement reconciliation, adjustment handling and correction workflows remain incomplete.
- Weekday, zone/corridor, duration-based hourly wage, waiting-time and dead-mileage metrics lack the required source fields/session model.
- CSV parser regression tests and SQLite store-level tests are authored but unexecuted; do not treat import/persistence integrity as verified yet.
- Road intelligence is not yet a provider-neutral connector/fusion system. Current mapped OSM observations and weather are not live traffic-speed coverage.
- Route planning uses low-volume public Nominatim/OSRM services and hands turn-by-turn guidance to external navigation; it is not a production SLA or live-traffic route engine.
- Community road-report moderation, expiry, reputation and shared backend are not complete.
- Real-device install, upgrade/signature continuity, battery/performance and accessibility checks are unverified.
- docs/SPEC.md is absent; docs/PRODUCT_CHARTER.md is the canonical product specification. docs/RISKS.md now records data integrity, privacy, source quality, safety, CI and release-signing risks. docs/STATE.md, docs/COVERAGE.md and root CHANGELOG.md now exist.
- The README checkpoint inconsistency found at session start was corrected to describe the five implemented pilot surfaces and the current limits of the map and offer capture.

## 5. Environment & how to run

- Rust tests: `cargo test --workspace --manifest-path rust/Cargo.toml` (run from repository root).
- Android unit tests: `cd android && ./gradlew --no-daemon testDebugUnitTest`.
- Android debug build: `cd android && ./gradlew --no-daemon assembleDebug`.
- CI build: `.github/workflows/build-signed-apk.yml` (Rust tests, Android unit tests, native Android Rust libraries, APK assembly; persistent signing secrets enable signed release output).
- Android baseline: Java 17, compile/target SDK 35, NDK 27.2.12479018, Gradle 8.9 in CI.
- A JDK and Kotlin compiler are present in the current container, but the repository checkout, Gradle project, Android SDK and Rust toolchain are not mounted. No local project build/test was executed this session.
- GitHub Actions run 47 (43146041937), run 48 attempt 1, and run 48 attempt 2 (manual retry at 13:16 UTC) all show failed jobs before runner assignment. Retry job 131208270290 has an empty step list; no test or build command ran. This is a pre-execution CI blocker; it is not a result from the source tests.

## 6. Next increments

1. Restore a runnable test environment without requiring paid GitHub-hosted minutes; then run Rust tests, Android unit tests (including Robolectric store tests), and APK assembly. Do not merge PR #3 or claim R-DM-001/R-DM-005 verified until these checks pass.
2. Validate the generic CSV contract against a real user-owned Uber or Bolt export and implement only evidence-backed provider adapters.
3. Validate the CSV contract against a real user-owned Uber or Bolt export; resolve duplicate/correction semantics before claiming statement reconciliation. Then choose one additional road-intelligence source for a provenance/freshness/failure-tested vertical slice.

## 7. Open questions for the human

- [ ] Which real export should be used first for adapter validation (Uber or Bolt)? Default: do not claim provider-specific support until a sample export is available and tested.
- [ ] Are the persistent Android release-signing secrets configured? Default: treat existing builds as debug-only until a signed workflow run verifies the certificate.
- [ ] Can GitHub Actions hosted runners be started for this private repository? Default: do not switch to a paid runner or merge unverified work.
