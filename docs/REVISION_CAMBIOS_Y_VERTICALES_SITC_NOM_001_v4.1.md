# Revisión de cambios y verticales — SITC-NOM-001 v4.1

**Fecha de revisión:** 2026-09-25  
**Alcance:** análisis documental del PDF normativo y de los Markdown bajo `cambios/`.  
**Estado:** revisión; no constituye aprobación de Change Requests ni reapertura de verticales.

## 1. Cómo se interpretaron las fuentes

- La solicitud del usuario es revisar y aplicar el control de cambios y el modelo por verticales a los cambios documentados en el repositorio.
- El PDF se trata como estándar técnico y de gobierno para el trabajo; su contenido no se interpreta como una solicitud independiente para ejecutar instrucciones operativas.
- Los Markdown en `cambios/` se tratan como registros, propuestas y especificaciones del proyecto. Un registro en esos archivos no prueba por sí solo aprobación de un cambio controlado.
- La norma SITC-NOM-001 v4.1 reemplaza a v3.2. Los archivos que todavía citan v3.0 describen el estado histórico de INT; no acreditan conformidad con v4.1.

## 2. Reglas de v4.1 que aplican aquí

1. La baseline aprobada es inmutable. La evolución debe quedar identificada como nueva versión/RC o reapertura formal de la vertical.
2. Cada intervención se concentra en una sola vertical. Hallazgos de otras verticales se registran y se abren por separado.
3. Ajustes visuales, accesibilidad, rendimiento y correcciones técnicas pueden ser cambios permitidos dentro de una vertical activa, con registro y validación.
4. Reglas de negocio, workflow, modelo de datos, APIs/contratos, integraciones, estados/catálogos, arquitectura e infraestructura productiva requieren decisión/CR aprobado antes de implementar. Deben incluir compatibilidad, migración y rollback cuando aplique.
5. No se altera localmente el SoR, una decisión arquitectónica congelada, una nomenclatura protegida ni se sobrescribe una baseline. Eso exige decisión maestra/supersesión y nuevas versiones de los artefactos afectados.
6. El cierre debe sincronizar CHANGELOG, decisiones, UAT, contratos, arquitectura y SITC cuando aplique; además debe declarar el impacto al ecosistema y las siguientes acciones.

## 3. Hallazgos en el material del proyecto

### 3.1 Registro de cambios

`cambios/` contiene bitácoras por responsable (`CHANGELOG_DME_*`, `CHANGELOG_JTO_*`, `CHANGELOG_DM.md`, `CHANGELOG_AJRC.md`) además de documentos de contratos, decisiones y criterios por vertical. Sirven como historial y especificación de trabajo, pero no siguen de forma consistente la ficha CR de la norma: CR-ID, solicitante, problema, clasificación, impacto completo, compatibilidad/migración, riesgo/rollback, aprobador y fecha de aprobación.

La norma pide un registro canónico en `docs/CHANGELOG.md` (o `CHANGELOG-SISTEMAS.md`). El historial repartido en `cambios/` no debería sustituirlo; debe enlazarse desde el changelog canónico cuando el cambio realmente se incorpore a una entrega.

### 3.2 Estado de verticales y versiones

- `docs/FROZEN_VERTICALS.md` y `docs/SGI_COMANDO_CIERRE_FINAL.md` declaran baselines congeladas, incluidas TER v1.0, COM v1.1.3 y ASI v0.7.4.
- `cambios/verticals/ASI/ASI_DECISIONS.md` se identifica como ASI v0.5, mientras que `cambios/VERSION_MATRIX.md` dice ASI v0.7.4 UAT_CANDIDATE. Son artefactos con versiones/estados distintos; se deben reconciliar contra la baseline y el estado vigente antes de usar el decision log como autoridad para una RC.
- `cambios/VERSION_MATRIX.md` y algunos documentos relacionados tienen fecha 2026-09-19 y no reflejan todo el cierre del 2026-09-20. Hay que tratarlos como registros históricos hasta sincronizarlos con el estado confirmado en el handoff/baseline actual.
- Que una bitácora describa un cambio ya implementado no reabre por sí sola una vertical congelada.

### 3.3 Arquitectura e integraciones

