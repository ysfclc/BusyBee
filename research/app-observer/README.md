# App Observer

App Observer is a separate Android research recorder used to collect the evidence needed to design and build BusyBee integrations. It observes one selected driver/navigation app during an explicit session and correlates two streams:

1. **Android UI evidence:** window transitions, visible UI labels and selected-app notifications (visible text is optional and off by default).
2. **PCAPdroid network evidence:** packets streamed from PCAPdroid's UDP exporter, filtered to the selected app, written to a standard PCAP file.

It is not a BusyBee dashboard, does not control third-party apps, and does not upload evidence.

## Research targets

- Uber Driver — `com.ubercab.driver`
- Bolt Driver — `ee.mtakso.driver`
- Waze — `com.waze`
- Google Maps — `com.google.android.apps.maps`

Only the selected package is used for the UI observer and PCAPdroid's `app_filter`.

## PCAPdroid integration

App Observer uses PCAPdroid's documented CaptureCtrl activity API:

- Requests capture start/stop through an Android activity result, so PCAPdroid can ask the user for consent.
- Starts `udp_exporter` mode filtered to the selected package.
- Sends packets to a UDP socket bound only to `127.0.0.1:5123`.
- Requests PCAPdroid's packet metadata extension, full payloads and TLS decryption when the payload option is enabled.
- Removes the PCAPdroid per-packet metadata trailer and writes the packet stream as a standard Ethernet PCAP file.
- Records packet timestamps, app identity/UID, IP endpoints, ports, transport protocol and payload lengths in a JSONL index.

The integration does not silently approve PCAPdroid, bypass certificate pinning, extract credentials, or replay requests. TLS decryption is subject to PCAPdroid's consent flow and the selected app's TLS controls. Encrypted payloads remain encrypted when PCAPdroid cannot decrypt them.

PCAPdroid must be installed separately. Stable and debug package IDs are detected:
- `com.emanuelef.remote_capture`
- `com.emanuelef.remote_capture.debug`

## UI observation

The Accessibility service is read-only and only records data when a research session is active and the event belongs to the selected package. It captures bounded UI snapshots, view IDs, class names, screen bounds and state flags. It never clicks, swipes, enters text or performs gestures.

The **Include visible UI labels and selected-app notification text** option is off by default. When enabled:
- Visible, non-editable, non-password node text and content descriptions can be stored.
- Selected-app notification title/text can be stored.
- Common email, phone, bearer-token and long-token patterns are masked.
- Names, addresses, fares and other contextual details may still be present. Automatic redaction is not complete; inspect the evidence bundle before sharing it.

Notification access and Usage Access remain optional. Accessibility access is needed for UI observation. PCAPdroid network capture is independently consented to inside PCAPdroid.

## How to collect evidence for BusyBee

1. Install App Observer and PCAPdroid.
2. In Android Accessibility settings, enable **App Observer read-only UI observer**.
3. Optionally enable Notification Access if notification evidence is useful.
4. Open App Observer and select Uber Driver or Bolt Driver.
5. Enable visible UI labels only when you are comfortable capturing that data. Keep the option off to collect UI structure without visible text.
6. Leave **Request full network payloads and TLS decryption** enabled for API research, or turn it off for connection-level evidence only.
7. Tap **Start UI + PCAPdroid session** and accept PCAPdroid's capture-consent prompt.
8. Use the selected driver app normally: launch it, go online, wait for an offer, inspect/accept/decline as appropriate, and observe the trip lifecycle. Keep the session focused on one app and one scenario.
9. Return to App Observer and tap **Stop Session**.
10. Tap **Export Last Evidence ZIP**, review the contents and remove anything you do not want to share.
11. Attach the ZIP to the BusyBee engineering conversation. The UI and packet timestamps let us correlate what appeared on screen with what the app sent/received.

## Evidence bundle

The ZIP can contain:

- `session.json` — target app, device, consent options, PCAPdroid version/status and session timing.
- `events.jsonl` — session lifecycle and receiver status.
- `ui-events.jsonl` — selected-app UI snapshots and optional visible labels/notification text.
- `network-events.jsonl` — packet-level metadata.
- `network.pcap` — standard Ethernet PCAP with packet payloads as captured by PCAPdroid.
- `device-capability-report.json` — Android app/version/permission and observer capability report.
- `REVIEW-BEFORE-SHARING.txt` — reminder to inspect the bundle for personal data.

All evidence stays in App Observer's private app storage until the user exports it. The app does not send network data to a remote endpoint; its `INTERNET` permission is used for the loopback UDP receiver only. Android may show the foreground-capture notification while a session is running.

## What the evidence does and does not tell us

A PCAP file gives us the actual packet stream and timestamps, but it is not automatically a decoded API schema. TLS payloads are readable only when PCAPdroid successfully decrypts them. HTTP/2, QUIC, protobuf, compression, binary formats and app-specific framing still need protocol-aware analysis. App Observer deliberately exports source evidence instead of inventing meanings for unknown fields.

## Build

From repository root:

```sh
chmod +x android/gradlew
android/gradlew -p research/app-observer/android --no-daemon testDebugUnitTest assembleDebug
```

Debug APK:

`research/app-observer/android/app/build/outputs/apk/debug/app-debug.apk`

Package:

`com.ysfclc.appobserver.debug`

## Privacy and boundaries

- No automatic upload or analytics.
- No reading of provider private databases or other apps' private files.
- No credential collection by design, request replay, TLS pinning bypass, process injection or third-party app actions.
- Packet captures and selected-app text can contain personal data. Review before sharing and delete sessions you no longer need.
