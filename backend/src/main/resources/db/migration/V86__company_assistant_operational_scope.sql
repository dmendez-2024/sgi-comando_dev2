CREATE UNIQUE INDEX IF NOT EXISTS ux_user_operational_scope_company_responsible
  ON user_operational_scope(instance_country_id, scope_type, scope_id)
  WHERE scope_type = 'COMPANY'
    AND username IN ('coord', 'asistente')
    AND scope_id IS NOT NULL;
