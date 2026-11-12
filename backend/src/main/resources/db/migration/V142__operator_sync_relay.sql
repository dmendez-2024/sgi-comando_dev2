-- SGI Operador: un solo dispositivo por punto. Si un agente terminó su turno con registros sin sincronizar, el
-- siguiente agente del mismo punto los envía desde ese dispositivo. Cada registro queda a nombre de quien lo hizo
-- (empleado de la asignación); aquí queda quién lo sincronizó.
CREATE TABLE operator_sync_relay (
    instance_country_id uuid NOT NULL,
    operation varchar(60) NOT NULL,
    reference_id varchar(160) NOT NULL,
    assignment_id uuid NOT NULL,
    author_employee_id uuid NOT NULL,
    author_username varchar(160) NOT NULL,
    relayed_by_employee_id uuid NOT NULL,
    relayed_by_username varchar(160) NOT NULL,
    relayed_at timestamptz NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (instance_country_id, operation, reference_id)
);
CREATE INDEX ix_operator_sync_relay_assignment ON operator_sync_relay(instance_country_id, assignment_id);
CREATE INDEX ix_operator_sync_relay_relayer ON operator_sync_relay(instance_country_id, relayed_by_employee_id, relayed_at);
