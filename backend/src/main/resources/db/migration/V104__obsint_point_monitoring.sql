-- Monitoreo OBSINT del Punto: qué Compañía lo monitorea y quién es el monitorista responsable (y su suplente).
-- Sin fila, el Punto lo monitorea Kaibil (la orden OBSINT llega a Kaibil) y lo atiende cualquier monitor de Kaibil de turno.
-- La Compañía de monitoreo es independiente de la Compañía que opera el Punto (point.company_id).
CREATE TABLE obsint_point_monitoring (
    instance_country_id     uuid         NOT NULL,
    point_id                uuid         NOT NULL REFERENCES point(id) ON DELETE CASCADE,
    company_id              uuid         NOT NULL REFERENCES company(id),
    responsible_employee_id uuid,
    backup_employee_id      uuid,
    updated_by              varchar(160) NOT NULL,
    updated_at              timestamptz  NOT NULL,
    PRIMARY KEY (instance_country_id, point_id),
    CONSTRAINT ck_obsint_monitoring_backup_differs CHECK (backup_employee_id IS NULL OR backup_employee_id IS DISTINCT FROM responsible_employee_id)
);
