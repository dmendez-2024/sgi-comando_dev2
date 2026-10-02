-- UAT ONLY — Operator credentials for Alexis Joel Triana Magallanes.
-- Clones the authentication profile and operational scopes from agente and
-- binds the individual username to the existing employee identity.
-- Never promote this fixture to production.

DO $$
DECLARE
  v_tenant uuid := '11111111-1111-1111-1111-111111111111';
  v_employee uuid := '54eca46f-260d-3611-869b-55c95533963f';
  v_matches integer;
BEGIN
  SELECT count(*)
    INTO v_matches
  FROM employee_operational_snapshot
  WHERE instance_country_id = v_tenant
    AND employee_id = v_employee
    AND upper(trim(full_name)) = upper('Alexis Joel Triana Magallanes')
    AND employment_status = 'ACTIVE';

  IF v_matches <> 1 THEN
    RAISE EXCEPTION 'Expected exactly one ACTIVE employee Alexis Joel Triana Magallanes; found %', v_matches;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM app_user
    WHERE instance_country_id = v_tenant
      AND username = 'agente'
      AND active = true
      AND roles LIKE '%AGENTE_SEGURIDAD%'
  ) THEN
    RAISE EXCEPTION 'Active UAT source user agente with AGENTE_SEGURIDAD role was not found';
  END IF;

  IF EXISTS (
    SELECT 1 FROM operator_employee_binding
    WHERE instance_country_id = v_tenant
      AND username = 'alexis.triana'
      AND employee_id <> v_employee
  ) THEN
    RAISE EXCEPTION 'Username alexis.triana is already bound to another employee';
  END IF;

  INSERT INTO app_user(id, username, password_hash, roles, display_name, instance_country_id, active)
  SELECT gen_random_uuid(), 'alexis.triana', password_hash, roles,
         'Alexis Joel Triana Magallanes', instance_country_id, true
  FROM app_user
  WHERE instance_country_id = v_tenant AND username = 'agente'
  ON CONFLICT (username) DO UPDATE
    SET password_hash = EXCLUDED.password_hash,
        roles = EXCLUDED.roles,
        display_name = EXCLUDED.display_name,
        active = true;

  DELETE FROM user_operational_scope
  WHERE instance_country_id = v_tenant AND username = 'alexis.triana';

  INSERT INTO user_operational_scope(id, instance_country_id, username, scope_type, scope_id, created_at, updated_at)
  SELECT gen_random_uuid(), instance_country_id, 'alexis.triana', scope_type, scope_id, now(), now()
  FROM user_operational_scope
  WHERE instance_country_id = v_tenant AND username = 'agente';

  INSERT INTO operator_employee_binding(instance_country_id, username, employee_id, active)
  VALUES (v_tenant, 'alexis.triana', v_employee, true)
  ON CONFLICT (instance_country_id, username) DO UPDATE
    SET employee_id = EXCLUDED.employee_id, active = true;
END $$;

-- Non-secret verification output.
SELECT u.username, u.display_name, u.roles, u.active,
       b.employee_id, e.full_name, count(s.id) AS operational_scopes
FROM app_user u
JOIN operator_employee_binding b
  ON b.instance_country_id = u.instance_country_id AND b.username = u.username
JOIN employee_operational_snapshot e
  ON e.instance_country_id = b.instance_country_id AND e.employee_id = b.employee_id
LEFT JOIN user_operational_scope s
  ON s.instance_country_id = u.instance_country_id AND s.username = u.username
WHERE u.instance_country_id = '11111111-1111-1111-1111-111111111111'
  AND u.username = 'alexis.triana'
GROUP BY u.username, u.display_name, u.roles, u.active, b.employee_id, e.full_name;
