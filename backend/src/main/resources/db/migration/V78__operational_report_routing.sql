ALTER TABLE operator_operational_report
    ADD COLUMN payload_hash varchar(64),
    ADD COLUMN routing_target varchar(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN routing_status varchar(32) NOT NULL DEFAULT 'NOT_APPLICABLE',
    ADD COLUMN routing_reference_id varchar(160),
    ADD COLUMN photo_count integer NOT NULL DEFAULT 0 CHECK (photo_count BETWEEN 0 AND 1);

COMMENT ON COLUMN operator_operational_report.routing_target IS
    'Destino futuro: STC para incidentes y vulnerabilidades, NONE para hallazgos.';
COMMENT ON COLUMN operator_operational_report.routing_status IS
    'PENDING_INTEGRATION deja trazabilidad sin ejecutar envíos externos hasta que CORE publique el contrato.';
