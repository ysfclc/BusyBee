# Owned Android Plaintext Observer POC — Runbook

This is a controlled experiment against **our own test application and local fixture only**. It does not target Uber, Bolt or any third-party process.

## What this proves

- The app constructs a synthetic JSON request and calls `PlaintextBoundary.outbound()` after serialisation but immediately before handing the body to `HttpsURLConnection`.
- The app reads the HTTPS response body after Android's TLS stack has decrypted it, then calls `PlaintextBoundary.inbound()` before JSON parsing or state handling.
- A Frida script hooks only those two app-owned methods and emits an allowlisted summary. It never prints raw payloads, headers, cookies or secret values.
- A local Python HTTPS fixture returns deterministic offer-observed, offer-updated and offer-expired responses.

This proves the plaintext boundary in a test app we own. It does **not** prove Uber/Bolt use the same methods, runtime, networking library or an instrumentable architecture.

## Requirements

- JDK 17, Android SDK 35, Android platform-tools and Python 3.
- OpenSSL.
- An Android emulator with a rootable test image and Frida server, or another explicitly controlled test environment where Frida can attach to this owned app.
- Frida client/server versions must match. Do not use a production driver phone or provider account for this experiment.

## 1. Create a local test CA and server certificate

From the repository root:

```sh
sh research/plaintext-observer/fixture/create-test-ca.sh
```

The script creates ignored files under `research/plaintext-observer/fixture/certs/`. The private keys are local-only and must never be committed or shared.

Install **only** `test-ca.crt` as a user CA on the test emulator (Settings → Security → Encryption & credentials → Install a certificate → CA certificate). This certificate is solely for this local fixture. Remove it after testing.

## 2. Start the HTTPS fixture

```sh
python3 research/plaintext-observer/fixture/server.py \
  --host 127.0.0.1 --port 8443 \
  --cert research/plaintext-observer/fixture/certs/localhost.crt \
  --key research/plaintext-observer/fixture/certs/localhost.key
```

Forward the port to the emulator:

```sh
adb reverse tcp:8443 tcp:8443
```

The app endpoint should be `https://localhost:8443/event`. The server accepts only synthetic event JSON and never contacts external services.

## 3. Build and install the isolated POC app

The main repository Android wrapper is reused; this is a separate Gradle project and application ID:

```sh
chmod +x android/gradlew
android/gradlew -p research/plaintext-observer/android testDebugUnitTest assembleDebug
adb install -r research/plaintext-observer/android/app/build/outputs/apk/debug/app-debug.apk
```

The debug package is `com.ysfclc.busybee.plaintextpoc.debug`. Open **BusyBee Plaintext POC**, set endpoint to `https://localhost:8443/event`, and leave the app ready.

## 4. Attach the owned-app observer

Start a matching Frida server on the test emulator using the standard Frida Android setup. This requires a controlled emulator configuration; it is not a production deployment instruction.

From a machine with the matching Frida CLI installed:

```sh
frida -U -f com.ysfclc.busybee.plaintextpoc.debug \
  -l research/plaintext-observer/instrumentation/observer.js
```

The script targets only:
- `com.ysfclc.busybee.plaintextpoc.PlaintextBoundary.outbound(String)`
- `com.ysfclc.busybee.plaintextpoc.PlaintextBoundary.inbound(String)`

It does not enumerate or attach to other apps. If the method is absent or its signature changes, the script must fail visibly; do not broaden hooks to unrelated TLS methods.

## 5. Execute and inspect

Tap **Send synthetic offer lifecycle**. Expected Frida messages include:
- `outbound-pre-transport` for offer_lookup, offer_update and offer_expired.
- `inbound-post-tls-decrypt` for offer_observed, offer_updated and offer_expired.
- Matching correlation ID `poc-offer-0001` and sequence values 1–3.
- `secret_fields_redacted: true` for both directions.
- No `synthetic_secret` value, raw payload, headers or credentials in observer output.

The app screen should show the three response event types. The local fixture emits no provider data and no real account identifiers.

## Acceptance checklist

- [ ] HTTPS handshake succeeds using the locally installed test CA.
- [ ] All three outbound events are observed before transport.
- [ ] All three inbound events are observed after TLS decryption.
- [ ] Request/response correlation IDs and sequence values match.
- [ ] The observer output contains allowlisted fields only; secret values never appear.
- [ ] Removing the Frida hook causes the observer evidence to disappear rather than silently claiming coverage.
- [ ] A malformed/unavailable endpoint produces a visible app error.
- [ ] No third-party app, account, traffic, credential or provider APK is involved.

## Security cleanup

Stop the fixture, uninstall the POC app, remove the user-installed test CA, remove the local certificate directory if no longer needed, and stop Frida server. Do not commit certificates, private keys, Frida binaries, device dumps or raw traffic.

## Limits and next gate

This first implementation demonstrates explicit application-owned plaintext boundary methods. Frida is observing those methods; it is **not** yet a generic OkHttp/TLS hook and is not evidence of compatibility with Uber/Bolt. Next, measure observer overhead against an uninstrumented run and validate drop/delay/reorder/malformed scenarios. Provider-specific access and permission review remains separate.
