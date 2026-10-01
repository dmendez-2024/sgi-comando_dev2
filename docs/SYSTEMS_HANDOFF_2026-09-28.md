# SGI: Comando — Entrega a SISTEMAS — baseline FROZEN

**Freeze funcional/UAT:** 2026-09-27  
**Entrega prevista a SISTEMAS:** 2026-09-28  
**Estado del paquete:** **CLOSED / FROZEN**  
**Norma de interconexiones:** SITC-NOM-001 v4.1

## 1. Baseline canónica congelada

| Vertical | Versión | Estado |
|---|---:|---|
| TER — Territorio | v1.0 | FROZEN |
| COM — Compañías | v1.1.3 | FROZEN |
| SER — Servicios | v0.10.10 | FROZEN |
| ASI — Asignaciones | v0.7.4 | FROZEN |
| COO — Coordinación | v0.1 | FROZEN |
| BIT — Bitácora global | v0.1 | FROZEN |
| CNS — Consignas operativas | v0.1.2 | FROZEN |
| NOV — Novedades | v0.1 | FROZEN |
| CSL — Consola | v0.2.5 | FROZEN |
| NEX — Nexus | v0.1 | FROZEN |

**Regla de cambio:** ninguna de estas versiones debe modificarse in-place. Cualquier cambio posterior crea una nueva versión y preserva esta entrega.

## 2. Últimos cambios incluidos

### CSL v0.2.5
- Notificación de Incidentes en Consola.
- Taxonomía fuente: 3 categorías / 20 subcategorías / 90 incidentes.
- Flujo de Inasistencia programada/efectiva y Cobertura/Reasignación.
- Candidato muestra nombre, teléfono y `Francos Trabajados: N (últ. 6 meses)`.
- Asset de Incidentes corregido.
- Teléfono, francos, disponibilidad, coordenadas y parte de la lógica de Incidentes siguen siendo DEMO/local en esta UAT.

### NEX v0.1
- Ubicación: `Operaciones > Servicios > Configuración del Punto > Checklist de configuración`.
- Nexus queda después de **Consignas** y antes de **Recursos Humanos**.
- Dos vistas: **Reglas** e **Historial**.
- Modelo de regla: Evento objetivo (`Objeto + Evento/Estado`) + relación `ANTES / DURANTE / DESPUÉS` + Evento de referencia (`Objeto + Evento/Estado`) + ventana/correlación + acciones opcionales.
- Reglas UAT persisten en `localStorage` por Punto.
- **No existe todavía runtime EVC/BPM productivo, persistencia backend de Nexus ni contratos productivos de eventos.** El freeze valida el modelo funcional/UI, no declara terminada esa implementación productiva.

## 3. Arquitectura / integración congelada

- `CORE` = `UNIVERSAL`.
- `SGI_COM` = `PE_SPECIFIC`.
- P0/P1 técnico del 27-09 está integrado.
- `sitc/SGI_Comando_CURRENT.sitcpack` es el CURRENT entregado.
- No se transporta `.env`; crear desde `.env.example` por ambiente.
- No introducir clientes HTTP ad-hoc: usar la capa genérica de interconexiones documentada.

## 4. Build fixes acumulados incluidos

- Frontend fija TypeScript `5.9.3`.
- Taxonomía de Incidentes tipada para `strict` TypeScript.
- Backend RRHH usa `WebApplicationException(..., Response.Status.CONFLICT)` para HTTP 409; no existe dependencia inválida a `jakarta.ws.rs.ConflictException`.
- `uat-start.ps1` valida secretos reales y no debe bloquear por `CHANGE_ME` dentro de comentarios.

## 5. Flyway / base de datos — no improvisar

- No editar migraciones históricas para resolver checksums.
- V22 aplicada históricamente tiene checksum de referencia **1953092034**.
- V30/V31 destructivas de UAT fueron retiradas del camino automático; revisar `docs/MIGRATION_SAFETY_V30_V31.md`.
- Para una base existente, respaldar y clasificar antes de cualquier reconciliación.

## 6. Arranque UAT

Desde la carpeta que contiene `repo/`:

```powershell
Copy-Item ".\repo\.env.example" ".\repo\.env"
```

Configurar todos los secretos reales/UAT del `.env` y luego:

```powershell
powershell -ExecutionPolicy Bypass -File .\repo\scripts\uat-start.ps1
```

```powershell
powershell -ExecutionPolicy Bypass -File .\repo\scripts\uat-open.ps1
```

Estado:

```powershell
powershell -ExecutionPolicy Bypass -File .\repo\scripts\uat-status.ps1
```

Detener:

```powershell
powershell -ExecutionPolicy Bypass -File .\repo\scripts\uat-stop.ps1
```

Frontend esperado: `http://localhost:5173`  
Backend esperado: `http://localhost:8080`

## 7. Gate técnico para SISTEMAS

Antes de promover fuera de UAT, SISTEMAS debe ejecutar y registrar:

1. build frontend completo;
2. Maven package/tests según pipeline;
3. Docker Compose end-to-end;
4. Flyway sobre base limpia y clasificación de bases existentes;
5. pruebas de interconexiones v4.1;
6. seguridad/secretos/credenciales;
7. regresión funcional de verticales congeladas;
8. para NEX, diseño productivo separado de runtime, persistencia y contratos antes de activarlo como BPM real.

## 8. Documentos de lectura prioritaria

1. `docs/SYSTEMS_HANDOFF_2026-09-28.md`
2. `docs/00_HANDOFF.md`
3. `FINAL_RELEASE_MANIFEST_2026-09-27.json`
4. `docs/VERSION_MATRIX.md`
5. `docs/MIGRATION_SAFETY_V30_V31.md`
6. `docs/INTERCONNECTIONS.md`
7. `docs/SITCPACK.md`
8. `README_CSL_v0.2.5.md`
9. `README_NEX_v0.1.md`
10. `docs/NEXT_ACTIONS.md`

## 9. Límite del freeze

`FROZEN` significa que esta es la baseline aceptada para handoff y no debe alterarse. No significa que los componentes expresamente DEMO/local de CSL o NEX hayan sido convertidos mágicamente en servicios productivos. Esos pendientes están documentados y deben implementarse en versiones posteriores.
