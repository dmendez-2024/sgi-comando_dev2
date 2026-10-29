-- OBSINT como "puesto" en Consignas (diapositiva 9 de "OBSINT Estructura"): la consigna marcada la verifica el Monitorista OBSINT.
ALTER TABLE consignment ADD COLUMN obsint boolean NOT NULL DEFAULT false;

-- Tarea OBSINT vinculada a una consigna de Consignas por su código (el código se mantiene entre versiones del protocolo).
-- NULL = tarea creada antes del vínculo, escrita directamente en OBSINT.
ALTER TABLE obsint_consigna ADD COLUMN consignment_code varchar(32);
