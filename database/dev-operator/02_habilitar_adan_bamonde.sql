-- Solo para la base de DESARROLLO. No incluir en Flyway.
-- Configura la cuenta adan.bamonde para el empleado existente de Galvarino.
-- Si la cuenta no existe, copia el hash de la credencial UAT de agente;
-- si existe, conserva su contraseña. No copia turnos ni asignaciones.
-- Revisar 01_verificar_adan_bamonde.sql antes de ejecutar.
BEGIN;

CREATE TEMP TABLE _operator_target ON COMMIT DROP AS
SELECT 'Adan Rufino Bamonde Palma'::text AS full_name,
       'adan.bamonde'::varchar(80) AS username;

DO $configure_operator$
DECLARE
  v_expected_name text;
  v_username varchar(80);
  v_tenant uuid;
  v_source_roles varchar(500);
  v_source_hash varchar(255);
  v_company_id uuid;
  v_company_count integer;
  v_employee_id uuid;
  v_actual_name varchar(180);
  v_employee_count integer;
  v_membership_count integer;
  v_existing_user_tenant uuid;
  v_existing_user_roles varchar(500);
  v_existing_user_name varchar(160);
  v_existing_user boolean;
BEGIN
  SELECT full_name, username INTO STRICT v_expected_name, v_username FROM _operator_target;
  IF v_username !~ '^[a-z][a-z0-9._-]{2,79}$' OR v_username = 'agente' THEN
    RAISE EXCEPTION 'Nombre de usuario inválido: %', v_username;
  END IF;

  SELECT instance_country_id, roles, password_hash
    INTO v_tenant, v_source_roles, v_source_hash
    FROM app_user WHERE username = 'agente' AND active;
  IF NOT FOUND OR v_source_roles <> 'AGENTE_SEGURIDAD' THEN
    RAISE EXCEPTION 'Falta el usuario origen agente activo con rol AGENTE_SEGURIDAD';
  END IF;

  SELECT count(*) INTO v_company_count
    FROM company WHERE instance_country_id = v_tenant
      AND lower(btrim(name)) = 'galvarino' AND status = 'ACTIVE';
  IF v_company_count <> 1 THEN
    RAISE EXCEPTION 'Se esperaba una compañía Galvarino activa en la instancia; se encontraron %', v_company_count;
  END IF;
  SELECT id INTO v_company_id FROM company
    WHERE instance_country_id = v_tenant
      AND lower(btrim(name)) = 'galvarino' AND status = 'ACTIVE';

  SELECT count(*) INTO v_employee_count
    FROM employee_operational_snapshot e
    WHERE e.instance_country_id = v_tenant AND e.company_id = v_company_id
      AND lower(translate(regexp_replace(btrim(e.full_name), '\s+', ' ', 'g'),
          'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')) =
          lower(translate(v_expected_name, 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun'))
      AND e.employment_status = 'ACTIVE'
      AND lower(btrim(e.role_code)) = 'agente de seguridad';
  IF v_employee_count <> 1 THEN
    RAISE EXCEPTION 'Adan debe figurar exactamente una vez como Agente de Seguridad activo en Galvarino; encontrados %', v_employee_count;
  END IF;
  SELECT e.employee_id, e.full_name INTO v_employee_id, v_actual_name
    FROM employee_operational_snapshot e
    WHERE e.instance_country_id = v_tenant AND e.company_id = v_company_id
      AND lower(translate(regexp_replace(btrim(e.full_name), '\s+', ' ', 'g'),
          'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')) =
          lower(translate(v_expected_name, 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun'))
      AND e.employment_status = 'ACTIVE'
      AND lower(btrim(e.role_code)) = 'agente de seguridad'
    FOR UPDATE;

  SELECT count(*) INTO v_membership_count
    FROM company_membership m
    WHERE m.instance_country_id = v_tenant AND m.company_id = v_company_id
      AND m.employee_id = v_employee_id AND m.membership_type = 'PRIMARY'
      AND m.ends_at IS NULL AND m.starts_at <= now()
      AND lower(btrim(m.role_code)) = 'agente de seguridad';
  IF v_membership_count <> 1 THEN
    RAISE EXCEPTION 'El empleado no tiene una membresía primaria vigente como agente en Galvarino';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM user_operational_scope s
    WHERE s.instance_country_id = v_tenant AND s.username = 'agente'
      AND s.scope_type = 'COMPANY' AND s.scope_id = v_company_id
  ) THEN
    RAISE EXCEPTION 'El usuario origen agente no tiene alcance de Galvarino';
  END IF;
  IF EXISTS (
    SELECT 1 FROM operator_employee_binding b
    WHERE b.instance_country_id = v_tenant AND b.employee_id = v_employee_id
      AND b.username <> v_username AND b.active
  ) THEN
    RAISE EXCEPTION 'Este empleado ya tiene otro usuario de Operador activo';
  END IF;
  IF EXISTS (
    SELECT 1 FROM app_user u
    WHERE u.instance_country_id = v_tenant AND u.username <> v_username
      AND lower(translate(regexp_replace(btrim(u.display_name), '\s+', ' ', 'g'),
          'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')) =
          lower(translate(regexp_replace(btrim(v_actual_name), '\s+', ' ', 'g'),
          'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun'))
  ) THEN
    RAISE EXCEPTION 'Ya existe una cuenta con el nombre del empleado; use ese username en _operator_target';
  END IF;

  SELECT instance_country_id, roles, display_name
    INTO v_existing_user_tenant, v_existing_user_roles, v_existing_user_name
    FROM app_user WHERE username = v_username FOR UPDATE;
  v_existing_user := FOUND;
  IF v_existing_user THEN
    IF v_existing_user_tenant <> v_tenant
       OR v_existing_user_roles <> 'AGENTE_SEGURIDAD'
       OR lower(translate(btrim(v_existing_user_name), 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')) <>
          lower(translate(btrim(v_actual_name), 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')) THEN
      RAISE EXCEPTION 'El username % ya existe con otra identidad, instancia o rol', v_username;
    END IF;
    UPDATE app_user SET active = true WHERE username = v_username;
  ELSE
    INSERT INTO app_user(id, username, password_hash, roles, display_name, instance_country_id, active)
    VALUES (gen_random_uuid(), v_username, v_source_hash, v_source_roles,
            v_actual_name, v_tenant, true);
  END IF;

  IF EXISTS (
    SELECT 1 FROM user_operational_scope s
    WHERE s.instance_country_id = v_tenant AND s.username = v_username
      AND (s.scope_type <> 'COMPANY' OR s.scope_id IS DISTINCT FROM v_company_id)
  ) THEN
    RAISE EXCEPTION 'El username % tiene otro alcance operacional; no se modifica automáticamente', v_username;
  END IF;
  INSERT INTO user_operational_scope(
    id, instance_country_id, username, scope_type, scope_id, created_at, updated_at
  )
  SELECT gen_random_uuid(), v_tenant, v_username, 'COMPANY', v_company_id, now(), now()
  WHERE NOT EXISTS (
    SELECT 1 FROM user_operational_scope s
    WHERE s.instance_country_id = v_tenant AND s.username = v_username
      AND s.scope_type = 'COMPANY' AND s.scope_id = v_company_id
  );

  IF EXISTS (
    SELECT 1 FROM operator_employee_binding b
    WHERE b.instance_country_id = v_tenant AND b.username = v_username
      AND b.employee_id <> v_employee_id
  ) THEN
    RAISE EXCEPTION 'El username % está vinculado a otro empleado', v_username;
  END IF;
  INSERT INTO operator_employee_binding(instance_country_id, username, employee_id, active)
  VALUES (v_tenant, v_username, v_employee_id, true)
  ON CONFLICT (instance_country_id, username) DO UPDATE SET active = true;

  IF (SELECT count(*) FROM user_operational_scope s
      WHERE s.instance_country_id = v_tenant AND s.username = v_username) <> 1 THEN
    RAISE EXCEPTION 'La cuenta debe tener exactamente un alcance operacional';
  END IF;
  RAISE NOTICE 'Cuenta de Operador lista: %, empleado %, compañía Galvarino', v_username, v_employee_id;
END
$configure_operator$;

-- Confirmación sin exponer el hash de contraseña.
SELECT u.username, u.display_name, u.roles, u.active AS user_active,
       b.employee_id, b.active AS binding_active, c.name AS company_name,
       count(s.id) AS galvarino_scopes
FROM _operator_target p
JOIN app_user u ON u.username = p.username
JOIN operator_employee_binding b
  ON b.instance_country_id = u.instance_country_id AND b.username = u.username
JOIN employee_operational_snapshot e
  ON e.instance_country_id = b.instance_country_id AND e.employee_id = b.employee_id
JOIN company c ON c.id = e.company_id AND c.instance_country_id = e.instance_country_id
LEFT JOIN user_operational_scope s
  ON s.instance_country_id = u.instance_country_id AND s.username = u.username
  AND s.scope_type = 'COMPANY' AND s.scope_id = c.id
GROUP BY u.username, u.display_name, u.roles, u.active,
         b.employee_id, b.active, c.name;

COMMIT;
