package com.cajamarca.sgi.comando.operator;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.*;

/**
 * La app del agente necesita el motivo del rechazo, no solo el código HTTP. Los recursos del operador
 * devuelven el mensaje de la excepción como texto plano cuando la respuesta no trae cuerpo propio.
 */
final class OperatorErrors {
    private OperatorErrors() {}

    static Response withMessage(WebApplicationException e) {
        Response r = e.getResponse();
        if (r.hasEntity() || e.getMessage() == null) return r;
        return Response.status(r.getStatus()).entity(e.getMessage()).type(MediaType.TEXT_PLAIN_TYPE).build();
    }
}
