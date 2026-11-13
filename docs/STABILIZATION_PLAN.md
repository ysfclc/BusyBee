
## Follow-up source review — MapLibre, TTS and location lifecycle (2026-11-13)

The worker callback mitigation does not cover all asynchronous completions in MainActivity.kt:

| Path | Current behavior | Lifecycle gap | Status |
|---|---|---|---|
| MapView.getMapAsync / Style loaded callback | Stores the map, changes camera, renders road annotations, starts refresh and location work | No finishing/destroyed check around either callback | Source-confirmed candidate; no runtime reproduction |
| TextToSpeech initialization callback | Mutates speechReady and configures language | Can complete after onDestroy() has shut down the engine | Source-confirmed candidate; no runtime reproduction |
| LocationListener.onLocationChanged | Caches fix and calls map update | A queued callback may arrive after onPause() removed updates; update path does not require RESUMED state | Source-confirmed candidate; no runtime reproduction |

This review does not identify the user's IllegalStateException root cause and does not change BB-002's status. The next permitted stabilization increment is narrowly limited to these callback lifetime boundaries, with tests for the lifecycle helper and source read-back. Do not expand into navigation behavior, MapLibre rendering changes or UI work. Physical-device interruption tests remain mandatory.
