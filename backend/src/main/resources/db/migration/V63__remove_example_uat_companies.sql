-- Remove the example companies introduced by V18 while preserving Kaibil.
-- Employees attached to the example companies are kept and moved to Kaibil.
-- Only empty, unpublished UAT assignment plans can be removed by this migration.

CREATE TEMP TABLE uat_example_company_targets ON COMMIT DROP AS
WITH expected(core_company_id, code, name) AS (
    VALUES
        ('a2000000-0000-0000-0000-000000000002'::uuid, 'COM-002', 'Atahualpa'),
        ('a2000000-0000-0000-0000-000000000003'::uuid, 'COM-003', 'Lanceros'),
        ('a2000000-0000-0000-0000-000000000004'::uuid, 'COM-004', 'Cóndor'),
        ('a2000000-0000-0000-0000-000000000006'::uuid, 'COM-006', 'Andes')
)
SELECT c.id AS company_id,
       c.instance_country_id,
       c.core_catalog_id,
       c.code,
       c.name
FROM expected e
JOIN company c
  ON c.core_catalog_id = e.core_company_id
 AND c.code = e.code
 AND c.name = e.name
 AND c.source_system = 'CORE'
 AND c.source_version = 'CORE-UAT-2026.09';

CREATE TEMP TABLE uat_company_reassignment ON COMMIT DROP AS
SELECT target.company_id,
       kaibil.id AS kaibil_company_id,
       target.instance_country_id
FROM uat_example_company_targets target
JOIN company kaibil
  ON kaibil.instance_country_id = target.instance_country_id
 AND kaibil.core_catalog_id = 'a2000000-0000-0000-0000-000000000005'::uuid
 AND kaibil.code = 'KAI-001'
 AND kaibil.name = 'Kaibil';

DO $migration$
DECLARE
    target_count integer;
    reassignment_count integer;
    invalid_plan_count bigint;
    plan_reference_count bigint;
BEGIN
    SELECT count(*) INTO target_count
    FROM uat_example_company_targets;

    SELECT count(*) INTO reassignment_count
    FROM uat_company_reassignment;

    IF reassignment_count <> target_count THEN
        RAISE EXCEPTION
            'UAT company cleanup cancelled: Kaibil was not found for every target tenant (targets=%, mappings=%).',
            target_count, reassignment_count;
    END IF;

    SELECT count(*) INTO invalid_plan_count
    FROM assignment_plan plan
    JOIN uat_example_company_targets target ON target.company_id = plan.company_id
    WHERE plan.status <> 'DRAFT'
       OR plan.published_at IS NOT NULL;

    SELECT count(*) INTO plan_reference_count
    FROM assignment_plan plan
    JOIN uat_example_company_targets target ON target.company_id = plan.company_id
    WHERE EXISTS (
              SELECT 1
              FROM operational_assignment assignment
              WHERE assignment.assignment_plan_id = plan.id
          )
       OR EXISTS (
              SELECT 1
              FROM assignment_event event
              WHERE event.assignment_plan_id = plan.id
          );

    IF invalid_plan_count > 0 OR plan_reference_count > 0 THEN
        RAISE EXCEPTION
            'UAT company cleanup cancelled: plans must be unpublished DRAFT records without assignments or events (invalid=%, referenced=%).',
            invalid_plan_count, plan_reference_count;
    END IF;
END
$migration$;

-- Preserve employee data by assigning employees and all their membership history
-- from the example company to Kaibil in the same instance-country.
UPDATE employee_operational_snapshot employee
SET company_id = mapping.kaibil_company_id,
    updated_at = now()
FROM uat_company_reassignment mapping
WHERE employee.company_id = mapping.company_id;

UPDATE company_membership membership
SET company_id = mapping.kaibil_company_id,
    updated_at = now()
FROM uat_company_reassignment mapping
WHERE membership.company_id = mapping.company_id;

-- These plans are generated UAT drafts and were verified above as unpublished
-- and empty. Removing them avoids collisions because all employees now use Kaibil.
DELETE FROM assignment_plan plan
USING uat_example_company_targets target
WHERE plan.company_id = target.company_id;

-- Remove non-FK scopes that would otherwise point to a company that no longer exists.
DELETE FROM user_operational_scope scope
USING uat_example_company_targets target
WHERE scope.scope_id = target.company_id;

DELETE FROM access_list_entry entry
USING uat_example_company_targets target
WHERE upper(entry.scope_type) = 'COMPANY'
  AND entry.scope_id = target.company_id;

-- Abort before deleting companies if any operational or future FK still references
-- one of them. Configuration regions and version snapshots are removed afterwards.
DO $migration$
DECLARE
    fk record;
    reference_count bigint;
    blockers text := '';
BEGIN
    FOR fk IN
        SELECT constraint_row.conrelid::regclass AS table_name,
               attribute_row.attname AS column_name,
               cardinality(constraint_row.conkey) AS column_count
        FROM pg_constraint constraint_row
        JOIN pg_attribute attribute_row
          ON attribute_row.attrelid = constraint_row.conrelid
         AND attribute_row.attnum = constraint_row.conkey[1]
        WHERE constraint_row.contype = 'f'
          AND constraint_row.confrelid = 'company'::regclass
        ORDER BY constraint_row.conrelid::regclass::text, attribute_row.attname
    LOOP
        IF fk.column_count <> 1 THEN
            RAISE EXCEPTION 'Composite FK to company is not supported: %', fk.table_name;
        END IF;

        IF fk.table_name IN ('company_region'::regclass, 'company_version'::regclass) THEN
            CONTINUE;
        END IF;

        EXECUTE format(
            'SELECT count(*) FROM %s WHERE %I IN (SELECT company_id FROM uat_example_company_targets)',
            fk.table_name,
            fk.column_name
        ) INTO reference_count;

        IF reference_count > 0 THEN
            blockers := blockers || format('%s.%I=%s; ', fk.table_name, fk.column_name, reference_count);
        END IF;
    END LOOP;

    IF blockers <> '' THEN
        RAISE EXCEPTION
            'UAT company cleanup cancelled because operational references remain: %',
            blockers;
    END IF;
END
$migration$;

DELETE FROM company_region region
USING uat_example_company_targets target
WHERE region.company_id = target.company_id;

DELETE FROM company_version version
USING uat_example_company_targets target
WHERE version.company_id = target.company_id;

DELETE FROM company company_row
USING uat_example_company_targets target
WHERE company_row.id = target.company_id;

-- Delete only the original UAT catalog snapshots. A snapshot already refreshed by
-- the real CORE integration has another source_version and is preserved.
DELETE FROM core_company_catalog_snapshot snapshot
USING (VALUES
    ('a2000000-0000-0000-0000-000000000002'::uuid, 'COM-002'),
    ('a2000000-0000-0000-0000-000000000003'::uuid, 'COM-003'),
    ('a2000000-0000-0000-0000-000000000004'::uuid, 'COM-004'),
    ('a2000000-0000-0000-0000-000000000006'::uuid, 'COM-006')
) AS expected(core_company_id, code)
WHERE snapshot.core_company_id = expected.core_company_id
  AND snapshot.code = expected.code
  AND snapshot.source_version = 'CORE-UAT-2026.09';
