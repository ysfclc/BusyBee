# App Observer — Generic Behaviour Recorder

App Observer is a separate Android research recorder for BusyBee engineering. It observes any user-selected app that Android exposes through the launcher-app query; there is no Uber/Bolt/Waze/Maps allowlist. An explicit **App Observer (self-test)** target is also available to verify the recorder against its own UI.

## What it records and renders

- One explicitly selected app per manually started session.
- Read-only Accessibility event timeline and structure-only UI snapshots.
- An on-device live evidence viewer showing the latest visible control tree, resource IDs, bounds, control flags and recent event timeline.
- Structural screen fingerprints that group repeat captures of the same hierarchy/control pattern. These are structural matches, not semantic screen understanding.
- Interaction source-node structure where Android exposes it.
- Notification envelope metadata for the selected app, without notification content.
- Optional PCAPdroid TCP-exporter packet-header metadata: time, app identifier, IP version, protocol, ports, flags and byte counts, shown as a readable timeline.

After starting a session, switch to the selected target app and use it normally, then return to App Observer to see live evidence. UI structure capture is independent of PCAPdroid; network metadata appears only after PCAPdroid has been approved and packet headers are actually received.

## Privacy baseline

The recorder does not store visible UI text, typed values, notification titles or bodies, screenshots, clipboard data, URLs, domains, IP addresses, headers, cookies, tokens, application payloads or raw PCAP files.

Only a bounded packet-header prefix is read; the remainder is drained in a small buffer that is wiped after use. The prefix is wiped after parsing. Allowlisted header metadata is retained and all packet content is discarded. Full-payload capture and TLS decryption are always disabled. New sessions do not create network.pcap; evidence export excludes any legacy raw PCAP and sanitises JSONL fields again before sharing.

Data stays in App Observer's private storage until the user explicitly exports the evidence ZIP. There is no upload path.

## Android app discovery

The picker queries installed apps with a MAIN / LAUNCHER activity. This avoids the restricted QUERY_ALL_PACKAGES permission. Hidden, disabled and non-launchable packages are therefore outside the picker. App Observer itself is added explicitly as a self-test target at the bottom of the list. The AccessibilityService checks the selected package before evidence is written.

## Observation flow

1. Install App Observer and optionally PCAPdroid.
2. Enable App Observer's read-only Accessibility service.
3. Optionally enable notification access for notification envelope metadata.
4. Select any app shown in the dynamic picker, or choose App Observer (self-test).
5. Start a session and approve PCAPdroid's capture-consent prompt if available.
6. Switch to the selected app and use it normally. No actions are performed by App Observer.
7. Return to App Observer to inspect the latest structural evidence and repeated fingerprints.
8. Stop the session and optionally export the privacy-minimised evidence ZIP.

## Evidence layout

- session.json: selected target, Android API, session mode and privacy flags.
- events.jsonl: receiver and session lifecycle.
- ui-events.jsonl: structure-only UI and notification metadata, including structural fingerprints.
- network-events.jsonl: allowlisted packet-header metadata only.
- device-capability-report.json: minimal Android access/capability report.
- PRIVACY-AND-REVIEW.txt: export privacy summary.

## Scope boundary

This is an observation instrument and a first exact-structure matching aid for a future state-transition classifier. It does not yet classify semantic behaviour, learn business events, infer the meaning of controls, or execute reusable automations. A matching fingerprint means the captured hierarchy and control-state pattern match, not that the screen's business meaning is understood. It never taps, swipes, types, replays network requests or controls the target app.

## Build

From the repository root:

    chmod +x android/gradlew
    android/gradlew -p research/app-observer-capture/android --no-daemon testDebugUnitTest assembleDebug

The live-viewer debug variant uses package ID `com.ysfclc.appobserver.capture.generic.viewer.debug` and app label **App Observer Live Viewer**. It installs beside earlier App Observer packages and is visually distinguishable in the launcher. CI debug APKs still use ephemeral signing keys; a later build may need side-by-side installation or a persistent signing setup for seamless updates.
