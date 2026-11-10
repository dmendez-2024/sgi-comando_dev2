package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.socket.SocketConfig;
import jakarta.persistence.EntityManager;
import java.util.UUID;

/** Identidad controlada para probar autorización de destinatarios sin fixtures de IDENT. */
public final class OperatorMessagesTestAccess {
    private OperatorMessagesTestAccess() {}
    /** Recurso visto por un Agente de Seguridad. */
    public static OperatorMessageResource resource(EntityManager em,SocketConfig config,com.cajamarca.sgi.comando.obsint.ObsintMessageReceipts receipts,UUID tenant,UUID employee) {
        return resource(em,config,receipts,tenant,employee,true);
    }
    /** Recurso visto por un Agente (agent=true) o por un Supervisor de Seguridad que no es Agente (agent=false). */
    public static OperatorMessageResource resource(EntityManager em,SocketConfig config,com.cajamarca.sgi.comando.obsint.ObsintMessageReceipts receipts,UUID tenant,UUID employee,boolean agent) {
        var resource=new OperatorMessageResource(); resource.em=em; resource.config=config; resource.receipts=receipts;
        resource.roomCatalog=new com.cajamarca.sgi.comando.communication.CommunicationRooms();
        resource.tenant=new TenantContext() { public UUID instanceCountryId() { return tenant; } };
        resource.context=new OperatorContext() {
            public UUID employee() { return employee; }
            public boolean isAgent() { return agent; }
        };
        return resource;
    }
}
