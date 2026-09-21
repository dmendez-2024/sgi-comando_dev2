# SGI: Comando — ASI / Asignaciones

**Versión:** v0.5  
**Estado:** CANDIDATO UAT  
**Fecha:** 2026-09-09  
**Base:** ASI v0.4 sobre COM v1.0 FROZEN + TER v1.0 FROZEN  
**Especificación de implementación:** `SGI_Comando_Asignaciones_Especificacion_UX_v1.0`  
**Scope:** ASI y contratos de lectura requeridos por ASI. TER v1.0 y COM v1.0 permanecen congelados.

## Objetivo

Convertir Asignaciones de una matriz semanal con interacción de tarjetas a un **spreadsheet operacional**: rápido para selección, copiar/pegar, repetir patrones y navegar fechas, pero sujeto a las reglas de negocio de SGI.

## Cambios principales

1. **Una celda = un Turno completo.** Un turno 18:00–06:00 vive en el día de inicio y muestra `+1`; ya no se divide en dos fragmentos visuales.
2. **Timeline continuo.** La pantalla precarga cinco semanas (35 días) y permite scroll horizontal; el selector semanal posiciona el viewport, no limita el modelo de interacción.
3. **Pasado read-only.** Fechas anteriores al día operacional actual permanecen visibles pero no admiten edición masiva ni Drag & Drop.
4. **Selección tipo hoja de cálculo.** Click, Shift, Ctrl/Meta y click-arrastre seleccionan celdas sin modo separado de “seleccionar”.
5. **Ctrl+C / Ctrl+V / Delete / Ctrl+Z / Ctrl+Y.** Se incorpora clipboard interno 2D, pegado TSV desde Excel, vaciado y Undo/Redo lógico.
6. **Fill handle.** El patrón seleccionado se puede repetir literalmente mediante el handle de la esquina inferior derecha.
7. **Copiar Día / Ciclo / Selección.** Son unidades explícitas de copia.
8. **Ciclo no equivale a semana.** La longitud del ciclo se consume desde SIC: COM; UAT contiene referencias 6-2 = 8 días y 5-2 = 7 días.
9. **Validación cross-week.** Solapamiento y auto-relevo se validan contra asignaciones operacionales del colaborador, no únicamente dentro del `AssignmentPlan` semanal.
10. **Personal disponible compacto y colapsable.** Se reduce el consumo horizontal del panel lateral.


## Ajustes UX v0.5

1. **Resumen semanal:** orden fijo ID promedio → IC promedio → turnos asignados → turnos sin asignar → porcentaje de turnos asignados.
2. **Preview Drag & Drop:** verde si cumple ID del TIER y compatibilidad 100%; amarillo cuando existe brecha de ID/compatibilidad sin bloqueo; rojo ante indisponibilidad, solapamiento, auto-relevo u otro blocker.
3. **Tarjeta compacta:** elimina fotografía, dial/círculo de IC y auditoría visible; muestra nombre, ID y **CP (Compatibilidad del Puesto)**.
4. **Detalle al click:** click y soltar sobre la tarjeta abre una ventana con resumen de persona, evolución del ID, Compatibilidad del Puesto e historial de autoría de esa asignación puntual.

## Contrato de ciclo

ASI no se convierte en SoR de Formato o Rotación. Para UAT v0.4 se agrega `post_planning_cycle_snapshot`, un snapshot de lectura del contrato recibido desde SIC: COM:

- `post_id`
- `rotation_code`
- `cycle_length_days`
- `source_system`
- `source_version`

La UI **no** puede derivar `cycle_length_days` suponiendo siete días.

## Invariantes congelados

- Los Turnos visibles son exactamente los definidos por SIC: COM para cada Puesto; no existe supuesto de 2 turnos.
- TER v1.0 FROZEN y COM v1.0 FROZEN no reciben cambios funcionales.
- ASI mantiene planificación y auditoría; no se convierte en SoR de Cliente, Personal, geografía, Formato o Rotación.


### Contrato de ingreso de personal desde SIC: RRHH — 2026-09-19
SIC: RRHH debe entregar cada colaborador visible en SGI con adscripción explícita a **Seguridad Física (SF) + Compañía**. SIC: RRHH es SoR de esa relación inicial. SGI no crea ni infiere la membresía laboral; las transferencias operacionales posteriores se orquestan en ASI y se sincronizan de vuelta a SIC: RRHH.
