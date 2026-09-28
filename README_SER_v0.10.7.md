# SER v0.10.7 — Patrullas UX/UI build corregido

Baseline: SER v0.10.6.

Hotfix de entrega sobre la homologación de Patrullas al estándar de Bitácora.

- Corrige JSX mal cerrado en `PatrolConfig.tsx` que impedía `npm run build`.
- Conserva `Protocolos del Punto`, columnas Código / Protocolo / Patrullas / Estado.
- Conserva banda `Patrullas (x/15)` y límite backend de 15 Patrullas por Protocolo.
- Conserva steps numerados: Definición, Patrulla, Hitos, Reglas por hito, Evidencias, Trazabilidad.
- Corrige `uat-start.ps1` para preservar el código real de salida de Docker Compose y mostrar diagnóstico coherente.
- No modifica migraciones Flyway.
