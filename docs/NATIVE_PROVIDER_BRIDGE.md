# Native Provider Bridge — Product and Architecture Design

**Date:** 2026-11-15  
**Status:** design baseline; implementation not started  
**Product outcome:** see the driver's Uber/Bolt activity inside BusyBee through one native, structured integration surface.

Related: Product Charter, System Architecture, Platform Activity Integration, Driver Mirror Import, Plaintext Observer POC.

## 1. User outcome

The driver should not have to think about CSV files, notification parsing, network hooks or provider-specific formats.

They open BusyBee, connect a provider where a permitted connection path exists, and see the provider's available activity in a consistent BusyBee experience:

- Current and recent offers.
- Offer details and expiry where exposed.
- Accepted, rejected, expired and cancelled outcomes where observable.
- Active and completed trips.
- Pickup/dropoff, route, distance and duration where exposed.
- Gross fare, fees, bonuses, tips, adjustments and payout where exposed.
- Online/session state only where a source explicitly supplies it.
- Earnings timeline and cross-provider daily/weekly summaries.
- Clear connection health, last sync and data coverage.

The experience should feel like a first-party integration. The acquisition method is an implementation detail, but must never be disguised: every record carries provenance and freshness.

## 2. What "native API-like" means in BusyBee

It means a first-class **internal provider API** and typed event contract, not a claim that Uber or Bolt provide an unrestricted public driver API.

~~~text
Uber adapter ─┐
Bolt adapter ─┼─> Provider Bridge API ─> Event normaliser ─> Driver Mirror
Other adapter ┘          |                      |                 |
                    capability + health     provenance       offers / trips /
                    permission state        + validation      earnings / insights
~~~

Each provider is an adapter behind the same contract. UI and analytics never parse Uber notification text, Bolt-specific JSON, instrumentation output or CSV columns directly.

### 2.1 Adapter interface (conceptual Kotlin contract)

~~~kotlin
interface DriverProviderAdapter {
    val provider: ProviderId
    suspend fun capabilities(): ProviderCapabilities
    suspend fun connectionState(): ProviderConnectionState
    suspend fun connect(): ConnectResult
    suspend fun sync(since: Instant?): SyncResult
    fun observeEvents(): Flow<ProviderEvent>
    suspend fun disconnect()
}
~~~

The interface is internal to BusyBee. An adapter can be backed by an official API, documented export, Android-native user-authorised event source or another separately approved mechanism. Unsupported capabilities return an explicit unsupported state; never an empty successful response.

### 2.2 Canonical event envelope

Every observation is normalised into a provider-neutral event with:

- Event ID and provider event ID when available.
- Provider and adapter ID/version.
- Event type and lifecycle state.
- Source method (official API, user export, notification, user capture, authorised test instrumentation, etc.).
- Event time, receive time and optional expiry.
- Correlation ID and trip/offer identity where available.
- Normalised payload with nullable fields.
- Field-level availability/completeness and confidence.
- Evidence reference/fingerprint and deduplication key.
- Retention classification and redaction status.

Keep original provider payload only when permitted and necessary. Never store access tokens, cookies, passwords, payment credentials or unrelated request/response content.

### 2.3 Capability discovery

Each provider advertises capabilities independently:

- Live offers
- Offer lifecycle
- Active trip
- Completed trips
- Trip route, distance and duration
- Fare breakdown and payouts
- Driver session
- Ratings
- Historical sync

Each capability has an access state: available, partial, permission required, approval required, unsupported, blocked or unknown.

Do not show a generic green "Connected" state if only notification titles are visible. Show exactly which capabilities work.

## 3. Connection experience

Add a dedicated **Connected Apps** section under Sources, with provider cards for Uber Driver and Bolt Driver.

Each card contains:

- Provider identity and app detection (detection is not a connection).
- Connection state and selected acquisition method.
- Capability chips: Offers, Trips, Earnings, History, etc.
- Last event received and last successful sync.
- Permissions required, with explanation before opening Android Settings.
- Coverage summary, e.g. "Offers: partial via notifications; trip history: not connected".
- Test-connection action and disconnect / clear-source-data action.
- Provider-specific setup and known limitations.

Connection states distinguish:
1. Not configured.
2. Permission required.
3. Connected with partial data.
4. Connected with verified capability coverage.
5. Stale / degraded.
6. Blocked / unsupported.
7. Disconnected.

Installing Uber/Bolt or detecting its package is not proof of access. The UI must not claim an API connection merely because notification access was granted.

## 4. Driver data experience inside BusyBee

The Driver Mirror becomes the unified view, not a separate CSV import report.

### 4.1 Overview

- Today's gross and known fees/bonuses.
- Net before vehicle cost only when source fields support it.
- Completed trip count and active trip state.
- Offers observed and outcomes, with coverage caveat.
- Per-provider contribution.
- Freshness indicator and missing-data warnings.

Never label gross as profit or take-home. Never infer unknown fees as zero.

### 4.2 Activity timeline

One chronological stream across providers:
- Offer received / updated / expired.
- Trip accepted / started / updated / completed / cancelled.
- Fare, tip, bonus, fee or payout adjustment.
- Imported historical record.

Each item shows provider, event time, source method, freshness and field completeness. Tapping it opens original evidence where available.

### 4.3 Offers

