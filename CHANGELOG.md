# Changelog

## 2026-11-12 — Earnings integrity and project continuity

- Corrected headline earnings so only recognised PLN trip rows contribute; offers and other activity records no longer inflate trip gross, fees, bonuses or known net.
- Tightened headline-net completeness: every recognised PLN trip must have explicit gross, fees and bonus values.
- Added unit tests for mixed trip/offer imports, incomplete offers, missing trip amounts and non-PLN-only data.
- Added the initial project state ledger and requirement coverage matrix.
- Corrected the README checkpoint to reflect the current Map, Offers, Earnings, Driver Hub and Sources surfaces.
- CI verification is pending for this increment; see [the phase log](docs/PHASE_LOG.md).
