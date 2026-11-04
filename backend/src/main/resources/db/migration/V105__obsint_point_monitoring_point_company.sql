-- El monitoreo OBSINT usa la Compañía del Punto (la que se asigna en Servicios: la orden llega a Kaibil y desde ahí se manda
-- a una Compañía). En la pestaña OBSINT solo se eligen el monitorista responsable y el suplente; la Compañía propia sobra.
ALTER TABLE obsint_point_monitoring DROP COLUMN company_id;
