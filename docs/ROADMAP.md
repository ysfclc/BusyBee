# Delivery Roadmap

**Rule:** one phase at a time. Each phase has a concrete exit gate. Do not start the next phase merely because a document or scaffold exists.

## Phase 1 — Product and architecture lock

**Deliverables**
- Product charter and non-goals.
- Provider-neutral system architecture.
- Source register with access and licensing status.
- Canonical event/evidence model.
- Phase plan and repository delivery rules.
- Healthy control-repository workflow validation.

**Exit gate**
- Decisions are recorded in GitHub.
- Existing BusyBee import pipeline passes its static workflow validation.
- No app code is generated before this checkpoint is complete.

## Phase 2 — Executable foundation

**Deliverables**
- Dedicated private product repository imported through BusyBee.
- Rust workspace with domain crates and tests.
- Android Kotlin application shell.
- Kotlin/Rust bridge.
- Local database and migration baseline.
- Map renderer proof of concept with a permitted base map.
- CI producing a debug APK artifact.

**Exit gate**
- App installs and launches on a real Android device.
- Rust tests and Android build pass.
- Map pans/zooms and shows a location with permission.
- No provider credentials are embedded in the APK.

## Phase 3 — First end-to-end public-data slice

**Deliverables**
- Source registry and connector SDK.
- First geography source and one live local event/transport feed.
- Raw observation → canonical event → map overlay pipeline.
- Provenance and freshness UI.
- Connector health and graceful degradation.

**Exit gate**
- A real event can be traced from source record to map marker.
- Stale/expired events are removed or clearly marked.
- Tests cover malformed, duplicate, delayed and contradictory records.

## Phase 4 — Multi-provider traffic and route comparison

**Deliverables**
- Parallel commercial provider trials.
- Provider-specific traffic/incident connectors where terms permit.
- Route-candidate model and comparison.
- Coverage, ETA, latency and cost benchmark.

**Exit gate**
- At least two independently sourced capabilities operate in parallel.
- Provider failure does not take down the map.
- Data rights and route semantics are documented before combining outputs.

## Phase 5 — Driver and community intelligence

**Deliverables**
- Consent-based driver reports.
- Moderation, reputation and expiry.
- Android location/motion observations.
- Private driver activity and trip records where lawfully accessible.
- Community/private data separation.

**Exit gate**
- A report can be submitted, reviewed, displayed with provenance and expired.
- Private driver records cannot leak into shared layers.

## Phase 6 — Driver economics and digital mirror

**Deliverables**
- Trip/offer/earnings import paths.
- Vehicle cost profile and net earning calculations.
- Time/zone/platform analysis.
- Data completeness and confidence reporting.

**Exit gate**
- Every insight is traceable to source records and states its coverage period.
- No missing data is silently treated as zero.

## Phase 7 — Navigation, 3D enrichment and hardening

**Deliverables**
- Navigation guidance and voice.
- Context-aware driving UI.
- Progressive 3D buildings/terrain and camera presentation.
- Performance, battery, privacy, safety and accessibility work.

**Exit gate**
- Real-world route testing passes.
- Driving presentation is glanceable and does not bury immediate hazards.
- 3D is an enhancement, not a substitute for correct geometry or navigation.

## Phase discipline

- Each phase is committed as a complete checkpoint.
- Keep a phase log and record decisions/deviations.
- Never replace a working project snapshot with an incomplete ZIP.
- The BusyBee import pipeline treats each archive as a **complete snapshot** and replaces the target repository's tracked tree. Every later archive must therefore contain the entire project, not only changed files.
- Do not upload secrets, signing keys, local configuration, generated APKs or build caches.
- Build failures are fixed before moving to the next phase.
