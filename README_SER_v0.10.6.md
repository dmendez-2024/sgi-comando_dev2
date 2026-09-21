# SER v0.10.6 — Patrullas homologadas al estándar Bitácora

Baseline: SER v0.10.5.

Incluye la homologación de la pantalla de **Patrullas** al estándar UX/UI de **Bitácora**:
- `Protocolos del Punto` con estructura Código / Protocolo / Patrullas / Estado.
- Banda de selección `Patrullas (x/15)`.
- Steps numerados con círculo: Definición, Patrulla, Hitos, Reglas por hito, Evidencias y Trazabilidad.
- Pestaña inicial enfocada en `Patrulla` al seleccionar o crear una patrulla.
- Validación backend: máximo **15 Patrullas** por Protocolo.

No modifica migraciones Flyway existentes.
