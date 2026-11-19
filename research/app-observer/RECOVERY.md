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
- Draft PR: [#18](https://github.com/ysfclc/BusyBee/pull/18)
- This branch is deliberately separate from the older draft PR #17 branch, which contains unrelated BusyBee-side PCAP/HAR ingestion work. Do not merge that branch as part of this App Observer task.

## Implemented in source on this branch

- Explicit session lifecycle and selected-app filtering.
- Read-only Accessibility UI timeline for the selected package, with optional visible text capture.
- Exclusion of editable and password fields.
- Common phone, email and token redaction before visible text is persisted.
- Selected-app notification evidence when visible text is opted in.
- PCAPdroid CaptureCtrl API integration for start/stop and package filtering.
- PCAPdroid UDP exporter receiver bound to loopback only.
- Classic PCAP output with a global-header link type selected from the first valid packet: raw IP in the default exporter mode, Ethernet when the optional PCAPdroid metadata extension is present.
- A single link type per PCAP file; an unexpected change is recorded in the session log and the incompatible datagram is excluded rather than corrupting the file.
- Packet-level JSONL index with selected-package identity, endpoints, ports, protocol, payload sizes and timestamps; UID is only available when the optional PCAPdroid extension is present.
- ZIP export containing session metadata, UI timeline, network event index, PCAP and Android capability report.
- Unit tests for raw-IP and Ethernet PCAP headers, UDP-exporter packet parsing, metadata-extension parsing and malformed datagrams.

## Latest CI verification — 2026-11-19

- Branch head: `ae8cefb516f2e9f255b250a7d62efc57410a876f`
- Draft PR: [#18](https://github.com/ysfclc/BusyBee/pull/18)
- GitHub Actions run: [#46](https://github.com/ysfclc/BusyBee/actions/runs/44595753635) — **passed**
- Android unit tests: passed
- Debug APK assembly: passed
- APK v2 signature verification: passed
- Artifact upload: passed
- Package: `com.ysfclc.appobserver.debug`
- Version: `0.2.0-observation-debug` (versionCode 2)
- APK SHA-256: `0b7ed28f99d8efcc8468f2eb9bffcf11234c1db5e13ab80756359d5b36e22207`
- Workflow artifact: `14564359227`, `app-observer-evidence-recorder-46` (temporary, expires 2026-12-03)

The artifact was downloaded and its APK SHA-256 was independently checked against the workflow's `SHA256SUMS` file.

## Not yet verified on a real device

- PCAPdroid interoperability on a physical Android device.
- Actual packet delivery, TLS decryption outcomes and packet-to-screen correlation against Uber/Bolt.
- OEM-specific Accessibility service reliability when App Observer is backgrounded.

The APK build and automated tests are verified; end-to-end device observation is not. Do not claim real Uber/Bolt packets have been captured until a physical-device evidence ZIP proves it.

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
