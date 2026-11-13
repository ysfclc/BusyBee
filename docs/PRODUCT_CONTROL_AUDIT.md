# BusyBee Product Control & Write-Path Audit

**Audit date:** 2026-11-13  
**Audited ref:** `daf04d6d45de67203412b7c77b2379253ce57124`  
**Branch:** `feat/live-navigation-vehicle-2026-11-13`  
**Mode:** Read-only product/source audit. STOP-WORK remains active. No product code changed.

## Executive finding

BusyBee has several working-looking create/save flows, but the product does not yet provide the basic record-management controls expected of a driver tool. Most records are create-only. Users can complete some items or skip an offer, but cannot reliably correct, edit, archive, delete, undo, export or restore their own data from the UI.

This is a product completeness and data ownership gap, separate from the reported runtime crash. The audit confirms source-level controls and missing store operations; it does not prove that the controls work on a physical phone.

## Evidence-based control inventory

Status meanings:
- **Present in source:** a visible UI action and corresponding source/store path exist.
- **Partial:** some write/status behavior exists, but lifecycle or user control is incomplete.
- **Missing in audited source:** no corresponding UI/store operation was found in the audited branch.
- **Unverified:** source presence does not establish runtime/device behavior.

| Area | Controls present in source | Missing / incomplete controls | Status |
|---|---|---|---|
| Main navigation | MAP, OFFERS, EARNINGS, DRIVER HUB, SOURCES tabs | No user settings/account/data-management surface | Partial |
| Map layers | Toggle Roadworks, Hazards and Driver reports layers | No layer preferences persistence; chip selection is in-memory only | Partial |
| Destination / route | Enter destination, choose geocoding candidate, choose route alternative, start/stop foreground guidance, open external Google navigation | No saved destinations, route history, route editing, destination correction after selection, or background navigation | Partial |
| Driver road reports | Create a private report with category, description and current/last map position; display reports | No edit, withdraw/delete, expiry, share/consent or moderation controls; report location can use an old last-known fix | Partial |
| Offer Desk rules | Edit and save cost/km, minimum net/hour, maximum pickup distance and minimum rating | Inputs are not validated as a complete numeric/range set; malformed values may silently fall back through `num()`; no reset-to-default or profile presets | Partial |
| Ride offers | Manually create offer; create from captured notification; open platform to review; skip pending offer | No edit/delete/restore; skipped status is the only exposed post-create state control; no acceptance/booking automation (correctly left to platform) | Partial |
| Vehicle profile | Edit vehicle name, category, fuel, consumption, energy price and maintenance/km; persist to SQLite; calculated cost feeds offer scoring | No multiple vehicles or active-vehicle history; category does not change routing profile; validation covers key numeric fields but no vehicle dimensions/access limits | Present, with known limitations |
| Driver admin items | Create reminder, insurance/payment/document/expense/service/contact item; optional due date, amount, recurrence; mark done/undo completion; recurring completion creates next occurrence | No edit/delete/archive; no due-date change or correction; no attachment replacement/removal; amount parsing can silently turn invalid input into null; no reminder notification scheduling is established by this UI | Partial |
| Documents | Pick a document, persist URI permission, store filename/reference, open file | No replace, rename, metadata edit, remove, broken-link repair, file-content copy or backup/export flow | Partial |
| WhatsApp knowledge base | Import TXT into local SQLite, search indexed text, list imported document names | No preview, edit, delete, re-index, per-document search result management or import undo; imported content is stored as full text | Partial |
| Earnings / trip mirror | Import CSV, show totals, platform summaries, history, recent rows and raw evidence; deduplicate by fingerprint | No import preview, field mapping, row selection, correction, exclusion, delete, undo/rollback after successful import, or user-visible reconciliation workflow | Partial; evidence retention exists |
| Sources | Show source catalogue and open platform/settings destinations | No per-source connection health history, credential management, export controls or granular privacy controls | Partial |
| Privacy / ownership | Manifest disables Android backup; personal records are stored in app-local SQLite; road reports are explicitly private; diagnostics are user-copied and omit exception messages | No in-app export, full data deletion, selective deletion, database backup/restore, retention controls or diagnostic reset UI | Missing |