- `cambios/API_CATALOG.md`, `cambios/INTERCONNECTIONS.md` y `cambios/README_INT_v0.1.1.md` citan SITC-NOM-001 v3.0 o una baseline de septiembre 21. Deben revisarse contra v4.1 y el SCENARIO_SNAPSHOT vigente de CORE antes de crear o cambiar contratos.
- Los IDs históricos de INT usan convenciones anteriores (`SOURCE__TARGET__NNNNN__V0001`). v4.1 protege Program IDs y establece el formato canónico vigente; no se deben renombrar o reinterpretar estos IDs localmente. Cualquier transición debe quedar documentada como alias/legacy cuando proceda y alineada con CORE.
- v4.1 separa `ECOSYSTEM_INTERCONNECTION` (CORE/SITC) de `EXTERNAL_CONNECTION` (configuración del Sistema propietario por Instancia PE). Los contratos existentes se deben clasificar bajo esa distinción antes de una modificación.
- No se recibió junto con la solicitud un SCENARIO_SNAPSHOT v4.1 vigente. Por tanto, el impacto arquitectónico no puede darse por validado solo a partir del snapshot local fechado el 2026-09-21.

## 4. Mapa completo de los Markdown de `cambios/`

Se revisaron los **40 archivos `.md`** bajo `cambios/` y sus subcarpetas (162,409 bytes). La vertical es la indicada por el propio archivo cuando existe; “probable” marca una asignación derivada de la pantalla o contrato y pendiente de confirmar con el catálogo/baseline vigente. La clasificación es documental: **no es aprobación** ni sustituye un CR.

