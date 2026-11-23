# BusyBee — Driver Command Centre

BusyBee is the control repository and project delivery ledger for **Driver Command Centre**: an Android-native driver intelligence platform combining a private cross-platform Driver Data Mirror with a provider-neutral Road Intelligence Aggregator. The map is the spatial interface, not the product itself.

The product vision is one map that aggregates all accessible and legally usable data sources: commercial traffic and route providers, public and municipal feeds, environmental data, Android/device observations and opt-in driver reports. It normalises evidence into one canonical intelligence layer while preserving source provenance, freshness, conflicts and licence constraints.

## Phase 1 — Product and architecture lock

- [Product charter](docs/PRODUCT_CHARTER.md)
- [System architecture](docs/ARCHITECTURE.md)
- [Source register](docs/SOURCE_REGISTER.md)
- [Delivery roadmap](docs/ROADMAP.md)
- [Architecture decisions](docs/DECISIONS.md)
- [Phase log](docs/PHASE_LOG.md)
- [Platform Activity Integration research and design](docs/PLATFORM_ACTIVITY_INTEGRATION.md)
- [Native Provider Bridge design](docs/NATIVE_PROVIDER_BRIDGE.md)
- [Plaintext Observer proof-of-concept design](docs/PLAINTEXT_OBSERVER_POC.md)
- [Phase 2 executable foundation](docs/PHASE_2_FOUNDATION.md)
- [Android release signing](docs/ANDROID_SIGNING.md)
- [Control repository setup](SETUP.md)

## Delivery model

BusyBee is a phase-import control repository. Each `incoming/<name>.zip` is a complete snapshot imported into a private target repository named `<name>`. The import pipeline preserves target Git history, records provenance, scans and normalises the archive, and builds an Android APK when a Rust workspace and Android app are both present.

**Important:** every phase archive must contain the entire current project. It is not a patch mechanism.

## Current checkpoint

- Phase: 3 — Driver Data Mirror (first vertical slice implemented; real export adapters and CI/device verification pending)
- Product: Driver Command Centre
- Pilot: Kraków, Poland
- Client: Android / Kotlin
- Domain core: Rust
- Provider strategy: multi-source aggregator; no exclusive map vendor
- Current state: the Android app now opens into a local-first Driver Data Mirror with CSV import, a private SQLite source ledger, raw-row evidence, duplicate protection, platform activity and cautious financial totals. It does not claim direct Uber/Bolt API access. The old map/POI screen has been removed from this milestone; road intelligence returns after the mirror and driver economics are proven. See [CSV import contract](docs/DRIVER_MIRROR_IMPORT.md).
- Provider integration direction: a native-feeling internal Provider Bridge API will unify provider activity inside BusyBee. Current data is still partial (notifications + imports); no verified direct Uber/Bolt API connection exists. See [Native Provider Bridge](docs/NATIVE_PROVIDER_BRIDGE.md), [process-observation research](docs/PLATFORM_ACTIVITY_INTEGRATION.md) and [owned-app POC](docs/PLAINTEXT_OBSERVER_POC.md).

## Current Android product surfaces

The current build has six driver-facing surfaces:

- **Map:** tilted spatial map, mapped roadworks/hazards, local driver reports, weather context, destination routing and handoff to turn-by-turn navigation.
- **Offers:** opt-in notification capture for supported ride apps, manual offer review, driver-controlled filters, and ranking by estimated net PLN/hour after pickup distance and vehicle cost.
- **Activity:** unified local timeline of source observations and canonical provider events, with provider/source filters and evidence inspection. Manually entered or notification-reviewed offers emit typed events; raw notifications remain unclassified until reviewed.
- **Earnings:** private imported trip history and cautious PLN totals.
- **Driver Hub:** documents, due-date reminders, recurring insurance/payment schedules, completion history, expenses, service contacts and local WhatsApp export search.
- **Sources:** explicit Uber Driver / Bolt Driver app handoff cards, per-provider local data coverage, notification permission state, source gaps and migration diagnostics. App presence and notification permission remain separate from a verified provider API connection.

The offer workflow does not claim direct Uber/Bolt partner API access or auto-acceptance. It surfaces captured information and hands the driver back to the source app for the final decision. Road feed coverage is not equivalent to live traffic speed; source health and gaps remain visible.

### Spatial rendering

The map now uses the OpenFreeMap Liberty vector style over OpenStreetMap-derived data. Liberty includes 3D building extrusions; the camera opens tilted at neighbourhood scale. This is 3D buildings and camera pitch, not 3D terrain. Map source attribution remains visible through the style.
