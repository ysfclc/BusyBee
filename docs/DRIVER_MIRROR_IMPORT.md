# Driver mirror CSV import

BusyBee imports a documented provider-neutral CSV interchange format. This is not represented as a direct Uber or Bolt API integration. Provider exports vary; specific adapters are added after validating real sample files.

Required columns: `platform`, `record_type`.

Optional columns: `external_id`, `occurred_at`, `pickup`, `dropoff`, `distance_km`, `gross`, `fees`, `bonus`, `currency`, `status`.

Amounts accept dot or comma decimal separators. Blank amounts remain NULL, never zero. Currency defaults to PLN only when omitted. Use ISO-8601 timestamps.

```csv
platform,record_type,external_id,occurred_at,pickup,dropoff,distance_km,gross,fees,bonus,currency,status
Uber,trip,trip-001,2026-10-31T18:32:00+02:00,Old Town,Podgorze,8.4,42.50,8.50,0,PLN,completed
Bolt,trip,ride-002,2026-10-31T19:10:00+02:00,Kazimierz,Krowodrza,5.2,29.00,5.80,,PLN,completed
```

Each row stores its raw key/value evidence, source filename, import batch and deterministic fingerprint. Duplicate rows do not double-count in the canonical record table. Import batches record received and inserted counts. Data stays in the app-private SQLite database; no upload, scraping or account linking occurs.

The headline net is gross minus recorded fees plus recorded bonuses, before vehicle costs, taxes and other adjustments. BusyBee shows it only when every PLN record carrying gross also has explicit fee and bonus values; an empty field is unknown, not zero. It is not labelled profit or take-home pay. Real provider-format adapters and statement reconciliation are the next validation step.

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


## Review and correction workflow

Before saving, BusyBee previews the selected file's platforms, record counts, missing trip fields and currency treatment. Invalid numeric values and negative distances block the import; blank financial fields remain unknown. A present-but-blank currency field is not treated as PLN. PLN is the default only when the currency column is omitted entirely.

After import, each trip can be inspected and corrected. Corrections update only the normalized record and are stored in a before/after audit history. The original imported row, source filename and fingerprint remain unchanged. Re-importing the same source row therefore does not overwrite the driver's correction or double-count the trip.
