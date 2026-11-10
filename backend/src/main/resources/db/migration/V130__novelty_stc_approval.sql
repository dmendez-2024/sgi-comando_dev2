ALTER TABLE incident_notification
    ADD COLUMN operational_report_id uuid REFERENCES operator_operational_report(id);

CREATE UNIQUE INDEX uq_incident_notification_operational_report
    ON incident_notification(instance_country_id, operational_report_id)
    WHERE operational_report_id IS NOT NULL;

-- Compatibilidad con borradores que ya guardaron la relación dentro del JSON.
UPDATE incident_notification incident
   SET operational_report_id = (incident.payload_json::jsonb ->> 'operationalReportId')::uuid
 WHERE incident.operational_report_id IS NULL
   AND incident.payload_json::jsonb ->> 'operationalReportId'
       ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
   AND EXISTS (
       SELECT 1
         FROM operator_operational_report report
        WHERE report.id = (incident.payload_json::jsonb ->> 'operationalReportId')::uuid
          AND report.instance_country_id = incident.instance_country_id
          AND report.report_type = 'INCIDENT'
   );

ALTER TABLE operator_operational_report
    ADD COLUMN stc_start_date timestamptz,
    ADD COLUMN stc_end_date timestamptz,
    ADD COLUMN stc_attempts smallint,
    ADD CONSTRAINT ck_operator_report_stc_attempts
        CHECK (stc_attempts IS NULL OR stc_attempts BETWEEN 1 AND 2);

CREATE UNIQUE INDEX uq_operator_report_stc_task
    ON operator_operational_report(instance_country_id, routing_reference_id)
    WHERE routing_target = 'STC' AND routing_reference_id IS NOT NULL;

COMMENT ON COLUMN operator_operational_report.routing_reference_id IS
    'Primer código retornado por STC en taskCodeList; se usa para correlacionar la finalización.';
COMMENT ON COLUMN operator_operational_report.routing_target IS
    'Destino de integración; hallazgos, vulnerabilidades e incidentes aprobados se envían a STC.';
COMMENT ON COLUMN operator_operational_report.routing_status IS
    'PENDING_INTEGRATION hasta aprobar; CREATED cuando STC confirma y devuelve taskCodeList.';
COMMENT ON COLUMN operator_operational_report.stc_start_date IS
    'Instante ISO de aprobación enviado a STC como createDate y startDate.';
COMMENT ON COLUMN operator_operational_report.stc_end_date IS
    'Fin del domingo de la semana ISO de aprobación en America/Guayaquil, almacenado como timestamptz.';
