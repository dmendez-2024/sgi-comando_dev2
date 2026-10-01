# SGI: Comando — PERF v0.1

Technical performance hardening package built on the frozen `CSL v0.1` UI/business baseline.

## Scope

No visual redesign and no business-rule changes. The optimization focuses on the high-volume `Asignaciones` path that handles personnel pools and employee/shift compatibility evaluation.

## Main changes

### Personnel pool
- PostgreSQL now performs personnel filtering, counting, sorting and pagination.
- Frontend requests personnel in pages of 100 and incrementally loads more while scrolling.
- Availability filters are applied server-side so filtering does not require downloading the full personnel population.
- Assigned-hour aggregation is limited to the employees in the current page.
- Open-transfer company data is batch-loaded rather than queried per employee.
- Avatar images use browser lazy loading and asynchronous decoding.

### Assignment evaluation
- Employee compatibility evaluation batch-loads posts, skill requirements, employee skills, unavailability and nearby assignments.
- Removed the prior per-shift database query pattern.
- Evaluation only considers assignment rows within the relevant plan time window instead of complete assignment history.

### Frontend computation
- Shifts, rows, posts and coordinates are indexed with Maps instead of repeated nested `filter()` scans.
- Point coverage and post metrics are precomputed per render state.
- Drag/drop assignment mutations patch local state instead of re-downloading the entire week and personnel pool after every edit.
- Large personnel rows use browser render containment; appearance is unchanged.

### Database
`V26__assignment_performance_indexes.sql` adds indexes only. Existing migrations are untouched.

## Target
The implementation is designed so a 2,000+ person company does not require 2,000 employee records to be loaded, enriched, rendered or reloaded for ordinary assignment work.

Actual latency still depends on production hardware, PostgreSQL statistics, concurrency and network conditions. Use `scripts/perf-assignments.ps1` against a representative dataset before production acceptance.

## Documentation addendum — VISINT / Impulsos

This package also carries the approved cross-system documentation for `SGI_OPR → SGI_COM → VISINT → SGI_COM`, Historical note: this package originally described SGI: Comando as System of Record for Impulse rules and awards. **SUPERSEDED 2026-09-27:** CORE is SoR of versioned Impulse rules; each SGI: Comando PE applies them and is SoR of the award/ledger/balance. No UI/performance behavior is changed by this documentation addendum. See `docs/SGI_OPR_VISINT_IMPULSOS.md`.
