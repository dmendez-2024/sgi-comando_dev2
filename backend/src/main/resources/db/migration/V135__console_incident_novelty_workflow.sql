-- Los incidentes creados desde Consola no nacen de una asignacion de SGI Operador.
-- La fuente conserva la regla: solo SGI_COMANDO puede omitir asignacion y empleado.
ALTER TABLE operator_operational_report
    ALTER COLUMN assignment_id DROP NOT NULL,
    ALTER COLUMN employee_id DROP NOT NULL,
    ALTER COLUMN username TYPE varchar(160),
    ALTER COLUMN category TYPE varchar(120),
    ALTER COLUMN subcategory TYPE varchar(160),
    ADD COLUMN reporter_persona_id bigint;

UPDATE operator_operational_report report
   SET reporter_persona_id = employee.persona_id
  FROM employee_operational_snapshot employee
 WHERE report.reporter_persona_id IS NULL
   AND report.employee_id = employee.employee_id
   AND report.instance_country_id = employee.instance_country_id;

ALTER TABLE operator_operational_report
    ADD CONSTRAINT ck_operator_report_origin_context
    CHECK (
        source = 'SGI_COMANDO'
        OR (assignment_id IS NOT NULL AND employee_id IS NOT NULL)
    );

CREATE INDEX ix_operator_report_reporter_persona
    ON operator_operational_report(instance_country_id, reporter_persona_id)
    WHERE reporter_persona_id IS NOT NULL;

COMMENT ON COLUMN operator_operational_report.reporter_persona_id IS
    'persona_id de quien registro la novedad; permite consultar en DHO el correo owner enviado a STC.';
