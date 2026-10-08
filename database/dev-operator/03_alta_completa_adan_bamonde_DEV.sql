-- SOLO DESARROLLO. Alta manual excepcional cuando el evento de SIC/RRHH no está disponible.
-- persona_id=5621 proporcionado para Adan; confirmar que corresponde a la base fuente.
-- Requiere SGI_RRHH_USE_CANONICAL_EMPLOYEE_ID=false (valor predeterminado).
-- El tenant destino se toma de instance_country_context, el mismo que usa el backend.
-- Se copia sólo el hash UAT y el rol de agente; su tenant/scope antiguos no se copian.
-- No es una migración Flyway. Ejecutar el archivo completo en una sola sesión.
BEGIN;

CREATE TEMP TABLE _adan_alta_parametros ON COMMIT DROP AS
SELECT 5621::bigint AS persona_id,
       'Adan Rufino Bamonde Palma'::varchar(180) AS full_name,
       'adan.bamonde'::varchar(80) AS username;

DO $alta_adan$
DECLARE
  p record;
  v_tenant uuid;
  v_company uuid;
  v_company_count integer;
  v_source_hash varchar(255);
  v_source_roles varchar(500);
  v_hash text;
  v_employee uuid;
  v_existing_count integer;
  v_snapshot employee_operational_snapshot%ROWTYPE;
  v_membership company_membership%ROWTYPE;
  v_user app_user%ROWTYPE;
