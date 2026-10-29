-- COM: responsables independientes de Compañía.
-- responsible_employee_id se conserva como columna legada del Coordinador.
ALTER TABLE company
  ADD COLUMN IF NOT EXISTS assistant_employee_id uuid DEFAULT NULL;

-- Las asignaciones que antes usaban el único campo Responsable para un
-- Asistente se trasladan al nuevo campo sin perder la selección existente.
UPDATE company company_row
SET assistant_employee_id = company_row.responsible_employee_id,
    responsible_employee_id = NULL,
    updated_at = now()
WHERE company_row.responsible_employee_id IS NOT NULL
  AND company_row.assistant_employee_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM employee_operational_snapshot employee
    WHERE employee.instance_country_id = company_row.instance_country_id
      AND employee.employee_id = company_row.responsible_employee_id
      AND lower(employee.role_code) LIKE '%asistente%'
  );

-- V86 omitió username de la clave y, por ello, impedía guardar a la vez
-- el alcance del Coordinador y el del Asistente para la misma Compañía.
DROP INDEX IF EXISTS ux_user_operational_scope_company_responsible;

-- Para las Compañías que ya tienen responsables configurados, se reconstruyen
-- únicamente los dos alcances administrados por COM. Otros alcances se respetan.
DELETE FROM user_operational_scope scope
USING company company_row
WHERE scope.instance_country_id = company_row.instance_country_id
  AND scope.scope_type = 'COMPANY'
  AND scope.scope_id = company_row.id
  AND scope.username IN ('coord', 'asistente')
  AND (company_row.responsible_employee_id IS NOT NULL
    OR company_row.assistant_employee_id IS NOT NULL);

INSERT INTO user_operational_scope
  (id, instance_country_id, username, scope_type, scope_id, persona_id, created_at, updated_at)
SELECT gen_random_uuid(), company_row.instance_country_id, 'coord', 'COMPANY',
       company_row.id, employee.persona_id, now(), now()
FROM company company_row
JOIN employee_operational_snapshot employee
  ON employee.instance_country_id = company_row.instance_country_id
 AND employee.employee_id = company_row.responsible_employee_id
WHERE employee.employment_status = 'ACTIVE'
  AND employee.persona_id IS NOT NULL
  AND lower(employee.role_code) LIKE '%coordinador%'
ON CONFLICT DO NOTHING;

INSERT INTO user_operational_scope
  (id, instance_country_id, username, scope_type, scope_id, persona_id, created_at, updated_at)
SELECT gen_random_uuid(), company_row.instance_country_id, 'asistente', 'COMPANY',
       company_row.id, employee.persona_id, now(), now()
FROM company company_row
JOIN employee_operational_snapshot employee
  ON employee.instance_country_id = company_row.instance_country_id
 AND employee.employee_id = company_row.assistant_employee_id
WHERE employee.employment_status = 'ACTIVE'
  AND employee.persona_id IS NOT NULL
  AND lower(employee.role_code) LIKE '%asistente%'
ON CONFLICT DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_operational_scope_company_responsible
  ON user_operational_scope(instance_country_id, username, scope_type, scope_id)
  WHERE scope_type = 'COMPANY'
    AND username IN ('coord', 'asistente')
    AND scope_id IS NOT NULL;
