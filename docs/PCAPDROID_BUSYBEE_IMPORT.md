# PCAPdroid → BusyBee payload import

## Purpose

Import traffic that PCAPdroid has already decrypted and exported, interpret the individual HTTP calls and convert supported driver objects into the existing BusyBee Driver Mirror. This is a local evidence-ingestion path, not a general TLS decryptor or provider API client.

## Phone workflow

1. Open PCAPdroid and select the target app (Bolt Driver or Uber Driver).
2. Enable Full payload capture. Enable TLS decryption where the target app permits it.
3. Reproduce a small, identifiable, harmless app action.
4. Open PCAPdroid's HTTP Requests view. Confirm that the target requests contain bodies (a green open-lock connection indicator denotes successful TLS decryption).
5. Export the requests as HAR.
6. In BusyBee open **Sources → Import PCAPdroid HAR**, select the HAR file, then choose Bolt Driver or Uber Driver.
7. Review reconstructed requests and responses in the latest capture list. Offer/trip-shaped JSON records with explicit IDs are added to the existing Driver Mirror and carry capture/evidence references.

## What the interpreter currently does

- Parses HAR request and response entries and their method, host, path, status, timestamp and content types.
- Removes URL query strings from the persisted/displayed path; does not persist HTTP header values.
- Decodes HAR base64 bodies, JSON, UTF-8 text and form bodies.
- Expands gzip and deflate bodies within a bounded size.
- Identifies opaque binary and Protobuf-like wire layouts. Protobuf field numbers are not assigned meanings without a schema.
- Classifies diagnostic, analytics, realtime-handshake, provider-API-candidate and other calls.
- Extracts explicit offer/trip objects from provider-candidate JSON payloads. Fields remain nullable; unknown currency remains UNKNOWN.
- Redacts common credentials and personal identifiers from stored payload previews.
- Persists capture metadata and calls in `busybee_driver_mirror.db`; normalized records and evidence use the existing Driver Mirror tables. No third data store is created.

## Important limits

- HAR only contains the traffic PCAPdroid chose and was able to expose. Encrypted-only TLS flows do not become plaintext through this import.
- This version interprets HTTP request/response bodies. It does not yet reassemble raw TCP streams, MQTT messages or arbitrary WebSocket binary frames from PCAPNG.
- A Protobuf-like payload needs its schema before its fields can be mapped.
- Extractor aliases are generic initial guesses, not a verified Bolt/Uber schema. Real user-owned HAR captures are needed to add accurate provider-specific field mappings and to distinguish offers, trips, payouts and lifecycle updates.
- An endpoint being classified as a provider API candidate is a research hint, not proof that its payload represents a driver event.
- The import does not replay calls, execute provider-side actions or reuse credentials.

## Verification

The JVM fixture tests cover JSON request/response reconstruction, explicit offer extraction, analytics/diagnostics exclusion, credential redaction, gzip and Protobuf-like recognition. The Android CI workflow tests and assembles the BusyBee app. Real-device validation against an actual PCAPdroid HAR remains a separate acceptance check.
