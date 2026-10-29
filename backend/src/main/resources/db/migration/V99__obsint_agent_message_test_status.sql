-- OBSINT · Mensajes al agente: el envío real a SGI Operador aún no existe, así que se registran como TEST (modo prueba),
-- no como SENT. Los mensajes ya guardados nunca se enviaron: pasan a TEST.
ALTER TABLE obsint_agent_message DROP CONSTRAINT obsint_agent_message_delivery_status_check;

UPDATE obsint_agent_message SET delivery_status = 'TEST' WHERE delivery_status = 'SENT';

ALTER TABLE obsint_agent_message
    ADD CONSTRAINT obsint_agent_message_delivery_status_check CHECK (delivery_status IN ('TEST', 'SENT', 'FAILED'));
