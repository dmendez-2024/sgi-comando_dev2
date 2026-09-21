# ASI v0.5 — Handoff a Sistemas

## Alcance v0.5
Ajuste exclusivamente frontend/UX sobre ASI v0.4.

### Frontend
- orden y textos del Resumen semanal;
- preview Drag & Drop con borde verde / amarillo / rojo según `EvaluationDto`;
- bloqueo frontend del drop rojo, manteniendo la validación backend;
- tarjetas de asignación compactas sin foto, dial IC ni auditoría inline;
- `CP` como abreviatura visible de Compatibilidad del Puesto en la tarjeta;
- click y soltar sobre tarjeta abre detalle con persona, evolución ID, CP e historial de autoría.

### Backend
Sin cambios en v0.5. Se reutiliza el backend de ASI v0.4.

### Base de datos
Sin cambios en v0.5. Se mantiene `V9__asi_spreadsheet_cycles.sql` de ASI v0.4.

## Integración productiva pendiente heredada
La semilla UAT de V9 representa el contrato de SIC: COM. En producción, el snapshot debe alimentarse mediante el mecanismo de integración acordado con SIC: COM; ASI no debe calcular el ciclo desde la semana ni mantener manualmente la rotación.

## Protección de congelados
TER v1.0 y COM v1.0 permanecen FROZEN. No se modifican tablas, recursos o reglas funcionales de esas verticales.

## Validación antes de promoción
1. Build Docker completo.
2. Confirmar que Flyway no agrega migraciones nuevas en v0.5.
3. UAT funcional `ASI_UAT.md`.
4. Regresión ASI v0.4 + TER/COM.
5. Verificación específica de click vs. drag sobre tarjetas.
