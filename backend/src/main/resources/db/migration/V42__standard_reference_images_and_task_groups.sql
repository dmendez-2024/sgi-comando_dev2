-- Fase 4: fotos estándar (1..5) para Hitos de patrulla, evidencias de Consigna y campos de Bitácora en una sola tabla,
-- y agrupación de ejecuciones que no son de patrulla (turno para Consignas, registro de visitante para Bitácora).

CREATE TABLE standard_reference_image (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  target_type varchar(40) NOT NULL CHECK (target_type IN ('PATROL_CHECKPOINT','CONSIGNMENT_EVIDENCE','LOGBOOK_FIELD')),
  target_id uuid NOT NULL,
  position integer NOT NULL CHECK (position BETWEEN 1 AND 5),
  original_name varchar(255),
  content_type varchar(100) NOT NULL,
  object_key varchar(300) NOT NULL,
  sha256 char(64) NOT NULL,
  size_bytes bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_standard_reference_position UNIQUE (target_type, target_id, position) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX ix_standard_reference_target ON standard_reference_image(target_type, target_id);

-- Patrullas: se mueven las fotos estándar existentes (mismos ids, para no romper los snapshots de VISINT).
INSERT INTO standard_reference_image(id, instance_country_id, target_type, target_id, position, original_name, content_type, object_key, sha256, size_bytes, created_at)
SELECT id, instance_country_id, 'PATROL_CHECKPOINT', checkpoint_id, position, original_name, content_type, object_key, sha256, size_bytes, created_at
FROM patrol_checkpoint_standard_image;
DROP TABLE patrol_checkpoint_standard_image;

-- Consignas y Bitácora: la foto estándar única (ya en MinIO) pasa a ser la n.º 1. Las que aún están en bytea las pasa StandardImageMigrator.
INSERT INTO standard_reference_image(id, instance_country_id, target_type, target_id, position, original_name, content_type, object_key, sha256, size_bytes)
SELECT gen_random_uuid(), instance_country_id, 'CONSIGNMENT_EVIDENCE', id, 1, standard_image_original_name, coalesce(standard_image_content_type, 'image/jpeg'),
       standard_image_object_key, standard_image_sha256, coalesce(standard_image_size, 0)
FROM consignment_evidence WHERE standard_image_object_key IS NOT NULL AND standard_image_sha256 IS NOT NULL;
UPDATE consignment_evidence SET standard_image_object_key = NULL, standard_image_sha256 = NULL, standard_image_size = NULL,
       standard_image_original_name = NULL, standard_image_content_type = NULL
WHERE standard_image_object_key IS NOT NULL AND standard_image_sha256 IS NOT NULL;

INSERT INTO standard_reference_image(id, instance_country_id, target_type, target_id, position, original_name, content_type, object_key, sha256, size_bytes)
SELECT gen_random_uuid(), instance_country_id, 'LOGBOOK_FIELD', id, 1, standard_image_original_name, coalesce(standard_image_content_type, 'image/jpeg'),
       standard_image_object_key, standard_image_sha256, coalesce(standard_image_size, 0)
FROM logbook_protocol_field WHERE standard_image_object_key IS NOT NULL AND standard_image_sha256 IS NOT NULL;
UPDATE logbook_protocol_field SET standard_image_object_key = NULL, standard_image_sha256 = NULL, standard_image_size = NULL,
       standard_image_original_name = NULL, standard_image_content_type = NULL
WHERE standard_image_object_key IS NOT NULL AND standard_image_sha256 IS NOT NULL;

-- Ejecuciones de Consignas (grupo = asignación/turno) y Bitácora (grupo = registro del visitante): una captura por destino y grupo.
ALTER TABLE task_execution ADD COLUMN group_id uuid;
CREATE UNIQUE INDEX uq_task_execution_group_capture ON task_execution(instance_country_id, group_id, target_id, capture_no) WHERE patrol_execution_id IS NULL AND group_id IS NOT NULL;
