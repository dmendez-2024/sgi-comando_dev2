-- Alcance operacional derivado de los responsables configurados en TER y COM.
-- Un responsable real (persona_id) no puede repetir el mismo objeto de alcance.
WITH duplicates AS (
  SELECT id,
         row_number() OVER (
           PARTITION BY instance_country_id, persona_id, scope_type, scope_id
           ORDER BY updated_at DESC, created_at DESC, id
         ) AS duplicate_number
  FROM user_operational_scope
  WHERE persona_id IS NOT NULL
    AND scope_id IS NOT NULL
    AND scope_type IN ('ZONE', 'REGION', 'COMPANY')
)
DELETE FROM user_operational_scope scope
USING duplicates duplicate
WHERE scope.id = duplicate.id
  AND duplicate.duplicate_number > 1;

-- Lleva a la tabla de alcances los responsables que ya estaban configurados antes
-- de esta versión. Solo se consideran empleados activos con persona_id real.
WITH configured_responsible AS (
  SELECT zone.instance_country_id, 'dzonal'::varchar AS username,
         'ZONE'::varchar AS scope_type, zone.id AS scope_id, employee.persona_id
  FROM territory_zone zone
  JOIN employee_operational_snapshot employee
    ON employee.instance_country_id = zone.instance_country_id
   AND employee.employee_id = zone.responsible_employee_id
  WHERE employee.employment_status = 'ACTIVE'
    AND employee.persona_id IS NOT NULL
  UNION ALL
  SELECT region.instance_country_id, 'jregional'::varchar,
         'REGION'::varchar, region.id, employee.persona_id
  FROM territory_region region
  JOIN employee_operational_snapshot employee
    ON employee.instance_country_id = region.instance_country_id
   AND employee.employee_id = region.responsible_employee_id
  WHERE employee.employment_status = 'ACTIVE'
    AND employee.persona_id IS NOT NULL
  UNION ALL
  SELECT company.instance_country_id, 'coord'::varchar,
         'COMPANY'::varchar, company.id, employee.persona_id
  FROM company
  JOIN employee_operational_snapshot employee
    ON employee.instance_country_id = company.instance_country_id
   AND employee.employee_id = company.responsible_employee_id
  WHERE employee.employment_status = 'ACTIVE'
    AND employee.persona_id IS NOT NULL
)
DELETE FROM user_operational_scope scope
WHERE (scope.scope_type = 'ZONE' AND scope.username = 'dzonal'
    OR scope.scope_type = 'REGION' AND scope.username = 'jregional'
    OR scope.scope_type = 'COMPANY' AND scope.username = 'coord')
  AND NOT EXISTS (
    SELECT 1
    FROM configured_responsible configured
    WHERE configured.instance_country_id = scope.instance_country_id
      AND configured.username = scope.username
      AND configured.scope_type = scope.scope_type
      AND configured.scope_id = scope.scope_id
      AND configured.persona_id = scope.persona_id
  );

WITH configured_responsible AS (
  SELECT zone.instance_country_id, 'dzonal'::varchar AS username,
         'ZONE'::varchar AS scope_type, zone.id AS scope_id, employee.persona_id
  FROM territory_zone zone
  JOIN employee_operational_snapshot employee
    ON employee.instance_country_id = zone.instance_country_id
   AND employee.employee_id = zone.responsible_employee_id
  WHERE employee.employment_status = 'ACTIVE'
    AND employee.persona_id IS NOT NULL
  UNION ALL
  SELECT region.instance_country_id, 'jregional'::varchar,
         'REGION'::varchar, region.id, employee.persona_id
  FROM territory_region region
  JOIN employee_operational_snapshot employee
    ON employee.instance_country_id = region.instance_country_id
   AND employee.employee_id = region.responsible_employee_id
  WHERE employee.employment_status = 'ACTIVE'
    AND employee.persona_id IS NOT NULL
  UNION ALL
  SELECT company.instance_country_id, 'coord'::varchar,
         'COMPANY'::varchar, company.id, employee.persona_id
  FROM company
  JOIN employee_operational_snapshot employee
    ON employee.instance_country_id = company.instance_country_id
   AND employee.employee_id = company.responsible_employee_id
  WHERE employee.employment_status = 'ACTIVE'
    AND employee.persona_id IS NOT NULL
)
UPDATE user_operational_scope scope
SET username = configured.username,
    updated_at = now()