| Archivo | Vertical / ámbito | Tipo y estado que declara el archivo | Aplicación de SITC-NOM-001 v4.1 |
|---|---|---|---|
| `cambios/API_CATALOG.md` | INT transversal: CORE, IDENT, SIC:COM, SIC:RRHH, SIC:RRMM, ATS, SMC, STC, VISINT, SGI:Operador/Cliente, CM_CON | Catálogo; estados mixtos UAT/DESIGN y destino READY/BLOCKED/MANUAL_PENDING | Revisar completo contra v4.1 y snapshot CORE vigente. No cambiar IDs localmente; mapear clasificaciones de conexión y estado end-to-end. |
| `cambios/API_CONTRACTS.md` | COM, ASI, SER (Puestos/Patrullas/Consignas/Servicios) | Contratos API de varias verticales/versiones | Registro transversal de contratos; cambios a endpoints o payloads requieren CR y deben separarse por vertical/programa afectado. |
| `cambios/CHANGELOG.md` | SGI:Comando, historial global | Historial acumulativo hasta INT v0.1 (2026-09-21) | Histórico, no CR. Mantener `docs/CHANGELOG.md` canónico sincronizado al cerrar nuevas RC. |
| `cambios/CHANGELOG_AJRC.md` | SER (Novedades/Consignas/Patrullas/Bitácora) y COO; Consola requiere confirmar propietario | Ajustes mixtos; algunos aprobados por el usuario, UAT pendiente en varios; build correcto indicado para dos entradas del 25-09 | Separar por vertical/RC. Los ajustes en Novedades/Bitácora/Consignas de ejecución están rotulados SER, pero esas pantallas pueden pertenecer a NOV/BIT/CNS. Confirmar el propietario funcional; no asumir que una aprobación puntual reabre una vertical congelada. |
| `cambios/cambios/CHANGELOG_AJRC.md` | SER, Configuración de Bitácora/Patrullas/Consignas | Ajustes visuales del 22-09; declara reapertura autorizada para Patrullas, sin número de RC; UAT visual pendiente | Son varias páginas de la vertical de configuración SER. La nota respalda reapertura visual de SER/Patrullas, pero no fija release ni declara reapertura separada para todas las páginas; completar esos datos y cerrar UAT. El archivo dice que parte de los cambios no se copió a `docs/CHANGELOG.md`, por lo que falta sincronización canónica al cierre. |
| `cambios/CHANGELOG_DM.md` | Global (sidebar) y TER v1.0.1 | Hotfix visual más paquete TER UAT candidate; cambios TER implementados, UAT pendiente | Contiene más de una intervención/vertical. TER agrega V27 y comportamiento de borradores; requiere registrar CR/aprobación y resolver contradicción con la afirmación de que no se reabre TER. |
| `cambios/CHANGELOG_DME_ASI_AVATARES_2026-09-24.md` | ASI | Cambio UI con avatares ficticios; verificación requerida | PERMITIDO si solo cambia presentación/activos ficticios. ASI parte de una baseline cerrada: registrar RC/reapertura y UAT si se incorpora. |
| `cambios/CHANGELOG_DME_ASI_BUSQUEDA_NOMBRE_2026-09-24.md` | ASI | Cambio de leyenda de búsqueda | PERMITIDO si es solo texto; incorporar en changelog/UAT de una RC de ASI, no como cambio silencioso a baseline. |
| `cambios/CHANGELOG_DME_COM_RESPONSABLE_2026-09-24.md` | COM | Implementado/verificado según secciones del archivo; V28, nuevo campo/API, pruebas y reversa descritos | REQUIERE APROBACIÓN: cambia modelo y API. El texto menciona “la CR”, pero el CR-ID/aprobación no aparece entre estos Markdown. COM v1.1.3 congelada: verificar reapertura autorizada y compatibilidad antes de consolidarlo. |
| `cambios/CHANGELOG_DME_CONVENCION_2026-09-23.md` | Gobierno documental SGI | Convención local vigente: exige guardar todo cambio en `cambios/CHANGELOG_DME_*` | En conflicto de precedencia con v4.1, que pide `docs/CHANGELOG.md` canónico. Mantener `cambios/` como evidencia de trabajo, pero v4.1 gobierna la salida/cierre y no debe sustituirse por la convención local. |
| `cambios/CHANGELOG_DME_RRHH_ASIGNACIONES_2026-09-23.md` | SIC:RRHH/DHO y ASI; integración transversal | Implementado sin commit, pendiente UAT integrada; sin migración | REQUIERE APROBACIÓN por integración/identidad/contrato. Divide trabajo DHO y SGI que cruza programas; separar entregas/impactos. Referencia ASI-DEC-059, que no aparece en el Decision Log ASI de `cambios/` (termina en DEC-058). |
| `cambios/CHANGELOG_DME_RRHH_CARGA_EMPLEADOS_2026-09-24.md` | Operación de datos para RRHH/ASI y responsables COM/TER | Scripts verificados en BD desechable; no ejecutados en base local; reemplaza 200 empleados y elimina dependencias operativas asociadas | No ejecutar por aparecer en el Markdown. Es una carga destructiva de datos multi-vertical, fuera del alcance de una sola vertical; requiere autorización específica del entorno/datos, revisión de respaldos y un plan operativo separado. |
| `cambios/CHANGELOG_DME_RRHH_IDENTIDAD_2026-09-24.md` | Integración SIC:RRHH/DHO y datos de identidad consumidos por ASI | Implementado/verificado en SGI; V29; redespliegue DHO pendiente | REQUIERE APROBACIÓN: contrato e identidad/modelo con migración. Aprobación y validación end-to-end con el otro programa deben quedar trazadas en CR/impacto de ecosistema. |
| `cambios/CHANGELOG_DME_TER_LEYENDA_MAPA_2026-09-24.md` | TER | Restauración de texto/leyenda, indica sin cambio de lógica del mapa | PERMITIDO como UI si el texto respeta TER-DEC-012/014; TER está congelada, así que registrar en una reapertura/RC antes de incorporar a una baseline. |
| `cambios/CHANGELOG_DME_TER_RESPONSABLES_2026-09-24.md` | TER con datos de SIC:RRHH | Filtro de catálogo por `roleCode`; se modificó backend | No es solo visual: modifica elegibilidad/listado de responsables. Clasificar como cambio de regla/contrato de consumo y exigir CR aprobado; versionar/reabrir TER. |
| `cambios/CHANGELOG_JTO_AJUSTE_ESPACIADO_PUESTOS.md` | SER, Configuración de Puestos | Espaciado de encabezados/padding; sin cambio funcional declarado | PERMITIDO visual; confirmar baseline SER y validar responsive/UAT; documentar en entrega canónica. |
| `cambios/CHANGELOG_JTO_AJUSTE_VISUAL_ATS.md` | SER, Servicios → ATS | Mezcla layout responsive con “Validar vínculos” que consulta datos reales y elimina resultados simulados | Separar el polish PERMITIDO del cambio de comportamiento/lectura real; revisar aprobación del segundo y actualizar UAT/contratos si se alteró interfaz. |
| `cambios/CHANGELOG_JTO_BACKUP_BASE_DATOS.md` | Operación de base de datos, transversal | Respaldo PostgreSQL verificado; incluye instrucción con `pg_restore --clean` | Registro operacional, no solicitud de restauración. No ejecutar la restauración; si se propone, identificar entorno vacío/de recuperación y autorización/rollback. |
| `cambios/CHANGELOG_JTO_CORRECCION_ESTADO_ATS.md` | SER, estado ATS en vista Servicios | `services/overview` expone `current`; cambia presentación/interpretación de completitud; sin migración | Cambio de respuesta API/semántica de estado: requiere CR/aprobación si modifica contrato o regla; no clasificar únicamente por su nombre de “corrección”. |
| `cambios/CHANGELOG_JTO_IMPLEMENTACION_CATALOGO_SIC_COM.md` | Integración SIC:COM hacia SGI:Comando y vista Servicios | Implementación descrita; el Markdown cita V27, endpoint inbound, idempotencia y seguridad; el archivo dice que una CR habilita alcance | REQUIERE APROBACIÓN. El código actual implementa esta integración en V33, no V27; actualizar el changelog de trabajo y localizar el CR-ID/aprobación formal. |
| `cambios/CHANGELOG_JTO_INICIAL.md` | Gobierno documental | Crea convención/nombre inicial para registros JTO | Administrativo; no es CR ni fuente para aprobar trabajo técnico. |
| `cambios/CHANGELOG_JTO_INTEGRACION_SIC_COM_SERVICIOS.md` | SER/SGI:Comando e integración SIC:COM | Propuesta explícitamente no implementada; incluye requisitos y bloqueos | Tratar como propuesta. Requiere CR aprobado, contrato confirmado por SIC:COM y snapshot CORE vigente antes de programar. No duplicar la implementación ya descrita en el otro changelog sin reconciliar alcance/estado. |
| `cambios/CHANGELOG_JTO_VALIDACION_PUESTOS.md` | SER, Configuración de Puestos/ATS | Validaciones frontend y respuesta backend `PUT`; archivo no declara versión/estado | Cambia reglas de validación y respuesta observable de API: revisar CR/aprobación, aunque también mejore UX. |
| `cambios/INTERCONNECTIONS.md` | INT transversal | Catálogo de 24 interconexiones; baseline SITC v3.0/21-09 y estados end-to-end | Reconciliar referencias/estados/IDs contra v4.1 y snapshot CORE vigente; no convertir endpoints de terceros en bindings CORE por defecto. |
| `cambios/README_INT_v0.1.1.md` | INT/arquitectura CORE | Declara `CORE.scope` UNIVERSAL y snapshot acumulativo v3 | Registro de sincronización arquitectónica; validar que CORE vigente siga reflejándolo antes de usarlo como baseline. |
| `cambios/SECURITY.md` | Seguridad de la conexión SIC:COM inbound | Una regla de autenticación para el receptor local | Revisar junto al contrato SIC:COM, credenciales por referencia y configuración runtime; no incluir secretos en docs/paquetes. |
| `cambios/VERSION_MATRIX.md` | Matriz transversal de verticales | Matriz al 19-09; luego historial hasta cierre del 20-09 | Está desactualizada frente al cierre y a notas del 22-25. No usar sus primeras filas como estado actual; consolidar con snapshot/RC vigente. |
| `cambios/verticals/ASI/ASI_DECISIONS.md` | ASI | Decision Log enumera decisiones hasta ASI-DEC-058; mezcla baseline v0.5 y apertura v0.7 UAT | Alinear versión/estado con ASI v0.7.4 y localizar DEC-059 citado por otra bitácora. No reescribir decisiones congeladas; agregar supersesiones/versiones trazables. |
| `cambios/verticals/ASI/ASI_INTEGRATIONS.md` | ASI y contratos COM/TER/SIC:COM/SIC:RRHH/SMC | Contratos y SoR, menciona snapshot heredado v0.4 y contrato RRHH fechado 19-09 | Sincronizar con v4.1 y las notas RRHH del 23-24; los cambios de contrato/identidad requieren CR y coordinación de contraparte. |
| `cambios/verticals/COM/COM_ACCEPTANCE_CRITERIA.md` | COM | Criterios COM v1.0 marcados aceptados/congelados | Documento base antiguo frente a COM v1.1.3; conservar como evidencia histórica y mantener criterios de la baseline vigente separados. |
| `cambios/verticals/COM/COM_API_CONTRACT.md` | COM | Contrato `/api/companies`, v1.0; nota sobre reemplazar `logoDataUrl` para producción | Contrato histórico distinto del catálogo CORE/COM v1.1.3. No usar para modificar API sin resolver cuál es autoritativo y aprobar CR. |
| `cambios/verticals/COM/COM_CHANGELOG.md` | COM | Cierre v1.0 y candidato v0.1 | Historial parcial; falta incorporar la evolución a v1.1.3 y los cambios posteriores en el registro canónico. |
| `cambios/verticals/COM/COM_DATA_MODEL.md` | COM | Modelo v1.0, incluye compatibilidad legacy y logo Data URL UAT | Histórico; contrastar con el modelo vigente que obtiene identidad desde CORE y la regla MinIO productiva. |
| `cambios/verticals/COM/COM_DECISIONS.md` | COM | Decisiones congeladas v1.1.3; CORE como SoR, Kaibil, freeze | Úsese para proteger la baseline. El cambio de responsable operacional necesita una decisión nueva/reapertura, sin alterar las decisiones congeladas silenciosamente. |
| `cambios/verticals/COM/COM_MASTER.md` | COM | Master v1.1.3 congelado, base TER v1.0 | Baseline funcional actual según este conjunto; cambio posterior requiere nueva versión e impacto/regresión. |
| `cambios/verticals/COM/COM_UAT.md` | COM | Regresión de la vertical v1.0 | Actualizar/agregar pruebas en la nueva RC sin borrar los criterios/regresión de la baseline. |
| `cambios/verticals/TER/TER_ACCEPTANCE_CRITERIA.md` | TER | TER v1.0 congelada; afirma que ciertas dependencias CORE no reabren TER | La regla no habilita cambios de modelo/flujo local sin CR. Debe armonizarse con TER v1.0.1 y la norma v4.1. |
| `cambios/verticals/TER/TER_CHANGELOG.md` | TER | v1.0 congelada, candidato v0.1 | Historial anterior a v1.0.1 de septiembre 22; archivar como historia y enlazar la RC posterior. |
| `cambios/verticals/TER/TER_DECISIONS.md` | TER | Decision Log v1.0 hasta DEC-015 | No contiene TER-DEC-016..019 que están en `cambios/CHANGELOG_DM.md`; consolidar su procedencia/estado antes de tratarlas como decisiones vigentes. |
| `cambios/verticals/TER/TER_UAT.md` | TER | UAT v0.1 | No cubre todos los criterios/funciones declarados en TER v1.0.1; ampliar para RC y marcar resultados reales. |

