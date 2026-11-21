# App Observer — Behaviour Learning and Privacy Baseline

**Status:** The first on-device multimodal learning layer is implemented and CI-verified (2026-11-20), including bundled OCR/image labelling, multi-source evidence fusion, machine-localised regions, recurring-state clustering, interaction-linked transitions, custom concept feedback and persistent model import/export. Physical Redmi acceptance, per-app accuracy benchmarks, verified business outcomes and automation execution remain future work.

## 1. Product objective

App Observer is a general-purpose Android application-understanding research instrument. It is not limited to Uber, Bolt, navigation or any fixed provider category.

Its job is to observe one user-selected app during an explicit session and produce evidence that can later be classified into reusable concepts:

- Screens and screen states.
- UI structure, control roles and hierarchy.
- User-visible interaction events; typed fields are excluded from OCR and raw recognised screen text is not persisted.
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

Screenshots are the visual ground truth. During a manually started session, the AccessibilityService requests a full-display screenshot after meaningful UI events (window changes, clicks, scrolls, selections and text-change notifications), rate-limited to about one request per 1.2 seconds. Screenshots are saved locally as JPEGs and linked to their UI event by screenshot ID, timestamp, event type and dimensions. Android may block secure/protected windows or other capture surfaces; capture failures are recorded.

### Machine understanding and evidence fusion

Every stored screenshot is passed to bundled, on-device ML Kit text recognition and image labelling. OCR lines overlapping Accessibility nodes marked editable or password are excluded. OCR runs in memory; arbitrary recognised strings are discarded rather than written to JSONL. The classifier emits concept hypotheses with provisional evidence scores, source provenance and machine-localised regions from OCR bounds or Accessibility element geometry. Initial concept families include chat/conversation, price/fare, map, route summary, service offer, trip state, confirmation result, search, list/feed, form, authentication, payment, notification, profile/account, dashboards, orders, bookings, navigation, media, documents and common navigation/input/action controls. Corrections can introduce custom per-app concepts beyond this starter vocabulary. The current lexicon supports common English, Polish and Turkish terms.

The visual/OCR result is fused with the nearest captured Accessibility hierarchy and resource IDs, the interaction trigger and source node, notification category/count metadata, any notification text already authorised by an explicit rule, and packet-header volume/protocol/port observations in an eight-second correlation window. Network metadata is corroborating timing/shape evidence only; it is not treated as proof of a server-side operation. The resulting `screen-understanding.jsonl` records hypotheses, explicitly uncalibrated evidence scores, evidence sources, machine-localised candidate regions, selected extracted currency amounts, source availability/missing-source flags, correlated-source counts and a privacy-safe feature vector. The screenshot viewer overlays machine regions in blue and optional user-authored regions in green. Raw OCR lines and arbitrary user text are not retained by this stream.

A persistent local learner clusters recurring screens by Jaccard similarity over the privacy-safe multimodal feature vector. It tracks per-app state IDs, occurrence counts, dominant concept votes and transitions linked to the most recent interaction trigger. An optional correction screen accepts a category correction and trains a per-app multinomial Naive Bayes model from the same sanitised feature vector. It supports built-in concepts and new user-named concepts, so the model vocabulary can expand beyond the starter taxonomy. Two reviewed examples per category across at least two categories are required before the trained classifier influences future predictions. User correction is a feedback channel, not a prerequisite for each identification. Learning memory lives outside individual sessions and is included in evidence export/import.

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

The current learner emits a state-transition observation with:

- Previous and resulting state IDs.
- The interaction event that preceded the new screenshot.
- Repeated-state similarity and occurrence count.
- Concept hypotheses with confidence and explicit source provenance.
- Notification and packet-header evidence counts in the temporal window.
- A persistent transition count for repeated from/to/trigger combinations.

