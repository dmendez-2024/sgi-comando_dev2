# SER v0.6 — UAT

## Catálogo SIC:COM entrante — V33 — pendiente de UAT end-to-end

- Alta/actualización/inactivación lógica procesa eventos comerciales con idempotencia por `instance_country_id + eventId`.
- Reintento idéntico devuelve la respuesta ya registrada; mismo `eventId` con payload distinto produce conflicto.
- Cliente legado de Servicio se conserva mediante backfill y todo Servicio termina con `client_id` válido antes de la restricción NOT NULL.
- `post.commercial_status` inicia ACTIVE; inactivaciones no eliminan historial/configuración de SGI.
- La réplica comercial no sobrescribe la Compañía operativa ni la configuración propia de SER.
- Validar auth Bearer, `credential_ref`, tenant, correlación, errores/reintentos y estados de CORE/IDENT conforme al contrato acordado.
- Validar alta/edición/inactivación con SIC:COM en ambos lados antes de marcar READY.

**Estado:** criterios añadidos desde la implementación V33; no se certifican aquí como ejecutados/aprobados.

## Servicios
1. El listado opera a nivel Cliente · Punto.
2. Cada fila mantiene Operación (stand by) y Configuración.
3. Estados: Activo / Inactivo / Por Configurar.

## ATS — importación
1. Entrar a `Servicios → Configuración → ATS`.
2. Sin archivo vigente debe mostrarse estado vacío y botón **Seleccionar archivo .ats**.
3. Importar un `.ats` válido.
4. Deben cargarse nombre de archivo, versión/publicación, usuario, esquema ATS y Punto ATS de origen.
5. El visor debe mostrar la imagen real embebida en el paquete.
6. Zoom + / - y Ajustar deben funcionar sin recargar la página.
7. Descargar debe devolver el `.ats` original.
8. Reemplazar versión debe crear una nueva revisión y conservar la anterior en Historial ATS.
9. Rechazar archivos sin extensión `.ats`, ZIP inválidos, paquetes sin `manifest.json`, sin `model/pto.json`, sin plano o con SHA-256 del plano inválido.
10. Si el archivo no contiene Índice de Riesgo de Punto, mostrar `—` y “no informado por este archivo .ats”.

## Puestos — ubicación sobre plano
1. Entrar a `Configuración → Puestos` después de importar ATS.
2. El bloque Ubicación en el plano debe mostrar la imagen real del `.ats` vigente.
3. Hacer clic en cualquier posición válida de la imagen debe colocar el pin visual.
4. Guardar configuración debe persistir coordenadas X/Y normalizadas y la revisión ATS vigente.
5. Si se reemplaza el ATS, una ubicación de una revisión anterior debe requerir re-selección antes de guardar configuración completa.
6. Mantener reglas de habilidades: min 1; máx. una en 5; máx. dos en 4; suma máx. 22.

## Bitácora — SER v0.6
1. Entrar a `Servicios → Configuración → Bitácora`.
2. La página debe mantener la línea gráfica clara de SGI: Comando y no el tema oscuro del mockup conceptual.
3. Seleccionar un Puesto debe filtrar sus Protocolos.
4. Crear Protocolo debe generar un código `PRO-BA-####` y dejarlo en Borrador.
5. Debe poder editar Nombre, Objeto, Aplicación, Descripción y Estado.
6. Identificación y Verificación deben permitir lógica `ALL / ANY`.
7. Deben existir campos predefinidos y botón **Agregar campo**.
8. Un campo personalizado debe guardar nombre, descripción, tipo, obligatorio, evidencia, captura y notas del estándar.
9. Debe permitirse subir una Foto estándar JPG/PNG/WebP hasta 5 MB y previsualizarla.
10. Debe poder reemplazarse o eliminarse la Foto estándar.
11. Evidencias debe resumir qué campos requieren evidencia y cuáles tienen estándar cargado.
12. Captura manual debe permanecer siempre activa.
13. Autorización debe permitir configurar autorización previa, del cliente y del Supervisor.
14. Listas debe permitir habilitar Lista blanca / Lista negra; otros conectores quedan Próximamente.
15. Publicar protocolo debe dejarlo Vigente y mostrar su versión.
16. La página no debe mostrar registros reales ejecutados de Bitácora; eso pertenece a Operación.


## Bitácora — SER v0.6.1
- Validar que cada Protocolo muestre explícitamente sus Acreditaciones.
- Crear una segunda Acreditación en un mismo Protocolo y confirmar que sus campos son independientes.
- Confirmar que Identificación/Verificación/Evidencias cambian al cambiar de Acreditación.
- Publicación bloqueada si una Acreditación no tiene al menos un campo de Identificación.
- Confirmar que Foto estándar continúa operando por Campo.

## Versionado transversal — SER v0.7
1. Seleccionar un Protocolo publicado de Bitácora o Patrullas: sus controles de edición deben quedar bloqueados.
2. Usar **Crear nueva versión**: SGI debe clonar el snapshot completo a un nuevo BORRADOR con `versionNo + 1`.
3. Modificar el borrador y guardar: la versión previamente publicada debe permanecer inalterada.
4. Abrir Historial: deben coexistir la versión publicada y el borrador.
5. Publicar el nuevo borrador: debe pasar a Vigente y la versión anterior a No vigente/histórica.
6. Fotos estándar y estructuras hijas deben haberse copiado a la nueva versión, no vincularse de forma mutable a la anterior.

