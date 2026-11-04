package com.cajamarca.sgi.comando.obsint;

import jakarta.ws.rs.ForbiddenException;

/** SEN-01: el Punto no tiene orden de servicio OBSINT. Viaja como 403 con {"code": "OBSINT_SIN_ORDEN"}. */
public class ObsintDisabledException extends ForbiddenException {
    public static final String CODE = "OBSINT_SIN_ORDEN";

    public ObsintDisabledException() { super("Requiere orden de servicio OBSINT"); }
}