This is an initial learning layer, not yet a validated behaviour model. A temporal correlation does not establish causation, and a repeated visual state does not prove that a business operation succeeded. Production-grade behaviour learning still needs controlled repeat traces, negative/failure examples, postcondition verification, false-positive measurement and per-app acceptance reports. A single observed transition must never be promoted to an automation skill.

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
- Raw OCR text is processed ephemerally on-device for screen understanding and discarded. Inferred concepts, confidence, source provenance, safe feature vectors and selected extracted entities (currently currency amounts when the price/fare hypothesis is sufficiently strong) are retained locally.
- Accessibility text and notification title/body payloads remain off by default. A user-drawn meaning rule must explicitly authorise those additional content sources. The learner may consume already-authorised notification payloads in memory, but does not copy their raw contents into the machine-understanding stream.
- Password nodes are never read. Typed values outside explicitly authorised regions are not read. The observer never reads clipboard contents.
- No raw network payloads, raw IP addresses, domains, headers, tokens or raw packet files.
- No third-party app screens are captured when only the selected-app scope is active. System-surface screenshot capture is available only after a system-surface rule exists and is limited to Android system/updated-system package events; system UI node text is not scraped.
- Export is user-initiated. The evidence bundle contains screenshots, rule-authorised semantic payloads, screen-understanding records, privacy-safe feature vectors and the persistent learning model alongside the UI/network records. Extracted amounts and explicitly authorised payloads may be sensitive; review before sharing.
- Import of a prior evidence ZIP preserves its session records/screenshots, merges valid meaning rules and imports/merges local learning memory. The old app can remain installed during migration.


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
- Structure-only Accessibility snapshots with hierarchy indexes, interaction source-node evidence and versioned SHA-256 structural fingerprints.
- Bundled on-device OCR and image labelling for every stored screenshot, with editable/password-region masking and ephemeral OCR text handling.
- Multi-source concept inference correlating OCR, visual labels, Accessibility structure/resource IDs, interactions, notification metadata/authorised payloads and nearby packet-header timing.
- Confidence-scored concept hypotheses with source provenance, OCR-derived region bounds and selected currency entity extraction.
- Persistent per-app recurring-screen clusters, occurrence counts and interaction-linked transition counts.
- Optional correction workflow and per-app multinomial Naive Bayes training from privacy-safe feature vectors.
- Machine-understanding JSONL and persistent learning-model export/import, with OCR raw text excluded from the model and inference evidence.
- Full-display screenshots through Android's Accessibility screenshot API (API 30+), stored locally and linked to UI events.
- Screenshot viewer and region annotation activity; arbitrary user-defined meaning categories and normalised region selectors.
- Persistent local semantic rules with visual-only, explicit Accessibility text and selected-app notification-content source modes.
- Separate semantic-payload stream linked by rule ID and screenshot ID, plus explicit system-surface screenshot scope.
- Evidence export/import for screenshots, semantic payloads, rules and earlier session JSONL.
- Notification envelope metadata by default; content only when an explicit notification_text rule exists.
- PCAPdroid TCP stream inspection with only allowlisted packet-header metadata persisted; no raw PCAP or TLS-decrypted traffic.
- Stable release application ID and external persistent signing configuration; CI intentionally withholds installable APK artifacts until release-signing secrets are configured.

Still not yet implemented or device-verified:
- Physical Redmi acceptance for screenshot OCR/image labelling, multi-source evidence alignment, notification linkage and background analysis performance.
- Per-app accuracy benchmarks, labelled evaluation sets, confusion matrices and false-positive/false-negative thresholds.
- Better visual localisation for controls/regions beyond OCR text boxes and Accessibility bounds; current image labelling is whole-image classification.
- Robust transition/outcome learning with controlled repeats, negative examples and independently verified postconditions.
- Reusable automation skills or action execution.
- Automatic CI signing remains dependent on configuring the four repository secrets; the first stable APK was signed locally with the persistent key, but Actions must use the same key for future release updates.
- Physical-device memory, latency, battery, storage-retention and long-session stress testing.

Preserve this boundary: a screenshot is visual evidence, a semantic rule is optional user-authored feedback/permission, and a model inference is a separate evidence-backed hypothesis. The current engine identifies candidate screen concepts and recurring states; it does not yet prove business outcomes or perform automation.


## 9. Product-goal audit and required correction — 2026-11-21

The implementation above is a useful capture and first-pass perception foundation. It must not be described as the completed learning engine or as general app understanding.

### The actual product goal

Given any user-selected launchable Android app, observe it read-only and progressively construct a reusable, evidence-backed model of how that app is structured and behaves. The machine—not the user—must discover meaningful screens, regions, elements, relationships, states and transitions. User corrections are a feedback channel, not a requirement to draw or name every object. The model must be able to say that it does not know, preserve competing hypotheses and improve from repeated observations and verified outcomes.

