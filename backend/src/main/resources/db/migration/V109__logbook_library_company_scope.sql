-- A protocol version may be exported by successive companies if its Point changes ownership.
ALTER TABLE logbook_protocol_library_export
  DROP CONSTRAINT uq_logbook_library_export_version_scope;
CREATE UNIQUE INDEX uq_logbook_library_company_version
  ON logbook_protocol_library_export(instance_country_id, source_protocol_id, company_id)
  WHERE scope = 'COMPANY';
CREATE UNIQUE INDEX uq_logbook_library_country_version
  ON logbook_protocol_library_export(instance_country_id, source_protocol_id)
  WHERE scope = 'COUNTRY';