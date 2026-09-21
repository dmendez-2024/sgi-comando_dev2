# SGI: Comando — SER v0.10.3.4 UAT Hotfix

## Corrección crítica de arranque
- Se restauró `V22__ser_bitacora_protocol_post_scope.sql` **byte-for-byte** al contenido originalmente aplicado a la base UAT en SER v0.10.3.
- Motivo: versiones 0.10.3.1–0.10.3.3 cambiaron únicamente el comentario de cabecera de V22. Flyway incluye esos bytes en el checksum, por lo que el cambio provocó `FlywayValidateException: Migration checksum mismatch for migration version 22`.
- No se ejecuta `flyway repair` y no se altera `flyway_schema_history`: se conserva la migración histórica inmutable, que es la corrección adecuada.

## Funcionalidad conservada
- Bitácora: selección de Puestos del Punto a los que aplica cada Protocolo.
- Listado central: todos los Protocolos del Punto; los que no aplican al Puesto seleccionado aparecen contextualmente `Inactivo`.
- Mejoras de tipografía y padding del panel derecho.
- Estados Borrador / Inactivo / Activo.
- Máximo 10 acreditaciones por Protocolo.
