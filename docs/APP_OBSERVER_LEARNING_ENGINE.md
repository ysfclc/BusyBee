# App Observer — Behaviour Learning and Privacy Baseline

**Status:** Approved product and engineering direction. A generic structure-only recorder is being implemented in the research branch. Pattern classification and automation execution are future work.

## 1. Product objective

App Observer is a general-purpose Android application-understanding research instrument. It is not limited to Uber, Bolt, navigation or any fixed provider category.

Its job is to observe one user-selected app during an explicit session and produce evidence that can later be classified into reusable concepts:

- Screens and screen states.
- UI structure, control roles and hierarchy.
- User-visible interaction events, without reading content entered or displayed.
- State transitions and timing.
- Notification envelope changes.
- Network activity patterns using minimised packet-header metadata.
- Relationships between an interaction, resulting UI transition and nearby network activity.

The intended downstream outcome is an application behaviour model: what states exist, what actions appear available, which transitions occur, and what evidence indicates that a transition completed.

## 2. No provider allowlist

The app picker is populated dynamically from installed applications that expose a launcher activity to Android's package-visibility query. It excludes App Observer itself, but does not special-case Uber, Bolt, Waze or Google Maps.

The user selects one target package and manually starts one session. The AccessibilityService can receive system events broadly, but the recorder processes and stores events only when their package matches the selected target.

We deliberately do not request the restricted QUERY_ALL_PACKAGES permission. Therefore hidden, disabled, non-launchable and some system components will not appear. This is a package-discovery boundary, not an app-family restriction. A user can refresh the picker after installing an app.

## 3. Evidence model

### UI structure evidence

Persist only structural fields:

- Package identifier and event type.
- Wall-clock timestamp, elapsed-realtime timestamp and Android event uptime.
- Window ID and window class.
- Node class and resource ID.
- Parent index, depth, child index and child count.
- Bounds and boolean state: clickable, scrollable, editable, password, enabled, selected and checked.
- For interaction events, the source node's structural signature when Android exposes it.

Do not read or store AccessibilityNodeInfo text, content descriptions, hint text, editable contents, typed values or clipboard state. Do not collect screenshots or screen recordings. Password and editable controls are represented by structural flags only.

### Notification evidence

Record only package, post time, category, ongoing/group flags, action count and whether a content intent exists. Never read notification titles, bodies, expanded text, reply text or action labels.

### Network evidence

PCAPdroid's TCP exporter is an optional source. The packet stream is handled locally. Packet bytes are parsed in memory to derive a narrow allowlist:

- Packet timestamp.
- Target app identifier.
- IP version and transport protocol.
- Source and destination ports.
- TCP flags where available.
- Frame and transport payload byte counts.

Do not retain source/destination IP addresses, domain names, DNS values, URLs, TLS metadata, headers, cookies, tokens, application payloads or raw PCAP files. Disable PCAPdroid full-payload capture and TLS decryption. Packet bytes are discarded after header metadata is derived. This data describes timing and shape, not application request/response meaning.

The allowlist is intentional: packet sizes and timing may help align an interaction with a network burst, but this is not proof of a business operation and does not reveal encrypted content.

## 4. Correlation and later classification

Evidence records carry both wall-clock and monotonic timing where available. This permits a later classifier to correlate:

1. A user interaction event and its source node signature.
2. The pre-action UI structure.
3. Subsequent UI tree changes.
4. Notification metadata from the selected app.
5. Packet-header bursts from the selected app.

The next model should express a candidate behaviour as a state-transition record:

- State before: structural screen fingerprint and relevant control signatures.
- Interaction: event type and structural source-node fingerprint.
- State after: resulting screen fingerprint.
- Network evidence: time-windowed protocol/port/size sequence, if available.
- Outcome evidence: explicit UI or notification evidence.
- Repeatability: number of matching controlled traces.
- Confidence: evidence-backed classification, not an unsupported probability.

A single trace is a candidate, not a reusable skill. Reusable patterns should be promoted only after repeat runs and negative/failure cases demonstrate stable preconditions and postconditions.

## 5. Reusable automation boundary

The observation engine is read-only. It does not tap, swipe, type, invoke app controls or replay requests.

A future automation layer may turn verified behaviour into reusable skills with:

- Preconditions and target identification.
- Supported action method.
- Expected postconditions.
- Independent verification.
- Safe retry and timeout rules.
- Capability and permission declaration.
- Human-confirmation requirements for consequential actions.
- Unsupported and degraded result states.

Keep generic skills separate from app profiles. For example, a generic open-item-details skill can be reused across shopping, travel, mail and driver apps when each app exposes enough accessible structure. The app profile explains how that app expresses the item and detail state. The observer must not assume that the same action is available in every app.

No automation executor is included in this observer build. Android platform policy and distribution constraints must be evaluated separately before any general-purpose Accessibility-based action engine is designed or shipped.

## 6. Privacy contract

- Manual session start and stop; one selected app at a time.
- Local private storage only; no background upload or telemetry.
- No visible or typed text, notification content, screenshots, clipboard, files, credentials or account details.
- No raw network payloads, raw IP addresses, domains, headers, tokens or raw packet files.
- No collection of other apps' events during a selected-app session.
- Export is user-initiated; JSONL evidence is sanitised again at export to strip sensitive fields from legacy sessions.
- The export includes a privacy notice and capability report.
- Do not treat automatic redaction as sufficient: the design avoids collecting content instead.

## 7. Acceptance gates

### Recorder gate

- App picker discovers ordinary launchable apps without a hardcoded provider list.
- Only the selected app's UI and notification evidence is written.
- UI records preserve structural hierarchy and interaction source signatures without text.
- Network records contain only allowlisted metadata, with no address or payload fields.
- No network.pcap is created or included in exports.
- Legacy evidence is sanitised before export.
- Session evidence stays local until explicit export.

### Learning gate

Using controlled sessions for a chosen app:
- Classify screen states using structure fingerprints.
- Match interaction source nodes to pre/post state changes.
- Correlate network metadata without treating correlation as causation.
- Repeat the same workflow and measure stable versus variable structure.
- Produce candidate state-transition patterns with trace references and explicit missing evidence.

### Automation gate

Only after learning gate passes:
- Build a reusable skill schema.
- Test preconditions, action dispatch, postconditions and independent verification.
- Measure false-positive and false-success behaviour.
- Add human approval for high-impact actions.
- Assess Android policy and supported distribution route.

## 8. Current implementation status

Implemented in the App Observer capture project:
- Dynamic launchable-app picker rather than a four-provider hardcoded catalogue.
- Selected-package filtering in the Accessibility and notification listeners.
- Structure-only accessibility snapshots with hierarchy indexes and interaction source-node evidence.
- Notification envelope metadata without titles or bodies.
- PCAPdroid TCP stream inspection with only allowlisted header metadata persisted.
- No raw packet/PCAP file persistence; no full-payload or TLS-decryption request.
- Private local session storage and export-time sanitisation.

Not yet implemented or device-verified:
- Automated state fingerprint clustering and behaviour classification.
- Semantic correlation engine across UI/network evidence.
- Reusable automation skills or action executor.
- Physical-device acceptance across a representative set of apps.
- Per-app coverage report and false-positive evaluation.

This status must be preserved: building the recorder does not mean app understanding or automation has been completed.
