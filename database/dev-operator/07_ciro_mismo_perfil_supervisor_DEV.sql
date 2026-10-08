-- SOLO DESARROLLO/UAT. Cuenta personal de Ciro con el perfil del usuario supervisor.
-- Ejecutar completo. Si la sesión está abortada, ejecutar ROLLBACK por separado antes.
-- Reutiliza la ficha de RRHH; crea membresía primaria si falta y no hay conflictos.
-- No crea turnos. No cambia el usuario supervisor.
-- Cuenta nueva: usa el hash de supervisor para la prueba, salvo new_password_hash.
-- Cuenta existente: conserva su contraseña salvo new_password_hash.
-- Identidad confirmada por diagnóstico: Ciro = 7008, employee_id 882a874b-...;
-- 3286 pertenece a Carlos y no se utiliza. Traslada solo el vínculo agente.prueba de Ciro.
BEGIN;
CREATE TEMP TABLE _ciro_profile_params ON COMMIT DROP AS
SELECT 'supervisor'::text AS source_username,
       'ciro.chavez'::text AS target_username, -- Cambiar si ya tiene otro username.
       'Ciro Antonio Chavez Molina'::text AS full_name,
       7008::bigint AS persona_id,
       '882a874b-b6a6-36ed-93a6-9671881abb66'::uuid AS employee_id,
       'agente.prueba'::text AS previous_binding_username,
       NULL::text AS new_password_hash;      -- Opcional, bcrypt MCF; nunca texto plano.
CREATE TEMP TABLE _ciro_profile_companies(company_id uuid PRIMARY KEY) ON COMMIT DROP;
CREATE OR REPLACE FUNCTION pg_temp.ciro_normalize(value text) RETURNS text
LANGUAGE sql IMMUTABLE AS $normalize$
  SELECT lower(translate(regexp_replace(btrim(coalesce(value, '')), '[[:space:]]+', ' ', 'g'),
    'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun'));
$normalize$;

DO $ciro_profile$
DECLARE
  p record;
  v_tenant uuid;
  v_source app_user%ROWTYPE;
  v_target app_user%ROWTYPE;
  v_employee employee_operational_snapshot%ROWTYPE;
  v_scope record;
  v_company uuid;
  v_count integer;
  v_existing boolean;
