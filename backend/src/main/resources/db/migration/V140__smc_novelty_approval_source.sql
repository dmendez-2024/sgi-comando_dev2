-- Expandir la lista sin modificar las migraciones ya aplicadas ni los cursores existentes.
ALTER TABLE smc_source_cursor DROP CONSTRAINT smc_source_cursor_source_type_check;
ALTER TABLE smc_source_cursor ADD CONSTRAINT smc_source_cursor_source_type_check
CHECK (source_type IN ('PATROL_EXECUTION','CONSIGNMENT_COMPLIANCE','IMPULSE_EVALUATION',
                      'PATROL_DISTANCE','NOVELTY_APPROVAL'));

CREATE INDEX ix_smc_report_approval
ON operator_operational_report_decision(instance_country_id,report_id,created_at,id)
WHERE action='APPROVE' AND resulting_status='APPROVED';

