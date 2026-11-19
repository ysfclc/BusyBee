# App Observer recovery and project state

## Product purpose

App Observer is a separate Android research recorder whose job is to provide real evidence for BusyBee engineering. It correlates the selected driver's app UI lifecycle with network packets captured through PCAPdroid.

It is not a standalone diagnostics dashboard, not the BusyBee app, and not just a HAR importer.

## Source of truth

- Repository: `ysfclc/BusyBee`
- Isolated working branch: `research/app-observer-observation-2026-11-19`
- Base: `main`
- App source: `research/app-observer/android`
- Debug package: `com.ysfclc.appobserver.debug`
- Debug APK: `research/app-observer/android/app/build/outputs/apk/debug/app-debug.apk`
- This branch is deliberately separate from the older draft PR #17 branch, which contains unrelated BusyBee-side PCAP/HAR ingestion work. Do not merge that branch as part of this App Observer task.

## Implemented in source on this branch

- Explicit session lifecycle and selected-app filtering.
- Read-only Accessibility UI timeline for the selected package, with optional visible text capture.
- Exclusion of editable and password fields.
- Common phone, email and token redaction before visible text is persisted.
- Selected-app notification evidence when visible text is opted in.
- PCAPdroid CaptureCtrl API integration for start/stop and package filtering.
- PCAPdroid UDP exporter receiver bound to loopback only.
- PCAPdroid extension/FCS removal and standard Ethernet PCAP output.
- Packet-level JSONL index with app identity/UID, endpoints, ports, protocol, payload sizes and timestamps.
- ZIP export containing session metadata, UI timeline, network event index, PCAP and Android capability report.
- Unit tests for UDP-exporter packet parsing and extension stripping.

## Not yet verified

- The Android Gradle build and unit tests on this branch.
- PCAPdroid interoperability on a physical Android device.
- Actual packet delivery, TLS decryption outcomes and packet-to-screen correlation against Uber/Bolt.
- OEM-specific Accessibility service reliability when App Observer is backgrounded.

Do not describe these items as complete until the workflow/device evidence exists.

## Build

```sh
chmod +x android/gradlew
android/gradlew -p research/app-observer/android --no-daemon testDebugUnitTest assembleDebug
```

## Acceptance test on device

1. Install App Observer and PCAPdroid.
2. Enable App Observer's Accessibility service.
3. Open App Observer, select Uber Driver or Bolt Driver, and start a session.
4. Approve PCAPdroid's capture request.
5. Use the selected app through several visible state changes and one network event.
6. Stop the session and export its ZIP.
7. Confirm that `session.json`, `ui-events.jsonl`, `network-events.jsonl` and `network.pcap` exist when the relevant capture channels were enabled.
8. Open the PCAP in Wireshark or another analyzer and compare timestamps with the UI timeline.
9. Review for personal data before sharing the ZIP.

## Safety and scope guard

- No third-party app actions, clicks, gestures, request replay, credential collection, process injection, private database access or TLS pinning bypass.
- The user explicitly starts each session and approves PCAPdroid's capture.
- Visible UI/notification text is an optional explicit session setting.
- Evidence remains local until the user exports it. The ZIP can contain sensitive information and must be reviewed before sharing.
- Keep all research capture and analysis in this App Observer project unless a separate, explicit BusyBee integration task is authorized.