BEGIN
  SELECT * INTO STRICT p FROM _adan_alta_parametros;
  IF p.persona_id <= 0 THEN
    RAISE EXCEPTION 'Sustituya persona_id=0 por el personas.id real de SIC/RRHH';
  END IF;
  IF p.username !~ '^[a-z][a-z0-9._-]{2,79}$' OR p.username = 'agente' THEN
    RAISE EXCEPTION 'Username inválido';
  END IF;

  SELECT instance_country_id INTO v_tenant
    FROM instance_country_context WHERE singleton_key = 1;
  IF v_tenant IS NULL THEN
    RAISE EXCEPTION 'Falta instance_country_context: el backend no tiene tenant vigente';
  END IF;

  SELECT password_hash, roles
    INTO v_source_hash, v_source_roles
    FROM app_user WHERE username = 'agente' AND active;
  IF NOT FOUND OR v_source_roles <> 'AGENTE_SEGURIDAD' THEN
    RAISE EXCEPTION 'Falta agente activo con rol AGENTE_SEGURIDAD';
  END IF;

  SELECT count(*) INTO v_company_count FROM company
   WHERE instance_country_id = v_tenant
     AND lower(btrim(name)) = 'galvarino' AND status = 'ACTIVE';
  IF v_company_count <> 1 THEN
    RAISE EXCEPTION 'Se esperaba exactamente una compañía Galvarino activa: %', v_company_count;
  END IF;
  SELECT id INTO v_company FROM company
   WHERE instance_country_id = v_tenant
     AND lower(btrim(name)) = 'galvarino' AND status = 'ACTIVE';
  -- Reproduce Java UUID.nameUUIDFromBytes("SIC_RRHH|tenant|PERSONAS|personaId").
  v_hash := md5('SIC_RRHH|' || v_tenant::text || '|PERSONAS|' || p.persona_id::text);
  v_employee := (
      substr(v_hash, 1, 8) || '-' || substr(v_hash, 9, 4) || '-3' ||
      substr(v_hash, 14, 3) || '-' ||
      substr('89ab', ((strpos('0123456789abcdef', substr(v_hash, 17, 1)) - 1) % 4) + 1, 1) ||
      substr(v_hash, 18, 3) || '-' || substr(v_hash, 21, 12)
    )::uuid;

  SELECT count(*) INTO v_existing_count FROM employee_operational_snapshot e
   WHERE e.instance_country_id = v_tenant
     AND (e.persona_id = p.persona_id OR e.employee_id = v_employee
          OR lower(btrim(e.full_name)) = lower(btrim(p.full_name)));
  IF v_existing_count > 1 THEN
    RAISE EXCEPTION 'Identidad de empleado ambigua: % registros coincidentes', v_existing_count;
  END IF;
  IF v_existing_count = 1 THEN
    SELECT * INTO v_snapshot FROM employee_operational_snapshot e
     WHERE e.instance_country_id = v_tenant
       AND (e.persona_id = p.persona_id OR e.employee_id = v_employee
            OR lower(btrim(e.full_name)) = lower(btrim(p.full_name))) FOR UPDATE;
    IF v_snapshot.employee_id <> v_employee
       OR v_snapshot.persona_id IS DISTINCT FROM p.persona_id
       OR v_snapshot.company_id <> v_company
       OR lower(btrim(v_snapshot.full_name)) <> lower(btrim(p.full_name))
       OR lower(btrim(v_snapshot.role_code)) <> 'agente de seguridad'
       OR v_snapshot.employment_status <> 'ACTIVE' THEN
      RAISE EXCEPTION 'Existe una ficha con identidad, compañía, rol o estado distinto; revisar antes de continuar';
    END IF;
  ELSE
    INSERT INTO employee_operational_snapshot(
      id, instance_country_id, employee_id, persona_id, company_id, full_name,
      role_code, employment_status, id_score, preferred_shift, required_change,
      photo_key, updated_from_source_at, created_at, updated_at
    ) VALUES (
      gen_random_uuid(), v_tenant, v_employee, p.persona_id, v_company, p.full_name,
      'Agente de Seguridad', 'ACTIVE', NULL, NULL, false,
      NULL, now(), now(), now()
    );
  END IF;

  SELECT * INTO v_membership FROM company_membership m
   WHERE m.instance_country_id = v_tenant AND m.employee_id = v_employee
     AND m.membership_type = 'PRIMARY' AND m.ends_at IS NULL FOR UPDATE;
  IF FOUND THEN
    IF v_membership.company_id <> v_company
       OR lower(btrim(v_membership.role_code)) <> 'agente de seguridad'
       OR v_membership.starts_at > now() THEN
      RAISE EXCEPTION 'La membresía primaria existente no corresponde a Galvarino/agente vigente';
    END IF;
  ELSE
    INSERT INTO company_membership(
      id, instance_country_id, company_id, employee_id, membership_type,
      role_code, starts_at, ends_at, required_change, created_at, updated_at
    ) VALUES (
      gen_random_uuid(), v_tenant, v_company, v_employee, 'PRIMARY',
      'Agente de Seguridad', now(), NULL, false, now(), now()
    );
  END IF;

  IF EXISTS (
    SELECT 1 FROM app_user u
     WHERE u.instance_country_id = v_tenant AND u.username <> p.username
       AND lower(btrim(u.display_name)) = lower(btrim(p.full_name))
  ) THEN
    RAISE EXCEPTION 'Ya existe otra cuenta con el nombre de Adan';
  END IF;
  SELECT * INTO v_user FROM app_user WHERE username = p.username FOR UPDATE;
  IF FOUND THEN
    IF v_user.instance_country_id <> v_tenant
       OR v_user.roles <> 'AGENTE_SEGURIDAD'
       OR lower(btrim(v_user.display_name)) <> lower(btrim(p.full_name)) THEN
      RAISE EXCEPTION 'El username ya existe con otra identidad, instancia o rol';
    END IF;
    UPDATE app_user SET active = true WHERE username = p.username;
  ELSE
    -- Sólo desarrollo: comparte la credencial UAT de agente hasta asignar una propia.
    INSERT INTO app_user(id, username, password_hash, roles, display_name, instance_country_id, active)
    VALUES (gen_random_uuid(), p.username, v_source_hash, 'AGENTE_SEGURIDAD',
            p.full_name, v_tenant, true);
  END IF;

  IF EXISTS (
    SELECT 1 FROM user_operational_scope s
     WHERE s.instance_country_id = v_tenant AND s.username = p.username
       AND (s.scope_type <> 'COMPANY' OR s.scope_id IS DISTINCT FROM v_company)
  ) THEN
    RAISE EXCEPTION 'La cuenta ya tiene un alcance operacional distinto';
  END IF;
  INSERT INTO user_operational_scope(
    id, instance_country_id, username, scope_type, scope_id, created_at, updated_at
  ) SELECT gen_random_uuid(), v_tenant, p.username, 'COMPANY', v_company, now(), now()
    WHERE NOT EXISTS (
      SELECT 1 FROM user_operational_scope s
       WHERE s.instance_country_id = v_tenant AND s.username = p.username
         AND s.scope_type = 'COMPANY' AND s.scope_id = v_company
    );

  IF EXISTS (
    SELECT 1 FROM operator_employee_binding b
     WHERE b.instance_country_id = v_tenant
       AND ((b.username = p.username AND b.employee_id <> v_employee)
         OR (b.employee_id = v_employee AND b.username <> p.username AND b.active))
  ) THEN
    RAISE EXCEPTION 'Conflicto con otro vínculo activo usuario-empleado';
  END IF;
  INSERT INTO operator_employee_binding(instance_country_id, username, employee_id, active)
  VALUES (v_tenant, p.username, v_employee, true)
  ON CONFLICT (instance_country_id, username) DO UPDATE SET active = true;

  RAISE NOTICE 'Alta de desarrollo lista: %, persona_id %, employee_id %',
    p.username, p.persona_id, v_employee;
END
$alta_adan$;

-- La salida debe ser exactamente una fila antes del COMMIT.
SELECT u.username, u.display_name, u.roles, u.active AS user_active,
       e.persona_id, e.employee_id, e.employment_status,
       c.name AS company_name, m.membership_type, m.ends_at,
       s.scope_type, b.active AS binding_active
FROM _adan_alta_parametros p
JOIN app_user u ON u.username = p.username
JOIN operator_employee_binding b
  ON b.instance_country_id = u.instance_country_id AND b.username = u.username
JOIN employee_operational_snapshot e
  ON e.instance_country_id = b.instance_country_id AND e.employee_id = b.employee_id
JOIN company c ON c.id = e.company_id AND c.instance_country_id = e.instance_country_id
JOIN company_membership m
  ON m.instance_country_id = e.instance_country_id AND m.employee_id = e.employee_id
  AND m.company_id = c.id AND m.membership_type = 'PRIMARY' AND m.ends_at IS NULL
JOIN user_operational_scope s
  ON s.instance_country_id = u.instance_country_id AND s.username = u.username
  AND s.scope_type = 'COMPANY' AND s.scope_id = c.id;

COMMIT;
