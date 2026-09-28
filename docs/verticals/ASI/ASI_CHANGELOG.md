# ASI — Changelog

## Extensión de identidad recibida de RRHH — V29 — 2026-09-24

- La capa operacional conserva `employee_id` UUID estable y agrega `persona_id` nullable como referencia numérica de DHO/SIC:RRHH.
- Se documentó en `ASI_INTEGRATIONS.md` y `ASI_UAT.md`; el backfill histórico no se realiza automáticamente.
- Estado citado por la nota DME: SGI implementado/verificado; redespliegue local DHO y UAT integrada pendientes.
- CR/aprobación formal y compatibilidad del contrato bajo v4.1 aún deben localizarse; esta entrada no declara una nueva baseline congelada.

## v0.5 — 2026-09-09
- Resumen semanal reordenado: ID promedio, IC promedio, turnos asignados, turnos sin asignar, porcentaje de turnos asignados.
- Etiquetas `sin asignar` → `turnos sin asignar` y `cobertura` → `de turnos asignados`.
- Preview de Drag & Drop conserva semáforo GREEN/AMBER/RED y elimina el borde azul neutral como estado de elegibilidad.
- Preview del colaborador se precarga desde el inicio del gesto de drag; RED se bloquea en frontend además de la validación backend.
- Tarjetas de asignación compactas sin fotografía, círculo IC ni auditoría inline.
- Compatibilidad visible en tarjeta renombrada a CP (Compatibilidad del Puesto).
- Click y soltar sobre tarjeta abre detalle con persona, evolución ID, CP e historial de autoría.
- Sin cambios backend, DB, TER o COM.

## v0.4 — 2026-09-09
- Rediseño de Asignaciones como spreadsheet operacional.
- Turnos que cruzan medianoche se representan en una sola celda del día de inicio con indicador `+1`.
- Timeline horizontal de 35 días con navegación semanal como posicionamiento.
- Fechas pasadas visibles en modo read-only.
- Selección directa rectangular con Click / Shift / Ctrl-Meta / arrastre.
- Clipboard 2D interno; Ctrl+C/V; pegado TSV desde Excel por nombre completo o ID de colaborador.
- Delete/Backspace para vaciar; Ctrl+Z/Y para Undo/Redo de operaciones masivas en borrador.
- Fill handle para repetir literalmente patrones multi-celda.
- Copiar Día, Copiar Ciclo y Copiar Selección.
- Copiar Día/Ciclo preserva también celdas vacías del patrón, necesarias para descansos.
- Longitud del ciclo consumida desde snapshot de SIC: COM; UAT: 6-2 = 8 días, 5-2 = 7 días.
- Validación de overlap/auto-relevo ampliada más allá del límite de la semana calendario.
- Personal disponible convertido a lista compacta y colapsable.
- Sidebar actualizado a ASI v0.4 UAT.
- Migración DB V9 para snapshot de ciclo operacional.
- Sin cambios funcionales a TER v1.0 FROZEN ni COM v1.0 FROZEN.

## v0.3 — 2026-09-09
- Preview visual previo al drop sobre turno lógico.
- Resumen semanal superior sensible a filtros.
- Ledger TER/COM/ASI en Sidebar.

## v0.2 — 2026-09-09
- Recuperado Drag & Drop desde Personal disponible.
- Separado click de ficha vs. arrastre.

## v0.1 — 2026-09-09
- Semáforo de elegibilidad, filtros y controles de acceso iniciales.
