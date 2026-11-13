
## R-15 — MapLibre and speech callbacks outliving Activity state (source-confirmed risk; runtime impact unverified, 2026-11-13)

**Status: Candidate — not reproduced; not attributed to BB-002.** A follow-up read of the current PR #8 head (0885934a7d002cf202838cc67d67d8261148bdf3) found asynchronous callbacks outside the worker dispatcher coverage:

- MapView.getMapAsync callback records diagnostics, stores the map and calls setStyle; its style-loaded callback marks readiness, updates camera/view state, renders annotations and starts road/location work without checking whether the Activity is finishing or destroyed.
- The TextToSpeech initialization callback mutates speechReady and configures the TTS engine without checking Activity lifetime. onDestroy() shuts the engine down, but an already-pending initialization callback is a separate asynchronous completion path.
- The LocationListener caches a fix and calls updateDriverLocation. onPause() removes location updates, but a callback already queued before removal can still arrive; updateDriverLocation checks finishing/destroyed and map-style readiness but not whether the Activity is resumed.

These are source-level lifecycle gaps, not evidence that MapLibre or TTS caused the reported exception. The worker dispatcher added earlier covers only the seven worker-originated UI callbacks. Next narrowly scoped hardening: route MapLibre/TTS completions through the same pre-post and execution-time liveness guard, and prevent a late location callback from touching the paused map while retaining its location fix. Add focused regression coverage where the lifecycle contract can be tested without a real MapLibre renderer. Device validation remains required.
