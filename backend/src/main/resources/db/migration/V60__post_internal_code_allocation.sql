-- SIC:COM does not assign codes to operational posts.  SGI:Comando owns the
-- allocation of a durable, tenant-unique code derived from the commercial data.
CREATE TABLE post_code_allocation (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  base_code varchar(4) NOT NULL,
  source_signature varchar(800) NOT NULL,
  discriminator varchar(32) NOT NULL DEFAULT '',
  last_sequence integer NOT NULL DEFAULT 0 CHECK (last_sequence >= 0),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_post_code_allocation_signature
    UNIQUE(instance_country_id, base_code, source_signature),
  CONSTRAINT uq_post_code_allocation_discriminator
    UNIQUE(instance_country_id, base_code, discriminator)
);

CREATE INDEX ix_post_code_allocation_tenant_base
  ON post_code_allocation(instance_country_id, base_code);
