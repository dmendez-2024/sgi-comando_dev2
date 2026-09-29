-- UAT | Vincula el usuario "agente" a un empleado de Galvarino y lo asigna a los turnos de GGTT01
-- desde ayer hasta 14 días. Idempotente. Requiere planes de asignación de Galvarino para esas semanas.
BEGIN;
INSERT INTO operator_employee_binding(instance_country_id, username, employee_id, active)
SELECT '11111111-1111-1111-1111-111111111111', 'agente', e.employee_id, true
FROM employee_operational_snapshot e
WHERE e.company_id='20000000-0000-0000-0000-000000000001'
ORDER BY e.full_name LIMIT 1
ON CONFLICT (instance_country_id, username) DO NOTHING;

INSERT INTO operational_assignment(id, instance_country_id, assignment_plan_id, shift_occurrence_id, employee_id, status, created_at, updated_at, assigned_by_username, assigned_at)
SELECT gen_random_uuid(), s.instance_country_id, ap.id, s.id, b.employee_id, 'PUBLISHED', now(), now(), 'uat-fixture', now()
FROM shift_occurrence s
JOIN assignment_plan ap ON ap.instance_country_id=s.instance_country_id AND ap.company_id='20000000-0000-0000-0000-000000000001'
  AND ap.week_start=date_trunc('week', s.starts_at)::date
JOIN operator_employee_binding b ON b.instance_country_id=s.instance_country_id AND b.username='agente'
WHERE s.post_id='50000000-0000-0000-0000-000000000001'
  AND s.ends_at >= now() - interval '1 day' AND s.starts_at <= now() + interval '14 days'
  AND NOT EXISTS (SELECT 1 FROM operational_assignment a WHERE a.shift_occurrence_id=s.id AND a.status<>'REMOVED');
COMMIT;
