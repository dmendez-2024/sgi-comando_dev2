-- Alta de permisos/vínculo para una cuenta EXISTENTE. No es una migración Flyway.
-- Ejecutar completo en una sesión, en la base utilizada por SGI Comando.
-- Primero ejecutar 04_diagnosticar_supervisor.sql.
-- No crea personas, membresías ni asignaciones y conserva la contraseña por defecto.
-- Destino: Ciro Antonio Chavez Molina. Su ficha y membresía deben existir desde SIC/RRHH.
BEGIN;

CREATE TEMP TABLE _supervisor_operator_params ON COMMIT DROP AS
SELECT 'supervisor'::varchar(80) AS username,
       NULL::uuid AS employee_id,           -- UUID real del empleado, si no tiene vínculo.
       NULL::bigint AS persona_id,          -- Alternativa: personas.id real de SIC/RRHH.
       'Ciro Antonio Chavez Molina'::text AS full_name, -- Nombre real; debe coincidir con una sola ficha.
       'Galvarino'::text AS company_name,    -- Cambiar si pertenece a otra compañía.
       NULL::text AS new_password_hash;     -- Opcional: bcrypt NUEVO; NULL conserva contraseña.

DO $configure_supervisor$
DECLARE
  p record;
  v_tenant uuid;
  v_company uuid;
  v_employee uuid;
  v_count integer;
  v_user app_user%ROWTYPE;
  v_snapshot employee_operational_snapshot%ROWTYPE;
  v_binding operator_employee_binding%ROWTYPE;
BEGIN
  SELECT * INTO STRICT p FROM _supervisor_operator_params;
  SELECT instance_country_id INTO STRICT v_tenant
    FROM instance_country_context WHERE singleton_key = 1;
  SELECT * INTO v_user FROM app_user WHERE username = p.username FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'No existe app_user con username %. Este script requiere una cuenta existente.', p.username;
  END IF;
  IF v_user.instance_country_id IS DISTINCT FROM v_tenant THEN
    RAISE EXCEPTION 'Cuenta en tenant %, backend en %. Revisar migración de instancia; no se cambia el tenant de la cuenta.',
      v_user.instance_country_id, v_tenant;
  END IF;
  IF EXISTS (SELECT 1 FROM unnest(regexp_split_to_array(v_user.roles, '\s*,\s*')) AS r(role)
             WHERE btrim(r.role) NOT IN ('', 'SUPERVISOR', 'SUPERVISOR_SEGURIDAD')) THEN
    RAISE EXCEPTION 'Cuenta con otros roles (%). Revisar identidad antes de reemplazarlos.', v_user.roles;
  END IF;
  IF p.new_password_hash IS NOT NULL AND
     p.new_password_hash !~ '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$' THEN
    RAISE EXCEPTION 'new_password_hash debe ser bcrypt MCF válido, no una contraseña en texto plano.';
  END IF;

  -- Serializa modificaciones de alcance/vínculo, incluso entre usernames distintos.
  LOCK TABLE operator_employee_binding, user_operational_scope IN SHARE ROW EXCLUSIVE MODE;
  SELECT count(*) INTO v_count FROM company
    WHERE instance_country_id = v_tenant AND lower(btrim(name)) = lower(btrim(p.company_name))
      AND status = 'ACTIVE';
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'Se esperaba una compañía % activa en el tenant vigente; encontradas %.', p.company_name, v_count;
  END IF;
  SELECT id INTO v_company FROM company
    WHERE instance_country_id = v_tenant AND lower(btrim(name)) = lower(btrim(p.company_name))
      AND status = 'ACTIVE';
  SELECT * INTO v_binding FROM operator_employee_binding
    WHERE instance_country_id = v_tenant AND username = p.username;

  -- Sin parámetros de persona, solo reutiliza un vínculo ya existente.
  IF p.employee_id IS NULL AND p.persona_id IS NULL AND p.full_name IS NULL THEN
    v_employee := v_binding.employee_id;
    IF v_employee IS NULL THEN
      RAISE EXCEPTION 'Falta identificar al empleado de %. Complete employee_id, persona_id o full_name con el diagnóstico 04.', p.username;
    END IF;
  ELSE
    SELECT count(*) INTO v_count FROM employee_operational_snapshot e
      WHERE e.instance_country_id = v_tenant
        AND (p.employee_id IS NULL OR e.employee_id = p.employee_id)
        AND (p.persona_id IS NULL OR e.persona_id = p.persona_id)
        AND (p.full_name IS NULL OR lower(btrim(e.full_name)) = lower(btrim(p.full_name)));
    IF v_count <> 1 THEN
      RAISE EXCEPTION 'La identidad debe corresponder a exactamente un empleado del tenant; encontrados %.', v_count;
    END IF;
    SELECT e.employee_id INTO v_employee FROM employee_operational_snapshot e
      WHERE e.instance_country_id = v_tenant
        AND (p.employee_id IS NULL OR e.employee_id = p.employee_id)
        AND (p.persona_id IS NULL OR e.persona_id = p.persona_id)
        AND (p.full_name IS NULL OR lower(btrim(e.full_name)) = lower(btrim(p.full_name)));
  END IF;
  SELECT * INTO v_snapshot FROM employee_operational_snapshot
    WHERE instance_country_id = v_tenant AND employee_id = v_employee FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'El empleado vinculado no existe en el catálogo operacional del tenant.';
  END IF;
  IF v_snapshot.company_id IS DISTINCT FROM v_company
     OR v_snapshot.employment_status <> 'ACTIVE'
     OR lower(btrim(v_snapshot.role_code)) NOT IN
        ('supervisor_seguridad', 'supervisor de seguridad', 'supervisor de seguridad cl') THEN
    RAISE EXCEPTION 'El empleado % debe ser Supervisor de Seguridad activo en %; revisar SIC/RRHH.',
      v_snapshot.full_name, p.company_name;
  END IF;
  SELECT count(*) INTO v_count FROM company_membership
    WHERE instance_country_id = v_tenant AND employee_id = v_employee
      AND company_id = v_company AND membership_type = 'PRIMARY'
      AND starts_at <= now() AND (ends_at IS NULL OR ends_at > now())
      AND lower(btrim(role_code)) IN
        ('supervisor_seguridad', 'supervisor de seguridad', 'supervisor de seguridad cl');
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'Falta una única membresía primaria vigente como supervisor en %. Revisar SIC/RRHH.', p.company_name;
  END IF;
  IF v_binding.employee_id IS NOT NULL AND v_binding.employee_id <> v_employee THEN
    RAISE EXCEPTION 'La cuenta ya está vinculada a otro empleado; no se reasigna automáticamente.';
  END IF;
  IF EXISTS (SELECT 1 FROM operator_employee_binding
             WHERE instance_country_id = v_tenant AND employee_id = v_employee
               AND username <> p.username AND active) THEN
    RAISE EXCEPTION 'El empleado ya tiene otro username de operador activo.';
  END IF;
  IF EXISTS (SELECT 1 FROM user_operational_scope
             WHERE username = p.username AND (instance_country_id <> v_tenant
               OR scope_type <> 'COMPANY' OR scope_id IS DISTINCT FROM v_company)) THEN
    RAISE EXCEPTION 'La cuenta tiene otros alcances. Revisarlos antes de habilitarla para esta compañía.';
  END IF;

  UPDATE app_user SET active = true, roles = 'SUPERVISOR_SEGURIDAD',
    password_hash = coalesce(p.new_password_hash, password_hash)
    WHERE id = v_user.id;
  INSERT INTO user_operational_scope(id, instance_country_id, username, scope_type, scope_id, created_at, updated_at)
  SELECT gen_random_uuid(), v_tenant, p.username, 'COMPANY', v_company, now(), now()
  WHERE NOT EXISTS (SELECT 1 FROM user_operational_scope
    WHERE instance_country_id = v_tenant AND username = p.username
      AND scope_type = 'COMPANY' AND scope_id = v_company);
  INSERT INTO operator_employee_binding(instance_country_id, username, employee_id, active)
  VALUES (v_tenant, p.username, v_employee, true)
  ON CONFLICT (instance_country_id, username) DO UPDATE SET active = true;
  RAISE NOTICE 'Configurado: %, rol SUPERVISOR_SEGURIDAD, empleado % (%), compañía %. Contraseña %.',
    p.username, v_employee, v_snapshot.full_name, p.company_name,
    CASE WHEN p.new_password_hash IS NULL THEN 'conservada' ELSE 'actualizada' END;
