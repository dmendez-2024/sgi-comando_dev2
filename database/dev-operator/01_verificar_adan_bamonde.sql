-- Solo lectura. Ejecutar en la base de desarrollo de SGI Comando.
-- 1) Diagnóstico: esta consulta siempre devuelve una fila y muestra qué filtro falla.
SELECT current_database() AS database_name,
       (SELECT count(*) FROM app_user WHERE username = 'agente') AS source_users,
       (SELECT count(*) FROM company WHERE lower(btrim(name)) = 'galvarino') AS galvarino_companies,
       (SELECT count(*) FROM company c JOIN app_user u
          ON u.instance_country_id = c.instance_country_id
         WHERE u.username = 'agente' AND lower(btrim(c.name)) = 'galvarino'
           AND c.status = 'ACTIVE') AS active_galvarino_in_source_tenant,
       (SELECT count(*) FROM employee_operational_snapshot e
         WHERE e.full_name ILIKE '%Bamonde%') AS bamonde_snapshots_any_company,
       (SELECT count(*) FROM employee_operational_snapshot e
         JOIN company c ON c.id = e.company_id AND c.instance_country_id = e.instance_country_id
         JOIN app_user u ON u.instance_country_id = e.instance_country_id
         WHERE u.username = 'agente' AND lower(btrim(c.name)) = 'galvarino'
           AND e.full_name ILIKE '%Bamonde%') AS bamonde_snapshots_in_galvarino;

-- 2) Coincidencias por cualquier parte distintiva del nombre, incluso si
--    el apellido, estado o compañía difieren.
SELECT e.employee_id, e.persona_id, e.full_name, e.role_code,
       e.employment_status, c.code AS company_code, c.name AS company_name,
       c.status AS company_status, e.instance_country_id,
       lower(translate(regexp_replace(btrim(e.full_name), '\s+', ' ', 'g'),
         'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')) =
         'adan rufino bamonde palma' AS matches_exact_name,
       e.instance_country_id =
         (SELECT u.instance_country_id FROM app_user u WHERE u.username = 'agente')
         AS same_source_tenant,
       lower(btrim(c.name)) = 'galvarino' AND c.status = 'ACTIVE' AS active_galvarino,
       e.employment_status = 'ACTIVE' AS active_employee,
       lower(btrim(e.role_code)) = 'agente de seguridad' AS agent_role,
       EXISTS (
         SELECT 1 FROM company_membership m
         WHERE m.instance_country_id = e.instance_country_id
           AND m.employee_id = e.employee_id AND m.company_id = e.company_id
           AND m.membership_type = 'PRIMARY' AND m.ends_at IS NULL
           AND m.starts_at <= now()
       ) AS primary_membership_active
FROM employee_operational_snapshot e
LEFT JOIN company c ON c.id = e.company_id AND c.instance_country_id = e.instance_country_id
WHERE e.full_name ILIKE '%Bamonde%'
   OR e.full_name ILIKE '%Adan%'
   OR e.full_name ILIKE '%Adán%'
   OR e.full_name ILIKE '%Palma%'
ORDER BY e.full_name;

-- 3) Posibles cuentas existentes con ese apellido.
SELECT u.username, u.display_name, u.roles, u.active, u.instance_country_id,
       b.employee_id AS bound_employee_id, b.active AS binding_active
FROM app_user u
LEFT JOIN operator_employee_binding b
  ON b.instance_country_id = u.instance_country_id AND b.username = u.username
WHERE u.display_name ILIKE '%Bamonde%' OR u.username ILIKE '%bamonde%'
ORDER BY u.username;

-- 4) Resultado exacto requerido por el script de alta: debe aparecer una fila.
WITH origen AS (
  SELECT username, instance_country_id, roles, active
  FROM app_user
  WHERE username = 'agente'
), galvarino AS (
  SELECT c.id, c.instance_country_id, c.code, c.name
  FROM company c
  JOIN origen o ON o.instance_country_id = c.instance_country_id
  WHERE lower(btrim(c.name)) = 'galvarino' AND c.status = 'ACTIVE'
), persona AS (
  SELECT e.employee_id, e.persona_id, e.full_name, e.role_code,
         e.employment_status, e.instance_country_id, e.company_id
  FROM employee_operational_snapshot e
  JOIN galvarino c ON c.id = e.company_id AND c.instance_country_id = e.instance_country_id
  WHERE lower(translate(regexp_replace(btrim(e.full_name), '\s+', ' ', 'g'),
        'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')) = 'adan rufino bamonde palma'
)
SELECT p.employee_id, p.persona_id, p.full_name, p.role_code, p.employment_status,
       c.code AS company_code, c.name AS company_name,
       EXISTS (
         SELECT 1 FROM company_membership m
         WHERE m.instance_country_id = p.instance_country_id
           AND m.employee_id = p.employee_id AND m.company_id = c.id
           AND m.membership_type = 'PRIMARY' AND m.ends_at IS NULL
           AND m.starts_at <= now()
       ) AS primary_membership_active,
       (SELECT string_agg(b.username, ', ' ORDER BY b.username)
        FROM operator_employee_binding b
        WHERE b.instance_country_id = p.instance_country_id
          AND b.employee_id = p.employee_id AND b.active) AS current_operator_logins,
       (SELECT string_agg(u.username, ', ' ORDER BY u.username)
        FROM app_user u
        WHERE u.instance_country_id = p.instance_country_id
          AND lower(translate(regexp_replace(btrim(u.display_name), '\s+', ' ', 'g'),
              'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')) =
              lower(translate(regexp_replace(btrim(p.full_name), '\s+', ' ', 'g'),
              'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun'))
       ) AS existing_accounts_with_same_name
FROM persona p
JOIN galvarino c ON c.id = p.company_id
ORDER BY p.full_name;

-- Comprueba que la cuenta UAT de origen tiene el rol y el alcance requeridos.
SELECT u.username, u.roles, u.active, c.code AS scope_company_code, c.name AS scope_company_name
FROM app_user u
LEFT JOIN user_operational_scope s
  ON s.instance_country_id = u.instance_country_id AND s.username = u.username
  AND s.scope_type = 'COMPANY'
LEFT JOIN company c ON c.id = s.scope_id AND c.instance_country_id = u.instance_country_id
WHERE u.username = 'agente';
