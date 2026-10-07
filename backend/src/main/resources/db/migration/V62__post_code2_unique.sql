-- Code2 is the unique external post reference supplied by SIC:COM.
-- Legacy rows may remain NULL until their external reference is available.
ALTER TABLE post DROP CONSTRAINT post_instance_country_id_code_key;

CREATE UNIQUE INDEX ux_post_tenant_code2
  ON post(instance_country_id, code2)
  WHERE code2 IS NOT NULL;
