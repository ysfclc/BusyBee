# BusyBee — Observation-to-Event Pipeline

**Date:** 2026-11-21  
**Status:** Product/architecture correction and implementation contract  
**Owner product:** BusyBee Driver Command Centre  
**Research instrument:** App Observer (temporary; not a product surface)

## 1. Product objective

BusyBee is the product. App Observer is a temporary engineering instrument used to develop and validate the observation, interpretation and classification capabilities that BusyBee needs to ingest activity from other apps.

The goal is not to ship a standalone app-understanding product or build a complete abstract model of every app. The goal is to turn permitted observations of external applications and other sources into useful, provider-neutral, evidence-backed BusyBee events.

**Observations are evidence. Classified events are the output. BusyBee's canonical domain model is the contract.**

The observer may capture screenshots, OCR/layout cues, Accessibility structure and events, user interactions, permitted notification content/metadata and limited network metadata. These are complementary sensors. They must be fused while retaining their source identity, limitations, permissions and uncertainty.

## 2. End-to-end system

```text
External apps / Android / imports / public feeds / driver reports
                              |
                     Source adapters
                              |
                    Observation ledger
                              |
                Perception + evidence fusion
                              |
              Event interpretation/classification
                              |
             Validation / identity / deduplication
                              |
                 BusyBee canonical events
                              |
             Driver Mirror / Offer Desk / analytics
                              |
                   BusyBee user experience
```

The observation/perception implementation may initially live in the separate App Observer research app because that is a convenient test harness. Its reusable components and contracts must be designed for integration into BusyBee's Android client. The research app itself is not a product dependency and must not become a second permanent product.

## 3. Current BusyBee baseline (verified 2026-11-21)

The repository already has useful pieces, but they are separate storage/workflow paths rather than one canonical event pipeline:

- `PlatformNotificationListener` recognises a fixed set of ride-app package names and forwards notification title/body directly to `DriverCommandStore.captureNotice`.
- `DriverCommandStore` stores notices in a dedicated `notices` table with a content fingerprint. Offers are stored separately in `offers`.
- `DriverMirrorStore` stores imported CSV rows and their raw evidence separately in `raw_evidence` and `records`.
- `road-core` currently exposes a small `RoadEvent` type and freshness check; it does not yet define the driver observation envelope, driver activity lifecycle events, evidence links or event reconciliation.

Therefore the first integration is not to add more screen understanding to App Observer. It is to establish a shared driver observation/event contract and route one source through it without losing the existing evidence. The existing notification, manual-offer and CSV paths must be migrated incrementally; do not delete or silently reinterpret their existing records.

## 4. Pipeline responsibilities

### 4.1 Source adapters

Each adapter describes what it can actually observe and how:

- Provider/API integration where documented and authorised.
- User-owned CSV/statement imports.
- Android notification listener, with explicit permission and payload minimisation.
- User-initiated or explicitly enabled screen capture and OCR.
- Accessibility-visible structure and interaction events, subject to Android policy and clear user controls.
- Network metadata only for transport timing/shape; ordinary TLS packet metadata is not plaintext application data.
- Controlled, owned-app instrumentation for research only.

Adapters must not claim capabilities they do not have. An adapter declares provider/package, acquisition method, version, capabilities, permission state, coverage gaps, timestamps and retention/privacy class.

### 4.2 Observation ledger

An observation is an immutable evidence record, not a business fact.

Minimum envelope:
- observation ID and schema version;
- source/adapter ID, provider/package and adapter version;
- acquisition method and permission/consent context;
- observed time, received time and monotonic sequence/time where available;
- correlation/session ID and target app identity;
- evidence type and structured payload or private evidence reference;
- field-level availability/redaction information;
- provenance, quality limitations and retention classification.

Keep raw evidence separate from derived interpretation. Persist only data permitted by source terms and the user's choices. Do not retain passwords, typed secrets, tokens, cookies, payment credentials or unrelated screen contents. OCR raw text is ephemeral by default; explicitly authorised content must carry its rule/permission provenance.

### 4.3 Perception and evidence fusion

Convert observations into generic evidence primitives: text regions/shapes, UI elements, controls, containers, images, layout geometry, interaction signals and source activity. A scene graph is an intermediate representation that relates these primitives within an observation.

Temporal identity, relation matching and screen-state inference are supporting techniques. Implement them only where they improve event extraction, deduplication or outcome verification. They are not independent product milestones.

Fuse sources without adding arbitrary confidence scores. Preserve disagreement, missing sensors, stale evidence and unknowns. Time proximity alone does not prove causality.

### 4.4 Event interpretation

Interpret evidence into provider-neutral event candidates. Initial driver activity lifecycle includes:

- `OfferObserved`
- `OfferUpdated`
- `OfferExpired`
- `OfferDecisionObserved`
- `TripStarted`
- `TripUpdated`
- `TripCompleted`
- `TripCancelled`
- `EarningObserved`
- `AdjustmentObserved`

The event model must allow optional provider-specific fields without inventing absent values. A visible offer is not automatically an accepted offer; a disappearing screen is not automatically proof of expiry; a fare displayed in a UI is not automatically a settled payout.

