# Habilitar a Adan Bamonde en SGI Operador (desarrollo)

Estos scripts son manuales para PostgreSQL. No son migraciones Flyway y no se ejecutan al iniciar la aplicación.

## Si Adan todavía no figura como empleado

El conteo `bamonde_snapshots_any_company = 0` significa que SGI Comando no tiene su ficha operacional. Antes de crear el acceso, confirmar su identidad en SIC/RRHH y enviar o reprocesar el evento de empleado hacia SGI Comando. El contrato de entrada `POST /api/v1/inbound/sic-rrhh/employee-events` requiere el identificador real (`employeeId`/`personaId` o `canonicalEmployeeId`), `fullName`, `roleCode`, `employmentStatus`, `updatedFromSourceAt` y la compañía (`companyCode` o `companyCoreCatalogId`). Para este alta debe llegar activo, con rol `Agente de Seguridad` y compañía Galvarino. La integración requiere la credencial y las cabeceras del contrato; debe enviarla el proceso autorizado de SIC/RRHH, no una cuenta de usuario del navegador. El backend guarda la ficha en `employee_operational_snapshot` y crea su membresía primaria en `company_membership`.

Una vez procesado el evento, repetir el script 01. La consulta exacta debe devolver una sola fila con `employment_status = ACTIVE`, `company_name = Galvarino` y `primary_membership_active = true`. Entonces ejecutar el script 02 para crear o vincular su `app_user`, alcance de compañía y `operator_employee_binding`. La consulta 3 del script 01 puede estar vacía antes de este paso: todavía no hay cuenta. Si la consulta exacta sigue vacía, no ejecutar el script 02; revisar la fila de coincidencias de la consulta 2.

El evento de personal **no** crea `app_user`. Después de validarlo, el script 02 abre una transacción y registra/reactiva `app_user` con rol `AGENTE_SEGURIDAD`, registra `user_operational_scope` para Galvarino y registra/reactiva `operator_employee_binding` para el empleado. Si la cuenta es nueva, usa el hash UAT de `agente` únicamente en desarrollo; si ya existe, conserva su contraseña. El `COMMIT` al final hace persistentes los tres cambios. Su consulta final verifica usuario, vínculo y alcance. Una asignación de turno o puesto se publica aparte y no forma parte del inicio de sesión.

## Alternativa SQL única para desarrollo

Si no está disponible el evento de SIC/RRHH en desarrollo, `03_alta_completa_adan_bamonde_DEV.sql` registra en una transacción las cinco tablas necesarias: `employee_operational_snapshot`, `company_membership`, `app_user`, `user_operational_scope` y `operator_employee_binding`. Está preparado con `persona_id = 5621`, informado para Adan, y deriva el `employee_id` del mismo modo que la integración cuando `SGI_RRHH_USE_CANONICAL_EMPLOYEE_ID=false` (valor predeterminado). El tenant destino procede de `instance_country_context`, igual que en el backend; copia sólo la credencial UAT y el rol de `agente`, aunque esa cuenta permanezca en un tenant antiguo. Confirmar antes de ejecutarlo que **5621 es su personas.id real en SIC/RRHH** y que la instancia no usa identificadores canónicos. El script aborta ante cuentas, fichas, membresías o vínculos contradictorios, y termina con una consulta de verificación y `COMMIT`. No hace falta ejecutar el script 02 después. Cuando la integración de RRHH esté disponible, enviar también el evento legítimo para mantener la trazabilidad del origen.

Si DBeaver muestra `25P02`, revisar el error anterior que abortó la transacción. Ejecutar `00_diagnosticar_entorno_DEV.sql` en la misma conexión: inicia con `ROLLBACK` para liberar la transacción fallida y muestra la base, el servidor, la instancia de `agente`, su alcance y las compañías similares. Si `active_galvarino_in_agente_tenant` es 0, no repetir el alta hasta identificar por qué Galvarino no está activa en esa instancia. Ejecutar el alta como **script completo** en DBeaver, no cada sentencia por separado.

La autenticación Basic de los endpoints de SGI Comando lee `app_user`. El frontend de este repositorio (`frontend/src/api.ts` y `frontend/src/components/Header.tsx`) usa usuarios UAT predefinidos y una contraseña fija: no ofrece un formulario para ingresar un username nuevo. La aplicación externa SGI Operador debe presentar sus credenciales y consultar los endpoints autenticados; la creación SQL de la cuenta no modifica automáticamente su pantalla de acceso.

1. Ejecutar `01_verificar_adan_bamonde.sql` en la base del servidor de desarrollo. La primera consulta muestra conteos por etapa y siempre devuelve una fila. Las siguientes buscan coincidencias por partes del nombre y cuentas existentes. La consulta de resultado exacto debe devolver **exactamente una fila**: Adan Rufino Bamonde Palma, empleado activo y miembro primario vigente de Galvarino. La última muestra `agente` activo, con rol `AGENTE_SEGURIDAD` y alcance de Galvarino. Si no hay fila de empleado, primero debe llegar desde SIC: RRHH al catálogo operacional; el script 02 no crea fichas laborales.
2. Revisar el `username` propuesto (`adan.bamonde`) en la primera sección de `02_habilitar_adan_bamonde.sql`. Si Adan ya tiene una cuenta con otro nombre de acceso, escribir allí ese nombre antes de ejecutar. Ejecutar el script completo en una sola sesión; termina con `COMMIT` y muestra una fila de verificación sin exponer contraseñas.

Ejemplo con `psql` desde una máquina con acceso a la base de desarrollo (sustituir host, usuario y base):

```sh
psql -X -v ON_ERROR_STOP=1 -h HOST -U USUARIO -d BASE -f database/dev-operator/01_verificar_adan_bamonde.sql
psql -X -v ON_ERROR_STOP=1 -h HOST -U USUARIO -d BASE -f database/dev-operator/02_habilitar_adan_bamonde.sql
```

Si `adan.bamonde` ya existe con el rol correcto, el script conserva su contraseña. Si crea una cuenta nueva, copia **solo en desarrollo** el hash de la credencial UAT de `agente`; no crea ni modifica la ficha del empleado. El alcance queda limitado a Galvarino. El script aborta ante identidad ambigua, membresía ausente, otro rol, alcance ajeno o vínculo previo con otro empleado.

El backend debe tener `SGI_OPERATOR_RELIEF_UAT_ENABLED=true` para habilitar las rutas de Operador. El acceso puede funcionar aunque la pantalla de turnos esté vacía: las asignaciones vigentes del empleado se publican por separado; estos scripts no crean turnos ni copian asignaciones de `agente`.
