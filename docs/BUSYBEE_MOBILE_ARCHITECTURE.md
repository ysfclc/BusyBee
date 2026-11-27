# BusyBee Mobile Architecture

**Status:** Proposed architecture baseline  
**Audit date:** 19 November 2026  
**Repository baseline:** ysfclc/BusyBee, main, inspected 19 November 2026  
**Primary platform:** Android  
**Pilot geography:** Kraków, Poland  
**Initial performance device:** Redmi Note 12 Pro 5G, Dimensity 1080, 8 GB RAM, Android 14  
**Existing technology:** Kotlin Android application, Rust deterministic domain core, MapLibre renderer  
**AI constraint:** semantic AI inference and preference-memory retrieval run on-device. Cloud LLM access is not required for normal operation.

> This is an architecture proposal based on the repository as it exists on the audit date. It is not a claim that the proposed modules, local model, Room database, provider bridge or background session service have already been implemented.

---

## Product definition

BusyBee is a local-first driver intelligence and operating platform. It combines two inseparable product systems:

1. **Driver Data Mirror / Digital Twin:** a private, evidence-backed representation of the driver's work across Uber, Bolt and other connected platforms: offers, trips, lifecycle, earnings, fees, routes, time, vehicle costs and outcomes.
2. **Road Intelligence Aggregator:** a provider-neutral picture of roads, routes, traffic, hazards, closures, weather, city infrastructure, driver reports and navigation context.

The map is the primary spatial interface to this intelligence. It is not the entire product and it is not owned by one map vendor.

BusyBee is also the central operator for connected applications. It ingests signals through each app's actually available interfaces, maintains unified state and sends actions through documented APIs, Android intents, notification actions or constrained UI interaction when supported.

**“Inside BusyBee” is a product-level integration goal, not a promise that arbitrary third-party applications can be virtualised or that their private processes can be controlled.** Third-party apps remain separate Android applications unless they explicitly support embedding or another integration mechanism. BusyBee owns orchestration, intelligence, its operating surface and its evidence ledger; it does not own Uber's, Bolt's, Waze's or Google Maps' private implementation.

The local model is the semantic assistant. It is not the source of truth, route calculator, financial ledger, event processor or action executor. Deterministic code owns those responsibilities.

### Architecture invariants

- The central map is provider-neutral. Rendering, base map, routing, navigation telemetry and road-event sources are separate concerns.
- Every external signal is an observation with source, acquisition method, timestamps, coverage, precision, freshness and licence/retention status.
- An observation is not automatically a fact. Conflicting or incomplete evidence remains visible.
- Driver and location data are private by default. Community publication is explicit and separate.
- Provider observation and provider action are separate capabilities. Capturing a request never authorises replaying it.
- The on-device model can explain and recommend; deterministic code validates and calculates; the user authorises consequential actions.
- App closure is not force-stop. A user-started foreground session can continue under Android rules; a force-stopped app cannot promise continued operation.
- No feature depends on a single provider, model, connector or network connection.
- A missing field remains unknown. It is never replaced with zero, a guess or a model-generated assertion.

# 1. Constraint Inventory

These three constraints are the architecture's skeleton.

## C1. External-app access and Android execution are fragmented and permission-bound

Android apps run in separate processes and sandboxes. Another app can communicate with a target only through interfaces the target or Android exposes: documented APIs, exported components, intents, notification actions, accessibility-visible UI or other permitted system mechanisms. An app's internal function or backend endpoint is not automatically a public interface.

Background operation is also conditional. Android restricts starting foreground services from the background; Android 14 and later add while-in-use permission checks for services that use location, camera or microphone. WorkManager is appropriate for persistent deferrable work, not continuous high-frequency driver observation.

**Architectural implications**

- Model every external app as a connector with a capability declaration. “Installed” does not mean “connected”.
- Separate data acquisition from action execution. A provider may expose read-only information but no action capability.
- Model each capability independently as available, partial, permission-required, approval-required, unsupported, blocked or unknown.
- Use a user-started Driver Session foreground service for continuous permitted location/session work. It must have an ongoing notification and an explicit stop action.
- Use WorkManager for deferred sync, exports, reconciliation and maintenance.
- Persist enough state to recover after process death. Do not promise survival after force-stop, revoked permissions, OEM battery termination or reboot unless that behaviour is actually proven.
- Cross-app Activity Embedding is optional only when the target app opts in. It is not the base architecture.

**Failure mode:** A provider update removes an exposed interface; Android denies a service start; an OEM terminates the session; an app changes its notification or UI. BusyBee marks the affected capability degraded and keeps unrelated connectors and map functions available.

**Reversal cost:** High if private provider protocols or Accessibility become the core domain model; Low if they remain adapters behind stable contracts.

## C2. Local AI competes for RAM, latency, thermals, battery and context

The pilot device has 8 GB of physical RAM, but Android, MapLibre, navigation, provider apps and BusyBee share it. Model loading has a non-trivial cost: LiteRT-LM documentation warns that engine initialization can take up to about ten seconds and should run off the UI thread. GPU/NPU availability depends on device libraries and drivers. A background driver session must not continuously run a generative model.

The context window is also scarce. Large context does not equal good context. Raw notifications, route histories, trip rows, packet captures and old chat turns will overwhelm a small model and reduce relevance.

**Architectural implications**

- Local AI is a bounded subsystem with a model lifecycle manager, hard token budgets and explicit memory/latency budgets.
- Begin with a small quantised instruction model candidate, not an assumption that a 4B model will fit. Benchmark on the actual Redmi before selecting a default.
- Keep deterministic event processing and driver-session collection operational when the model is unloaded, slow or unavailable.
- Invoke generation on demand or for a discrete high-value event. Never keep a generative model in a continuous hot loop while driving.
- Use structured snapshots and curated memory cards, not raw database dumps or transcript accumulation.
- Treat inference output as untrusted data. Validate schema, evidence references, numeric ranges and action permissions before display or execution.

**Failure mode:** Model load fails, accelerator is unsupported, inference exceeds its deadline, process is reclaimed, memory pressure rises or output is malformed. Core app operation continues with deterministic UI and explicit AI-unavailable feedback.

**Reversal cost:** Medium for swapping runtimes behind an inference port; High if prompt logic, provider logic and product rules are embedded directly in a model-specific agent.

## C3. BusyBee must preserve truth across conflicting sources and private data

The map and Driver Mirror combine evidence with different authority, update cadence, precision, licence and retention rules. Navigation data may be display-only, transient or non-cacheable. A notification may be partial. A driver export may be authoritative for historical earnings but not live state. A public road report may be stale. A local model can produce fluent but unsupported claims.

**Architectural implications**

- Every observation is retained with provenance and field-level completeness. Canonical events link to supporting or contradicting evidence.
- Keep public/shared road intelligence separate from private driver activity.
- Put retention and redistribution rules on the source/connector, not in UI code.
- Store locally first. Network outages must not erase the Driver Mirror or local map state.
- The LLM never calculates earnings, route geometry, event confidence or expiry. Deterministic functions do.
- No provider data is persisted, transformed or redistributed unless the source licence and applicable terms allow it.

**Failure mode:** Conflicting road reports, incomplete earnings, a revoked provider licence, stale observations or an AI hallucination. BusyBee exposes the disagreement, missing field or source restriction rather than silently collapsing it.

**Reversal cost:** High if source provenance and raw evidence are discarded; Low if source adapters and canonical contracts preserve evidence from the beginning.

# 2. Reuse Inventory

The following distinguishes implemented main-branch assets from design-only work. A design document is not an implemented feature.

