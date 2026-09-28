-- SGI: Comando — ASI v0.4
-- Snapshot read-only del ciclo operacional recibido desde SIC: COM.
-- ASI NO se convierte en SoR de Formato/Rotación; persiste solamente el snapshot consumido
-- para poder operar/copiar ciclos que crucen semanas calendario.

CREATE TABLE IF NOT EXISTS post_planning_cycle_snapshot (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  post_id uuid NOT NULL UNIQUE REFERENCES post(id),
  rotation_code varchar(32) NOT NULL,
  cycle_length_days integer NOT NULL CHECK (cycle_length_days > 0 AND cycle_length_days <= 90),
  source_system varchar(32) NOT NULL,
  source_version varchar(80) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_post_planning_cycle_snapshot_tenant_post
  ON post_planning_cycle_snapshot(instance_country_id, post_id);

-- Referencias UAT explícitas de SIC: COM. No se deduce el ciclo desde la semana visual.
INSERT INTO post_planning_cycle_snapshot(
  id,instance_country_id,post_id,rotation_code,cycle_length_days,source_system,source_version,created_at,updated_at
)
SELECT gen_random_uuid(),p.instance_country_id,p.id,
       CASE WHEN p.id='50000000-0000-0000-0000-000000000003'::uuid THEN '6-2' ELSE '5-2' END,
       CASE WHEN p.id='50000000-0000-0000-0000-000000000003'::uuid THEN 8 ELSE 7 END,
       'SIC: COM','SIC-COM-UAT-2026.09-ASI-v04',now(),now()
FROM post p
WHERE p.instance_country_id='11111111-1111-1111-1111-111111111111'::uuid
ON CONFLICT (post_id) DO UPDATE SET
  rotation_code=EXCLUDED.rotation_code,
  cycle_length_days=EXCLUDED.cycle_length_days,
  source_system=EXCLUDED.source_system,
  source_version=EXCLUDED.source_version,
  updated_at=now();
