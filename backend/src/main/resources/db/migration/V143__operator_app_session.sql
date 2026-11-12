-- Registro de inicio y cierre de sesión de los agentes en las aplicaciones móviles. Una fila por sesión: la app genera
-- el id al ingresar y lo reutiliza al cerrar sesión (o en reintentos sin conexión). application identifica la app que
-- la abrió; hoy solo existe SGI_OPERADOR.
CREATE TABLE operator_app_session (
    id uuid PRIMARY KEY,
    instance_country_id uuid NOT NULL,
    application varchar(40) NOT NULL DEFAULT 'SGI_OPERADOR',
    employee_id uuid NOT NULL,
    username varchar(160) NOT NULL,
    assignment_id uuid,
    device_id varchar(160),
    app_version varchar(40),
    logged_in_at timestamptz,
    login_received_at timestamptz,
    logged_out_at timestamptz,
    logout_received_at timestamptz,
    logout_reason varchar(30),
    CONSTRAINT ck_operator_app_session_logout_reason CHECK (logout_reason IS NULL OR logout_reason IN ('USER','EXPIRED','REVOKED'))
);
CREATE INDEX ix_operator_app_session_employee ON operator_app_session(instance_country_id, employee_id, logged_in_at DESC);
CREATE INDEX ix_operator_app_session_application ON operator_app_session(instance_country_id, application, logged_in_at DESC);
