# State Ledger
_Last updated: 2026-11-12 by ChatGPT — session 1_

## 1. Product snapshot

BusyBee is the control and delivery repository for Driver Command Centre, an Android-native driver-intelligence product piloting in Kraków. It combines a private, local-first Driver Data Mirror for cross-platform activity and economics with a provider-neutral road-intelligence and navigation experience. The map is a spatial interface to those capabilities, not the product by itself. Kotlin owns Android UI and platform integration; Rust is the deterministic domain core. Completeness means evidence-backed records, honest source coverage, safe driver-facing UX, and end-to-end tested capabilities—not a populated demo map.

## 2. Status board

| Area | State | Notes |
|---|---|---|
| Product charter / architecture | ✅ baseline | Product direction, source register and ADR-001–008 are committed |
| Android application shell | ✅ baseline | Stable package ID; Kotlin app and MapLibre integration |
| Spatial map / routing | 🟡 partial | Tilted Liberty vector map, mapped road observations, weather context and OSRM alternatives; not a complete multi-source live traffic aggregator |
| Driver Data Mirror import | 🟡 partial | Provider-neutral CSV import into private SQLite; provider-specific exports and statement reconciliation are unvalidated |
| Mirror evidence / deduplication | 🟡 partial | Raw rows retained; canonical fingerprints prevent exact duplicate financial rows |
| Driver economics | 🟡 partial | Platform and start-hour groupings; no validated hourly wage, weekday/zone or dead-mileage analysis |
| Offer Desk | 🟡 partial | Manual entry and opt-in notification capture/ranking; no direct Uber/Bolt partner API or auto-acceptance |
| Driver Hub | 🟡 partial | Documents, reminders, recurrence, expense/contact and WhatsApp TXT import/search surfaces |
| Source catalogue | 🟡 partial | Honest access/status catalogue; broad connector framework and live feed-health are not complete |
| Rust domain core | 🟡 partial | Road-event model, TTL logic and JNI version bridge; canonical fusion engine is not implemented |
| CI / APK | 🟡 verified in CI | Phase log records successful Rust/Android tests and debug APK builds through run 46; no confirmed real-device install/launch |
| Release signing | ⚪ pending | Persistent release keystore secrets were not configured in the last documented successful build |

## 3. Currently in flight

- R-DM-005 — make headline earnings totals include recognised trip records only, excluding offers and other activity; add unit tests and update product docs. Working branch: `fix/trip-only-earnings-2026-11-12`.

## 4. Known gaps & stubs

- Provider-specific Uber/Bolt exports have not been validated against real user-owned sample files; current CSV is a documented interchange contract, not a provider adapter.
- Financial statement reconciliation, adjustment handling and correction workflows remain incomplete.
- Weekday, zone/corridor, duration-based hourly wage, waiting-time and dead-mileage metrics lack the required source fields/session model.
- Road intelligence is not yet a provider-neutral connector/fusion system. Current mapped OSM observations and weather are not live traffic-speed coverage.
- Route planning uses low-volume public Nominatim/OSRM services and hands turn-by-turn guidance to external navigation; it is not a production SLA or live-traffic route engine.
- Community road-report moderation, expiry, reputation and shared backend are not complete.
- Real-device install, upgrade/signature continuity, battery/performance and accessibility checks are unverified.
- Separate `docs/SPEC.md`, `docs/RISKS.md`, `docs/STATE.md` and root `CHANGELOG.md` were absent at session start. `docs/PRODUCT_CHARTER.md` is the current product specification; this ledger and a changelog are being established. No coverage matrix existed.
- README's earlier current-checkpoint paragraph says the old map/POI screen was removed and road intelligence deferred, but the current `MainActivity.kt` and later Phase Log entries include map, offers and Driver Hub surfaces. README needs reconciliation.

## 5. Environment & how to run

- Rust tests: `cargo test --workspace --manifest-path rust/Cargo.toml` (run from repository root).
- Android unit tests: `cd android && ./gradlew --no-daemon testDebugUnitTest`.
- Android debug build: `cd android && ./gradlew --no-daemon assembleDebug`.
- CI build: `.github/workflows/build-signed-apk.yml` (Rust tests, Android unit tests, native Android Rust libraries, APK assembly; persistent signing secrets enable signed release output).
- Android baseline: Java 17, compile/target SDK 35, NDK 27.2.12479018, Gradle 8.9 in CI.
- Local repository checkout/build tools were not mounted in this session; source changes are made through the connected GitHub repository and CI is the executable verification path.

## 6. Next three increments

1. Complete and verify R-DM-005 trip-only headline earnings aggregation.
2. Validate the generic CSV contract against real user-owned Uber/Bolt exports and implement only evidence-backed adapters.
3. Prove one additional road-intelligence source end-to-end with provenance, freshness, licence handling and failure tests.

## 7. Open questions for the human

- [ ] Which real export should be used first for adapter validation (Uber or Bolt)? Default: do not claim provider-specific support until a sample export is available and tested.
- [ ] Are the persistent Android release-signing secrets configured? Default: treat the latest known APK as debug-only until a signed workflow run verifies the certificate.
