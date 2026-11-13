# Optional Driver Mirror CSV backfill and reconciliation

**This is an optional interchange format, not the normal BusyBee workflow.** Native driver-app capture (or an approved provider API) is the primary route into the Driver Mirror. The user must not need to upload a CSV to use BusyBee or view its core capabilities.

CSV/statement import remains useful for historical backfill, source reconciliation and recovery. It must stay secondary in navigation and must never be presented as a required setup step. See [PRODUCT_CONTRACT.md](PRODUCT_CONTRACT.md).

---

BusyBee imports a documented provider-neutral CSV interchange format. This is not represented as a direct Uber or Bolt API integration. Provider exports vary; specific adapters are added after validating real sample files.

Required columns: `platform`, `record_type`.

Optional columns: `external_id`, `occurred_at`, `pickup`, `dropoff`, `distance_km`, `gross`, `fees`, `bonus`, `currency`, `status`.

Amounts accept dot or comma decimal separators. Blank amounts remain NULL, never zero. Currency defaults to PLN only when omitted. Use ISO-8601 timestamps.

### Import validation rules

- Header names are trimmed, lowercased independently of device locale, and spaces become underscores.
- Empty, duplicate-after-normalisation, and unsupported headers are rejected.
- Every non-empty data row must include non-blank `platform` and `record_type` values.
- Extra cells are rejected rather than silently discarded. Omitted trailing optional cells are treated as blank/unknown.
- Quoted commas and escaped double quotes are supported. Unclosed quoted fields are rejected.

```csv
platform,record_type,external_id,occurred_at,pickup,dropoff,distance_km,gross,fees,bonus,currency,status
Uber,trip,trip-001,2026-10-31T18:32:00+02:00,Old Town,Podgorze,8.4,42.50,8.50,0,PLN,completed
Bolt,trip,ride-002,2026-10-31T19:10:00+02:00,Kazimierz,Krowodrza,5.2,29.00,5.80,,PLN,completed
```

Each row stores its raw key/value evidence, source filename, import batch and deterministic fingerprint. Duplicate rows do not double-count in the canonical record table. Import batches record received and inserted counts. Data stays in the app-private SQLite database; no upload, scraping or account linking occurs.

### Persistence integrity checks

`DriverMirrorStoreTest` exercises the SQLite-backed store with Robolectric:

- Repeated rows in one batch and across separate imports produce one canonical record, while all three source-row evidence entries remain available.
- Import history reports received and inserted counts for each batch.
- An injected row-processing failure after a valid row verifies that the import batch, canonical record and evidence rows roll back together.

These tests are authored but have not yet been executed because GitHub-hosted Actions jobs currently fail before runner assignment. They must pass in `./gradlew --no-daemon testDebugUnitTest` before this persistence capability is marked verified.

### R-DM-005 — Trip-only headline earnings

Headline earnings are calculated from recognised trip records only: `trip`, `ride` and `completed_trip`. Offers and other activity records are counted in their own activity totals but never contribute to gross, fees, bonuses or net earnings.

Gross, fees and bonuses are partial sums of known values across eligible PLN trips. `KNOWN NET` is gross minus fees plus bonuses, before vehicle costs, taxes and other adjustments; it is shown only when at least one eligible PLN trip exists and every eligible PLN trip has explicit gross, fee and bonus values. A missing amount is unknown, not zero. The figure is not labelled profit or take-home pay.

Real provider-format adapters and statement reconciliation are the next validation step.

## Current driver analytics

The Driver Mirror screen groups recognised trip types (`trip`, `ride`, `completed_trip`) by platform and recorded start hour. It reports source coverage alongside totals.

- Platform gross, fees and bonuses are partial sums of known values; they are not presented as complete if source rows are missing fields.
- Known net before vehicle cost is calculated only for rows with gross, fees and bonus all present.
- Estimated net after vehicle cost is calculated only for rows that also have a non-negative distance, using the driver's saved PLN/km assumption.
- Non-PLN records are not mixed into PLN totals.
- Start-hour groups show trip counts, gross and complete net before vehicle cost. They are not hourly-wage estimates because trip duration and waiting time are not yet part of the imported record schema.
- Missing fee, bonus, distance or timestamp values are surfaced as data gaps. Empty values are never silently converted to zero.
- The app shows recent import batches and raw source-row previews so derived totals can be traced back to evidence.

Not yet calculated: weekday profitability, zone/corridor performance, effective hourly wage, empty/dead mileage, waiting time, tax, and fully reconciled vehicle expenses. These require additional source fields and/or a validated trip-session model.
