# CHANGELOG DME — Eventos DHO mediante CORE

Fecha: 2026-10-01

## Alcance

- Se adopta `SIC_DHO_SGI_COM_0001_v001` como identificador canónico para los eventos de activación e inactivación enviados por SIC:DHO.
- El receptor mantiene como alias de transición `SIC_RRHH_SGI_COM_0001_v001` y los identificadores históricos existentes, evitando interrumpir consumidores anteriores.
- Se conserva el endpoint `POST /api/v1/inbound/sic-rrhh/employee-events`; los eventos `ACTIVE` e `INACTIVE` continúan utilizando el mismo contrato.
- Se define por defecto la referencia técnica `SIC_RRHH_SGI_COM_0001_v001` para reutilizar la credencial ya existente sin incluir el secreto en el repositorio.
- Se actualizan el catálogo de interconexiones y el catálogo de API con el identificador registrado en CORE.

## Configuración por ambiente

- CORE mantiene un binding independiente para `LOCAL`, `DEVELOPMENT`, `UAT`, `STAGING`, `PRODUCTION` y `DISASTER_RECOVERY`.
- SIC:DHO selecciona el binding mediante `DHO_SGI_COMANDO_ENVIRONMENT`; cambiar de ambiente no requiere modificar el endpoint en código.
- El valor histórico `DEV` se normaliza a `DEVELOPMENT` para conservar compatibilidad con la configuración anterior de DHO.
- El modo directo se conserva como respaldo mediante `DHO_SGI_COMANDO_USE_CORE=false` y `DHO_SGI_COMANDO_DIRECT_URL`.

## Compatibilidad

- No se modifica el modelo de datos ni se agrega una migración de base de datos.
- No se eliminan empleados al recibir una desvinculación; el evento actualiza su estado operacional a `INACTIVE`.