Each candidate carries:
- canonical event type and lifecycle/entity key when available;
- fields with per-field provenance and certainty/quality rationale;
- source observation IDs and evidence references;
- event time estimate and ingestion time;
- classification/model/rules version;
- status: candidate, corroborated, confirmed, contradicted, superseded or unresolved;
- reasons for missing/ambiguous fields and conflicting evidence.

Do not describe uncalibrated scores as probabilities. Never convert unknown to zero or missing to false.

### 4.5 Canonicalisation and delivery into BusyBee

The Rust domain core owns provider-neutral event contracts, validation, identity resolution, deduplication, evidence links, conflict handling, freshness and persistence. Kotlin owns Android capture, permissions, lifecycle and UI. Use a narrow, versioned Kotlin-to-Rust boundary; keep Android APIs and provider SDK objects out of canonical domain types.

Store the original observation/evidence link and the normalised event separately. Deduplicate by real-world entity/lifecycle and source identifiers where available, not just matching text. Preserve revisions and contradictions rather than overwriting history.

Canonical events then feed existing BusyBee surfaces such as Offer Desk, Driver Data Mirror and analytics. They must not bypass the common ingestion/domain layer to write directly into UI-specific state.

## 5. What App Observer is and is not

App Observer is a temporary research and validation harness for:
- collecting controlled, user-authorised observations;
- testing capture reliability and privacy boundaries;
- developing reusable perception/scene-graph components;
- testing event classification against labelled traces;
- exporting reproducible evidence fixtures for automated tests.

It is not:
- BusyBee's product identity or a permanent user-facing companion app;
- the final destination for screen labels, scene graphs or app profiles;
- a requirement that drivers install and operate a second app;
- permission to bypass app isolation, root devices, modify third-party APKs or replay provider requests.

Where a capture capability is appropriate for production, port/integrate its reusable implementation into BusyBee's source-adapter architecture. Keep research-only instrumentation out of production.

## 6. First implementation vertical slice

Do not attempt all apps and all event types at once. Prove one end-to-end path using a synthetic ride-hailing app and HTTPS service that we own, then a permitted user-visible observation path.

1. Generate a deterministic synthetic offer lifecycle: observed, updated, expired; include duplicate, delayed, malformed and out-of-order cases.
2. Capture the test app using the same observation envelope planned for BusyBee.
3. Produce evidence-backed `OfferObserved`, `OfferUpdated` and `OfferExpired` candidates.
4. Pass candidates through the canonical Rust domain contract and local event ledger.
5. Render them in BusyBee's Offer Desk/Driver Mirror with a trace back to source evidence.
6. Replay the same evidence and prove idempotency, ordering, uncertainty and conflict handling.
7. Only then add another event type or provider adapter.

The owned-app plaintext POC remains a controlled research test. It proves the observer can see application data at authorised boundaries in an app we own; it does not prove that Uber/Bolt can or may be instrumented.

## 7. Acceptance criteria

The pipeline is not complete because a screenshot gets a label or a scene graph gets persisted. It is complete when:

- A known workflow yields the correct canonical BusyBee event sequence.
- Each event is traceable to one or more immutable observations and exact evidence.
- Missing/unknown fields remain missing; no fabricated defaults.
- Duplicate observations do not duplicate canonical events.
- Delayed and out-of-order evidence is reconciled without losing history.
- Failed, cancelled, stale and ambiguous outcomes are not reported as success.
- Contradictory sources remain visible and explainable.
- Source permission, coverage, freshness and retention are enforced.
- Replaying an evidence bundle reproduces the same event decisions for a fixed model/rules version.
- Tests measure event precision/recall, false-success/false-expiry rates, field accuracy, latency and device resource cost.
- BusyBee works in a degraded but honest state when an adapter is unavailable.

## 8. Engineering priorities

1. **Define and test the versioned observation envelope and canonical event contract.**
2. **Build one synthetic offer lifecycle end-to-end through capture → interpretation → Rust canonical event → BusyBee UI.**
3. **Make event provenance, uncertainty, replay and idempotency first-class.**
4. Improve scene graph and temporal identity only where test failures show they are needed for reliable event extraction.
5. Expand event types and adapters incrementally, based on evidence coverage and product value.
6. Evaluate provider compatibility, terms and Android policy separately for each acquisition method.

The previous App Observer milestones M1–M5 are demoted from product roadmap to supporting research techniques. In particular, temporal element identity is not the next goal by itself; it is justified only by measurable improvement to event extraction.


## 9. Implementation status — 2026-11-21

The first shared contract slice is now present on the `app-observer-intelligence-reset` branch:

- Rust: `observation::ObservationEnvelope`, source identity/kind, evidence references, observed facts, `DriverActivityEvent`, canonical field certainty/provenance, validation and a deterministic in-memory event ledger.
- The ledger treats exact replays as duplicates. A different payload reusing an event ID is retained alongside the original and both versions are marked `Conflict`; evidence is not silently overwritten.
- Android: matching Kotlin DTOs and validation rules are defined in `ObservationContract.kt`.
- Rust unit tests cover envelope timestamp validation, provenance requirements, unknown-field preservation, duplicate insertion and conflicting event IDs.

This is a contract/domain primitive, not a completed runtime pipeline. It is not yet wired to notification capture, manual offers or CSV imports; it does not yet classify offer lifecycle observations, persist events durably, or deliver them to Offer Desk/Driver Mirror. CI must pass on the exact latest branch head before treating the contract as verified. No physical-device verification has been performed for this slice.