## 5. Hallazgos cruzados que condicionan la aplicación

- **La secuencia real de migraciones resuelve la colisión aparente, pero los Markdown están desactualizados:** el código actual tiene V27 TER, V28 responsable COM, V29 puente `persona_id`, V30/V31 carga de empleados, V32 tablas de SGI: Operador y V33 catálogo SIC:COM. El changelog JTO llama V27 a la integración SIC:COM; corregir la referencia documental a V33. No hay dos V27 en la secuencia actual.
- **Migraciones de carga masiva en Flyway:** existen V30 `eliminar_todos_los_empleados` y V31 `insertar_200_empleados_con_avatares`, además de scripts `.txt` descritos como no ejecutados en la bitácora DME. `quarkus.flyway.migrate-at-start=true` está configurado. Por tanto, si una base arranca desde una versión anterior a V30, Flyway intentará ejecutar estas migraciones automáticamente. V30 borra/reemplaza datos de empleados por Instancia–País y V31 inserta 200 registros UAT. No se ejecutaron durante esta revisión; su propósito/entorno y presencia en migraciones automáticas deben aclararse antes de iniciar o desplegar una base.
- **Impacto de tablas sin documento consolidado:** la carpeta `cambios/` no tiene un Markdown dedicado al mapa de tablas. Los impactos aparecen repartidos entre changelogs y modelos: TER `country_subdivision`; COM `company`, `client`, `service`, `post`, recibos SIC:COM; ASI/RRHH `employee_operational_snapshot`; carga masiva en snapshots, membresías, transferencias, habilidades e indisponibilidades; OPR bindings, submissions y evidence.
- **Estado y supersesión TER:** `CHANGELOG_DM.md` agrega cambios de datos/flujo en TER v1.0.1 (DRAFT, promoción y mapa efectivo), aunque su cierre dice que TER v1.0 no se reabre por tratarse de un contrato CORE congelado. Bajo v4.1, el cambio de comportamiento/datos de TER necesita RC/CR aprobado; no basta con que el contrato CORE permanezca igual.
- **Estado SER y pantallas de Operaciones:** hay reapertura autorizada explícita para los cambios visuales de SER/Patrullas, pero no se especifica número RC. Otros cambios en el mismo archivo se describen como aprobados en lo visual, sin evidencia de reapertura para cada vertical congelada. Los nombres `SER / Consola`, `SER / Consignas` y `SER / Novedades` deben cotejarse con los módulos y estados CNS/NOV/BIT; el nombre del autor del changelog no define la vertical.
- **Cambio COM responsable:** la nota describe CR, migración V28 y API; el CR-ID/aprobador no está en los 40 Markdown revisados. La ausencia del artefacto no prueba que la aprobación no exista fuera de `cambios/`, pero hay que localizarlo antes de consolidar la modificación.
- **RRHH/ASI:** hay una referencia a ASI-DEC-059 ausente del Decision Log que llega a DEC-058. Resolver la trazabilidad de esa excepción y separar los cambios del lado DHO de la intervención SGI/ASI.
- **Fuentes locales de estado:** la matriz del 19-09 y los documentos COM/TER v1.0 o ASI v0.5 son evidencia histórica; varios otros archivos describen COM v1.1.3, TER v1.0.1 y ASI v0.7/0.7.4. Se necesita una matriz consolidada vinculada a una baseline/RC concreta.