BEGIN
  SELECT * INTO STRICT p FROM _ciro_profile_params;
  IF p.target_username = p.source_username OR p.target_username !~ '^[a-z][a-z0-9._-]{2,79}$' THEN
    RAISE EXCEPTION 'Usar un username personal válido, diferente de supervisor.';
  END IF;
  SELECT instance_country_id INTO STRICT v_tenant FROM instance_country_context
    WHERE singleton_key = 1 FOR SHARE;
  SELECT * INTO STRICT v_source FROM app_user WHERE username = p.source_username FOR SHARE;
  IF NOT v_source.active OR btrim(v_source.roles) <> 'SUPERVISOR_SEGURIDAD' THEN
    RAISE EXCEPTION 'El usuario base supervisor debe estar activo con rol SUPERVISOR_SEGURIDAD.';
  END IF;
  IF p.new_password_hash IS NOT NULL AND
     p.new_password_hash !~ '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$' THEN
    RAISE EXCEPTION 'new_password_hash debe ser bcrypt MCF válido.';
  END IF;
  -- Evita crear dos cuentas/vínculos simultáneamente para la misma persona.
  LOCK TABLE app_user IN SHARE ROW EXCLUSIVE MODE;
  LOCK TABLE operator_employee_binding, user_operational_scope IN SHARE ROW EXCLUSIVE MODE;

  -- Copia solo scopes COMPANY del perfil base y resuelve compañías en el tenant vigente.
  -- No copia el tenant antiguo 1111... aunque supervisor permanezca allí.
  IF NOT EXISTS (SELECT 1 FROM user_operational_scope WHERE username = p.source_username) THEN
    RAISE EXCEPTION 'supervisor no tiene alcances. Configurar su alcance antes de copiar el perfil.';
  END IF;
  FOR v_scope IN SELECT * FROM user_operational_scope WHERE username = p.source_username LOOP
    IF v_scope.scope_type <> 'COMPANY' OR v_scope.scope_id IS NULL THEN
      RAISE EXCEPTION 'El perfil base contiene alcance distinto de COMPANY; requiere revisión.';
    END IF;
    SELECT id INTO v_company FROM company WHERE id = v_scope.scope_id
      AND instance_country_id = v_tenant AND status = 'ACTIVE';
    IF NOT FOUND THEN
      SELECT count(*) INTO v_count FROM company dst JOIN company src
        ON lower(btrim(dst.name)) = lower(btrim(src.name))
        WHERE src.id = v_scope.scope_id AND src.instance_country_id = v_scope.instance_country_id
          AND dst.instance_country_id = v_tenant AND dst.status = 'ACTIVE';
      IF v_count <> 1 THEN
        RAISE EXCEPTION 'No se puede resolver compañía del alcance % en el tenant vigente.', v_scope.scope_id;
      END IF;
      SELECT dst.id INTO v_company FROM company dst JOIN company src
        ON lower(btrim(dst.name)) = lower(btrim(src.name))
        WHERE src.id = v_scope.scope_id AND src.instance_country_id = v_scope.instance_country_id
          AND dst.instance_country_id = v_tenant AND dst.status = 'ACTIVE';
    END IF;
    INSERT INTO _ciro_profile_companies VALUES(v_company) ON CONFLICT DO NOTHING;
  END LOOP;

  SELECT count(*) INTO v_count FROM employee_operational_snapshot e
    WHERE e.instance_country_id = v_tenant
      AND (pg_temp.ciro_normalize(e.full_name) = pg_temp.ciro_normalize(p.full_name)
        OR (p.persona_id IS NOT NULL AND e.persona_id = p.persona_id));
  IF v_count = 0 AND EXISTS (SELECT 1 FROM employee_operational_snapshot e
    WHERE e.instance_country_id <> v_tenant
      AND (e.persona_id = p.persona_id
        OR pg_temp.ciro_normalize(e.full_name) = pg_temp.ciro_normalize(p.full_name))) THEN
    RAISE EXCEPTION 'La ficha de Ciro existe en otro tenant. Ejecutar 08_diagnosticar_ficha_ciro.sql; no se migra ni duplica automáticamente.';
  END IF;
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'Falta una ficha única de Ciro en el tenant vigente (encontradas %). Ejecutar 08_diagnosticar_ficha_ciro.sql para localizar persona_id 7008 y posibles diferencias de nombre.', v_count;
  END IF;
  SELECT * INTO v_employee FROM employee_operational_snapshot e
    WHERE e.instance_country_id = v_tenant
      AND (pg_temp.ciro_normalize(e.full_name) = pg_temp.ciro_normalize(p.full_name)
        OR (p.persona_id IS NOT NULL AND e.persona_id = p.persona_id)) FOR UPDATE;
  IF pg_temp.ciro_normalize(v_employee.full_name) <> pg_temp.ciro_normalize(p.full_name)
     OR v_employee.employee_id IS DISTINCT FROM p.employee_id
     OR (p.persona_id IS NOT NULL AND v_employee.persona_id IS NOT NULL
       AND v_employee.persona_id <> p.persona_id) THEN
    RAISE EXCEPTION 'Ficha contradictoria: nombre %, persona_id %. Esperados Ciro Antonio Chavez Molina / 7008 y employee_id 882a874b-b6a6-36ed-93a6-9671881abb66. Revisar diagnóstico 08.',
      v_employee.full_name, v_employee.persona_id;
  END IF;
  IF v_employee.employment_status <> 'ACTIVE' OR lower(btrim(v_employee.role_code)) NOT IN
    ('supervisor_seguridad', 'supervisor de seguridad', 'supervisor de seguridad cl') THEN
    RAISE EXCEPTION 'Ciro debe figurar activo como Supervisor de Seguridad en SIC/RRHH.';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM _ciro_profile_companies WHERE company_id = v_employee.company_id) THEN
    RAISE EXCEPTION 'La compañía de Ciro no pertenece al alcance del usuario base supervisor.';
  END IF;
  -- Completa solo un persona_id ausente en una ficha única con nombre validado.
  -- Mantiene el employee_id existente (también cuando procede de SIC/RRHH canónico).
  IF v_employee.persona_id IS NULL AND p.persona_id IS NOT NULL THEN
    UPDATE employee_operational_snapshot SET persona_id = p.persona_id
      WHERE instance_country_id = v_tenant AND employee_id = v_employee.employee_id;
  END IF;
  SELECT count(*) INTO v_count FROM company_membership m
    WHERE m.instance_country_id = v_tenant AND m.employee_id = v_employee.employee_id
      AND m.company_id = v_employee.company_id AND m.membership_type = 'PRIMARY'
      AND m.starts_at <= now() AND (m.ends_at IS NULL OR m.ends_at > now())
      AND lower(btrim(m.role_code)) IN
        ('supervisor_seguridad', 'supervisor de seguridad', 'supervisor de seguridad cl');
  IF v_count = 0 THEN
    IF EXISTS (SELECT 1 FROM company_membership m
      WHERE m.instance_country_id = v_tenant AND m.employee_id = v_employee.employee_id
        AND m.membership_type = 'PRIMARY' AND (m.ends_at IS NULL OR m.ends_at > now())) THEN
      RAISE EXCEPTION 'Ciro tiene una membresía primaria distinta o futura; revisar antes de continuar.';
    END IF;
    INSERT INTO company_membership(id, instance_country_id, company_id, employee_id,
      membership_type, role_code, starts_at, ends_at, required_change, created_at, updated_at)
    VALUES(gen_random_uuid(), v_tenant, v_employee.company_id, v_employee.employee_id,
      'PRIMARY', v_employee.role_code, now(), NULL, false, now(), now());
  ELSIF v_count <> 1 THEN
    RAISE EXCEPTION 'Falta una única membresía primaria vigente de Ciro como supervisor.';
  END IF;
  -- Si ya hay una cuenta personal con su nombre, exige usar su username.
  IF EXISTS (SELECT 1 FROM app_user WHERE username <> p.target_username
    AND pg_temp.ciro_normalize(display_name) = pg_temp.ciro_normalize(v_employee.full_name)
    AND NOT (username = p.previous_binding_username AND EXISTS
      (SELECT 1 FROM operator_employee_binding b WHERE b.username = p.previous_binding_username
        AND b.instance_country_id = v_tenant AND b.employee_id = v_employee.employee_id))) THEN
    RAISE EXCEPTION 'Ciro ya tiene cuenta con otro username. Usarlo en target_username.';
  END IF;
  SELECT * INTO v_target FROM app_user WHERE username = p.target_username FOR UPDATE;
  v_existing := FOUND;
  IF v_existing AND (v_target.instance_country_id IS DISTINCT FROM v_tenant
    OR pg_temp.ciro_normalize(v_target.display_name) <> pg_temp.ciro_normalize(v_employee.full_name)
    OR EXISTS (SELECT 1 FROM unnest(regexp_split_to_array(v_target.roles, '\s*,\s*')) AS r(role)
      WHERE btrim(r.role) NOT IN ('', 'SUPERVISOR', 'SUPERVISOR_SEGURIDAD', 'AGENTE_SEGURIDAD'))) THEN
    RAISE EXCEPTION 'El username destino tiene otra identidad, tenant o roles; revisar antes de continuar.';
  END IF;
  IF EXISTS (SELECT 1 FROM operator_employee_binding WHERE username = p.target_username
    AND (instance_country_id <> v_tenant OR employee_id <> v_employee.employee_id)) THEN
    RAISE EXCEPTION 'El username destino está vinculado a otro empleado o tenant.';
  END IF;
  IF EXISTS (SELECT 1 FROM operator_employee_binding WHERE instance_country_id = v_tenant
    AND employee_id = v_employee.employee_id AND username <> p.target_username
    AND username <> p.previous_binding_username AND active) THEN
    RAISE EXCEPTION 'Ciro ya tiene otro username vinculado activo. Revisar y usar esa cuenta.';
  END IF;
  IF EXISTS (SELECT 1 FROM user_operational_scope s WHERE s.username = p.target_username
    AND (s.instance_country_id <> v_tenant OR s.scope_type <> 'COMPANY'
      OR NOT EXISTS (SELECT 1 FROM _ciro_profile_companies cp WHERE cp.company_id = s.scope_id))) THEN
    RAISE EXCEPTION 'El destino tiene alcances diferentes; no se eliminan automáticamente.';
  END IF;

  -- El diagnóstico confirmó que agente.prueba representa a Ciro. Mantiene la fila
  -- histórica, desactivando solo este vínculo, sin modificar su cuenta ni contraseña.
  UPDATE operator_employee_binding SET active = false
    WHERE instance_country_id = v_tenant AND username = p.previous_binding_username
      AND employee_id = v_employee.employee_id AND active;
  IF v_existing THEN
    UPDATE app_user SET active = true, roles = v_source.roles,
      password_hash = coalesce(p.new_password_hash, password_hash) WHERE id = v_target.id;
  ELSE
    INSERT INTO app_user(id, username, password_hash, roles, display_name, instance_country_id, active)
    VALUES(gen_random_uuid(), p.target_username, coalesce(p.new_password_hash, v_source.password_hash),
      v_source.roles, v_employee.full_name, v_tenant, true);
  END IF;
  INSERT INTO user_operational_scope(id, instance_country_id, username, scope_type, scope_id, created_at, updated_at)
  SELECT gen_random_uuid(), v_tenant, p.target_username, 'COMPANY', cp.company_id, now(), now()
    FROM _ciro_profile_companies cp WHERE NOT EXISTS (SELECT 1 FROM user_operational_scope s
      WHERE s.username = p.target_username AND s.instance_country_id = v_tenant
        AND s.scope_type = 'COMPANY' AND s.scope_id = cp.company_id);
  INSERT INTO operator_employee_binding(instance_country_id, username, employee_id, active)
  VALUES(v_tenant, p.target_username, v_employee.employee_id, true)
  ON CONFLICT (instance_country_id, username) DO UPDATE SET active = true;
  RAISE NOTICE 'Ciro habilitado: username %, perfil de %, tenant %, empleado %.',
    p.target_username, p.source_username, v_tenant, v_employee.employee_id;
END
$ciro_profile$;

SELECT u.username, u.display_name, u.roles, u.active, u.instance_country_id,
       b.employee_id, b.active AS binding_active, s.scope_type, c.name AS company_name
FROM _ciro_profile_params p
JOIN app_user u ON u.username = p.target_username
JOIN operator_employee_binding b ON b.username = u.username AND b.instance_country_id = u.instance_country_id
JOIN user_operational_scope s ON s.username = u.username AND s.instance_country_id = u.instance_country_id
JOIN company c ON c.id = s.scope_id AND c.instance_country_id = s.instance_country_id;
COMMIT;
