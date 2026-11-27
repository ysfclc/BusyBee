# Plaintext Observer Proof of Concept — Design

**Date:** 2026-11-15  
**Status:** first owned-app harness, local HTTPS fixture and allowlist-only Frida hooks implemented on `feat/native-provider-bridge-core`; Android CI and emulator execution pending  
**Parent design:** [Platform Activity Integration](PLATFORM_ACTIVITY_INTEGRATION.md)  
**Target:** an Android test application and HTTPS test service that we own

## 1. Objective

Prove two separate claims without touching Uber Driver or Bolt Driver:

1. Outbound application request data can be observed in plaintext inside an authorised app process before TLS encryption.
2. Inbound application response data can be observed in plaintext inside that process after TLS decryption and before the application consumes it.

The test must also prove that a runtime observer can identify a known synthetic offer lifecycle, redact secrets and report when it loses coverage.

This is not a provider-app compatibility test and cannot establish that Uber/Bolt use the same networking library, method names, serialization path or runtime.

## 2. POC components

### A. Android test application

A small Kotlin application, separate from BusyBee production, with:

- A synthetic Offer API client.
- A deliberately stable application-owned boundary method for outbound request construction.
- A deliberately stable application-owned boundary method for inbound response decoding.
- A test-only observer interface that can emit structured evidence.
- A visible test screen showing sent, received, parsed, redacted and failed events.
- A test mode indicator so no test traffic can be mistaken for real driver activity.

The app should use a standard HTTPS client (OkHttp is an initial candidate) and a test certificate trusted only by the test environment. No provider credentials or real ride data are used.

### B. Local HTTPS test service

A local test endpoint, reachable from the emulator, which:

- Accepts synthetic JSON offer events.
- Returns deterministic synthetic offer responses.
- Can delay, duplicate, reorder, truncate or reject responses under explicit test controls.
- Returns a synthetic lifecycle sequence: observed → updated → expired, and a separate accepted-in-test outcome.
- Never contacts Uber, Bolt or any third-party service.

### C. Dynamic instrumentation runner

A test-only instrumentation setup for the owned app process. The implementation can evaluate Frida in an Android emulator or embed a Gadget only in the app we own.

The observer hooks only the app-owned boundary methods in this POC. It must not inspect unrelated processes, collect device-wide traffic or access third-party applications.

Two observation points are required:

- **Outbound:** immediately after the synthetic request object is fully constructed and before it is handed to the HTTPS transport.
- **Inbound:** immediately after the HTTPS response body has been decoded and before the application applies its business-state transition.

These are application plaintext boundaries. They prove the data is available before encryption and after decryption without relying on packet capture.

A separate optional test may observe the underlying networking-library methods to compare coverage. It is not the first acceptance gate because library internals are version-sensitive.

### D. Evidence viewer

A simple local test log, not a production dashboard. Each observed event displays:

- Direction: outbound / inbound.
- Boundary name and app build.
- Monotonic sequence number.
- Wall-clock timestamp and monotonic elapsed time.
- Synthetic correlation ID.
- Payload field names and redacted values.
- Observer health and parse status.

No raw TLS keys, cookies, auth headers or full device traffic are required.

## 3. Synthetic event contract

Use a deliberately fictional provider and synthetic IDs.

Example outbound request:

```json
{
  "event_type": "offer_lookup",
  "correlation_id": "poc-offer-0001",
  "driver_session": "synthetic-session",
  "client_timestamp": "2026-11-15T12:00:00Z",
  "capabilities": ["offer", "trip_status"]
}
```

Example inbound offer response:

```json
{
  "event_type": "offer_observed",
  "correlation_id": "poc-offer-0001",
  "offer": {
    "offer_id": "synthetic-ride-001",
    "currency": "PLN",
    "gross_amount": 24.50,
    "pickup_distance_km": 1.8,
    "estimated_trip_distance_km": 6.2,
    "expires_at": "2026-11-15T12:00:20Z"
  }
}
```

Example lifecycle update:

