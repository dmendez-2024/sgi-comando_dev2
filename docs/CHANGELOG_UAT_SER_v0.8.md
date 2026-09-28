# SGI: Comando — SER v0.8 UAT

## Vertical implementada
**Servicios → Configuración → Consignas**.

## Modelo funcional
`Punto → Protocolo de Consignas → Consigna → Alcance → Reglas/Evidencias`.

### Protocolos
- Protocolos independientes de Bitácora y Patrullas.
- Un solo Protocolo vigente de Consignas por Punto.
- Estados: Borrador, Publicado, Vigente y No vigente/histórico.
- Publicado = contenido inmutable.
- Editar un Protocolo publicado crea una nueva versión Borrador por copia completa.
- Un Protocolo Publicado puede quedar listo sin estar vigente y activarse posteriormente.

### Consignas
- Código, Título, Instrucción y Prioridad.
- Alcance: **Todo el Punto** o **uno o varios Puestos**.
- Vigencia: Permanente / Temporal.
- Aplicación: Todo el tiempo / Calendario.
- Reglas combinables: Acuse, Confirmación, Evidencia, GPS de ejecución y Observación.
- Ubicación esperada opcional: Plano ATS o coordenadas.

### Evidencias
- 0..n Evidencias por Consigna.
- Foto estándar real JPG/PNG/WebP, máx. 5 MB.
- Notas del estándar y versión del activo.
- Preparación explícita para futura integración VISINT.

## Base de datos
Flyway `V17__ser_consignments_protocols.sql` evoluciona la tabla canónica `consignment` y agrega únicamente las entidades necesarias:
- `consignment_protocol`
- `consignment_post_scope`
- `consignment_evidence`

No se crea una tabla paralela de Consignas.

## Compatibilidad
Se conserva `GET /api/consignments?pointId=...` para consumidores anteriores. La nueva configuración utiliza `/api/consignments/protocols`.

## Pendiente controlado
La regla de alcance `Todo el Punto / 1..n Puestos` queda aprobada también para Bitácora y Patrullas, pero SER v0.8 no modifica esas dos UAT existentes; el retrofit se hará en una corrida dedicada.
