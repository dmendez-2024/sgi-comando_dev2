package com.cajamarca.sgi.comando.obsint;

import jakarta.ws.rs.WebApplicationException;

/** Regla de OBSINT incumplida (409). Los recursos devuelven el mensaje como {"message": …} para que la web lo muestre. */
public class ObsintRuleException extends WebApplicationException {
    public ObsintRuleException(String message) {
        super(message, 409);
    }
}