END
$configure_supervisor$;

-- Verificación sin mostrar el hash. COMMIT solo persiste si no hubo excepciones.
SELECT u.username, u.roles, u.active AS user_active, b.employee_id,
       b.active AS binding_active, e.full_name, e.role_code, c.name AS company_name
FROM _supervisor_operator_params p
JOIN app_user u ON u.username = p.username
JOIN operator_employee_binding b ON b.instance_country_id = u.instance_country_id AND b.username = u.username
JOIN employee_operational_snapshot e ON e.instance_country_id = b.instance_country_id AND e.employee_id = b.employee_id
JOIN company c ON c.id = e.company_id AND c.instance_country_id = e.instance_country_id;

-- Candidatas para runtime: turno actual o entrada anticipada hasta 59 minutos.
-- No tener filas no significa contraseña incorrecta: falta una asignación utilizable.
SELECT a.id AS assignment_id, a.status, po.code AS post_code, po.name AS post_name,
       s.starts_at, s.ends_at,
       CASE WHEN s.starts_at <= now() THEN 'CURRENT_SHIFT' ELSE 'EARLY_ENTRY' END AS access_mode
FROM _supervisor_operator_params p
JOIN app_user u ON u.username = p.username
JOIN operator_employee_binding b ON b.instance_country_id = u.instance_country_id AND b.username = u.username
JOIN operational_assignment a ON a.instance_country_id = b.instance_country_id
  AND coalesce(a.actual_employee_id, a.employee_id) = b.employee_id AND a.status <> 'REMOVED'
JOIN shift_occurrence s ON s.id = a.shift_occurrence_id AND s.instance_country_id = a.instance_country_id
JOIN post po ON po.id = s.post_id AND po.instance_country_id = s.instance_country_id
WHERE s.ends_at > now() AND s.starts_at <= now() + interval '59 minutes'
ORDER BY s.starts_at, a.id;

COMMIT;
