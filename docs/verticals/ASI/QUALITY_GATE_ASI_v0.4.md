# Quality Gate — ASI v0.4

- Raíz única `repo/`: PASS (verificada en empaquetado final).
- Scope funcional ASI; TER v1.0 y COM v1.0 congelados: PASS por inspección de diff.
- `Assignments.tsx` parse/transpile TypeScript 5.8: PASS.
- `Sidebar.tsx` parse/transpile TypeScript 5.8: PASS.
- JSON / SITC: PASS.
- Migración DB nueva: `V9__asi_spreadsheet_cycles.sql` PRESENTE.
- Backend ASI: CAMBIADO (`PostDto` ciclo + validación cross-week).
- Build npm/Vite completo: PENDIENTE UAT LOCAL; el entorno de empaquetado no dispone de dependencias npm y la instalación excedió la ventana disponible.
- Build Maven/Docker Compose: PENDIENTE UAT LOCAL; Maven/Docker no están disponibles en este runtime.

No se declara BUILD VALIDADO hasta que la UAT local compile y levante el paquete.
