# Performance Audit — SGI: Comando PERF v0.1

## Finding
The dominant scalability risk for 2,000+ guards was `Asignaciones`, not the visual layer of the other frozen verticals.

The original personnel endpoint loaded the whole company population into Java before applying pagination. It also aggregated assignment hours beyond the visible page. Selecting one employee for compatibility evaluation could generate database work repeatedly for every shift in the plan.

## Remediation

1. Database-side pagination and search for personnel.
2. Server-side availability filtering.
3. Page-scoped enrichment for unavailability, transfers and assigned hours.
4. Batch compatibility evaluation instead of per-shift queries.
5. Time-window restriction for overlap/auto-relevo checks.
6. Frontend indexing for hot repeated lookups.
7. Incremental personnel loading and lazy images.
8. Local state patches after normal drag/drop edits instead of full reloads.
9. Performance indexes in Flyway V26.

## Complexity change

### Personnel browse
Before: application work and memory grew with the complete company personnel population even when only the first page was visible.

After: ordinary browse work is bounded by a maximum page size of 100 plus small supporting lookups.

### Employee evaluation
Before: several database queries could execute per required shift.

After: supporting datasets are batch-loaded once per plan/employee evaluation and shift states are computed in memory.

## Other verticals
A static review of frontend pages and backend resources was performed. No other current screen has the same direct dependency on a 2,000-person population. Business/UI code outside the assignment performance path was intentionally left unchanged to protect frozen baselines.

## Production validation
Use a production-like database containing at least 2,000 employees in a company and representative shifts/assignments. Run `ANALYZE` after applying V26, then verify endpoint latency, query plans, JVM heap and browser frame responsiveness under concurrent users.
