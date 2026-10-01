package com.cajamarca.sgi.comando.storage;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.*;

/** MinIO no responde: se informa como 503 con un mensaje legible. */
public class StorageUnavailableException extends WebApplicationException {
    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause, Response.status(503).entity(message).type(MediaType.TEXT_PLAIN_TYPE).build());
    }
}
