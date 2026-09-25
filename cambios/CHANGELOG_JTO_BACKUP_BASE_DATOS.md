# CHANGELOG_JTO_BACKUP_BASE_DATOS

## Respaldo PostgreSQL

- Fecha: 2026-09-24.
- Archivo: `backups/SGI_COMANDO_20260924_171812.dump`.
- Formato: PostgreSQL custom, comprimido, sin propietarios ni privilegios.
- Base respaldada: `sgi_comando`.
- Verificación: `pg_restore --list` completada correctamente; 454 entradas detectadas.
- Integridad: checksum SHA-256 en `backups/SGI_COMANDO_20260924_171812.dump.sha256`.
- Permisos locales: `600`; el archivo puede contener datos sensibles.
- Alcance: este respaldo cubre PostgreSQL. No incluye objetos almacenados en MinIO.

## Restauración

Restaurar únicamente en una base vacía o de recuperación; `--clean` elimina objetos existentes antes de recrearlos:

```bash
cat backups/SGI_COMANDO_20260924_171812.dump | docker exec -i repo_postgres_1 pg_restore -U sgi -d sgi_comando --clean --if-exists --no-owner --no-privileges
```
