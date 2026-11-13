
## Session handoff — 2026-11-13 (remaining asynchronous lifecycle paths)

- **Done:** Read the current PR #8 MainActivity.kt end-to-end and traced getMapAsync / style-loaded, TextToSpeech initialization and location-listener completion paths.
- **Finding:** Three additional Activity-owned asynchronous paths are not covered by LifecycleSafeUiDispatcher: MapLibre map/style callbacks can mutate map and view state after Activity teardown; TTS initialization can complete after shutdown; and a location callback already queued before onPause() removes updates can still reach map mutation while the Activity is paused. These are source-confirmed lifecycle gaps, not reproduced failures and not an assigned cause for BB-002.
- **State changed:** Documentation only. No code, UI, database, APK or PR merge changed in this review step. STOP-WORK remains active.
- **Next:** Apply only narrow lifecycle-boundary guards to those callback paths, add focused regression coverage where possible, and verify the complete source read-back. Do not claim BB-002 fixed without the original DIAG/logcat and device reproduction.
- **Risks/landmines:** The MapLibre native renderer is not exercised by the existing boolean readiness test. A Robolectric dispatcher test cannot substitute for physical-device MapLibre lifecycle validation.
