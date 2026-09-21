-- SER v0.10.8
-- Activación operativa por Puesto para Protocolos de Patrullas.
-- ACTIVO y BORRADOR heredan el Puesto ancla; INACTIVO inicia sin Puestos activos.

CREATE TABLE IF NOT EXISTS patrol_protocol_post_scope (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  protocol_id uuid NOT NULL REFERENCES patrol_protocol(id) ON DELETE CASCADE,
  post_id uuid NOT NULL REFERENCES post(id),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_patrol_protocol_post_scope UNIQUE(instance_country_id,protocol_id,post_id)
);
CREATE INDEX IF NOT EXISTS ix_patrol_protocol_post_scope_protocol
  ON patrol_protocol_post_scope(instance_country_id,protocol_id);

INSERT INTO patrol_protocol_post_scope(id,instance_country_id,protocol_id,post_id,created_at,updated_at)
SELECT gen_random_uuid(),p.instance_country_id,p.id,p.post_id,now(),now()
FROM patrol_protocol p
WHERE p.post_id IS NOT NULL
  AND p.status IN ('ACTIVO','BORRADOR')
  AND NOT EXISTS (
    SELECT 1
    FROM patrol_protocol_post_scope s
    WHERE s.instance_country_id=p.instance_country_id
      AND s.protocol_id=p.id
      AND s.post_id=p.post_id
  );
