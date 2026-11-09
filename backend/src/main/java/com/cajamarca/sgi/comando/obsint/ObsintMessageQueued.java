package com.cajamarca.sgi.comando.obsint;

import java.util.UUID;

/** Se despacha solamente después de confirmar la transacción del mensaje. */
public record ObsintMessageQueued(UUID messageId) {}
