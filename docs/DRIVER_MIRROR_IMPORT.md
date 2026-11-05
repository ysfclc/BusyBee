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

Known net is gross minus recorded fees plus recorded bonuses, before vehicle costs, taxes and missing adjustments. It is not labelled profit or take-home pay. Real provider-format adapters and statement reconciliation are the next validation step.
