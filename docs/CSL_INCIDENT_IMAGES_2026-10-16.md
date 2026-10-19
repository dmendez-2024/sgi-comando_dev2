# CSL IMG RC1 — Imágenes persistentes de incidentes

Inicio: 2026-10-16. Validación: 2026-10-19. CR: [CR-SGI_COM-CSL-0001](CR_SGI_COM_CSL_0001.md). Estado: implementado en desarrollo, pendiente despliegue y UAT de navegador. No se sobrescribe la baseline histórica congelada.

## Comportamiento y almacenamiento

Descripción y Resolución admiten cinco imágenes cada una, JPEG/PNG/WebP, hasta 5 MiB por archivo. ImageSniffer verifica los bytes; Digests calcula SHA-256. El formulario diferencia pendiente de guardar, guardada y archivo histórico no disponible.

Con fotos pendientes, Guardar borrador/Finalizar primero guarda DRAFT, sube los archivos y después confirma el estado solicitado. Si una carga falla, mantiene el borrador y los archivos pendientes en la memoria del formulario para reintento; no anuncia finalización. Recargar/cerrar antes de guardar pierde los archivos locales: no se implementa cola offline. Reenviar el mismo UUID y bytes reutiliza la evidencia; otro incidente, sección o contenido con ese UUID responde 409.

Se reutilizan StorageService, el bucket configurado `sgi.storage.evidence-bucket` y la entidad canónica `evidence_object`. Objetos: `evidence/{instanceCountryId}/incidents/{incidentId}/{evidenceId}.{ext}`. PostgreSQL conserva bucket, clave, MIME detectado, tamaño, hash, actor, tiempos y vínculo al incidente. El JSON del formulario conserva referencias; no contiene binarios/base64 ni credenciales. No se inventan asignaciones o agentes para Consola.

Solo se muestran las referencias activas de descripción/resolución. Quitar una imagen de la selección no borra el objeto ni la evidencia canónica. Se rechazan referencias ajenas o no subidas. La carga y lectura comprueban los roles y el alcance territorial del formulario; los borradores sin Compañía pertenecen a su creador. Las escrituras bloquean la fila del incidente para serializar cargas y límite por sección. Los objetos se sirven por endpoint autenticado, sin URL pública.

Las fotos históricas con solo metadata se identifican como no disponibles y requieren volver a adjuntar el original. No se pueden recuperar bytes nunca almacenados. Esta RC supersede la limitación de imágenes de CSL_INCIDENT_PERSISTENCE_2026-10-08.md, sin reescribir el alcance histórico de CSL v0.2. STC y VISINT no se activan para estos adjuntos.

## API interna v1

| Método y ruta | Contrato |
|---|---|
| POST `/api/v1/incidents/{incidentId}/evidences` | Multipart `section=description|resolution`, `clientEvidenceId` UUID, `file`. Respuesta: `id` (UUID cliente), `evidenceId`, `name`, `size`, `type`, `sha256`, `stored=true`. |
| GET `/api/v1/incidents/{incidentId}/evidences` | Lista activa de `{id: evidenceId, source: OBJECT, section}`. |
| GET `/api/v1/incidents/{incidentId}/evidences/{photoId}/content` | Bytes con MIME detectado, `Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`. |

Errores: 400 formato/tamaño/sección/límite/referencia inválidos; 401 sin autenticación; 403 rol/ámbito o borrador ajeno; 404 incidente/imagen no visible; 409 UUID en conflicto; 503 almacenamiento indisponible con mensaje legible. Se conserva POST/PUT `/api/incidents` y se normalizan referencias con metadata del servidor. El navegador no decide claves de objeto ni tenant.

## Migración y reversión

V85 agrega `evidence_object.incident_id` con FK, tipos INCIDENT_DESCRIPTION/INCIDENT_RESOLUTION y contexto de Consola sin assignment_id/employee_id. El CHECK exige esos IDs para todos los tipos previos de Operador; en incidentes exige incident_id=event_id=target_id y ausencia de asignación/agente. Username admite 160 caracteres. No se modifican migraciones anteriores.

Respaldar BD antes del despliegue y revisar historial Flyway. Desplegar backend/frontend compatibles y aplicar V85 mediante Flyway; no resetear volúmenes ni reparar checksums antiguos automáticamente. Rollback: volver a la aplicación anterior conservando V85 y fotos; su galería no conocerá estos adjuntos. MinIO y PostgreSQL no comparten transacción: una caída posterior a la subida puede dejar un objeto huérfano, que debe conciliarse con metadata antes de limpiarlo. Retención y limpieza automática quedan pendientes.

## Validación efectuada

