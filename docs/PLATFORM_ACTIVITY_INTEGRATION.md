# Platform Activity Integration — Research and Design Record

**Date:** 2026-11-15  
**Status:** design baseline; implementation not started  
**Scope:** user-authorised Uber Driver / Bolt Driver activity inputs for the BusyBee Driver Data Mirror and Offer Desk  
**Related product documents:** [Product charter](PRODUCT_CHARTER.md), [System architecture](ARCHITECTURE.md), [Roadmap](ROADMAP.md), [Driver mirror import contract](DRIVER_MIRROR_IMPORT.md), [Architecture decisions](DECISIONS.md)

## 1. Why this document exists

The existing Phase 3 Driver Data Mirror is a local-first ledger with provider-neutral CSV import, raw-row evidence, duplicate protection and cautious financial rollups. It is not a direct Uber/Bolt integration. Existing notification capture and user-entered offer paths are limited, user-visible inputs; they do not expose arbitrary application networking data.

The product requirement now being investigated is broader:

- Observe ride-hailing activity from inside the provider application's process where technically and legitimately possible.
- Specifically investigate the plaintext boundary: outbound request data before TLS encryption and inbound response data after TLS decryption.
- Extract useful offer/trip lifecycle facts into BusyBee's provider-neutral model.
- Present a unified view of offers, trips and driver economics.
- Keep the official provider application responsible for provider-side decisions and actions unless a documented, supported integration explicitly permits more.

This is an integration research track, not a claim that Uber or Bolt currently expose an API to BusyBee.

## 2. Terminology: the distinctions that must not be blurred

### 2.1 Packet/VPN observation

Android `VpnService` can route traffic through a local VPN interface and observe packets, destinations, timing and sizes. For ordinary HTTPS/TLS traffic, it does not reveal request/response plaintext. It is not equivalent to observing the payload inside the app process.

**Decision:** VPN packet observation is not the primary solution to the stated requirement. It may only be considered for transport diagnostics or metadata where appropriate.

### 2.2 User-visible observation

Notification listeners, user-selected screenshots, screen capture and accessibility-visible UI can expose information rendered by an app. These are useful fallbacks and can be user-friendly, but they are not in-process API observation and may omit fields or lifecycle events.

**Decision:** keep these as distinct input adapters. Never describe them as network/API interception.

### 2.3 In-process plaintext observation

An application constructs request objects and serialises request bodies before TLS encrypts bytes for transport. On the receive path, TLS decrypts bytes before the application consumes the response. Instrumentation inside an authorised test process can inspect data at those boundaries or at the networking-library layer.

Potential research techniques include dynamic instrumentation in a controlled test application, instrumented builds owned by the developer, and documented debugging/test hooks. Frida is a known instrumentation framework; Frida Gadget is a library loaded by the target process. These facts establish technical plausibility, not compatibility with a production ride-hailing application.

The exact hooks are runtime- and version-dependent. A generic OkHttp hook, Java TLS hook or native TLS hook cannot be assumed to observe every request. The current networking stack, serialization path, TLS provider, native libraries and process topology of each provider app have not been verified.

**Decision:** prove the observation model against an owned test application first. Do not ship instrumentation or modified provider binaries as part of BusyBee.

## 3. Feasibility matrix

| Approach | Can expose plaintext? | Requires target app modification or privileged access? | BusyBee role |
|---|---|---|---|
| Official provider API / partner integration | Yes, for documented fields and permitted operations | Provider credentials/approval | Preferred production adapter |
| User-owned statement/export import | Yes, in exported records | User selects file | Existing baseline; reliable historical evidence |
| Opt-in notification access | Only fields present in notifications | Explicit Android notification-listener permission | Limited live activity adapter |
| User-initiated screen capture / OCR | Only rendered and captured content | Explicit user action/permission | Fallback evidence adapter |
| Android VPN packet observation | No, not for ordinary TLS payloads without separate decryption capability | VPN consent; further decryption is not guaranteed | Not the requested solution |
| Frida Server / root-based injection | Potentially, if the relevant process and hooks are compatible | Root/privileged process injection | Controlled research only; not assumed for daily phone |
| Frida Gadget in a target APK | Potentially, if the gadget loads and relevant hooks are compatible | Target APK must be modified and re-signed | Controlled research only; not assumed viable for provider apps |
| Emulator + owned test app | Yes, by instrumenting an app and network stack we control | Test harness, not provider app | First proof-of-concept environment |

### Non-root reality

