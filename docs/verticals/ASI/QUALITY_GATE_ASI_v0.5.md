# Quality Gate — ASI v0.5

- Scope funcional limitado a los cuatro ajustes del documento `CAMBIOS PARA ASI v0.5`: PASS por inspección de diff.
- TER v1.0 y COM v1.0: FROZEN; sin cambios funcionales.
- Backend ASI: SIN CAMBIOS respecto de v0.4.
- Base de datos / migraciones: SIN CAMBIOS respecto de v0.4.
- Semáforo de elegibilidad: reutiliza `EvaluationDto` de v0.4 (GREEN/AMBER/RED); rojo se bloquea también en frontend antes del POST.
- `Assignments.tsx`: parse/transpile TypeScript 5.8, 0 diagnósticos sintácticos.
- `Sidebar.tsx`: parse/transpile TypeScript 5.8, 0 diagnósticos sintácticos.
- JSON / SITC: PASS; 23 archivos parseados correctamente antes del empaquetado final.
- Build npm/Vite completo: PENDIENTE UAT LOCAL; el runtime de empaquetado no contiene `node_modules`.
- Build Maven/Docker Compose: PENDIENTE UAT LOCAL.

No se declara BUILD VALIDADO hasta que la UAT local compile y levante el paquete.
