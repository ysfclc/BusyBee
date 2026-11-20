# App Observer — Behaviour Learning and Privacy Baseline

**Status:** Approved product and engineering direction. Generic privacy-first recorder, on-device structural evidence viewer, exact structural fingerprints and App Observer self-test target are implemented and CI-verified (2026-11-20). Physical-device acceptance, semantic pattern classification and automation execution remain future work.

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

The app picker is populated dynamically from installed applications that expose a launcher activity to Android's package-visibility query. It does not special-case Uber, Bolt, Waze or Google Maps. App Observer is appended as an explicit self-test target so the recorder can observe its own UI.

The user selects one target package (or the explicit App Observer self-test target) and manually starts one session. The AccessibilityService can receive system events broadly, but the recorder processes and stores events only when their package matches the selected target.

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
- A versioned SHA-256 structural fingerprint over hierarchy indexes, node classes, resource IDs and boolean control states. The fingerprint excludes text, descriptions, editable values and pixel bounds; it groups structurally equivalent trees but does not assert semantic screen identity.

Screenshots are now the visual ground truth. During a manually started session, the AccessibilityService requests a full-display screenshot after meaningful UI events (window changes, clicks, scrolls, selections and text-change notifications), rate-limited to about one request per 1.2 seconds. Screenshots are saved locally as JPEGs and linked to their UI event by screenshot ID, timestamp, event type and dimensions. Android may block secure/protected windows or other capture surfaces; capture failures are recorded.

By default, AccessibilityNodeInfo text, content descriptions, hints, editable contents and typed values are not read. The user can draw a region on a captured screenshot and create a meaning rule. Each rule supplies a user-defined category, scope and explicit payload source: visual_only, accessibility_text or notification_text. Only a matching region rule permits the corresponding content read. Password nodes are always excluded. User-defined rules persist locally across sessions and are included in evidence export/import. See [Visual Ground Truth and Meaning Rules](APP_OBSERVER_VISUAL_MEANING.md).

### Notification evidence

Notification envelope metadata remains the default. Notification title/body content is read only when a user-authored rule explicitly selects notification_text for the selected app or the Android system-surface scope. Payload records carry the rule ID, category, source, timestamp and screenshot reference when available. Notification action labels and reply input are not read.

### Network evidence

PCAPdroid's TCP exporter is an optional source. The packet stream is handled locally. Packet bytes are parsed in memory to derive a narrow allowlist:

- Packet timestamp.
- Target app identifier.
- IP version and transport protocol.
- Source and destination ports.
- TCP flags where available.
- Frame and transport payload byte counts.

Do not retain source/destination IP addresses, domain names, DNS values, URLs, TLS metadata, headers, cookies, tokens, application payloads or raw PCAP files. Disable PCAPdroid full-payload capture and TLS decryption. Only a bounded header prefix is read into memory. The rest of each packet is drained through a small wiped buffer, and the prefix is wiped after parsing. Packet content is not retained. This data describes timing and shape, not application request/response meaning.

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

- Manual session start and stop; one selected app at a time, with an explicit separate rule scope for Android system surfaces.
- Local private storage only; no background upload or telemetry.
- Full-display screenshots are saved during an explicitly started session. A screenshot can contain sensitive visible content; review it before sharing.
- Accessibility text and notification content remain off by default. A user-drawn meaning rule must explicitly authorise a content source for a region/category before its payload is read.
- Password nodes are never read. Typed values outside explicitly authorised regions are not read. The observer never reads clipboard contents.
- No raw network payloads, raw IP addresses, domains, headers, tokens or raw packet files.
- No third-party app screens are captured when only the selected-app scope is active. System-surface screenshot capture is available only after a system-surface rule exists and is limited to Android system/updated-system package events; system UI node text is not scraped.
- Export is user-initiated. The evidence bundle contains screenshots, rule-authorised semantic payloads and the rule definitions alongside the UI/network records.
- Import of a prior evidence ZIP preserves its session records/screenshots and merges valid meaning rules. The old app can remain installed during migration.


## 7. Acceptance gates

### Recorder gate

- App picker discovers ordinary launchable apps without a hardcoded provider list.
- Only the selected app's UI and notification evidence is written.
- UI records preserve structural hierarchy and interaction source signatures without text.
- The on-device live viewer renders recent UI events, the latest visible control tree, resource IDs, bounds, boolean control states, source-node metadata and repeated structural fingerprints.
- The app picker includes an explicit App Observer self-test target; target filtering is verified by unit tests.
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
- Dynamic launchable-app picker rather than a provider-specific catalogue, plus explicit App Observer self-test target.
- Selected-package filtering and the generic UI diagnostic pipeline, including root-window, node-access, persistence and screenshot counters.
- Structure-only accessibility snapshots with hierarchy indexes, interaction source-node evidence and versioned SHA-256 structural fingerprints.
- Full-display screenshots through Android's Accessibility screenshot API (API 30+), stored locally and linked to UI events.
- Screenshot viewer and region annotation activity; arbitrary user-defined meaning categories and normalised region selectors.
- Persistent local semantic rules with visual-only, explicit Accessibility text and selected-app notification-content source modes.
- Separate semantic-payload stream linked by rule ID and screenshot ID, plus explicit system-surface screenshot scope.
- Evidence export/import for screenshots, semantic payloads, rules and earlier session JSONL.
- Notification envelope metadata by default; content only when an explicit notification_text rule exists.
- PCAPdroid TCP stream inspection with only allowlisted packet-header metadata persisted; no raw PCAP or TLS-decrypted traffic.
- Stable release application ID and external persistent signing configuration; CI intentionally withholds installable APK artifacts until release-signing secrets are configured.

Still not yet implemented or device-verified:
- Physical Redmi acceptance for screenshots, region matching, system-surface screenshot capture and notification payload linkage.
- Visual semantic recognition and automatic meaning suggestions; the current categories are authored by the user, not inferred.
- State/behaviour learning and correlation across screenshot, UI, notification and network evidence.
- Reusable automation skills or action execution.
- Persistent signing secret installation in GitHub repository settings and the first stable release artifact; the private keystore was generated and the workflow is configured, but the secrets must be installed before the next release can be built and verified.
- Per-app coverage report and false-positive evaluation.

Preserve this boundary: a screenshot is visual evidence, a semantic rule is user-authored meaning, and a model inference is a separate future classification. Building the recorder does not mean app understanding or automation has been completed.
