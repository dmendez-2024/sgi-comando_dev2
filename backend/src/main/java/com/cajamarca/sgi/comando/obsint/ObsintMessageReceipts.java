package com.cajamarca.sgi.comando.obsint;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.ws.rs.BadRequestException;
import org.jboss.logging.Logger;
import java.time.Instant;

/** Recibos compartidos por el gateway y la app; el token de la app aporta la identidad autenticada. */
@ApplicationScoped
public class ObsintMessageReceipts {
    private static final Logger LOG=Logger.getLogger(ObsintMessageReceipts.class);
    public enum Via { SOCKET_ACK, OPERATOR_API }
    @Inject EntityManager em;
    /** Requiere transacción. Bloquea destinatario y después mensaje, igual que el despachador. */
    public ObsintAgentMessageRecipient apply(ObsintAgentMessageRecipient r,String status,Via via,Instant at) {
        if(!("READ".equals(status)||"DELIVERED".equals(status)||("REJECTED".equals(status)&&via==Via.SOCKET_ACK)))
            throw new BadRequestException("Recibo no válido");
        em.refresh(r,LockModeType.PESSIMISTIC_WRITE);
        ObsintAgentMessage m=em.find(ObsintAgentMessage.class,r.messageId,LockModeType.PESSIMISTIC_WRITE);
        applyState(r,status,via,at,Instant.now());
        ObsintAgentMessageRecipient.refresh(m);
        LOG.infof("obsint_agent_message_receipt message=%s recipient=%s employee=%s status=%s via=%s",m.id,r.id,r.employeeId,status,via);
        return r;
    }
    public static void applyState(ObsintAgentMessageRecipient r,String status,Via via,Instant at,Instant now) {
        if(at==null || at.isAfter(now.plusSeconds(300))) at=now;
        if("REJECTED".equals(status)) {
            if(r.appRejectedAt==null) r.appRejectedAt=at;
            r.lastError="APP_REJECTED"; return;
        }
        if("FAILED".equals(r.status) && !"EXPIRED".equals(r.lastError)) return;
        if(r.deliveredAt==null) { r.deliveredAt=at; r.deliveredVia=via.name(); }
        else if(via==Via.OPERATOR_API) r.deliveredVia=via.name();
        if("READ".equals(status)) {
            if(r.readAt==null) { r.readAt=at; r.readVia=via.name(); }
            else if(via==Via.OPERATOR_API) r.readVia=via.name();
        }
        if(ObsintAgentMessageRecipient.progress(status)>ObsintAgentMessageRecipient.progress(r.status)) r.status=status;
        if("EXPIRED".equals(r.lastError)) r.lastError=null;
    }
}