- Build frontend: TypeScript/Vite correcto, con advertencia de tamaño de bundle.
- Build Docker backend: Java 25, compilación de tests y empaquetado Quarkus correctos. El Dockerfile usa skipTests: no se afirma ejecución de JUnit.
- V85 aplicada en base desechable con copia solo del esquema local; sin copiar personas. No equivale a validar todas las migraciones históricas desde cero.
- `scripts/uat/incident-images-test.mjs`: PASS, 33 peticiones verificadas con PostgreSQL y MinIO reales. Bytes exactos, descripción/resolución, idempotencia, finalizar/reabrir, retirada de selección, límites, tipos, UUID conflictivo, referencia ajena, borrador ajeno, usuario sin ámbito, otra instancia y metadata histórica.
- `scripts/uat/incident-images-storage-failure-test.mjs`: PASS, almacenamiento inaccesible devuelve 503 y preserva DRAFT sin referencia falsa.
- `scripts/uat/incident-images-constraints.sql`: PASS, Operador sin asignación/empleado es rechazado por ck_evidence_object_context.
- Servicios temporales: backend 18085/18086; base `sgi_csl_images_test_20261016_1740`; bucket `sgi-csl-images-test-20261016`. No se reiniciaron los servicios operacionales.
- Cierre: ambos backends temporales detenidos. Se conservan la base y el bucket de validación; para repetir las pruebas hay que iniciar las instancias temporales de nuevo.
- Pendiente navegador: adjuntar, guardar borrador, reabrir, finalizar y comprobar miniaturas; repetir fallo de red y reintento. No se declara apto para producción ni desplegado remotamente.

## Validación desde la pantalla y cierre

Corrección solicitada el 2026-10-19: se retiran los cuatro scripts auxiliares de esta intervención, incluidos los fixtures con usuarios, instancias y UUID fijos. Los resultados de servidor anteriores se conservan solo como historial de lo efectivamente ejecutado; esos scripts no forman parte de la implementación ni del arranque.

La validación de aceptación se hace desde Consola con la sesión autenticada y los registros existentes que el usuario tenga autorizados:

1. Abrir Notificar Incidente o editar un incidente autorizado, seleccionando Cliente, Punto, Puesto y colaboradores de los catálogos reales disponibles en pantalla.
2. Adjuntar imágenes originales en Descripción y Resolución. Confirmar que indican Pendiente de guardar.
3. Guardar borrador; comprobar Guardada, salir y volver a abrir. Comparar nombres y contenido de las fotos.
4. Completar los campos requeridos y Finalizar. Abrir el detalle, comprobar ambas fotos y abrirlas a tamaño completo.
5. Recargar y repetir la consulta para comprobar persistencia. Quitar una referencia desde Editar y confirmar que desaparece de la galería después de guardar.
6. Registrar fallos y evidencia observada. No inventar datos laborales, responsables, asignaciones ni instancias; no insertar registros directamente en SQL ni ejecutar semillas para completar esta prueba. Si falta sesión o un registro adecuado, identificar la dependencia.

Las imágenes originales históricas sin binario deben volver a adjuntarse; una referencia anterior no acredita que el archivo esté almacenado.

Siguiente acción: Sistemas despliega la RC en UAT, comprueba Flyway/MinIO, ejecuta pruebas de interfaz y registra aceptación; luego completa el gate pre-producción. Otros programas afectados: ninguno. Interconexiones nuevas/modificadas/retiradas: 0/0/0. Sin cambios de interconexiones/SITC; no se requiere promoción en CORE.
# Corrección del formulario el 2026-10-19

La prueba desde pantalla detectó que cambiar la key del editor al recibir el ID del primer borrador desmontaba el componente. La nueva instancia conservaba el payload anterior a la subida; al finalizar enviaba listas vacías, aunque los archivos ya estaban almacenados. Se eliminó esa key variable para conservar los archivos y referencias durante toda la sesión de edición. Cerrar y volver a abrir el formulario sigue creando una nueva sesión.

Verificación de esta corrección: frontend compilado y reconstruido en Docker local. Para el caso real INC-2026-000001, se recuperaron por el endpoint habitual de actualización las referencias de los dos objetos existentes (descripción y resolución), sin crear imágenes ni incidentes. Ambas descargas autenticadas respondieron HTTP 200, con 36973 y 246983 bytes respectivamente. El nombre original no se conservaba en evidence_object tras perder el payload; se usó el nombre explícito evidencia-recuperada. Pendiente repetir desde pantalla el flujo completo con un incidente nuevo para confirmar que no vuelve a ocurrir.

El usuario confirmó la visualización de las fotos. A petición suya se retiraron el override local, los flags de excepción, su aviso y la cuenta temporal creada para desbloquear la prueba. Se restauraron las validaciones originales de Cliente/Punto y colaboradores tanto en frontend como en backend y se reconstruyeron ambos servicios sin override. Verificación: finalizar sin ubicación devuelve HTTP 400; la cuenta retirada devuelve HTTP 401. Se conservan el incidente creado por el usuario y sus dos referencias de imagen. El acceso posterior requiere una cuenta autorizada o IDENT configurado; no se agregó una semilla ni un mecanismo de autenticación alternativo.

