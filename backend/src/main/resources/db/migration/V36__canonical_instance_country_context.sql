-- Contexto canónico Instancia-País.
-- No contiene UUIDs de ambiente: descubre el contexto legado existente y la
-- aplicación lo reconcilia con el identificador que entrega CORE.

CREATE TABLE instance_country_context (
  singleton_key smallint PRIMARY KEY CHECK (singleton_key = 1),
  instance_country_id uuid NOT NULL,
  country_id uuid,
  country_iso_alpha2 varchar(2),
  country_name varchar(120),
  instance_country_code varchar(32),
  instance_country_name varchar(160),
  source_system varchar(16) NOT NULL DEFAULT 'LOCAL_DISCOVERY',
  source_synced_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE instance_country_context_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  previous_instance_country_id uuid NOT NULL,
  new_instance_country_id uuid NOT NULL,
  country_id uuid NOT NULL,
  country_iso_alpha2 varchar(2) NOT NULL,
  instance_country_code varchar(32) NOT NULL,
  migrated_row_count integer NOT NULL,
  source_system varchar(16) NOT NULL,
  migrated_at timestamptz NOT NULL DEFAULT now()
);

DO $$
DECLARE
  tenant_table record;
  table_distinct_count bigint;
  table_instance_country_id uuid;
  discovered_instance_country_id uuid;
BEGIN
  FOR tenant_table IN
    SELECT c.table_schema, c.table_name
      FROM information_schema.columns c
      JOIN information_schema.tables t
        ON t.table_schema = c.table_schema
       AND t.table_name = c.table_name
     WHERE c.table_schema = 'public'
       AND c.column_name = 'instance_country_id'
       AND t.table_type = 'BASE TABLE'
       AND c.table_name <> 'instance_country_context'
     ORDER BY c.table_name
  LOOP
    EXECUTE format(
      'select count(distinct instance_country_id), min(instance_country_id::text)::uuid from %I.%I where instance_country_id is not null',
      tenant_table.table_schema,
      tenant_table.table_name
    ) INTO table_distinct_count, table_instance_country_id;

    IF table_distinct_count > 1 THEN
      RAISE EXCEPTION 'La tabla %.% contiene más de un instance_country_id; se requiere revisión manual antes de migrar.',
        tenant_table.table_schema, tenant_table.table_name;
    END IF;

    IF table_distinct_count = 1 THEN
      IF discovered_instance_country_id IS NULL THEN
        discovered_instance_country_id := table_instance_country_id;
      ELSIF discovered_instance_country_id <> table_instance_country_id THEN
        RAISE EXCEPTION 'La base contiene varios contextos Instancia-País; se requiere revisión manual antes de migrar.';
      END IF;
    END IF;
  END LOOP;

  IF discovered_instance_country_id IS NULL THEN
    RAISE EXCEPTION 'No se pudo descubrir el contexto Instancia-País existente.';
  END IF;

  INSERT INTO instance_country_context(singleton_key, instance_country_id)
  VALUES (1, discovered_instance_country_id);
END
$$;

CREATE OR REPLACE FUNCTION sgi_migrate_instance_country_context(
  canonical_instance_country_id uuid,
  canonical_country_id uuid,
  canonical_country_iso_alpha2 text,
  canonical_country_name text,
  canonical_instance_country_code text,
  canonical_instance_country_name text
) RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
  previous_instance_country_id uuid;
  tenant_table record;
  unexpected_count bigint;
  canonical_count bigint;
  updated_in_table integer;
  total_updated integer := 0;
BEGIN
  IF canonical_instance_country_id IS NULL OR canonical_country_id IS NULL THEN
    RAISE EXCEPTION 'CORE devolvió un contexto Instancia-País incompleto.';
  END IF;
  IF nullif(btrim(canonical_country_iso_alpha2), '') IS NULL
     OR nullif(btrim(canonical_instance_country_code), '') IS NULL THEN
    RAISE EXCEPTION 'CORE devolvió códigos de contexto vacíos.';
  END IF;

  SELECT instance_country_id
    INTO previous_instance_country_id
    FROM instance_country_context
   WHERE singleton_key = 1
   FOR UPDATE;

  IF previous_instance_country_id IS NULL THEN
    RAISE EXCEPTION 'No existe un contexto Instancia-País local para reconciliar.';
  END IF;

  IF previous_instance_country_id <> canonical_instance_country_id THEN
    FOR tenant_table IN
      SELECT c.table_schema, c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema
         AND t.table_name = c.table_name
       WHERE c.table_schema = 'public'
         AND c.column_name = 'instance_country_id'
         AND t.table_type = 'BASE TABLE'
         AND c.table_name <> 'instance_country_context'
       ORDER BY c.table_name
    LOOP
      EXECUTE format(
        'select count(*) filter (where instance_country_id is not null and instance_country_id not in ($1,$2)), count(*) filter (where instance_country_id=$2) from %I.%I',
        tenant_table.table_schema,
        tenant_table.table_name
      ) USING previous_instance_country_id, canonical_instance_country_id
        INTO unexpected_count, canonical_count;

      IF unexpected_count > 0 THEN
        RAISE EXCEPTION 'La tabla %.% contiene un contexto distinto del vigente y del canónico; migración cancelada.',
          tenant_table.table_schema, tenant_table.table_name;
      END IF;
      IF canonical_count > 0 THEN
        RAISE EXCEPTION 'La tabla %.% ya contiene filas del contexto canónico junto al contexto anterior; migración cancelada para evitar colisiones.',
          tenant_table.table_schema, tenant_table.table_name;
      END IF;
    END LOOP;

    FOR tenant_table IN
      SELECT c.table_schema, c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema
         AND t.table_name = c.table_name
       WHERE c.table_schema = 'public'
         AND c.column_name = 'instance_country_id'
         AND t.table_type = 'BASE TABLE'
         AND c.table_name <> 'instance_country_context'
       ORDER BY c.table_name
    LOOP
      EXECUTE format(
        'update %I.%I set instance_country_id=$1 where instance_country_id=$2',
        tenant_table.table_schema,
        tenant_table.table_name
      ) USING canonical_instance_country_id, previous_instance_country_id;
      GET DIAGNOSTICS updated_in_table = ROW_COUNT;
      total_updated := total_updated + updated_in_table;
    END LOOP;

    INSERT INTO instance_country_context_history(
      previous_instance_country_id,
      new_instance_country_id,
      country_id,
      country_iso_alpha2,
      instance_country_code,
      migrated_row_count,
      source_system
    ) VALUES (
      previous_instance_country_id,
      canonical_instance_country_id,
      canonical_country_id,
      upper(btrim(canonical_country_iso_alpha2)),
      btrim(canonical_instance_country_code),
      total_updated,
      'CORE'
    );
  END IF;

  UPDATE instance_country_context
     SET instance_country_id = canonical_instance_country_id,
         country_id = canonical_country_id,
         country_iso_alpha2 = upper(btrim(canonical_country_iso_alpha2)),
         country_name = nullif(btrim(canonical_country_name), ''),
         instance_country_code = btrim(canonical_instance_country_code),
         instance_country_name = nullif(btrim(canonical_instance_country_name), ''),
         source_system = 'CORE',
         source_synced_at = now(),
         updated_at = now()
   WHERE singleton_key = 1;

  RETURN total_updated;
END
$$;

COMMENT ON TABLE instance_country_context IS
  'Último contexto Instancia-País canónico resuelto desde CORE; singleton local de arranque.';
COMMENT ON TABLE instance_country_context_history IS
  'Auditoría de migraciones del alcance instance_country_id hacia la identidad canónica de CORE.';
