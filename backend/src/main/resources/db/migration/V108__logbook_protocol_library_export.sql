-- A library entry points to an immutable, published Bitacora protocol version.
CREATE TABLE logbook_protocol_library_export (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  source_protocol_id uuid NOT NULL REFERENCES logbook_protocol(id),
  scope varchar(16) NOT NULL CHECK (scope IN ('COMPANY', 'COUNTRY')),
  company_id uuid REFERENCES company(id),
  exported_by_username varchar(80) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_logbook_library_export_company CHECK (
    (scope = 'COMPANY' AND company_id IS NOT NULL) OR
    (scope = 'COUNTRY' AND company_id IS NULL)
  ),
  CONSTRAINT uq_logbook_library_export_version_scope
    UNIQUE (instance_country_id, source_protocol_id, scope)
);
CREATE INDEX ix_logbook_library_export_scope
  ON logbook_protocol_library_export(instance_country_id, scope, company_id);
