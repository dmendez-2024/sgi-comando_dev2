-- SEN-01: OBSINT solo se habilita en los Puntos cuyo servicio es una orden de servicio OBSINT.
-- La marca va en el servicio (no se deduce del nombre visible).
ALTER TABLE service ADD COLUMN obsint boolean NOT NULL DEFAULT false;

-- Para no cortar lo que ya funciona: los servicios con algún protocolo OBSINT en sus Puntos quedan marcados.
UPDATE service s SET obsint = true
WHERE EXISTS (
    SELECT 1 FROM point p JOIN obsint_protocol o ON o.point_id = p.id AND o.instance_country_id = p.instance_country_id
    WHERE p.service_id = s.id
);
