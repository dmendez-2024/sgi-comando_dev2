-- Socket real (cm-socket): canal por persona, envío 1 a 1 / 1 a muchos y recibos auditables por agente.
ALTER TABLE obsint_agent_message
    ADD COLUMN target varchar(10) NOT NULL DEFAULT 'POINT' CHECK (target IN ('POINT', 'AGENTS'));

ALTER TABLE obsint_agent_message_recipient
    ADD COLUMN persona_id bigint,
    ADD COLUMN local_recipients int,
    ADD COLUMN delivered_via varchar(20) CHECK (delivered_via IN ('SOCKET_ACK', 'OPERATOR_API')),
    ADD COLUMN read_via varchar(20) CHECK (read_via IN ('SOCKET_ACK', 'OPERATOR_API')),
    ADD COLUMN app_rejected_at timestamptz;

ALTER TABLE obsint_agent_message_recipient ADD COLUMN full_name varchar(200), ADD COLUMN post_name varchar(200);
