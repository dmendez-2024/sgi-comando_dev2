-- SER v0.10.1
-- Estandariza estados de Protocolos a BORRADOR / INACTIVO / ACTIVO.
-- La regla de máximo 10 acreditaciones se aplica en backend para permitir mensajes funcionales claros.

ALTER TABLE logbook_protocol DROP CONSTRAINT IF EXISTS logbook_protocol_status_check;
UPDATE logbook_protocol
SET status = CASE
  WHEN status='BORRADOR' THEN 'BORRADOR'
  WHEN status='VIGENTE' THEN 'ACTIVO'
  ELSE 'INACTIVO'
END;
ALTER TABLE logbook_protocol ADD CONSTRAINT logbook_protocol_status_check CHECK (status IN ('BORRADOR','INACTIVO','ACTIVO'));

ALTER TABLE patrol_protocol DROP CONSTRAINT IF EXISTS patrol_protocol_status_check;
UPDATE patrol_protocol
SET status = CASE
  WHEN status='BORRADOR' THEN 'BORRADOR'
  WHEN status='VIGENTE' THEN 'ACTIVO'
  ELSE 'INACTIVO'
END;
ALTER TABLE patrol_protocol ADD CONSTRAINT patrol_protocol_status_check CHECK (status IN ('BORRADOR','INACTIVO','ACTIVO'));

-- Las definiciones de patrulla siguen el estado de su protocolo.
UPDATE patrol_definition d
SET status = p.status
FROM patrol_protocol p
WHERE d.protocol_id = p.id;

DROP INDEX IF EXISTS ux_consignment_protocol_vigente;
ALTER TABLE consignment_protocol DROP CONSTRAINT IF EXISTS consignment_protocol_status_check;
UPDATE consignment_protocol
SET status = CASE
  WHEN status='BORRADOR' THEN 'BORRADOR'
  WHEN status='VIGENTE' THEN 'ACTIVO'
  ELSE 'INACTIVO'
END;
ALTER TABLE consignment_protocol ADD CONSTRAINT consignment_protocol_status_check CHECK (status IN ('BORRADOR','INACTIVO','ACTIVO'));
CREATE UNIQUE INDEX IF NOT EXISTS ux_consignment_protocol_activo ON consignment_protocol(instance_country_id,point_id) WHERE status='ACTIVO';
