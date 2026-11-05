# Driver Command Centre — Product Charter

**Status:** Phase 1 — product direction locked  
**Pilot geography:** Kraków, Poland  
**Primary platform:** Android  
**Working product name:** Driver Command Centre (name is not a branding decision)

## 1. The product

Build one driver-facing intelligence map that aggregates every useful, accessible and legally usable road, traffic, navigation, safety, city, environmental, vehicle and driver-network data source into one coherent experience.

This is an **aggregator of aggregators**. It is not a new map provider, not a wrapper around one navigation SDK, and not a single-provider traffic app. No commercial provider gets architectural ownership of the product.

The map is the shared surface. Behind it, independent connectors ingest source data in parallel; a common intelligence layer reconciles overlapping and conflicting observations; the app renders one understandable live picture.

## 2. What "all possible data" means

We actively catalogue and integrate all viable source classes:

- Commercial map, traffic, incident, routing and place providers.
- Public and municipal road, transport, closure, construction and infrastructure datasets.
- Weather, air-quality, flood, event, parking and public-transport feeds.
- Android location, motion, camera and vehicle signals where the user grants permission.
- User-visible ride-hailing information where Android exposes it and platform rules allow it.
- Driver-submitted observations and opt-in community intelligence.

"All possible" means broad, provider-neutral coverage and an extensible connector system. It does **not** mean scraping private app databases, bypassing authentication, violating terms, or pretending that partner-only feeds are public APIs. Every source must have an explicit access, licence, retention and redistribution status.

## 3. Product outcomes

1. **One map, many sources:** no forced choice between TomTom, HERE, Google, Mapbox, Esri, Waze, OSM, government feeds, city feeds or our own observations.
2. **A trustworthy road picture:** every material event carries its source, observation time, location precision, freshness and confidence rationale.
3. **Useful navigation:** compare viable routes and provider estimates; give clear manoeuvres and immediate hazard awareness without inventing lane-level precision.
4. **Driver economics:** make demand, route history, time, platform outcomes and vehicle costs understandable in geographic context.
5. **Community intelligence:** let drivers contribute useful reports with consent, moderation, expiry and reputation safeguards.
6. **Progressive 3D:** make the map feel living and spatial, with 3D camera presentation as the default visual direction. Do not make a complete photorealistic 3D city model a launch dependency.

## 4. Equal product priorities, context-aware presentation

Navigation, safety awareness and driver economics are all first-class product goals. They must not all compete for attention at the same moment.

While moving, the interface prioritises the next manoeuvre, immediate hazards and essential safety information. Earnings zones, analytics and source inspection remain available but are visually quieter. When parked, the user can explore all layers and inspect the full intelligence picture.

Police-related information, if lawfully available, is limited to general situational awareness with source, timestamp and uncertainty. The product must not provide enforcement-evasion routing or help users avoid checkpoints. Actual road hazards, closures and traffic restrictions can inform routing.

## 5. Non-goals and hard boundaries

- Do not build a replacement for every commercial map or routing engine.
- Do not choose one provider as the exclusive data backbone.
- Do not assume data displayed by a consumer app is available through an API.
- Do not export or persist data from a provider SDK unless its licence explicitly permits it.
- Do not claim GPS-only lane-level positioning.
- Do not infer a confirmed road fact from one uncertain camera or community observation.
- Do not automate acceptance of ride offers or actions in third-party apps.
- Do not expose one driver's earnings, trips or location history to the community by default.
- Do not make the driving screen a dense dashboard.

## 6. First pilot

Start in Kraków with a source-coverage baseline and a small, testable road-intelligence slice. The initial build should prove the full pipeline:

**source → raw observation → canonical event → spatial match → evidence assessment → map overlay → expiry/update**

The first release is successful when users can see where each displayed event came from, when it was observed, whether it is stale, and why the system considers it relevant. Source count alone is not success.

## 7. Product quality rules

- Source provenance is mandatory.
- Staleness and expiry are explicit.
- Conflicts are retained, not silently averaged away.
- Missing data is visible as missing, never fabricated.
- Every connector can fail independently.
- The map remains usable when any single provider is unavailable.
- Personal data is private by default.
- Driving interactions are glanceable, accessible and safe.
- Every phase ends with a working, verifiable checkpoint.