```json
{
  "event_type": "offer_expired",
  "correlation_id": "poc-offer-0001",
  "offer_id": "synthetic-ride-001",
  "event_timestamp": "2026-11-15T12:00:20Z"
}
```

All values are fake and for test use only. The POC schema is a fixture, not a claim about Uber/Bolt wire formats.

## 4. Test scenarios

| ID | Scenario | Expected result |
|---|---|---|
| POC-01 | Send a valid synthetic offer request | Outbound plaintext observed with correct correlation ID |
| POC-02 | Receive a valid synthetic offer response over TLS | Inbound plaintext observed after decryption |
| POC-03 | Parse the offer lifecycle | Correct offer ID, amount, currency and expiry |
| POC-04 | Response delayed beyond expiry | Event marked stale/expired; not surfaced as live |
| POC-05 | Duplicate response | Evidence retained; canonical offer not double-counted |
| POC-06 | Out-of-order update | Sequence/timestamps retained; state reconciliation flags conflict |
| POC-07 | Missing optional fields | Null/unknown preserved; no fabricated zero |
| POC-08 | Malformed JSON / transport error | Parse or transport failure is visible |
| POC-09 | Synthetic secret field included | Secret is redacted before persistence |
| POC-10 | Observer detached or hook target changes | Coverage becomes unavailable; no silent success |
| POC-11 | App process restarts | Observer reattaches only through explicit test harness lifecycle; gap is logged |
| POC-12 | Instrumentation overhead | CPU, memory, battery and latency are measured against an uninstrumented baseline |

## 5. Acceptance criteria

The POC is successful only when all are true:

- The observer sees the exact synthetic outbound payload before the HTTPS transport encrypts it.
- The observer sees the exact synthetic inbound payload after TLS decryption and before domain-state processing.
- Both directions correlate to the same synthetic offer without relying on timing guesses.
- A complete lifecycle can be reconstructed from multiple events.
- Redaction tests prove secrets are not written to persistent evidence.
- Duplicate, delayed, malformed and out-of-order cases are represented honestly.
- Observer loss, unsupported method signatures and process restarts are visible as coverage gaps.
- The test report compares instrumented and uninstrumented runtime overhead.
- No production Uber/Bolt APK, account, credential or traffic is involved.

## 6. Outputs to preserve

The POC implementation should produce:

1. A standalone Android test app module or separate test project.
2. A local HTTPS fixture service and deterministic event fixtures.
3. A test-only instrumentation runner and observer script/configuration.
4. Automated tests for the event envelope, redaction, ordering and lifecycle.
5. A repeatable runbook for emulator setup and test execution.
6. A results report recording device/emulator image, Android version, app build, library versions, instrumentation version, test outcomes and limitations.

Do not put generated test APKs, certificates with private keys, Frida binaries, secrets or device-specific state into the repository. Commit source, public test fixtures and reproducible instructions only.

## 7. What this POC will not prove

- It does not prove Uber/Bolt can be instrumented.
- It does not prove their apps expose the same fields as the synthetic fixture.
- It does not prove their terms permit a particular acquisition method.
- It does not prove the technique works on a non-rooted Redmi without modifying the provider apps.
- It does not authorise replaying provider requests or automating offer acceptance.
- It does not replace the separate provider-access and legal/terms assessment.

## 8. Implementation order

1. Add the synthetic HTTPS fixture service and event fixtures.
2. Build the Kotlin test app with stable request/response boundary methods.
3. Verify plaintext at both boundaries using the app-owned observer interface.
4. Add dynamic instrumentation of only the owned boundary methods.
5. Add redaction, correlation, lifecycle and failure-mode tests.
6. Record baseline vs instrumented performance.
7. Review the result and decide whether a separate provider compatibility study is justified.

**Current implementation checkpoint:** the isolated Android project, HTTPS fixture, local test-CA generator, explicit outbound/inbound boundary methods, Frida observer and runbook are committed. POC-01/02 are not yet claimed as passed; CI build and a controlled emulator run are the next verification gates. See [runbook](../research/plaintext-observer/README.md).
