> **Current product requirements:** [Canonical Product Contract](docs/PRODUCT_CONTRACT.md). The latest implementation replaces notification-only/CSV-first/chat-export shortcuts with user-consented native driver-app capture, driver-selected WhatsApp groups, and a map-first workspace. This is an implementation candidate, not device-accepted product completion.

# BusyBee — Driver Command Centre

BusyBee is the product brand for **Driver Command Centre**: an Android-native driver intelligence platform combining a private cross-platform Driver Data Mirror with a provider-neutral Road Intelligence Aggregator. The map is the spatial interface, not the product itself.

The product vision is one map that aggregates all accessible and legally usable data sources: commercial traffic and route providers, public and municipal feeds, environmental data, Android/device observations and opt-in driver reports. It normalises evidence into one canonical intelligence layer while preserving source provenance, freshness, conflicts and licence constraints.

## Phase 1 — Product and architecture lock

- [Product charter](docs/PRODUCT_CHARTER.md)
- [BusyBee brand system](docs/BRAND.md)
- [System architecture](docs/ARCHITECTURE.md)
- [Source register](docs/SOURCE_REGISTER.md)
- [Delivery roadmap](docs/ROADMAP.md)
- [Architecture decisions](docs/DECISIONS.md)
- [Phase log](docs/PHASE_LOG.md)
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
- Current state: five Android surfaces — Map, Offers, Earnings, Driver Hub and Sources. The private Driver Data Mirror stores reviewed native-screen observations and optional historical imports with source evidence and duplicate protection. Normal use does not require CSV. The Map includes mapped road observations, weather context, destination routing and 3D buildings; it is not yet the complete provider-neutral live road-intelligence aggregator. Uber/Bolt provider API access is not claimed; the user-started Android capture bridge is the native fallback, with local OCR and manual review. See the [canonical product contract](docs/PRODUCT_CONTRACT.md), [optional import contract](docs/DRIVER_MIRROR_IMPORT.md) and [delivery roadmap](docs/ROADMAP.md).


## Android APK delivery

The Android app can be built and distributed through the dedicated Vercel project. See [Vercel APK build](docs/VERCEL_APK_BUILD.md) for the toolchain, protected download page, debug/release signing boundary and verification status. A build is not considered available until the Vercel deployment completes and its APK metadata is present.

## Current Android product surfaces

The current build replaces the POI-only experiment with five driver-facing surfaces:

- **Map:** full-screen spatial workspace, floating controls, mapped roadworks/hazards, private driver reports, weather context, destination routing and foreground turn-by-turn guidance.
- **Offers:** user-started Android app-window capture with on-device OCR and a review queue; Uber/Bolt are explicit capture targets, with additional named platforms supported where the Android picker allows them. Notifications are a secondary fallback. Manual correction and ranking use driver-controlled filters, pickup distance and vehicle cost.
- **Earnings:** private Driver Mirror for driver-confirmed trips and offers, source evidence, cautious PLN totals, vehicle-cost estimates, review inbox and data-coverage disclosures. Legacy CSV imports remain optional for backfill/reconciliation.
- **Driver Hub:** vehicle profile, documents, reminders, recurring insurance/payment schedules, completion history, expenses, service contacts and driver-selected WhatsApp group monitoring. Group visibility is limited to notification content exposed by Android; no private chat database or chat-export import is used.
- **Sources:** honest connected / permission-required / not-connected source catalogue.

The native capture workflow requires explicit Android system consent and user selection of the driver-app window; OCR runs locally, screenshots are not saved/uploaded, and every observation requires driver review. The app does not claim direct Uber/Bolt partner API access or auto-acceptance. It hands the driver back to the source app for the final decision. Road feed coverage is not equivalent to live traffic speed; source health and gaps remain visible. The current UX increment also keeps the map dock compact, makes Earnings comparisons horizontally browsable, and turns Offers/Driver Hub into purpose-built workflows. Sources still needs its dedicated redesign.


### Spatial rendering

The map now uses the OpenFreeMap Liberty vector style over OpenStreetMap-derived data. Liberty includes 3D building extrusions; the camera opens tilted at neighbourhood scale. This is 3D buildings and camera pitch, not 3D terrain. Map source attribution remains visible through the style.
