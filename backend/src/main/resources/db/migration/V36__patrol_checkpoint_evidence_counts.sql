-- Cantidad de fotos que el agente debe enviar por Hito.
ALTER TABLE patrol_checkpoint
  ADD COLUMN evidence_min_count integer NOT NULL DEFAULT 1,
  ADD COLUMN evidence_max_count integer NOT NULL DEFAULT 5,
  ADD CONSTRAINT ck_patrol_checkpoint_evidence_count
    CHECK (evidence_min_count BETWEEN 1 AND 5 AND evidence_max_count BETWEEN evidence_min_count AND 5);
