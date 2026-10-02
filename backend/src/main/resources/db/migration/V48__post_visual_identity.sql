ALTER TABLE post_operational_config
  ADD COLUMN alias varchar(120),
  ADD COLUMN visual_title varchar(300);

ALTER TABLE standard_reference_image
  DROP CONSTRAINT standard_reference_image_target_type_check;
ALTER TABLE standard_reference_image
  ADD CONSTRAINT standard_reference_image_target_type_check
  CHECK (target_type IN ('PATROL_CHECKPOINT', 'CONSIGNMENT_EVIDENCE', 'LOGBOOK_FIELD', 'POST_CONFIG'));
