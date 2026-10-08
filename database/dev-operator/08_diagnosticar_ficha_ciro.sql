-- Solo lectura. Si está en 25P02, ejecutar ROLLBACK por separado primero.
SELECT current_database() AS database_name, instance_country_id AS backend_tenant
FROM instance_country_context WHERE singleton_key = 1;

-- Busca en todos los tenants y sin exigir coincidencia exacta de nombre/cargo.
SELECT e.employee_id, e.persona_id, e.full_name, e.role_code, e.employment_status,
       e.instance_country_id AS employee_tenant,
       e.instance_country_id = ctx.instance_country_id AS tenant_correcto,
       c.name AS company_name, c.instance_country_id AS company_tenant,
       b.username AS linked_username, b.active AS binding_active
FROM employee_operational_snapshot e
CROSS JOIN instance_country_context ctx
LEFT JOIN company c ON c.id = e.company_id
LEFT JOIN operator_employee_binding b
  ON b.instance_country_id = e.instance_country_id AND b.employee_id = e.employee_id
WHERE ctx.singleton_key = 1 AND
  (e.persona_id IN (7008, 3286) OR lower(e.full_name) LIKE '%ciro%'
    OR lower(translate(e.full_name, 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')) LIKE '%chavez%molina%')
ORDER BY e.instance_country_id, e.full_name;
