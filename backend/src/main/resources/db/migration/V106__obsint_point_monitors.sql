-- Monitoristas OBSINT del Punto: uno o más (antes, responsable y suplente). Son empleados de la Compañía del Punto con cargo de
-- monitoreo; los ve cualquiera de ellos que esté de turno. Sin ninguno (o ninguno de turno), cualquier monitor de turno de esa Compañía.
CREATE TABLE obsint_point_monitor (
    instance_country_id uuid         NOT NULL,
    point_id            uuid         NOT NULL REFERENCES point(id) ON DELETE CASCADE,
    employee_id         uuid         NOT NULL,
    updated_by          varchar(160) NOT NULL,
    updated_at          timestamptz  NOT NULL,
    PRIMARY KEY (instance_country_id, point_id, employee_id)
);

-- Se conservan los responsables y suplentes ya elegidos.
INSERT INTO obsint_point_monitor(instance_country_id, point_id, employee_id, updated_by, updated_at)
SELECT instance_country_id, point_id, responsible_employee_id, updated_by, updated_at FROM obsint_point_monitoring WHERE responsible_employee_id IS NOT NULL
UNION
SELECT instance_country_id, point_id, backup_employee_id, updated_by, updated_at FROM obsint_point_monitoring WHERE backup_employee_id IS NOT NULL;

DROP TABLE obsint_point_monitoring;
