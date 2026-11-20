# App Observer — Android Capture

App Observer is a generic Android application-understanding instrument, not a provider-specific recorder. The user selects one launchable app and manually starts/stops each observation session. During a session, on-device models identify candidate screen concepts and learn recurring states from combined visual, Accessibility, interaction, notification and network evidence.

## Current evidence sources

- Full-display screenshots requested through the Android Accessibility screenshot API (Android 11/API 30+), rate-limited to roughly 1.2 seconds after meaningful UI events, with one bounded retry when Android reports a short interval.
- Accessibility hierarchy and structural event metadata for the selected app.
- Bundled on-device OCR and image labelling, with raw OCR text discarded after inference.
- Machine-inferred screen concepts with confidence, evidence provenance and OCR-derived region bounds.
- Persistent per-app recurring-state clusters and interaction-linked transition counts.
- Optional corrections for built-in or custom concept names, training a per-app classifier from privacy-safe feature vectors.
- User-authored screenshot-region meaning rules as optional annotations/permissions, not prerequisites for classification.
- Notification envelope metadata by default; notification content only when a user rule explicitly selects notification_text.
- Optional PCAPdroid packet-header metadata only. No raw PCAP, raw addresses, TLS decryption or application payloads.

Android may block screenshots of secure or protected windows. An empty or unavailable Accessibility hierarchy does not imply the screenshot is blank. OCR and image labelling run locally. Editable/password node bounds are excluded from OCR when exposed; raw OCR strings are not written to evidence. Inferred concepts, confidence, provenance, safe feature vectors and selected amounts may be retained locally.

## Meaning rules

The machine identifies screen concepts automatically. DEFINE MEANING FROM THIS SCREENSHOT remains an optional way to define a region or explicitly authorise extra Accessibility/notification text for that region. Use CORRECT / TEACH THE LOCAL MODEL to train from an incorrect whole-screen classification.

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
- screen-understanding.jsonl: machine concepts, confidence, sources, OCR regions, selected entities, fused evidence and learned state/transition references (no raw OCR text).
- learning-model.json: persistent per-app screen clusters, transitions and reviewed classifier training counts/features.
- device-capability-report.json: Android/device capability and inference diagnostics.
- PRIVACY-AND-REVIEW.txt: export review requirements.

## Build and release signing

The release application ID is com.ysfclc.appobserver.capture. Future releases must use the same persistent release keystore and increment versionCode. Debug packages are separate and are not release updates. See docs/APP_OBSERVER_PERSISTENT_SIGNING.md.
