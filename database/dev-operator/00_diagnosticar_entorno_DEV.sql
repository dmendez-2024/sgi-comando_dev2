-- Ejecutar en la MISMA conexión de DBeaver donde falló el alta.
-- Una transacción PostgreSQL abortada debe cerrarse antes de cualquier SELECT.
ROLLBACK;

SELECT current_database() AS database_name,
       current_user AS database_user,
       inet_server_addr() AS server_address,
       inet_server_port() AS server_port,
       (SELECT instance_country_id FROM instance_country_context WHERE singleton_key = 1)
         AS backend_instance_country_id,
       (SELECT instance_country_id FROM app_user WHERE username = 'agente') AS agente_instance_country_id,
       (SELECT count(*) FROM company c JOIN app_user u
          ON u.instance_country_id = c.instance_country_id
         WHERE u.username = 'agente'
           AND lower(btrim(c.name)) = 'galvarino'
           AND c.status = 'ACTIVE') AS active_galvarino_in_agente_tenant;

-- Muestra el alcance real de agente y el estado de sus compañías.
SELECT u.username, u.active AS user_active, u.instance_country_id,
       s.scope_type, s.scope_id,
       c.code AS company_code, c.name AS company_name, c.status AS company_status
FROM app_user u
LEFT JOIN user_operational_scope s
  ON s.instance_country_id = u.instance_country_id AND s.username = u.username
LEFT JOIN company c
  ON c.id = s.scope_id AND c.instance_country_id = u.instance_country_id
WHERE u.username = 'agente'
ORDER BY s.scope_type, c.name;

-- Busca compañías similares en cualquier instancia de esta base.
SELECT id, instance_country_id, code, name, status
FROM company
WHERE name ILIKE '%galvarino%' OR code = 'COM-001'
ORDER BY instance_country_id, name;

SELECT instance_country_id,
       count(*) AS user_count,
       count(*) FILTER (WHERE active) AS active_user_count
FROM app_user GROUP BY instance_country_id ORDER BY instance_country_id;
