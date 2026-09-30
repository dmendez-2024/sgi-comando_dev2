-- Hitos de patrulla: hasta 5 fotos estándar por Hito (se envían a VISINT como referenceImages) y una sola foto del agente.

CREATE TABLE patrol_checkpoint_standard_image (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  checkpoint_id uuid NOT NULL REFERENCES patrol_checkpoint(id) ON DELETE CASCADE,
  position integer NOT NULL CHECK (position BETWEEN 1 AND 5),
  original_name varchar(255),
  content_type varchar(100) NOT NULL,
  object_key varchar(300) NOT NULL,
  sha256 char(64) NOT NULL,
  size_bytes bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_patrol_standard_image_position UNIQUE (checkpoint_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- La foto estándar única que ya existía pasa a ser la n.º 1. Las que aún están en bytea las pasa StandardImageMigrator al subirlas a MinIO.
INSERT INTO patrol_checkpoint_standard_image(id, instance_country_id, checkpoint_id, position, original_name, content_type, object_key, sha256, size_bytes)
SELECT gen_random_uuid(), instance_country_id, id, 1, standard_image_original_name, coalesce(standard_image_content_type, 'image/jpeg'),
       standard_image_object_key, standard_image_sha256, coalesce(standard_image_size, 0)
FROM patrol_checkpoint WHERE standard_image_object_key IS NOT NULL AND standard_image_sha256 IS NOT NULL;
UPDATE patrol_checkpoint SET standard_image_object_key = NULL, standard_image_sha256 = NULL, standard_image_size = NULL,
       standard_image_original_name = NULL, standard_image_content_type = NULL
WHERE standard_image_object_key IS NOT NULL AND standard_image_sha256 IS NOT NULL;

-- El agente envía una sola foto por Hito: se elimina la configuración de mínimo/máximo.
ALTER TABLE patrol_checkpoint DROP COLUMN evidence_min_count, DROP COLUMN evidence_max_count;

-- Fotos estándar enviadas a VISINT en cada revisión (snapshot).
CREATE TABLE visual_review_standard (
  id uuid PRIMARY KEY,
  review_id uuid NOT NULL REFERENCES visual_review(id) ON DELETE CASCADE,
  position integer NOT NULL,
  standard_image_id uuid NOT NULL,
  bucket varchar(63) NOT NULL,
  object_key varchar(300) NOT NULL,
  sha256 char(64) NOT NULL,
  content_type varchar(100) NOT NULL,
  UNIQUE (review_id, position)
);
INSERT INTO visual_review_standard(id, review_id, position, standard_image_id, bucket, object_key, sha256, content_type)
SELECT gen_random_uuid(), id, 1, gen_random_uuid(), standard_bucket, standard_object_key, standard_sha256,
       CASE WHEN standard_object_key LIKE '%.png' THEN 'image/png' WHEN standard_object_key LIKE '%.webp' THEN 'image/webp' ELSE 'image/jpeg' END
FROM visual_review WHERE standard_object_key IS NOT NULL AND standard_bucket IS NOT NULL AND standard_sha256 IS NOT NULL;
ALTER TABLE visual_review DROP COLUMN standard_bucket, DROP COLUMN standard_object_key, DROP COLUMN standard_sha256;

-- matchedReferenceId de VISINT ahora identifica una foto estándar (antes, una foto del agente).
ALTER TABLE visual_review RENAME COLUMN matched_evidence_id TO matched_standard_image_id;
UPDATE visual_review SET matched_standard_image_id = NULL;
