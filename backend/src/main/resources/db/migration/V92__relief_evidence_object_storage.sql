-- Fotos del relevo en MinIO: la fila conserva solo bucket + object_key. content queda para las fotos antiguas
-- hasta que ReliefEvidenceMigrator las suba; luego una migración posterior elimina la columna.
ALTER TABLE operator_relief_evidence
  ADD COLUMN bucket varchar(63),
  ADD COLUMN object_key varchar(300),
  ADD COLUMN size_bytes bigint,
  ALTER COLUMN content DROP NOT NULL;

DO $$
DECLARE c text;
BEGIN
  FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='operator_relief_evidence'::regclass AND contype='c'
           AND pg_get_constraintdef(oid) LIKE '%octet_length%' LOOP
    EXECUTE format('ALTER TABLE operator_relief_evidence DROP CONSTRAINT %I',c);
  END LOOP;
END $$;

ALTER TABLE operator_relief_evidence ADD CONSTRAINT ck_relief_evidence_storage
  CHECK ((bucket IS NOT NULL AND object_key IS NOT NULL AND size_bytes BETWEEN 1 AND 5242880) OR content IS NOT NULL);
CREATE INDEX ix_relief_evidence_legacy ON operator_relief_evidence(created_at) WHERE content IS NOT NULL;
