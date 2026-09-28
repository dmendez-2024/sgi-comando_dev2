# CSL v0.2.2 — Build/Launcher Fix

Date: 2026-09-27
Status: UAT_CANDIDATE

## Scope

Technical correction only over CSL v0.2.1. No UI, incident taxonomy, backend, database, Flyway, SITC, interconnection, or EVC behavior changes.

## Corrections

1. `frontend/package.json`: `typescript` changed from nonexistent `6.0.0` in the `typescript` npm package to stable `5.9.3`. This preserves the TypeScript 5.x compiler line and is compatible with the React type definitions used by this UAT.
2. `scripts/uat-start.ps1`: `.env` placeholder validation now ignores blank/comment lines and only blocks effective assignments that remain `=CHANGE_ME`.
3. `.env.example`: explanatory comment no longer contains the placeholder token.
4. UAT frontend metadata/launcher expected version updated to `0.2.2`.

## Explicitly unchanged

- Console UI and accordion behavior.
- Incident notification UX and Excel taxonomy.
- Backend/runtime business logic.
- Database/Flyway.
- SITC/CURRENT.
- EVC: not implemented.
