-- Published protocol versions available for reuse in Patrols and Consignments.
CREATE TABLE protocol_library_export (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  module varchar(16) NOT NULL CHECK (module IN ('PATROL','CONSIGNMENT')),
  source_protocol_id uuid NOT NULL,
  scope varchar(16) NOT NULL CHECK (scope IN ('COMPANY','COUNTRY')),
  company_id uuid REFERENCES company(id),
  exported_by_username varchar(80) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_protocol_library_company CHECK (
    (scope='COMPANY' AND company_id IS NOT NULL) OR
    (scope='COUNTRY' AND company_id IS NULL)
  )
);
CREATE UNIQUE INDEX uq_protocol_library_company_version
  ON protocol_library_export(instance_country_id,module,source_protocol_id,company_id)
  WHERE scope='COMPANY';
CREATE UNIQUE INDEX uq_protocol_library_country_version
  ON protocol_library_export(instance_country_id,module,source_protocol_id)
  WHERE scope='COUNTRY';
CREATE INDEX ix_protocol_library_visible
  ON protocol_library_export(instance_country_id,module,scope,company_id);