## Write-path findings

### 1. Driver admin records are create/complete only

Evidence:
- `MainActivity.kt`: `itemDialog()` creates a new item; the list exposes `MARK DONE` / `COMPLETED` and `OPEN FILE`.
- `DriverCommandStore.kt`: `addItem()`, `items()`, `completeItem()` exist.
- No update/delete/archive methods exist for `driver_items`.

The completion toggle can undo the done state, but it does not correct the title, details, amount, due date, recurrence or URI. A wrong record therefore remains wrong unless the user creates another one.

### 2. Offer records have only a narrow status update

Evidence:
- `offerDialog()` creates offers.
- `offerCard()` exposes platform review and `SKIP`.
- Store API exposes `saveOffer()` and `updateOfferStatus()`, but no update/delete.

A typo in fare, distance, pickup or destination cannot be corrected. A skipped offer remains stored and there is no visible restore/delete control.

### 3. Imported trip records are effectively immutable from the UI

Evidence:
- `DriverMirrorStore.importRows()` writes import metadata, normalized records and raw evidence transactionally.
- Duplicate fingerprints are ignored while raw evidence is retained.
- UI exposes import history, a small raw-evidence preview and recent records.
- No edit, exclude, delete, import rollback or import-history removal method exists.

Keeping raw evidence is a valuable integrity safeguard. However, it must be paired with a separate, auditable correction layer rather than forcing incorrect normalized values to remain in financial calculations. The UI currently has no such correction layer.

### 4. Import is immediate, without a review step

CSV import reads and parses the selected file, then calls `importRows()` immediately. There is no pre-commit preview, row selection, duplicate explanation, field mapping or confirmation screen. SQLite transaction handling protects a failed import from a partial commit, but it does not provide user-initiated undo after a successful import.

Document and WhatsApp imports also write immediately after file selection. They have no preview or removal flow.

### 5. Validation is inconsistent

- Vehicle profile: checks positive paired consumption/energy price and non-negative maintenance.
- Manual offer: checks fare, pickup/trip distance and duration, but bonus and passenger rating are not range-validated.
- Reminder/admin item: due date is checked, but malformed amount input becomes null silently; title can be saved as “Untitled”.
- Offer rules: blank values default, but non-numeric or out-of-range values are not rejected by the editor before saving.
- Road report: empty description becomes “Driver observation”.

The UI should distinguish invalid input from intentionally blank/unknown values. Silent coercion is especially problematic for driver economics.

## Runtime and crash-risk review

These are source-level risk candidates, not confirmed explanations for the user's IllegalStateException.

| ID | Severity | Finding | Why it matters | Evidence / status |
|---|---|---|---|---|
| RT-01 | Critical, unresolved | User-reported generic action failures and IllegalStateException still have no device reproduction/stack trace | Root cause cannot responsibly be assigned from exception class or toast | Open defect BB-001/BB-002; DIAG report not yet inspected |
| RT-02 | High, candidate | Several network workers post UI work through `runOnUiThread`; not every callback checks `isFinishing/isDestroyed` before changing views or showing dialogs | Activity can be paused/destroyed while network work is pending; stale UI callbacks/dialog windows may fail or update detached UI | `planRoute()`, `refreshRoadIntelligence()`, reroute callback; source review only |
| RT-03 | High, candidate | MapLibre style readiness is now tracked and annotation entry points include readiness guards | This reduces one asynchronous style-loading hazard, but current tests only test the boolean state helper, not MapLibre annotation behavior or Activity lifecycle | `MapStyleState.kt`, `MapStyleStateTest.kt`; device validation absent |
| RT-04 | High, candidate | `getMapAsync` and style-loaded callback retain Activity/view references; the callback itself has no early destroyed-Activity guard | A delayed callback after Activity teardown could interact with stale views or location requests | `MainActivity.onCreate()`; not reproduced |
| RT-05 | Medium | `safeUiAction()` catches `Exception` for synchronous UI callbacks only | It cannot catch asynchronous worker exceptions, native renderer crashes, process kills, or all fatal JVM errors; the user-facing toast is not a universal crash shield | `safeUiAction()`, `CrashDiagnostics.kt` |
| RT-06 | Medium | Location callbacks and navigation state are Activity-owned; location updates are removed in `onPause()` | Navigation pauses when app leaves foreground; lifecycle interruptions may also reset the driver's expectation of active guidance | `onPause()`, `onResume()`; foreground-only is a known limitation |
| RT-07 | Medium | Route search and external feed operations rely on public endpoints | Timeouts, rate limits, malformed responses and outages can prevent routes/road/weather data; source errors are not equivalent to app crashes | `RoutePlanner.kt`, Overpass and Open-Meteo calls |
| RT-08 | Medium | The map-style readiness flag is monotonic and does not represent style reload/failure state | If style is ever replaced/reloaded or fails, the boolean may not accurately describe current renderer readiness | Current source loads one style; risk candidate, not a demonstrated defect |

