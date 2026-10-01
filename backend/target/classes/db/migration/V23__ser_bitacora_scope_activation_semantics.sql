-- SER v0.10.4
-- Desde esta versión, logbook_protocol_post_scope representa activación operativa por Puesto.
-- Un Protocolo publicado INACTIVO no debe conservar Puestos activos heredados de V22.
-- Los BORRADORES conservan su selección como alcance previsto para publicación.

DELETE FROM logbook_protocol_post_scope s
USING logbook_protocol p
WHERE s.protocol_id = p.id
  AND s.instance_country_id = p.instance_country_id
  AND p.status = 'INACTIVO';
