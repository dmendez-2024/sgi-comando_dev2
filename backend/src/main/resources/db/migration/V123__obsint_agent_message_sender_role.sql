-- Cargo del monitorista al enviar (role_code del empleado). Es lo único del remitente que ve el agente en SGI Operador:
-- ni nombre, ni usuario, ni identificadores.
ALTER TABLE obsint_agent_message ADD COLUMN sender_role varchar(120);
