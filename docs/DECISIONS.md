# Architecture Decision Record

## ADR-001 — Aggregate providers; do not select one map vendor

**Status:** Accepted — Phase 1  
**Decision:** Build a provider-neutral connector framework. Integrate all sources that are technically accessible and legally usable. No single commercial provider is the system of record for all map, traffic, incident and routing data.

**Reason:** Providers have different geographic strengths, update cadences, event categories, licensing terms and route behaviour. A single-provider design would unnecessarily cap coverage and create lock-in.

**Consequences:** We must implement canonical schemas, source provenance, entity resolution, conflict handling, licensing controls and independent connector health. This is deliberate complexity and a core product capability.

## ADR-002 — One canonical intelligence layer, many source-specific adapters

**Status:** Accepted — Phase 1  
**Decision:** Keep provider-native payloads at the connector boundary and normalise into canonical observations/events. Preserve source-specific fields and evidence links.

**Reason:** Flattening data too early loses useful detail; rendering provider data directly makes the UI provider-dependent.

## ADR-003 — Rust domain core, Kotlin Android shell

**Status:** Accepted — Phase 1  
**Decision:** Rust owns deterministic domain logic; Kotlin owns Android platform access, lifecycle, permissions and UI.

**Reason:** The core should be testable and reusable without coupling business logic to Android APIs. Android lifecycle and permissions still require native implementation.

## ADR-004 — Renderer independent of route and data providers

**Status:** Accepted — Phase 1  
**Decision:** Evaluate MapLibre Native as the first renderer candidate. The renderer must not determine which traffic, incident or route connectors can be integrated.

**Reason:** Rendering, base-map licensing, routing and live intelligence are separate concerns.

## ADR-005 — Evidence, freshness and licence are first-class data

**Status:** Accepted — Phase 1  
**Decision:** Every canonical event retains provenance, observation times, spatial precision, evidence links, confidence rationale and retention classification.

**Reason:** An attractive map with stale, duplicated or untraceable information is not trustworthy.

## ADR-006 — Local-first personal data; community sharing is opt-in

**Status:** Accepted — Phase 1  
**Decision:** Personal trips, earnings, location history and vehicle data remain private by default. Community reports are separate, explicit contributions.

**Reason:** The product must not expose personal driver behaviour to other drivers or a central service without a clear purpose and consent.

## ADR-007 — Progressive 3D, not a city-model-first project

**Status:** Accepted — Phase 1  
**Decision:** 3D camera presentation is the intended default visual direction, but complete city reconstruction is not a launch dependency.

**Reason:** Road topology, event quality and safe navigation create more value than an expensive visual model with unreliable underlying data.

## ADR-008 — Safety-aware police information

**Status:** Accepted — Phase 1  
**Decision:** Any lawfully available police-related information is contextual awareness only, with source and timestamp. No enforcement-evasion routing or checkpoint avoidance.

**Reason:** The map should represent road conditions and safety context, not facilitate evasion.


## ADR-009 — Native driver-app integration is primary

**Status:** Accepted — 2026-11-13

**Decision:** BusyBee's primary Driver Mirror ingestion path is approved provider APIs and/or explicit user-initiated Android-native integration with on-device processing and review. Notification capture is a secondary fallback. CSV import is optional backfill/reconciliation only.

**Reason:** The product must use data from the driver's actual Uber, Bolt and other driver apps; requiring CSV upload or relying only on notifications does not satisfy the agreed product.

**Consequences:** Implement independent per-platform adapters and capability states. Respect provider approval, Android consent and app security. Do not access private databases, undocumented endpoints or automate ride actions. Validate real app screens and API entitlements before claiming connected status.

## ADR-010 — WhatsApp tracking is selected-group based

**Status:** Accepted — 2026-11-13

**Decision:** Discover groups from notification-visible metadata and let the driver individually enable, inspect, pause and delete group data. Do not require chat-export files.

**Reason:** The intended workflow is to choose the driver's groups to track, not import entire chat histories.

**Consequences:** Persist message previews only for explicitly enabled groups. Android/WhatsApp notification visibility is incomplete and must be stated; private chat database access is prohibited.

## ADR-011 — Full-screen map-first workspace and bespoke surfaces

**Status:** Accepted — 2026-11-13

**Decision:** Map is the default full-screen spatial workspace with floating controls and context-sensitive overlays. Other primary surfaces must be designed for their workflows rather than reusing a generic stacked-card dashboard.

**Reason:** The map is central to the Driver Command Centre, and visual hierarchy must support drivers rather than present a dense generic dashboard.

**Consequences:** A palette update or padding changes do not count as redesign. Premium redesign and target-device validation remain explicit acceptance gates.
