-- Login con IDENT: el alcance territorial (zona / region / compania) de cada persona se busca por su persona_id de DHO,
-- que IDENT envia en el token. username se mantiene para los usuarios de SGI Operador (usuario y contrasena) mientras
-- esa app no tenga login con IDENT; para personas de IDENT se guarda su correo.
ALTER TABLE user_operational_scope ADD COLUMN IF NOT EXISTS persona_id bigint;

CREATE INDEX IF NOT EXISTS ix_user_operational_scope_persona
  ON user_operational_scope(instance_country_id, persona_id)
  WHERE persona_id IS NOT NULL;