| Existing asset | Observed status on main | Treatment |
|---|---|---|
| Rust domain workspace and Android native bridge | Present; Kotlin calls a Rust shared library through a narrow native bridge | Retain Rust for deterministic, platform-independent computation. Keep Android lifecycle, UI and permissions in Kotlin. |
| MapLibre Android renderer | Present | Retain as initial renderer. Keep base map, route and intelligence providers independent. |
| OpenFreeMap Liberty / OSM-derived map direction | Present in current map direction and README; attribution is required | Preserve the dark, tilted, 3D-building direction. Treat style and base map as replaceable. |
| Road intelligence direction | Product charter, source register and system architecture define it; live feed coverage remains limited | Reuse source registry, freshness, TTL, evidence and conflict concepts. Do not describe mapped roadwork observations as real-time traffic speed. |
| DriverMirrorStore | Present as SQLiteOpenHelper database with imported records, raw CSV evidence and duplicate fingerprints | Preserve raw imported evidence and NULL semantics. Migrate non-destructively. |
| DriverCommandStore | Present as a separate SQLiteOpenHelper with offers, notices, driver items, community docs, road reports and settings | Do not create a third disconnected driver store. Reconcile into the unified persistence layer. |
| PlatformNotificationListener | Present; captures visible notification title/body for recognised ride apps | Retain as one partial acquisition adapter. It is not a provider API connection and does not prove full offer/trip access. |
| CSV import | Present; provider-neutral interchange and raw evidence retention documented | Keep as user-selected historical input until real provider export samples validate specific adapters. |
| DriverAnalytics | Present as deterministic Kotlin calculations with incomplete-field warnings | Preserve calculations and null semantics. Move reusable algorithms to Rust only when there is a tested need. |
| RoutePlanner | Present; Nominatim geocoding and OSRM route alternatives | Keep behind a route-provider adapter. Its estimates are not live-traffic guidance. Review service policies before production-scale use. |
| Product Charter, System Architecture, Decisions, Roadmap and Source Register | Present | These remain the source of product intent. This document refines rather than replaces the provider-neutral aggregator and Driver Mirror. |
| Native Provider Bridge | Design document exists; implementation is not on main | Adopt adapter, capability, provenance and health contracts. Build as a vertical slice; do not claim Uber/Bolt APIs exist. |
| Platform Activity Integration / owned-app plaintext observer POC | Research/design only on main; third-party compatibility is explicitly unproven | Keep as a separate feasibility track. Do not put instrumentation or provider internals in the production domain. |
| App Observer | Separate research app/branch family, not a BusyBee production module | Use exported, user-authorised evidence to validate connectors. BusyBee must not require App Observer or PCAPdroid to run. |
| Current Android UI | One large imperative MainActivity with custom Views and hard-coded visual values | Migrate incrementally to Compose and a design-system module. Preserve working MapLibre behaviour. |
| Existing app dependencies | Core KTX, AppCompat, MapLibre and JUnit in inspected app module | Compose, Room, lifecycle/state tools and local AI runtime are future additions, not current capabilities. |
| Local AI | No local LLM runtime or AI memory pipeline found on main | Treat as required new subsystem. Reuse previous LiteRT-LM/Gemma experiments as learning, not as evidence of production readiness. |

### Explicitly rejected reuse

- Do not extend the oversized single-Activity pattern as the next feature architecture.
- Do not keep two independent SQLite stores as competing sources of truth.
- Do not make notification parsing the canonical provider API.
- Do not let MapLibre, a provider SDK or one traffic vendor own the domain.
- Do not copy raw transcripts or every observed app event into AI memory.
- Do not make a research-only instrumentation technique a production dependency.
- Do not move deterministic calculations into the LLM.
- Do not make Memo, App Observer or another project a runtime dependency of BusyBee.

# 3. Domain Decisions

Each decision records alternatives, product-specific rationale, failure mode and reversal cost.

## 3.1 Presentation and Domain Architecture

**Decision**

Use Clean Architecture boundaries with feature-scoped MVI/UDF presentation, implemented incrementally in Kotlin, while retaining the existing Rust engine for deterministic cross-platform computation.

Kotlin owns Android capabilities, repositories, persistence orchestration, feature state holders and UI. Rust owns only pure algorithms that materially benefit from reuse, performance or deterministic cross-platform tests: spatial evidence matching, route/geometry calculations, evidence scoring and selected driver-economics functions. Keep the Kotlin/Rust boundary narrow, typed and versioned.

Each feature has a state holder and immutable UI state. State flows down; user intents flow up. A reducer owns only its feature's state; there is no global reducer containing map, driver, settings, provider and AI actions.

**Alternatives considered**

- Plain MVVM without feature boundaries: simpler at first, but invites duplicated mutable screen state and I/O in ViewModels.
- One application-wide MVI reducer: too much coordination and coupling for a solo/small-team project.
- Full Clean Architecture with a use case for every operation: rejected as ceremony without benefit.
- All-Rust domain and UI: rejected because Android lifecycle, permissions, Compose and services belong in Kotlin.

**Rationale tied to constraints**

BusyBee has several independent long-lived state machines: map/route, source health, Driver Mirror, offers/trips, active driver session and model lifecycle. Feature-scoped reducers prevent an offer event from rebuilding unrelated map or settings state. The repository can migrate without rewriting all existing Kotlin/Rust code. Android officially recommends UDF, ViewModels and lifecycle-aware state collection for complex UI. [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations).

**Reducer discipline**

- One immutable state type per feature.
- Reducers are pure transformations of state plus feature intent.
- Effects are typed and handled outside reducers; reducers never perform I/O.
- Use cases exist only for logic shared by features, meaningful product operations or independently testable transactions.
- Cross-feature operations go through domain use cases or an application command, not direct ViewModel references.
- State is normalised: IDs and bounded summaries in UI state, not whole databases or raw payloads.
- Split a reducer when it contains independent state machines, not just because it reaches a line-count threshold.
- No catch-all AppAction enum.

**Failure mode**

State is duplicated among Room, Rust, repositories and ViewModels; reducers become large; the FFI becomes chatty or fragile.

**Reversal cost: Medium.** Feature state holders and repositories can be replaced independently. Moving large amounts of business logic into Rust or replacing the canonical event contract after connectors ship would be expensive.

## 3.2 UI Architecture and Design System

**Decision**

Move new and migrated screens to Jetpack Compose with Material 3 primitives governed by a BusyBee design-system module. Keep MapLibre behind a dedicated MapSurface boundary; use AndroidView interop while necessary rather than rewriting the working map renderer.

The design system uses semantic layers:

1. **Tokens:** semantic colour, typography, spacing, shape, elevation, motion, map-overlay and status tokens.
2. **Primitives:** text, icon, surface, divider, focus ring, status indicator and touch target.
3. **Components:** buttons, chips, source badges, offer cards, freshness markers, metric values and map controls.
4. **Compositions:** offer summary, source health row, trip timeline item, evidence panel, route comparison and driver-session strip.
5. **Templates:** map-first driving shell, parked analysis shell, source setup and Driver Mirror detail.
6. **Feature screens:** composed from the design-system API and feature-specific state.

Preserve the existing dark/night and mint identity as a starting point, then extract semantic tokens. Do not mechanically reproduce hard-coded values.

**Alternatives considered**

- Continue Views: lowest migration cost, but the current large MainActivity is already the maintenance warning.
- Compose everywhere immediately: rejected because it risks destabilising the working MapLibre integration in one rewrite.
- Rigid Atomic Design taxonomy: rejected because it can create arbitrary component boundaries and wrappers.
- Raw Material 3 without a BusyBee layer: rejected because identity, driving density and source-state semantics would drift.

