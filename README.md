# BusyBee — Driver Command Centre

BusyBee is the control repository and project delivery ledger for **Driver Command Centre**: an Android-native, provider-neutral road and driver intelligence map.

The product vision is one map that aggregates all accessible and legally usable data sources: commercial traffic and route providers, public and municipal feeds, environmental data, Android/device observations and opt-in driver reports. It normalises evidence into one canonical intelligence layer while preserving source provenance, freshness, conflicts and licence constraints.

## Phase 1 — Product and architecture lock

- [Product charter](docs/PRODUCT_CHARTER.md)
- [System architecture](docs/ARCHITECTURE.md)
- [Source register](docs/SOURCE_REGISTER.md)
- [Delivery roadmap](docs/ROADMAP.md)
- [Architecture decisions](docs/DECISIONS.md)
- [Phase log](docs/PHASE_LOG.md)
- [Control repository setup](SETUP.md)

## Delivery model

BusyBee is a phase-import control repository. Each `incoming/<name>.zip` is a complete snapshot imported into a private target repository named `<name>`. The import pipeline preserves target Git history, records provenance, scans and normalises the archive, and builds an Android APK when a Rust workspace and Android app are both present.

**Important:** every phase archive must contain the entire current project. It is not a patch mechanism.

## Current checkpoint

- Phase: 1 complete — Product and architecture lock
- Product: Driver Command Centre
- Pilot: Kraków, Poland
- Client: Android / Kotlin
- Domain core: Rust
- Provider strategy: multi-source aggregator; no exclusive map vendor
- Current state: Phase 1 merged; Phase 2 executable foundation is next. The first valid project import will run the corrected actionlint gate before any repository creation.
