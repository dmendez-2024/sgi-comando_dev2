package com.cajamarca.sgi.comando.obsint;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Regla de la consola incumplida (409). El mensaje viaja como texto plano para que la web lo muestre tal cual. */
public class ObsintRuleException extends WebApplicationException {
    public ObsintRuleException(String message) {
        super(message, Response.status(Response.Status.CONFLICT).entity(message).type(MediaType.TEXT_PLAIN + ";charset=UTF-8").build());
    }
}