**Rationale tied to constraints**

Compose supports immutable state-driven rendering and testable semantics. A first-class design-system module gives one place to enforce contrast, touch targets, driving-mode hierarchy, source status and adaptive layouts. Incremental migration protects the map. Android documents custom Compose design-system extension points and adaptive window classes. [Custom Compose design systems](https://developer.android.com/develop/ui/compose/designsystems/custom) · [Window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes).

**Enforcement**

- Only core:designsystem may define tokens or wrap shared Material primitives.
- Feature modules may not hard-code shared colours, spacing or typography.
- Add Detekt/Konsist or equivalent architecture rules for forbidden imports and raw colour literals.
- Every reusable component has previews for normal, loading, disabled, stale and error states as applicable.
- Compose screenshot/golden tests cover map overlays, offer states, source health and dark theme.
- Android Lint and Compose UI tests run in CI.
- Interactive targets are at least 48 dp where practicable; normal text contrast target is 4.5:1 and large text 3:1.
- Meaning is never conveyed by colour alone.
- Custom map controls expose labels, role, state and actions to accessibility services.

**Responsive behaviour**

Use window-size classes based on the available app window, not device model names:

- Compact phone: map-first, bottom navigation and one compact intelligence sheet.
- Medium: map plus persistent activity panel where useful.
- Expanded/tablet/foldable: map and driver timeline/source inspector side by side.
- Handle window resize, fold/unfold and multi-window without losing domain state.

**Failure mode**

Compose migration introduces map lifecycle bugs or UI jank, tokens are bypassed, or accessibility guidance remains untested.

**Reversal cost: Medium.** Tokens and feature boundaries remain useful if the rendering framework changes. MapLibre interop keeps the spatial renderer separately replaceable.

## 3.3 UX Architecture

**Decision**

Keep a map-first, context-aware operating UX with separate Driving and Parked modes. Preserve the established product surfaces: Map, Offers, Earnings/Driver Mirror, Driver Hub and Sources/Connected Apps. The local assistant is embedded in these surfaces, not a mandatory full-screen chatbot.

**Alternatives considered**

- Chat-first shell: rejected because the driver needs direct map and command surfaces; requiring conversation for deterministic actions adds friction.
- Dashboard-first shell: rejected because it competes for attention and weakens the map's spatial role.
- One screen per data source: rejected because BusyBee's value is aggregation, not reproducing provider apps.

**Rationale tied to constraints**

Driving tasks require glanceable high-priority information; parked tasks can expose analytics and evidence. A feature-local navigation stack lets each area retain state without central reducer coupling. AI is progressive disclosure, not a replacement for predictable interface controls.

**Navigation and state restoration**

- One Activity hosts the app and typed navigation destinations.
- Top-level destinations preserve their own back stack and durable screen state.
- Deep links from apps, notifications, files and widgets become typed intents at the navigation boundary.
- Map state contains camera, selected layers, selected route and active panel; do not persist animation frames.
- Back closes transient sheets first, then returns within the current feature, then exits the top-level shell.
- Restore from saved state and repositories, never from a ViewModel alone.
- Navigation cannot trigger provider actions as a side effect.
- Process restoration must never replay a command or start a driver session without user intent and valid Android permission state.

**Driving mode**

- Map remains the visual centre.
- Show next manoeuvre, immediate hazards, current driver-session status and high-priority events only.
- Large predictable controls; no dense charts, source payloads, long AI answers or multi-step forms.
- Voice/haptic feedback is opt-in and must not compete with navigation audio.
- Complex decisions are deferred until parked; the UI should not encourage typing or reading while moving.

**Parked mode**

- Full route comparison, provenance, offer economics, Driver Mirror analytics, settings and AI explanations.
- Evidence inspection and manual reconciliation.
- Coverage and missing fields displayed next to financial and road insights.

**Feedback contract**

Every asynchronous surface uses the same states:

- Loading: what is loading and whether old value remains usable.
- Content: value plus source and freshness.
- Empty: no records exist versus no source reports an event.
- Partial: some sources/fields are available and others missing.
- Stale: data exists but is outside freshness window.
- Offline: local data usable, network-only features unavailable.
- Permission required: explain why and open the relevant Android settings surface.
- Unsupported/blocked: explicit state, never an endless spinner.
- Error: recoverable action and diagnostic reference, not a raw exception.
- Pending action: dispatched or awaiting confirmation.
- Verified: only after the underlying outcome is confirmed.
- Unknown outcome: action was sent but success could not be independently established.

**AI progressive disclosure and human checkpoints**

1. Inline answer: concise result, time and source.
2. Expand: why, evidence and uncertainty.
3. Inspect: provenance, missing fields, conflicts and deterministic calculation.
4. Act: a separate command preview with side effects and confirmation.

The model never mutates product state directly. Consequential external actions require explicit confirmation and independent verification.

**AI degradation**

- Core screens and deterministic calculations continue if AI is unloaded or unavailable.
- Show a compact unavailable/slow state; never block route, offer or ledger operation.
- Cancel stale inference when the active route or offer changes.
- Never reuse an answer for another offer/route without validating its evidence IDs and expiry.
- Deterministic filters, rankings and summaries remain available.

**Failure mode**

Map becomes a dense dashboard, critical feedback is hidden in transient snackbars, or fluent AI prose appears authoritative without evidence.

**Reversal cost: Low–Medium.** Top-level destinations can change. Driving safety and evidence-feedback contracts should remain invariants.

## 3.4 Local AI Integration Layer

**Decision**

AI is an on-device, bounded subsystem behind a model-agnostic Kotlin interface. LiteRT-LM is the first runtime candidate because its Android Kotlin API supports local inference and CPU/GPU/NPU backends. Begin with a small quantised instruction model in approximately the 1B parameter class as a candidate, not a locked selection. Benchmark on the actual Redmi before selecting a default.

CPU is the compatibility baseline. GPU/NPU is enabled only after measurements on the device and confirmation that required libraries and drivers work. The presence of an NPU in the SoC does not prove runtime compatibility.

No model is bundled in the base APK. Models are versioned downloadable artifacts with checksum, tokenizer, licence, model card, disk-space check and explicit delete/replace control. Pin runtime versions. Do not use dynamic latest dependencies in release builds.

**Alternatives considered**

- Cloud LLM as default: rejected because local inference is a hard requirement and driver data is sensitive.
- Keep a 4B model resident: rejected pending memory/thermal evidence on this specific device.
- Put an LLM in the background event loop: rejected because it wastes battery and competes with navigation and provider apps.
- Build AI directly into Rust core: rejected because the runtime lifecycle and model API are separate, replaceable concerns.
- Treat LiteRT-LM as permanent: rejected; it is an adapter candidate, not domain architecture.

**Rationale tied to constraints**

LiteRT-LM initialization can take several seconds, so model loading must be asynchronous and lazy. Device hardware acceleration is not guaranteed. Model runtime must be replaceable and app operation must not depend on model availability. [LiteRT-LM Android documentation](https://developers.google.com/edge/litert-lm/android).

**Responsibility minimisation**

Deterministic code owns:
- Offer ranking, fare/fee/bonus arithmetic, currencies and net/hour calculations.
- Route geometry, distance, ETA arithmetic and route comparison.
- Source freshness, expiry, connector health and permissions.
- Deduplication, ordering, entity matching and evidence confidence rules.
- Database writes, migrations and retention.
- Action allowlists, confirmation, dispatch and verification.

The local model handles:
- Natural-language questions over the user's BusyBee data.
- Semantic classification of ambiguous permitted descriptions.
- Summaries of a bounded time window or driver session.
- Explanation of deterministic route/offer/economics calculations.
- Mapping free-form goals to an allowlisted read-only query plan.
- Selection and description of relevant, user-approved memory.

The model does not:
- Invent missing trip, traffic or earnings fields.
- Calculate canonical financial or routing values that code can calculate.
- Determine licence, permission or legal status.
- Directly execute provider actions, SQL, shell commands or arbitrary code.
- Continuously analyse every packet, notification or screen.

**Model abstraction**

- LocalModelRuntime: load/unload, health, metadata, backend, generate and cancel.
- EmbeddingRuntime: embed, health, metadata and unload.
- ModelRegistry: model ID, version, checksum, tokenizer, languages, licence, size and compatibility.
- ModelLifecycleManager: lazy load, refcount, memory-pressure unload, cancellation and fallback.
- InferenceRequest: task, context, token cap, deadline, output schema and data classification.
- InferenceResult: typed payload, model/runtime versions, latency, token counts, evidence IDs and validation state.

Domain features depend on task ports, never LiteRT-LM types. A future llama.cpp or other runtime is an adapter replacement.

**Inference pipeline**

1. Receive typed task request.
2. Build permission-checked context snapshot.
3. Redact secrets and unrelated personal fields.
4. Tokenise with the selected model's tokenizer; trim before inference.
5. Assemble versioned policy, task schema and selected dynamic context.
6. Run on a background dispatcher.
7. Parse output using strict typed serialization.
8. Validate schema, enum values, numeric bounds, evidence IDs, freshness and capabilities.
9. Permit at most one constrained format-repair attempt using the same bounded evidence; otherwise use deterministic fallback.
10. Persist only a useful user-approved summary or explicit preference, not the raw prompt by default.

**Defensive output**

- Treat all model output as untrusted.
- Reject invalid JSON, unknown values, non-finite numbers, unresolved IDs, unsupported actions and ungrounded citations.
- Evidence references must resolve to current permitted records.
- Re-parse query plans into allowlisted query types; no arbitrary SQL.
- Proposed actions become previews, never executable commands.
- For financial/safety outputs, show deterministic calculation separately from the natural-language explanation.

**Initial resource budgets — targets, not measured device results**

| Budget | Initial target | Exceeded behaviour |
|---|---:|---|
| Default input context | 1,200 tokens | Trim low-priority evidence and old turns |
| Hard input cap | 1,600 tokens | Reject or rebuild context; never overflow |
| Generated output | 192 tokens | Stop and provide concise partial/fallback |
| Active generative inference | One at a time | Cancel stale task; newest relevant task wins |
| Cold model initialization | Target no more than 12 seconds | UI remains usable; load only when requested |
| Warm first-token latency | Target median no more than 3 seconds | Deterministic answer or processing state |
| Typical task deadline | 15 seconds | Cancel and use deterministic fallback |
| Incremental model PSS | Target no more than 1.2 GB; investigate above 1.5 GB | Unload, reduce model/context or disable AI |
| Background generation | None continuous | Deterministic collectors only |
| LLM + embedding model | Avoid simultaneous residency by default | Load/unload sequentially |

Measure model file size, resident memory, tokens/sec, accelerator stability, thermal throttling and battery. Parameter count does not prove device performance.

**Language and retrieval model**

Evaluate Polish, English and Turkish. Polish is the pilot locale. EmbeddingGemma 1 (308M parameters) is a candidate for multilingual retrieval; its published model overview describes support for more than 100 languages and quantised use below 200 MB RAM. This is a candidate to benchmark, not a requirement. FTS5 lexical search must work without embeddings. [EmbeddingGemma overview](https://gemma.ai/models/embeddinggemma/).

**Failure mode**

Cold load too slow; GPU/NPU crashes; model displaces MapLibre/provider apps; model output malformed; quality changes after model update.

**Reversal cost: Medium.** Runtime/model are behind interfaces; a new inference engine is contained. Moving deterministic logic into prompts or changing canonical product contracts is high-cost and prohibited.

## 3.5 Context Feeding and Memory Architecture

**Decision**

Use a selective context assembler with separate static, dynamic and ephemeral sections. The model receives task-relevant snapshots and curated memory cards, never raw database dumps, raw event streams or full chat transcripts.

Memory is a preference-aligned fact store, not a transcript accumulator. Store only stable, useful, permitted facts explicitly stated or confirmed by the user. No speculative inferred memory is written.

**Alternatives considered**

- Entire chat transcript as memory: rejected because it grows without bound, repeats stale facts and creates contradictions.
- RAG over the entire driver database and packet archive: rejected because retrieval becomes noisy, leaks sensitive details into prompts and wastes context.
- Always load all preferences: rejected because most preferences do not affect every task.
- Vector database service: rejected for a local-first phone app with a small curated memory corpus; SQLite FTS5 plus optional local embeddings is sufficient to test first.

**Rationale tied to constraints**

The context window and compute are scarce, and BusyBee's structured data already has deterministic query mechanisms. Only evidence that can change the answer belongs in the prompt. The model must not become a shadow database.

**Static context**

- Compact product/system instruction.
- Safety and privacy rules.
- Output schema for the active task.
- Minimal read-only tool catalogue.
- User language/format preference if relevant.

**Dynamic context**

- Current driver session state.
- Active offer/trip summary, if applicable.
- Current map viewport or route summary.
- Small set of live road events with source/freshness.
- Connector health relevant to the task.
- Vehicle profile only when query requires it.
- Retrieved memory cards that materially change the answer.

**Ephemeral context**

- Current user request.
- Up to four recent relevant turns or a short validated task summary.
- Current screen selections.
- Pending confirmation state.

**Strict context budget**

Tokenizer for the active model is authoritative. Character approximation can be used only for pre-filtering.

Default budget: **1,200 input tokens**. Hard cap: **1,600 tokens**.

| Section | Default tokens |
|---|---:|
| Policy, privacy and safety | 160 |
| Task contract, output schema and minimal tools | 140 |
| Current user request | 160 |
| Structured driver/map snapshot | 220 |
| Supporting evidence summaries | 250 |
| Retrieved stable memory | 120 |
| Short validated session summary | 100 |
| Reserve | 50 |
| **Total** | **1,200** |

Rules:
- Rank evidence by task relevance, freshness, source quality and field completeness using deterministic code.
- Ordinary answers receive at most three evidence items. Parked-mode deep analysis may request more, still below the hard cap.
- Retrieve no more than three memory cards by default, each no longer than 120 tokens.
- Each evidence item carries compact source ID and timestamp.
- Empty sections do not get filled with unrelated data.
- Output tokens are budgeted separately.
- If relevant evidence is absent, the model must say so.

**Memory record contract**

Each card contains stable ID, subject, predicate, value, language, source (user-stated/user-confirmed/user-controlled setting), creation and last-confirmed times, optional expiry, sensitivity/retention class, supersession link and status (current, superseded, expired, deleted). An embedding and embedding-model version are optional.

Valid example: “Prefers kilometres.” A one-time destination, transient offer, payment amount or unconfirmed inference is not a stable memory.

**Retrieval pipeline**

1. Resolve task type and required fields.
2. Build a compact query from current user request and typed screen context.
3. Apply sensitivity and retention filters before search.
4. Lexical search current memory with SQLite FTS5.
5. If available, embed the query and compare only eligible memory cards. Keep vectors as versioned numeric blobs; no vector server.
6. Merge lexical and semantic ranking with a deterministic reciprocal-rank fusion or similarly tested method.
7. Boost explicit confirmation and task relevance; penalise stale/superseded cards.
8. Return at most three by default.
9. Tokenise candidates and enforce hard budget.
10. Record which IDs were retrieved and whether they materially affected the output.

Embeddings aid retrieval; they do not establish truth. Lexical-only fallback is mandatory.

**Chunking**

- Product facts remain structured; never prose-chunk database rows.
- Stable memory is one fact per card.
- User-selected documents are chunked by heading/paragraph, target 150–300 tokens, with source/page reference.
- PCAP/HAR, notification bodies, app logs and full trip histories are never indexed as general memory. A user-selected evidence analysis may create a small redacted report with its own retention policy.

**Deduplication and conflict resolution**

- Updating the same fact key supersedes the old card transactionally.
- Retrieve only the current fact; retain supersession edge for audit.
- Time-scoped facts may coexist with explicit validity windows.
- User corrections outrank model-generated summaries.
- Ask before storing ambiguous durable memory.

**Session rotation and prompt hardening**

- Start a new task session when intent changes, active provider/offer/route changes, or context reaches 70% of budget.
- Keep at most four relevant turns. Older turns are dropped unless reduced to a validated 100-token summary of explicit goals, decisions and unresolved items.
- New session starts from current structured state, not previous model answer.
- Provider payloads, imported documents, notifications and screen text are untrusted quoted data; they cannot override instructions or tool definitions.
- Prompt/schema changes pass the same local golden-set evaluation as model changes.

**Explicit exclusions**

Never include routinely: full conversation history; all trips; raw PCAP/HAR; cookies/tokens/passwords/authorisation headers/payment credentials; another person's identifiable data; full location history when current location is enough; stale offers presented as live; prior unsupported model answers; inferred personality traits; or provider data whose licence disallows processing/persistence.

**Failure mode**

Retrieval misses a useful preference, stale memory remains current, embedding runtime is unavailable, or trimming removes key evidence.

**Reversal cost: Low–Medium.** Cards, ranking and embeddings are replaceable. Reversing an unconstrained transcript-based memory system after users depend on it would be costly; transcript accumulation is rejected from day one.

## 3.6 Data and Persistence Architecture

**Decision**

Adopt Room for new typed persistence access, while preserving existing SQLite data. Separate private product data, public road cache and AI memory by database/repository boundary.

Proposed stores:

1. **Private product DB:** driver events, offers, trips, earnings line items, vehicle profile/costs, provider capabilities/health, commands/verifications and private evidence metadata.
2. **Public road/cache DB:** permitted road observations, canonical road events, source coverage and expiry metadata; enforce provider-specific cache rules.
3. **AI memory DB:** curated preference cards and user-selected summaries with optional embeddings. The AI feature cannot query product DAOs directly.

Room is an abstraction over SQLite with compile-time query checks and migration support. [Room documentation](https://developer.android.com/training/data-storage/room). Existing SQLite can be migrated incrementally. [SQLite-to-Room migration](https://developer.android.com/training/data-storage/room/sqlite-room-migration).

**Alternatives considered**

- Continue SQLiteOpenHelper: minimal dependencies, but current split stores and untyped SQL increase reconciliation risk.
- Room: selected for typed DAOs, query checks and migrations while retaining SQLite.
- One shared DB for product and memory: rejected because it weakens the memory boundary and enables accidental raw-data retrieval.
- Immediate encryption without a build/ABI test: rejected; SQLCipher has native dependencies and must be validated against ABI/build/licence/performance constraints.

**Rationale tied to constraints**

BusyBee already stores structured driver records and raw evidence. Room can improve query and migration discipline without discarding SQLite. Separate memory storage prevents AI from treating all driver rows as personal memory. Public road data has different licences and expiry rules from private driver data.

**Encryption boundary**

- Private product and memory files are encrypted at rest using SQLCipher for Android + Room after a build/licence/performance gate.
- Generate a random DB key locally and wrap it with Android Keystore. Never hard-code or store plaintext keys in preferences.
- If the key is invalidated, fail closed and offer recovery from a user-held encrypted export; never silently create an empty DB over existing data.
- Public road cache remains separate from private trip/location records.
- Logs and diagnostics never contain keys or raw sensitive payloads.
- Exclude private DB/key material from unencrypted backup/transfer.
- Provide explicit encrypted export/restore using a user-controlled recovery secret and vetted password-based derivation. A Keystore key is not assumed restorable on a new device.

**Migration from current stores**

1. Back up both current SQLite files.
2. Read offers, notices, driver items, community docs, road reports, settings, imports, raw evidence and canonical imported records.
3. Map records into the new schema without dropping fields or evidence.
4. Preserve original IDs, timestamps, filenames, fingerprints and import batches.
5. Deduplicate only when canonical fingerprints prove equivalence; retain links to every source evidence row.
6. Compare row counts, amount totals, null counts and fingerprint sets.
7. Commit only after invariants pass.
8. Keep original DB files read-only until two successful opens and one backup/export verification.
9. Never use destructive fallback migrations.

**Sync**

Device-local state is the local source of truth. If network sync is introduced, use an outbox/inbox with stable IDs, idempotency and conflict records. WorkManager drains deferrable sync with constraints and backoff. A server is introduced only for protected API credentials, central feed polling, community collaboration or user-enabled multi-device sync. The core driver mirror and map remain useful offline.

**Failure mode**

Migration loses raw evidence; SQLCipher native library causes ABI/build problems; Keystore key invalidation blocks access; backup restores DB without key.

**Reversal cost: Medium–High.** Data schema and encryption are expensive to reverse once user data accumulates. Stage and verify before deleting legacy stores.

## 3.7 Observability and Evaluation

**Decision**

Instrument evidence, capability health, latency and data quality. Default diagnostics are local-only. Sending diagnostics off-device is explicit opt-in and redacted.

**Alternatives considered**

- Cloud analytics by default: rejected because trip/location/provider data is sensitive and local-first is a core constraint.
- Log raw payloads for debugging: rejected because logs become a second ungoverned personal-data store.
- Evaluate AI by subjective impressions only: rejected because prompt/model changes need regression evidence.

**Rationale tied to constraints**

Connector failures and AI quality errors must be observable without uploading private user data. Local golden sets make model/runtime/prompt changes comparable offline.

**Product/connector metrics**

- Connector state and per-capability coverage.
- Last successful event/sync and event lag.
- Missing fields by provider/event type.
- Duplicate and conflict rate.
- Stale observation count.
- Permission revocation and unsupported-version count.
- Driver session uptime/interruption/recovery.
- Route/source latency, errors, quotas and licence status.

**AI metrics**

- Model cold-load and warm first-token times.
- Input/output token counts and truncation.
- Context assembly, retrieval and inference latency.
- Peak PSS, OOM/unload count and accelerator fallback.
- Schema parse/validation failures.
- Deterministic fallback frequency.
- Evidence reference validity and grounding rate.
- Memory retrieval precision, supersession correctness and unused-context rate.
- User corrections and feedback reason, local by default.

**Logs**

Log structured event ID, connector ID/version, model ID/version, task ID and latency. Do not log raw provider payloads, credentials, full location traces, full chat history or sensitive documents. Redact before persistence/export, not only in the UI.

**Evaluation without cloud calls**

- Golden fixtures for canonical events, conflicts, stale records, missing fields and provider-version changes.
- Golden natural-language queries in Polish, English and Turkish.
- Deterministic assertions for calculations, migrations and command transitions.
- AI rubrics for evidence citations, completeness, abstention when evidence is absent, no unsupported action and concise output.
- Freeze evaluation fixtures and compare structured output and evidence IDs, not only text similarity.
- Device benchmark records Android version, app build, model artifact, backend, thermal state, memory and battery before/after.
- Every model/runtime/prompt change requires regression results before rollout.

**Failure mode**

Metrics lack enough context to reproduce a bug, or diagnostics leak sensitive driver information.

**Reversal cost: Low.** Metrics can be added/removed; privacy classifications and event IDs should be stable from the start.

## 3.8 Evolution and Reversibility

**Decision**

Design explicit seams for model, embedding, provider, map, storage, sync and UI replacements. Keep modules few until there is a real independent build/test or ownership boundary.

**Alternatives considered**

- Large up-front micro-module architecture: rejected because it increases build/DI overhead before boundaries have been proven.
- Single app module forever: rejected because platform, AI, map and features need independent tests and dependency enforcement.
- Commit to one provider/model for convenience: rejected because it recreates lock-in.

**Rationale tied to constraints**

Provider APIs and Android permissions change, model/runtime support varies across hardware, map licensing varies, and data migration is hard to undo. Isolating these points makes the product adaptable without a rewrite.

**Failure mode**

An adapter leaks provider-specific types into the UI/domain; one model library or map vendor becomes a transitive dependency of all features; sync logic spreads through ViewModels.

**Reversal cost: Low–Medium** when seams are respected; High if third-party internals become domain types or raw payloads become the only stored representation.

### Reversal-cost register

| Decision | Main risk | Reversal cost |
|---|---|---|
| Kotlin Android shell | Platform API churn | Medium |
| Rust pure deterministic core | FFI complexity | Medium |
| Feature-scoped MVI/UDF | Reducer explosion | Medium |
| Compose + design system | Migration and map interop regression | Medium |
| MapLibre behind MapSurface | Renderer/performance limits | Low–Medium |
| Provider adapter/capability registry | App updates and source churn | Low |
| Room with staged migration | Schema/data migration | Medium–High |
| SQLCipher + Keystore | ABI, performance and recovery | Medium–High |
| LiteRT-LM behind runtime interface | Model/backend incompatibility | Medium |
| Separate AI memory DB | Extra backup/migration work | Low–Medium |
| Local inference by default | Small model limits | Low to add explicit optional remote adapter later |
| User-started foreground driver session | OEM/Android termination | Medium |
| WorkManager for deferrable tasks | Not real-time | Low |
| No app virtualisation dependency | Some app screens cannot be embedded | High to reverse; do not make embedding a foundation |
| Observation/action separation | More explicit command states | High to remove; preserve as invariant |

### Expected evolution seams

- Model swap: LocalModelRuntime + model manifest.
- Embedding swap: EmbeddingRuntime + versioned vector dimensions.
- Optional cloud fallback, if approved later: separate opt-in adapter, disabled by default, with a data-transfer warning.
- Provider source swap: replace adapter behind canonical contracts.
- Map renderer swap: MapSurface consumes provider-neutral geometry/layers.
- Base map swap: change map source without changing routing/intelligence.
- Persistence swap: repositories isolate Room/SQLCipher from domain models.
- Multi-device sync: add inbox/outbox at repository boundary.
- Accessibility/automation: optional platform adapter, never the only provider-action path or a domain dependency.

# 4. Context Feeding Contract

This section is normative. Every model call must follow it.

## 4.1 Assembly order

1. Policy header: BusyBee role, privacy, safety and never-invent-missing-data rule.
2. Task contract: one task type, output schema and minimal allowed read-only tools.
3. Current user intent in the user's language.
4. Current state snapshot: only required fields with timestamps and source IDs.
5. Evidence candidates: top-ranked, compact and traceable.
6. Memory: up to three current, relevant, user-approved cards.
7. Ephemeral task summary only when it changes the answer.
8. Tokenise with active model tokenizer, trim by relevance and enforce the hard cap.

## 4.2 Task-specific context

| Task | Allowed context | Explicitly excluded |
|---|---|---|
| What is happening around me? | Current location/precision, nearby road events, source freshness/coverage, active route summary | Full location history, unrelated earnings, full notifications |
| Is this offer worthwhile? | Current offer fields, pickup/trip distance, duration, known fare/fees/bonus, vehicle cost, completeness | Other users' trips, unknown fees as zero, old offer as live |
| Why this route? | Candidate route metrics, source/time, known traffic/closure observations and conflicts | Unsupported lane claims, hidden provider payloads |
| Summarise my shift | Explicit time range, trip/offer summaries, reconciled earnings and missing-field coverage | Every raw event, raw export, credentials |
| What should BusyBee remember? | Current user statement and relevant current memory cards | Automatic transcript mining or inferred traits |
| Inspect a provider issue | User-selected redacted evidence, app/version and acquisition metadata | Unrestricted packet dump, secrets or unrelated people’s data |

## 4.3 Read and action tool policy

- Read tools expose typed summaries, not unrestricted database/filesystem access.
- Arguments are validated against strict schemas and bounded by time range, radius, result count and source list.
- The model cannot construct SQL, shell commands, arbitrary URLs, scripts or raw provider requests.
- Read-only queries execute only after schema and permission checks.
- Mutating operations go to a separate command router and require user confirmation unless a future documented low-risk preference explicitly allows them.
- Tool execution returns typed result and evidence IDs. The model cannot invent successful tool results.
- The command router verifies actual outcome independently of model output.

## 4.4 Context quality gates

Rebuild/reject context if an item is expired, not licensed for the intended use, duplicated, superseded, missing provenance for a time-sensitive answer, or from a revoked permission. Cancel inference if the active offer/route changes. An answer is grounded only when material claims point to allowed evidence or deterministic calculations.

# 5. UI/UX Architecture Map

## 5.1 Product surfaces

- **Map:** central spatial view; route, traffic, road events, navigation state, driver position and intelligence layers.
- **Offers:** unified provider-neutral offer desk; source, freshness, known economics, uncertainty and official-app handoff where needed.
- **Earnings / Driver Mirror:** unified timeline, imports, trips, reconciled amounts, vehicle costs and data coverage.
- **Driver Hub:** vehicle profile, recurring costs/reminders, documents and maintenance context.
- **Sources / Connected Apps:** app discovery, permission state, source method, capability chips, health, last event, coverage and disconnect/delete controls.
- **Assistant:** contextual Ask BusyBee entry points from map, offer, trip, source and hub; receives only the context required by the current task.

## 5.2 Design-system hierarchy

~~~text
core:designsystem
  foundation/
    ColorTokens, TypeTokens, SpacingTokens, ShapeTokens
    ElevationTokens, MotionTokens, MapTokens, StatusTokens
  primitives/
    BeeText, BeeIcon, BeeSurface, BeeDivider
    BeeStatus, BeeTouchTarget
  components/
    BeeButton, BeeChip, BeeMetric, BeeSourceBadge
    BeeFreshness, BeePermissionRow, BeeOfferCard
  compositions/
    OfferSummary, ProviderHealthRow, EvidencePanel
    TripTimelineItem, RouteComparison, DriverSessionStrip
  templates/
    DrivingShell, ParkedShell, MapFirstLayout, SourceSetupLayout
~~~

Atomic Design is only a vocabulary for composition depth, not a requirement for a class per visual fragment.

## 5.3 Navigation/state restoration

One Activity and typed destinations. Top-level routes preserve their own back stack. Deep links are parsed at the boundary and converted to typed intents. Map camera, selected layers and panels are durable preferences. Active provider session and command lifecycle are persisted domain state, not navigation state. Process restart restores the last safe surface; it does not replay commands or restart location service without user intent and current Android permission conditions.

## 5.4 Reusable feedback states

Loading, Content, Empty, Partial, Stale, Offline, Permission Required, Unsupported, Blocked, Error, Pending Action, Verified and Unknown Outcome. “Unknown outcome” is first-class: if a command was sent but cannot be confirmed, do not label it success or failure; guide the user to inspect the provider app before retrying.

## 5.5 Accessibility and driving safety

- Target WCAG 2.1 AA contrast criteria for text and controls where applicable, with Android-native semantics.
- At least 48 dp touch targets where practicable; do not crowd adjacent controls.
- TalkBack labels, roles, state and action descriptions are mandatory for custom controls.
- Support large font/display scaling without clipping critical values.
- Never use colour alone for health or confidence.
- Provide high contrast and reduced motion settings.
- Avoid auto-scrolling or animations that conceal controls.
- Driving mode hides dense AI prose and secondary analytics; parked mode permits detailed inspection.
- Test with TalkBack, Accessibility Scanner, large font and one-handed use on the actual phone.

# 6. Module Dependency Diagram

## 6.1 Target Gradle modules

Begin with a small set of real boundaries. Extract a provider-specific module only when it has an independent SDK, credentials, test boundary or release cadence.

~~~text
:app                         composition root and navigation shell
  |
  +--> :feature:map
  +--> :feature:offers
  +--> :feature:driver-mirror
  +--> :feature:driver-hub
  +--> :feature:sources
  +--> :feature:assistant
  |
  +--> :core:designsystem
  +--> :core:domain
  +--> :core:data
  +--> :core:database
  +--> :core:android
  +--> :core:map
  +--> :core:ai
  +--> :core:native
~~~

Responsibilities:

- **app:** dependency graph, root navigation and startup. No business calculations.
- **core:domain:** canonical IDs/value objects/events, capability contracts, validation and use-case ports; pure Kotlin.
- **core:data:** repositories, source registry, adapter orchestration, event ingestion and reconciliation.
- **core:database:** Room databases/DAOs, schema migrations, SQLCipher and export/backup boundaries.
- **core:android:** app discovery, package visibility, notifications, location, intents, foreground driver-session service, permission adapters and optional Accessibility adapter.
- **core:map:** MapLibre surface, state binding and overlay rendering; no provider business logic.
- **core:ai:** local model runtime, lifecycle, context assembler, retrieval, memory gateway, output parser and evaluation hooks.
- **core:native:** narrow Kotlin/Rust FFI wrapper; no UI or Android framework types.
- **core:designsystem:** tokens and shared Compose components.
- **feature modules:** feature state/reducers/ViewModels and UI composition. Call domain use cases/repositories through declared contracts.

## 6.2 Dependency rules

~~~mermaid
flowchart TD
  APP[app composition root]
  UI[feature UI modules]
  DS[core:designsystem]
  DOMAIN[core:domain]
  DATA[core:data and repositories]
  DB[core:database and Room]
  ANDROID[core:android platform adapters]
  MAP[core:map and MapLibre]
  AI[core:ai and local inference]
  NATIVE[core:native FFI]
  RUST[Rust deterministic core]
  PROVIDERS[provider connectors]

  APP --> UI
  APP --> DATA
  APP --> ANDROID
  APP --> AI
  UI --> DS
  UI --> DOMAIN
  UI --> MAP
  UI --> AI
  DATA --> DOMAIN
  DATA --> DB
  DATA --> PROVIDERS
  ANDROID --> DOMAIN
  AI --> DOMAIN
  MAP --> DOMAIN
  DATA --> NATIVE
  NATIVE --> RUST
~~~

**Forbidden dependencies**

- Domain must not depend on Android, Compose, MapLibre, Room, LiteRT-LM or provider SDKs.
- Feature modules must not depend directly on one another.
- ViewModels must not open databases or call provider SDKs directly.
- Map renderer must not parse Uber/Bolt/Google/Waze payloads.
- AI must not write product DAOs or execute commands.
- Provider adapters must not import feature UI types.
- Rust must not depend on Android framework, Room, Compose or provider SDKs.
- No cyclic Gradle module dependencies.
- No provider-specific parsing in canonical Driver Mirror UI.

## 6.3 Enforcement

- Gradle dependency locking and module dependency checks.
- Kotlin formatting/static analysis (Detekt/ktlint or equivalent, selected once).
- Android Lint and manifest permission review.
- Architecture tests (Konsist or equivalent) for package/import rules.
- Rust unit tests and JNI contract tests.
- Room schema export committed to version control.
- CI fails when features import provider SDKs or DAOs directly.
- Each new dependency receives a short ADR: licence, maintenance, APK/ABI size, privacy and reversal cost.

# 7. Risk Register

| Risk | Impact | Mitigation | Early-warning signal |
|---|---|---|---|
| Provider interfaces are incomplete, unstable or unavailable | Required live data/actions unavailable for one source | Capability-based adapters, official/documented paths first, honest partial states, separate source research | Increasing unknown/degraded capability state; provider update breaks parsing; no reproducible field data |
| Android/OEM background termination | Lost location or driver-session events | User-started foreground service, WorkManager for deferrable work, durable queue, Xiaomi/HyperOS soak tests, clear recovery | Session gaps, missed events, battery restrictions or restart loop |
| Local model consumes RAM/battery or is too slow | Map/navigation/provider apps become unstable | Small quantised model, lazy load, one inference, context cap, PSS/thermal budget, unload on pressure and deterministic fallback | PSS >1.2 GB target, OOM, TTFT >3s median, thermal throttle, provider app restart |
| Evidence fusion creates stale/false facts | Unsafe route context or wrong driver economics | Provenance, freshness/TTL, conflicts, completeness, deterministic calculations and golden tests | Rising conflicts/duplicates; stale data shown live; unsupported assertions |
| Storage migration/encryption breaks driver data | User loses offers, trips, earnings, imports or evidence | Snapshot migration, row/amount/null/hash reconciliation, rollback copy, key recovery and encrypted export test | Row-count mismatch, totals change, NULL becomes zero, DB/key errors |

# 8. Open Questions

These cannot be closed by architecture diagrams. Each has a small experiment and an evidence gate.

| Question | Cheapest experiment | Evidence required |
|---|---|---|
| Which LiteRT-LM model/backend works on the Redmi? | Isolated benchmark screen with pinned runtime and one 1B-class quantised model. CPU first; GPU/NPU only when supported libraries are verified. | Cold load, warm TTFT, tokens/sec, PSS, thermal/battery effect and stability while MapLibre/Uber/Bolt run. |
| Do local embeddings beat FTS5 enough to justify another model? | 30–50 user-approved cards in Polish/English/Turkish; compare FTS5 vs FTS5 + EmbeddingGemma 1 on labelled queries. | Precision@3, recall@3, latency, memory and whether results change the answer. |
| Can both SQLite stores migrate without loss? | Copy current DBs from a test install, run one-shot Room migration and compare rows, fingerprints, raw evidence links, NULLs and sums. | Machine-readable report with no unexplained difference and successful rollback. |
| Which Uber/Bolt capabilities are accessible on the daily phone? | Assess each provider independently: documented APIs, account exports, user-visible notifications and other explicitly permitted routes. Use App Observer only for authorised research evidence. | Per-capability matrix: app version, method, fields, timestamps, permission, terms review and missed-event rate. |
| Can Google Maps/Waze navigation data feed the BusyBee map in Poland? | Check current eligibility and provider terms; run a small approved data-sharing test or retain route handoff as fallback. | Whether route, traffic, ETA or session data may be received, cached and rendered on MapLibre under current licence/preview terms in Poland. |
| Does a driver session survive normal app closure on Xiaomi/HyperOS? | User-started 60–90 minute session: switch apps, lock screen, test low battery and OS process pressure; separately test process kill and force-stop. | Uptime, location gaps, notification state, battery and recovery. |
| Can provider screens be embedded inside BusyBee? | Inspect explicit target-app embedding support; test only with an app that opts in. | Target declares embedding support and working proof; otherwise use external launch/handoff. |

**Assumptions until experiments close**

- Android-first and local-first.
- Redmi Note 12 Pro 5G is the initial performance floor, not the final support matrix.
- No root, bootloader unlock, third-party APK repackaging, authentication bypass or request replay.
- No cloud LLM for normal operation.
- Provider data is stored only where current terms and applicable law permit it.
- Partial capability is acceptable; product value must not depend on every provider exposing every field.

# 9. Implementation Sequence and Exit Gates

Work in vertical slices. Do not pause product delivery for a wholesale rewrite.

## Slice 0 — Baseline and protection

- Tag the current source/build state.
- Capture test copies and schemas of both SQLite databases.
- Add migration fixtures and reconciliation report.
- Add architecture tests before package/module extraction.
- Keep the working MapLibre path.

**Exit:** reproducible baseline and no-loss migration fixture.

## Slice 1 — Canonical events and Provider Bridge

- Define ProviderId, ConnectorId, capability state, source method, evidence envelope, event/receive times and field completeness.
- Define canonical offer, trip, earnings, navigation session, road observation/event and command/verification contracts.
- Add adapter registry and health/coverage model.
- Adapt current notification capture and CSV import to emit canonical events.
- Build one unified activity timeline using existing data only.

**Exit:** notifications and CSV imports share one typed contract; UI does not parse provider strings; partial capability is visible.

## Slice 2 — Unified persistence and data integrity

- Add Room and schema export.
- Migrate both current SQLite stores non-destructively.
- Separate private product and public road cache boundaries.
- Add SQLCipher/Keystore only after build/licence/performance gate.
- Add idempotency, evidence links, event ordering and expiry.

**Exit:** migration report clean; data survives upgrade, process death and offline use.

## Slice 3 — Persistent driver session and real connectors

- Add user-started foreground DriverSessionService with visible notification and explicit Stop.
- Add location/Android signal adapters only for granted permissions.
- Add WorkManager for scheduled sync/export/maintenance.
- Test normal app closure, OEM restrictions, process death and recovery.
- Add one provider capability only after source and terms review.

**Exit:** background session works on the declared Android/OEM support matrix and reports interruptions correctly.

## Slice 4 — Compose UI and map intelligence

- Extract design tokens and reusable components.
- Migrate shell incrementally to Compose.
- Preserve MapLibre through MapSurface interop.
- Connect map to canonical layers and source health.
- Add Driving/Parked modes and adaptive layouts.

**Exit:** map works; feature screens use repositories and immutable state; accessibility and screenshot tests pass.

## Slice 5 — Local AI foundation

- Add LiteRT-LM behind LocalModelRuntime.
- Benchmark 1B-class candidate on Redmi.
- Add model download/checksum/licence management and lazy lifecycle.
- Implement context builder with 1,200/1,600 token limits.
- Start with one task: explain an already deterministic offer/route result using cited local evidence.
- Add strict parser, timeout, fallback and local golden set.
- Add FTS5 preference memory; add embeddings only if retrieval experiment passes.

**Exit:** AI cannot mutate canonical data or execute provider actions; output is evidence-linked; model unload/fallback does not break BusyBee.

## Slice 6 — Operator command path

- Add command router with distinct observation and action adapters.
- Start with low-risk, verifiable actions such as launching a navigation provider with a destination.
- Command lifecycle: Created → AwaitingConfirmation → Dispatched → Observed → Verified / Failed / Unknown.
- Require explicit confirmation for external side effects.
- Add provider actions only when documented/supportable.

**Exit:** each action declares mechanism, permission, confirmation, timeout, verification method and safe unknown-outcome behaviour.

---

# Targeted Reference Research

These references supply platform principles and known failure modes. Recheck version, licence and eligibility when implementing each slice.

1. Android architecture recommendations — repositories, UDF, ViewModels and lifecycle-aware state:  
   https://developer.android.com/topic/architecture/recommendations
2. Android modularization:  
   https://developer.android.com/topic/modularization
3. Compose custom design systems:  
   https://developer.android.com/develop/ui/compose/designsystems/custom
4. Adaptive window size classes:  
   https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes
5. Compose accessibility and minimum touch targets:  
   https://developer.android.com/codelabs/jetpack-compose-accessibility
6. Android Accessibility Service:  
   https://developer.android.com/guide/topics/ui/accessibility/service
7. Cross-application Activity Embedding and target opt-in:  
   https://developer.android.com/develop/ui/views/layout/activity-embedding
8. Android package visibility:  
   https://developer.android.com/training/package-visibility/declaring
9. Foreground-service background-start restrictions and while-in-use permission constraints:  
   https://developer.android.com/develop/background-work/services/foreground-services/restrictions-bg-start
10. Foreground-service types, including location:  
    https://developer.android.com/about/versions/14/changes/fgs-types-required
11. WorkManager persistent work and retries:  
    https://developer.android.com/develop/background-work/background-tasks/persistent
12. Room and local SQLite persistence:  
    https://developer.android.com/training/data-storage/room
13. Incremental migration from SQLite to Room:  
    https://developer.android.com/training/data-storage/room/sqlite-room-migration
14. SQLCipher for Android (verify version, licence and ABI before adoption):  
    https://github.com/sqlcipher/sqlcipher-android
15. Android backup best practices:  
    https://developer.android.com/privacy-and-security/risks/backup-best-practices
16. LiteRT-LM Android:  
    https://developers.google.com/edge/litert-lm/android
17. LiteRT overview:  
    https://developers.google.com/edge/litert/overview
18. EmbeddingGemma model overview:  
    https://gemma.ai/models/embeddinggemma/

---

## Architecture Acceptance Checklist

- [ ] Existing driver/import data migrates with reconciled row counts, values, NULLs and raw evidence.
- [ ] Every source declares acquisition method, capabilities, licence, retention and health.
- [ ] UI/analytics consume canonical records, never provider-specific payloads.
- [ ] A source outage does not blank the map or erase the Driver Mirror.
- [ ] Background session is user-started, visible, permission-aware and interruption-aware.
- [ ] Force-stop and revoked-permission behaviour are represented honestly.
- [ ] MapLibre remains independent of basemap, routing and navigation-data providers.
- [ ] Compose tokens/components are enforced by architecture tests and lint.
- [ ] Driving UI is glanceable and passes accessibility checks.
- [ ] Local model loads lazily, is measured on the pilot device and can be unloaded.
- [ ] Model output is parsed, validated, grounded and has deterministic fallback.
- [ ] No raw transcript accumulator exists.
- [ ] Memory is explicit, compact, deduplicated and user-controllable.
- [ ] Product data and AI memory have separate repositories, retention and backup rules.
- [ ] No command is successful until independently verified.
- [ ] Model, provider, map and storage implementations can be replaced behind declared seams.

**Status rule:** This document is a proposal based on the inspected repository and current platform research. Compose, Room, LiteRT-LM, the unified Provider Bridge, SQLCipher and persistent driver-session service are not claimed to be implemented. Each becomes implemented only after its slice passes the exit gate and exact evidence is recorded in the repository phase log.
