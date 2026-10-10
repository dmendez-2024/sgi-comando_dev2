# SGI: Comando — Login con IDENT (2026-10-07)

Rama base: `Ambiente_dev` (local, sin commit). Brief IDENT S40-S41; SITC-NOM-001 v4.1 §2/§10 (direcciones por CORE). Requiere aprobación del equipo de SGI: Comando.

## Cambios
| ID | Descripción |
|----|-------------|
| SGI-IDENT-001 | Backend valida el token de IDENT (RS256, iss, aud=`SGI_COM`, exp) con Java estándar, sin dependencias nuevas (`security/IdentTokenVerifier`, `IdentAuthenticationMechanism`, `IdentIdentityProvider`). La dirección de IDENT se resuelve en CORE (`SGI_COM_IDENT_0001_v001`); hosts permitidos `sgi.ident.allowed-hosts`. |
| SGI-IDENT-002 | Con `sgi.ident.enabled=true` la web solo entra con IDENT; usuario y contraseña (`app_user`) quedan solo para SGI Operador en `/api/v1/operator/**` (`OperatorBasicOnlyFilter`). El Rol sale del token; el nombre de la persona es su correo (auditoría "quién lo hizo"). |
| SGI-IDENT-003 | Alcance territorial por `persona_id` (DHO) para personas de IDENT: `V68__user_operational_scope_persona_id.sql` (antes V60; renumerada el 2026-10-08 porque el equipo SGI agregó otra V60) + `UserOperationalScope.of(...)`. Operador web: empleado por `employee_operational_snapshot.persona_id`. |
| SGI-IDENT-004 | Publicación de los 11 Roles (`rbac/roles.json`) en IDENT por `SGI_COM_IDENT_0002_v001` con la clave `SGI_COM_PUBLISH` de OpenBao (AppRole propio): `ident/IdentRolePublisher`, `ident/OpenBaoSecretReader`. |
| SGI-IDENT-005 | `GET /api/v1/auth/ident-config` (público, sin secretos). |
| SGI-IDENT-006 | Frontend: login IDENT (PKCE, token en memoria, `security/identAuth.ts`); el selector "Usuario UAT" solo aparece si IDENT está apagado; encabezado con correo, Rol, "← Plataformas" y "Cerrar sesión"; `getUser()` devuelve el perfil según el Rol de IDENT. |

## Pendientes / avisos
- `persona_id` en el token de IDENT (lo agrega la vertical RRHH): sin él los Roles con alcance ven vacío.
- **V55 duplicado en `Ambiente_dev`** (`V55__operator_logbook_records.sql` y `V55__relief_station_visint.sql`): Flyway no arranca. En local se renombró el primero a V59 solo para levantar; **no subir ese cambio**, lo corrige el equipo.
- Las claves de servicio siguen en `.env` (SITC-NOM-001 §13.2 pide credential_ref + almacén seguro).
- Local con IDENT: `IDENT_HANDOFF…/repo/scripts/uat/sgi-local-con-ident.sh`.

## 2026-10-08 — El PE sale del token de IDENT (sin ID ni nemotécnico configurado)
- `security/InstanceCountrySource.java` (nuevo): PE del token (`active_instance_country_id`); sin persona, la Instancia PE donde CORE tiene ACTIVA `SGI_COM_IDENT_0001`.
- `IdentTokenVerifier`, `IdentIdentity`, `IdentIdentityProvider`: leen el PE del token y rechazan (401) si no es una Instancia PE de SGI_COM. IDENT y la publicación de Roles se resuelven con la Instancia PE que indica CORE.
- `CoreCatalogService`, `CoreCatalogAdapter`, `VisintEndpoint`: usan ese PE (antes `EC` / `ECU-CM` / ID fijo).
- Se quitan de `application.properties`: `sgi.core.country-code`, `sgi.core.instance-country-code`, `sgi.core.country-id`, `sgi.interconnections.instance-country-id`; del `.env`: `SGI_INTERCONNECTIONS_INSTANCE_COUNTRY_ID`.
- Pruebas: `VisintEndpointTest` (7), `CoreCatalogServiceTest` (3), `IdentEmployeeQueryResourceContractTest` (1): OK.
- Migración del `persona_id` renumerada a **V68** (había dos V60). ⚠️ Pendiente del equipo SGI: `V63`–`V67` son copias idénticas de `V55`–`V59` y no son repetibles; en una base que ya tiene V55–V59 Flyway falla en V64. Deben quitar una de las dos series. En una base que ya aplicó la V60 antigua (persona_id), borrar esa fila de `flyway_schema_history`: la V68 es repetible (`IF NOT EXISTS`).
