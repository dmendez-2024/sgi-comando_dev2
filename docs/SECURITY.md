# SGI: Comando — Seguridad y reproducibilidad P0/P1

## Adjuntos de incidentes — CSL IMG RC1

Reutilizan autenticación y roles del formulario y el ámbito por instancia/Compañía. Borradores sin Compañía: solo creador. Carga, listado y bytes verifican el incidente; el navegador no define tenant, claves ni vínculos ajenos. Tipos por bytes, 5 MiB, cinco fotos/sección y UUID+hash para reintentos. Filas del incidente bloqueadas para serializar escritura. No hay acceso público a MinIO; contenido privado no-store/nosniff. Actor, hash y tiempos en evidencia canónica; retirada de selección conserva objeto y metadata. [Pruebas y límites](CSL_INCIDENT_IMAGES_2026-10-16.md).

**Norma:** SITC-NOM-001 v4.1  
**Fecha:** 2026-09-27

## Secretos

- `.env` está excluido por `.gitignore` y no forma parte del entregable.
- `.env.example` contiene únicamente nombres de variables y `CHANGE_ME`.
- `application.properties` ya no contiene passwords por defecto para PostgreSQL/MinIO.
- Docker Compose exige `POSTGRES_PASSWORD` y `MINIO_ROOT_PASSWORD`.
- SIC:COM y SIC:RRHH inbound usan `credential_ref`; el secreto se resuelve mediante `CredentialRefResolver`.
- Producción debe sustituir el resolver de entorno por Vault/KMS conservando el contrato.
- Los `.sitcpack` contienen referencias de credencial, nunca secretos.

## Integraciones

- La lógica de negocio no define host/IP/puerto/Authorization físico.
- `CORE` resuelve ECOSYSTEM_INTERCONNECTION por Instancia PE + ambiente.
- `CORE` no es proxy obligatorio del tráfico funcional.
- IDs v4.1 están fijados en `InterconnectionIds`; aliases v3 existen solo para transición.
- El catálogo runtime es allowlist: una integración desconocida no se ejecuta.

## Reproducibilidad frontend

Las dependencias directas quedan fijadas a versiones exactas en `frontend/package.json`:

- Vite `8.3.0`
- `@vitejs/plugin-react` `6.1.1`
- TypeScript `6.0.0`
- React / ReactDOM `19.2.0`
- lucide-react `1.47.0`
- tipos React `19.3.0`

La RC no incluye `node_modules`. Sistemas debe generar y versionar `package-lock.json` desde un entorno con acceso al registry antes del gate de producción; esta sesión no tuvo acceso npm desde el contenedor para generarlo de forma verificable.

## Flyway

V30/V31 destructivos quedaron en cuarentena. Ver `docs/MIGRATION_SAFETY_V30_V31.md`.

## UI / EVC

No se alteró intencionalmente la UI y no se implementó EVC/Eventos de Cumplimiento.
