# BusyBee Activity & Network Runtime — Research Findings

**Status:** Research recorded; no implementation authorised by this document  
**Research date:** 2026-11-14  
**Product:** BusyBee Android Driver Command Centre  
**Scope:** Native Android activity tracking, app-scoped network observation, deeper request/response visibility, and authorised action coordination.

## 1. Executive conclusion

BusyBee can host a substantial activity and network-observation subsystem inside the existing Android application. A custom Android distribution or separate monitoring application is not a prerequisite.

The realistic architecture has multiple observation depths:

1. **Android activity metadata:** which selected app is foregrounded, when app/activity transitions occur, and how long the driver uses each app.
2. **Network-flow metadata:** per-app connections, destination addresses/ports, protocol, timing and byte counts using an app-scoped Android VPN.
3. **Decrypted application transactions:** HTTP requests/responses and structured payloads, when the target app and its transport stack permit TLS inspection or when an authorised, controlled runtime instrumentation environment can observe plaintext at the application boundary.
4. **Provider-authorised operations:** calls made by BusyBee through documented APIs and credentials granted to the driver/fleet.

These are distinct capabilities. Network visibility does not imply payload decryption; observing a private app request does not make that endpoint a supported or authorised integration; and observing a request does not grant permission to replay or modify it.

**Recommendation:** retain this work inside BusyBee as a native Activity & Network Runtime. First establish no-root app activity and network-flow observation. Investigate deeper inspection separately against the exact installed Uber/Bolt app versions and only in a controlled, owner-authorised test setup.

## 2. Current repository baseline

At the research checkpoint, the current PR #9 branch is `feat/map-first-route-dock-2026-11-13`, head `23111ee7c944b379fa8fa30ac83fab34ff21e43b`. PR #9 remains draft, open and unmerged.

Existing native capabilities:

- `DriverAppAccessibilityService`: explicitly enabled, package-scoped, read-only observation of visible non-editable/non-password UI text for the configured Uber Driver and Bolt Driver package IDs.
- `PlatformNotificationListener`: user-enabled notification observation, with exact package matching for the two configured driver apps.
- `DriverAppCapture` / `MediaProjection`: manually initiated screen capture with on-device OCR as a fallback.
- Existing local stores and parser/review flows retain observation provenance and user review state.

Not present in the inspected branch:

- `UsageStatsManager` activity-history integration.
- A BusyBee `VpnService` implementation.
- Per-app network flow attribution and persistence.
- TLS inspection or application-process instrumentation.
- A provider API connector with verified production credentials.
- An authorised provider action adapter for ride acceptance or other driver-side commands.

This is a source inspection snapshot, not a build or device-verification claim.

## 3. Native Android activity tracking

### UsageStatsManager / UsageEvents

Android's `UsageStatsManager.queryEvents(beginTime, endTime)` exposes system-recorded app usage events, including activity lifecycle transitions. It requires `PACKAGE_USAGE_STATS`, which the user grants through Settings. Event history is retained only for a limited period (documented as a few days); on Android R and later, queries can return null while the user is locked.

Use this as a history/query source, not as a guaranteed real-time callback or a record of every physical touch. Combine it with the existing AccessibilityService signals when building a current-app/presence estimate.

Official reference: https://developer.android.com/reference/android/app/usage/UsageStatsManager

### AppOpsManager

AppOps is useful for Android's own sensitive-operation accounting, but it is not a general third-party API-call recorder. Public observer scope and access controls do not provide an ordinary BusyBee app with a universal feed of other applications' internal operations. Do not design the runtime around AppOps as a global event bus.

### NetworkStatsManager

Do not assume `NetworkStatsManager.queryDetailsForUid` is an ordinary-app solution for reading Uber/Bolt byte counts. Android documents that reading other apps' usage statistics requires `PACKAGE_USAGE_STATS`, a system-level permission not granted to ordinary third-party apps (with special device/profile-owner cases). Its historical buckets are also too coarse for request-level tracing.

Official reference: https://developer.android.com/reference/android/app/usage/NetworkStatsManager

## 4. App-scoped VpnService — no-root network observation

Android's public `VpnService` API allows BusyBee to create a local TUN interface after explicit user approval. `VpnService.Builder.addAllowedApplication(packageName)` can scope the VPN to a selected set of installed packages. If at least one allowed package is added, other apps use their normal network path instead of the VPN.

For the known BusyBee package mappings, an opt-in profile could select:

- Uber Driver: `com.ubercab.driver`
- Bolt Driver: `ee.mtakso.driver`

