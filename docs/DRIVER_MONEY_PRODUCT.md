# BusyBee Driver Money

**Status:** product implementation checkpoint — 2026-11-25  
**Phase:** Driver Data Mirror / Driver Intelligence  
**Data model:** private, local-first, provider-neutral

## The job this product does

Driver Money answers four practical questions from the driver's own records:

1. How much did I earn in the selected period?
2. What remains after platform fees, bonuses and the configured vehicle-cost estimate?
3. Which platform performed better on comparable records?
4. How much of that answer is supported by complete source data?

This is a private work ledger, not a generic finance dashboard. It must show the underlying trips and source evidence behind every total.

## Primary workflow

1. **Bring records in.** The driver selects a statement/interchange CSV from device storage.
2. **Review before saving.** BusyBee shows the filename, row count, platform coverage, trip count, missing fields and currency treatment. Invalid numeric values and negative distances stop the import before any rows are written.
3. **Preserve evidence.** The original imported fields, source filename and import batch are retained locally. Duplicate canonical rows are not counted twice; their source evidence remains available.
4. **Read the period.** Today, 7 days, 30 days and All time are available. The same period selection drives the money totals, platform comparison and trip ledger.
5. **Inspect and correct the record.** Tapping a trip opens its imported values, source file, external ID and raw source fields. Corrections update the normalized record while preserving original evidence and an before/after audit history. Re-importing the same source row cannot overwrite a correction.

## Accounting and evidence rules

- PLN is the only currency used in PLN totals. Other currencies and explicitly blank currency values remain separate; no exchange rate is guessed.
- PLN gross, fees and bonuses are partial known-value sums. The data-coverage counts show how many trip rows supplied each field.
- Known net before vehicle cost is calculated per trip only when gross, fees and bonus are all present: gross - fees + bonus.
- Estimated vehicle cost is known distance multiplied by the driver's saved all-in PLN/km assumption.
- Estimated net after vehicle cost is calculated only for trips with gross, fees, bonus and non-negative distance. Incomplete trips are excluded, never filled with zero.
- The vehicle-cost estimate is not a tax calculation, take-home pay, or fully reconciled profit.
- Recorded paid expenses remain separate until they can be matched to the same trip or reporting period. They are not silently added to or subtracted from the per-kilometre estimate.
- Finite date periods exclude trips with unknown/unparseable timestamps and disclose their count. All time includes them but labels their timing as unknown.
- Provider timestamps with offsets are converted to the device's local calendar date for period selection.
- A missing currency column uses the documented interchange default of PLN. A present-but-blank currency field stays blank/unknown and is excluded from PLN totals.

## Current input boundary

The current import is the documented provider-neutral CSV interchange format in [DRIVER_MIRROR_IMPORT.md](DRIVER_MIRROR_IMPORT.md). It is not a verified Uber or Bolt export adapter and does not imply direct account/API access. Provider-specific adapters require representative, user-authorised source files and reconciliation against the original platform statement.

## Product acceptance still required

- Validate representative real Uber and/or Bolt statement exports and build source-specific adapters only where actual formats justify them.
- Reconcile imported trip counts and financial totals against the original statement, with discrepancies explained.
- Validate representative real Uber and/or Bolt statement exports and build source-specific adapters only where actual formats justify them.
- Reconcile imported trip counts and financial totals against the original statement, with discrepancies explained.
- Validate the correction workflow on-device and confirm the audit history survives restart.
- Validate the workflow on a real device using an existing local database and a signed update; confirm import, restart, duplicate re-import and evidence inspection survive.
- Expand the canonical trip/session model before claiming hourly wage, waiting-time utilisation, dead mileage, tax or fully allocated vehicle profit.

## Privacy

Imported statements and trip economics stay in the app-private local database. This product does not upload the driver's statements, earnings, routes or source evidence.
