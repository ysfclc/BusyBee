> **Current product requirements:** [Canonical Product Contract](docs/PRODUCT_CONTRACT.md). The latest implementation replaces notification-only/CSV-first/chat-export shortcuts with user-consented native driver-app capture, driver-selected WhatsApp groups, and a map-first workspace. This is an implementation candidate, not device-accepted product completion.

# BusyBee — Driver Command Centre

BusyBee is the product brand for **Driver Command Centre**: an Android-native driver intelligence platform combining a private cross-platform Driver Data Mirror with a provider-neutral Road Intelligence Aggregator. The map is the spatial interface, not the product itself.

The product vision is one map that aggregates all accessible and legally usable data sources: commercial traffic and route providers, public and municipal feeds, environmental data, Android/device observations and opt-in driver reports. It normalises evidence into one canonical intelligence layer while preserving source provenance, freshness, conflicts and licence constraints.

## Phase 1 — Product and architecture lock

- [Product charter](docs/PRODUCT_CHARTER.md)
- [BusyBee brand system](docs/BRAND.md)
- [System architecture](docs/ARCHITECTURE.md)
- [Activity & Network Runtime research](docs/BUSYBEE_ACTIVITY_NETWORK_RUNTIME.md)
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
- Current state: five Android surfaces — Map, Offers, Earnings, Driver Hub and Sources. The private Driver Data Mirror stores reviewed observations from a package-scoped, explicitly enabled Android read-only UI listener, optional app notifications and a manual MediaProjection/OCR fallback. Normal use does not require CSV. New relevant driver-app observations/notifications can trigger local BusyBee review alerts. The map has solar-aware day/dusk/night map styles and mapped road/weather context, but does not yet have a licensed live traffic-speed feed. Uber/Bolt provider API access is not claimed; Android UI observation is limited to visible text exposed to accessibility and is not a provider API. See the [canonical product contract](docs/PRODUCT_CONTRACT.md), [optional import contract](docs/DRIVER_MIRROR_IMPORT.md) and [integration/map UX roadmap](docs/INTEGRATION_MAP_UX_ROADMAP.md).


## Android APK delivery

The Android app can be built and distributed through the dedicated Vercel project. See [Vercel APK build](docs/VERCEL_APK_BUILD.md) for the toolchain, protected download page, debug/release signing boundary and verification status. A build is not considered available until the Vercel deployment completes and its APK metadata is present.

## Current Android product surfaces

The current build replaces the POI-only experiment with five driver-facing surfaces:

- **Map:** full-screen spatial workspace, floating controls, solar-aware day/dusk/night base styles, mapped roadworks/hazards, private driver reports, weather context, destination routing and foreground turn-by-turn guidance. Live traffic speed is not yet integrated.
- **Offers:** explicitly enabled, read-only Android live UI observation for visible Uber/Bolt driver-app text, with package filtering, on-device parsing, a review queue and generic BusyBee alerts. Android notifications are a second signal. One-session MediaProjection with local OCR remains a manual fallback when app text is not exposed. Manual correction and ranking use driver-controlled filters, pickup distance and vehicle cost.
- **Earnings:** private Driver Mirror for driver-confirmed trips and offers, source evidence, cautious PLN totals, vehicle-cost estimates, review inbox and data-coverage disclosures. Legacy CSV imports remain optional for backfill/reconciliation.
- **Driver Hub:** vehicle profile, documents, reminders, recurring insurance/payment schedules, completion history, expenses, service contacts and driver-selected WhatsApp group monitoring. Group visibility is limited to notification content exposed by Android; no private chat database or chat-export import is used.
- **Sources:** honest connected / permission-required / not-connected source catalogue.

The current live observer is a read-only Android AccessibilityService enabled by the driver in Android Settings after an in-app disclosure. Native UsageStatsManager activity tracking and app-scoped VpnService network-flow observation are documented as a future internal BusyBee runtime investigation, not implemented or connected yet. It only observes visible UI text from the configured Uber Driver and Bolt Driver packages; it does not tap controls, accept/decline rides, inspect other apps, read private databases, record audio or save screenshots. It may miss custom/secure app surfaces and is not an official provider API connection. New relevant observations and ride-app notifications can trigger generic BusyBee notifications that deep-link to review. Manual MediaProjection/OCR is retained only as a fallback. The app does not claim direct Uber/Bolt API access or automatic ride actions. Road feed coverage is not equivalent to live traffic speed; source health and gaps remain visible. Day/dusk/night themes follow solar position at the last permitted location with a local-clock fallback. Sources documents the current permission and connection state; live traffic connectors remain unconnected.


### Spatial rendering

The map now uses the OpenFreeMap Liberty vector style over OpenStreetMap-derived data. Liberty includes 3D building extrusions; the camera opens tilted at neighbourhood scale. This is 3D buildings and camera pitch, not 3D terrain. Map source attribution remains visible through the style.
