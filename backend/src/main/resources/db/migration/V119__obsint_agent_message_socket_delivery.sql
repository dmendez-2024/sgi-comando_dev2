-- OBSINT · Envío real de mensajes al agente por el Socket general (conexión directa, sin Interconexión).
-- Estados del mensaje: TEST (socket apagado), PENDING, SENT (el socket lo aceptó), DELIVERED (la app lo recibió),
-- READ (el agente lo abrió), FAILED (sin agente de turno, rechazado o vencido).
ALTER TABLE obsint_agent_message DROP CONSTRAINT obsint_agent_message_delivery_status_check;
ALTER TABLE obsint_agent_message
    ADD CONSTRAINT obsint_agent_message_delivery_status_check
        CHECK (delivery_status IN ('TEST', 'PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED'));
ALTER TABLE obsint_agent_message ADD COLUMN failure_reason varchar(60);

-- Un mensaje puede ir a varios agentes: el Punto puede tener varios Puestos con agente de turno.
CREATE TABLE obsint_agent_message_recipient (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    message_id uuid NOT NULL REFERENCES obsint_agent_message(id) ON DELETE CASCADE,
    employee_id uuid NOT NULL,
    assignment_id uuid NOT NULL,
    post_id uuid NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED')),
    attempts int NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    last_error varchar(200),
    sent_at timestamptz,
    delivered_at timestamptz,
    read_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_obsint_agent_message_recipient UNIQUE (message_id, employee_id)
);
-- Cola de despacho: solo los pendientes.
CREATE INDEX ix_obsint_msg_recipient_pending ON obsint_agent_message_recipient(next_attempt_at) WHERE status = 'PENDING';
-- Sincronización de la app del agente.
CREATE INDEX ix_obsint_msg_recipient_employee ON obsint_agent_message_recipient(instance_country_id, employee_id, created_at);
