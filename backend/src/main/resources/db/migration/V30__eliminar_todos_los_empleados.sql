-- SGI: Comando | Flyway V30 quarantine tombstone
-- P0 safety correction 2026-09-27.
--
-- The original V30 was a DESTRUCTIVE UAT data-reset script and MUST NOT run
-- automatically as a schema migration. Its exact historical SQL is preserved at:
--   database/uat-fixtures/legacy-original-flyway/V30__eliminar_todos_los_empleados.sql
-- and the explicit UAT fixture is:
--   database/uat-fixtures/DME_01_eliminar_todos_los_empleados_UAT.sql
--
-- Fresh databases safely record V30 as applied without deleting any business data.
-- Existing databases that already applied the former V30 will have a checksum
-- mismatch: follow docs/MIGRATION_SAFETY_V30_V31.md before starting this RC.

BEGIN;

LOCK TABLE
    employee_operational_snapshot,
    employee_skill_snapshot,
    employee_unavailability_snapshot,
    employee_company_transfer,
    company_membership,
    company,
    territory_zone,
    territory_region,
    operational_assignment,
    vulnerability_confirmation,
    patrol_execution
IN SHARE ROW EXCLUSIVE MODE;

DO $dme$
DECLARE
    assignment_count bigint;
    confirmation_count bigint;
    patrol_count bigint;
BEGIN
    SELECT count(*)
      INTO assignment_count
      FROM operational_assignment
     WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
       AND (employee_id IS NOT NULL OR actual_employee_id IS NOT NULL);

    SELECT count(*)
      INTO confirmation_count
      FROM vulnerability_confirmation
     WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid;

    SELECT count(*)
      INTO patrol_count
      FROM patrol_execution
     WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid;

    IF assignment_count > 0 OR confirmation_count > 0 OR patrol_count > 0 THEN
        RAISE EXCEPTION
            'Eliminación cancelada: existen referencias históricas (asignaciones=%, confirmaciones=%, patrullas=%). No se modificó ningún dato.',
            assignment_count, confirmation_count, patrol_count;
    END IF;
END
$dme$;

UPDATE company
   SET responsible_employee_id = NULL,
       updated_at = now()
 WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
   AND responsible_employee_id IS NOT NULL;

UPDATE territory_zone
   SET responsible_employee_id = NULL,
       updated_at = now()
 WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
   AND responsible_employee_id IS NOT NULL;

UPDATE territory_region
   SET responsible_employee_id = NULL,
       updated_at = now()
 WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
   AND responsible_employee_id IS NOT NULL;

DELETE FROM employee_company_transfer
 WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid;

DELETE FROM employee_unavailability_snapshot
 WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid;

DELETE FROM employee_skill_snapshot
 WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid;

DELETE FROM company_membership
 WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid;

DELETE FROM employee_operational_snapshot
 WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid;

DO $dme$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM employee_operational_snapshot
         WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
    ) OR EXISTS (
        SELECT 1
          FROM company_membership
         WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
    ) OR EXISTS (
        SELECT 1
          FROM employee_skill_snapshot
         WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
    ) OR EXISTS (
        SELECT 1
          FROM employee_unavailability_snapshot
         WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
    ) OR EXISTS (
        SELECT 1
          FROM employee_company_transfer
         WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid
    ) THEN
        RAISE EXCEPTION 'La validación posterior detectó información de empleados sin eliminar.';
    END IF;
END
$dme$;

SELECT
    (SELECT count(*) FROM employee_operational_snapshot WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid) AS snapshots_restantes,
    (SELECT count(*) FROM company_membership WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid) AS membresias_restantes,
    (SELECT count(*) FROM employee_skill_snapshot WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid) AS habilidades_restantes,
    (SELECT count(*) FROM employee_unavailability_snapshot WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid) AS indisponibilidades_restantes,
    (SELECT count(*) FROM employee_company_transfer WHERE instance_country_id = '11111111-1111-1111-1111-111111111111'::uuid) AS transferencias_restantes;

COMMIT;
