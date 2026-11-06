# Delivery Roadmap

**Rule:** one phase at a time. Each phase must deliver a usable product capability, not just infrastructure or a visual demo.

## Product north star

BusyBee is a driver intelligence platform, not a POI map. It has two core data systems:

1. **Private Driver Data Mirror / Digital Twin:** reconstruct the driver's work across Uber, Bolt and other platforms — trips, offers, earnings, fees, routes, time, vehicle costs and outcomes.
2. **Provider-neutral Road Intelligence Aggregator:** combine permitted commercial, public, municipal, environmental, Android and opt-in driver/community sources into one evidence-backed road picture.

The map is the spatial interface for these systems. Base-map rendering and generic nearby POIs are supporting infrastructure, not product milestones.

## Phase 1 — Product and architecture lock

**Status:** complete.

- Product charter, canonical model, architecture, source register and privacy/licensing rules.
- Provider-neutral connectors and evidence provenance.
- Personal data separated from shared/public intelligence.

## Phase 2 — Executable foundation

**Status:** foundation exists; not a product-complete milestone.

- Android app shell, Rust domain core, map renderer, Kotlin/Rust bridge and CI.
- Current map + generic OSM amenity pins are explicitly a temporary renderer/data-fetch smoke test.
- They must not be mistaken for the intended product or used as the basis for declaring a feature complete.

**Exit gate:** install/launch on device, persistent release signing and local storage/migration baseline verified.

## Phase 3 — Driver Data Mirror (first real product capability)

**Status:** first usable vertical slice implemented. Real provider-export validation, statement reconciliation and completeness review with the driver's own data remain open.

**Goal:** make BusyBee understand the driver's actual work, across platforms, under the driver's control.

**Deliverables**
- Local-first source ledger with provenance, timestamps, completeness and permissions.
- User-authorised capture/import paths for Uber and Bolt activity: notifications where exposed, statements/exports, user-selected files and guided user-initiated capture where permitted.
- Canonical records for platforms, sessions, offers, trips, route/time, gross earnings, fees, bonuses, adjustments and vehicle costs.
- Reconciliation and duplicate detection without silently inventing missing values.
- Driver profile and vehicle cost model; private data stays private by default.

**Exit gate**
- Import/capture a representative set of real user records from at least one platform and show the original evidence beside the normalised records.
- Cross-check totals against the source statement.
- Clearly show unknowns, gaps, duplicate candidates and confidence; never treat missing values as zero.

## Phase 4 — Driver intelligence and decision engine

**Status:** initial platform comparison, start-hour grouping and per-kilometre vehicle-cost estimates implemented. Weekday/zone/route economics, duration-based hourly earnings and dead-mileage analysis remain blocked on additional source fields.

**Goal:** turn the mirror into answers and recommendations a working driver can use.

**Deliverables**
- Net earnings after platform fees and configurable vehicle costs.
- Earnings by hour, weekday, zone, route and platform.
- Paid distance vs empty/dead mileage, time utilisation and effective hourly rate.
- Uber/Bolt/platform comparisons and offer outcome history.
- Explainable recommendations for working times/zones and platform choice, with evidence and coverage limits.
- Geographic visualisation of the driver's own activity and outcomes.

**Exit gate**
- Every metric is reproducible from underlying records.
- Every recommendation states why it was made and what data is missing.
- User can correct source records and see derived metrics update.

## Phase 5 — Road Intelligence Aggregator

**Goal:** fuse multiple independent sources into a trustworthy live road picture.

**Deliverables**
- Connector registry/SDK and independent connector health.
- Kraków public/municipal roadworks, restrictions, transit and relevant city feeds where accessible.
- Commercial provider capability trials (e.g. TomTom, HERE, Mapbox, Google Navigation SDK) without exclusive vendor lock-in.
- Weather, visibility, air-quality and other relevant context.
- Canonical road events, spatial matching, evidence fusion, deduplication, contradiction handling, expiry and source attribution.
- Source coverage and freshness view; explicit unavailable vs no-event states.

**Exit gate**
- At least two independent sources feed one canonical event capability where legally possible.
- User can inspect provenance, observation time, freshness and confidence rationale.
- Duplicate, contradictory, delayed and expired observations are handled by tests.

## Phase 6 — Unified spatial intelligence and navigation

**Goal:** combine the private driver mirror with public road intelligence in one spatial experience.

**Deliverables**
- One map with selectable layers for navigation, hazards, roadworks, safety awareness, context, driver economics and personal history.
- Context-aware drive/explore/driver-intelligence modes.
- Multi-provider route candidates and comparison where licensed and available.
- 3D camera presentation by default; progressive buildings/terrain enrichment.
- Voice and glanceable driving interface; immediate hazards take priority while moving.
- Police-related information only as lawful general awareness; never enforcement-evasion routing.

**Exit gate**
- Real Kraków route tests validate route guidance, hazard relevance and driving usability.
- Private driver records never leak into shared layers.
- The map remains useful under individual provider outages.

## Phase 7 — Driver network and advanced capabilities

**Deliverables**
- Consent-based driver reports, moderation, reputation and expiry.
- Optional vehicle/OBD-II and sensor signals.
- Further platform connectors and source coverage.
- Privacy-preserving aggregated insights only with explicit opt-in.
- Performance, battery, security, accessibility and operational hardening.

**Exit gate**
- Shared reports are traceable, moderated, time-bounded and separated from private data.
- Security/privacy review and real-world pilot acceptance pass.

## Non-goals

- A map that merely displays nearby amenities.
- A single-provider map or traffic wrapper.
- A generic dashboard with manually entered earnings as its only intelligence.
- Fake live data, fabricated demand heatmaps or unsupported claims of Uber/Bolt API access.
- Scraping private databases, bypassing authentication, or violating provider terms.

## Phase discipline

- Each phase is committed as a complete checkpoint.
- Keep a phase log and record decisions/deviations.
- Never replace a working project snapshot with an incomplete ZIP.
- Do not upload secrets, signing keys, local configuration, generated APKs or build caches.
- Build failures are fixed before moving to the next phase.