### Map-style patch verification

At the audited commit:
- `MapStyleState.kt` exists.
- The style callback calls `markReady()` after the MapLibre style load callback.
- `displayRoute()`, `updateDriverLocation()`, `refreshRoadIntelligence()` and `renderRoadEvents()` guard against a not-ready style; lifecycle guards exist on several paths.
- `MapStyleStateTest.kt` tests initial false state and idempotent transition to true.

This verifies source presence only. It does **not** establish that the user's crash is fixed, nor does the helper test exercise MapLibre, delayed callbacks, tab switching or physical-device behavior.

## Product control requirements before any new feature work

Keep this as a documented backlog, not authorization to implement while STOP-WORK is active.

1. **Universal record actions:** view, edit, save, cancel, delete/archive, undo where safe; explicit confirmation for destructive operations.
2. **Driver admin editor:** edit every saved field; replace/remove attachments; mark complete/undo; edit recurrence and next due date.
3. **Offer editor:** correct all captured fields; preserve raw notification evidence; distinguish pending, skipped and manually dismissed.
4. **Trip correction ledger:** preserve original raw import evidence; add user correction/annotation/exclusion records with timestamps and reason; never silently overwrite source facts.
5. **Import review:** preview file, platform/schema, row counts, duplicates, invalid rows and missing fields; allow cancel before commit; provide rollback of a specific import without deleting unrelated evidence.
6. **Report controls:** edit, withdraw/delete and expire local road reports; clearly label private versus shared if sharing is ever introduced.
7. **Data ownership:** export user records and evidence in a documented format; selective/full delete; backup/restore; explain what remains on-device.
8. **Consistent validation:** reject invalid numbers/ranges; distinguish unknown from zero; show field-level errors and preserve user-entered values when validation fails.
9. **Lifecycle-safe asynchronous work:** cancel or invalidate callbacks on Activity teardown; guard all UI posts/dialogs; test permission changes, process recreation, offline and delayed responses.
10. **Observable action outcomes:** every write reports success/failure; no silent fallback or generic error that hides which field or operation failed.

## Recommended order

1. Resolve BB-001/BB-002 from actual DIAG/logcat evidence; do not implement CRUD while the app's basic reliability is unverified.
2. Complete the device launch/tab/lifecycle smoke matrix.
3. Verify map and route correctness on the target phone.
4. Reconcile current import/economics behavior against a real user-owned export.
5. Only after stability acceptance, prioritize correction/deletion/undo and data export as the first product-completeness slice.
6. Redesign the visual system and improve map realism only after the stable baseline is accepted.

## Scope and confidence

- Audited: `MainActivity.kt`, `DriverCommandStore.kt`, `DriverMirrorStore.kt`, `VehicleProfile.kt`, `MapStyleState.kt`, `CrashDiagnostics.kt`, `RoutePlanner.kt`, manifest and relevant tests at the stated commit.
- Not audited: physical-device behavior, every external provider response, screenshots or a user session replay.
- No code, schema, UI or PR merge was performed by this audit.
- STOP-WORK remains active. All runtime risks remain unconfirmed until reproduced on the target device.