A non-root Android device does not grant one ordinary app the ability to inject code into another app's process. Frida Gadget can be loaded by a target process when that target is packaged to include it; this is not a general-purpose BusyBee-to-Uber/Bolt injection mechanism.

Embedding Gadget into a third-party APK means modifying and re-signing that APK. This can invalidate the original signature and may break updates, integrity/attestation checks, certificate pinning, login, push notifications or other functionality. It may also violate provider terms. None of these failure modes has been tested against current Uber/Bolt builds.

Root-based injection avoids modifying the target APK but changes the device security model and may still be detected or blocked. Root is not a prerequisite we may silently impose on the driver's daily phone.

**Current device constraint:** the pilot device is a Redmi Note 12 Pro 5G running Android 14. Its root state and willingness to modify the production provider apps are not assumed. No root, bootloader unlock, APK replacement or app tampering is authorised by this design record.

## 4. Proposed BusyBee architecture

Provider activity must enter the existing Driver Data Mirror through a new source-adapter boundary. The domain model must not depend on Frida, OkHttp, TLS internals, Android notification APIs or provider-specific UI layouts.

```text
Provider activity sources
  ├─ documented API / partner integration
  ├─ user-owned exports and statements
  ├─ opt-in notifications
  ├─ user-initiated visible capture
  └─ authorised research/test instrumentation (not production by default)
                 |
          Source adapters
                 |
       Evidence envelope + provenance
                 |
       Provider-neutral normalisation
                 |
      Offer / Trip / Earnings lifecycle
                 |
       Local source ledger + evidence
                 |
       Offer Desk / Driver Mirror / analytics
```

### 4.1 Adapter contract

Each adapter should report:

- Stable adapter ID, adapter version and provider identity.
- Capability set: offers, offer updates, expiry, acceptance state, trip state, route, fare, fees, bonuses, cancellation, payout, etc.
- Acquisition method and required user permission.
- Whether data is authoritative, user-visible, inferred or partial.
- Source event time, local receive time and clock/latency uncertainty.
- Original provider event/request identifier where lawfully available.
- Evidence reference or permitted payload digest.
- Field-level completeness and confidence rationale.
- Retention/licence classification.
- Adapter health, last successful observation and known blind spots.

Do not persist credentials, session tokens, cookies, authorisation headers, payment credentials or unrelated personal payload fields. Research instrumentation must redact secrets before data leaves the test process.

### 4.2 Canonical lifecycle

The normalised domain should model events rather than treating each observation as a complete truth:

- `OfferObserved`
- `OfferUpdated`
- `OfferExpired`
- `OfferDecisionObserved`
- `TripStarted`
- `TripUpdated`
- `TripCompleted`
- `TripCancelled`
- `EarningObserved`
- `AdjustmentObserved`

An observation is evidence; it does not automatically prove a provider-side state transition. Preserve source and timestamps, deduplicate idempotently, reconcile conflicting evidence, and expose unknown states rather than fabricating them.

The canonical offer should allow nullable provider-specific facts: pickup/dropoff, estimated distance/time, gross fare, currency, bonuses, expiry, rating, acceptance constraints and any other fields actually observed. Never assume a field exists in every provider's response.

### 4.3 Observation and action are separate capabilities

BusyBee may aggregate and rank information the driver is allowed to access. Capturing an acceptance-related request does not authorise BusyBee to replay it, construct a new provider request, automate acceptance, or control the provider account.

The first integration design is **observe → normalise → explain → hand back to the official app**. Provider-side actions remain in the official app unless a documented provider integration explicitly authorises them and a separate product/security decision approves the action.

## 5. Research plan and gates

### Gate A — owned test harness (first)

Build or use a small Android test application and a local test endpoint that we control. It should generate representative request/response payloads and use TLS. Demonstrate:

1. Outbound application-level data is visible before encryption within the owned test process.
2. Inbound application-level data is visible after decryption within the owned test process.
3. The observer extracts a deliberately synthetic offer and lifecycle sequence.
4. Secrets and unrelated fields are redacted.
5. The observer fails closed and reports unsupported runtime/library versions rather than silently emitting incomplete data.

This validates the concept without modifying or bypassing a third-party app.

### Gate B — provider access and compatibility assessment

For Uber and Bolt separately:

