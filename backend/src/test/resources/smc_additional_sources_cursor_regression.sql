\set ON_ERROR_STOP on
-- Ejecutar SOLO en una base vacía y aislada. Usa las migraciones reales.
BEGIN;
CREATE TABLE patrol_execution(instance_country_id uuid,updated_at timestamptz,id uuid,finished_at timestamptz,result text,employee_id uuid);
CREATE TABLE consignment_compliance(instance_country_id uuid,created_at timestamptz,id uuid,result text,employee_id uuid);
CREATE TABLE impulse_evaluation(instance_country_id uuid,evaluated_at timestamptz,id uuid,decision text);
\ir ../../main/resources/db/migration/V93__smc_incremental_source_cursor.sql
\ir ../../main/resources/db/migration/V122__smc_impulse_source.sql
INSERT INTO smc_source_cursor(instance_country_id,source_type,changed_at,last_source_id)
SELECT '00000000-0000-0000-0000-000000000001',t,'2026-11-10T12:00:00Z','00000000-0000-0000-0000-000000000002'
FROM unnest(ARRAY['PATROL_EXECUTION','CONSIGNMENT_COMPLIANCE','IMPULSE_EVALUATION']) AS t;
DO $$ BEGIN
    BEGIN
        INSERT INTO smc_source_cursor(instance_country_id,source_type,changed_at)
        VALUES('00000000-0000-0000-0000-000000000001','PATROL_DISTANCE',current_timestamp);
        RAISE EXCEPTION 'El esquema antiguo debió rechazar PATROL_DISTANCE';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: reproducido SQLState 23514 antes de V139';
    END;
END $$;
\ir ../../main/resources/db/migration/V139__smc_patrol_distance_cursor.sql
INSERT INTO smc_source_cursor(instance_country_id,source_type,changed_at)
VALUES('00000000-0000-0000-0000-000000000001','PATROL_DISTANCE',current_timestamp);
DO $$ BEGIN
    IF (SELECT count(*) FROM smc_source_cursor)<>4 THEN RAISE EXCEPTION 'Deben coexistir cuatro fuentes'; END IF;
    IF (SELECT count(*) FROM smc_source_cursor WHERE source_type<>'PATROL_DISTANCE'
        AND changed_at='2026-11-10T12:00:00Z' AND last_source_id='00000000-0000-0000-0000-000000000002')<>3
        THEN RAISE EXCEPTION 'Se modificaron los cursores existentes'; END IF;
    BEGIN
        INSERT INTO smc_source_cursor(instance_country_id,source_type,changed_at)
        VALUES('00000000-0000-0000-0000-000000000001','UNKNOWN_SOURCE',current_timestamp);
        RAISE EXCEPTION 'Las fuentes desconocidas deben seguir rechazándose';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    RAISE NOTICE 'PASS: cuatro fuentes admitidas, cursores conservados, fuente desconocida rechazada';
END $$;
CREATE TABLE operator_operational_report_decision(instance_country_id uuid,report_id uuid,created_at timestamptz,id uuid,action text,resulting_status text);
\ir ../../main/resources/db/migration/V140__smc_novelty_approval_source.sql
INSERT INTO smc_source_cursor(instance_country_id,source_type,changed_at)
SELECT '00000000-0000-0000-0000-000000000001',t,current_timestamp
FROM unnest(ARRAY['NOVELTY_APPROVAL']) AS t;
DO $$ BEGIN
    IF (SELECT count(*) FROM smc_source_cursor)<>5 THEN RAISE EXCEPTION 'Deben coexistir cinco fuentes'; END IF;
    IF (SELECT count(*) FROM smc_source_cursor WHERE source_type IN ('PATROL_EXECUTION','CONSIGNMENT_COMPLIANCE','IMPULSE_EVALUATION')
        AND changed_at='2026-11-10T12:00:00Z' AND last_source_id='00000000-0000-0000-0000-000000000002')<>3
        THEN RAISE EXCEPTION 'V140 modificó cursores existentes'; END IF;
    BEGIN
        INSERT INTO smc_source_cursor(instance_country_id,source_type,changed_at)
        VALUES('00000000-0000-0000-0000-000000000001','UNKNOWN_SOURCE',current_timestamp);
        RAISE EXCEPTION 'Las fuentes desconocidas deben seguir rechazándose';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    RAISE NOTICE 'PASS: cinco fuentes admitidas y cursores conservados tras V140';
END $$;
ROLLBACK;