The package IDs must still be verified against the actual installed target-device versions before activation. Android permits only one active VPN service per user/profile, so BusyBee must detect and explain conflicts with another VPN.

A production implementation must forward traffic correctly; simply reading packets from the TUN descriptor will break connectivity. It needs a robust userspace forwarding stack, IPv4/IPv6, TCP/UDP, DNS, socket protection to avoid routing its own upstream traffic back into the tunnel, lifecycle recovery and strict fail-open/fail-closed decisions. It must be tested on Wi-Fi/mobile transitions and OEM battery-management behaviour.

On API 29+, `ConnectivityManager.getConnectionOwnerUid(protocol, local, remote)` can return the UID owning a TCP/UDP connection associated with the calling VPN app's tunnel. This can support attribution; it is not a general global socket-inspection permission.

Official references:

- https://developer.android.com/reference/android/net/VpnService
- https://developer.android.com/reference/android/net/VpnService.Builder#addAllowedApplication(java.lang.String)
- https://developer.android.com/reference/android/net/ConnectivityManager#getConnectionOwnerUid(int,android.net.InetSocketAddress,android.net.InetSocketAddress)

### What flow metadata can be recorded

Where visible and correctly attributed:

- app/package identity and UID;
- transport protocol and local/remote address/port;
- DNS queries/responses and hostname hints where not hidden;
- TLS handshake metadata such as SNI where available;
- connection start/end, duration, direction and byte counts;
- failures, resets and network transitions.

Encrypted application payloads remain encrypted. TLS 1.3, encrypted DNS, QUIC, ECH and app-specific transport behaviour can reduce metadata visibility. QUIC is UDP-based and must not be silently ignored.

## 5. Decrypted HTTP/API visibility — available routes and limits

### Route A: local TLS proxy / trusted CA

A local proxy can decrypt HTTPS when the app trusts the proxy's CA and its protocol is supported. Android apps targeting modern Android versions commonly do not trust user-added CAs by default unless their network security configuration permits them. Certificate pinning can impose an additional trust check.

PCAPdroid is a concrete proof that Android's local VPN model can be combined with TLS inspection and HTTP request/response display. Its own documentation also warns that decryption of ordinary modern apps commonly fails, that root may be needed, and that QUIC decryption is not supported in its documented workflow.

References:

- https://github.com/emanuele-f/PCAPdroid
- https://emanuele-f.github.io/PCAPdroid/tls_decryption.html
- https://emanuele-f.github.io/PCAPdroid/faq.html
- https://developer.android.com/privacy-and-security/security-config

Use this route as a controlled diagnostic test, not as an assumption that Uber/Bolt traffic will decrypt. A system CA alone does not defeat certificate pinning.

### Route B: observe plaintext at the application boundary

Where an application has already decrypted a response or is about to encrypt a request, runtime instrumentation may be able to observe data at selected networking/library boundaries. Frida is an established dynamic instrumentation framework for inspecting and debugging app processes; OWASP's mobile testing guidance documents runtime instrumentation as a security-testing technique.

Potential targets depend on the actual APK and may include Java networking libraries, Cronet, native TLS libraries, gRPC/protobuf serializers or app-specific wrappers. No single hook is universal. Obfuscation, native code, multiple network stacks, app updates, integrity checks and anti-tampering can invalidate a technique.

This is a research avenue for a controlled, owner-authorised test device and exact app build. It is not a committed BusyBee feature and must not be treated as a supported integration contract.

References:

- https://frida.re/docs/
- https://mas.owasp.org/MASTG/
- https://mas.owasp.org/MASTG/techniques/android/MASTG-TECH-0012/

### Route C: privileged/root instrumentation

Root or a system-level test environment can provide additional visibility and allow controlled diagnostic tooling to operate with elevated privileges. It can also introduce device-integrity failures, weaken security, break banking/driver apps, complicate OTA updates and create maintenance burden. Root does not automatically decrypt TLS or defeat every app-level integrity check.

Do not require root for the normal BusyBee product. Treat this as an optional research environment only after a no-root baseline has been measured. Do not root the user's primary phone merely to test an unverified theory.

### Route D: static APK and protocol analysis

Inspect the exact APK version to inventory package identity, target SDK, network-security configuration, bundled networking libraries, native libraries, protocol definitions and visible endpoint references. Static analysis helps select the correct observation boundary but cannot prove live server behaviour or provide current session credentials.

