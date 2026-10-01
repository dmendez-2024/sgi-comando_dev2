-- SER v0.10.3
-- Permite que un Protocolo de Bitácora aplique a uno o más Puestos del mismo Punto.

CREATE TABLE IF NOT EXISTS logbook_protocol_post_scope (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  protocol_id uuid NOT NULL REFERENCES logbook_protocol(id) ON DELETE CASCADE,
  post_id uuid NOT NULL REFERENCES post(id),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_logbook_protocol_post_scope UNIQUE(instance_country_id,protocol_id,post_id)
);
CREATE INDEX IF NOT EXISTS ix_logbook_protocol_post_scope_protocol ON logbook_protocol_post_scope(instance_country_id,protocol_id);

INSERT INTO logbook_protocol_post_scope(id,instance_country_id,protocol_id,post_id,created_at,updated_at)
SELECT gen_random_uuid(),instance_country_id,id,post_id,now(),now()
FROM logbook_protocol p
WHERE post_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM logbook_protocol_post_scope s
    WHERE s.instance_country_id=p.instance_country_id AND s.protocol_id=p.id AND s.post_id=p.post_id
  );
