# BusyBee Local Intelligence Catalogue

## Purpose

The Local Intelligence Catalogue is a provider-neutral registry of model metadata, runtime capabilities, device requirements, and task capabilities. It is not a model marketplace UI and is not itself an inference engine.

It is a subsystem of BusyBee's intelligence plane, separate from:
- the Native Provider Bridge, which acquires external provider data and actions;
- the evidence and observation pipeline, which records what was observed;
- the Unified Activity Ledger, which persists provenance and canonical events;
- deterministic business logic, which calculates fares, costs, rankings, state transitions, and validates extracted values.

## Initial implementation

`LocalIntelligenceCatalogue.kt` introduces:
- `ModelDescriptor`: ID, version, format, size, licence, capabilities, requirements, installed path and optional source URI.
- `RuntimeDescriptor`: runtime identity, supported model formats and availability.
- `DeviceCapabilityProfile`: RAM, storage, ABI, NPU, thermal and battery facts.
- `LocalInferenceRuntime`: adapter contract for runtime compatibility checks.
- `IntelligenceTask` and `IntelligencePrivacyPolicy`: task capability and explicit execution constraints.
- `LocalIntelligenceCatalogue`: in-memory registry and conservative target selection.

## Routing and privacy rules

1. Only installed models are eligible for local selection.
2. A local model is eligible only if a registered runtime reports AVAILABLE, supports its format, and confirms device compatibility.
3. Cloud execution requires BOTH a task-level `allowCloud` opt-in and the `CLOUD_ALLOWED` policy.
4. `ASK_BEFORE_CLOUD` never silently selects cloud. The caller must obtain approval and then submit an explicitly permitted task.
5. LOCAL_ONLY and LOCAL_FIRST never silently send data to a cloud service.
6. If no eligible target exists, the catalogue returns a local target with an explanatory reason; the caller must handle unsupported capability gracefully.
7. Catalogue selection is not proof of runtime execution. The execution adapter must report actual success/failure and resource use.

## Design principles

- Capabilities are not the same thing as models. A model may implement multiple capabilities; a capability may be supplied by multiple models or non-LLM engines.
- Deterministic calculations and state transitions stay in native code. Do not use an LLM for fare arithmetic, idempotency, ledger integrity, or action confirmation.
- Model metadata must include licence and provenance before any download/distribution feature is added.
- Device suitability must be measured on real hardware. Declared requirements are only initial filters; runtime benchmarks, thermal behavior, memory pressure and battery impact must inform later ranking.
- Model discovery must not scan arbitrary private storage. Future discovery should use explicit app-managed model directories, supported document providers, or user-selected files.
- No model downloads, inference engines, network calls, background services, or UI are introduced in this slice.

## Device profiler increment

`DeviceCapabilityProfiler.kt` now reads total RAM, app-volume free storage, supported Android ABIs, thermal status where supported, and battery percentage where available. It deliberately reports NPU availability as unknown because there is no reliable vendor-neutral Android probe. Missing measurements remain null.

`ModelDeviceCompatibility` centrally rejects models whose declared minimum RAM/storage, ABI requirements, or NPU requirements are not met. Unknown values do not satisfy a declared requirement. Runtime adapters still perform their own deeper compatibility checks.

## Planned increments

1. **Registry contract and profiler (implemented):** metadata, runtime adapter contract, Android device profile, conservative hardware filtering, privacy-aware routing, unit tests.
2. **Runtime adapters:** integrate selected engines independently, beginning with one runtime proven on supported devices.
4. **Model inventory:** enumerate app-managed and user-authorized models; verify file format, size, digest and licence metadata.
5. **Benchmark and suitability:** measure load time, tokens/sec or task latency, peak memory, thermal throttling and battery impact.
6. **Task execution:** typed input/output contracts, cancellation, resource limits, provenance, and error handling.
7. **Orchestration:** route each task to deterministic code, local model, or explicitly approved cloud execution.
8. **UI catalogue:** explain installed/available models, capabilities, storage cost, compatibility, privacy and benchmark results.

## Acceptance criteria for this slice

- Model IDs and runtime IDs are stable registry keys.
- Re-registering an ID replaces its descriptor rather than duplicating it.
- Uninstalled models are never selected.
- Unavailable or incompatible runtimes are never selected.
- Cloud is never selected without both explicit task opt-in and CLOUD_ALLOWED policy.
- ASK_BEFORE_CLOUD does not count as approval.
- The catalogue has no dependency on the Activity Ledger or provider-specific adapters.
