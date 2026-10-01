# Fase 4 — Consignas y Bitácora con fotos del agente y VISINT · Plan

**Objetivo:** llevar Consignas y Bitácora al nivel de Patrullas: 1–5 fotos estándar, foto del agente por FormData, validación VISINT, resultado al agente y vista Operación.

## Decisiones (acordadas con el usuario 2026-09-30)
- **Consignas:** cada evidencia tipo **Foto** de una consigna **vigente** del Puesto se envía **una vez por turno** (asignación). VISINT la compara con sus 1–5 fotos estándar; si "no cumple", nueva captura en el mismo turno.
- **Bitácora (registro de visitantes):** cada **registro** (`entryId` generado por la app) envía una foto por campo con evidencia. VISINT valida los campos tipo **DOCUMENTO** (Cédula, Pasaporte, Credencial) contra 1–5 fotos estándar (foto clara y del documento correcto). **Rostro** (tipo IMAGEN) solo se guarda: el reconocimiento facial está fuera de alcance. Sin límite de registros por turno; nueva captura por campo dentro del mismo registro si "no cumple".
- VISINT activo por defecto y sin casilla en pantalla (igual que Patrullas): Consigna → evidencia tipo Foto; Bitácora → campo con evidencia y tipo DOCUMENTO. Publicar exige ≥1 foto estándar en cada evidencia/campo con VISINT.

## Diseño
1. **Fotos estándar genéricas:** tabla `standard_reference_image(target_type, target_id, position 1..5, …)` para `PATROL_CHECKPOINT`, `CONSIGNMENT_EVIDENCE`, `LOGBOOK_FIELD` (V42). Se migran las de Patrullas (`patrol_checkpoint_standard_image`, que se elimina) y la foto única de Consignas/Bitácora como n.º 1. Entidad `StandardReferenceImage` + servicio compartido.
2. **Endpoints de configuración:** `POST|GET|DELETE …/standard-images[/{imageId}]` en Consignas (`/api/consignments/evidences/{id}`) y Bitácora (`/api/bitacora/fields/{id}`); se retiran los de foto única.
3. **Agente:** runtime con `consignmentTasks` y `logbookTasks`; `/evidences` acepta `CONSIGNMENT_EVIDENCE` y `LOGBOOK_FIELD`; nuevo evento `TASK_EVIDENCE_SUBMITTED {targetType, targetId, groupId?}` (grupo = asignación para consignas, `entryId` para bitácora; columna `task_execution.group_id`, índice único parcial con `capture_no`). Foto estándar para el agente: `GET /api/v1/operator/standard-images/{imageId}?assignmentId=`.
4. **VISINT:** revisión genérica por tipo de destino; `serviceType` CONSIGNA/BITACORA; `serviceId` = consigna / registro.
5. **Resultado al agente:** el `GET /api/v1/operator/executions/{eventId}` ya es genérico; se agrega `?groupId=`.
6. **Operación:** filas genéricas (Patrulla/Consigna/Bitácora) con código, nombre y grupo.
7. **Frontend:** galería de fotos estándar compartida en las 3 configuraciones (sin "Próximamente"); Simulador con tipo de tarea; Operación con columna "Tarea".
8. **Pruebas:** fotos estándar de Consignas y Bitácora, flujo agente→VISINT→resultado para ambos (incluye Rostro sin VISINT y nueva captura), Operación; suite completa y E2E con el VISINT real.
