-- Reparación puntual de la cuenta UAT supervisor; no migra el tenant de toda la base.
-- Si la sesión está abortada (25P02), ejecutar ROLLBACK antes de este archivo.
-- Conserva contraseña, estado e identidad de la cuenta. Ejecutar completo.
BEGIN;
DO $repair_supervisor_tenant$
DECLARE
  v_username text := 'supervisor';
  v_old uuid := '11111111-1111-1111-1111-111111111111';
  v_expected_current uuid := '398233d2-293a-4709-ac30-b74c4269e22d';
  v_current uuid;
  v_company uuid;
  v_count integer;
  v_user app_user%ROWTYPE;
BEGIN
  SELECT instance_country_id INTO STRICT v_current
    FROM instance_country_context WHERE singleton_key = 1 FOR SHARE;
  IF v_current IS DISTINCT FROM v_expected_current THEN
    RAISE EXCEPTION 'Instancia inesperada: %. Este archivo corresponde al tenant %.', v_current, v_expected_current;
  END IF;
  SELECT * INTO STRICT v_user FROM app_user WHERE username = v_username FOR UPDATE;
  IF v_user.instance_country_id NOT IN (v_old, v_current) THEN
    RAISE EXCEPTION 'La cuenta pertenece a un tercer tenant: %.', v_user.instance_country_id;
  END IF;
  IF EXISTS (SELECT 1 FROM unnest(regexp_split_to_array(v_user.roles, '\s*,\s*')) AS r(role)
             WHERE btrim(r.role) NOT IN ('SUPERVISOR', 'SUPERVISOR_SEGURIDAD')) THEN
    RAISE EXCEPTION 'Roles inesperados: %. Revisar identidad de la cuenta.', v_user.roles;
  END IF;
  LOCK TABLE operator_employee_binding, user_operational_scope IN SHARE ROW EXCLUSIVE MODE;
  -- Un vínculo antiguo necesita migración de identidad de empleado, no solo cambiar el tenant.
  IF EXISTS (SELECT 1 FROM operator_employee_binding WHERE username = v_username
             AND instance_country_id <> v_current) THEN
    RAISE EXCEPTION 'Existe un vínculo empleado en otro tenant; revisar su identidad antes de migrarlo.';
  END IF;
  SELECT count(*) INTO v_count FROM company WHERE instance_country_id = v_current
    AND lower(btrim(name)) = 'galvarino' AND status = 'ACTIVE';
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'Se esperaba una única Galvarino activa en el tenant actual; encontradas %.', v_count;
  END IF;
  SELECT id INTO v_company FROM company WHERE instance_country_id = v_current
    AND lower(btrim(name)) = 'galvarino' AND status = 'ACTIVE';
  IF EXISTS (SELECT 1 FROM user_operational_scope s WHERE s.username = v_username
    AND (s.instance_country_id NOT IN (v_old, v_current) OR s.scope_type <> 'COMPANY'
      OR s.scope_id IS NULL
      OR (s.instance_country_id = v_current AND s.scope_id IS DISTINCT FROM v_company)
      OR (s.instance_country_id = v_old AND s.scope_id IS DISTINCT FROM v_company
        AND NOT EXISTS (SELECT 1 FROM company c WHERE c.id = s.scope_id
          AND c.instance_country_id = v_old AND lower(btrim(c.name)) = 'galvarino')))) THEN
    RAISE EXCEPTION 'La cuenta tiene alcances distintos de Galvarino; no se migran automáticamente.';
  END IF;

  UPDATE app_user SET instance_country_id = v_current WHERE id = v_user.id;
  -- Sustituye únicamente los alcances antiguos de esta cuenta, previamente validados.
  DELETE FROM user_operational_scope WHERE username = v_username AND instance_country_id = v_old;
  INSERT INTO user_operational_scope(id, instance_country_id, username, scope_type, scope_id, created_at, updated_at)
  SELECT gen_random_uuid(), v_current, v_username, 'COMPANY', v_company, now(), now()
  WHERE NOT EXISTS (SELECT 1 FROM user_operational_scope WHERE username = v_username
    AND instance_country_id = v_current AND scope_type = 'COMPANY' AND scope_id = v_company);
  RAISE NOTICE 'Tenant de % corregido a %. Falta ejecutar 05 para vincular a Ciro si su ficha ya existe.', v_username, v_current;
END
$repair_supervisor_tenant$;

SELECT u.username, u.roles, u.active, u.instance_country_id,
       s.scope_type, c.name AS company_name
FROM app_user u
LEFT JOIN user_operational_scope s ON s.username = u.username AND s.instance_country_id = u.instance_country_id
LEFT JOIN company c ON c.id = s.scope_id AND c.instance_country_id = s.instance_country_id
WHERE u.username = 'supervisor';
COMMIT;