FROM configured_responsible configured
WHERE scope.instance_country_id = configured.instance_country_id
  AND scope.persona_id = configured.persona_id
  AND scope.scope_type = configured.scope_type
  AND scope.scope_id = configured.scope_id;

WITH configured_responsible AS (
  SELECT zone.instance_country_id, 'dzonal'::varchar AS username,
         'ZONE'::varchar AS scope_type, zone.id AS scope_id, employee.persona_id
  FROM territory_zone zone
  JOIN employee_operational_snapshot employee
    ON employee.instance_country_id = zone.instance_country_id
   AND employee.employee_id = zone.responsible_employee_id
  WHERE employee.employment_status = 'ACTIVE'
    AND employee.persona_id IS NOT NULL
  UNION ALL
  SELECT region.instance_country_id, 'jregional'::varchar,
         'REGION'::varchar, region.id, employee.persona_id
  FROM territory_region region
  JOIN employee_operational_snapshot employee
    ON employee.instance_country_id = region.instance_country_id
   AND employee.employee_id = region.responsible_employee_id
  WHERE employee.employment_status = 'ACTIVE'
    AND employee.persona_id IS NOT NULL
  UNION ALL
  SELECT company.instance_country_id, 'coord'::varchar,
         'COMPANY'::varchar, company.id, employee.persona_id
  FROM company
  JOIN employee_operational_snapshot employee
    ON employee.instance_country_id = company.instance_country_id
   AND employee.employee_id = company.responsible_employee_id
  WHERE employee.employment_status = 'ACTIVE'
    AND employee.persona_id IS NOT NULL
)
INSERT INTO user_operational_scope
  (id, instance_country_id, username, scope_type, scope_id, persona_id, created_at, updated_at)
SELECT gen_random_uuid(), configured.instance_country_id, configured.username,
       configured.scope_type, configured.scope_id, configured.persona_id, now(), now()
FROM configured_responsible configured
WHERE NOT EXISTS (
  SELECT 1
  FROM user_operational_scope scope
  WHERE scope.instance_country_id = configured.instance_country_id
    AND scope.persona_id = configured.persona_id
    AND scope.scope_type = configured.scope_type
    AND scope.scope_id = configured.scope_id
);

-- Los alias dzonal, jregional y coord representan el único responsable configurado
-- para cada Zona, Región o Compañía. Se conserva la fila más recientemente actualizada.
WITH duplicates AS (
  SELECT id,
         row_number() OVER (
           PARTITION BY instance_country_id, username, scope_type, scope_id
           ORDER BY updated_at DESC, created_at DESC, id
         ) AS duplicate_number
  FROM user_operational_scope
  WHERE scope_id IS NOT NULL
    AND (scope_type = 'ZONE' AND username = 'dzonal'
      OR scope_type = 'REGION' AND username = 'jregional'
      OR scope_type = 'COMPANY' AND username = 'coord')
)
DELETE FROM user_operational_scope scope
USING duplicates duplicate
WHERE scope.id = duplicate.id
  AND duplicate.duplicate_number > 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_operational_scope_persona_target
  ON user_operational_scope(instance_country_id, persona_id, scope_type, scope_id)
  WHERE persona_id IS NOT NULL
    AND scope_id IS NOT NULL
    AND scope_type IN ('ZONE', 'REGION', 'COMPANY');

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_operational_scope_responsible_alias_target
  ON user_operational_scope(instance_country_id, username, scope_type, scope_id)
  WHERE scope_id IS NOT NULL
    AND (scope_type = 'ZONE' AND username = 'dzonal'
      OR scope_type = 'REGION' AND username = 'jregional'
      OR scope_type = 'COMPANY' AND username = 'coord');
