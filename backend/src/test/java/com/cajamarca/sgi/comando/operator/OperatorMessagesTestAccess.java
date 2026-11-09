package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.socket.SocketConfig;
import jakarta.persistence.EntityManager;
import java.util.UUID;

/** Identidad controlada para probar autorización de destinatarios sin fixtures de IDENT. */
public final class OperatorMessagesTestAccess {
    private OperatorMessagesTestAccess() {}
    public static OperatorMessageResource resource(EntityManager em,SocketConfig config,com.cajamarca.sgi.comando.obsint.ObsintMessageReceipts receipts,UUID tenant,UUID employee) {
        var resource=new OperatorMessageResource(); resource.em=em; resource.config=config; resource.receipts=receipts;
        resource.tenant=new TenantContext() { public UUID instanceCountryId() { return tenant; } };
        resource.context=new OperatorContext() { public UUID employee() { return employee; } };
        return resource;
    }
}
