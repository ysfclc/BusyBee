# App Observer — Visual Ground Truth and Meaning Rules

## Product principle

A screenshot is the visual ground truth for a captured moment, but the user does not have to explain it. The machine analyses the image with on-device OCR and image labelling, then fuses the result with Accessibility structure, interactions, notifications and nearby network timing to identify candidate concepts and screen states.

User-authored regions are optional feedback and explicit permission controls for additional Accessibility/notification text. They are not the primary classification mechanism. Machine hypotheses retain confidence and source provenance so an OCR clue, an Accessibility match, a notification signal and a network correlation remain distinguishable.

## Screenshot capture

- A manually started session for one selected app captures the full visible display after relevant Accessibility events (window changes, clicks, scrolls, selections and text-change notifications).
- Capture is rate-limited to approximately one screenshot request per 1.2 seconds, with one bounded retry when Android explicitly reports that the request interval was too short.
- Screenshots are requested through Android's AccessibilityService screenshot API on Android 11/API 30 and later.
- Screenshot bytes are stored as JPEG files in the selected session's private app storage. Each screenshot is linked to its event by screenshot ID, timestamp, target/system-surface scope, image dimensions and event type.
- A screenshot can contain sensitive information that is visibly rendered. Screenshots remain local and are included in an export only when the user explicitly exports the evidence bundle.
- Android can refuse or redact screenshots for secure windows, protected content, unsupported display surfaces or platform restrictions. A missing screenshot is an explicit capture failure, not proof that a screen was blank.
- The screenshot API and Accessibility hierarchy are separate sources. A screenshot can be available even when a window does not expose useful Accessibility nodes.

## On-device multimodal understanding

Every stored screenshot is analysed locally by bundled ML Kit OCR and image labelling. Text lines overlapping Accessibility nodes marked editable or password are excluded. OCR text exists only in memory for the inference pass; arbitrary recognised strings are discarded and not stored.

The inference engine combines:

- OCR phrase/layout cues and currency patterns.
- Whole-screen image labels.
- Accessibility classes, resource IDs, control density and scroll/edit structure.
- The interaction event and source-node evidence nearest to the screenshot.
- Notification categories/counts and notification text only where an explicit rule has already authorised it.
- Packet count, byte volume, protocol and port patterns from the nearby eight-second observation window.

The result is a set of candidate concepts (for example chat, price/fare, map, route summary, service offer, trip status, confirmation, search, list/feed, form, authentication, payment or profile) with confidence, evidence-source names and OCR-derived region bounds. Selected currency amounts are retained only when the price/fare hypothesis is sufficiently strong. Raw OCR lines are not persisted.

A persistent local state learner clusters recurring screens using privacy-safe feature vectors, counts repeated observations and records transitions associated with the preceding interaction. A correction workflow can train a per-app classifier from these sanitised features. Network timing is corroborating evidence, not proof that a remote operation succeeded.

## Optional meaning rules and correction feedback

The machine identifies candidate meanings automatically. The user can still open a screenshot, draw a rectangle and add a region-specific permission/annotation when a result needs refinement. This is optional. A separate correction action can train the classifier for the target app from the current screen feature vector. The rule editor asks for:

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