The intended output is not merely a label such as "map" or "payment". It is a model containing screen/state instances; visual regions and UI elements; containment, grouping, alignment, adjacency and label/control relationships; entities and attributes that persist or change; interaction-linked transitions including failures and ambiguous outcomes; hypotheses with supporting and contradicting evidence; and reusable app-independent patterns separated from app-specific mappings.

### What the current code actually does

1. MultimodalInference.classify is primarily a hand-authored phrase lexicon, currency regex, a small set of image-label mappings and hard-coded Accessibility class/resource-ID rules. Its output vocabulary is fixed in code except for labels introduced through explicit correction.
2. Whole-screen ML Kit image labels do not localise objects or explain relations between them. Most machine regions come from OCR line boxes or Accessibility bounds, not learned visual object/element detection.
3. featureVector converts the same starter lexicon and selected structural cues into categorical flags. It does not learn an open-ended representation of an app's elements or relationships.
4. LocalLearningMemory.observe clusters these flags with Jaccard similarity and counts transitions. It is recurring-feature clustering, not a learned state model. Merging feature sets into a cluster can blur distinct states.
5. The optional per-app Naive Bayes classifier learns co-occurrence between fixed features and labels from user corrections. It does not discover new concepts or relationships independently, and the current heuristic prediction remains dominant in the blend.
6. ScreenUnderstandingService joins nearby records using timestamp windows. This is useful correlation, but it is not yet a shared event/scene graph with explicit identity, causal uncertainty, relation links and replayable evidence.
7. Confidence values are heuristic evidence scores and are uncalibrated. They must not be presented as probabilities or as proof that a control or business operation was understood.

Therefore the present build is capture + heuristic multimodal classification + simple recurring-feature memory + optional supervised correction. It is not yet the open-ended app-learning system described by the product objective.

### Correct engineering direction

Do not keep adding concepts to the hard-coded vocabulary as the primary way to improve the product. The next engine milestone must establish a generic, replayable screen model:

1. Synchronized observation record: one timestamped observation links screenshot, UI hierarchy, source interaction, notification evidence and network metadata, each with its own source identity, timing uncertainty and availability.
2. Generic scene graph: represent visual regions, OCR-derived text shapes, Accessibility elements and interaction targets as nodes. Add explicit hierarchy and geometric/semantic relations. Raw OCR text remains ephemeral unless separately authorised by a meaning rule.
3. Temporal identity and change: match elements and regions between observations; distinguish stable identity from changing content, movement, reordering, insertion and disappearance.
4. Relational memory: learn recurring element arrangements, containment/grouping, attributes and transitions from graph structure and repeated traces. Retain counterexamples and competing state hypotheses rather than merging everything into one centroid.
5. Hypothesis and verification loop: infer candidate roles/meanings from all evidence, record why each hypothesis exists, predict observable changes, then strengthen or weaken the hypothesis after the next observation. Correlation alone never proves causation or success.
6. Open-world learning: allow unknown elements and new concepts. Generalise reusable patterns across apps while retaining app-specific selectors/mappings. Corrections should update a versioned model and be evaluated against held-out traces.
7. Evaluation before claims: use labelled traces from multiple unrelated apps, repeated workflows, negative/failure cases and app-version changes. Report precision/recall, localisation quality, state/transition accuracy, calibration, missed evidence and resource cost.

### Required acceptance tests for the next milestone

- Discover a previously unseen screen/element without adding a new hard-coded phrase or provider rule.
- Identify generic controls and content regions when OCR is absent or misleading, using structure and visual evidence.
- Link a text region to its containing card/control and preserve the relationship in the exported model.
- Match the same element across screenshots despite text/value changes and small layout movement.
- Distinguish visually similar screens whose state differs, and avoid merging them solely because their feature sets overlap.
- Learn a repeated transition from several traces, retain a failed/cancelled counterexample and mark an ambiguous outcome as unknown.
- Show exactly which sources support and contradict a hypothesis; remove a source and verify graceful degradation.
- Demonstrate a reusable pattern in a second unrelated app without copying the first app's hard-coded labels.
- Measure false positives, false negatives, localisation errors, latency, memory, battery and storage on the Redmi Android 14 pilot device.

Until these tests pass, describe the engine as experimental and do not call it production-grade app understanding. The next implementation should focus on the generic scene graph and temporal/relational memory—not another round of screen-category heuristics.
