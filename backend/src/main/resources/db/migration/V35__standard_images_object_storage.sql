-- Fotos estándar pasan a MinIO. standard_image_data queda como respaldo hasta migrar y se elimina en una versión posterior.
ALTER TABLE patrol_checkpoint ADD COLUMN standard_image_object_key varchar(300), ADD COLUMN standard_image_sha256 char(64), ADD COLUMN standard_image_size bigint;
ALTER TABLE consignment_evidence ADD COLUMN standard_image_object_key varchar(300), ADD COLUMN standard_image_sha256 char(64), ADD COLUMN standard_image_size bigint;
ALTER TABLE logbook_protocol_field ADD COLUMN standard_image_object_key varchar(300), ADD COLUMN standard_image_sha256 char(64), ADD COLUMN standard_image_size bigint;