- Identify documented APIs, partner programmes, export formats, notification content and supported user-visible integration paths.
- Review applicable terms, Android policies, privacy obligations and data retention limits before implementation.
- Establish what the user can lawfully access and what BusyBee may store, transform and display.
- Record the actual provider app version, runtime/network stack only from authorised analysis and publicly documented or otherwise permitted evidence.
- Do not attempt to bypass anti-tamper, authentication, integrity checks, certificate pinning or access controls.
- Do not replace the user's production app with a modified APK as an assumed deployment strategy.

### Gate C — real-data adapter proof

Use user-owned exports or another permitted source to validate the canonical offer/trip schema and reconcile it against source records. The adapter must show evidence and field-level gaps.

### Gate D — device and shift reliability

Only after a supported and permitted input path is established, measure on the Redmi Android 14 device:

- Background survival and missed-event detection.
- Battery, thermal and memory overhead.
- Duplicate, delayed and out-of-order event handling.
- Behaviour across app updates and process restarts.
- Data minimisation, local storage and deletion.
- Clear degraded/not-connected state.

No production rollout based solely on an emulator demonstration.

## 6. Stress cases and acceptance criteria

| Risk | Required behaviour |
|---|---|
| Provider app update changes networking internals | Adapter reports incompatible/unknown; no silent false data |
| TLS or library implementation differs | Capability is marked unsupported until revalidated |
| Instrumentation is unavailable or detected | Do not evade protections; fall back to permitted adapters |
| Offer arrives late or already expired | Mark stale/expired; never present as live |
| Two offers overlap | Preserve independent IDs and event ordering |
| Duplicate observations | Retain evidence, prevent duplicate financial counting |
| Conflicting lifecycle evidence | Preserve conflict and source provenance |
| Partial offer fields | Display unknowns explicitly; do not invent values |
| Process death / Android background limits | Surface source-health gap and recover without fabricating events |
| Credentials or sensitive headers appear | Redact; never persist them in the driver ledger |
| Provider action is requested | Hand back to official app; no request replay or unsupported automation |

Minimum proof-of-concept acceptance:

- Both outbound and inbound plaintext observation demonstrated in an owned test app.
- Synthetic offer extracted with correct fields, event order and timestamps.
- Redaction tests pass.
- Unsupported versions and dropped events are visible.
- No production Uber/Bolt app modification, root requirement or provider-protection bypass is needed for the owned test.
- Provider-specific production feasibility remains separately labelled unproven until Gates B–D pass.

## 7. Security, privacy and compliance boundaries

- Only analyse test applications, systems and data for which the project has explicit authorisation.
- Do not intercept another user's traffic or collect unrelated app content.
- Do not store session tokens, cookies, credentials, payment details or full raw traffic dumps.
- Minimise payload fields before persistence; keep personal trip/offer data local by default.
- Respect provider terms, applicable law, Android platform policy and data-subject rights.
- Do not bypass authentication, certificate pinning, anti-tamper, integrity checks or other access controls.
- Do not replay provider requests, forge signatures, impersonate clients or automate unsupported provider actions.
- Maintain a source register entry and a capability/permission declaration for every adapter.

## 8. Current conclusion

**Technically plausible:** observing plaintext inside an application process is a known instrumentation capability in controlled/authorised environments.

**Not yet proven:** compatibility with current production Uber Driver and Bolt Driver apps; complete offer/trip field coverage; stable operation on the non-rooted daily phone; or a supported path that does not require modifying provider apps.

**Not the solution:** ordinary VPN packet capture, generic Android usage statistics or screen OCR presented as if they were in-process API observation.

**Product decision:** preserve the in-process plaintext observation requirement as an explicit research question. First validate the boundary in an owned test harness, then assess documented/provider-permitted access paths. Keep the production BusyBee architecture adapter-based and source-neutral. Do not make root, repackaged provider APKs or unsupported request replay a product dependency.

## 9. Reference material

These references describe the instrumentation and Android testing mechanisms. They do not establish permission to instrument Uber/Bolt or guarantee compatibility with either application.

- [Frida — Android](https://frida.re/docs/android/)
- [Frida — Gadget](https://frida.re/docs/gadget/)
- [OWASP MASTG — Dynamic Analysis on Non-Rooted Devices](https://mas.owasp.org/MASTG/techniques/android/MASTG-TECH-0026/)
- [OWASP MASTG — Detecting Runtime Hooking](https://mas.owasp.org/MASTG/knowledge/android/MASVS-RESILIENCE/MASTG-KNOW-0030/)
- [Android — VPN](https://developer.android.com/develop/connectivity/vpn)
- [Android — Notification listener service](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