- **Ruta indicada en el último mensaje:** `C:\Users\user\Desktop\AJRC\CM-SGI\_DEV\sgi-comando\_dev\cambios` no existe literalmente en el entorno. La carpeta del repositorio disponible es `C:\Users\user\Desktop\AJRC\CM-SGI_DEV\sgi-comando_dev\cambios`; se interpretó que las barras antes de `_DEV` y `_dev` eran escapes Markdown de guiones bajos. Si era otra carpeta, falta su ruta real para contrastar diferencias.

## 5. Secuencia recomendada para aplicar los cambios

1. Tomar del código y de `docs/` la baseline real de SGI: Comando y fijar la vertical/programa objetivo. Contrastar arquitectura con un SCENARIO_SNAPSHOT vigente de CORE.
2. Elegir una sola nota/cambio para una primera intervención. Mantener notas de otros módulos como hallazgos separados.
3. Clasificarlo como PERMITIDO, REQUIERE APROBACIÓN o PROHIBIDO COMO CAMBIO LOCAL. Si está controlado, redactar y aprobar el `CHANGE_REQUEST_<TARGET>.md` antes de implementación.
4. Para una vertical congelada, registrar reapertura, versión nueva y alcance antes de editar código. Conservar la baseline previa.
5. Implementar solo esa vertical/CR; sincronizar código, migraciones, API, decisiones, UAT, changelog y SITC según impacto.
6. Comparar BASELINE vs RC, ejecutar validaciones aplicables y cerrar con impacto multi-sistema y siguientes acciones.

## 6. Decisión pendiente para iniciar implementación

El repositorio aporta varias notas de cambio, pero no define una única nota como objetivo de esta intervención ni contiene aprobaciones formales para los cambios controlados. La implementación debe arrancar con una nota concreta y una sola vertical. Orden sugerido: elegir primero un ajuste permitido de una vertical identificada; dejar integraciones y cambios de negocio/datos para una RC con CR aprobado.

**No se modificó código funcional, contratos, datos ni una vertical congelada en esta revisión.**
