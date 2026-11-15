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

## ADR-009 — Provider activity enters through source adapters; observation and action remain separate

**Status:** Accepted as an architecture boundary; provider-specific feasibility remains under investigation — 2026-11-15.

**Decision:**
- Keep provider activity acquisition behind independent adapters and feed the existing local-first Driver Data Mirror.
- Preserve the user's explicit research requirement: investigate plaintext request observation before TLS encryption and response observation after TLS decryption, inside an authorised application process.
- Do not conflate this with VPN packet metadata, notifications, screen capture or OCR. Each is a different source capability with different completeness.
- First validate the plaintext-observation concept in an owned Android test application. Then assess documented/provider-permitted access paths separately for Uber and Bolt.
- Do not make root, modified/re-signed third-party APKs, bypassing provider protections, or request replay a production dependency.
- Keep observation, normalisation, offer ranking and provider-side actions as separate capabilities. Initial product flow is observe → normalise → explain → hand back to the official app.

**Reason:** The plaintext boundary is technically observable in controlled instrumentation environments, but compatibility, permission, stability and data rights for current production ride-hailing apps are unproven. A source-neutral adapter boundary allows the product to progress without pretending that a research technique is a supported provider API.

**Consequences:**
- The canonical domain model must not depend on Frida, TLS internals, OkHttp, notification APIs or provider UI layout.
- Every observation carries source method, event/receive timestamps, field completeness, provenance and retention classification.
- Secrets and unrelated payload fields must be redacted before persistence.
- Unsupported versions, stale offers, missed events and conflicting evidence must be visible.
- Provider-side actions remain in the official app unless a documented integration and separate security/product decision explicitly authorise them.

**Design record:** [Platform Activity Integration — Research and Design](PLATFORM_ACTIVITY_INTEGRATION.md).