A unified offer card shows only fields actually available:
- Provider and live/stale state.
- Fare and currency.
- Pickup and destination.
- Pickup distance, trip distance and estimated duration.
- Expiry countdown only when a reliable expiry timestamp exists.
- Bonus, passenger rating and constraints where exposed.
- Estimated vehicle cost and net/hour only when inputs are complete.

The card links back to the official provider app for the provider-side decision. BusyBee does not auto-accept or replay provider requests.

### 4.4 Trips and earnings

Trip records reconcile observations into a trip lifecycle. Earnings are itemised by source fields (gross, platform fee, tip, bonus, adjustment, payout) rather than compressed into one guessed amount. Statement/export records can reconcile and correct incomplete live observations; preserve both evidence streams and the reconciliation result.

## 5. Source acquisition strategy

A provider adapter is not the same thing as its acquisition mechanism. Evaluate methods in this order, separately for Uber and Bolt:

1. **Official, documented provider API / partner access** — preferred for a stable supported live connection, if available to this use case.
2. **Provider-supported account data export / statement** — preferred for authoritative historical trip and earnings records.
3. **Android-native user-authorised signals** — notifications and explicit share/open-with flows; useful for incremental events but inherently partial.
4. **Other explicitly permitted interfaces** — only after terms, policy, privacy and technical review.
5. **In-process plaintext instrumentation research** — separate controlled feasibility track; not a production connection assumption. The owned-app POC validates the technique, not Uber/Bolt compatibility.

Do not access private app databases, silently scrape app storage, bypass authentication or integrity controls, repackage provider APKs as a product dependency, or replay provider requests. If a provider offers no permitted machine-readable access, BusyBee must show the limitation rather than fabricate a "native API" connection.

## 6. How this relates to the existing code

Current implementation, confirmed on main:

- DriverMirrorStore.kt stores imported CSV records and raw import evidence in busybee_driver_mirror.db.
- DriverCommandStore.kt stores offers and notification records in a separate busybee_command_centre.db.
- PlatformNotificationListener.kt extracts visible notification title/body for recognised ride-platform packages.
- MainActivity.kt has Offers, Earnings and Sources surfaces; Sources currently describes notification access and manual/import paths.
- There is no DriverProviderAdapter, unified provider event bus, capability registry or connection-health model.
- The two SQLite stores mean offer observations and imported trip/earnings records are not yet reconciled into one canonical event ledger.

The next implementation must close this model split deliberately. Do not add a third disconnected store or make the UI query raw notification text.

## 7. Proposed implementation slices

### Slice 1 — Provider Bridge domain contract

- Add provider identity, capability, connection state, source method and canonical event types.
- Add adapter registry and event ingestion boundary.
- Add idempotency, correlation, source timestamps, field completeness and health reporting.
- Add tests for event ordering, duplicate observations, partial payloads and stale events.
- Keep the adapter contract independent from the chosen acquisition technique.

**Exit:** synthetic adapter emits offer/trip/earning events and the Driver Mirror can query them through one repository.

### Slice 2 — Unified local ledger

- Migrate or bridge existing offers, notices, records and raw_evidence without losing user data.
- Use versioned, transactional migrations; no destructive database recreation.
- Preserve source evidence and deduplicate across different acquisition methods.
- Reconcile offer observations with trip and statement records.
- Keep private driver data local by default.

**Exit:** old imports and notifications appear in one timeline and analytics without double counting.

### Slice 3 — Connected Apps UX

- Build Uber and Bolt connection cards with real capability states.
- Permission explanation and Android settings handoff.
- Per-provider last event, last sync, data coverage and health.
- Unified activity timeline, offers, trips and earnings.
- Honest partial/unsupported states.

**Exit:** user can see every piece of driver data BusyBee currently has, understand where it came from and what is missing.

### Slice 4 — Live source adapters

- Improve the Android notification adapter as a clearly partial source.
- Add provider-specific import parsers only after obtaining representative user-owned samples.
- Implement any documented provider API adapter only after confirming access and terms.
- Reassess the separate authorised process-observation research track; do not couple it to UI or canonical storage.

**Exit:** at least one real provider source delivers validated records, with field coverage and freshness measured against source evidence.

### Slice 5 — Reconciliation and driver intelligence

- Reconcile live event observations with historical statements.
- Build trip lifecycle, earnings breakdown, platform comparisons and effective hourly economics from verified fields.
- Show missing coverage as explicit gaps.
- Add export and user-controlled deletion.

**Exit:** source totals reconcile; each metric is traceable and explainable.

## 8. Non-negotiable quality rules

- Native-feeling UX does not justify pretending there is an official provider API.
- Never claim connected without a successful capability/data check.
- Never show stale observations as live.
- Never turn missing fields into zero.
- Never double count one offer/trip observed through multiple sources.
- Never lose raw evidence or existing driver records during migrations.
- Keep provider acquisition, canonical model, storage and UI separate.
- User can see and disconnect every source.
- Do not make root, provider APK modification or unsupported request replay a production requirement.
- The first real milestone is driver data visible in BusyBee, with source and freshness labels—not a demo instrumentation console.

## 9. Immediate next checkpoint

Implement Slice 1 and the non-destructive foundation of Slice 2, then surface existing notification/import data in one unified Driver Mirror timeline. This creates a real native integration surface immediately while leaving each provider's live acquisition capability honest and independently replaceable.
