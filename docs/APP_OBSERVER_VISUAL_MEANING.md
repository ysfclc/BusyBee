# App Observer — Visual Ground Truth and Meaning Rules

## Product principle

A screenshot is the visual ground truth for a captured moment. Accessibility hierarchy, notification events and network timing are evidence linked to that image; none of those sources should silently decide what a region means.

The user names regions and chooses which additional evidence may be attached. A rule can be applied again in later sessions for the same app. Categories are user-defined and are not limited to a fixed list such as chat, notification bar, price, map, offer, button or status.

## Screenshot capture

- A manually started session for one selected app captures the full visible display after relevant Accessibility events (window changes, clicks, scrolls, selections and text-change notifications).
- Capture is rate-limited to approximately one screenshot request per 1.2 seconds, with one bounded retry when Android explicitly reports that the request interval was too short.
- Screenshots are requested through Android's AccessibilityService screenshot API on Android 11/API 30 and later.
- Screenshot bytes are stored as JPEG files in the selected session's private app storage. Each screenshot is linked to its event by screenshot ID, timestamp, target/system-surface scope, image dimensions and event type.
- A screenshot can contain sensitive information that is visibly rendered. Screenshots remain local and are included in an export only when the user explicitly exports the evidence bundle.
- Android can refuse or redact screenshots for secure windows, protected content, unsupported display surfaces or platform restrictions. A missing screenshot is an explicit capture failure, not proof that a screen was blank.
- The screenshot API and Accessibility hierarchy are separate sources. A screenshot can be available even when a window does not expose useful Accessibility nodes.

## Meaning-rule authoring

The user opens the latest screenshot, draws a rectangle over a region and defines:

1. **Meaning/category:** any label chosen by the user, for example chat messages, notification bar, offer card, price, route summary, map controls or a custom label.
2. **Scope:** the selected app or Android system surfaces such as the status bar/notification shade.
3. **Payload source:**
   - visual_only: retain the region in the screenshot; do not read text.
   - accessibility_text: read Accessibility node text only when the node's centre falls inside the user-selected region; password nodes and editable input fields are always excluded.
   - notification_text: link content from NotificationListenerService for notifications posted by the selected app. This requires the notification-listener permission and a rule explicitly selecting this source.

Rules use normalised screenshot coordinates, the source screenshot's aspect ratio, and a stable rule ID, package/scope, category, payload source, screenshot origin ID and creation time. A portrait rule is not applied to a landscape capture; older imported rules without aspect-ratio metadata remain compatible. They are stored separately from session evidence, so they are reusable across sessions and are included in export/import bundles.

System-surface rules do not enable broad scraping of other installed apps. They only permit screenshots triggered by Android system/updated-system package events; they do not store those packages' Accessibility trees or node text. Notification text associated with a system-surface rule still comes from the selected app's notification listener events.

## Linked semantic evidence

Rule-authorised content is written to semantic-payloads.jsonl, not merged into ordinary UI tree records. A payload record links:

- timestamp and selected app package,
- semantic rule ID and user-defined category,
- source method (accessibility_node_text or notification_listener),
- screenshot ID when available,
- structural node class/resource ID where applicable,
- content fields that the rule explicitly authorised.

Ordinary sessions do not read Accessibility text, notification titles, notification bodies or typed values. Content is not inferred from a network packet. Packet headers remain metadata-only.

Visual-only rules do not enable text extraction. User choices are recorded as local rule definitions and exported with the evidence so a later analysis can distinguish captured visual facts from user-authored interpretation.

## Persistence and migration

- Session screenshots and linked payloads live under the session store.
- Meaning rules live in app-private preferences, independent of a session.
- Evidence ZIP export includes session metadata, event streams, screenshots, semantic payloads, meaning rules and the capability report.
- The import action accepts a previous evidence ZIP and preserves its session records/screenshots while merging valid meaning rules.
- Normal app updates must keep the same release application ID and the same persistent signing key. Replacing a release signing key breaks Android's update relationship.
- The initial stable release uses com.ysfclc.appobserver.capture; existing debug viewer installations remain separate and can stay installed during the one-time migration.

## Current limits / next learning stage

Implemented in this workstream: event-triggered screenshots, screenshot preview, draw-to-define region rules, local rule persistence, explicit Accessibility text/notification payload selection, linked payload export, system-surface screenshot scope and prior evidence import.

Still separate work: visual semantic recognition, automatic rule suggestions, confidence scoring, repeated state clustering beyond exact structural fingerprints, and behaviour/state-transition learning. The observer must preserve a distinction between a screenshot, a user-defined meaning, and a machine-inferred interpretation. No app action execution is part of this capability.