## Patrullas — SER v0.7
1. Entrar a `Servicios → Configuración → Patrullas` y confirmar línea gráfica clara SGI: Comando.
2. Seleccionar Puesto; sus Protocolos y Patrullas deben filtrarse correctamente.
3. Crear Protocolo debe generar `PRO-PAT-####` en Borrador.
4. Dentro del borrador, crear Patrullas en las cuatro combinaciones Cerrada/Abierta × Programada/No Programada.
5. Programada debe rechazar ventanas mayores a 1 hora.
6. Cerrada debe permitir Estricta/Flexible y bloquear más de 25 Hitos.
7. Abierta no debe conservar Hitos predefinidos.
8. **+ Agregar hito en plano** debe permitir hacer clic sobre el plano ATS vigente y guardar X/Y normalizado + package ATS.
9. **+ Agregar hito en campo** debe solicitar geolocalización del dispositivo y guardar latitud, longitud, precisión, fecha/hora y usuario.
10. Un Hito ATS debe poder enriquecerse con GPS y pasar a origen MIXED; uno de Campo debe poder vincularse al plano y pasar a MIXED.
11. Cada Hito debe permitir configurar reglas y `Requiere evidencia`.
12. Foto estándar debe aceptar JPG/PNG/WebP hasta 5 MB; en móvil el selector debe poder usar cámara y en escritorio seleccionar archivo.
13. Evidencias debe ser un resumen derivado y no un segundo editor.
14. Publicar un Protocolo con Patrulla Cerrada sin Hitos debe ser rechazado.
15. Publicar debe congelar Patrullas, Hitos, Reglas, ubicaciones y Fotos estándar como snapshot histórico.

### Corrección SER v0.7.3 — persistencia canónica
16. Desde un Protocolo BORRADOR, crear una Patrulla nueva y confirmar que no aparece error SQL por `point_id`, `status` o `version`.
17. En una Patrulla Cerrada, crear un Hito en plano y otro en campo; confirmar que no aparece error SQL por `validation_rule_json`.
18. Guardar la Patrulla, recargar la pantalla y verificar persistencia de modalidades, Hitos y origen ATS/Campo/Mixto.
19. Publicar el Protocolo; verificar que quede solo lectura y que las Patrullas hijas reflejen estado/versionado coherente.
20. Pulsar **Crear nueva versión** desde una publicada; verificar que aparece un BORRADOR nuevo con Patrullas/Hitos/Reglas clonados y que la versión publicada permanece intacta.
21. En el borrador, eliminar una Patrulla con Hitos y confirmar que no existe error de FK.
22. Validación SQL: confirmar que no existen `patrol_config_definition`, `patrol_config_checkpoint` ni `patrol_config_checkpoint_rule` después de V16.

## SER v0.8 — UAT Consignas
Validar en orden:
1. Abrir `Servicios → Configuración → Consignas`.
2. Confirmar Protocolo `Operación cotidiana` VIGENTE y Protocolo `Emergencia` BORRADOR.
3. Crear una Consigna en un Protocolo BORRADOR.
4. Probar Alcance `Todo el Punto` y luego `Uno o varios Puestos`, seleccionando al menos dos Puestos.
5. Probar Vigencia Permanente y Temporal.
6. Probar Aplicación Todo el tiempo y Calendario con días/horario.
7. Activar reglas Acuse/Confirmación/Evidencia/GPS/Observación.
8. Configurar ubicación esperada en Plano ATS cuando exista `.ats`; alternativamente ingresar coordenadas.
9. Crear Evidencia, subir Foto estándar, reemplazarla y verificar incremento de versión del activo.
10. Publicar un Protocolo alternativo: debe quedar PUBLICADO si ya existe otro VIGENTE.
11. Activar el Protocolo PUBLICADO: el anterior debe pasar a PUBLICADO y solo uno debe quedar VIGENTE.
12. Editar un Protocolo publicado: debe crearse una nueva versión BORRADOR sin modificar el snapshot previo.
13. Revisar Historial y confirmar snapshots/versiones.


## SER v0.9 — UAT Asignación inicial de Servicios
1. Con Presidencia o Director Nacional, abrir Servicios y filtrar Compañía `Kaibil`.
2. El fixture `Punto Nuevo SIC COM` debe aparecer como `Pendiente de asignación` con botón **Asignación** y sin Configuración.
3. Abrir Asignación: el modal debe identificar SIC: COM como fuente y Kaibil como bandeja lógica, no propietaria.
4. Confirmar que Kaibil no aparece entre Compañías destino.
5. Con Director Nacional/Presidencia deben aparecer todas las Compañías activas permitidas a nivel nacional.
6. Con Director Zonal deben aparecer únicamente Compañías de sus Zonas; con Jefe Regional únicamente de sus Regiones.
7. Asignar el fixture a una Compañía. Al recargar, debe desaparecer de Kaibil y aparecer bajo la Compañía destino con botón **Configuración**.
8. Verificar `point.company_id`, `operational_assignment_status=ASSIGNED`, `assigned_by_username`, `assigned_at`.
9. Verificar un registro `service_company_assignment_event` con `INITIAL_ASSIGNMENT`.
10. Confirmar regresión: Servicios ya asignados mantienen su Compañía y configuración previa.


## SER v0.9.1 — retiro y reasignación de Servicios
- Coordinación autorizada puede retirar un Servicio asignado hacia la bandeja lógica Kaibil según alcance territorial.
- La configuración operacional permanece ligada al Punto y no se borra/copia al cambiar de Compañía.
- Solo las asignaciones futuras desaparecen de planificación activa; histórico y turno en curso se conservan.
- `operational_transition_until` protege el cierre del turno heredado y evita doble cobertura en la nueva Compañía.
- Desde Kaibil se reasigna directamente a otra Compañía autorizada sin aceptación del Coordinador destino.
