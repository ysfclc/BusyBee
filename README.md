# BusyBee — Driver Command Centre

BusyBee is an Android-native Driver Command Centre for independent drivers. It brings three connected capabilities into one private workspace:

1. **Driver Data Mirror** — evidence-backed offers, offer outcomes, trips, time, routes, earnings, fees, bonuses, vehicle costs and cross-platform work history.
2. **Road Intelligence & Navigation** — a full-screen spatial workspace combining map context, route planning, weather, road observations and opt-in driver/community information, with clear source freshness and confidence.
3. **Driver Operations** — vehicle profile, documents, expenses, insurance/payment reminders and driver-selected WhatsApp group intelligence.

The map is the default launch surface and uses the full display. It is the spatial workspace, not the whole product: the Mirror, capture/review workflow and driver operations remain equally core.

## Product contract

- [Canonical product contract](docs/PRODUCT_CONTRACT.md)
- [Map icon system and acceptance](docs/MAP_ICON_SYSTEM.md)
- [Map and workspace UX roadmap](docs/INTEGRATION_MAP_UX_ROADMAP.md)
- [Integration checkpoint and verification ledger](docs/INTEGRATION_CHECKPOINT_2026-11-16.md)
- [Native Provider Bridge design](docs/NATIVE_PROVIDER_BRIDGE.md)
- [Roadmap](docs/ROADMAP.md)
- [Phase log](docs/PHASE_LOG.md)
- [Android signing](docs/ANDROID_SIGNING.md)

## Current engineering checkpoint — 2026-11-27

The map-first/native-capture integration (PR #16) is merged into `main` at merge commit `954bf872d29ea3e43c23f80d615487c902dbc8cc`. It restores the BusyBee adaptive launcher icon, the lifecycle-safe MapLibre tab host, crash diagnostics, route/navigation surface, capture workflows and typed Provider Bridge persistence.

**Latest signed mainline APK:** GitHub release `build-627`, version `0.1.627`, package `com.ysfclc.busybee.drivercommandcentre`. The build passed actionlint, Rust tests/native builds, Android unit/Robolectric tests, the owned-app observer POC checks, release assembly, `apksigner` verification and checksum/provenance generation. Its release certificate matches the previous signed build #622. See [the build-627 release](https://github.com/ysfclc/BusyBee/releases/tag/build-627).

The mainline workflow now uses the existing `BUSYBEE_KEYSTORE_BASE64` and `BUSYBEE_KEYSTORE_PASSWORD` secrets, discovers the keystore alias, refuses to publish a debug APK as a mainline release, and checks the package/launcher resources before building. The launcher artwork guard prevents the missing-icon regression from silently returning.

**Data architecture on main:** `busybee_driver_mirror.db` (schema v4) contains imported driver evidence/records and the additive `provider_events` ledger. `busybee_command_centre.db` (schema v3) contains offers, notices, driver items, community/road data, settings and selected WhatsApp-group records. These are the two established production stores.

**Held work:** PR #29 remains draft. Its proposed `busybee_driver_activity.db` third database is still the active read source on that branch; its migration/consolidation is not complete and it has not passed physical-device parity checks. Do not merge it wholesale or replace the current stores. App Observer remains a separate research/POC line, not a production BusyBee provider integration.

**Device acceptance is still pending.** CI proves the source builds and the APK is signed; it does not prove that the app is stable on the target Xiaomi device, that every tab interaction survives repeated use, or that notification/accessibility/location permissions and background behavior work as expected. Do not uninstall the existing app to work around a signature error.

## Data and privacy boundaries

- No verified Uber or Bolt individual-driver API connection is claimed.
- Native app observation is read-only and explicitly enabled by the driver; provider decisions remain in the official app.
- Notifications are a secondary signal; manual screen OCR is a user-started fallback.
- CSV/statements remain optional historical backfill, not normal onboarding.
- Selected WhatsApp groups are opt-in and controllable per group.
- Unknown amounts, currencies, routes and durations remain unknown; they are never silently converted to zero.
- Event provenance and evidence are retained. The unified event timeline is not an authoritative cross-source reconciliation until conflict matching is implemented and tested.
