# Validación de datos UAT — Patrullas para presentación

## Alcance

- Punto: `PTO-001`
- Puesto: `GGTT01`
- Protocolo vigente: `PRO-PAT-0004 · Protocolo Integral de Prevención y Control`

## Resultado

- Protocolos activos en el puesto: `1`.
- Patrullas activas en el protocolo: `3`.
- Hitos por patrulla: `3`.
- Reglas por Hito: `3` (`INSPECCION_VISUAL`, `FOTOGRAFIA`, `CONFIRMACION`).
- El protocolo anterior `PRO-PAT-0001` quedó `INACTIVO`; los borradores se conservaron para mantener trazabilidad.

## Ejecución

La carga se aplicó mediante un fixture SQL versionado, dentro de una única transacción. El primer intento falló por una restricción obligatoria y fue revertido íntegramente; luego se corrigió el fixture y la ejecución terminó en `COMMIT`.
