# App Observer — Generic Behaviour Recorder

App Observer is a separate Android research recorder for BusyBee engineering. It observes any user-selected app that Android exposes through the launcher-app query; there is no Uber/Bolt/Waze/Maps allowlist. App Observer itself is excluded from the target list.

## What it records

- One explicitly selected app per manually started session.
- Read-only Accessibility event timeline and structure-only UI snapshots.
- Interaction source-node structure where Android exposes it.
- Notification envelope metadata for the selected app, without notification content.
- Optional PCAPdroid TCP-exporter packet-header metadata: time, app identifier, IP version, protocol, ports, flags and byte counts.

## Privacy baseline

The recorder does not store visible UI text, typed values, notification titles or bodies, screenshots, clipboard data, URLs, domains, IP addresses, headers, cookies, tokens, application payloads or raw PCAP files.

Packet bytes arrive from PCAPdroid and are inspected in memory only to derive allowlisted header metadata. They are then discarded. Full-payload capture and TLS decryption are always disabled. New sessions do not create network.pcap; evidence export excludes any legacy raw PCAP and sanitises JSONL fields again before sharing.

Data stays in App Observer's private storage until the user explicitly exports the evidence ZIP. There is no upload path.

## Android app discovery

The picker queries installed apps with a MAIN / LAUNCHER activity. This avoids the restricted QUERY_ALL_PACKAGES permission. Hidden, disabled and non-launchable packages are therefore outside the picker. The AccessibilityService does not use a fixed package filter; the active session's selected package is checked in code before evidence is written.

## Observation flow

1. Install App Observer and optionally PCAPdroid.
2. Enable App Observer's read-only Accessibility service.
3. Optionally enable notification access for notification envelope metadata.
4. Select any app shown in the dynamic picker.
5. Start a session and approve PCAPdroid's capture-consent prompt if available.
6. Use the selected app normally. No actions are performed by App Observer.
7. Stop the session and optionally export the privacy-minimised evidence ZIP.

## Evidence layout

- session.json: selected target, Android API, session mode and privacy flags.
- events.jsonl: receiver and session lifecycle.
- ui-events.jsonl: structure-only UI and notification metadata.
- network-events.jsonl: allowlisted packet-header metadata only.
- device-capability-report.json: minimal Android access/capability report.
- PRIVACY-AND-REVIEW.txt: export privacy summary.

## Scope boundary

This is an observation instrument for a future state-transition classifier. It does not yet classify behaviour, learn semantic business events, or execute reusable automations. It never taps, swipes, types, replays network requests or controls the target app.

## Build

From the repository root:

    chmod +x android/gradlew
    android/gradlew -p research/app-observer-capture/android --no-daemon testDebugUnitTest assembleDebug

CI debug APK is a test artifact, not a stable update channel; CI debug signing keys can differ between runs.
