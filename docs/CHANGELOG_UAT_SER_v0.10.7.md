# SGI: Comando — SER v0.10.7 UAT

## Corrección de build

SER v0.10.6 contenía un error de estructura JSX en `PatrolConfig.tsx`: al reemplazar el bloque de navegación de Patrullas se eliminó accidentalmente la apertura del fragmento condicional `patrolDraft ? <>`, dejando el cierre `</> : ...` sin su par. TypeScript/Vite detenían el build del frontend.

SER v0.10.7 restaura correctamente ese fragmento y valida sintácticamente todos los archivos TS/TSX del frontend.

## Patrullas — alcance preservado
- `Protocolos del Punto`.
- Columnas: Código / Protocolo / Patrullas / Estado.
- Banda `Patrullas (x/15)`.
- Máximo 15 Patrullas por Protocolo, validado en backend.
- Navegación numerada: Definición / Patrulla / Hitos / Reglas por hito / Evidencias / Trazabilidad.
- Seleccionar o crear Patrulla abre la pestaña Patrulla.

## UAT launcher
`uat-start.ps1` ahora captura el código de salida de `docker compose up` antes de ejecutar `docker compose ps`, evitando reportar falsamente `exit code 0` después de un fallo de build.

## Base de datos
- Sin nuevas migraciones Flyway.
- V22/V23 y migraciones existentes permanecen sin modificación.
