CREATE TABLE institutional_document (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  company_id uuid REFERENCES company(id),
  name varchar(180) NOT NULL,
  version varchar(40) NOT NULL,
  status varchar(24) NOT NULL CHECK (status IN ('VIGENTE','NO_VIGENTE')),
  original_name varchar(255) NOT NULL,
  content_type varchar(100) NOT NULL,
  bucket varchar(80) NOT NULL,
  object_key varchar(400) NOT NULL,
  sha256 varchar(64) NOT NULL,
  size_bytes bigint NOT NULL CHECK (size_bytes > 0),
  uploaded_by varchar(160) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX uq_institutional_document_version
  ON institutional_document(
    instance_country_id,
    coalesce(company_id,'00000000-0000-0000-0000-000000000000'::uuid),
    lower(name),
    lower(version)
  );
CREATE UNIQUE INDEX uq_institutional_document_object
  ON institutional_document(bucket,object_key);
CREATE INDEX ix_institutional_document_visible
  ON institutional_document(instance_country_id,company_id,status,updated_at DESC);
