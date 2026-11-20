# App Observer Capture

App Observer Capture is a separate Android research recorder used to collect evidence for BusyBee engineering. It correlates a manually selected driver/navigation app's read-only UI timeline with packet traffic exported by PCAPdroid.

It is intentionally separate from BusyBee and from the preserved App Observer Lab 0.1 survey app. Its CI/debug package is com.ysfclc.appobserver.capture.debug so it can be installed side-by-side with the survey build.

## What it records
- One explicitly selected target at a time: Uber Driver, Bolt Driver, Waze or Google Maps.
- Read-only Accessibility UI structure and lifecycle events for only the selected package.
- Optional visible UI and notification text, off by default.
- PCAPdroid packet capture restricted with app_filter to the selected package.
- Packet timestamps, IP endpoints, ports, transport protocol and payload lengths.
- A local evidence ZIP with session metadata, UI timeline, network index, PCAP and capability report.

The recorder never clicks, swipes, enters data or replays requests. It does not upload anything. Session data stays in App Observer's private storage until explicitly exported.

## PCAPdroid
Install PCAPdroid separately. App Observer uses its documented CaptureCtrl Activity API and asks PCAPdroid to start/stop capture through Android's activity-result consent flow.

The recorder uses TCP Exporter (PCAP-over-IP) and listens on loopback 127.0.0.1:5123. TCP is selected because PCAPdroid documents it as reliable; UDP can drop or reorder packets. The target package is passed to app_filter.

Full payload capture and TLS decryption are off by default. Enabling them is an explicit research choice. TLS decryption may fail for apps that enforce certificate pinning or other platform protections; no bypass is attempted. Captures can contain account, trip, location or other sensitive information.

## Focused observation
1. Install App Observer Capture and PCAPdroid.
2. Enable App Observer's read-only Accessibility service.
3. Select one installed target app.
4. Leave visible text and full-payload/TLS options off unless the specific test requires them.
5. Start a session and approve PCAPdroid's capture prompt.
6. Use the selected app normally for one controlled scenario.
7. Stop the session in App Observer Capture.
8. Export the evidence ZIP and inspect it before sharing.

A useful first scenario is app launch → online/available → wait for a test event → open its details → close/return. Do not accept a real ride or trigger a paid action solely for this test.

## Evidence layout
- session.json: target, device, versions, consent options and session timing.
- events.jsonl: session and receiver lifecycle.
- ui-events.jsonl: selected-app UI events and optional visible text.
- network-events.jsonl: per-packet metadata index.
- network.pcap: standard PCAP stream received from PCAPdroid TCP Exporter.
- device-capability-report.json: Android access and installed app report.
- REVIEW-BEFORE-SHARING.txt: privacy reminder.

## Build
From the repository root:

    chmod +x android/gradlew
    android/gradlew -p research/app-observer-capture/android --no-daemon testDebugUnitTest assembleDebug

CI debug APK is a test artifact, not a stable update channel; CI debug signing keys can differ between runs.