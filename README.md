# BusyBee — Driver Command Centre

BusyBee is an Android-native Driver Command Centre for independent drivers. It brings three connected capabilities into one private workspace:

1. **Driver Data Mirror** — evidence-backed offers, offer outcomes, trips, time, routes, earnings, fees, bonuses, vehicle costs and cross-platform work history.
2. **Road Intelligence & Navigation** — a full-screen spatial workspace combining map context, route planning, weather, road observations and opt-in driver/community information, with clear source freshness and confidence.
3. **Driver Operations** — vehicle profile, documents, expenses, insurance/payment reminders and driver-selected WhatsApp group intelligence.

The map is the default launch surface and uses the full display. It is the spatial workspace, not the whole product: the Mirror, capture/review workflow and driver operations remain equally core.

## Product contract

- [Canonical product contract](docs/PRODUCT_CONTRACT.md)
- [Map and workspace UX roadmap](docs/INTEGRATION_MAP_UX_ROADMAP.md)
- [Integration checkpoint and verification ledger](docs/INTEGRATION_CHECKPOINT_2026-11-16.md)
- [Native Provider Bridge design](docs/NATIVE_PROVIDER_BRIDGE.md)
- [Roadmap](docs/ROADMAP.md)
- [Phase log](docs/PHASE_LOG.md)
- [Android signing](docs/ANDROID_SIGNING.md)

## Current engineering checkpoint

The integration candidate combines the map-first/native-capture feature lineage with the typed Provider Bridge and its durable local event ledger. The candidate preserves the existing app ID and existing database names. The Provider Bridge uses an additive schema migration in `busybee_driver_mirror.db`; it does not introduce a third database.

**Verification is deliberately separate from implementation.** The candidate has passed Rust tests, Android unit/Robolectric tests, debug APK assembly/signature verification, and the owned-app POC test/build step in GitHub Actions. Persistent release signing is not configured, so the generated .debug package cannot update the installed release app. Target-phone acceptance remains pending. See the integration checkpoint for exact SHAs, known limits and next acceptance gates.

## Data and privacy boundaries

- No verified Uber or Bolt individual-driver API connection is claimed.
- Native app observation is read-only and explicitly enabled by the driver; provider decisions remain in the official app.
- Notifications are a secondary signal; manual screen OCR is a user-started fallback.
- CSV/statements remain optional historical backfill, not normal onboarding.
- Selected WhatsApp groups are opt-in and controllable per group.
- Unknown amounts, currencies, routes and durations remain unknown; they are never silently converted to zero.
- Event provenance and evidence are retained. The unified event timeline is not an authoritative cross-source reconciliation until conflict matching is implemented and tested.
