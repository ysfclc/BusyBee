# App Observer — Android Capture

App Observer is a generic, read-only Android behaviour recorder. It is not limited to a predefined provider list. The user selects one launchable app and manually starts/stops each observation session.

## Current evidence sources

- Full-display screenshots requested through the Android Accessibility screenshot API (Android 11/API 30+), rate-limited to roughly 1.2 seconds after meaningful UI events.
- Accessibility hierarchy and structural event metadata for the selected app.
- User-authored screenshot-region meaning rules.
- Notification envelope metadata by default; notification content only when a user rule explicitly selects notification_text.
- Optional PCAPdroid packet-header metadata only. No raw PCAP, raw addresses, TLS decryption or application payloads.

Android may block screenshots of secure or protected windows. An empty or unavailable Accessibility hierarchy does not imply the screenshot is blank.

## Meaning rules

Open the latest screenshot and choose DEFINE MEANING FROM THIS SCREENSHOT. Draw a region and assign any label, for example chat, notification bar, offer card, amount, map or a custom category.

Choose one evidence source:
- Visual screenshot only: retain the region and meaning label; do not read text.
- Read UI text inside this region: read Accessibility node text only for nodes whose centre falls within the region; password nodes are always excluded.
- Link selected app notification content: read notification title/body text only for the selected app when notification access is enabled.

Rules may target the selected app or Android system surfaces (for example status bar/notification shade). System-surface rules capture screenshots when Android system/updated-system packages produce relevant events. They do not collect those packages' Accessibility trees or text. Notification text remains sourced from the selected app's notification listener.

Rules are stored locally outside individual sessions and are included in exported evidence ZIPs. A user can import a prior evidence ZIP to restore earlier session records/screenshots and merge meaning rules.

## Permissions

1. Enable App Observer's read-only Accessibility service. Screenshot capture is part of this service configuration.
2. Optionally enable notification access. Notification content is still not read unless a matching meaning rule explicitly allows it.
3. Approve the manual session foreground notification on Android 13+.
4. Install PCAPdroid only when network metadata is needed; UI screenshots and structure capture do not depend on it.

## Evidence files

- session.json: session identity, target, privacy choices and capture capabilities.
- events.jsonl: session lifecycle, diagnostics and PCAPdroid state.
- ui-events.jsonl: structural UI events, fingerprints and screenshot references.
- screenshots/: JPEG screenshots and index.jsonl metadata.
- semantic-payloads.jsonl: only content explicitly authorised by a matching meaning rule.
- semantic-rules.json: exported user-authored rules.
- network-events.jsonl: allowlisted packet-header metadata when PCAPdroid is active.
- device-capability-report.json: Android/device capability snapshot.
- PRIVACY-AND-REVIEW.txt: export review requirements.

## Build and release signing

The release application ID is com.ysfclc.appobserver.capture. Future releases must use the same persistent release keystore and increment versionCode. Debug packages are separate and are not release updates. See docs/APP_OBSERVER_PERSISTENT_SIGNING.md.
