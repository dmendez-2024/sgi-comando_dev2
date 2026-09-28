# Baseline de Implementación SGI: Comando v0.1

## 1. Arquitectura

SGI: Comando inicia como **modular monolith**. Los límites de dominio son reales en código y paquetes; el despliegue inicial es único para disminuir latencia, complejidad operativa y costo de coordinación distribuida.

Plataformas web separadas:
- SGI: Comando
- SGI: Cliente
- SGI: Monitoreo

No comparten frontend. Comparten contratos/API únicamente cuando el dominio lo requiera.

## 2. Contexto y aislamiento

Toda entidad operacional pertenece directa o indirectamente a `instance_country_id`. Este campo no se omite en UAT aunque CORE todavía sea MOCK/LOCAL.

`instance_country_id` es frontera de seguridad y de consulta. Todos los índices de alto tráfico parten de él.

## 3. Adapters externos

Puertos previstos:
- CORE
- SIC: COM
- SIC: RRHH
- SIC: RRMM
- ATS
- STC
- SMC

La primera UAT usa proveedores locales/mocks. Cambiar a HTTP/eventos no debe tocar el dominio.

## 4. Seguridad/RBAC

Autenticación UAT local. Los roles de referencia se alinean con la jerarquía Cajamarca entregada; los permisos son capacidades del producto, no simples niveles jerárquicos.

Roles mínimos de esta UAT:
- DIRECTOR_OPERACIONES_NACIONAL
- DIRECTOR_NACIONAL
- DIRECTOR_ZONAL
- COORDINADOR_COMPANIA
- ASISTENTE_COORDINACION
- SUPERVISOR_SEGURIDAD
- AGENTE_SEGURIDAD
- CLIENTE

## 5. Persistencia

- PostgreSQL 17
- Flyway
- UUID internos
- códigos humanos separados
- versionado/inmutabilidad para reglas publicadas y hechos operacionales
- `created_at`, `updated_at` y actor cuando corresponde
- Transactional Outbox para fan-out e integraciones

## 6. Archivos

MinIO guarda bytes de evidencias y documentos. PostgreSQL guarda metadata, hash SHA-256, tamaño, content-type, entidad vinculada y auditoría.

## 7. Rendimiento y escala

Objetivo de arquitectura: 15.000 guardias por Instancia–País–Empresa; 5.000–6.000 simultáneamente en turno.

Reglas obligatorias:
- paginación server-side;
- evitar `SELECT *` en endpoints de listado;
- sin N+1;
- índices compuestos iniciando por `instance_country_id`;
- índices parciales para estados activos/abiertos;
- payloads móviles pequeños;
- evidencias pesadas por demanda;
- operaciones idempotentes;
- outbox para integración/fan-out;
- caché local limitada en móvil según diseño de Bitácora;
- observabilidad de latencia p50/p95/p99 antes de producción.

La capacidad exacta se valida con load test; no se afirma por diseño únicamente.

## 8. REGESEP

Un REGESEP por Punto. Es un reglamento compuesto, no un documento editado directamente. Una nueva versión efectiva de ATS o una publicación normativa relevante genera nueva versión automáticamente. El PDF es una representación.

## 9. Plataformas móviles

SGI: Agente y futuro SGI: Operador consumen contratos backend; no se reconstruyen en esta primera UAT. Se podrá agregar un simulador UAT para validar Relevos/Bitácora/Patrullas end-to-end.

## 10. Orden de implementación

1. Shell/contexto/RBAC
2. Compañías
3. Servicios/Puntos/Puestos
4. Asignaciones
5. Relevos
6. Consignas/Novedades/STC
7. Bitácora
8. Patrullas
9. REGESEP completo
10. Dashboard/Reportes/Auditoría/Configuración avanzada

## Incremento UAT v0.2 — Asignaciones

La primera vertical transaccional implementada es SGI-06 Asignaciones. Se mantiene el modular monolith y se incorporan adapters LOCAL para SIC: RRHH, SIC: COM y SMC.

Decisiones técnicas importantes:
- snapshots operacionales de RRHH son read-only y están desacoplados del futuro conector real;
- ocurrencias de Turno se materializan por semana desde templates comerciales, evitando asumir número fijo de turnos;
- consultas de personal se paginan server-side;
- cobertura del Dashboard se calcula desde `ShiftOccurrence` + `OperationalAssignment`;
- plan publicado conserva snapshot JSON inmutable y las modificaciones posteriores usan Reasignación explícita;
- `assignment_event` conserva trazabilidad y publicación genera Transactional Outbox.
