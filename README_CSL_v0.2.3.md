# SGI: Comando — CSL v0.2.3 UAT

Hotfix técnico acumulativo sobre CSL v0.2.2.

## Corrección

El build real de Docker/TypeScript de CSL v0.2.2 expuso dos errores estrictos en `IncidentNotificationPanel.tsx` (TS7053 y TS7006) al indexar dinámicamente la taxonomía de Incidentes. La causa estaba en que `satisfies` preservaba las claves literales internas y no exponía una firma de índice `string`.

CSL v0.2.3 tipa `INCIDENT_TAXONOMY` explícitamente como `Record<IncidentCategory, Record<string, string[]>>`. Con ello, `INCIDENT_TAXONOMY[category][subcategory]` devuelve `string[]` y el parámetro de `incidentTypes.map(...)` queda tipado como `string`.

No hay cambios funcionales, visuales, backend, base de datos, Flyway, interconexiones ni SITC. Se conserva TypeScript 5.9.3 del hotfix anterior.