Record APK version, source, acquisition date and SHA-256 for reproducibility. Do not modify/repackage production APKs as the normal integration approach: signature changes can break SSO, integrity checks and updates.

### Important distinction: decoded payload is not automatically JSON

Even if TLS is successfully observed, the application payload may be protobuf, compressed data, GraphQL, binary framing or another protocol. The observer must identify framing/content encoding and version its decoders. Unknown payloads remain opaque evidence; do not fabricate parsed fields.

## 6. PCAPdroid as a technical reference, not a product dependency

PCAPdroid demonstrates:

- no-root per-app traffic capture using Android `VpnService`;
- local processing without a remote VPN server;
- app-attributed connection lists and packet capture;
- optional TLS decryption using a local proxy/certificate when compatible;
- an app API for forwarding capture information to another app.

Its public repository documents a stack including `zdtun`, `nDPI` and `mitmproxy`. This is strong evidence that the no-root flow-observation portion is practical.

BusyBee should study the architecture and evaluate reusable components only after a licence and dependency audit. Do not embed, fork or copy GPL-licensed code without an explicit compatibility decision. Do not make a second monitoring app a required runtime dependency.

References:

- https://github.com/emanuele-f/PCAPdroid
- https://github.com/emanuele-f/PCAPdroid/blob/master/docs/app_api.md

## 7. Provider APIs and authorised actions

### Uber Driver API

Uber documents limited-access Driver API scopes for accounts, payments and trip history. Access requires application/approval; the documentation does not establish a general-purpose driver-side command API for accepting arbitrary offers.

References:

- https://developer.uber.com/docs/drivers/introduction
- https://developer.uber.com/docs/drivers/guides/scopes
- https://developer.uber.com/docs/drivers/references/api/v1/partners-trips-get
- https://developer.uber.com/docs/drivers/references/api/v1/partners-payments-get

### Bolt Fleet Integration API

Bolt's Fleet API is for authorised fleet partners. It uses OAuth client credentials and a `company_id` associated with the API key; documented capabilities include fleet drivers/vehicles, online status/current order and ride history. This is not a universal personal API for every individual driver.

Reference: https://developer.bolt.eu/fleet-integration/main/

### Action boundary

Classify operations explicitly:

1. **BusyBee-owned actions:** can be automated within BusyBee (local records, reconciliation proposals, reminders, analytics).
2. **Documented provider API actions:** only implement where the provider documents the operation and grants the necessary credentials/scopes/contractual permission.
3. **Source-app-only actions:** provide a user-confirmed handoff to the original app unless a supported interface is established.

An observed private endpoint is not permission to call it. Do not replay, forge, mutate or automate private provider requests; do not capture/store bearer tokens, cookies, refresh tokens, passenger data or other secrets. Do not implement ride acceptance/decline or other high-impact provider actions through reverse-engineered endpoints. A successful HTTP response is not proof that a trip or payment state has been authoritatively committed; reconcile against provider-confirmed state.

## 8. Proposed in-app subsystem

Keep this inside BusyBee's existing Kotlin + Rust architecture:

- **Activity Observer (Kotlin):** UsageStatsManager query/polling, lifecycle signals, exact package allowlist and permission status.
- **Network Observer (Kotlin + native networking component as needed):** opt-in app-scoped VpnService, flow attribution, DNS/TLS metadata and connection health.
- **Existing UI Observer:** AccessibilityService remains read-only and package-scoped.
- **Existing Notification Observer:** notifications remain a separate, partial signal.
- **Evidence/Event Recorder:** immutable source observations with source, package/UID, timestamp, app version, observation depth, raw-evidence policy, parser version and confidence.
- **Correlation Engine (Rust):** joins independent observations into candidate app states, offers, trips and earnings events without treating a network connection as a business event.
- **Action Coordinator:** separates local BusyBee actions, provider-authorised API operations and user-confirmed source-app handoffs.

Suggested event source types:

- `ANDROID_USAGE_EVENT`
- `ANDROID_ACCESSIBILITY_UI`
- `ANDROID_NOTIFICATION`
- `ANDROID_VPN_FLOW`
- `ANDROID_DNS_OBSERVATION`
- `ANDROID_TLS_METADATA`
- `ANDROID_TLS_DECRYPTED_DIAGNOSTIC` (only where legally and technically permitted)
- `PROVIDER_OFFICIAL_API`

Every record must distinguish observed fact from inference. A connection to a server is not an offer, a POST is not an accepted ride, and a response is not a confirmed financial record without semantic validation and authoritative evidence.

