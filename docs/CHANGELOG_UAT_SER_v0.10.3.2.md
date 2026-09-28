# SGI: Comando — SER v0.10.3.2 UAT launcher hotfix

This patch does not change the approved Bitácora functional scope introduced in SER v0.10.3.1.

## UAT launcher corrections
- Isolates SGI: Comando under Docker Compose project `sgi-comando-uat` so unrelated `repo-*` containers do not interfere with UAT startup.
- Preserves existing UAT PostgreSQL and MinIO data by keeping the historical named volumes `repo_postgres_data` and `repo_minio_data`.
- Backend readiness now fails fast if the container exits/restarts.
- Readiness wait is capped at approximately 30 seconds and prints progress instead of appearing frozen.
- On failure, backend status and the last 160 backend log lines are printed automatically in the same terminal.
- Adds `repo/scripts/uat-status.ps1` for one-command diagnostics.
- Frontend version check remains mandatory and expects SER v0.10.3.2.
