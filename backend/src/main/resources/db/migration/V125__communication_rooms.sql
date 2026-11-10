-- Salas de chat de Comunicación (SGI Operador). Cada mensaje al agente pertenece a una sala; los de la Consola OBSINT
-- van a la sala por defecto "Monitor Consola" (código MONITOR_CONSOLA). Las salas se ven en SGI Operador por la 0037.
CREATE TABLE communication_room (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    code varchar(60) NOT NULL,
    name varchar(120) NOT NULL,
    source varchar(30) NOT NULL CHECK (source IN ('OBSINT_CONSOLE')),
    is_default boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_communication_room_code UNIQUE (instance_country_id, code)
);
-- Una sola sala por defecto por instancia.
CREATE UNIQUE INDEX uq_communication_room_default ON communication_room(instance_country_id) WHERE is_default;

INSERT INTO communication_room(id, instance_country_id, code, name, source, is_default, active, created_at, updated_at)
SELECT gen_random_uuid(), c.instance_country_id, 'MONITOR_CONSOLA', 'Monitor Consola', 'OBSINT_CONSOLE', true, true, now(), now()
  FROM instance_country_context c
 WHERE c.singleton_key = 1
ON CONFLICT (instance_country_id, code) DO NOTHING;

ALTER TABLE obsint_agent_message ADD COLUMN room_id uuid REFERENCES communication_room(id);

-- Los mensajes ya enviados desde la consola pasan a la sala por defecto de su instancia.
UPDATE obsint_agent_message m
   SET room_id = r.id
  FROM communication_room r
 WHERE r.instance_country_id = m.instance_country_id AND r.code = 'MONITOR_CONSOLA' AND m.room_id IS NULL;

CREATE INDEX ix_obsint_agent_message_room ON obsint_agent_message(room_id, sent_at);
