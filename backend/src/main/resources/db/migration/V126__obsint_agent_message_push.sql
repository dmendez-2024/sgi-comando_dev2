-- Push FCM a SGI Operador (app en segundo plano o cerrada). Se envía una vez por destinatario cuando el gateway del
-- socket acepta la publicación; es de mejor esfuerzo y no cambia el estado del destinatario, solo queda auditado aquí.
ALTER TABLE obsint_agent_message_recipient
    ADD COLUMN push_sent_at timestamptz,
    ADD COLUMN push_error varchar(120);
