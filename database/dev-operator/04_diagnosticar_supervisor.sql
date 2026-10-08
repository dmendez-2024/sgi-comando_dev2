-- Solo lectura. Ejecutar en la base PostgreSQL utilizada por SGI Comando.
-- No muestra contraseñas ni hashes. Cambiar el username si corresponde.
SELECT current_database() AS database_name,
       ctx.instance_country_id AS backend_tenant,
       u.username, u.display_name, u.roles, u.active,
       u.instance_country_id AS user_tenant,
       u.instance_country_id = ctx.instance_country_id AS tenant_correcto,
       'SUPERVISOR_SEGURIDAD' = ANY(regexp_split_to_array(u.roles, '\s*,\s*')) AS rol_correcto,
       b.employee_id, b.active AS binding_active,
       e.persona_id, e.full_name, e.role_code, e.employment_status,
       c.name AS company_name,
       EXISTS (SELECT 1 FROM company_membership m
         WHERE m.instance_country_id = ctx.instance_country_id
           AND m.employee_id = b.employee_id AND m.company_id = e.company_id
           AND m.membership_type = 'PRIMARY' AND m.starts_at <= now()
           AND (m.ends_at IS NULL OR m.ends_at > now())) AS membresia_vigente,
       EXISTS (SELECT 1 FROM user_operational_scope s
         WHERE s.instance_country_id = ctx.instance_country_id AND s.username = u.username
           AND s.scope_type = 'COMPANY' AND s.scope_id = e.company_id) AS alcance_compania
FROM instance_country_context ctx
LEFT JOIN app_user u ON u.username = 'supervisor'
LEFT JOIN operator_employee_binding b
  ON b.instance_country_id = ctx.instance_country_id AND b.username = u.username
LEFT JOIN employee_operational_snapshot e
  ON e.instance_country_id = b.instance_country_id AND e.employee_id = b.employee_id
LEFT JOIN company c ON c.id = e.company_id AND c.instance_country_id = e.instance_country_id
WHERE ctx.singleton_key = 1;

-- Candidatos: elegir la persona real; no vincular automáticamente al primero.
SELECT e.employee_id, e.persona_id, e.full_name, e.role_code,
       e.employment_status, c.name AS company_name,
       b.username AS linked_username, b.active AS binding_active
FROM employee_operational_snapshot e
JOIN instance_country_context ctx ON ctx.singleton_key = 1
  AND ctx.instance_country_id = e.instance_country_id
JOIN company c ON c.id = e.company_id AND c.instance_country_id = e.instance_country_id
LEFT JOIN operator_employee_binding b
  ON b.instance_country_id = e.instance_country_id AND b.employee_id = e.employee_id
WHERE lower(btrim(e.role_code)) IN
  ('supervisor_seguridad', 'supervisor de seguridad', 'supervisor de seguridad cl')
ORDER BY c.name, e.full_name;

-- Persona solicitada: permite comprobar su existencia aunque el cargo esté mal configurado.
SELECT e.employee_id, e.persona_id, e.full_name, e.role_code,
       e.employment_status, e.instance_country_id, c.name AS company_name
FROM employee_operational_snapshot e
JOIN company c ON c.id = e.company_id AND c.instance_country_id = e.instance_country_id
WHERE lower(btrim(e.full_name)) = lower('Ciro Antonio Chavez Molina');

-- Alcances existentes, incluidos registros que pertenecen a otro tenant.
SELECT s.instance_country_id, s.username, s.scope_type, s.scope_id, c.name AS company_name
FROM user_operational_scope s
LEFT JOIN company c ON c.id = s.scope_id AND c.instance_country_id = s.instance_country_id
WHERE s.username = 'supervisor'
ORDER BY s.instance_country_id, s.scope_type;
