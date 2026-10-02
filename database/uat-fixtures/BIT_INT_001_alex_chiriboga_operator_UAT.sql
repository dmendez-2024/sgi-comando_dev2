-- UAT ONLY — BIT-INT-001
-- Creates alex.chiriboga with the same UAT authentication profile and operational
-- scopes as agente, then binds it to the exact employee identity below.
-- Never promote this fixture to production.

DO $$
DECLARE
  v_tenant uuid := '11111111-1111-1111-1111-111111111111';
  v_employee uuid;
  v_matches integer;
BEGIN
  -- Identity comes from the approved explicit UAT fixture DME_02, row 67.
  -- Row 67 maps deterministically to company order 1 (COM-001).
  INSERT INTO employee_operational_snapshot(
    id, instance_country_id, employee_id, persona_id, company_id, full_name,
    role_code, employment_status, id_score, preferred_shift, required_change,
    photo_key, updated_from_source_at, created_at, updated_at
  )
  SELECT gen_random_uuid(), v_tenant,
         'fcf488ea-9c43-3a1d-9494-a5c8a4145fa3'::uuid, 9636, c.id,
         'Alex Enrique Chiriboga Mafla', 'Agente de Seguridad', 'ACTIVE',
         NULL, NULL, false, '/avatars/82000000-0000-0000-0000-000000000001.png',
         now(), now(), now()
  FROM company c
  WHERE c.instance_country_id = v_tenant AND c.code = 'COM-001' AND c.status = 'ACTIVE'
    AND NOT EXISTS (
      SELECT 1 FROM employee_operational_snapshot e
      WHERE e.instance_country_id = v_tenant
        AND e.employee_id = 'fcf488ea-9c43-3a1d-9494-a5c8a4145fa3'::uuid
    );

  INSERT INTO company_membership(
    id, instance_country_id, company_id, employee_id, membership_type, role_code,
    starts_at, ends_at, required_change, created_at, updated_at
  )
  SELECT gen_random_uuid(), v_tenant, c.id,
         'fcf488ea-9c43-3a1d-9494-a5c8a4145fa3'::uuid,
         'PRIMARY', 'Agente de Seguridad', current_date, NULL, false, now(), now()
  FROM company c
  WHERE c.instance_country_id = v_tenant AND c.code = 'COM-001' AND c.status = 'ACTIVE'
    AND NOT EXISTS (
      SELECT 1 FROM company_membership m
      WHERE m.instance_country_id = v_tenant
        AND m.employee_id = 'fcf488ea-9c43-3a1d-9494-a5c8a4145fa3'::uuid
        AND m.membership_type = 'PRIMARY'
    );

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

  IF NOT EXISTS (
    SELECT 1 FROM app_user
    WHERE instance_country_id = v_tenant
      AND username = 'agente'
      AND active = true
      AND roles LIKE '%AGENTE_SEGURIDAD%'
  ) THEN
    RAISE EXCEPTION 'Active UAT source user agente with AGENTE_SEGURIDAD role was not found';
  END IF;

  INSERT INTO app_user(id, username, password_hash, roles, display_name, instance_country_id, active)
  SELECT gen_random_uuid(), 'alex.chiriboga', password_hash, roles,
         'Alex Enrique Chiriboga Mafla', instance_country_id, true
  FROM app_user
  WHERE instance_country_id = v_tenant AND username = 'agente'
  ON CONFLICT (username) DO UPDATE
    SET password_hash = EXCLUDED.password_hash,
        roles = EXCLUDED.roles,
        display_name = EXCLUDED.display_name,
        active = true;

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
WHERE u.instance_country_id = '11111111-1111-1111-1111-111111111111'
  AND u.username = 'alex.chiriboga';

