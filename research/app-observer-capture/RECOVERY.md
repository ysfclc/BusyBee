# App Observer Capture — recovery and acceptance record

## Product boundary

App Observer is a generic, read-only Android research instrument for controlled observation of user-selected apps. It is not BusyBee production UI and does not perform actions in target apps. BusyBee remains the driver product and owns canonical driver events.

The original survey tool at `research/app-observer` remains a separate preserved baseline. Do not replace or merge its data/package identity with this capture app.

## Current candidate

- Source project: `research/app-observer-capture/android`
- Source imported from reset-branch commit: `8d8a9945c88352e85ee58ae3d282533c20fb3363`
- Stable release package: `com.ysfclc.appobserver.capture`
- Candidate version: `0.6.0-scene-graph` (versionCode 9)
- Side-by-side debug package: `com.ysfclc.appobserver.capture.generic.viewer.diagnostics.debug`
- Debug version: `0.6.0-scene-graph-debug`
- Android: min SDK 26, target SDK 35, Java 17
- Persistent release certificate SHA-256: `6c268b04e8deef08ffd000a22cbdf21ec4ab47a816838a59e6fa71ce18bcec6a`

The versionCode is incremented from the previously documented 0.5.0 / versionCode 8 candidate. Preserve the same application ID and persistent signing key for stable updates. Debug builds use a separate package identity and can be installed beside the stable app.

## Research capabilities

- Dynamically discover launcher-visible apps without `QUERY_ALL_PACKAGES`; manually select one target per session.
- Read-only Accessibility structure and event observation.
- User-visible screenshot capture where Android permits it, with capture failures recorded.
- On-device OCR and image labelling; raw OCR strings are not persisted by the inference stream.
- A generic scene graph with geometry/hierarchy relations and evidence provenance. This is a structural representation, not a semantic understanding of the target app.
- Recurring-state clustering and correction-trained local classification using privacy-safe features. Scores are provisional, not calibrated probabilities.
- Optional notification content only when an explicit meaning rule authorises it; otherwise notification envelope metadata.
- Optional PCAPdroid packet-header metadata only. No raw PCAP, raw IP addresses, TLS decryption or application payload capture.
- Local evidence and model storage with user-initiated export/import.

No action executor, request replay, target-app automation, provider-process instrumentation or third-party APK modification is included.

## Verification status

The source reset branch was CI-verified at commit `8d8a9945c88352e85ee58ae3d282533c20fb3363`:
- App Observer workflow #328 passed tests, debug/release compilation, package/version checks and ZIP alignment.
- The workflow uploaded an unsigned release candidate because persistent Actions signing secrets were absent.
- The exact clean-mainline 0.6.0 candidate is pending its own CI run.
- Physical Redmi/Android 14 acceptance, screenshot coverage across representative apps, PCAPdroid interoperability, classifier accuracy, battery/latency and privacy acceptance remain unverified.

A CI build does not establish that screenshots work for every app or that inferred concepts/business events are correct.

## Build

From the repository root:

```sh
chmod +x android/gradlew
android/gradlew -p research/app-observer-capture/android --no-daemon testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease
```

Debug APK:
`research/app-observer-capture/android/app/build/outputs/apk/debug/app-debug.apk`

Unsigned release candidate (when persistent signing secrets are absent):
`research/app-observer-capture/android/app/build/outputs/apk/release/app-release-unsigned.apk`

## Phone acceptance runbook

1. Install the side-by-side debug APK; do not uninstall the stable app or App Observer Lab.
2. Enable App Observer's read-only Accessibility service.
3. Grant notification access only if the session needs notification evidence. Notification text is not collected unless an explicit rule authorises it.
4. Select one launchable app and start a manual observation session.
5. Exercise a repeatable workflow, including a state change and a cancellation/failure where available.
6. Return to App Observer, inspect screenshots, scene graph, evidence provenance and source-availability diagnostics.
7. Stop the session and export the evidence bundle. Review screenshots and any explicitly authorised text before sharing.
8. Repeat with App Observer's self-test target and at least one unrelated app.

Keep the stable app's private data and signing identity intact. Do not install an unsigned release candidate as an update. Use the debug package for side-by-side testing until a signed version 9 release is produced and verified.

## Release signing

The release JKS and passwords are private and must never enter the repository. Automated stable updates require the existing four Actions secrets documented in `docs/APP_OBSERVER_PERSISTENT_SIGNING.md`. If they remain unavailable, an unsigned release candidate is not installable as an update; do not substitute a new signing key.
