-- UAT ONLY — BIT-INT-001
-- Creates alex.chiriboga with the same UAT authentication profile and operational
-- scopes as agente, then binds it to Alex's existing employee identity.
-- Never promote this fixture to production.

DO $$
DECLARE
  v_tenant uuid;
  v_employee uuid;
  v_matches integer;
BEGIN
  SELECT count(*) INTO v_matches
  FROM app_user
  WHERE username = 'agente' AND active = true AND roles LIKE '%AGENTE_SEGURIDAD%';
  IF v_matches <> 1 THEN
    RAISE EXCEPTION 'Expected exactly one active UAT source user agente; found %', v_matches;
  END IF;

  SELECT instance_country_id INTO v_tenant FROM app_user WHERE username = 'agente';

  SELECT count(*)
    INTO v_matches
  FROM employee_operational_snapshot
  WHERE instance_country_id = v_tenant
    AND upper(trim(full_name)) = upper('Alex Enrique Chiriboga Mafla')
    AND employment_status = 'ACTIVE';

  IF v_matches <> 1 THEN
    RAISE EXCEPTION 'Expected exactly one ACTIVE employee named Alex Enrique Chiriboga Mafla; found %', v_matches;
  END IF;

  SELECT employee_id INTO v_employee
  FROM employee_operational_snapshot
  WHERE instance_country_id = v_tenant
    AND upper(trim(full_name)) = upper('Alex Enrique Chiriboga Mafla')
    AND employment_status = 'ACTIVE';

  INSERT INTO app_user(id, username, password_hash, roles, display_name, instance_country_id, active)
  SELECT gen_random_uuid(), 'alex.chiriboga', password_hash, roles,
         'Alex Enrique Chiriboga Mafla', instance_country_id, true
  FROM app_user
  WHERE instance_country_id = v_tenant AND username = 'agente'
  ON CONFLICT (username) DO UPDATE
    SET roles = EXCLUDED.roles,
        display_name = EXCLUDED.display_name,
        active = true
    WHERE app_user.instance_country_id = EXCLUDED.instance_country_id;

  IF NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'alex.chiriboga' AND instance_country_id = v_tenant) THEN
    RAISE EXCEPTION 'Existing alex.chiriboga belongs to another instance';
  END IF;

  DELETE FROM user_operational_scope
  WHERE instance_country_id = v_tenant AND username = 'alex.chiriboga';

  INSERT INTO user_operational_scope(id, instance_country_id, username, scope_type, scope_id, created_at, updated_at)
  SELECT gen_random_uuid(), instance_country_id, 'alex.chiriboga', scope_type, scope_id, now(), now()
  FROM user_operational_scope
  WHERE instance_country_id = v_tenant AND username = 'agente';

  INSERT INTO operator_employee_binding(instance_country_id, username, employee_id, active)
  VALUES (v_tenant, 'alex.chiriboga', v_employee, true)
  ON CONFLICT (instance_country_id, username) DO UPDATE
    SET employee_id = EXCLUDED.employee_id, active = true;
END $$;

-- Non-secret verification output.
SELECT u.username, u.display_name, u.roles, u.active, b.employee_id, e.full_name
FROM app_user u
JOIN operator_employee_binding b
  ON b.instance_country_id = u.instance_country_id AND b.username = u.username
JOIN employee_operational_snapshot e
  ON e.instance_country_id = b.instance_country_id AND e.employee_id = b.employee_id
WHERE u.instance_country_id = (SELECT instance_country_id FROM app_user WHERE username = 'agente')
  AND u.username = 'alex.chiriboga';