## 9. Privacy, security and distribution requirements

- Explicit opt-in for Usage Access, VPN capture and any deeper diagnostic mode.
- Explain exactly which apps are selected, what metadata is collected and whether payload inspection is enabled.
- Default to metadata-only capture; no packet bodies or secrets in normal operation.
- If controlled payload diagnostics are ever enabled, use short sessions, local-only storage, strict app allowlists, explicit user initiation, automatic expiry and redaction. Never capture credentials or passenger personal data.
- Provide a visible running indicator, stop control, retention controls and deletion/export controls.
- VPN traffic must remain local unless a separate, explicit destination and purpose are disclosed.
- Handle the single-active-VPN constraint, VPN revocation, process death, network changes and Xiaomi battery management.
- Assess Google Play VpnService policy and Accessibility API declaration requirements before distribution. Do not assume that a technically possible privileged observation mode is Play-eligible.
- Keep the existing product-contract prohibitions on covert observation, private database access, arbitrary UI automation and automatic ride acceptance/decline.

## 10. Investigation and acceptance gates

No implementation should be declared complete until these gates pass:

1. **Baseline inventory:** obtain exact installed Uber/Bolt package names, versions, target SDKs and APK hashes from the target phone; document the Android/HyperOS version.
2. **Activity proof:** show correct foreground/background transitions for both driver apps, including screen lock, split screen, app process restart and device reboot.
3. **VPN flow proof:** with explicit user consent, capture only selected driver-app flows; prove all other apps bypass the VPN; prove Uber/Bolt retain working connectivity across Wi-Fi/mobile changes, IPv4/IPv6 and UDP/QUIC.
4. **Attribution proof:** verify the owning UID/package mapping and handle shared processes or unresolved flows without false attribution.
5. **TLS capability matrix:** test local CA inspection against the exact app versions; record whether each connection is decrypted, pinned, QUIC, unsupported or unknown. Do not infer pinning from a generic TLS failure.
6. **Deep instrumentation feasibility:** only in a controlled owner-authorised test environment, identify the actual networking stack and determine whether a stable, non-secret plaintext observation boundary exists. Document app update/integrity risks.
7. **Semantic validation:** correlate captured observations with controlled UI actions and provider-confirmed outcomes. Unknown endpoints and payloads remain unclassified.
8. **Safety and performance:** verify no dropped ride-app connectivity, unacceptable battery drain, sensitive-data leakage or background-service instability.
9. **Policy and licence review:** review provider terms, Android distribution policy and all third-party component licences before enabling any mode for distribution.

## 11. Current decision record

- **Accepted direction:** investigate and build activity/network observation as an internal BusyBee subsystem; no separate product or custom OS is required for the initial implementation.
- **Preferred first implementation:** UsageStatsManager plus app-scoped VpnService flow metadata, integrated with existing Accessibility/Notification observations.
- **Research-only option:** local TLS inspection and app-process instrumentation against exact app builds in a controlled environment.
- **Not promised:** complete decrypted Uber/Bolt API logs, stable hooks across app versions, or the ability to invoke private provider operations.
- **Not authorised by this document:** code changes, root installation, APK patching, credential capture, private endpoint replay, ride acceptance/decline automation, PR merge or release.

The user will plan the next phase separately. This document records findings and boundaries; it does not start implementation.

## 12. References

- Android UsageStatsManager: https://developer.android.com/reference/android/app/usage/UsageStatsManager
- Android NetworkStatsManager: https://developer.android.com/reference/android/app/usage/NetworkStatsManager
- Android VpnService: https://developer.android.com/reference/android/net/VpnService
- Android VpnService.Builder: https://developer.android.com/reference/android/net/VpnService.Builder
- Android ConnectivityManager: https://developer.android.com/reference/android/net/ConnectivityManager
- Android Network Security Configuration: https://developer.android.com/privacy-and-security/security-config
- PCAPdroid source: https://github.com/emanuele-f/PCAPdroid
- PCAPdroid TLS inspection guide: https://emanuele-f.github.io/PCAPdroid/tls_decryption.html
- PCAPdroid app API: https://github.com/emanuele-f/PCAPdroid/blob/master/docs/app_api.md
- Frida documentation: https://frida.re/docs/
- OWASP Mobile Application Security Testing Guide: https://mas.owasp.org/MASTG/
- Uber Driver API: https://developer.uber.com/docs/drivers/introduction
- Bolt Fleet Integration API: https://developer.bolt.eu/fleet-integration/main/
